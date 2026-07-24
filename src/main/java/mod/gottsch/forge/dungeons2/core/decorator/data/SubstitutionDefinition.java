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

/**
 * A motif's full weathering table, loaded from
 * {@code data/<namespace>/substitution/<motif>.json}: an ordered list of
 * {@link SubstitutionRule}s.
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
public record SubstitutionDefinition(List<SubstitutionRule> rules) {

    public static final Codec<SubstitutionDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SubstitutionRule.CODEC.listOf().fieldOf("rules").forGetter(SubstitutionDefinition::rules)
    ).apply(instance, SubstitutionDefinition::new));
}
