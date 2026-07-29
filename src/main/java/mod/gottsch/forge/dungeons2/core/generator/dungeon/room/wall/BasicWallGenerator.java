/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.pattern.wall.WallPattern;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns.WALL_PATTERN;

/**
 * Builds the four walls of a {@link RoomData}, plus interior air, as
 * {@link BlockPlacement}s.
 *
 * <p>Output coords are floor-local X/Z and absolute world Y. The room
 * occupies {@code [originX..originX+width-1] x [originZ..originZ+depth-1]}
 * on the floor; vertically it spans {@code [floorY+1..floorY+height-2]} for
 * walls/air (the floor block at Y=floorY and the ceiling at Y=floorY+height-1
 * are emitted by {@code BasicFloorGenerator} / {@code BasicCeilingGenerator}).</p>
 *
 * <p>Perimeter cells listed in {@link RoomData#getDoorways()} are emitted as
 * <strong>air</strong> at the two door-half levels ({@code floorY+1} /
 * {@code floorY+2}) instead of wall &mdash; see {@link #DOOR_HALF_LOW}.</p>
 *
 * @author Mark Gottschling on Mar 6, 2024 (Phase 2 rewrite May 25, 2026)
 */
public class BasicWallGenerator implements IDungeonWallGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    /**
     * Y offsets (above the floor surface) that {@code BasicDoorGenerator} fills
     * with the two door halves. The wall must NOT emit a solid block here: the
     * room's decoration pass runs before {@code DungeonDoorPiece} carves the door,
     * so a full cube in the door cell anchors glow lichen in the room air beside
     * it, facing the door cell. The door is then placed into that cell and the
     * lichen &mdash; a MultifaceBlock, rendered flush against its anchor's face
     * &mdash; ends up plastered onto the door. The door piece belongs to a
     * different piece entirely, so nothing on the processor side can see this
     * coming; removing the anchor is the only fix. The sill ({@code floorY}) and
     * lintel ({@code floorY+3}) stay solid &mdash; they are full cubes in the
     * finished doorway, so lichen against them is ordinary wall growth.
     */
    private static final int DOOR_HALF_LOW = 1;
    private static final int DOOR_HALF_HIGH = 2;

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        BlockSet blockSet = BlockProvider.get(motif, WALL_PATTERN, random);
        BlockState wallState = blockSet.get(WallPattern.WALL).orElse(DEFAULT);
        BlockState airState = Blocks.AIR.defaultBlockState();

        int width = room.getWidth();
        int depth = room.getDepth();
        int height = room.getHeight();
        int originX = room.getOriginX();
        int originZ = room.getOriginZ();

        // Doorway cells are floor-local grid coords, the same space as originX/originZ.
        Set<Coords2D> doorways = new HashSet<>(room.getDoorways());

        // Walls along the two x-axis edges (x=0 and x=width-1).
        int[] xEdges = {0, width - 1};
        for (int x : xEdges) {
            for (int z = 0; z < depth; z++) {
                boolean doorway = doorways.contains(new Coords2D(originX + x, originZ + z));
                for (int y = 1; y < height - 1; y++) {
                    out.add(BlockStateCodec.placement(
                            originX + x, floorY + y, originZ + z,
                            isDoorHalf(doorway, y) ? airState : wallState));
                }
            }
        }
        // Walls along the two z-axis edges (z=0 and z=depth-1).
        int[] zEdges = {0, depth - 1};
        for (int z : zEdges) {
            for (int x = 0; x < width; x++) {
                boolean doorway = doorways.contains(new Coords2D(originX + x, originZ + z));
                for (int y = 1; y < height - 1; y++) {
                    out.add(BlockStateCodec.placement(
                            originX + x, floorY + y, originZ + z,
                            isDoorHalf(doorway, y) ? airState : wallState));
                }
            }
        }
        // Air filling the room interior (between the walls, above the floor).
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                for (int y = 1; y < height - 1; y++) {
                    out.add(BlockStateCodec.placement(
                            originX + x, floorY + y, originZ + z, airState));
                }
            }
        }
    }

    /** True when (doorway cell, wall-relative Y) is one of the two door-half levels. */
    private static boolean isDoorHalf(boolean doorway, int y) {
        return doorway && (y == DOOR_HALF_LOW || y == DOOR_HALF_HIGH);
    }
}
