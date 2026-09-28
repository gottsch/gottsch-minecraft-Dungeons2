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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.data.CorridorTrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The datapack form of a corridor style's {@code traps} block (#108 stage 3).
 *
 * <pre>
 * "traps": { "chance": 0.25, "options": [
 *   { "kind": "false_floor", "weight": 2, "length": 2, "depth": 4,
 *     "spike_block": "minecraft:pointed_dripstone", "spike_properties": { "vertical_direction": "up" } },
 *   { "kind": "hazard", "weight": 1, "rim_block": "$inlay", ... } ] }
 * </pre>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class CorridorTrapCodec {

    private CorridorTrapCodec() {}

    public static final int DEFAULT_LENGTH = 2;
    public static final int DEFAULT_DEPTH = 4;
    public static final double DEFAULT_SPIKE_PROBABILITY = 0.4;

    public static final Codec<CorridorTrap> TRAP = RecordCodecBuilder.<CorridorTrap>create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("kind").forGetter(CorridorTrap::kind),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                            .forGetter(CorridorTrap::weight),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 3), "length", DEFAULT_LENGTH)
                            .forGetter(CorridorTrap::length),
                    // From 2: a one-deep trench is a step down, not a fall.
                    Codecs.strictOptionalFieldOf(Codec.intRange(2, 24), "depth", DEFAULT_DEPTH)
                            .forGetter(CorridorTrap::depth),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "spike_block")
                            .forGetter(CorridorTrap::spikeBlock),
                    Codecs.strictOptionalFieldOf(Codec.unboundedMap(Codec.STRING, Codec.STRING),
                            "spike_properties", Map.of()).forGetter(CorridorTrap::spikeProperties),
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0, 1.0), "spike_probability",
                            DEFAULT_SPIKE_PROBABILITY).forGetter(CorridorTrap::spikeProbability),
                    Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "rim_block")
                            .forGetter(CorridorTrap::rimBlock)
            ).apply(instance, CorridorTrap::new)).flatXmap(CorridorTrapCodec::validate,
            CorridorTrapCodec::validate);

    public static final Codec<CorridorTrap.Options> CODEC = RecordCodecBuilder.<CorridorTrap.Options>create(instance ->
            instance.group(
                    Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0, 1.0), "chance", 1.0)
                            .forGetter(CorridorTrap.Options::chance),
                    TRAP.listOf().fieldOf("options").forGetter(CorridorTrap.Options::options)
            ).apply(instance, CorridorTrap.Options::new)).flatXmap(CorridorTrapCodec::validate,
            CorridorTrapCodec::validate);

    private static DataResult<CorridorTrap> validate(CorridorTrap trap) {
        if (!CorridorTrap.HAZARD.equals(trap.kind()) && !CorridorTrap.FALSE_FLOOR.equals(trap.kind())) {
            return DataResult.error(() -> "corridor trap: unknown kind '" + trap.kind()
                    + "' -- expected 'hazard' or 'false_floor'");
        }
        // A rim is the hazard's fair warning; a false floor exists to have none (the room rule).
        if (trap.isFalseFloor() && trap.rimBlock().isPresent()) {
            return DataResult.error(() -> "corridor trap: a false_floor cannot take 'rim_block' -- "
                    + "the rim is a tell, and a false floor is the trap that has none");
        }
        return DataResult.success(trap);
    }

    private static DataResult<CorridorTrap.Options> validate(CorridorTrap.Options options) {
        if (options.options().isEmpty()) {
            return DataResult.error(() -> "corridor traps: 'options' must not be empty");
        }
        return DataResult.success(options);
    }

    /** Resolves {@code $role} block ids, returning the same instance when nothing changed. */
    public static Optional<CorridorTrap.Options> withRoles(Optional<CorridorTrap.Options> traps,
                                                           UnaryOperator<String> resolver) {
        if (traps.isEmpty()) {
            return traps;
        }
        boolean changed = false;
        List<CorridorTrap> resolved = new ArrayList<>();
        for (CorridorTrap t : traps.get().options()) {
            Optional<String> spike = Codecs.resolveRole(t.spikeBlock(), resolver);
            Optional<String> rim = Codecs.resolveRole(t.rimBlock(), resolver);
            if (spike.equals(t.spikeBlock()) && rim.equals(t.rimBlock())) {
                resolved.add(t);
            } else {
                changed = true;
                resolved.add(new CorridorTrap(t.kind(), t.weight(), t.length(), t.depth(), spike,
                        t.spikeProperties(), t.spikeProbability(), rim));
            }
        }
        return changed ? Optional.of(new CorridorTrap.Options(traps.get().chance(), List.copyOf(resolved)))
                : traps;
    }
}
