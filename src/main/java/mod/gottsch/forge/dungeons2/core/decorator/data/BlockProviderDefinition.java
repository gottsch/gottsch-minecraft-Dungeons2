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

import java.util.List;
import java.util.Map;

/**
 * The decorator/material palette for a single motif, loaded from
 * {@code data/<namespace>/block_provider/<motif>.json}. Maps each pattern id
 * (e.g. {@code "wall_pattern"}) to an ordered list of {@link BlockSetDefinition}s
 * &mdash; mirroring the runtime {@code BlockProvider}'s
 * {@code Multimap<patternId, BlockSet>} so population is a near-trivial copy.
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
public record BlockProviderDefinition(Map<String, List<BlockSetDefinition>> patterns) {

    public static final Codec<BlockProviderDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, BlockSetDefinition.CODEC.listOf()).fieldOf("patterns")
                    .forGetter(BlockProviderDefinition::patterns)
    ).apply(instance, BlockProviderDefinition::new));
}
