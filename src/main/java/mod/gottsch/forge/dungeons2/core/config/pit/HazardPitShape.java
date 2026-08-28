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
package mod.gottsch.forge.dungeons2.core.config.pit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.HazardPitShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.IPitShapeProvider;

import java.util.Map;
import java.util.Optional;

/**
 * A sheer-sided shaft with spikes at the bottom: a trap, not a room feature.
 *
 * <p><strong>The hazard is the sheer sides.</strong> A player can jump onto a block one high, so
 * any sheer pit two or more deep is one they fall into and cannot climb out of; the spikes turn a
 * nuisance into a threat. That is why this is its own provider rather than a flag on the court
 * shapes &mdash; an author placing a trap should have had to name it.</p>
 *
 * <p>{@code spikeBlock} defaults to nothing, which gives a plain oubliette. Authoring it wants
 * {@code vertical_direction: up} in {@code spikeProperties}: Minecraft has one dripstone block for
 * both ends, and <strong>only the upward tip multiplies fall damage</strong>, so a shaft floored
 * with downward ones is decoration. The codec cannot check that, because a pack may use a different
 * block whose states mean something else entirely.</p>
 *
 * <p>{@code offsetX}/{@code offsetZ} shift the shaft off centre; it is still kept inside the
 * interior's walkable ring, so a trap never blocks the room it is in.</p>
 */
public record HazardPitShape(int width, int depth, int offsetX, int offsetZ,
                             Optional<String> spikeBlock, Map<String, String> spikeProperties,
                             double spikeProbability) implements PitShapePattern {

    public static final String NAME = "hazard";

    public static final int DEFAULT_WIDTH = 3;

    /** Three: past the two that already traps a player, so the fall itself hurts. */
    public static final int DEFAULT_DEPTH = 3;

    public static final double DEFAULT_SPIKE_PROBABILITY = 0.35D;

    public HazardPitShape() {
        this(DEFAULT_WIDTH, DEFAULT_DEPTH, 0, 0, Optional.empty(), Map.of(),
                DEFAULT_SPIKE_PROBABILITY);
    }

    public static final MapCodec<HazardPitShape> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "width",
                            DEFAULT_WIDTH).forGetter(HazardPitShape::width),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 24), "depth", DEFAULT_DEPTH)
                            .forGetter(HazardPitShape::depth),
                    // Signed: a trap in the exact middle of every room announces itself.
                    Codecs.strictOptionalFieldOf(Codec.intRange(-16, 16), "offsetX", 0)
                            .forGetter(HazardPitShape::offsetX),
                    Codecs.strictOptionalFieldOf(Codec.intRange(-16, 16), "offsetZ", 0)
                            .forGetter(HazardPitShape::offsetZ),
                    Codecs.strictOptionalFieldOf(Codec.STRING, "spikeBlock")
                            .forGetter(HazardPitShape::spikeBlock),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "spikeProperties", Map.of()).forGetter(HazardPitShape::spikeProperties),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D), "spikeProbability",
                            DEFAULT_SPIKE_PROBABILITY).forGetter(HazardPitShape::spikeProbability)
            ).apply(instance, HazardPitShape::new)));

    @Override
    public MapCodec<? extends PitShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPitShapeProvider provider() {
        return new HazardPitShapeProvider(width, depth, offsetX, offsetZ,
                spikeBlock.orElse(null), spikeProperties, spikeProbability);
    }
}
