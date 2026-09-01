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
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.PilastersWallPatternProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Optional;

/**
 * The fields a pilaster strip is authored from, shared by the two layouts.
 *
 * <h2>Why this record exists</h2>
 * <p>{@code pilasters} and {@code end_pilasters} are the same strip at different positions &mdash;
 * one provider, one {@code Layout} enum apart. Under the registry each needs its own
 * {@link MapCodec}, because the codec instance is what recovers the id on encode, so a single
 * shared record type could not be registered twice. Rather than duplicate ten field declarations,
 * both wrap this and {@code xmap} onto it, which keeps their JSON keys flat and identical and
 * leaves one place to change if a field is added.</p>
 *
 * <p>The alternative considered was one {@code dungeons2:pilasters} id with a {@code layout} config
 * field, folding {@code end_pilasters} away. It is arguably the better model, and it was not taken:
 * the two names are what packs author today, and a registry migration is enough of a break without
 * also retiring a vocabulary word.</p>
 *
 * <h2>The three rows take their properties separately</h2>
 * <p>Unlike a course's three block slots, which share one map. A pilaster needs it: a plinth and a
 * capital are typically the SAME block at opposite values of a vertical property
 * ({@code dungeonblocks}' pillar blocks use {@code base}, where {@code up} is the unrotated model
 * and {@code down} is it flipped), so one shared map cannot express a column.</p>
 */
public record PilasterShape(String block, Optional<String> baseBlock, Optional<String> capBlock,
                            int spacing, int inset, int projection, CourseOrient orient,
                            Map<String, String> properties,
                            Optional<Map<String, String>> baseProperties,
                            Optional<Map<String, String>> capProperties) {

    /** A plain strip of one block at the default rhythm, flush with the wall. */
    public PilasterShape(String block) {
        this(block, Optional.empty(), Optional.empty(),
                PilastersWallPatternProvider.DEFAULT_SPACING,
                PilastersWallPatternProvider.DEFAULT_INSET, 0, CourseOrient.NONE,
                Map.of(), Optional.empty(), Optional.empty());
    }

    public static final MapCodec<PilasterShape> MAP_CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    // REQUIRED, and that is what retires WallPatternEntry.validate's
                    // "block is required -- there is no default material" rule: the flat record had
                    // to make it Optional because `courses` has no block of its own.
                    Codecs.BLOCK_ID.fieldOf("block").forGetter(PilasterShape::block),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID, "base_block")
                            .forGetter(PilasterShape::baseBlock),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID, "cap_block")
                            .forGetter(PilasterShape::capBlock),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "spacing",
                                    PilastersWallPatternProvider.DEFAULT_SPACING)
                            .forGetter(PilasterShape::spacing),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                                    PilastersWallPatternProvider.DEFAULT_INSET)
                            .forGetter(PilasterShape::inset),
                    Codecs.strictOptionalFieldOf(Codec.intRange(0,
                                    mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.MAX_PROJECTION),
                            "projection", 0).forGetter(PilasterShape::projection),
                    Codecs.strictOptionalFieldOf(CourseOrient.CODEC, "orient", CourseOrient.NONE)
                            .forGetter(PilasterShape::orient),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "properties", Map.of()).forGetter(PilasterShape::properties),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "base_properties").forGetter(PilasterShape::baseProperties),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "cap_properties").forGetter(PilasterShape::capProperties)
            ).apply(instance, PilasterShape::new)));

    /** The base block, falling back to {@link #block} when unauthored. */
    public String baseBlockOrBase() {
        return baseBlock.orElse(block);
    }

    /** The cap block, falling back to {@link #block} when unauthored. */
    public String capBlockOrBase() {
        return capBlock.orElse(block);
    }

    /** The base row's properties, falling back to {@link #properties} when unauthored. */
    public Map<String, String> basePropertiesOrBase() {
        return baseProperties.orElse(properties);
    }

    /** See {@link #basePropertiesOrBase}. */
    public Map<String, String> capPropertiesOrBase() {
        return capProperties.orElse(properties);
    }

    /** The provider for this strip at {@code layout}, or null if any of its three blocks won't resolve. */
    ISurfacePatternProvider provider(PilastersWallPatternProvider.Layout layout) {
        BlockState shaft = WallPattern.state(block, properties);
        BlockState base = WallPattern.state(baseBlockOrBase(), basePropertiesOrBase());
        BlockState cap = WallPattern.state(capBlockOrBase(), capPropertiesOrBase());
        if (shaft == null || base == null || cap == null) {
            return null;
        }
        return new PilastersWallPatternProvider(shaft, base, cap, spacing, projection, orient,
                layout, inset);
    }
}
