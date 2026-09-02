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

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfigHelper;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives real ROOM pieces through their real {@code postProcess}, against
 * {@link FakeWorldGenLevel} &mdash; the half of backlog #27 that was still open.
 *
 * <h2>Rooms were thought to be unreachable, and are not</h2>
 * <p>The standing note said a room piece cannot be driven headlessly because it spawns entities and
 * entity creation needs a real {@code ServerLevel} the stub cannot fake. That is true of the
 * <em>entity</em> half and stays true; it never blocked the block half. {@code placeEntities}
 * degrades per placement rather than throwing, and headless it does not even get that far &mdash;
 * {@code dungeonblocks} is not on this classpath, so {@code EntityType.byString} fails to resolve
 * the pot ids and {@code EntitySpawner.spawn} returns false with a warning. So a room's blocks, its
 * weathering pass and its {@code settleJoinShapes} all run here today, which is where every defect
 * this harness was built for has lived.</p>
 *
 * <p>The same classpath gap means every {@code dungeonblocks} block resolves to air, exactly as it
 * does in {@link CorridorPostProcessTest}. Assertions here are therefore about structure and
 * invariance, not about which decorative block landed.</p>
 *
 * <p>What is still out of reach, and is not chased: entities actually being constructed, and the
 * already-generated-chunk case (nothing here models a chunk that finished before the structure
 * ran).</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
class RoomPostProcessTest {

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

    /**
     * The room pieces production would emit, built with the shipped {@code sink_offset}.
     *
     * <p>Mirrors {@code DungeonPieceEmitter}: START / END slots belong to the template pieces and a
     * room that drew a Phase 8 prefab is assembled rather than built, so neither is wrapped by this
     * piece. Reading {@code sink_offset} from the datapack rather than passing 0 is what puts pits in
     * front of this test at all &mdash; it ships at 5.</p>
     */
    private static List<DungeonRoomPiece> pieces(DungeonLayout layout) {
        var generationConfig = DungeonGenerationConfigHelper.get(TestRegistries.get());
        int sinkOffset = generationConfig.sinkOffset();
        // #68: and the budget ABOVE the walking plane, which is the other half of the same floor's
        // budget and the cap on a rising vault. Passing only the sink is what the emitter used to
        // do, and leaving it that way here would have this harness build a box the render then
        // writes outside of -- which is exactly what `aRoomNeverWritesOutsideItsOwnBox` reported
        // the moment the mud band authored its first rising vault. Production reads both from the
        // same config; so must this.
        int ceilingBudget = generationConfig.ceilingBudget();
        List<DungeonRoomPiece> pieces = new ArrayList<>();
        for (FloorLayout floor : layout.getFloors()) {
            for (RoomData room : floor.getRooms()) {
                if (room.getRole().isProcedurallyBuilt() && room.getTemplateId() == null) {
                    pieces.add(new DungeonRoomPiece(room, MOTIF, floor.getFloorY(),
                            floor.getFloorIndex(), ANCHOR_X, ANCHOR_Z, sinkOffset, ceilingBudget));
                }
            }
        }
        return pieces;
    }

    /** Plans a dungeon and runs every room piece's postProcess into one shared fake level. */
    private static FakeWorldGenLevel run(long seed) {
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        for (DungeonRoomPiece piece : pieces(plan(seed))) {
            postProcessPerChunk(piece, level, seed);
        }
        return level;
    }

