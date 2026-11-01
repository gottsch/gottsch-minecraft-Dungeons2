/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Renders the floor surface of a {@link RoomData} as a list of {@link BlockPlacement}s.
 *
 * <p>See {@link mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.IDungeonWallGenerator}
 * for the coord-space convention.</p>
 *
 * @author Mark Gottschling on Mar 1, 2024 (Phase 2 rewrite May 25, 2026)
 */
public interface IDungeonFloorGenerator {
    void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out);
}
