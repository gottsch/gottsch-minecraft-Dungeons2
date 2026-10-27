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

import java.util.List;

/**
 * A name for a dungeon, so its maps can be told from another's: "Barrow of Ulric", and about one in
 * three with an adjective in front, "Deathly Barrow of Ulric". A pure function
 * of the dungeon's anchor, so every map of every floor of one dungeon carries the same name, and
 * the same dungeon is named the same in every world it generates in.
 *
 * <p>English only, deliberately: the name is a proper noun shown inside a translated title
 * ({@code map.dungeons2.dungeon_map.named}), not a phrase to translate.
 *
 * @author Mark Gottschling on 10/26/2026
 */
public final class DungeonNames {

    static final List<String> PLACES = List.of(
            "Barrow", "Crypt", "Vault", "Halls", "Catacombs", "Warren", "Undercroft", "Ossuary",
            "Sepulchre", "Delve", "Hold", "Tombs", "Cellars", "Deeps");

    static final List<String> OWNERS = List.of(
            "Ulric", "Aldred", "Morwen", "Hask", "Brannoc", "Ysolde", "Garrick", "Theodric",
            "Wynn", "Corvald", "Esk", "Maudrin", "Halvard", "Sabeth", "Orrin", "Grimbold",
            "Edda", "Tamsin", "Varek", "Leofric", "Isolde", "Dunstan", "Rhoswen", "Cedric");

    /** Prefixed to about a third of names (Mark, 2026-10-26): not all, or none of them stands out. */
    static final List<String> ADJECTIVES = List.of(
            "Terrible", "Deathly", "Forsaken", "Forgotten", "Accursed", "Silent", "Sunken", "Bleak",
            "Hollow", "Blighted", "Dread", "Shrouded", "Wretched", "Grim", "Lost", "Drowned");

    /** One name in this many takes an adjective. */
    static final int ADJECTIVE_ONE_IN = 3;

    private DungeonNames() {
    }

    /** The dungeon planned from {@code (x, z)}. */
    public static String nameFor(int x, int z) {
        long h = mix(((long) x << 32) ^ (z & 0xFFFFFFFFL));
        String place = PLACES.get((int) Long.remainderUnsigned(h, PLACES.size()));
        String owner = OWNERS.get((int) Long.remainderUnsigned(h >>> 32, OWNERS.size()));
        // a second mix, so whether there is an adjective is not tied to which place was drawn
        long g = mix(h);
        if (Long.remainderUnsigned(g, ADJECTIVE_ONE_IN) == 0) {
            String adjective = ADJECTIVES.get((int) Long.remainderUnsigned(g >>> 32, ADJECTIVES.size()));
            return adjective + " " + place + " of " + owner;
        }
        return place + " of " + owner;
    }

    /**
     * SplitMix64's finalizer: neighbouring anchors (a chunk apart) would otherwise draw neighbouring
     * names.
     */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
