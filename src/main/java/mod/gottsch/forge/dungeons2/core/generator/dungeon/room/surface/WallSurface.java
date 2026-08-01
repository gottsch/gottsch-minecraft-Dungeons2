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

    /** Floor-local X of the cell at position {@code u} along this run. */
    public int xAt(int u) {
        return startX + stepX * u;
    }

    /** Floor-local Z of the cell at position {@code u} along this run. */
    public int zAt(int u) {
        return startZ + stepZ * u;
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
