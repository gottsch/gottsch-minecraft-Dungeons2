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
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A {@link RoomScheme}'s {@code pots} slot: how many loot pots a room gets, which entity variants
 * they are drawn from, and the loot table they carry.
 *
 * <h2>These are entities, not blocks</h2>
 * <p>{@code dungeonblocks}' pots are {@code PotEntity} instances, not block states, which is why
 * this is the one scheme slot whose output does not go through the block pipeline. They also have
 * gravity and a fall-break distance, so a pot must be spawned resting on a solid floor cell &mdash;
 * {@code RoomPropGenerator} handles that, and it is the reason pots are placed on interior floor
 * cells only.</p>
 *
 * <h2>Loot</h2>
 * <p>{@code lootTable} is <strong>required</strong>, and required for a reason:
 * {@code PotEntity#dropLoot} returns early when its table id is null or {@code minecraft:empty},
 * with <em>no</em> fallback to the entity type's own table &mdash; and the tables
 * {@code dungeonblocks} ships for its three pot types are empty stubs with no pools. A pot without
 * a table here is a pot that shatters into nothing, silently. Making the field required turns that
 * into a load-time error instead.</p>
 *
 * <p>The table must be a {@code "type": "minecraft:entity"} table: the drop path builds its
 * {@code LootParams} with the ENTITY parameter set (origin, this-entity and damage-source
 * required). Each pot is given a non-zero {@code LootTableSeed}, so its contents are fixed when the
 * dungeon generates rather than rolled when a player breaks it &mdash; the same treatment vanilla
 * gives a structure chest.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record PotConfig(int minCount, int maxCount, String lootTable, List<PotVariant> variants,
                        SizeGate gate) {

    /** Ungated props -- placed whenever the scheme is rolled. */
    public PotConfig(int minCount, int maxCount, String lootTable, List<PotVariant> variants) {
        this(minCount, maxCount, lootTable, variants, SizeGate.UNBOUNDED);
    }

    /**
     * One weighted pot entity type. A separate record rather than a bare id list so a motif can say
     * "mostly the tall pot, occasionally a squat one" without repeating ids.
     */
    public record PotVariant(String entity, int weight) {
        // Codecs.closed -- see RoomScheme.CODEC.
        public static final Codec<PotVariant> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("entity").forGetter(PotVariant::entity),
                Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "weight", 1)
                        .forGetter(PotVariant::weight)
        ).apply(instance, PotVariant::new)));
    }

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<PotConfig> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "minCount", 1)
                    .forGetter(PotConfig::minCount),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "maxCount", 3)
                    .forGetter(PotConfig::maxCount),
            Codec.STRING.fieldOf("lootTable").forGetter(PotConfig::lootTable),
            PotVariant.CODEC.listOf().fieldOf("variants").forGetter(PotConfig::variants),
            SizeGate.MAP_CODEC.forGetter(PotConfig::gate)
    ).apply(instance, PotConfig::new)));

    /**
     * The inclusive count range, normalised. A {@code maxCount} below {@code minCount} is authoring
     * nonsense that a codec range cannot express (the bound is another field), so it is clamped
     * here rather than silently producing an empty or negative range at generation time.
     */
    public int clampedMaxCount() {
        return Math.max(minCount, maxCount);
    }
}
