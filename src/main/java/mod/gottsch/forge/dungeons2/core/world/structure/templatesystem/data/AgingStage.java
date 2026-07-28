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
import net.minecraft.world.level.block.Block;

/**
 * One step in an {@link AgingRule}'s decay chain: the block this stage degrades to,
 * and the chance of reaching it <em>given the previous stage was reached</em>.
 *
 * <p>The block is resolved by the codec, so an unknown id fails the datapack file
 * loudly at load rather than silently producing an un-aged dungeon.</p>
 *
 * @author Mark Gottschling on Jul 27, 2026
 */
public record AgingStage(Block block, double probability) {

    public static final Codec<AgingStage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("block").forGetter(AgingStage::block),
            Codec.DOUBLE.optionalFieldOf("probability", 0.0D).forGetter(AgingStage::probability)
    ).apply(instance, AgingStage::new));
}
