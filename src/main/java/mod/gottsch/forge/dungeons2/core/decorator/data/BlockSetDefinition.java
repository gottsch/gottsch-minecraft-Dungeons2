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
package mod.gottsch.forge.dungeons2.core.decorator.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * One block set within a pattern: a named bundle mapping pattern-element ids
 * (e.g. {@code "wall"}, {@code "corner"}) to block ids. Block ids are kept as
 * {@link ResourceLocation} and resolved tolerantly to a {@code Block} at
 * registry-population time, so an absent optional mod (e.g. {@code dungeonblocks:*})
 * degrades to air instead of failing the whole file.
 *
 * <p>Datapack analogue of the runtime {@code core.decorator.BlockSet}.
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
public record BlockSetDefinition(String id, Map<String, ResourceLocation> elements) {

    public static final Codec<BlockSetDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(BlockSetDefinition::id),
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).fieldOf("elements")
                    .forGetter(BlockSetDefinition::elements)
    ).apply(instance, BlockSetDefinition::new));
}
