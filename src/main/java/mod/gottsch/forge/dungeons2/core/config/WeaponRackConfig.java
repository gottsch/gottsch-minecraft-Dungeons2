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

import java.util.Optional;

/**
 * What a {@code dungeonblocks:weapon_rack} prop holds: its {@code rack} block on a {@code props}
 * variant.
 *
 * <h2>Loot tables, rolled when the chunk is written</h2>
 * <p>The weapons come from loot tables rather than from a list here, so a pack tunes them with
 * vanilla's own functions &mdash; {@code set_damage} for a worn blade, {@code enchant_with_levels}
 * for a good one &mdash; and D2 owns no enchanting rules. Which table fills which slot, and the seed
 * each roll uses, are decided when the room is planned; the tables themselves are rolled as the
 * rack is written (see {@code WeaponRackFiller}), because a rack shows its weapons and so cannot
 * wait for a player to open it the way a chest does.</p>
 *
 * <h2>The fine weapon</h2>
 * <p>Each filled slot rolls {@code loot_table}. With {@code fine_chance}, ONE of them rolls
 * {@code fine_loot_table} instead &mdash; never both, so a rack is at most one good weapon beside an
 * ordinary one. A rack only takes swords and axes; a roll that produces anything else leaves its
 * slot empty.</p>
 *
 * @author Mark Gottschling on 10/22/2026
 */
public record WeaponRackConfig(String lootTable, Optional<String> fineLootTable, double fineChance,
                               int minWeapons, int maxWeapons) {

    /** A rack has two slots. */
    public static final int SLOTS = 2;

    // Codecs.closed -- see RoomScheme.CODEC.
    public static final Codec<WeaponRackConfig> CODEC = Codecs.closed(RecordCodecBuilder.<WeaponRackConfig>mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("loot_table").forGetter(WeaponRackConfig::lootTable),
            Codecs.strictOptionalFieldOf(Codec.STRING, "fine_loot_table")
                    .forGetter(WeaponRackConfig::fineLootTable),
            Codecs.strictOptionalFieldOf(Codec.doubleRange(0.0D, 1.0D), "fine_chance", 0.0D)
                    .forGetter(WeaponRackConfig::fineChance),
            Codecs.strictOptionalFieldOf(Codec.intRange(0, SLOTS), "min_weapons", 1)
                    .forGetter(WeaponRackConfig::minWeapons),
            Codecs.strictOptionalFieldOf(Codec.intRange(1, SLOTS), "max_weapons", SLOTS)
                    .forGetter(WeaponRackConfig::maxWeapons)
    ).apply(instance, WeaponRackConfig::new)).flatXmap(WeaponRackConfig::validate,
            WeaponRackConfig::validate));

    /**
     * A {@code fine_chance} with no table to roll would be a rate that can never happen, and a
     * {@code fine_loot_table} at chance 0 a table that is never read; both are authoring mistakes
     * that would otherwise look like working config.
     */
    private static DataResult<WeaponRackConfig> validate(WeaponRackConfig config) {
        if (config.fineChance > 0.0D && config.fineLootTable.isEmpty()) {
            return DataResult.error(() -> "rack: 'fine_chance' is set but there is no"
                    + " 'fine_loot_table' to roll");
        }
        if (config.fineLootTable.isPresent() && config.fineChance <= 0.0D) {
            return DataResult.error(() -> "rack: 'fine_loot_table' is set but 'fine_chance' is 0,"
                    + " so it would never be rolled");
        }
        return DataResult.success(config);
    }

    /** The inclusive weapon-count range, normalised the way every slot's count range is. */
    public int clampedMaxWeapons() {
        return Math.max(minWeapons, maxWeapons);
    }
}
