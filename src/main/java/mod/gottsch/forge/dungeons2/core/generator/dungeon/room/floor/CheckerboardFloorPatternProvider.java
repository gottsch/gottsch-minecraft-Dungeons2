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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * Fills the whole floor alternating {@code primaryBlock}/{@code secondaryBlock} by {@code (x + z)
 * % 2}, 1x1 cells, no inset &mdash; a plain checkerboard fill rather than a ring like {@link
 * FloorBorderPatternProvider}. Both blocks are required per instance, sourced from {@code
 * floor_pattern_config} (see {@code FloorPatternEntry}'s {@code primaryBlock}/{@code
 * secondaryBlock} fields) &mdash; there is deliberately no Java-side default block for either
 * slot; {@code FloorPatternSelector} degrades a {@code "checkerboard"} entry to plain floor rather
 * than constructing this class with a guessed block when either fails to resolve. Configuring both
 * to the same block makes the checkerboard simply invisible, same graceful degradation the border
 * pattern's single-block-edge case already relies on.
 *
 * <p>Meant to be used as the base fill of a {@code "composite"} entry with a {@link
 * FloorBorderPatternProvider} ring layered on top (see {@link CompositeFloorPatternProvider}), but
 * works standalone too as its own {@code "checkerboard"} {@code type}.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public class CheckerboardFloorPatternProvider implements IDungeonFloorGenerator {

    private final Block primaryBlock;
    private final Block secondaryBlock;

    public CheckerboardFloorPatternProvider(Block primaryBlock, Block secondaryBlock) {
        this.primaryBlock = Objects.requireNonNull(primaryBlock, "primary_block");
        this.secondaryBlock = Objects.requireNonNull(secondaryBlock, "secondary_block");
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out);
    }

    /**
     * Builds the pattern for a floor of the given size at the given origin, independent of
     * {@link RoomData} (e.g. for use outside the room pipeline).
     */
    public void build(int width, int depth, int originX, int originZ, int floorY, List<BlockPlacement> out) {
        BlockState primary = primaryBlock.defaultBlockState();
        BlockState secondary = secondaryBlock.defaultBlockState();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                BlockState state = (x + z) % 2 == 0 ? primary : secondary;
                out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, state));
            }
        }
    }
}
