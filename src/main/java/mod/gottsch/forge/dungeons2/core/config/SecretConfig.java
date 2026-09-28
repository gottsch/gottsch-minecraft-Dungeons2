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
import java.util.Optional;

/**
 * Makes a scheme a SECRET room: its one doorway is hung with a hidden door in the room's own wall
 * stone, and a lever sconce on the corridor wall beside it opens it.
 *
 * <pre>
 * "secret": {
 *   "pedestal_loot_tables": [ { "loot_table": "dungeons2:pedestals/secret", "weight": 1 } ]
 * }
 * </pre>
 *
 * <h2>Presence is the switch</h2>
 * <p>{@code "secret": {}} is a whole, valid secret room: the door and the lever and nothing else.
 * Everything a room holds still comes from the ordinary slots &mdash; the {@code chests} slot is the
 * vault's chest. What only a secret room has is the pedestal, so that is the one thing here.</p>
 *
 * <h2>Which rooms can be secret is not authored</h2>
 * <p>A secret scheme is only ever rolled in a room that can take a hidden door: a NORMAL procedural
 * room with exactly one doorway, one cell wide, opening onto a corridor with wall on at least one
 * side of it for the lever, in a wall stone DungeonBlocks makes a hidden door for. That is decided
 * at generation by {@code SecretRoomPlanner}, which is the only place that can see the corridors.
 * Every other room drops secret schemes before weights are totalled, like any other gate.</p>
 *
 * @param pedestalLootTables weighted tables for the pedestal at the room's centre, rolled as chest
 *                           loot on first load with the FIRST stack kept. Absent: no pedestal,
 *                           unless the dungeon's counter-item is put here (#97), which always
 *                           brings one.
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public record SecretConfig(Optional<List<ChestConfig.LootTableEntry>> pedestalLootTables) {

    public static final SecretConfig EMPTY = new SecretConfig(Optional.empty());

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<SecretConfig> CODEC = Codecs.closed(RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codecs.strictOptionalFieldOf(ChestConfig.LootTableEntry.CODEC.listOf(), "pedestal_loot_tables")
                    .forGetter(SecretConfig::pedestalLootTables)
    ).apply(instance, SecretConfig::new)));

    /** The pedestal's tables, or empty for no pedestal. */
    public List<ChestConfig.LootTableEntry> pedestalTables() {
        return pedestalLootTables.orElseGet(List::of);
    }
}
