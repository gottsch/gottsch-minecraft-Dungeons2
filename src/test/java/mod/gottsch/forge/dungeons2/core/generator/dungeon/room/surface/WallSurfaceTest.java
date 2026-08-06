package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The (u, v) &rarr; world mapping, corner ownership, and the doorway mask.
 *
 * <p>These are the three things every future wall pattern inherits and none of them can restate: a
 * pattern authored in (u, v) cannot see corners or doors at all, so if this class gets them wrong,
 * every pattern is wrong in the same way.</p>
 */
class WallSurfaceTest {

    private static final int FLOOR_Y = 60;
    private static BlockState base;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        base = Blocks.STONE_BRICKS.defaultBlockState();
    }

    /** A 7x9 room (deliberately not square, so a swapped axis shows up) at origin (10, 20). */
    private static RoomData room() {
        return new RoomData(1, 10, 20, 7, 9, 5, RoomRole.NORMAL);
    }

    private static Map<Direction, WallSurface> byFacing(RoomData room) {
        return WallSurface.forRoom(room).stream()
                .collect(Collectors.toMap(WallSurface::facing, Function.identity()));
    }

    @Test
    void aRoomHasFourRunsOneFacingEachWay() {
        Map<Direction, WallSurface> runs = byFacing(room());
        assertEquals(Set.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST),
                runs.keySet());
    }

    /**
     * Z-edge runs span the full width and own the corners; X-edge runs cover interior depth only.
     * Together they cover the perimeter exactly once.
     */
    @Test
    void cornerOwnershipGivesTheZEdgeRunsTheFullWidth() {
        RoomData room = room();
        Map<Direction, WallSurface> runs = byFacing(room);

        assertEquals(room.getWidth(), runs.get(Direction.SOUTH).length());
        assertEquals(room.getWidth(), runs.get(Direction.NORTH).length());
        assertEquals(room.getDepth() - 2, runs.get(Direction.EAST).length());
        assertEquals(room.getDepth() - 2, runs.get(Direction.WEST).length());

        int total = WallSurface.forRoom(room).stream().mapToInt(WallSurface::length).sum();
        assertEquals(2 * room.getWidth() + 2 * room.getDepth() - 4, total,
                "the four runs should tile the perimeter with no cell shared or missed");
    }

    /** A run's facing points INTO the room -- that is the face a pattern decorates. */
    @Test
    void eachRunSitsOnTheEdgeItsFacingPointsAwayFrom() {
        RoomData room = room();
        Map<Direction, WallSurface> runs = byFacing(room);

        // Facing SOUTH (+z) means the wall is the northern edge, at the room's minimum Z.
        assertEquals(room.getOriginZ(), runs.get(Direction.SOUTH).zAt(0));
        assertEquals(room.getOriginZ() + room.getDepth() - 1, runs.get(Direction.NORTH).zAt(0));
        // Facing EAST (+x) means the wall is the western edge, at minimum X.
        assertEquals(room.getOriginX(), runs.get(Direction.EAST).xAt(0));
        assertEquals(room.getOriginX() + room.getWidth() - 1, runs.get(Direction.WEST).xAt(0));
    }

    /** u advances along +X on the Z-edge runs and +Z on the X-edge runs, never mirrored. */
    @Test
    void uAdvancesAlongThePositiveAxis() {
        Map<Direction, WallSurface> runs = byFacing(room());

        WallSurface north = runs.get(Direction.SOUTH);
        assertEquals(north.xAt(0) + 3, north.xAt(3));
        assertEquals(north.zAt(0), north.zAt(3), "a Z-edge run must not drift in Z");

        WallSurface west = runs.get(Direction.EAST);
        assertEquals(west.zAt(0) + 3, west.zAt(3));
        assertEquals(west.xAt(0), west.xAt(3), "an X-edge run must not drift in X");
    }

    @Test
    void anEmptyPlanRendersEveryCellAsTheBaseBlock() {
        RoomData room = room();
        int wallHeight = room.getHeight() - 2;
        List<BlockPlacement> out = new ArrayList<>();
        for (WallSurface surface : WallSurface.forRoom(room)) {
            surface.emit(SurfacePlan.of(surface.length(), wallHeight), FLOOR_Y, Set.of(), base, out);
        }
        assertEquals((2 * room.getWidth() + 2 * room.getDepth() - 4) * wallHeight, out.size());
        for (BlockPlacement bp : out) {
            assertEquals("minecraft:stone_bricks", bp.getBlockId());
        }
    }

    @Test
    void aPlannedCellWinsOverTheBaseBlock() {
        RoomData room = room();
        WallSurface north = byFacing(room).get(Direction.SOUTH);
        SurfacePlan plan = SurfacePlan.of(north.length(), 3);
        plan.set(2, 1, Blocks.POLISHED_ANDESITE.defaultBlockState());

        List<BlockPlacement> out = new ArrayList<>();
        north.emit(plan, FLOOR_Y, Set.of(), base, out);

        List<BlockPlacement> andesite = out.stream()
                .filter(bp -> "minecraft:polished_andesite".equals(bp.getBlockId())).toList();
        assertEquals(1, andesite.size());
        assertEquals(north.xAt(2), andesite.get(0).getX());
        assertEquals(FLOOR_Y + 1 + 1, andesite.get(0).getY(), "v maps to floorY + 1 + v");
    }

    /**
     * The doorway mask beats the pattern, and must: a solid block in a door cell is the
     * lichen-on-doors bug, and a pattern authored in (u, v) cannot see doors to avoid them.
     */
    @Test
    void theDoorwayMaskOverridesAPlannedCell() {
        RoomData room = room();
        WallSurface north = byFacing(room).get(Direction.SOUTH);
        Coords2D door = new Coords2D(north.xAt(3), north.zAt(3));

        // A pattern that tries to fill the whole wall solid, including the door column.
        SurfacePlan plan = SurfacePlan.of(north.length(), 3);
        for (int u = 0; u < north.length(); u++) {
            for (int v = 0; v < 3; v++) {
                plan.set(u, v, Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }

        List<BlockPlacement> out = new ArrayList<>();
        north.emit(plan, FLOOR_Y, Set.of(door), base, out);

        Set<Integer> airRows = new HashSet<>();
        for (BlockPlacement bp : out) {
            if (bp.getX() == door.getX() && bp.getZ() == door.getY()) {
                if ("minecraft:air".equals(bp.getBlockId())) {
                    airRows.add(bp.getY() - FLOOR_Y);
                }
            }
        }
        assertEquals(Set.of(1, 2), airRows,
                "the two door-half rows must be air even when a pattern fills them");
    }

    /** Only the door column is opened -- the mask must not leak along the run. */
    @Test
    void theDoorwayMaskAffectsOnlyItsOwnColumn() {
        RoomData room = room();
        WallSurface north = byFacing(room).get(Direction.SOUTH);
        Coords2D door = new Coords2D(north.xAt(3), north.zAt(3));

        List<BlockPlacement> out = new ArrayList<>();
        north.emit(SurfacePlan.of(north.length(), 3), FLOOR_Y, Set.of(door), base, out);

        for (BlockPlacement bp : out) {
            if (bp.getX() != door.getX() && "minecraft:air".equals(bp.getBlockId())) {
                throw new AssertionError("air outside the door column: " + bp);
            }
        }
        assertTrue(out.stream().anyMatch(bp -> "minecraft:air".equals(bp.getBlockId())));
    }

    // ---------- projecting trim at a doorway ----------

    /**
     * The run whose {@code u} range actually covers the room's doorways, with the doorway's local
     * {@code u} alongside. The SOUTH run starts at the room origin and steps in +X, so a doorway on
     * that wall sits at {@code doorX - originX}.
     */
    private static WallSurface southRun(RoomData room) {
        return byFacing(room).get(Direction.SOUTH);
    }

    /**
     * A full-height projecting pattern in a doorway column is dropped ENTIRELY, not clipped to
     * miss the two door rows.
     *
     * <p>Clipping is right for a cornice and wrong for a pilaster: take two cells out of a
     * floor-to-ceiling strip and what is left is a column of trim hanging above the opening with a
     * gap where it should meet the floor. Nothing in the wall's own (u, v) space can see a doorway,
     * so the rule has to live here.</p>
     */
    @Test
    void aFullHeightProjectingColumnIsDroppedWholeAtADoorway() {
        RoomData room = room();
        WallSurface run = southRun(room);
        int doorU = 3;
        Coords2D door = new Coords2D(run.xAt(doorU), run.zAt(doorU));

        // A strip running the full height of the wall, in the doorway's column and one beside it.
        SurfacePlan plan = SurfacePlan.of(run.length(), 3);
        for (int v = 0; v < 3; v++) {
            plan.set(doorU, v, base);
            plan.set(doorU + 1, v, base);
        }

        List<BlockPlacement> out = new ArrayList<>();
        run.emitProjected(plan, 1, FLOOR_Y, Set.of(door), out);

        int projectedX = run.xAt(doorU) + Direction.SOUTH.getStepX();
        int projectedZ = run.zAt(doorU) + Direction.SOUTH.getStepZ();
        assertTrue(out.stream().noneMatch(bp -> bp.getX() == projectedX && bp.getZ() == projectedZ),
                "the whole doorway column should be gone, not just its two door rows");
        assertEquals(3, out.stream()
                        .filter(bp -> bp.getX() == run.xAt(doorU + 1) + Direction.SOUTH.getStepX()
                                && bp.getZ() == run.zAt(doorU + 1) + Direction.SOUTH.getStepZ())
                        .count(),
                "the neighbouring strip is untouched");
    }

    /**
     * A cornice still draws straight over a doorway. It marks only the top row, never a door row,
     * so the drop rule leaves it alone -- which it must, since the lintel above a door is solid and
     * a band that broke there would look like damage.
     *
     * <p><strong>This one passes with the drop rule removed as well, on purpose.</strong> It is the
     * pairing assertion: the test above it would stay green if a future change dropped
     * <em>every</em> column at a doorway, and this is what stops that. Same role
     * {@code CeilingRingSettleTest#theRunsBetweenTheCornersStayStraight} plays for the ring.</p>
     */
    @Test
    void aTopAnchoredProjectingCourseStillCrossesADoorway() {
        RoomData room = room();
        WallSurface run = southRun(room);
        int doorU = 3;
        Coords2D door = new Coords2D(run.xAt(doorU), run.zAt(doorU));

        // One band on the top row only -- above both door halves.
        SurfacePlan plan = SurfacePlan.of(run.length(), 4);
        for (int u = 0; u < run.length(); u++) {
            plan.set(u, 3, base);
        }

        List<BlockPlacement> out = new ArrayList<>();
        run.emitProjected(plan, 1, FLOOR_Y, Set.of(door), out);

        assertTrue(out.stream().anyMatch(bp -> bp.getX() == run.xAt(doorU) + Direction.SOUTH.getStepX()
                        && bp.getZ() == run.zAt(doorU) + Direction.SOUTH.getStepZ()),
                "a cornice must not be interrupted by the doorway beneath it");
    }

    /** A room too thin to have interior depth still yields four runs; two just emit nothing. */
    @Test
    void aRoomWithNoInteriorDepthGivesZeroLengthXEdgeRuns() {
        RoomData thin = new RoomData(1, 0, 0, 7, 2, 5, RoomRole.NORMAL);
        Map<Direction, WallSurface> runs = byFacing(thin);
        assertEquals(0, runs.get(Direction.EAST).length());
        assertEquals(0, runs.get(Direction.WEST).length());

        List<BlockPlacement> out = new ArrayList<>();
        runs.get(Direction.EAST).emit(SurfacePlan.of(0, 3), FLOOR_Y, Set.of(), base, out);
        assertEquals(0, out.size());
    }
}
