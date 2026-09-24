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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.CrossFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.MaskSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Two bands crossing at the centre, {@code thickness} wide: the floor's {@code cross}, same
 * geometry.
 *
 * <p>Only {@code block} is placed; the other cells stay the ceiling's base. {@code inset}
 * confines the pattern to the panel inside a {@code border} at a smaller inset.</p>
 */
public record CrossCeilingPattern(String block, int thickness, int inset, Map<String, String> properties) implements CeilingPattern {

    public static final String NAME = "cross";

    /** See {@link CeilingPattern#withRoles}. */
    @Override
    public CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        if (resolvedBlock.equals(block)) {
            return this;
        }
        return new CrossCeilingPattern(resolvedBlock, thickness, inset, properties);
    }

    public static final MapCodec<CrossCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(CrossCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "thickness",
                                    CrossFloorPatternProvider.DEFAULT_THICKNESS)
                            .forGetter(CrossCeilingPattern::thickness),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset", 0)
                            .forGetter(CrossCeilingPattern::inset),
                    CeilingPattern.<CrossCeilingPattern>propertiesField(CrossCeilingPattern::properties)
            ).apply(instance, CrossCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    @Override
    public void addLayers(int projection, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state != null) {
            out.add(new Layer(projection, new MaskSurfacePatternProvider(inset, state,
                    (u, v) -> CrossFloorPatternProvider.plan(u, v, thickness))));
        }
    }
}