    /**
     * Runs one piece's {@code postProcess} the way the game does: once per chunk its bounding box
     * spans, with the box clipped to that chunk. Mirrors {@code StructureStart.placeInChunk}, and
     * {@code CorridorPostProcessTest.postProcessPerChunk} block for block.
     */
    private static void postProcessPerChunk(DungeonRoomPiece piece, FakeWorldGenLevel level, long seed) {
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
     * That a room's {@code postProcess} completes at all, and writes blocks.
     *
     * <p>The headline, and for rooms it covers strictly more machinery than the corridor equivalent:
     * scheme selection, the floor / wall / ceiling / pillar / platform pattern providers, pits, the
     * decoration and support sweeps, and the weathering pass. None of it had ever been run through
     * the real {@code postProcess} headlessly.</p>
     */
    @Test
    void postProcessCompletesAndWritesBlocks() {
        for (long seed = 0; seed < SEEDS; seed++) {
            FakeWorldGenLevel level = run(seed);
            assertFalse(level.blocks().isEmpty(), "seed " + seed + " wrote no blocks at all");
        }
    }

    /**
     * <strong>Splitting a room across chunks must not change what it builds.</strong>
     *
     * <p>The seam test, and the reason per-chunk invocation is worth modelling. Vanilla calls
     * {@code postProcess} once per chunk a piece spans, so every decision inside it is re-made N
     * times over the same room, each time with a different clip box. A room re-rolls its whole
     * scheme on each of those runs &mdash; which is safe only because the roll is seeded from
     * piece-stable state ({@code DungeonPiece#deterministicRandom}) rather than from the chunk
     * random {@code postProcess} is handed. Take that away and half a room is coffered and the other
     * half is not, along a line at a multiple of 16.</p>
     *
     * <p>Asserted by building each dungeon twice, once per chunk and once with a single box covering
     * the whole piece, and demanding the two worlds are identical.</p>
     */
    @Test
    void splittingARoomAcrossChunksBuildsTheSameThing() {
        List<String> all = new ArrayList<>();
        int total = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            FakeWorldGenLevel chunked = FakeWorldGenLevel.create();
            FakeWorldGenLevel whole = FakeWorldGenLevel.create();

            for (DungeonRoomPiece piece : pieces(plan(seed))) {
                postProcessPerChunk(piece, chunked, seed);

                BoundingBox box = piece.getBoundingBox();
                piece.postProcess(whole.level(), null, null, RandomSource.create(seed), box,
                        new ChunkPos(SectionPos.blockToSectionCoord(box.minX()),
                                SectionPos.blockToSectionCoord(box.minZ())),
                        new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ()));
            }

            Set<BlockPos> positions = new LinkedHashSet<>(chunked.blocks().keySet());
            positions.addAll(whole.blocks().keySet());
            for (BlockPos pos : positions) {
                BlockState a = chunked.blockAt(pos);
                BlockState b = whole.blockAt(pos);
                if (!a.equals(b)) {
                    all.add("seed " + seed + " " + pos.toShortString()
                            + ": per-chunk " + a + " vs whole-piece " + b);
                }
            }
            total += positions.size();
        }

        assertTrue(all.isEmpty(), all.size() + " of " + total
                + " block(s) differ depending on how the room was split across chunks -- a visible"
                + " seam at a multiple of 16. First few: " + all.subList(0, Math.min(12, all.size())));
    }

    /** Guards the test above from being vacuous: it proves nothing if no room crosses a boundary. */
    @Test
    void someRoomPieceActuallySpansMoreThanOneChunk() {
        int multiChunk = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            for (DungeonRoomPiece piece : pieces(plan(seed))) {
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
                "no room piece crossed a chunk boundary, so the seam test proved nothing");
    }

    /**
     * <strong>A room writes nothing outside its own bounding box.</strong>
     *
     * <p>Cheap here and unreachable anywhere else: only {@code postProcess} knows what the box is,
     * and only the world knows where the writes landed. It matters most downward. A pit is dug below
     * the walking plane and the box extends down by {@code sink_offset} to cover it, so a pit that
     * ever out-ran its budget would open a hole into the floor below &mdash; a hole the piece does
     * not own and the room below would not know about. It matters upward and sideways too:
     * {@code spawn_overrides bounding_box: piece} tests against this box, so a block outside it is a
     * block outside the dungeon as far as mob spawning is concerned.</p>
     */
    @Test
    void aRoomNeverWritesOutsideItsOwnBox() {
        List<String> escapes = new ArrayList<>();
        int written = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            for (DungeonRoomPiece piece : pieces(plan(seed))) {
                FakeWorldGenLevel level = FakeWorldGenLevel.create();
                postProcessPerChunk(piece, level, seed);
                BoundingBox box = piece.getBoundingBox();
                for (BlockPos pos : level.blocks().keySet()) {
                    written++;
                    if (!box.isInside(pos)) {
                        escapes.add("seed " + seed + " " + pos.toShortString() + " outside " + box);
                    }
                }
            }
        }
        assertTrue(written > 0, "no room wrote anything, so this test proved nothing");
        assertTrue(escapes.isEmpty(), escapes.size() + " of " + written
                + " block(s) landed outside the writing room's own bounding box. First few: "
                + escapes.subList(0, Math.min(8, escapes.size())));
    }

    /**
     * The weathering pass genuinely ran on a room.
     *
     * <p>Without this every test above would still pass with the room's processor list missing
     * entirely &mdash; the silent degradation {@link TestRegistries} exists to prevent. Keyed on
     * vanilla blocks only, because the {@code dungeonblocks} half of the weathering table resolves
     * to air on this classpath and would make the check depend on which mod jar is present.</p>
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
                "no room block was weathered in any seed -- the processor list is not being applied");
    }
}
