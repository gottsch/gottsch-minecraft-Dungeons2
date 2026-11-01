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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit;

import java.util.Map;
import java.util.Optional;

/**
 * Which floor stone has a DungeonBlocks crumbling floor, and its id.
 *
 * <p>A crumbling floor is its stone with faint hairline cracks, so a false floor only hides when
 * each lid cell is the crumbling version of the block the floor laid there. DungeonBlocks 3.0.0
 * makes them for the stones D2 floors with, named {@code crumbling_<source>}. A floor block not
 * listed here cannot be lidded, and {@code RoomPitGenerator} then leaves the whole pit open rather
 * than patch a lid out of the wrong stone.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class CrumblingFloors {

    static final Map<String, String> BY_FLOOR = Map.of(
            "minecraft:stone_bricks", "dungeonblocks:crumbling_stone_bricks",
            "minecraft:mossy_stone_bricks", "dungeonblocks:crumbling_mossy_stone_bricks",
            "minecraft:cracked_stone_bricks", "dungeonblocks:crumbling_cracked_stone_bricks",
            "minecraft:cobblestone", "dungeonblocks:crumbling_cobblestone",
            "minecraft:mossy_cobblestone", "dungeonblocks:crumbling_mossy_cobblestone",
            "minecraft:polished_andesite", "dungeonblocks:crumbling_polished_andesite",
            "minecraft:deepslate_bricks", "dungeonblocks:crumbling_deepslate_bricks",
            "minecraft:deepslate_tiles", "dungeonblocks:crumbling_deepslate_tiles",
            "minecraft:mud_bricks", "dungeonblocks:crumbling_mud_bricks");

    /** The lid for a stone with no crumbling version of its own, when a trap must lid regardless. */
    public static final String FALLBACK = "dungeonblocks:crumbling_stone_bricks";

    private CrumblingFloors() {}

    /** The crumbling version of {@code floorId}, or empty when DungeonBlocks has none. */
    public static Optional<String> forFloor(String floorId) {
        return Optional.ofNullable(BY_FLOOR.get(floorId));
    }
}
