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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Clears the air a room stands in: every interior cell between the walls, above the floor and below
 * the ceiling &mdash; {@code [1..width-2] x [1..height-2] x [1..depth-2]} in room-local coords.
 *
 * <h2>Why this is its own step</h2>
 * <p>{@code BasicWallGenerator} used to emit this alongside the wall faces, which conflated two
 * different jobs: the wall <em>surface</em>, and the room <em>volume</em>. Nothing noticed while
 * every room was a hollow box, because the two never overlap &mdash; walls occupy only the
 * perimeter ring, interior air only the inside of it.</p>
 *
 * <p>It stops being harmless the moment anything wants to stand <em>inside</em> the room: a pillar,
 * a vaulted ceiling springing below the ceiling plane, an altar, a hanging feature. Those all
 * occupy cells this fill claims, so with the fill owned by the wall generator, every one of them
 * would have had to either reach into wall code or run after it and hope the ordering held. Pulling
 * it out first makes "the room is hollow" a precondition that later steps build on top of, rather
 * than a side effect of one of them.</p>
 *
 * <p>Consequently this step stays deliberately dumb: it fills the whole interior unconditionally
 * and lets later steps overwrite what they want, per the ordering-is-execution-order convention the
 * {@code processor_list} files and {@code CompositeFloorPatternProvider} already use. A vault does
 * not ask this to leave room for it; it simply runs afterwards and wins the cells it needs.</p>
 *
 * <p>No interface behind it yet &mdash; there is exactly one way to hollow a rectangular room. When
 * a second shape appears (an irregular or cave-like volume), that is the moment to add one, the same
 * don't-over-commit reasoning {@code FloorPatternEntry} applies to its {@code type} discriminator.
 * </p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public final class RoomVolumeGenerator {

    private RoomVolumeGenerator() {}

    /**
     * Emits air for the room's interior. Touches neither the floor plane ({@code floorY}) nor the
     * ceiling plane ({@code floorY + height - 1}) nor the perimeter walls, so it never competes
     * with the three surface generators regardless of the order they run in.
     */
    public static void hollow(RoomData room, int floorY, List<BlockPlacement> out) {
        BlockState airState = Blocks.AIR.defaultBlockState();

        int width = room.getWidth();
        int depth = room.getDepth();
        int height = room.getHeight();
        int originX = room.getOriginX();
        int originZ = room.getOriginZ();

        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                for (int y = 1; y < height - 1; y++) {
                    out.add(BlockStateCodec.placement(
                            originX + x, floorY + y, originZ + z, airState));
                }
            }
        }
    }
}
