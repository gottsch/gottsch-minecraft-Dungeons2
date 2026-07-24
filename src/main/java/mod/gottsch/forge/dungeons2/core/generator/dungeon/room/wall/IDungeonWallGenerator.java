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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Renders walls for a {@link RoomData} room as a list of {@link BlockPlacement}s.
 *
 * <p>Phase 2 builder API: no {@code ServerLevel}, no direct block writes &mdash;
 * implementations append placements to {@code out} and return. Coordinates in
 * the emitted placements are floor-local X/Z + absolute world Y (see
 * {@link BlockPlacement} for the coord-space convention).</p>
 */
public interface IDungeonWallGenerator {
    /**
     * Append wall + interior-air placements for the given room to {@code out}.
     *
     * @param room    target room (provides size, floor-local origin, height)
     * @param floorY  absolute world Y of this room's floor surface
     * @param motif   theme used to resolve concrete block types
     * @param random  RNG; callers must supply a seeded source for determinism
     * @param out     accumulator the builder appends placements to
     */
    void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out);
}
