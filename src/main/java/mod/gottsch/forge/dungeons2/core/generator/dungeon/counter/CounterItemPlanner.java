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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.counter;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides whether a dungeon hides its boss's counter-item, and in which room &mdash; backlog #97.
 *
 * <h2>The boss&rarr;counter-item table is built once, here</h2>
 * <p>{@link #COUNTER_TABLES} maps a boss entity id to the loot table of the chest that answers it.
 * The Stone Colossus gets the Boots of Shock Absorption; the Beholder and Death Tyrant the Mirror
 * Shield. A new counter-item needs nothing but its rows and its table.</p>
 *
 * <h2>Found before the fight, never guaranteed</h2>
 * <p>Mark's rules for #97. The chest goes in an ordinary procedural room &mdash; never the boss
 * room, which is an authored template and pays out its own tier &mdash; and only
 * {@link #CHANCE} of the dungeons that qualify get one at all. A guaranteed counter-item would make
 * the fight a fetch quest.</p>
 *
 * <h2>Why at emit time</h2>
 * <p>The same wall the Mining Chest hit: the boss is a property of the whole dungeon
 * ({@link DungeonLayout#getBoss()}, decided at planning), and a room piece knows only its own
 * {@code RoomData}. So one plan is made where the layout is in hand, and it names one room.
 * Seeded from {@link DungeonLayout#getSeed()}, salted, so a rebuilt structure start plans the same
 * chest.</p>
 *
 * @author Mark Gottschling on Sep 24, 2026
 */
public final class CounterItemPlanner {

    /**
     * Boss entity id &rarr; the loot table of the chest that counters it. The two Beholder-kin share
     * one table: the Mirror Shield answers the eye, whichever eye it is.
     */
    static final Map<String, String> COUNTER_TABLES = Map.of(
            "dungeons2:stone_colossus", "dungeons2:chests/counter_stone_colossus",
            "dungeons2:beholder", "dungeons2:chests/counter_beholder",
            "dungeons2:death_tyrant", "dungeons2:chests/counter_beholder");

    /** Mark, 2026-09-24: about one qualifying dungeon in four. */
    static final double CHANCE = 0.25D;

    /** Keeps this roll out of step with the Mining Chest's, which seeds off the same layout. */
    private static final long SEED_SALT = 0x434F554E544552L; // "COUNTER"

    private CounterItemPlanner() {}

    /**
     * SplitMix64's finalizer. The very first {@code nextDouble} of a legacy {@code RandomSource}
     * barely moves between neighbouring seeds, and neighbouring dungeons get neighbouring seeds: fed
     * raw, 200 consecutive seeds all rolled above 0.25 and not one planned a chest. The chance roll
     * is the first draw, so the seed is scrambled first.
     */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * @param floorIndex the floor of the room named below; half the key, as room ids restart per floor
     * @param roomId     {@link RoomData#getId()} of the room that places the chest
     * @param lootTable  the counter chest's loot table
     */
    public record CounterChestPlan(int floorIndex, int roomId, String lootTable) {}

    /**
     * Plans this dungeon's counter-item chest, or empty: no boss, a boss with no counter-item, the
     * {@link #CHANCE} roll failing, or no procedural room to put it in.
     */
    public static Optional<CounterChestPlan> plan(DungeonLayout layout) {
        if (layout == null || layout.getBoss() == null) {
            return Optional.empty();
        }
        String table = COUNTER_TABLES.get(layout.getBoss());
        if (table == null) {
            return Optional.empty();
        }
        RandomSource random = RandomSource.create(mix(layout.getSeed() ^ SEED_SALT));
        // The chance is rolled BEFORE the room is drawn, off the same random; keep that order, or
        // every existing seed's chest moves.
        if (random.nextDouble() >= CHANCE) {
            return Optional.empty();
        }

        // NORMAL only. TERMINAL is procedural too, but it is the bottom floor's end room -- the
        // place a boss room would have gone -- and the counter must be found before the boss.
        List<CounterChestPlan> eligible = new ArrayList<>();
        for (FloorLayout floor : layout.getFloors()) {
            for (RoomData room : floor.getRooms()) {
                if (room.getRole() == RoomRole.NORMAL && room.getTemplateId() == null) {
                    eligible.add(new CounterChestPlan(floor.getFloorIndex(), room.getId(), table));
                }
            }
        }
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }
}
