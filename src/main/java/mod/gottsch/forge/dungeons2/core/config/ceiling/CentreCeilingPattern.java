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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CentreSurfacePatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * A boss of side {@code size} at the ceiling's centre.
 *
 * <p>Registered under BOTH {@code centre} and {@code center}. The flat switch accepted either
 * spelling as one case; a ResourceLocation cannot carry an alias, so the alternative was to pick a
 * spelling and silently break whoever wrote the other. Two ids over one codec costs a line.</p>
 *
 * <p>Declares no {@code orient}: a boss is a solid block with no edge to face.</p>
 */
public record CentreCeilingPattern(String block, int size, Map<String, String> properties)
        implements CeilingPattern {

    public static final String NAME = "centre";

    /** A plain boss of one block at the default size. */
    public CentreCeilingPattern(String block) {
        this(block, CentreSurfacePatternProvider.DEFAULT_SIZE, Map.of());
    }

    /** The US spelling, registered over the same codec. */
    public static final String ALIAS = "center";

    public static final MapCodec<CentreCeilingPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.BLOCK_ID.fieldOf("block").forGetter(CentreCeilingPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "size",
                                    CentreSurfacePatternProvider.DEFAULT_SIZE)
                            .forGetter(CentreCeilingPattern::size),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(CentreCeilingPattern::properties)
            ).apply(instance, CentreCeilingPattern::new)));

    @Override
    public MapCodec<? extends CeilingPattern> codec() {
        return CODEC;
    }

    @Override
    public void addLayers(int projection, List<Layer> out) {
        BlockState state = CeilingPattern.state(block, properties);
        if (state != null) {
            out.add(new Layer(projection, new CentreSurfacePatternProvider(size, state)));
        }
    }
}
