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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The room ceiling section of a {@link MotifConfig}. The corridor's own ceiling is separate, in
 * {@link CorridorConfig} &mdash; they were separate patterns pre-merge too
 * ({@code ceiling_pattern} vs {@code corridor_ceiling_pattern}).
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record CeilingConfig(String ceiling) {

    public static final CeilingConfig DEFAULT = new CeilingConfig("minecraft:stone_bricks");

    public static final Codec<CeilingConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ceiling").forGetter(CeilingConfig::ceiling)
    ).apply(instance, CeilingConfig::new));

    public BlockState ceilingState() {
        return BlockStateCodec.block(ceiling, Blocks.STONE_BRICKS);
    }
}
