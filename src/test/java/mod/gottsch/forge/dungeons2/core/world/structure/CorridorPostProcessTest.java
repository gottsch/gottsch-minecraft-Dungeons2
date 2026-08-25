/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.BasicCorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.diagnostic.FakeWorldGenLevel;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.dungeons2.diagnostic.TestRegistries;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives real corridor pieces through their real {@code postProcess}, against
 * {@link FakeWorldGenLevel}.
 *
 * <p>This is the check the rest of the suite could not make. Everything else calls the generators
 * directly and so sees a piece's <em>intent</em>; {@code postProcess} is where the weathering
 * processor list and {@code settleJoinShapes} run, and both of those have shipped defects that no
 * headless check could see. What is asserted here is deliberately about that last mile &mdash; what
 * the world ends up holding, not what the generator asked for.</p>
 *
 * <p>Corridor pieces specifically: they are pure block placement. A room piece also spawns entities,
 * which needs a real {@code ServerLevel} the stub does not have.</p>
 *
 * @author Mark Gottschling on Aug 05, 2026
 */
class CorridorPostProcessTest {

    private static final int ANCHOR_X = 0;
    private static final int ANCHOR_Z = 0;
    private static final int SURFACE_Y = 72;
    private static final String MOTIF = "classic";
    private static final int SEEDS = 4;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DungeonLayout plan(long seed) {
        return new DungeonStackPlanner(
                seed, new Coords(ANCHOR_X, 0, ANCHOR_Z), SURFACE_Y, MOTIF, new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withCorridorWidth(3)
                .withCorridorStyles(DungeonStructure.corridorStyleWeights(
                        MotifConfigs.load(MOTIF).corridor()))
                .plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
    }

    /** Plans a dungeon and runs every corridor piece's postProcess into one shared fake level. */
    private static FakeWorldGenLevel run(long seed) {
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        for (DungeonCorridorPiece piece : pieces(plan(seed))) {
            postProcessPerChunk(piece, level, seed);
        }
        return level;
    }

    private static List<DungeonCorridorPiece> pieces(DungeonLayout layout) {
        List<DungeonCorridorPiece> pieces = new ArrayList<>();
        for (FloorLayout floor : layout.getFloors()) {
            for (CorridorData corridor : floor.getCorridors()) {
                pieces.add(new DungeonCorridorPiece(
                        corridor, MOTIF, floor.getFloorY(), floor.getFloorIndex(), ANCHOR_X, ANCHOR_Z));
            }
        }
        return pieces;
    }

    /**
     * Runs one piece's {@code postProcess} the way the game does: <strong>once per chunk</strong>
     * its bounding box spans, with the box clipped to that chunk.
     *
     * <p>This is not a detail. A piece is invoked once per chunk and each call is expected to write
     * only its own slice, so everything in {@code postProcess} runs N times over the same piece.
     * That is what makes the two-pass split in {@code PieceProcessors} necessary &mdash; the
     * neighbour-aware half has to see the whole piece or it decorates the two sides of a seam
     * differently &mdash; and calling {@code postProcess} once with the whole box, as this test
     * originally did, exercises none of it.</p>
     *
     * <p>Mirrors {@code StructureStart.placeInChunk}: full build-height Y range, the
     * {@code intersects} guard, and the origin taken from the piece box's centre column.</p>
     */
    private static void postProcessPerChunk(DungeonCorridorPiece piece, FakeWorldGenLevel level, long seed) {
        BoundingBox pieceBox = piece.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(pieceBox.minX()),
                SectionPos.blockToSectionCoord(pieceBox.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(pieceBox.maxX()),
                SectionPos.blockToSectionCoord(pieceBox.maxZ()));
        BlockPos origin = new BlockPos(pieceBox.getCenter().getX(), pieceBox.minY(),
                pieceBox.getCenter().getZ());

        ChunkPos.rangeClosed(min, max).forEach(chunkPos -> {
            BoundingBox chunkBox = new BoundingBox(
                    chunkPos.getMinBlockX(), MIN_BUILD_HEIGHT, chunkPos.getMinBlockZ(),
                    chunkPos.getMaxBlockX(), MAX_BUILD_HEIGHT, chunkPos.getMaxBlockZ());
            if (!pieceBox.intersects(chunkBox)) {
                return;
            }
            piece.postProcess(level.level(), null, null, RandomSource.create(seed),
                    chunkBox, chunkPos, origin);
        });
    }

    /** Matches FakeWorldGenLevel's own build range. */
    private static final int MIN_BUILD_HEIGHT = -64;
    private static final int MAX_BUILD_HEIGHT = 320;

    /**
     * <strong>Splitting a piece across chunks must not change what it builds.</strong>
     *
     * <p>The seam test, and the reason per-chunk invocation is worth modelling at all. Vanilla calls
     * {@code postProcess} once per chunk a piece spans, so every decision inside it is re-made N
     * times over the same piece, each time with a different clip box. Anything that decides from
     * something chunk-scoped &mdash; the clip box, the chunk position, a level read outside the box
     * &mdash; comes out different on the two sides of a chunk boundary, and the result is a visible
     * discontinuity running dead straight through a corridor at a multiple of 16.</p>
     *
     * <p>Asserted by building each dungeon twice, once per chunk and once with a single box covering
     * the whole piece, and demanding the two worlds are identical. That also pins the invariant
     * {@code PieceProcessors}' two-pass split exists to hold: the neighbour-aware half sees the
     * whole piece, so its answers cannot depend on where the seams fall.</p>
     */
    @Test
    void splittingAPieceAcrossChunksBuildsTheSameThing() {
        List<String> all = new ArrayList<>();
        int total = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            FakeWorldGenLevel chunked = FakeWorldGenLevel.create();
            FakeWorldGenLevel whole = FakeWorldGenLevel.create();

            for (DungeonCorridorPiece piece : pieces(plan(seed))) {
                postProcessPerChunk(piece, chunked, seed);

                BoundingBox box = piece.getBoundingBox();
                whole.level().getClass(); // no-op; keeps the two calls visually parallel
                piece.postProcess(whole.level(), null, null, RandomSource.create(seed), box,
                        new ChunkPos(SectionPos.blockToSectionCoord(box.minX()),
                                SectionPos.blockToSectionCoord(box.minZ())),
                        new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ()));
            }

            List<String> differences = new ArrayList<>();
            Set<BlockPos> positions = new java.util.LinkedHashSet<>(chunked.blocks().keySet());
            positions.addAll(whole.blocks().keySet());
            for (BlockPos pos : positions) {
                BlockState a = chunked.blockAt(pos);
                BlockState b = whole.blockAt(pos);
                if (!a.equals(b)) {
                    differences.add(pos + ": per-chunk " + a + " vs whole-piece " + b);
                }
            }

            all.addAll(differences);
            total += positions.size();
        }

