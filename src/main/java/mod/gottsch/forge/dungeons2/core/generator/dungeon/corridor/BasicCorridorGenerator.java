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

import mod.gottsch.forge.dungeons2.core.config.CorridorConfig;
import mod.gottsch.forge.dungeons2.core.config.CorridorStyle;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds one corridor region as {@link BlockPlacement}s.
 *
 * <p>The corridor's wall height {@code h} comes from {@link CorridorData#getWallHeight()}
 * (the planner resolved it from the motif's {@code CorridorConfig}, rolling one
 * {@link CorridorStyle} per floor); the whole corridor is
 * {@code floorY .. floorY+h-1}. For each cell in the corridor: emits a floor block at
 * Y={@code floorY}, {@code h-2} air blocks above, and a motif ceiling block at
 * Y={@code floorY+h-1}. For each grid cell <em>neighboring</em> the corridor
 * that is rock / wall / door / connector / out-of-bounds, emits an {@code h}-block
 * wall column. Walls are deduped per builder
 * call so the same neighbor cell isn't emitted multiple times for a single
 * corridor; cross-corridor wall duplication is acceptable (the renderer
 * idempotently overwrites).</p>
 *
 * <p><strong>Height is not free above the doorway rows.</strong> {@code BasicDoorGenerator}
 * owns {@code floorY .. floorY+3} (sill / lower / upper / lintel) and both wall generators
 * pierce a door cell at {@code +1}/{@code +2}. Those four rows are identical at every height;
 * only what sits above them varies.</p>
 *
 * <p>A <strong>door</strong> neighbor gets that same column with the two
 * door-half levels left as air &mdash; see {@link #DOOR_HALF_LOW}.</p>
 *
 * <h2>The ceiling is a profile, not a row</h2>
 * <p>On {@code flat} it is one row of ceiling block at the top. On {@code arched} that crown row is
 * unchanged and the row below it ({@code h-2}) carries stair haunches leaning into the walls, so an
 * arch borrows a row rather than needing extra height. {@link #haunchFacing} decides which cells
 * get one; both build overloads feed it the same rule from different sources.</p>
 *
 * @author Mark Gottschling on Dec 5, 2023 (Phase 2 rewrite May 25, 2026)
 */
public class BasicCorridorGenerator implements ICorridorGenerator {

    private MotifConfig motifConfig = MotifConfig.DEFAULT;

    /** See {@code BasicWallGenerator#withMotifConfig}. */
    public BasicCorridorGenerator withMotifConfig(MotifConfig motifConfig) {
        this.motifConfig = motifConfig;
        return this;
    }

    /**
     * Y offsets (above the floor surface) that {@code BasicDoorGenerator} fills
     * with the two door halves. A corridor bordering a door cell must not emit a
     * solid block there: the corridor's decoration pass runs before
     * {@code DungeonDoorPiece} carves the door, so a full cube in the door cell
     * anchors glow lichen in the corridor air beside it, facing the door cell.
     * Glow lichen is a MultifaceBlock and renders flush against its anchor's
     * face, so once the door lands it appears plastered onto the door. The door
     * belongs to a different piece, so no processor can see it coming. Mirrors
     * {@code BasicWallGenerator}'s handling on the room side.
     */
    private static final int DOOR_HALF_LOW = 1;
    private static final int DOOR_HALF_HIGH = 2;

    @Override
    public void build(CorridorData corridor, Grid2D grid, int floorY,
                      IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        CorridorStyle style = motifConfig.corridor().styleFor(corridor.getStyleName());
        Palette palette = palette(style);
        int height = corridor.getWallHeight();
        int narrowCeiling = Math.min(height, style.narrowCellHeight());
        List<Course> courses = courses(style, height);

        // The arch reads the same two questions off the grid that the grid-free overload reads off
        // CorridorData, so both paths run one shared rule (see haunchFacing).
        Set<Coords2D> corridorCells = new HashSet<>(corridor.getCells());
        CellTest isWall = (cx, cz) -> isWallElement(grid, cx, cz);
        CellTest isOpen = (cx, cz) -> corridorCells.contains(new Coords2D(cx, cz));

        Set<Coords2D> wallsEmitted = new HashSet<>();
        for (Coords2D cell : corridor.getCells()) {
            int x = cell.getX();
            int z = cell.getY();
            emitCellColumn(x, z, floorY, height, narrowCeiling, palette, isWall, isOpen, random, out);

            // 8-neighbor wall columns, sourced live from the grid.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = x + dx;
                    int nz = z + dz;
                    Coords2D neighbor = new Coords2D(nx, nz);
                    if (wallsEmitted.contains(neighbor)) continue;
                    if (isDoorElement(grid, nx, nz)) {
                        emitDoorwayColumn(nx, nz, floorY, height, palette, courses, isOpen, random, out);
                        wallsEmitted.add(neighbor);
                    } else if (isWallElement(grid, nx, nz)) {
                        emitWallColumn(nx, nz, floorY, height, palette, courses, isOpen, random, out);
                        wallsEmitted.add(neighbor);
                    }
                }
            }
        }
    }

    @Override
    public void build(CorridorData corridor, int floorY,
                      IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        CorridorStyle style = motifConfig.corridor().styleFor(corridor.getStyleName());
        Palette palette = palette(style);
        int height = corridor.getWallHeight();
        int narrowCeiling = Math.min(height, style.narrowCellHeight());
        List<Course> courses = courses(style, height);

        // The grid is gone here, so wall adjacency comes from the cells the planner folded in.
        // wallCells and doorCells together are exactly what isWallElement would have answered
        // true for, with one known exception: a premade (connector) cell is deliberately absent
        // from both (see DungeonStackPlanner.convertLevel), so a corridor cell facing one gets no
        // haunch on that side where the grid-based overload would give it one. Only reachable via
        // an assembled entrance, and it costs one stair.
        Set<Coords2D> corridorCells = new HashSet<>(corridor.getCells());
        Set<Coords2D> wallCells = new HashSet<>(corridor.getWallCells());
        wallCells.addAll(corridor.getDoorCells());
        CellTest isWall = (cx, cz) -> wallCells.contains(new Coords2D(cx, cz));
        CellTest isOpen = (cx, cz) -> corridorCells.contains(new Coords2D(cx, cz));

        // Corridor columns first, then the pre-computed wall cells. The two sets
        // are disjoint within a single corridor, so order is immaterial; the
        // placements match the grid-based overload as a set.
        for (Coords2D cell : corridor.getCells()) {
            int x = cell.getX();
            int z = cell.getY();
            emitCellColumn(x, z, floorY, height, narrowCeiling, palette, isWall, isOpen, random, out);
        }
        for (Coords2D wall : corridor.getWallCells()) {
            emitWallColumn(wall.getX(), wall.getY(), floorY, height, palette, courses, isOpen, random, out);
        }
        for (Coords2D door : corridor.getDoorCells()) {
            emitDoorwayColumn(door.getX(), door.getY(), floorY, height, palette, courses, isOpen, random, out);
        }
    }

    /**
     * One corridor cell: picks its ceiling height, then whether it gets an arch haunch, then emits.
     *
     * <p>Both build overloads route through here so the two cannot drift &mdash; the only thing that
     * differs between them is where {@code isWall}/{@code isOpen} get their answers.</p>
     */
    private static void emitCellColumn(int x, int z, int floorY, int height, int narrowCeiling,
                                       Palette palette, CellTest isWall, CellTest isOpen,
                                       RandomSource random, List<BlockPlacement> out) {
        int ceilingHeight = isNarrow(x, z, isWall) ? narrowCeiling : height;
        // Re-checked per cell, not just per motif: a dropped ceiling can take a cell below the
        // height an arch needs even when the corridor as a whole cleared it at load time.
        Haunch haunch = ceilingHeight >= CorridorConfig.MIN_ARCHED_HEIGHT
                ? haunchFor(palette, x, z, isWall, isOpen) : null;
        emitCorridorColumn(x, z, floorY, height, ceilingHeight,
                haunch == null ? null : haunch.facing(),
                haunch == null ? "straight" : haunch.shape(),
                palette, random, out);
    }

    /**
     * A floor block at {@code floorY} (45% {@code floor}, 55% {@code alternateFloor}, matching
     * {@code BasicFloorGenerator}'s room-floor split), {@code height-2} air blocks above, and a
     * ceiling block at {@code floorY+height-1} (the top of the corridor walls), closing the corridor.
     */
    private static void emitCorridorColumn(int x, int z, int floorY, int height, int ceilingHeight,
                                            Direction haunch, String haunchShape, Palette palette,
                                            RandomSource random, List<BlockPlacement> out) {
        BlockState floor = RandomHelper.checkProbability(random, 45) ? palette.floor : palette.alternateFloor;
        out.add(BlockStateCodec.placement(x, floorY, z, floor));
        int haunchRow = ceilingHeight - 2;
        for (int yOffset = 1; yOffset < ceilingHeight - 1; yOffset++) {
            BlockState state = (haunch != null && yOffset == haunchRow)
                    ? haunchState(palette.arch, haunch, haunchShape) : palette.air;
            out.add(BlockStateCodec.placement(x, floorY + yOffset, z, state));
        }
        out.add(BlockStateCodec.placement(x, floorY + ceilingHeight - 1, z, palette.ceiling));
        // A dropped ceiling leaves rows between it and the corridor's full height. Fill them solid
        // rather than leaving them unwritten: the piece's bounding box covers them either way, and
        // whatever the terrain happened to put there could be a cave, i.e. a hole in the ceiling.
        for (int yOffset = ceilingHeight; yOffset < height; yOffset++) {
            out.add(BlockStateCodec.placement(x, floorY + yOffset, z, palette.wall));
        }
    }

    /**
     * True when this cell is only one cell wide on either axis &mdash; walls facing each other
     * across it. Exactly the cells that get no arch (there is no direction with a wall one side and
     * open corridor the other), and for the same underlying reason: there is no cross-section.
     */
    private static boolean isNarrow(int x, int z, CellTest isWall) {
        return (isWall.test(x, z - 1) && isWall.test(x, z + 1))
                || (isWall.test(x - 1, z) && isWall.test(x + 1, z));
    }

    /**
     * The haunch's orientation. Verified against the real block shapes rather than reasoned about:
     * a stair's <em>upper</em> half is solid on the side it {@code facing}s. So a haunch against a
     * west wall is {@code facing=west}, putting its mass into the wall, and {@code half=top}, which
     * joins it to the crown row above and leaves the cut-away quarter low and inboard &mdash; the
     * ceiling springing off the wall, which is what an arch is.
     *
     * <p>Set through {@code withProperties} rather than {@code setValue} so a datapack that names a
     * non-stairs block gets that block placed square instead of a crash.</p>
     */
    private static BlockState haunchState(BlockState arch, Direction facing, String shape) {
        return BlockStateCodec.withProperties(arch,
                Map.of("half", "top", "facing", facing.getSerializedName(), "shape", shape));
    }

    /**
     * The haunch's corner shape, computed here rather than left to vanilla.
     *
     * <h2>Why vanilla cannot do this one</h2>
     * <p>{@code StairBlock.getStairsShape} looks for a stair at {@code pos.relative(facing)} to
     * decide OUTER, and at {@code pos.relative(facing.getOpposite())} to decide INNER. A haunch
     * faces <em>into the wall</em>, so the first of those is always a solid wall block and the OUTER
     * branch can never fire &mdash; while the second sometimes finds a perpendicular haunch across a
     * narrow corridor and does fire. The result is inner corners that mitre and outer corners that
     * never do, which is exactly the "outers not populating" this fixes. We know the wall layout
     * outright, so we derive the shape from it instead of from what happens to be adjacent.</p>
     *
     * <p>{@code left}/{@code right} are along the wall run (perpendicular to {@code facing}).
     * A wall carrying on around the corner on one side means the haunch has to cover that side too,
     * which is an INNER; a wall that simply stops means the haunch tapers off, which is an OUTER.
     * Both sides qualifying at once is a one-cell wall stub, and a stair has only one shape, so left
     * wins &mdash; deterministically, not by iteration order.</p>
     */
    private static String haunchShape(int x, int z, Direction facing, CellTest isWall) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();
        if (isWall.test(x + left.getStepX(), z + left.getStepZ())) {
            return "inner_left";
        }
        if (isWall.test(x + right.getStepX(), z + right.getStepZ())) {
            return "inner_right";
        }
        // The wall run ends here when the cell alongside has no wall behind it in turn.
        if (!isWall.test(x + left.getStepX() + facing.getStepX(), z + left.getStepZ() + facing.getStepZ())) {
            return "outer_left";
        }
        if (!isWall.test(x + right.getStepX() + facing.getStepX(), z + right.getStepZ() + facing.getStepZ())) {
            return "outer_right";
        }
        return "straight";
    }

    /** One cell's haunch: which way it leans and which corner shape it takes. */
    private record Haunch(Direction facing, String shape) {}

    /**
     * This cell's haunch, or {@code null} for none.
     *
     * <p>Two cases, and the second is easy to miss. The common one is a cell with a wall
     * <em>orthogonally</em> beside it, which leans into that wall ({@link #haunchFacing}).</p>
     *
     * <p>The other is the cell that closes the chamfer <strong>around a convex corner</strong>. Where
     * a wall corner juts into the passage, the cells along each face get their haunch normally, but
     * the cell diagonally off the corner tip has no orthogonal wall at all &mdash; only a diagonal
     * one. Left to the first rule it gets nothing, and the chamfer arrives from two directions and
     * stops dead, leaving the notch that reads in game as "the corner is missing its outer block".
     * That cell wants an {@code outer_*} stair covering just the quarter facing the tip.</p>
     *
     * <p>The diagonal is only a corner tip if <em>both</em> cells flanking it are open; if either
     * were a wall this cell would have had an orthogonal wall and been handled above. Diagonals are
     * tried in a fixed order so a cell touching two tips is still deterministic.</p>
     */
    private static Haunch haunchFor(Palette palette, int x, int z, CellTest isWall, CellTest isOpen) {
        if (palette.arch == null) {
            return null;
        }
        Direction facing = haunchFacing(palette, x, z, isWall, isOpen);
        if (facing != null) {
            return new Haunch(facing, haunchShape(x, z, facing, isWall));
        }
        for (Corner corner : CORNERS) {
            if (!isWall.test(x + corner.dx, z + corner.dz)) {
                continue;
            }
            if (isWall.test(x + corner.dx, z) || isWall.test(x, z + corner.dz)) {
                continue; // not a tip -- a flanking wall means the orthogonal rule owned this cell
            }
            if (chamferAlreadyArrives(palette, x, z, corner.facing, isWall, isOpen)) {
                continue;
            }
            return new Haunch(corner.facing, corner.shape);
        }
        return null;
    }

    /**
     * True when the cell this cap would lean toward already carries a haunch leaning the
     * <strong>same</strong> way &mdash; in which case the cap is a second stair sitting directly in
     * front of the first, which is the "stairs stacked in front of stairs" this suppresses.
     *
     * <p>A cap is the one haunch that does <em>not</em> lean into a wall: its whole purpose is to
     * close the notch beside a convex corner tip, so by construction it leans over open corridor.
     * That makes "what is in front of me" a question only this branch has to ask.</p>
     *
     * <p>Deliberately narrow. The neighbour that closes the corner properly is the one facing
     * <em>perpendicular</em> to the cap &mdash; the two meet at right angles and mitre, which is
     * exactly the arrangement the {@code CORNERS} table was added for. Only a neighbour facing the
     * same way is a duplicate, and it was ~86% of caps: the chamfer had already arrived along that
     * axis and the cap re-covered ground that was covered.</p>
     */
    private static boolean chamferAlreadyArrives(Palette palette, int x, int z, Direction facing,
                                                 CellTest isWall, CellTest isOpen) {
        Direction ahead = haunchFacing(palette,
                x + facing.getStepX(), z + facing.getStepZ(), isWall, isOpen);
        return ahead == facing;
    }

    /**
     * A diagonal wall corner and the stair that caps it. {@code facing}/{@code shape} are chosen so
     * the solid quarter lands on the diagonal: with {@code half=top}, {@code outer_left} is the
     * counter-clockwise side of {@code facing} and {@code outer_right} the clockwise side, matching
     * vanilla's own reading of those values.
     */
    private record Corner(int dx, int dz, Direction facing, String shape) {}

    private static final Corner[] CORNERS = {
            new Corner(-1, -1, Direction.NORTH, "outer_left"),   // north-west
            new Corner(1, -1, Direction.NORTH, "outer_right"),   // north-east
            new Corner(-1, 1, Direction.SOUTH, "outer_right"),   // south-west
            new Corner(1, 1, Direction.SOUTH, "outer_left"),     // south-east
    };

    /**
     * Which way this cell's haunch leans, or {@code null} for none.
     *
     * <p>A haunch needs a wall to spring <em>from</em> and open corridor to spring <em>over</em>,
     * so a direction qualifies only when the neighbour that way is a wall and the neighbour the
     * opposite way is corridor. That single condition is what makes a 1-wide corridor degrade
     * correctly: walls on both sides means neither direction qualifies, so it stays flat rather
     * than arching itself shut.</p>
     *
     * <p>An inside corner has two qualifying directions; it takes the lowest {@link Direction}
     * ordinal (N, S, W, E), the same deterministic tie-break the courses work uses for
     * {@code orient}. Determinism matters more than which one wins &mdash; see the planner's
     * EnumMap fix for what a JVM-dependent choice costs.</p>
     */
    private static Direction haunchFacing(Palette palette, int x, int z, CellTest isWall, CellTest isOpen) {
        if (palette.arch == null) {
            return null;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int wx = x + d.getStepX();
            int wz = z + d.getStepZ();
            int ox = x - d.getStepX();
            int oz = z - d.getStepZ();
            if (isWall.test(wx, wz) && isOpen.test(ox, oz)) {
                return d;
            }
        }
        return null;
    }

    /** "Is the cell at these grid coords a wall / open corridor?", answered per build overload. */
    @FunctionalInterface
    private interface CellTest {
        boolean test(int x, int z);
    }

    /** A wall column {@code height} blocks tall (Y = floorY .. floorY+height-1). */
    private static void emitWallColumn(int x, int z, int floorY, int height, Palette palette,
                                       List<Course> courses, CellTest isOpen, RandomSource random,
                                       List<BlockPlacement> out) {
        Direction face = courseFacing(x, z, isOpen);
        for (int yOffset = 0; yOffset < height; yOffset++) {
            out.add(BlockStateCodec.placement(x, floorY + yOffset, z,
                    courseState(courses, yOffset, x, z, face, random, palette.wall)));
        }
    }

    /**
     * One resolved corridor course: a horizontal band at a known row of the wall column.
     *
     * <p>{@code row} is already anchored &mdash; the {@code bottom}/{@code top} arithmetic is done
     * once per build call in {@link #courses}, not per cell, because unlike a room every wall column
     * in a corridor is the same height.</p>
     */
    private record Course(BlockState block, BlockState alternate, int row,
                          WallPatternEntry.CourseOrient orient, WallPatternEntry.CourseAlternate mode) {}

    /**
     * The style's authored courses with their blocks resolved and their rows anchored against this
     * corridor's height.
     *
     * <p>A course that anchors outside the column is <strong>dropped, not clamped</strong> &mdash;
     * the same clipping convention a room's courses get, and it is what lets one authored style
     * carry a plinth and a crown without knowing whether this floor rolled 5 high or 8.</p>
     *
     * <h2>Anchors address the VISIBLE wall, not the whole column</h2>
     * <p>A wall column spans {@code floorY .. floorY + height - 1}, but its first and last rows can
     * never be seen. Row 0 sits at the same level as the corridor's own floor plane, walled in by
     * that floor beside it and by the column above it; row {@code height - 1} sits beside the
     * ceiling plane. Measured across 4 MEDIUM dungeons: <strong>0 of 5,279</strong> wall cells at
     * row 0 have a single air neighbour, and the top row is the same. The rows a player can
     * actually see are {@code 1 .. height - 2}.</p>
     *
     * <p>So {@code bottom}/0 is the lowest <em>visible</em> row and {@code top}/0 the highest. That
     * also makes this identical to a room's convention &mdash; {@code WallSurface.emit} writes at
     * {@code floorY + 1 + v}, so a room's {@code v} is this row minus one &mdash; which matters
     * because the two share {@link WallPatternEntry.CourseEntry} verbatim. Until Aug 2026 they did
     * not agree, and one authored course rendered in a room and vanished in a corridor. It was
     * reported as "large brick isn't in the corridors at all". It was there, in the one row nothing
     * can see.</p>
     */
    private List<Course> courses(CorridorStyle style, int height) {
        if (style.courses().isEmpty()) {
            return List.of();
        }
        List<Course> resolved = new ArrayList<>(style.courses().size());
        for (WallPatternEntry.CourseEntry entry : style.courses()) {
            int row = entry.anchor() == WallPatternEntry.CourseAnchor.TOP
                    ? height - 2 - entry.offset()
                    : 1 + entry.offset();
            if (row < 1 || row > height - 2) {
                continue;
            }
            resolved.add(new Course(
                    withProperties(entry.block(), entry.properties()),
                    withProperties(entry.alternateBlockOrBase(), entry.properties()),
                    row, entry.orient(), entry.alternate()));
        }
        return resolved;
    }

    private static BlockState withProperties(String block, Map<String, String> properties) {
        BlockState state = BlockStateCodec.block(block, Blocks.STONE_BRICKS);
        return properties.isEmpty() ? state : BlockStateCodec.withProperties(state, properties);
    }

    /**
     * The block for one cell of a wall column, or {@code fallback} when no course claims that row.
     *
     * <p>Later in the list wins, the same ordering-is-execution-order convention the room's courses
     * use. Randomness is drawn <strong>only</strong> when a {@code random}-mode course actually
     * claims the cell, so a motif that authors no courses consumes exactly the randomness it did
     * before and generates byte-identically.</p>
     */
    private static BlockState courseState(List<Course> courses, int yOffset, int x, int z,
                                          Direction face, RandomSource random, BlockState fallback) {
        BlockState state = fallback;
        for (Course course : courses) {
            if (course.row() != yOffset) {
                continue;
            }
            state = oriented(mixed(course, x, z, random), course.orient(), face);
        }
        return state;
    }

    /**
     * A course's block for one cell.
     *
     * <p>{@code random} is the room's own 45/55 split. {@code strict} alternates on
     * <strong>{@code (x + z)} parity</strong> rather than on a run coordinate, because a corridor
     * winds and has no {@code u}: parity alternates along both axes and carries through a 90° turn,
     * at the cost of a possible repeat at the turn cell itself. That is the same class of caveat
     * {@code WallSurface} already documents for a room's asymmetric patterns, and it is what a
     * mirrored block pair needs &mdash; mixed randomly the two halves stop reading as whole bricks.</p>
     */
    private static BlockState mixed(Course course, int x, int z, RandomSource random) {
        if (course.mode() == WallPatternEntry.CourseAlternate.STRICT) {
            return Math.floorMod(x + z, 2) == 0 ? course.block() : course.alternate();
        }
        return RandomHelper.checkProbability(random, 45) ? course.block() : course.alternate();
    }

    /**
     * Turns a course block to face the passage, mirroring the room provider's {@code oriented}.
     * {@code face} is the direction this wall cell's decorated side points, i.e. toward the corridor;
     * a stair's solid half sits on its own {@code facing} side, so a cornice against the wall wants
     * the opposite of it.
     */
    private static BlockState oriented(BlockState state, WallPatternEntry.CourseOrient orient,
                                       Direction face) {
        if (orient == WallPatternEntry.CourseOrient.NONE || face == null) {
            return state;
        }
        Direction facing = orient == WallPatternEntry.CourseOrient.TOWARD_WALL
                ? face.getOpposite()
                : face;
        return BlockStateCodec.withProperties(state, Map.of("facing", facing.getSerializedName()));
    }

    /**
     * Which way this wall cell's decorated face points: toward the corridor cell beside it.
     *
     * <p>{@code null} for a wall cell reached only diagonally &mdash; it has no face onto the
     * passage, so there is nothing to orient. It still takes the course block, which keeps the band
     * unbroken around an outside corner where the diagonal cell is the one a player sees end-on.</p>
     *
     * <p>A cell with corridor on two sides is an inside corner and faces both ways; it takes the
     * lowest {@link Direction} ordinal, the same deterministic tie-break {@link #haunchFacing} uses.
     * Determinism matters more than which one wins &mdash; see the planner's EnumMap fix.</p>
     */
    private static Direction courseFacing(int x, int z, CellTest isOpen) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (isOpen.test(x + d.getStepX(), z + d.getStepZ())) {
                return d;
            }
        }
        return null;
    }

    /**
     * A wall column with the two door-half levels left as air, so the doorway is
     * walkable and carries no full cube for the decoration pass to anchor to.
     * The sill ({@code floorY}) and lintel ({@code floorY+3}) levels stay solid:
     * they are full cubes in the finished doorway anyway, and keeping them means
     * a door piece that never runs leaves a 2-block gap rather than a full-height
     * hole in the corridor wall.
     *
     * <p>The pierced rows stay at exactly {@code +1}/{@code +2} whatever {@code height} is: the
     * doorway is the door piece's fixed 4-row column, not a fraction of the corridor.</p>
     */
    private static void emitDoorwayColumn(int x, int z, int floorY, int height, Palette palette,
                                          List<Course> courses, CellTest isOpen, RandomSource random,
                                          List<BlockPlacement> out) {
        Direction face = courseFacing(x, z, isOpen);
        for (int yOffset = 0; yOffset < height; yOffset++) {
            // The two pierced rows stay air whatever a course says: a band that filled them would
            // brick up the doorway, and unlike a room's projecting trim there is no cell to step
            // aside into. Every other row takes its course, so a band runs across a doorway's sill
            // and lintel rather than stopping dead at every opening.
            BlockState state = (yOffset == DOOR_HALF_LOW || yOffset == DOOR_HALF_HIGH)
                    ? palette.air
                    : courseState(courses, yOffset, x, z, face, random, palette.wall);
            out.add(BlockStateCodec.placement(x, floorY + yOffset, z, state));
        }
    }

    /**
     * Resolves the floor / wall / air / ceiling block states once per build call. The corridor has
     * its own floor pair and ceiling ({@code CorridorConfig}) but shares the room's wall block
     * ({@code WallConfig}), matching the pre-merge {@code block_provider} split.
     *
     * <p>Only the arch is per {@link CorridorStyle}: a style varies a floor's corridor
     * <em>geometry</em>, not what it is built of, so two floors of the same motif read as the same
     * place at different scales rather than as two motifs.</p>
     */
    private Palette palette(CorridorStyle style) {
        return new Palette(
                motifConfig.corridor().floorState(),
                motifConfig.corridor().alternateFloorState(),
                motifConfig.wall().wallState(),
                Blocks.AIR.defaultBlockState(),
                motifConfig.corridor().ceilingState(),
                motifConfig.corridor().archStateFor(style));
    }

    /** True if the cell at (x,z) is a wall-equivalent for corridor-wall placement. */
    private static boolean isWallElement(Grid2D grid, int x, int z) {
        if (x < 0 || z < 0 || x >= grid.getWidth() || z >= grid.getHeight()) {
            return true;
        }
        CellType type = grid.get(x, z).getType();
        return type == CellType.ROCK || type == CellType.WALL
                || type == CellType.DOOR || type == CellType.CONNECTOR;
    }

    /**
     * True if the cell at (x,z) is an opened doorway. CONNECTOR is deliberately
     * NOT included: an unopened connector is reverted to a plain wall and has no
     * door piece behind it, so piercing it would leave a hole.
     */
    private static boolean isDoorElement(Grid2D grid, int x, int z) {
        if (x < 0 || z < 0 || x >= grid.getWidth() || z >= grid.getHeight()) {
            return false;
        }
        return grid.get(x, z).getType() == CellType.DOOR;
    }

    /** Resolved block states for one corridor render pass. {@code arch} is null on a flat profile. */
    private record Palette(BlockState floor, BlockState alternateFloor, BlockState wall, BlockState air,
                            BlockState ceiling, BlockState arch) {}
}
