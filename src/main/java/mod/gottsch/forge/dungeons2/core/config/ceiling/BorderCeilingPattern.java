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
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling.CeilingPatternSelector.Layer;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CeilingSurface;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A ring following the ceiling's edge, {@code inset} cells in, with an optional distinct
 * {@code cornerBlock}.
 *
 * <p>{@code cornerBlock} falls back to {@code block} rather than dropping the ring: a typo in the
 * trim should not delete the border it was decorating.</p>
 */
public record BorderCeilingPattern(String block, Optional<String> cornerBlock, int inset,
                                   SurfaceOrient orient, Map<String, String> properties)
        implements CeilingPattern {

    public static final String NAME = "border";

    /** A plain ring of one block, flush and unoriented. */
    public BorderCeilingPattern(String block) {
        this(block, Optional.empty(), BorderSurfacePatternProvider.DEFAULT_INSET,
                SurfaceOrient.NONE, Map.of());
    }

    public static final MapCodec<BorderCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("block").forGetter(BorderCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.STRING, "cornerBlock")
                            .forGetter(BorderCeilingPattern::cornerBlock),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                                    BorderSurfacePatternProvider.DEFAULT_INSET)
                            .forGetter(BorderCeilingPattern::inset),
                    // One of the two types that declares `orient` at all -- see CeilingPattern.
                    Codecs.strictOptionalFieldOf(SurfaceOrient.CODEC, "orient", SurfaceOrient.NONE)
                            .forGetter(BorderCeilingPattern::orient),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(BorderCeilingPattern::properties)
            ).apply(instance, BorderCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    @Override
    public void addLayers(int projection, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state == null) {
            return;
        }
        BlockState corner = cornerBlock
                .map(id -> CeilingPattern.state(id, properties))
                .orElse(state);
        out.add(new Layer(projection, new BorderSurfacePatternProvider(inset, state,
                corner == null ? state : corner, orient,
                // The ring's outward direction is per cell, so it needs the surface's axes; this
                // pattern only ever draws on a ceiling, so it knows them.
                CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION)));
    }
}
