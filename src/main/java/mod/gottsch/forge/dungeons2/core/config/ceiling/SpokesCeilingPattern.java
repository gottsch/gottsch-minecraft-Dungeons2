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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RadialSpokesFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.MaskSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Lines radiating from the centre: the floor's {@code spokes}, same geometry.
 *
 * <p>Only {@code block} is placed; the other cells stay the ceiling's base. {@code inset}
 * confines the pattern to the panel inside a {@code border} at a smaller inset.</p>
 */
public record SpokesCeilingPattern(String block, int spokes, int inset, Map<String, String> properties) implements CeilingPattern {

    public static final String NAME = "spokes";

    /** See {@link CeilingPattern#withRoles}. */
    @Override
    public CeilingPattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        if (resolvedBlock.equals(block)) {
            return this;
        }
        return new SpokesCeilingPattern(resolvedBlock, spokes, inset, properties);
    }

    public static final MapCodec<SpokesCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(SpokesCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "spokes",
                                    RadialSpokesFloorPatternProvider.DEFAULT_SPOKES)
                            .forGetter(SpokesCeilingPattern::spokes),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset", 0)
                            .forGetter(SpokesCeilingPattern::inset),
                    CeilingPattern.<SpokesCeilingPattern>propertiesField(SpokesCeilingPattern::properties)
            ).apply(instance, SpokesCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    @Override
    public void addLayers(int projection, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state != null) {
            out.add(new Layer(projection, new MaskSurfacePatternProvider(inset, state,
                    (u, v) -> RadialSpokesFloorPatternProvider.plan(u, v, spokes))));
        }
    }
}
