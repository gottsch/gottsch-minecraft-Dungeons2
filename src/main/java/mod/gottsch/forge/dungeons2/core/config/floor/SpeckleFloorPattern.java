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
package mod.gottsch.forge.dungeons2.core.config.floor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RandomSpeckleFloorPatternProvider;
import net.minecraft.world.level.block.Block;

/**
 * {@code primaryBlock} everywhere, with {@code secondaryBlock} sprinkled in at {@code probability}
 * per cell &mdash; the rarer, randomized cousin of {@link CheckerboardFloorPattern}.
 *
 * <p>This is what the mud stratum paves with: cobblestone showing packed mud through at 0.12. Its
 * value over the {@code base}/{@code alternateBase} pair is precisely {@code probability} &mdash;
 * that pair is a fixed 45/55 roll, which reads as a checkerboard rather than as wear and cannot
 * express "mostly cobblestone" at all.</p>
 */
public record SpeckleFloorPattern(String primaryBlock, String secondaryBlock, double probability)
        implements FloorPattern {

    public static final String NAME = "speckle";

    public static final MapCodec<SpeckleFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    // Required, and that is new. Under the old flat record every block slot had to
                    // be optional because every other pattern's slots were absent by design, so a
                    // speckle entry missing its base degraded silently to plain floor.
                    Codec.STRING.fieldOf("primaryBlock").forGetter(SpeckleFloorPattern::primaryBlock),
                    Codec.STRING.fieldOf("secondaryBlock").forGetter(SpeckleFloorPattern::secondaryBlock),
                    // Keeps its own default: it is a pattern-shape knob, not a material, and 0
                    // legitimately means "the accent never appears".
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0, 1.0), "probability",
                                    RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY)
                            .forGetter(SpeckleFloorPattern::probability)
            ).apply(instance, SpeckleFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block base = FloorPatterns.block(primaryBlock);
        Block accent = FloorPatterns.block(secondaryBlock);
        return FloorPatterns.allResolve(base, accent)
                ? new RandomSpeckleFloorPatternProvider(probability, base, accent)
                : PlainFloorPattern.INSTANCE.generator(config);
    }
}
