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

import java.util.List;
import java.util.Optional;

/**
 * One weathering/decay rule consumed by {@code BlockSubstitutor}: blocks matching
 * {@link #from} are deterministically swapped for a weighted pick of {@link #to}
 * variants. {@link #chance} and {@link #weights} are optional (defaults applied in
 * the substitutor). This is the datapack analogue of the old
 * {@code BlockProviderConfiguration.Substitution}, now split out of the providers file.
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
public record SubstitutionRule(ResourceLocation from, List<ResourceLocation> to,
                               Optional<Double> chance, Optional<List<Integer>> weights) {

    public static final Codec<SubstitutionRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("from").forGetter(SubstitutionRule::from),
            ResourceLocation.CODEC.listOf().fieldOf("to").forGetter(SubstitutionRule::to),
            Codec.DOUBLE.optionalFieldOf("chance").forGetter(SubstitutionRule::chance),
            Codec.INT.listOf().optionalFieldOf("weights").forGetter(SubstitutionRule::weights)
    ).apply(instance, SubstitutionRule::new));
}
