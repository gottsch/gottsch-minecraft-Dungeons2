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
 * Fills the whole floor with {@code baseBlock}, sprinkling {@code accentBlock} in at
 * {@code probability} per cell &mdash; a rarer, randomized cousin of {@link
 * CheckerboardFloorPatternProvider}'s regular alternation. Unlike the border/checkerboard
 * patterns, this one's output isn't a pure function of {@code (x, z)}: it actually consumes the
 * room's {@code random}, same as {@link BasicFloorGenerator}'s own primary/alternate roll.
 *
 * <p>Both blocks are required per instance, sourced from {@code floor_pattern_config} (see
 * {@code FloorPatternEntry}'s {@code primaryBlock}/{@code secondaryBlock} fields, reused here for
 * base/accent) &mdash; there is deliberately no Java-side default block for either slot; {@code
 * FloorPatternSelector} degrades a {@code "speckle"} entry to plain floor rather than constructing
 * this class with a guessed block when either fails to resolve. {@code probability} keeps its own
 * default ({@link #DEFAULT_PROBABILITY}) since it's a pattern-shape knob, not a motif-scoped
 * material; a probability of {@code 0} makes the accent simply never appear.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public class RandomSpeckleFloorPatternProvider implements IDungeonFloorGenerator {
    /** 1 cell in 20, on average. */
    public static final double DEFAULT_PROBABILITY = 0.05;

    private final double probability;
    private final Block baseBlock;
    private final Block accentBlock;

    public RandomSpeckleFloorPatternProvider(double probability, Block baseBlock, Block accentBlock) {
        this.probability = probability;
        this.baseBlock = Objects.requireNonNull(baseBlock, "base_block");
        this.accentBlock = Objects.requireNonNull(accentBlock, "accentBlock");
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, random, out);
    }

    /**
     * Builds the pattern for a floor of the given size at the given origin, independent of
     * {@link RoomData} (e.g. for use outside the room pipeline).
     */
    public void build(int width, int depth, int originX, int originZ, int floorY, RandomSource random,
                       List<BlockPlacement> out) {
        BlockState base = baseBlock.defaultBlockState();
        BlockState accent = accentBlock.defaultBlockState();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                BlockState state = random.nextFloat() < probability ? accent : base;
                out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, state));
            }
        }
    }
}
