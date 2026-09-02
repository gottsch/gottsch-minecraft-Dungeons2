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
package mod.gottsch.forge.dungeons2.core.config.ceiling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector.Layer;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.FieldSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Every cell at or inside {@code inset} &mdash; the filled counterpart of {@link
 * BorderCeilingPattern}'s ring, and the step a RISING vault is built from (#68).
 *
 * <p>A hanging vault is authored as rings, because only its perimeter moves. A rising one needs the
 * whole inner area to move, so it needs an area: see {@link FieldSurfacePatternProvider} for why a
 * stack of rings cannot express it. A three-step rising vault is three of these, at
 * {@code inset} 1/2/3 and {@code rise} 1/2/3.</p>
 *
 * <p>Declares no {@code orient}: a field has no edge to face. Where a vault wants a springing course
 * of stairs at its lip, that is a {@code border} at the same rise, listed before the field.</p>
 */
public record FieldCeilingPattern(String block, int inset, Map<String, String> properties)
        implements CeilingPattern {

    public static final String NAME = "field";

    /** See {@link CeilingPattern#withRoles}. */
    @Override
    public CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        if (resolvedBlock.equals(block)) {
            return this;
        }
        return new FieldCeilingPattern(resolvedBlock, inset, properties);
    }

    /** The whole ceiling in one material, flush. */
    public FieldCeilingPattern(String block) {
        this(block, FieldSurfacePatternProvider.DEFAULT_INSET, Map.of());
    }

    public static final MapCodec<FieldCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(FieldCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                                    FieldSurfacePatternProvider.DEFAULT_INSET)
                            .forGetter(FieldCeilingPattern::inset),
                    CeilingPattern.<FieldCeilingPattern>propertiesField(FieldCeilingPattern::properties)
            ).apply(instance, FieldCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    @Override
    public void addLayers(int depth, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state != null) {
            out.add(new Layer(depth, new FieldSurfacePatternProvider(inset, state)));
        }
    }
}
