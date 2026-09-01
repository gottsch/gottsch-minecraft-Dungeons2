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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.GridSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * A lattice of ribs crossing the ceiling both ways, {@code spacing} apart -- the coffered look.
 *
 * <p>Declares no {@code orient}: a rib is a line with open room on both sides, so there is no
 * direction to face. Under the flat record that was a hand-written validation rule; here it is
 * simply a field this type does not have.</p>
 */
public record CoffersCeilingPattern(String block, int spacing, Map<String, String> properties)
        implements CeilingPattern {

    public static final String NAME = "coffers";

    /** A plain lattice of one block at the default rhythm. */
    public CoffersCeilingPattern(String block) {
        this(block, GridSurfacePatternProvider.DEFAULT_SPACING, Map.of());
    }

    public static final MapCodec<CoffersCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID.fieldOf("block").forGetter(CoffersCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "spacing",
                                    GridSurfacePatternProvider.DEFAULT_SPACING)
                            .forGetter(CoffersCeilingPattern::spacing),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(CoffersCeilingPattern::properties)
            ).apply(instance, CoffersCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    @Override
    public void addLayers(int projection, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state != null) {
            out.add(new Layer(projection, new GridSurfacePatternProvider(spacing, state)));
        }
    }
}
