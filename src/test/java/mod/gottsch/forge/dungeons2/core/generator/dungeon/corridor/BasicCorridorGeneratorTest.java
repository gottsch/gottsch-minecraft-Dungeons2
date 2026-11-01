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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.config.CorridorConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StairBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicCorridorGeneratorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A 7x7 grid with a 3-cell straight corridor in the middle (the rest is rock/wall). */
    private Grid2D simpleGrid() {
        Grid2D grid = new Grid2D(7, 7);
        // Grid is initialized with WALL borders and ROCK interior. Carve a corridor.
        for (int x = 2; x <= 4; x++) {
            grid.get(x, 3).setType(CellType.CORRIDOR);
            grid.get(x, 3).setRegionId(10);
        }
        return grid;
    }

    private CorridorData simpleCorridor() {
        CorridorData c = new CorridorData(10);
        c.getCells().add(new Coords2D(2, 3));
        c.getCells().add(new Coords2D(3, 3));
        c.getCells().add(new Coords2D(4, 3));
        return c;
    }

    @Test
    void corridorEmitsFloorAndAirColumnPerCell() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60,
                DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // Per corridor cell: 1 floor + 3 air = 4 placements.
        // Per cell also: up to 8 wall neighbors. For 3-cell line, neighbor walls
        // are deduped. We just verify the corridor cells got their 4 each.
        long corridorCellPlacements = out.stream()
                .filter(bp -> bp.getY() >= 60 && bp.getY() <= 63
                        && bp.getX() >= 2 && bp.getX() <= 4 && bp.getZ() == 3)
                .count();
        assertEquals(3 * 4, corridorCellPlacements,
                "Each corridor cell should have 1 floor + 3 air = 4 placements");
    }

    @Test
    void corridorWallsAreEmittedAroundCorridor() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60,
                DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // For corridor at (2..4, 3), neighbor cells include (1..5, 2), (1..5, 3), (1..5, 4)
        // minus the corridor itself. All neighbors are WALL/ROCK in this grid.
        // Each wall cell emits 5 vertical blocks (y=60..64).
        boolean sawWallColumn = false;
        for (BlockPlacement bp : out) {
            if (bp.getX() == 1 && bp.getZ() == 3 && bp.getY() == 62) {
                sawWallColumn = true;
                break;
            }
        }
        assertTrue(sawWallColumn,
                "Expected a wall column to the west of corridor at (1,*,3)");
    }

    /**
     * The corridor half of the lichen-on-doors fix: a bordering DOOR cell must
     * not carry a full cube at the two door-half levels, or the corridor's
     * decoration pass anchors lichen there facing the door cell and the lichen
     * ends up rendered onto the door {@code DungeonDoorPiece} carves in later.
     */
    @Test
    void aBorderingDoorCellIsPiercedAtTheTwoDoorHalfLevels() {
        Grid2D grid = simpleGrid();
        grid.get(1, 3).setType(CellType.DOOR); // west end of the corridor run

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertColumnIsPierced(out, 1, 3, 60);
    }

    /** A CONNECTOR is still a plain wall — no door piece follows it, so no hole. */
    @Test
    void aBorderingConnectorCellStaysSolid() {
        Grid2D grid = simpleGrid();
        grid.get(1, 3).setType(CellType.CONNECTOR);

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        for (BlockPlacement bp : out) {
            if (bp.getX() == 1 && bp.getZ() == 3) {
                assertEquals(false, "minecraft:air".equals(bp.getBlockId()),
                        "connector column must stay solid: " + bp);
            }
        }
    }

    /**
     * The grid-free overload (what the deserialized piece uses) must agree with
     * the grid-based one, reading door cells off {@code CorridorData}.
     */
    @Test
    void theGridFreeOverloadPiercesDoorCellsToo() {
        CorridorData corridor = simpleCorridor();
        corridor.getDoorCells().add(new Coords2D(1, 3));

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(corridor, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertColumnIsPierced(out, 1, 3, 60);
    }

    /** Air at floorY+1 / floorY+2, solid everywhere else, exactly {@code height} blocks. */
    private static void assertColumnIsPierced(List<BlockPlacement> out, int x, int z, int floorY) {
        assertColumnIsPierced(out, x, z, floorY, CorridorData.DEFAULT_WALL_HEIGHT);
    }

    private static void assertColumnIsPierced(List<BlockPlacement> out, int x, int z, int floorY, int height) {
        int seen = 0;
        for (BlockPlacement bp : out) {
            if (bp.getX() != x || bp.getZ() != z) continue;
            seen++;
            int offset = bp.getY() - floorY;
            if (offset == 1 || offset == 2) {
                assertEquals("minecraft:air", bp.getBlockId(),
                        "door-half level " + offset + " must be air: " + bp);
            } else {
                assertTrue(!"minecraft:air".equals(bp.getBlockId()),
                        "level " + offset + " must stay solid: " + bp);
            }
        }
        assertEquals(height, seen, "a doorway column is a full " + height + "-block column");
    }

    /**
     * Regression test for a bug this outlived two refactors. Pre-merge, {@code palette()}
     * resolved the corridor floor from the WALL_PATTERN BlockSet using a CorridorFloorPattern key
     * -- and since BlockSet was keyed by enum instance rather than name, that BlockSet (populated
     * only with WallPattern constants) never had the key, so the corridor floor always silently
     * fell through to a hardcoded stone_bricks regardless of what the datapack authored. The merged
     * MotifConfig makes that class of bug unexpressible: the corridor's blocks are named record
     * fields. Asserts the authored floor pair actually reaches the world.
     */
    @Test
    void corridorFloorComesFromTheMotifsCorridorSection() {
        MotifConfig motifConfig = new MotifConfig(
                MotifConfig.DEFAULT.wall(), MotifConfig.DEFAULT.ceiling(), MotifConfig.DEFAULT.door(),
                new CorridorConfig("minecraft:granite", "minecraft:diorite", "minecraft:andesite",
                        CorridorConfig.DEFAULT_HEIGHT),
                MotifConfig.DEFAULT.floor(), MotifConfig.DEFAULT.schemes());

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(motifConfig)
                .build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        boolean sawAuthoredFloor = out.stream().anyMatch(bp -> bp.getY() == 60
                && ("minecraft:granite".equals(bp.getBlockId()) || "minecraft:diorite".equals(bp.getBlockId())));
        assertTrue(sawAuthoredFloor, "corridor floor should come from CorridorConfig, not a hardcoded default");

        boolean sawAuthoredCeiling = out.stream()
                .anyMatch(bp -> bp.getY() == 64 && "minecraft:andesite".equals(bp.getBlockId()));
        assertTrue(sawAuthoredCeiling, "corridor ceiling should come from CorridorConfig too");
    }

    // -------- variable corridor height --------

    /**
     * A corridor's air gap grows with its height and the ceiling moves with it: at {@code h} the
     * cell is floor at {@code floorY}, {@code h-2} air rows, ceiling at {@code floorY+h-1}.
     */
    /**
     * Measured on a <em>wide</em> corridor deliberately: a 1-wide run drops its top course by
     * design (see {@link #aOneWideCellDropsItsCeilingAndFillsAbove}), so it is the wrong shape to
     * ask "does height reach the ceiling" of.
     */
    @Test
    void aTallerCorridorGetsMoreAirAndAHigherCeiling() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(wideCorridor(7), wideGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        long airInMiddleCell = out.stream()
                .filter(bp -> bp.getX() == 3 && bp.getZ() == 3 && "minecraft:air".equals(bp.getBlockId()))
                .count();
        assertEquals(5, airInMiddleCell, "a 7-high corridor has 5 air rows");

        boolean ceilingAtTop = out.stream()
                .anyMatch(bp -> bp.getX() == 3 && bp.getZ() == 3 && bp.getY() == 66
                        && !"minecraft:air".equals(bp.getBlockId()));
        assertTrue(ceilingAtTop, "the ceiling should sit at floorY+height-1 = 66");

        long westWallColumn = out.stream().filter(bp -> bp.getX() == 0 && bp.getZ() == 3).count();
        assertEquals(7, westWallColumn, "wall columns should be as tall as the corridor");
    }

    /**
     * The one thing height must not touch. {@code BasicDoorGenerator} owns
     * {@code floorY..floorY+3} and both wall generators pierce a door cell at {@code +1}/{@code +2};
     * a taller corridor may only add rows <em>above</em> that. If this ever drifts, doors are
     * either walled shut or hung in a hole.
     */
    @Test
    void aTallerCorridorStillPiercesExactlyTheTwoDoorHalfRows() {
        Grid2D grid = simpleGrid();
        grid.get(1, 3).setType(CellType.DOOR);

        CorridorData corridor = simpleCorridor();
        corridor.setWallHeight(8);

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(corridor, grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertColumnIsPierced(out, 1, 3, 60, 8);
    }

    /** The grid-free overload reads the same height off the data, so both paths still agree. */
    @Test
    void bothOverloadsAgreeAtANonDefaultHeight() {
        CorridorData gridBased = simpleCorridor();
        gridBased.setWallHeight(6);
        List<BlockPlacement> a = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(gridBased, simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), a);

        CorridorData gridFree = simpleCorridor();
        gridFree.setWallHeight(6);
        for (BlockPlacement bp : a) {
            if (bp.getZ() != 3 || bp.getX() < 2 || bp.getX() > 4) {
                Coords2D cell = new Coords2D(bp.getX(), bp.getZ());
                if (!gridFree.getWallCells().contains(cell)) {
                    gridFree.getWallCells().add(cell);
                }
            }
        }
        List<BlockPlacement> b = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(gridFree, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), b);

        assertEquals(a.size(), b.size(), "the two overloads should emit the same number of blocks");
    }

    // -------- arched profile --------

    /** An arched motif at the given height, built from stone brick stairs. */
    private static MotifConfig arched(int height) {
        return arched(height, Optional.empty());
    }

    private static MotifConfig arched(int height, Optional<Integer> narrowHeight) {
        return new MotifConfig(
                MotifConfig.DEFAULT.wall(), MotifConfig.DEFAULT.ceiling(), MotifConfig.DEFAULT.door(),
                new CorridorConfig("minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks",
                        height, CorridorConfig.Profile.ARCHED, Optional.of("minecraft:stone_brick_stairs"),
                        narrowHeight),
                MotifConfig.DEFAULT.floor(), MotifConfig.DEFAULT.schemes());
    }

    /** A 7x7 grid with a 3-wide corridor running east-west, so the arch has a cross-section. */
    private Grid2D wideGrid() {
        Grid2D grid = new Grid2D(7, 7);
        for (int x = 1; x <= 5; x++) {
            for (int z = 2; z <= 4; z++) {
                grid.get(x, z).setType(CellType.CORRIDOR);
                grid.get(x, z).setRegionId(10);
            }
        }
        return grid;
    }

    private CorridorData wideCorridor(int height) {
        CorridorData c = new CorridorData(10);
        for (int x = 1; x <= 5; x++) {
            for (int z = 2; z <= 4; z++) {
                c.getCells().add(new Coords2D(x, z));
            }
        }
        c.setWallHeight(height);
        return c;
    }

    private static BlockPlacement at(List<BlockPlacement> out, int x, int y, int z) {
        return out.stream().filter(bp -> bp.getX() == x && bp.getY() == y && bp.getZ() == z)
                .findFirst().orElseThrow(() -> new AssertionError("nothing placed at " + x + "," + y + "," + z));
    }

    /**
     * Read orientation off the <em>resolved</em> state, not the placement's property map:
     * {@code BlockStateCodec.encodeProperties} stores only non-default values, and stairs default
     * to {@code facing=north}, so the map is silently empty for exactly the case most worth
     * asserting.
     */
    private static String facingOf(BlockPlacement bp) {
        return BlockStateCodec.resolve(bp).getValue(StairBlock.FACING).getSerializedName();
    }

    private static String halfOf(BlockPlacement bp) {
        return BlockStateCodec.resolve(bp).getValue(StairBlock.HALF).getSerializedName();
    }

    /**
     * The cross-section of a 3-wide arch at height 7: haunches on the two wall-adjacent lanes
     * leaning into their own wall, clear air down the middle, crown across the top.
     *
     * <p>The facings are the whole point and they are easy to get backwards. A stair's upper half
     * is solid on the side it faces (verified against the real block shapes), so the north lane's
     * haunch must face NORTH to put its mass in the north wall.</p>
     */
    @Test
    void aThreeWideArchLeansItsHaunchesIntoTheWalls() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(arched(7))
                .build(wideCorridor(7), wideGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // haunch row is floorY + height - 2 = 65; corridor lanes are z=2 (north), 3, 4 (south).
        BlockPlacement north = at(out, 3, 65, 2);
        assertEquals("minecraft:stone_brick_stairs", north.getBlockId());
        assertEquals("north", facingOf(north), "north lane must lean into the north wall");
        assertEquals("top", halfOf(north), "a haunch hangs from the crown, not the floor");

        BlockPlacement south = at(out, 3, 65, 4);
        assertEquals("minecraft:stone_brick_stairs", south.getBlockId());
        assertEquals("south", facingOf(south), "south lane must lean into the south wall");

        assertEquals("minecraft:air", at(out, 3, 65, 3).getBlockId(),
                "the middle lane has no wall to spring from, so it stays open");
        assertEquals("minecraft:stone_bricks", at(out, 3, 66, 3).getBlockId(),
                "the crown row is unchanged by the arch");
    }

    /**
     * The degradation that matters, and it is narrower than "a 1-wide corridor gets no arch".
     *
     * <p>A 1-wide run has walls on both sides <em>across</em> its width, so no north/south haunch
     * ever qualifies — arching it from both sides would brick the passage shut at head height.
     * Along the run it is a different story: the cell at each end has a wall behind it and open
     * corridor ahead, so it does get a haunch, and that is the vault closing against the end wall
     * rather than anything blocking passage. Only the middle of the run is bare.</p>
     */
    @Test
    void aOneWideCorridorIsNeverArchedAcrossItsWidth() {
        CorridorData corridor = simpleCorridor(); // x = 2..4 at z = 3, walls north and south
        corridor.setWallHeight(7);

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(arched(7))
                .build(corridor, simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // narrowHeight defaults to no drop, so the haunch row is the usual 60 + 7 - 2 = 65.
        assertEquals("minecraft:air", at(out, 3, 65, 3).getBlockId(),
                "the middle of a 1-wide run has no wall to spring from in any direction");

        for (BlockPlacement bp : out) {
            if ("minecraft:stone_brick_stairs".equals(bp.getBlockId())) {
                String facing = facingOf(bp);
                assertTrue("east".equals(facing) || "west".equals(facing),
                        "a haunch across the 1-cell width would narrow the passage; got facing=" + facing);
            }
        }
    }

    /** Nothing the arch does may reach the door column's fixed floorY..floorY+3. */
    @Test
    void theArchNeverReachesIntoTheDoorColumn() {
        for (int height = CorridorConfig.MIN_ARCHED_HEIGHT; height <= CorridorConfig.MAX_HEIGHT; height++) {
            List<BlockPlacement> out = new ArrayList<>();
            new BasicCorridorGenerator().withMotifConfig(arched(height))
                    .build(wideCorridor(height), wideGrid(), 60, DungeonMotif.CLASSIC,
                            RandomSource.create(7L), out);

            for (BlockPlacement bp : out) {
                if ("minecraft:stone_brick_stairs".equals(bp.getBlockId())) {
                    assertTrue(bp.getY() > 63,
                            "height " + height + ": a haunch at " + bp.getY()
                                    + " lands in the door column (60..63)");
                }
            }
        }
    }

    /** Flat stays flat: the arch must be opt-in, not something every motif silently acquires. */
    @Test
    void aFlatProfileEmitsNoStairs() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(wideCorridor(7), wideGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertTrue(out.stream().noneMatch(bp -> bp.getBlockId().contains("stairs")),
                "the default flat profile should place no stairs");
    }

    // -------- narrow cells drop their top course --------

    /** A flat motif at the given height; narrowHeight defaults to no drop. */
    private static MotifConfig flat(int height) {
        return flat(height, Optional.empty());
    }

    private static MotifConfig flat(int height, Optional<Integer> narrowHeight) {
        return new MotifConfig(
                MotifConfig.DEFAULT.wall(), MotifConfig.DEFAULT.ceiling(), MotifConfig.DEFAULT.door(),
                new CorridorConfig("minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks",
                        height, CorridorConfig.Profile.FLAT, Optional.empty(), narrowHeight),
                MotifConfig.DEFAULT.floor(), MotifConfig.DEFAULT.schemes());
    }

    /**
     * A 1-wide run reads as a slot canyon at full height, so its ceiling comes down one course.
     * The rows above the dropped ceiling must be <em>filled</em>, not merely skipped: the piece's
     * bounding box covers them either way, and leaving them to whatever the terrain put there is
     * how you get a cave opening into the corridor roof.
     */
    @Test
    void aOneWideCellDropsItsCeilingAndFillsAbove() {
        CorridorData corridor = simpleCorridor(); // 1-wide run at z = 3
        corridor.setWallHeight(7);

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(flat(7, Optional.of(6)))
                .build(corridor, simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertEquals("minecraft:air", at(out, 3, 64, 3).getBlockId(), "row 4 is still headroom");
        assertEquals("minecraft:stone_bricks", at(out, 3, 65, 3).getBlockId(),
                "the ceiling drops to floorY+5 in a 1-wide cell");
        assertTrue(!"minecraft:air".equals(at(out, 3, 66, 3).getBlockId()),
                "the row above a dropped ceiling must be filled solid, not left open");

        long column = out.stream().filter(bp -> bp.getX() == 3 && bp.getZ() == 3).count();
        assertEquals(7, column, "a narrow cell still writes every row of the corridor's full height");
    }

    /** A wide corridor is unaffected — the rule is about cells with no cross-section. */
    @Test
    void aWideCellKeepsTheFullHeight() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(flat(7, Optional.of(6)))
                .build(wideCorridor(7), wideGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertEquals("minecraft:air", at(out, 3, 65, 3).getBlockId(),
                "a 3-wide corridor keeps its headroom at row 5");
        assertEquals("minecraft:stone_bricks", at(out, 3, 66, 3).getBlockId(),
                "and its ceiling stays at the full height");
    }

    /**
     * The narrow rule must not undercut the doorway. Dropping the ceiling shortens the cell, and a
     * cell in front of a door still has to clear the lintel at floorY+3.
     */
    @Test
    void aDroppedCeilingStillClearsTheDoorColumn() {
        for (int height = CorridorConfig.MIN_HEIGHT; height <= CorridorConfig.MAX_HEIGHT; height++) {
            CorridorData corridor = simpleCorridor();
            corridor.setWallHeight(height);
            Grid2D grid = simpleGrid();
            grid.get(1, 3).setType(CellType.DOOR);

            List<BlockPlacement> out = new ArrayList<>();
            new BasicCorridorGenerator().withMotifConfig(flat(height, Optional.of(height - 1)))
                    .build(corridor, grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

            for (int y = 61; y <= 63; y++) {
                assertEquals("minecraft:air", at(out, 2, y, 3).getBlockId(),
                        "height " + height + ": the cell at the doorway must stay open at y=" + y);
            }
        }
    }

    /** An arch needs 6; a narrow cell dropped below that gets no haunch rather than a bad one. */
    @Test
    void aNarrowCellDroppedBelowArchHeightGetsNoHaunch() {
        CorridorData corridor = simpleCorridor();
        corridor.setWallHeight(6); // narrow cells drop to 5, one below MIN_ARCHED_HEIGHT

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(arched(6, Optional.of(5)))
                .build(corridor, simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertTrue(out.stream().noneMatch(bp -> bp.getBlockId().contains("stairs")),
                "a cell dropped below the arch minimum must not be arched");
    }

    // -------- haunch corner shapes --------

    private static String shapeOf(BlockPlacement bp) {
        return BlockStateCodec.resolve(bp).getValue(StairBlock.SHAPE).getSerializedName();
    }

    /**
     * The straight run: haunches along an unbroken wall must stay {@code straight}. This is the
     * baseline the corner cases are measured against.
     */
    @Test
    void haunchesAlongAStraightWallAreStraight() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(arched(7))
                .build(wideCorridor(7), wideGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // Mid-run north-lane haunches, away from either end of the corridor.
        for (int x = 2; x <= 4; x++) {
            assertEquals("straight", shapeOf(at(out, x, 65, 2)),
                    "haunch at x=" + x + " runs along an unbroken wall");
        }
    }

    /** Where two walls meet, the haunch has to cover both faces — that is an inner corner. */
    @Test
    void twoWallsMeetingProduceAnInnerCorner() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(arched(7))
                .build(wideCorridor(7), wideGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // (1,2) is the north-west corner cell: wall to the north AND wall to the west.
        assertEquals("inner_left", shapeOf(at(out, 1, 65, 2)),
                "where two walls meet, the haunch must cover both faces");
    }

    /**
     * The bug this whole shape rule exists for, pinned on the case vanilla structurally cannot
     * reach. {@code StairBlock.getStairsShape} looks for a stair at {@code pos.relative(facing)} to
     * decide an outer corner — and a haunch faces into its wall, so that lookup always finds solid
     * wall and the outer branch can <em>never</em> fire. Left to vanilla every outer corner stays
     * {@code straight}, which is the notch that showed up in game.
     *
     * <p>A T-junction is the smallest shape that forces one: the north wall of the main run stops
     * where the branch opens, so the haunch beside the opening has to taper instead of running on.</p>
     */
    @Test
    void aWallRunEndingProducesAnOuterCorner() {
        // Main run x=1..5 at z=2..4, plus a branch heading north out of x=3.
        Grid2D grid = wideGrid();
        CorridorData corridor = wideCorridor(7);
        for (int z = 0; z <= 1; z++) {
            grid.get(3, z).setType(CellType.CORRIDOR);
            grid.get(3, z).setRegionId(10);
            corridor.getCells().add(new Coords2D(3, z));
        }

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(arched(7))
                .build(corridor, grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // (2,2) faces the north wall; to its east that wall stops where the branch opens.
        assertEquals("outer_right", shapeOf(at(out, 2, 65, 2)),
                "the haunch must taper where the wall run ends, not carry on square");
    }

    /** A shape the generator authored must survive; vanilla would reset it to straight. */
    @Test
    void anAuthoredCornerShapeIsNotStraight() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(arched(7))
                .build(wideCorridor(7), wideGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        long shaped = out.stream()
                .filter(bp -> "minecraft:stone_brick_stairs".equals(bp.getBlockId()))
                .filter(bp -> !"straight".equals(shapeOf(bp)))
                .count();
        assertTrue(shaped > 0, "a corridor with corners should author at least one non-straight haunch");
    }

    /**
     * The cell that closes the chamfer around a convex wall corner. It has <em>no</em> orthogonal
     * wall — only a diagonal one — so the ordinary "wall on one side, corridor on the other" rule
     * rejects it and it used to come out as air, leaving the notch reported in game as the corner
     * missing its outer block. It needs an {@code outer_*} stair capping the quarter that faces the
     * corner tip.
     */
    @Test
    void aConvexWallCornerGetsItsOuterCap() {
        // 7x7 grid; carve an L so a single wall cell juts into the passage at (2,2).
        Grid2D grid = new Grid2D(7, 7);
        CorridorData corridor = new CorridorData(10);
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                if (x <= 2 && z <= 2) continue; // leave the north-west block solid
                grid.get(x, z).setType(CellType.CORRIDOR);
                grid.get(x, z).setRegionId(10);
                corridor.getCells().add(new Coords2D(x, z));
            }
        }
        corridor.setWallHeight(7);

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(arched(7))
                .build(corridor, grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // (3,3) sits diagonally off the corner tip at (2,2): no orthogonal wall, wall to the NW.
        BlockPlacement cap = at(out, 3, 65, 3);
        assertEquals("minecraft:stone_brick_stairs", cap.getBlockId(),
                "the cell diagonally off a convex corner must still carry a haunch");
        assertEquals("outer_left", shapeOf(cap), "and it caps the corner rather than running square");
        assertEquals("north", facingOf(cap));
        assertEquals("top", halfOf(cap));
    }

    @Test
    void corridorBuilderIsDeterministic() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> a = new ArrayList<>();
        List<BlockPlacement> b = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), a);
        gen.build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), b);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).toString(), b.get(i).toString());
        }
    }
}