        assertTrue(all.isEmpty(), all.size() + " of " + total
                + " block(s) differ depending on how the piece was split across chunks -- a"
                + " visible seam at a multiple of 16. First few: "
                + all.subList(0, Math.min(12, all.size())));
    }

    /** Guards the test above from being vacuous: it proves nothing if no piece crosses a boundary. */
    @Test
    void someCorridorPieceActuallySpansMoreThanOneChunk() {
        int multiChunk = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            for (DungeonCorridorPiece piece : pieces(plan(seed))) {
                BoundingBox box = piece.getBoundingBox();
                boolean spans = SectionPos.blockToSectionCoord(box.minX())
                        != SectionPos.blockToSectionCoord(box.maxX())
                        || SectionPos.blockToSectionCoord(box.minZ())
                        != SectionPos.blockToSectionCoord(box.maxZ());
                if (spans) {
                    multiChunk++;
                }
            }
        }
        assertTrue(multiChunk > 0,
                "no corridor piece crossed a chunk boundary, so the seam test proved nothing");
    }

    /**
     * That {@code postProcess} completes at all is the headline. The Aug 03 {@code updateShape}
     * crash was a {@code ClassCastException} thrown from inside vanilla's own shape derivation,
     * during {@code settleJoinShapes} — it killed chunk generation outright and nothing in this
     * project could see it coming.
     */
    @Test
    void postProcessCompletesAndWritesBlocks() {
        for (long seed = 0; seed < SEEDS; seed++) {
            FakeWorldGenLevel level = run(seed);
            assertFalse(level.blocks().isEmpty(), "seed " + seed + " wrote no blocks at all");
        }
    }

    /**
     * Every corner shape the generator authored is still there once the block is in the world.
     *
     * <p>Two things could take it away and both are downstream of the generator, so only a
     * postProcess-level check sees either. {@code settleJoinShapes} would recompute it &mdash; a
     * corridor now opts out entirely ({@code DungeonCorridorPiece.settlesJoinShapes}), and this is
     * what pins that opt-out in place. And the weathering pass rewrites the block itself, so it
     * relies on {@code AgingProcessor} carrying {@code facing}/{@code half}/{@code shape} onto the
     * replacement; a vanilla {@code minecraft:rule} processor would drop them, which is why the
     * arch's blocks must never be aged by one.</p>
     */
    @Test
    void authoredArchShapesSurviveSettleJoinShapes() {
        List<String> reset = new ArrayList<>();
        int authored = 0;

        for (long seed = 0; seed < SEEDS; seed++) {
            for (Placed placed : placeAndTrack(seed)) {
                String intendedShape = placed.intended().getValue(StairBlock.SHAPE).getSerializedName();
                if (intendedShape.equals("straight")) {
                    continue; // no opinion -- vanilla is welcome to settle these
                }
                BlockState after = placed.after();
                if (!(after.getBlock() instanceof StairBlock)) {
                    // Weathered out of the stairs family entirely -- deliberate, and a shape
                    // question no longer applies. Where it may and may not land is
                    // anArchHaunchNeverWeathersIntoAHole's business, not this test's.
                    continue;
                }
                authored++;
                String actualShape = after.getValue(StairBlock.SHAPE).getSerializedName();
                if (!actualShape.equals(intendedShape)) {
                    reset.add("seed " + seed + " " + placed.pos() + " " + intendedShape + " -> " + actualShape);
                }
            }
        }

        assertTrue(authored > 0, "the generator authored no corner shapes, so this test proved nothing");
        assertTrue(reset.isEmpty(),
                authored + " authored corner shapes; " + reset.size() + " were reset by settleJoinShapes"
                        + " -- vanilla cannot derive an outer_* for a stair facing into its own wall, so"
                        + " these come back straight and render as the notch reported in game as 'the"
                        + " outers aren't populating'. First few: "
                        + reset.subList(0, Math.min(8, reset.size())));
    }

    /** One block the generator placed, and what the world holds at that position afterwards. */
    private record Placed(BlockPos pos, BlockState intended, BlockState after) {}

    /**
     * Runs every corridor piece's {@code postProcess} and pairs each stair the generator intended
     * with whatever ended up at that position.
     *
     * <p>The intent has to be computed through {@link BasicCorridorGenerator} with the real motif
     * rather than through the piece's own {@code renderPlacements()}: that method has no level, so
     * it cannot reach the registry and falls back to {@code MotifConfig.DEFAULT}, which is
     * {@code flat} and therefore has no arch and no stairs at all. {@code postProcess} resolves the
     * real motif from {@code level.registryAccess()}, so this matches what the piece actually does.</p>
     */
    private static List<Placed> placeAndTrack(long seed) {
        DungeonLayout layout = plan(seed);
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        List<BlockPos> stairPositions = new ArrayList<>();
        List<BlockState> stairStates = new ArrayList<>();

        for (FloorLayout floor : layout.getFloors()) {
            for (CorridorData corridor : floor.getCorridors()) {
                List<BlockPlacement> intended = new ArrayList<>();
                new BasicCorridorGenerator().withMotifConfig(MotifConfigs.load(MOTIF))
                        .build(corridor, floor.getFloorY(), DungeonMotif.CLASSIC,
                                RandomSource.create(seed), intended);
                for (BlockPlacement placement : intended) {
                    BlockState state = BlockStateCodec.resolve(placement);
                    if (state.getBlock() instanceof StairBlock) {
                        stairPositions.add(new BlockPos(ANCHOR_X + placement.getX(),
                                placement.getY(), ANCHOR_Z + placement.getZ()));
                        stairStates.add(state);
                    }
                }

                postProcessPerChunk(new DungeonCorridorPiece(
                        corridor, MOTIF, floor.getFloorY(), floor.getFloorIndex(), ANCHOR_X, ANCHOR_Z), level, seed);
            }
        }

        List<Placed> placed = new ArrayList<>(stairPositions.size());
        for (int i = 0; i < stairPositions.size(); i++) {
            placed.add(new Placed(stairPositions.get(i), stairStates.get(i),
                    level.blockAt(stairPositions.get(i))));
        }
        return placed;
    }

    /**
     * An arch haunch may weather into another stair, or into <strong>dirt</strong> &mdash; the
     * latter is deliberate (see {@code AgingChainRatesTest.stairsDecayToDirtAtTheIntendedRate}): it
     * reads as the stair having fallen out with earth coming through, and it is what gives
     * {@code hanging_growth} something to sprout from in a ceiling.
     *
     * <p>What it must never do is leave a <em>hole</em>. Air or a falling block in the haunch row is
     * an opening in the corridor roof, and the arch row is the one place in a corridor where the
     * ceiling is only one block thick. The stairs chains reach neither today; this asserts that
     * against what actually lands in the world rather than against the authored probabilities,
     * which is the half {@code AgingChainRatesTest} cannot see.</p>
     */
    @Test
    void anArchHaunchNeverWeathersIntoAHole() {
        // dungeonblocks:rubble is deliberately NOT in this set. It replaced gravel as the
        // deep-decay terminus on 2026-08-25 precisely because it is a plain full block that does
        // not fall, so it roofs the haunch row as well as the stone it replaced. Gravel and sand
        // stay listed: they are not placed anywhere today, and this is where reintroducing one
        // would be caught.
        Set<String> holes = Set.of("minecraft:air", "minecraft:gravel", "minecraft:sand");
        List<String> offenders = new ArrayList<>();
        int haunches = 0;

        for (long seed = 0; seed < SEEDS; seed++) {
            for (Placed placed : placeAndTrack(seed)) {
                haunches++;
                String landed = placed.after().getBlock().builtInRegistryHolder()
                        .key().location().toString();
                if (holes.contains(landed)) {
                    offenders.add("seed " + seed + " " + placed.pos() + " -> " + landed);
                }
            }
        }

        assertTrue(haunches > 0, "no arch haunches were generated, so this test proved nothing");
        assertTrue(offenders.isEmpty(),
                haunches + " haunches checked; " + offenders.size() + " became a hole in the corridor"
                        + " roof: " + offenders.subList(0, Math.min(8, offenders.size())));
    }

    /**
     * The weathering pass genuinely ran. Without this the two tests above would still pass with the
     * processor list missing entirely, which is exactly the silent degradation
     * {@link TestRegistries} exists to prevent.
     */
    @Test
    void theWeatheringPassActuallyRan() {
        boolean sawWeathering = false;
        for (long seed = 0; seed < SEEDS && !sawWeathering; seed++) {
            for (BlockState state : run(seed).blocks().values()) {
                String id = state.getBlock().builtInRegistryHolder().key().location().toString();
                if (id.contains("mossy") || id.equals("minecraft:cobblestone")) {
                    sawWeathering = true;
                    break;
                }
            }
        }
        assertTrue(sawWeathering,
                "no weathered block anywhere -- the processor list did not run, so every other "
                        + "assertion in this class is vacuous");
    }
}
