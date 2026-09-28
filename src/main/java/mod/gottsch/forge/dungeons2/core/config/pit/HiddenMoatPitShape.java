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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.HiddenMoatPitShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.IPitShapeProvider;

import java.util.Map;
import java.util.Optional;

/**
 * Take the prize, lose the floor: a spiked moat dug under the untouched floor around the room's
 * centre, sprung when the pedestal standing there is emptied.
 *
 * <p><strong>It only does anything in a secret room.</strong> The trap is carried by the pedestal
 * (a secret scheme's), which stands on the undug centre. In any other scheme it is a void under a
 * floor that never falls. The floor is not authored and not a crumbling block: it is the room's own
 * floor, walkable as any other, until the prize is taken.</p>
 */
public record HiddenMoatPitShape(int radius, int depth, Optional<String> spikeBlock,
                                 Map<String, String> spikeProperties, double spikeProbability)
        implements PitShapePattern {

    public static final String NAME = "hidden_moat";

    public static final MapCodec<HiddenMoatPitShape> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 8), "radius", 2)
                            .forGetter(HiddenMoatPitShape::radius),
                    Codecs.strictOptionalFieldOf(Codec.intRange(2, 24), "depth",
                            HazardPitShape.DEFAULT_DEPTH).forGetter(HiddenMoatPitShape::depth),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "spike_block")
                            .forGetter(HiddenMoatPitShape::spikeBlock),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "spike_properties", Map.of()).forGetter(HiddenMoatPitShape::spikeProperties),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D), "spike_probability",
                            HazardPitShape.DEFAULT_SPIKE_PROBABILITY)
                            .forGetter(HiddenMoatPitShape::spikeProbability)
            ).apply(instance, HiddenMoatPitShape::new)));

    /** See {@link PitShapePattern#withRoles}. */
    @Override
    public PitShapePattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        Optional<String> resolved = Codecs.resolveRole(spikeBlock, resolver);
        return resolved.equals(spikeBlock) ? this
                : new HiddenMoatPitShape(radius, depth, resolved, spikeProperties, spikeProbability);
    }

    @Override
    public MapCodec<? extends PitShapePattern> codec() {
        return CODEC;
    }

    @Override
    public HiddenMoatPitShapeProvider provider() {
        return new HiddenMoatPitShapeProvider(radius, depth, spikeBlock.orElse(null), spikeProperties,
                spikeProbability);
    }
}
