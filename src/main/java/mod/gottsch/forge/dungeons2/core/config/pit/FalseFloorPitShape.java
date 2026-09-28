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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.FalseFloorPitShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.IPitShapeProvider;

import java.util.Map;
import java.util.Optional;

/**
 * A spiked shaft under a crumbling copy of the floor: {@code hazard} with a lid that gives way.
 *
 * <p>The same fields as {@link HazardPitShape} but {@code rim_block}: a rim is the hazard's fair
 * warning, and a false floor exists to have none. The lid is not authored either &mdash; it is the
 * crumbling version of whatever the floor laid in each cell (see {@code CrumblingFloors}), so it
 * matches a bordered or pathed floor as well as a plain one. A floor stone with no crumbling
 * version leaves the pit open.</p>
 *
 * <p>{@code spike_block} takes {@code minecraft:pointed_dripstone} (with {@code vertical_direction:
 * up}) or {@code dungeonblocks:iron_spikes} (with {@code facing: up}); both only hurt pointing
 * up.</p>
 */
public record FalseFloorPitShape(int width, int depth, int offsetX, int offsetZ,
                                 Optional<String> spikeBlock, Map<String, String> spikeProperties,
                                 double spikeProbability) implements PitShapePattern {

    public static final String NAME = "false_floor";

    public static final MapCodec<FalseFloorPitShape> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "width",
                            HazardPitShape.DEFAULT_WIDTH).forGetter(FalseFloorPitShape::width),
                    // From 2: a one-deep "pit" is a step down, not a fall.
                    Codecs.strictOptionalFieldOf(Codec.intRange(2, 24), "depth",
                            HazardPitShape.DEFAULT_DEPTH).forGetter(FalseFloorPitShape::depth),
                    Codecs.strictOptionalFieldOf(Codec.intRange(-16, 16), "offset_x", 0)
                            .forGetter(FalseFloorPitShape::offsetX),
                    Codecs.strictOptionalFieldOf(Codec.intRange(-16, 16), "offset_z", 0)
                            .forGetter(FalseFloorPitShape::offsetZ),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "spike_block")
                            .forGetter(FalseFloorPitShape::spikeBlock),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "spike_properties", Map.of()).forGetter(FalseFloorPitShape::spikeProperties),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D), "spike_probability",
                            HazardPitShape.DEFAULT_SPIKE_PROBABILITY)
                            .forGetter(FalseFloorPitShape::spikeProbability)
            ).apply(instance, FalseFloorPitShape::new)));

    /** See {@link PitShapePattern#withRoles}. */
    @Override
    public PitShapePattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        Optional<String> resolved = Codecs.resolveRole(spikeBlock, resolver);
        return resolved.equals(spikeBlock) ? this
                : new FalseFloorPitShape(width, depth, offsetX, offsetZ, resolved, spikeProperties,
                        spikeProbability);
    }

    @Override
    public MapCodec<? extends PitShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPitShapeProvider provider() {
        return new FalseFloorPitShapeProvider(width, depth, offsetX, offsetZ,
                spikeBlock.orElse(null), spikeProperties, spikeProbability);
    }
}
