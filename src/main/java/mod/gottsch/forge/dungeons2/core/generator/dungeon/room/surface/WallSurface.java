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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One run of wall, and the mapping between a {@link SurfacePlan}'s {@code (u, v)} space and world
 * coordinates. Four of these make a room.
 *
 * <h2>Why patterns are authored per-run, not in room space</h2>
 * <p>A wall pattern that places a directional block &mdash; a stairs cornice, a slab ledge &mdash;
 * needs to know which way the surface faces, and the four walls of a room face four different ways.
 * Authoring in room {@code (x, z)} would force every provider to work that out per cell. Authoring
 * in {@code (u, v)} with a {@link #facing} attached to the run means a provider writes one pattern
 * and gets it applied correctly to all four walls.</p>
 *
 * <h2>Corner ownership</h2>
 * <p>The four corner columns belong to <strong>the Z-edge runs</strong> ({@link #facing} SOUTH and
 * NORTH, which span the full width); the X-edge runs cover the interior depth only. Some such rule
 * is mandatory. The pre-Aug-2026 wall generator emitted both edge loops at full length, so every
 * corner column was written twice; harmless when both writes are the same wall block, but it means
 * a pattern with any horizontal rhythm would have had its corners silently decided by whichever
 * loop ran last.</p>
 *
 * <p>A consequence worth knowing before authoring: {@code u} always advances along <strong>+X or
 * +Z</strong>, never mirrored to run clockwise around the room. Symmetric patterns (courses,
 * centred panels, evenly spaced pilasters) are unaffected; a deliberately asymmetric one reads
 * mirrored on opposite walls. Runs are symmetric about their own centre under this rule, which is
 * what a centred pattern actually wants &mdash; a pinwheel convention would make every run an odd
 * length and put every centred feature half a cell off.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public record WallSurface(int startX, int startZ, int stepX, int stepZ, int length, Direction facing) {

    /**
     * Y offsets above the floor plane that {@code BasicDoorGenerator} fills with the two door
     * halves, in {@code v} (so {@code v = 0} is the lowest wall row, world Y {@code floorY + 1}).
     *
     * <p>A wall must not emit a solid block here: the room's decoration pass runs before
     * {@code DungeonDoorPiece} carves the door, so a full cube in the door cell anchors glow lichen
     * in the room air beside it, facing the door cell. The door is then placed into that cell and
     * the lichen &mdash; a MultifaceBlock, rendered flush against its anchor's face &mdash; ends up
     * plastered onto the door. The door piece belongs to a different piece entirely, so nothing on
     * the processor side can see this coming; removing the anchor is the only fix. The sill
     * ({@code floorY}) and lintel ({@code v = 2}) stay solid &mdash; they are full cubes in the
     * finished doorway, so lichen against them is ordinary wall growth.</p>
     */
    public static final int DOOR_HALF_LOW_V = 0;
    public static final int DOOR_HALF_HIGH_V = 1;

    /**
     * The four wall runs of a room, in floor-local coords. Runs of non-positive length (a room too
     * thin to have interior depth) are still returned, and simply emit nothing.
     */
    public static List<WallSurface> forRoom(RoomData room) {
        int width = room.getWidth();
        int depth = room.getDepth();
        int originX = room.getOriginX();
        int originZ = room.getOriginZ();

        List<WallSurface> surfaces = new ArrayList<>(4);
        // Z-edge runs: full width, and they own the corner columns.
        surfaces.add(new WallSurface(originX, originZ, 1, 0, width, Direction.SOUTH));
        surfaces.add(new WallSurface(originX, originZ + depth - 1, 1, 0, width, Direction.NORTH));
        // X-edge runs: interior depth only, corners already taken.
        surfaces.add(new WallSurface(originX, originZ + 1, 0, 1, depth - 2, Direction.EAST));
        surfaces.add(new WallSurface(originX + width - 1, originZ + 1, 0, 1, depth - 2, Direction.WEST));
        return surfaces;
    }

    /**
     * The {@code u} positions on this run that are doorway cells (#72).
     *
     * <p>Lives here because this is the class whose {@link #xAt}/{@link #zAt} define the mapping
     * from a run position to the floor-local space the doorways are stored in &mdash; the same
     * reason {@code CeilingSurface} owns its axis directions. A pattern is given these rather than
     * the doorway coordinates so it never has to leave {@code (u, v)}; see
     * {@link IDoorAwarePatternProvider}.</p>
     *
     * <p>Note a 2-wide door is two ADJACENT columns here, because the maze stores it as two doorway
     * cells and this makes no attempt to merge them.</p>
     */
    public Set<Integer> doorColumns(Set<Coords2D> doorways) {
        if (doorways.isEmpty()) {
            return Set.of();
        }
        Set<Integer> columns = new HashSet<>();
        for (int u = 0; u < length; u++) {
            if (doorways.contains(new Coords2D(xAt(u), zAt(u)))) {
                columns.add(u);
            }
        }
        return columns;
    }

    /** Floor-local X of the cell at position {@code u} along this run. */
    public int xAt(int u) {
        return startX + stepX * u;
    }

    /** Floor-local Z of the cell at position {@code u} along this run. */
    public int zAt(int u) {
        return startZ + stepZ * u;
    }

    /**
     * Whether this run includes the room's corner columns &mdash; true for the Z-edge runs, which
     * span the full width. Derived rather than stored: only those runs step in X.
     */
    public boolean spansCorners() {
        return stepX != 0;
    }

    /**
     * First {@code u} whose projection at {@code depth} lands on a cell this run should own.
     *
     * <h2>Why a projection cannot simply cover the whole run</h2>
     * <p>Projecting moves a cell perpendicular to its own wall, which leaves the corner columns in
     * the wrong place. A Z-edge run's cell at {@code u = 0} sits in the <em>X-edge wall's</em>
     * column; pushing it into the room moves it along Z only, so it lands inside that wall rather
     * than in the room. Emitting it would put a stair block in the middle of a wall.</p>
     *
     * <p>One step further in, at {@code u = depth}, the cell lands exactly where the X-edge run's
     * own projection goes &mdash; correct, but owned by two runs with different facings, so the
     * corner's orientation would be decided by whichever ran last. Ceding those columns to the
     * X-edge runs gives every cell of the projected ring exactly one owner and one facing.</p>
     *
     * <p>The result is a complete ring: the X-edge runs supply the two side columns and the four
     * corners, the Z-edge runs everything between. This is the corner-ownership rule of
     * {@link #forRoom} applied one layer in &mdash; and it is why a flat course needs no such rule
     * (it never leaves its own wall) while a projecting one does.</p>
     */
    private int projectableFrom(int depth) {
        return projectableFrom(spansCorners(), depth);
    }

    /** Last {@code u} whose projection at {@code depth} belongs to this run. See {@link #projectableFrom}. */
    private int projectableTo(int depth) {
        return projectableTo(spansCorners(), length, depth);
    }

    /**
     * {@link #projectableFrom} as a pure function, for a provider that must plan <em>within</em> the
     * window rather than merely have its out-of-window cells discarded.
     *
     * <p>A pattern spread evenly across a run &mdash; pilasters &mdash; needs this. Centring the
     * strips over the full run and letting {@link #emitProjected} drop the ones that fall outside
     * loses exactly the outermost strips, so a 9-wide wall at spacing 4 keeps one of three. Centring
     * them over the window keeps all of them. A course does not care, since it spans the run and is
     * simply clipped.</p>
     *
     * <p>{@code spansCorners} is derivable from the run's facing &mdash; the Z-facing runs are the
     * ones that step in X &mdash; which is how a provider, handed only an extent and a facing, can
     * ask. Same equivalence {@code CoursesWallPatternProvider#ownsCorners} relies on.</p>
     */
    public static int projectableFrom(boolean spansCorners, int depth) {
        return spansCorners ? depth + 1 : 0;
    }

    /** See {@link #projectableFrom(boolean, int)}. */
    public static int projectableTo(boolean spansCorners, int length, int depth) {
        return spansCorners ? length - 2 - depth : length - 1;
    }

    /**
     * Writes a layer standing {@code depth} cells out from this wall into the room &mdash; a
     * cornice, a moulding, a ledge.
     *
     * <p>Two rules, both of which a pattern authored in {@code (u, v)} cannot apply for itself:</p>
     * <ul>
     *   <li><strong>Only marked cells are written.</strong> Unlike {@link #emit}, a null cell here
     *       is not "use the base block" &mdash; this layer is the room's open air, and filling it
     *       would wall the room in.</li>
     *   <li><strong>Nothing is placed in front of a doorway at door height</strong>, and a pattern
     *       that wanted to loses the <em>whole column</em>. A block at door height stands in the
     *       opening a player walks through; but simply skipping those two cells is only right for a
     *       pattern that has nothing else in that column. A pilaster runs the full height, so
     *       dropping two cells out of it leaves a column of trim floating above the doorway with a
     *       gap where it meets the floor &mdash; worse than not drawing it. See
     *       {@link #blockedByDoorway}.</li>
     * </ul>
     */
    public void emitProjected(SurfacePlan plan, int depth, int floorY, Set<Coords2D> doorways,
                              List<BlockPlacement> out) {
        for (int u = projectableFrom(depth); u <= projectableTo(depth); u++) {
            int x = xAt(u) + facing.getStepX() * depth;
            int z = zAt(u) + facing.getStepZ() * depth;
            if (doorways.contains(new Coords2D(xAt(u), zAt(u))) && blockedByDoorway(plan, u)) {
                continue;
            }
            for (int v = 0; v < plan.vSize(); v++) {
                BlockState planned = plan.get(u, v);
                if (planned != null) {
                    out.add(BlockStateCodec.placement(x, floorY + 1 + v, z, planned));
                }
            }
        }
    }

    /**
     * Whether a projecting pattern's column at {@code u} has to be dropped entirely because it
     * stands in a doorway.
     *
     * <p>The test is whether the pattern marks either <em>door-half</em> row. That single question
     * separates the two cases correctly without the pattern having to know doorways exist, which it
     * cannot: a cornice or a crown sits high on the wall, marks neither row, and draws straight over
     * the opening as it should &mdash; the lintel is solid there anyway. A pilaster marks every row
     * including those two, so it is dropped whole and the doorway is clear.</p>
     *
     * <p>Deciding it from the plan rather than from the provider is what keeps this a property of
     * the <em>geometry</em>. A pattern that grows a new shape gets the right answer without anyone
     * remembering to classify it, which is the same reason the door mask itself lives in
     * {@link #emit} rather than in each provider.</p>
     */
    private static boolean blockedByDoorway(SurfacePlan plan, int u) {
        return plan.get(u, DOOR_HALF_LOW_V) != null || plan.get(u, DOOR_HALF_HIGH_V) != null;
    }

    /**
     * Writes this run out: {@code plan}'s non-null cells, the {@code base} block everywhere else,
     * and <strong>air at the two door-half rows of any doorway cell</strong>.
     *
     * <p>The doorway mask is applied here, after the pattern, and that placement is deliberate. It
     * is not a cosmetic rule a provider may reasonably decide to override &mdash; a solid block in
     * a door cell is the lichen-on-doors bug (see {@link #DOOR_HALF_LOW_V}). Applying it centrally
     * means no wall pattern can reintroduce that fault by forgetting about doors, which every
     * pattern otherwise would, since doors are invisible in {@code (u, v)} space.</p>
     */
    public void emit(SurfacePlan plan, int floorY, Set<Coords2D> doorways, BlockState base,
                     List<BlockPlacement> out) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int u = 0; u < length; u++) {
            int x = xAt(u);
            int z = zAt(u);
            boolean doorway = doorways.contains(new Coords2D(x, z));
            for (int v = 0; v < plan.vSize(); v++) {
                BlockState state;
                if (doorway && (v == DOOR_HALF_LOW_V || v == DOOR_HALF_HIGH_V)) {
                    state = air;
                } else {
                    BlockState planned = plan.get(u, v);
                    state = planned != null ? planned : base;
                }
                out.add(BlockStateCodec.placement(x, floorY + 1 + v, z, state));
            }
        }
    }
}
