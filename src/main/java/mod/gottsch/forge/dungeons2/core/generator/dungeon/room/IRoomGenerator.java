/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import net.minecraft.util.RandomSource;

/**
 * Renders a single {@link RoomData} room into a {@link RoomPlacements} by orchestrating the wall,
 * floor, and ceiling sub-builders (plus any prop pass).
 *
 * <p>Phase 2 builder API: no {@code ServerLevel}, no direct block writes.
 * One piece of input = one room. The caller (Phase 3 piece renderer) handles
 * iterating rooms across a {@code FloorLayout}.</p>
 *
 * <p>Takes a {@link RoomPlacements} rather than a bare {@code List<}{@link BlockPlacement}{@code >}
 * because a room now emits entities as well as blocks, and the two reach the world by different
 * paths &mdash; blocks through the decoration/processor pass and idempotent, entities around it and
 * emphatically not. The element sub-builders keep the plain list; only the orchestrator sees both.
 * </p>
 */
public interface IRoomGenerator {
    void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, RoomPlacements out);
}
