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
package mod.gottsch.forge.dungeons2.core.config.wall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.PanelsWallPatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * A rectangular field of {@code block}, {@code width} cells wide.
 *
 * <p>A panel's FRAME is drawn by listing {@code courses} and {@code pilasters} around it, not by
 * this type -- which is why it declares no corner or trim slots of its own.</p>
 */
public record PanelsWallPattern(String block, int width, int spacing, int inset, int projection,
                                CourseOrient orient, Map<String, String> properties)
        implements WallPattern {

    public static final String NAME = "panels";

    public static final MapCodec<PanelsWallPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("block").forGetter(PanelsWallPattern::block),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "width",
                                    PanelsWallPatternProvider.DEFAULT_WIDTH)
                            .forGetter(PanelsWallPattern::width),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "spacing",
                                    PanelsWallPatternProvider.DEFAULT_WIDTH)
                            .forGetter(PanelsWallPattern::spacing),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset", 0)
                            .forGetter(PanelsWallPattern::inset),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, WallPatternEntry.MAX_PROJECTION),
                            "projection", 0).forGetter(PanelsWallPattern::projection),
                    Codecs.strictOptionalFieldOf(CourseOrient.CODEC, "orient", CourseOrient.NONE)
                            .forGetter(PanelsWallPattern::orient),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(PanelsWallPattern::properties)
            ).apply(instance, PanelsWallPattern::new)));

    @Override
    public MapCodec<? extends WallPattern> codec() {
        return CODEC;
    }

    @Override
    public ISurfacePatternProvider provider() {
        BlockState field = WallPattern.state(block, properties);
        return field == null ? null
                : new PanelsWallPatternProvider(field, width, spacing, inset, projection, orient);
    }
}
