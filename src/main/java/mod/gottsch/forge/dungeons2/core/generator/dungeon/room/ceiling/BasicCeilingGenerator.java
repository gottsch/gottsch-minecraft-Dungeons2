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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Builds the ceiling of a {@link RoomData} as {@link BlockPlacement}s.
 *
 * <p>Ceiling occupies the interior cells (1 inset from the walls) at world
 * Y={@code floorY + height - 1}.</p>
 *
 * @author Mark Gottschling on Mar 6, 2024 (Phase 2 rewrite May 25, 2026)
 */
public class BasicCeilingGenerator implements IDungeonCeilingGenerator {

    private MotifConfig motifConfig = MotifConfig.DEFAULT;

    /** See {@code BasicWallGenerator#withMotifConfig}. */
    public BasicCeilingGenerator withMotifConfig(MotifConfig motifConfig) {
        this.motifConfig = motifConfig;
        return this;
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        BlockState ceilingState = motifConfig.ceiling().ceilingState();

        int width = room.getWidth();
        int depth = room.getDepth();
        int height = room.getHeight();
        int originX = room.getOriginX();
        int originZ = room.getOriginZ();
        int ceilingY = floorY + height - 1;

        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                out.add(BlockStateCodec.placement(
                        originX + x, ceilingY, originZ + z, ceilingState));
            }
        }
    }
}
