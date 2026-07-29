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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Wall growth: a {@link DecorationRule} plus the two knobs that make it <em>cluster</em>
 * instead of speckling evenly.
 *
 * <p>A candidate's chance is {@code probability + n × bonus}, capped at {@code max}, where
 * {@code n} is how many of its six neighbours are already growth. With a low
 * {@code probability} and a high {@code bonus}, growth is rare to start but spreads once
 * started, which is what makes it read as patches on a wall rather than static.</p>
 *
 * @author Mark Gottschling on Jul 28, 2026
 */
public record WallGrowthRule(float probability, float bonus, float max, List<Block> blocks) {

    public static final WallGrowthRule NONE = new WallGrowthRule(0.0F, 0.0F, 1.0F, List.of());

    public static final Codec<WallGrowthRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("probability", 0.0F).forGetter(WallGrowthRule::probability),
            Codec.FLOAT.optionalFieldOf("bonus", 0.0F).forGetter(WallGrowthRule::bonus),
            Codec.FLOAT.optionalFieldOf("max", 1.0F).forGetter(WallGrowthRule::max),
            BuiltInRegistries.BLOCK.byNameCodec().listOf()
                    .optionalFieldOf("blocks", List.of()).forGetter(WallGrowthRule::blocks)
    ).apply(instance, WallGrowthRule::new));

    public WallGrowthRule {
        blocks = List.copyOf(blocks);
    }

    public boolean isActive() {
        return probability > 0.0F && !blocks.isEmpty();
    }

    /** The chance for a candidate with {@code adjacentGrowth} growth blocks touching it. */
    public float chanceWith(int adjacentGrowth) {
        return Math.min(max, probability + adjacentGrowth * bonus);
    }

    public Block pick(RandomSource random) {
        return blocks.get(random.nextInt(blocks.size()));
    }
}
