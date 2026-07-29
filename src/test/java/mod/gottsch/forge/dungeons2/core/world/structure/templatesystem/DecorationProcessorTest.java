/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data.AgingRule;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data.AgingStage;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data.BlockMatch;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data.DecorationRule;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data.WallGrowthRule;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link DecorationProcessor}, the neighbour-aware part of the decoration pass.
 *
 * <p>Every test runs it against a {@code null} {@code ServerLevelAccessor}. That is not a
 * shortcut &mdash; it <em>is</em> the contract. A {@link LevelIndependentProcessor} runs over
 * a procedural piece's whole block list, which spans chunks the current
 * {@code WorldGenRegion} does not cover, so touching the level at all would be illegal. A
 * test that passed a mock level would stop enforcing that.</p>
 *
 * <p>Behaviours keyed on a {@link BlockMatch} are tested through its {@code blocks} list, not
 * its {@code tags}: {@code BlockState#is(TagKey)} answers from the block's holder, and a bare
 * {@code Bootstrap.bootStrap()} has no server to bind tags, so every tag match would be
 * false.</p>
 *
 * @author Mark Gottschling on Jul 28, 2026
 */
class DecorationProcessorTest {

    private static final StructurePlaceSettings SETTINGS = new StructurePlaceSettings();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- builders -----------------------------------------------------------------

    private static DecorationRule rule(float probability, Block... blocks) {
        return new DecorationRule(probability, List.of(blocks));
    }

    private static WallGrowthRule wall(float probability, float bonus, float max, Block... blocks) {
        return new WallGrowthRule(probability, bonus, max, List.of(blocks));
    }

    private static BlockMatch match(Block... blocks) {
        return new BlockMatch(List.of(blocks), List.of());
    }

    /** Only cobwebs configured. */
    private static DecorationProcessor cobwebs(float probability) {
        return new DecorationProcessor(rule(probability, Blocks.COBWEB), WallGrowthRule.NONE,
                BlockMatch.NONE, DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE,
                DecorationRule.NONE, BlockMatch.NONE);
    }

    /** Only wall growth configured. */
    private static DecorationProcessor growth(WallGrowthRule wallGrowth) {
        return new DecorationProcessor(DecorationRule.NONE, wallGrowth, BlockMatch.NONE,
                DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE,
                DecorationRule.NONE, BlockMatch.NONE);
    }

    /** Only the dirt behaviours configured. */
    private static DecorationProcessor dirt(BlockMatch dirt, DecorationRule floor, DecorationRule hanging) {
        return new DecorationProcessor(DecorationRule.NONE, WallGrowthRule.NONE, dirt,
                floor, hanging, DecorationRule.NONE, DecorationRule.NONE, BlockMatch.NONE);
    }

    /** Only the water behaviours configured. */
    private static DecorationProcessor water(DecorationRule underwater, DecorationRule floating) {
        return new DecorationProcessor(DecorationRule.NONE, WallGrowthRule.NONE, BlockMatch.NONE,
                DecorationRule.NONE, DecorationRule.NONE, underwater, floating, BlockMatch.NONE);
    }

    /** Only unsupported-block removal configured. */
    private static DecorationProcessor unsupported(BlockMatch unsupported) {
        return new DecorationProcessor(DecorationRule.NONE, WallGrowthRule.NONE, BlockMatch.NONE,
                DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE,
                DecorationRule.NONE, unsupported);
    }

    // ---- harness ------------------------------------------------------------------

    private static StructureTemplate.StructureBlockInfo info(BlockPos pos, BlockState state) {
        return new StructureTemplate.StructureBlockInfo(pos, state, null);
    }

    private static StructureTemplate.StructureBlockInfo info(BlockPos pos, Block block) {
        return info(pos, block.defaultBlockState());
    }

    private static List<StructureTemplate.StructureBlockInfo> run(
            DecorationProcessor processor, List<StructureTemplate.StructureBlockInfo> blocks) {
        return run(processor, blocks, SETTINGS);
    }

    private static List<StructureTemplate.StructureBlockInfo> run(
            DecorationProcessor processor, List<StructureTemplate.StructureBlockInfo> blocks,
            StructurePlaceSettings settings) {
        // null level: see the class doc.
        return processor.finalizeProcessing(null, BlockPos.ZERO, BlockPos.ZERO, blocks, blocks, settings);
    }

    private static Map<BlockPos, BlockState> byPos(List<StructureTemplate.StructureBlockInfo> blocks) {
        Map<BlockPos, BlockState> map = new HashMap<>();
        blocks.forEach(info -> map.put(info.pos(), info.state()));
        return map;
    }

    /**
     * A one-block-thick wall along X at {@code z = 0}, with the cells at {@code z = 1} air.
     * Roughly the shape a corridor's north wall gives the processor.
     */
    private static List<StructureTemplate.StructureBlockInfo> wallWithAirInFront(int length) {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            blocks.add(info(new BlockPos(x, 64, 0), Blocks.STONE_BRICKS));
            blocks.add(info(new BlockPos(x, 64, 1), Blocks.AIR));
        }
        return blocks;
    }

    private static long count(List<StructureTemplate.StructureBlockInfo> blocks, Block block) {
        return blocks.stream().filter(info -> info.state().is(block)).count();
    }

    // ---- cobwebs ------------------------------------------------------------------

    @Test
    void cobwebsOnlyGoWhereSomethingSolidIsAdjacent() {
        // The point of the whole processor: the decision needs the NEIGHBOURS, not the
        // block. Two air blocks, identical states -- one beside a wall, one floating.
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 64, 0), Blocks.STONE_BRICKS),
                info(new BlockPos(1, 64, 0), Blocks.AIR),   // beside the wall
                info(new BlockPos(8, 64, 0), Blocks.AIR));  // out in the open

        Map<BlockPos, BlockState> out = byPos(run(cobwebs(1.0F), blocks));

        assertEquals(Blocks.COBWEB, out.get(new BlockPos(1, 64, 0)).getBlock());
        assertTrue(out.get(new BlockPos(8, 64, 0)).isAir(),
                "An air block with no solid neighbour has nothing to web onto");
        assertEquals(Blocks.STONE_BRICKS, out.get(new BlockPos(0, 64, 0)).getBlock(),
                "The wall itself must not be replaced");
    }

    @Test
    void cobwebsDoNotAnchorToNeighboursOutsideThePiece() {
        // Only blocks in the list count as neighbours. Anything else would mean reading
        // the level, which this processor is not allowed to do.
        List<StructureTemplate.StructureBlockInfo> blocks =
                List.of(info(new BlockPos(1, 64, 0), Blocks.AIR));

        assertTrue(byPos(run(cobwebs(1.0F), blocks)).get(new BlockPos(1, 64, 0)).isAir());
    }

    // ---- wall growth --------------------------------------------------------------

    @Test
    void growthAttachesToTheFaceOfTheWallItGrewOn() {
        // The growth sits in the AIR block and clings to the wall, so its face property
        // is the direction from the air towards the wall -- not the other way round.
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 64, 0), Blocks.STONE_BRICKS),
                info(new BlockPos(0, 64, 1), Blocks.AIR));

        BlockState grown = byPos(run(growth(wall(1.0F, 0.0F, 1.0F, Blocks.GLOW_LICHEN)), blocks))
                .get(new BlockPos(0, 64, 1));

        assertEquals(Blocks.GLOW_LICHEN, grown.getBlock());
        // The wall is NORTH of the air block.
        assertTrue(grown.getValue(MultifaceBlock.getFaceProperty(Direction.NORTH)));
        assertFalse(grown.getValue(MultifaceBlock.getFaceProperty(Direction.SOUTH)));
    }

    @Test
    void growthFaceSurvivesRotationAndMirroring() {
        // Processors see world POSITIONS but pre-transform STATES: vanilla applies
        // state.mirror(m).rotate(r) after processing. So the face has to be stored
        // inverse-transformed, or a rotated prefab grows lichen on the wrong side of the
        // air block (which renders as lichen floating against nothing).
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 64, 0), Blocks.STONE_BRICKS),
                info(new BlockPos(0, 64, 1), Blocks.AIR));

        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                StructurePlaceSettings settings =
                        new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
                BlockState stored = byPos(run(
                        growth(wall(1.0F, 0.0F, 1.0F, Blocks.GLOW_LICHEN)), blocks, settings))
                        .get(new BlockPos(0, 64, 1));

                // Exactly what placeInWorld does on the way into the world.
                BlockState placed = stored.mirror(mirror).rotate(rotation);

                assertTrue(placed.getValue(MultifaceBlock.getFaceProperty(Direction.NORTH)),
                        "rotation=" + rotation + " mirror=" + mirror
                                + " lost the north face after placement");
            }
        }
    }

    @Test
    void growthOnlyClingsToFullCubes() {
        // Reported in game: lichen appearing to grow in mid-air. Cause -- the anchor test was
        // `canOcclude()`, which is a LIGHT-occlusion flag, not a shape test, and is true for
        // stairs, slabs, walls, fences and DungeonBlocks' facade/pillar/corbel shapes. None
        // of those fill their cell, so growth placed against one hangs in the open beside it.
        // OAK_DOOR stands in for dungeonblocks:spruce_dungeon_door -- reported in game as
        // growth appearing on doors. A door is not a full cube, so this covers it.
        for (Block anchor : List.of(Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
                Blocks.STONE_BRICK_WALL, Blocks.OAK_FENCE, Blocks.IRON_BARS, Blocks.CHAIN,
                Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR, Blocks.IRON_DOOR)) {
            List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                    info(new BlockPos(0, 64, 0), anchor),
                    info(new BlockPos(0, 64, 1), Blocks.AIR));

            List<StructureTemplate.StructureBlockInfo> out =
                    run(growth(wall(1.0F, 0.0F, 1.0F, Blocks.GLOW_LICHEN)), blocks);

            assertEquals(0, count(out, Blocks.GLOW_LICHEN),
                    anchor + " does not fill its cell, so growth against it would hang in mid-air");
        }
    }

    @Test
    void aPartialBlockStillAnchorsACobweb() {
        // The looser test is kept where the block only has to EXIST. A cobweb strung across
        // a stair tread is fine; it isn't clinging to a face.
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 64, 0), Blocks.STONE_BRICK_STAIRS),
                info(new BlockPos(0, 64, 1), Blocks.AIR));

        assertEquals(1, count(run(cobwebs(1.0F), blocks), Blocks.COBWEB));
    }

    @Test
    void canOccludeIsNotAShapeTest() {
        // Pinning the vanilla fact the bug rested on, so nobody "simplifies" isFullCube back
        // to canOcclude(). If this ever fails, Mojang changed the flag's meaning.
        assertTrue(Blocks.STONE_BRICK_STAIRS.defaultBlockState().canOcclude(),
                "A stair reports canOcclude() -- which is exactly why it is the wrong test");
        assertTrue(Blocks.STONE_BRICK_SLAB.defaultBlockState().canOcclude());

        // ...whereas the shape test separates them, without touching a real level.
        assertFalse(Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        assertTrue(Blocks.STONE_BRICKS.defaultBlockState()
                .isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    @Test
    void growthClustersInsteadOfSpeckling() {
        // The genuinely good idea in VD's version: base chance low, bonus per adjacent
        // growth block high, so growth spreads in patches. With base 0 and a bonus, a
        // patch can only exist if it seeds off an existing one -- so nothing grows.
        List<StructureTemplate.StructureBlockInfo> out = run(
                growth(wall(0.0F, 0.9F, 1.0F, Blocks.GLOW_LICHEN)), wallWithAirInFront(16));

        assertEquals(0, count(out, Blocks.GLOW_LICHEN),
                "With a zero base chance there is no first growth block to cluster around");
    }

    @Test
    void clusteringRaisesTheChanceOfNeighbouringCells() {
        // Same wall, same seeds, differing only in the clustering bonus. Rolls are keyed
        // on position, so the bonus can only ever ADD growth -- it moves the threshold,
        // not the number. Long enough a wall that the difference is not a coin flip.
        List<StructureTemplate.StructureBlockInfo> wall = wallWithAirInFront(512);

        long sparse = count(run(growth(wall(0.05F, 0.0F, 1.0F, Blocks.GLOW_LICHEN)), wall),
                Blocks.GLOW_LICHEN);
        long clustered = count(run(growth(wall(0.05F, 0.4F, 1.0F, Blocks.GLOW_LICHEN)), wall),
                Blocks.GLOW_LICHEN);

        assertTrue(sparse > 0, "The base chance should seed some growth on a 512-long wall");
        assertTrue(clustered > sparse,
                "Clustering bonus produced no extra growth (" + clustered + " vs " + sparse + ")");
    }

    @Test
    void aClusterIsAllOneSpecies() {
        // A candidate inherits the species of the growth already touching it, so a patch
        // reads as one organism rather than a three-colour mosaic.
        List<StructureTemplate.StructureBlockInfo> out = run(
                growth(wall(1.0F, 0.0F, 1.0F, Blocks.GLOW_LICHEN, Blocks.VINE)),
                wallWithAirInFront(24));

        List<Block> species = out.stream()
                .filter(info -> !info.state().is(Blocks.STONE_BRICKS) && !info.state().isAir())
                .map(info -> info.state().getBlock())
                .distinct()
                .toList();

        assertEquals(1, species.size(),
                "A contiguous run of growth should be a single species, got " + species);
    }

    // ---- dirt: floor and hanging growth -------------------------------------------

    @Test
    void floorGrowthGoesAboveDirtAndHangingGrowthBelowIt() {
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 65, 0), Blocks.AIR),      // above
                info(new BlockPos(0, 64, 0), Blocks.DIRT),
                info(new BlockPos(0, 63, 0), Blocks.AIR));     // below

        Map<BlockPos, BlockState> out = byPos(run(dirt(match(Blocks.DIRT),
                rule(1.0F, Blocks.BROWN_MUSHROOM), rule(1.0F, Blocks.HANGING_ROOTS)), blocks));

        assertEquals(Blocks.BROWN_MUSHROOM, out.get(new BlockPos(0, 65, 0)).getBlock());
        assertEquals(Blocks.HANGING_ROOTS, out.get(new BlockPos(0, 63, 0)).getBlock());
        assertEquals(Blocks.DIRT, out.get(new BlockPos(0, 64, 0)).getBlock(),
                "The dirt itself is the anchor, not the target");
    }

    @Test
    void dirtGrowthNeedsAirToGrowInto() {
        // Buried dirt -- solid above and below -- grows nothing. This is the rule that
        // stops growth appearing inside a wall.
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 65, 0), Blocks.STONE_BRICKS),
                info(new BlockPos(0, 64, 0), Blocks.DIRT),
                info(new BlockPos(0, 63, 0), Blocks.STONE_BRICKS));

        List<StructureTemplate.StructureBlockInfo> out = run(dirt(match(Blocks.DIRT),
                rule(1.0F, Blocks.BROWN_MUSHROOM), rule(1.0F, Blocks.HANGING_ROOTS)), blocks);

        assertSame(blocks, out, "Buried dirt should not have produced any replacement at all");
    }

    @Test
    void aBlockOutsideTheDirtMatchGrowsNothing() {
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 65, 0), Blocks.AIR),
                info(new BlockPos(0, 64, 0), Blocks.STONE_BRICKS));

        assertSame(blocks, run(dirt(match(Blocks.DIRT), rule(1.0F, Blocks.BROWN_MUSHROOM),
                DecorationRule.NONE), blocks));
    }

    // ---- water --------------------------------------------------------------------

    @Test
    void seagrassReplacesWaterStandingOnASolidFloor() {
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 63, 0), Blocks.STONE_BRICKS),
                info(new BlockPos(0, 64, 0), Blocks.WATER),
                info(new BlockPos(0, 65, 0), Blocks.AIR));

        Map<BlockPos, BlockState> out = byPos(run(
                water(rule(1.0F, Blocks.SEAGRASS), rule(1.0F, Blocks.LILY_PAD)), blocks));

        // Underwater growth is the one behaviour that overwrites something other than air.
        assertEquals(Blocks.SEAGRASS, out.get(new BlockPos(0, 64, 0)).getBlock());
        assertEquals(Blocks.LILY_PAD, out.get(new BlockPos(0, 65, 0)).getBlock());
    }

    @Test
    void seagrassNeedsAFloorButLilyPadsDoNot() {
        // Water with nothing under it (the middle of a column) grows no seagrass, but the
        // surface still takes a lily pad.
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 64, 0), Blocks.WATER),
                info(new BlockPos(0, 65, 0), Blocks.AIR));

        Map<BlockPos, BlockState> out = byPos(run(
                water(rule(1.0F, Blocks.SEAGRASS), rule(1.0F, Blocks.LILY_PAD)), blocks));

        assertEquals(Blocks.WATER, out.get(new BlockPos(0, 64, 0)).getBlock());
        assertEquals(Blocks.LILY_PAD, out.get(new BlockPos(0, 65, 0)).getBlock());
    }

    // ---- unsupported blocks -------------------------------------------------------

    // A ladder stands in for a corbel/ledge: it carries the same `facing` property and
    // attaches by the same convention (vanilla LadderBlock#canSurvive checks
    // facing.getOpposite()), so the test doesn't need a DungeonBlocks block to be registered.
    private static final BlockPos LEDGE = new BlockPos(5, 64, 0);
    private static final Direction LEDGE_FACING = Direction.NORTH;
    /** Where a NORTH-facing wall-mounted block's wall is: behind it, to the south. */
    private static final BlockPos BEHIND_LEDGE = LEDGE.south();

    private static StructureTemplate.StructureBlockInfo ledge() {
        return info(LEDGE, Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, LEDGE_FACING));
    }

    /** The ledge plus air on every side and below — the wall behind it included. */
    private static List<StructureTemplate.StructureBlockInfo> ledgeWithAirAllRound() {
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        blocks.add(ledge());
        for (Direction direction : Direction.values()) {
            blocks.add(info(LEDGE.relative(direction), Blocks.AIR));
        }
        return blocks;
    }

    /** Replaces whatever is at {@code at} with {@code block}. */
    private static List<StructureTemplate.StructureBlockInfo> with(
            List<StructureTemplate.StructureBlockInfo> blocks, BlockPos at, Block block) {
        List<StructureTemplate.StructureBlockInfo> copy = new ArrayList<>(blocks);
        copy.replaceAll(info -> info.pos().equals(at) ? info(at, block) : info);
        return copy;
    }

    @Test
    void aWallMountedBlockLosingTheWallBehindItDeletesItself() {
        Map<BlockPos, BlockState> out =
                byPos(run(unsupported(match(Blocks.LADDER)), ledgeWithAirAllRound()));

        assertTrue(out.get(LEDGE).isAir(),
                "Nothing behind it, so nothing is holding it up");
    }

    @Test
    void onlyTheBlockBEHINDCountsAsSupport() {
        // The rule this behaviour turns on. A corbel is bracketed onto a wall and juts out
        // from it: it GIVES support to what sits on top, it doesn't TAKE support from there,
        // nor from the block below, nor from whatever happens to sit beside it. So a solid
        // block in any direction except behind must not save it.
        for (Direction direction : Direction.values()) {
            if (direction == LEDGE_FACING.getOpposite()) {
                continue;  // that one IS behind; covered below
            }
            Map<BlockPos, BlockState> out = byPos(run(unsupported(match(Blocks.LADDER)),
                    with(ledgeWithAirAllRound(), LEDGE.relative(direction), Blocks.STONE_BRICKS)));

            assertTrue(out.get(LEDGE).isAir(),
                    "Stone to the " + direction + " is not what holds a wall-mounted block up");
        }
    }

    @Test
    void anythingBehindItIsSupport() {
        // Support is "not air", NOT "is a full cube". Testing for a full cube -- which is
        // what VD's isSolidRender amounts to -- would delete every ledge mounted on a stair,
        // a slab or another ledge, all perfectly good architecture. Deleting authored
        // geometry is the expensive mistake here, so each of these must survive.
        List<Block> anchors = List.of(
                Blocks.STONE_BRICKS,              // a plain wall
                Blocks.STONE_BRICK_STAIRS,        // not a full cube
                Blocks.STONE_BRICK_SLAB,
                Blocks.STONE_BRICK_WALL,
                Blocks.GLOW_LICHEN,               // even something decoration itself placed
                Blocks.WATER);

        for (Block anchor : anchors) {
            Map<BlockPos, BlockState> out = byPos(run(unsupported(match(Blocks.LADDER)),
                    with(ledgeWithAirAllRound(), BEHIND_LEDGE, anchor)));

            assertEquals(Blocks.LADDER, out.get(LEDGE).getBlock(),
                    "A ledge backed by " + anchor + " is supported and must be kept");
        }
    }

    @Test
    void supportIsLookedForInWorldSpaceNotTemplateSpace() {
        // Processors see world POSITIONS but pre-transform STATES, so a stored facing has to
        // be transformed FORWARD to find the right neighbour -- the mirror image of what
        // wall growth does. stairs_1.nbt places a ledge and is jigsaw-placed at an arbitrary
        // rotation, so getting this wrong deletes ledges out of rotated prefabs only.
        for (Rotation rotation : Rotation.values()) {
            for (Mirror mirror : Mirror.values()) {
                StructurePlaceSettings settings =
                        new StructurePlaceSettings().setRotation(rotation).setMirror(mirror);
                // Where the stored NORTH facing actually points once placed.
                Direction world = rotation.rotate(mirror.mirror(LEDGE_FACING));
                BlockPos wall = LEDGE.relative(world.getOpposite());

                Map<BlockPos, BlockState> out = byPos(run(unsupported(match(Blocks.LADDER)),
                        with(ledgeWithAirAllRound(), wall, Blocks.STONE_BRICKS), settings));

                assertEquals(Blocks.LADDER, out.get(LEDGE).getBlock(),
                        "rotation=" + rotation + " mirror=" + mirror
                                + ": the wall is at " + wall + " once placed, so the ledge stays");
            }
        }
    }

    @Test
    void aBlockWithNoFacingFallsBackToLookingAllRound() {
        // No facing means no "behind" to test, so the generous rule stands: kept unless air
        // is seen on every side. A slab has `type` and `waterlogged`, no `facing`.
        List<StructureTemplate.StructureBlockInfo> allAir = new ArrayList<>();
        allAir.add(info(LEDGE, Blocks.STONE_BRICK_SLAB));
        for (Direction direction : Direction.values()) {
            allAir.add(info(LEDGE.relative(direction), Blocks.AIR));
        }

        assertTrue(byPos(run(unsupported(match(Blocks.STONE_BRICK_SLAB)), allAir)).get(LEDGE).isAir(),
                "Air on every side, so nothing is touching it");
        assertEquals(Blocks.STONE_BRICK_SLAB,
                byPos(run(unsupported(match(Blocks.STONE_BRICK_SLAB)),
                        with(allAir, LEDGE.east(), Blocks.STONE_BRICKS))).get(LEDGE).getBlock(),
                "Something is touching it, and with no facing we can't say it doesn't count");
    }

    @Test
    void aNeighbourOutsideThePieceCountsAsSupport() {
        // Absent means "this piece places nothing here", not "here is nothing" -- the wall
        // may belong to the adjoining piece, or lie outside a prefab's bounds. Only air we
        // can positively see counts against the block.
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(ledge());

        assertSame(blocks, run(unsupported(match(Blocks.LADDER)), blocks),
                "A ledge whose surroundings are simply unknown must be left alone");
    }

    @Test
    void removalHappensBeforeGrowthSoAVacatedCellCanBeDecorated() {
        // Phase ordering, and the reason it isn't just cosmetic: the cell the ledge gave up
        // is air by the time cobwebs are decided.
        List<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>(ledgeWithAirAllRound());
        // A wall to the EAST: not behind, so it doesn't save the ledge, but it does anchor a
        // cobweb in the cell the ledge vacates.
        blocks = with(blocks, LEDGE.east(), Blocks.STONE_BRICKS);

        DecorationProcessor processor = new DecorationProcessor(
                rule(1.0F, Blocks.COBWEB), WallGrowthRule.NONE, BlockMatch.NONE,
                DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE,
                DecorationRule.NONE, match(Blocks.LADDER));

        Map<BlockPos, BlockState> out = byPos(run(processor, blocks));

        // Both phases landed on the same cell: structural removal cleared the ledge, and the
        // growth phase then treated the cell as the air it had become and webbed it. Running
        // growth first would have found a ladder there and skipped it.
        assertEquals(Blocks.COBWEB, out.get(LEDGE).getBlock(),
                "The vacated cell should have been decorated in the same call");
    }

    // ---- cross-cutting guarantees -------------------------------------------------

    @Test
    void decisionsAreKeyedOnPositionSoChunkSeamsAgree() {
        // The real reason this can't use level.getRandom(): a procedural piece is
        // processed once per chunk it overlaps. Processing a slice must give the same
        // answer for the blocks in it as processing the whole piece did.
        List<StructureTemplate.StructureBlockInfo> wall = wallWithAirInFront(32);
        DecorationProcessor processor = new DecorationProcessor(
                rule(0.3F, Blocks.COBWEB), wall(0.3F, 0.0F, 1.0F, Blocks.GLOW_LICHEN),
                BlockMatch.NONE, DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE,
                DecorationRule.NONE, BlockMatch.NONE);

        Map<BlockPos, BlockState> whole = byPos(run(processor, wall));

        // The right-hand half, exactly as a second chunk pass would present it. The wall
        // is uniform, so the neighbour map of this slice is complete for every block in
        // it -- which is the condition PieceProcessors guarantees by never clipping
        // before this pass.
        BoundingBox slice = new BoundingBox(16, 0, -1, 31, 255, 2);
        Map<BlockPos, BlockState> half = byPos(run(processor,
                wall.stream().filter(info -> slice.isInside(info.pos())).toList()));

        assertFalse(half.isEmpty());
        half.forEach((pos, state) -> assertEquals(whole.get(pos), state,
                "Block at " + pos + " decorated differently in the two passes"));
    }

    @Test
    void behavioursAtTheSamePositionRollIndependently() {
        // Cobwebs and growth both write into air. Without a per-behaviour salt on the
        // position seed they would draw the identical first float, so a spot webbed at
        // p=0.5 would be exactly the set of spots grown at p=0.5.
        List<StructureTemplate.StructureBlockInfo> wall = wallWithAirInFront(64);

        List<BlockPos> webbed = positionsOf(run(cobwebs(0.5F), wall), Blocks.COBWEB);
        List<BlockPos> grown = positionsOf(
                run(growth(wall(0.5F, 0.0F, 1.0F, Blocks.GLOW_LICHEN)), wall), Blocks.GLOW_LICHEN);

        assertFalse(webbed.isEmpty());
        assertFalse(grown.isEmpty());
        assertFalse(webbed.equals(grown), "Cobweb and growth rolls are perfectly correlated");
    }

    @Test
    void aProcessorWithNothingConfiguredIsAPassThrough() {
        // The default codec values are all off, so an author who adds the processor
        // without setting anything gets no surprise decoration -- and no copied list.
        List<StructureTemplate.StructureBlockInfo> wall = wallWithAirInFront(8);
        DecorationProcessor idle = new DecorationProcessor(
                DecorationRule.NONE, WallGrowthRule.NONE, BlockMatch.NONE, DecorationRule.NONE,
                DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE, BlockMatch.NONE);

        assertSame(wall, run(idle, wall));
    }

    @Test
    void aRuleWithAProbabilityButNoPaletteIsInert() {
        // Palettes name blocks from other mods, so "off" has to be expressible without
        // naming any. A probability alone must not fire and pick from an empty list.
        List<StructureTemplate.StructureBlockInfo> wall = wallWithAirInFront(8);
        DecorationProcessor paletteless = new DecorationProcessor(
                new DecorationRule(1.0F, List.of()), WallGrowthRule.NONE, BlockMatch.NONE,
                DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE,
                DecorationRule.NONE, BlockMatch.NONE);

        assertSame(wall, run(paletteless, wall));
    }

    @Test
    void solidBlocksAreNeverReplacedByDecoration() {
        // Decoration only ever writes into air (or, for underwater growth, water). The
        // shell of a room must survive whatever is turned on.
        List<StructureTemplate.StructureBlockInfo> wall = wallWithAirInFront(32);
        DecorationProcessor everything = new DecorationProcessor(
                rule(1.0F, Blocks.COBWEB), wall(1.0F, 0.0F, 1.0F, Blocks.GLOW_LICHEN),
                match(Blocks.STONE_BRICKS), rule(1.0F, Blocks.BROWN_MUSHROOM),
                rule(1.0F, Blocks.HANGING_ROOTS), rule(1.0F, Blocks.SEAGRASS),
                rule(1.0F, Blocks.LILY_PAD), BlockMatch.NONE);

        assertEquals(32, count(run(everything, wall), Blocks.STONE_BRICKS),
                "Every wall block should still be there");
    }

    @Test
    void agingOutputIsVisibleToDecorationInTheSamePass() {
        // Why AgingProcessor is marked LevelIndependentProcessor too. Both land in
        // PieceProcessors' unclipped pass, so they go through ONE processBlockInfos call:
        // vanilla runs every processor's processBlock over the whole list first, then each
        // finalizeProcessing in turn. Aging therefore turns the stone to dirt BEFORE
        // decoration looks for dirt to grow on -- which is the only reason floor growth
        // does anything at all in classic, where no dirt is ever authored.
        //
        // Also, incidentally, a live check that a whole pass runs against a null level.
        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.addProcessor(new AgingProcessor(1, List.of(new AgingRule(
                Blocks.STONE_BRICKS, List.of(new AgingStage(Blocks.DIRT, 1.0))))));
        settings.addProcessor(dirt(match(Blocks.DIRT), rule(1.0F, Blocks.BROWN_MUSHROOM),
                DecorationRule.NONE));

        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 64, 0), Blocks.STONE_BRICKS),
                info(new BlockPos(0, 65, 0), Blocks.AIR));

        Map<BlockPos, BlockState> out = byPos(StructureTemplate.processBlockInfos(
                null, BlockPos.ZERO, BlockPos.ZERO, settings, blocks, null));

        assertEquals(Blocks.DIRT, out.get(new BlockPos(0, 64, 0)).getBlock(),
                "Aging should have run first");
        assertEquals(Blocks.BROWN_MUSHROOM, out.get(new BlockPos(0, 65, 0)).getBlock(),
                "Decoration should have seen the dirt aging produced, not the original stone");
    }

    @Test
    void decorationDoesNotSeeAgingFromAnEarlierSeparatePass() {
        // The negative of the test above, pinning down why the pass split is on "reads the
        // level" and not on "is neighbour-aware". Run separately -- decoration first, as it
        // would if aging were left in the clipped pass -- and the mushroom never appears.
        List<StructureTemplate.StructureBlockInfo> blocks = List.of(
                info(new BlockPos(0, 64, 0), Blocks.STONE_BRICKS),
                info(new BlockPos(0, 65, 0), Blocks.AIR));

        List<StructureTemplate.StructureBlockInfo> decoratedFirst = run(
                dirt(match(Blocks.DIRT), rule(1.0F, Blocks.BROWN_MUSHROOM), DecorationRule.NONE),
                blocks);

        assertSame(blocks, decoratedFirst,
                "Decoration ahead of aging has no dirt to find -- this is the ordering the"
                        + " LevelIndependentProcessor marker on AgingProcessor prevents");
    }

    private static List<BlockPos> positionsOf(
            List<StructureTemplate.StructureBlockInfo> blocks, Block block) {
        return blocks.stream().filter(info -> info.state().is(block))
                .map(StructureTemplate.StructureBlockInfo::pos).toList();
    }
}
