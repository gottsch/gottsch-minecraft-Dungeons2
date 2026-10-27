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
package mod.gottsch.forge.dungeons2.core.map;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which dungeon a Dungeon Map belongs to: the stamp carries its dungeon, the name is stable per
 * dungeon and varies between neighbours, and a stamp from before the change still loads.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class DungeonMapIdentityTest {

    @Test
    void theDungeonSurvivesTheStack() {
        FloorMapStamp stamp = new FloorMapStamp(1, 40, 100, 200, 160, 260, null, new int[]{130, 230});
        FloorMapStamp loaded = FloorMapStamp.load(stamp.save());
        assertNotNull(loaded);
        assertTrue(loaded.hasDungeon());
        assertArrayEquals(new int[]{130, 230}, loaded.dungeon());
    }

    /** A map made before 2026-10-26 has no dungeon and keeps its plain title. */
    @Test
    void anOldStampLoadsWithoutADungeon() {
        CompoundTag tag = new FloorMapStamp(0, 60, 0, 0, 40, 40, null).save();
        FloorMapStamp loaded = FloorMapStamp.load(tag);
        assertNotNull(loaded);
        assertFalse(loaded.hasDungeon());
    }

    @Test
    void theNameIsStablePerDungeon() {
        assertEquals(DungeonNames.nameFor(6180, 8870), DungeonNames.nameFor(6180, 8870));
        assertTrue(DungeonNames.nameFor(6180, 8870).contains(" of "));
    }

    /** Anchors a chunk apart must not all draw the same name; 64 neighbours give many distinct. */
    @Test
    void neighbouringDungeonsAreNamedApart() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            names.add(DungeonNames.nameFor(8 + 16 * i, 8));
        }
        assertTrue(names.size() > 40, "only " + names.size() + " distinct names across 64 anchors");
    }

    /** Some names carry an adjective and most do not -- about a third of 300 anchors, give or take. */
    @Test
    void aboutAThirdTakeAnAdjective() {
        int prefixed = 0;
        for (int i = 0; i < 300; i++) {
            String name = DungeonNames.nameFor(8 + 16 * i, 8 - 16 * i);
            if (DungeonNames.ADJECTIVES.stream().anyMatch(a -> name.startsWith(a + " "))) {
                prefixed++;
            }
        }
        assertTrue(prefixed > 60 && prefixed < 140, prefixed + " of 300 prefixed");
    }

    @Test
    void coversIsTheFootprintWithAMargin() {
        FloorMapStamp stamp = new FloorMapStamp(0, 60, 100, 200, 160, 260, null, new int[]{130, 230});
        assertTrue(stamp.covers(130.5, 230.5, 0));
        assertTrue(stamp.covers(98, 230, 4));
        assertFalse(stamp.covers(90, 230, 4));
    }
}
