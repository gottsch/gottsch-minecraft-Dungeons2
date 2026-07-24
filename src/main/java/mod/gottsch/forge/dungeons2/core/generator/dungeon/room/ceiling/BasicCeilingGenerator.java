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

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.pattern.ceiling.CeilingPattern;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static mod.gottsch.forge.dungeons2.core.decorator.DungeonRoomPatterns.CEILING_PATTERN;

/**
 * Builds the ceiling of a {@link RoomData} as {@link BlockPlacement}s.
 *
 * <p>Ceiling occupies the interior cells (1 inset from the walls) at world
 * Y={@code floorY + height - 1}.</p>
 *
 * @author Mark Gottschling on Mar 6, 2024 (Phase 2 rewrite May 25, 2026)
 */
public class BasicCeilingGenerator implements IDungeonCeilingGenerator {
    private static final BlockState DEFAULT = Blocks.STONE_BRICKS.defaultBlockState();

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        BlockSet blockSet = BlockProvider.get(motif, CEILING_PATTERN, random);
        BlockState ceilingState = blockSet.get(CeilingPattern.CEILING).orElse(DEFAULT);

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
