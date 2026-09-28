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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.door;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;

import java.util.Map;
import java.util.Optional;

/**
 * The DungeonBlocks blocks a secret doorway is built from, and which wall stone has a hidden door.
 *
 * <p>A hidden door is a door in its wall's own texture, so one exists per stone: DungeonBlocks
 * 3.0.0 makes them for the stones D2 builds walls in (classic's stone bricks and the cracked and
 * mossy ones its weathering produces, catacombs' bricks, deep_slate's deepslate bricks, the mud
 * band's mud bricks). A wall stone not in {@link #BY_WALL} gets no secret room at all &mdash; a
 * stone-brick door in a brick wall is not hidden. A new wall stone needs a new hidden door in
 * DungeonBlocks and one line here.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class HiddenDoors {

    public static final String LEVER_SCONCE = "dungeonblocks:lever_sconce";
    public static final String TORCH_SCONCE = "dungeonblocks:torch_sconce_block";
    public static final String PEDESTAL = "dungeonblocks:pedestal";

    /** Wall block id &rarr; its hidden door. DungeonBlocks names them singular: stone_brick_hidden_door. */
    static final Map<String, String> BY_WALL = Map.of(
            "minecraft:stone_bricks", "dungeonblocks:stone_brick_hidden_door",
            "minecraft:mossy_stone_bricks", "dungeonblocks:mossy_stone_brick_hidden_door",
            "minecraft:cracked_stone_bricks", "dungeonblocks:cracked_stone_brick_hidden_door",
            "minecraft:bricks", "dungeonblocks:brick_hidden_door",
            "minecraft:deepslate_bricks", "dungeonblocks:deepslate_brick_hidden_door",
            "minecraft:mud_bricks", "dungeonblocks:mud_brick_hidden_door");

    private HiddenDoors() {}

    /**
     * Whether a block id is registered &mdash; false for every DungeonBlocks block when an older
     * DungeonBlocks without the secret-passage blocks is installed, which turns secret rooms off
     * rather than hanging a door of air.
     */
    public static boolean isRegistered(String id) {
        return BlockStateCodec.blockOrNull(id) != null;
    }

    /** The hidden door for a wall of {@code wallId}, or empty when there is none. */
    public static Optional<String> forWall(String wallId) {
        return Optional.ofNullable(BY_WALL.get(wallId));
    }
}
