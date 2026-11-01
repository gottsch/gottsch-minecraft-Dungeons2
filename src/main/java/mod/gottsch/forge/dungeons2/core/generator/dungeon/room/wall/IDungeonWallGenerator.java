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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Set;

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

    /**
     * The room's interior cells this generator's projecting trim took at floor level
     * ({@code floorY + 1}), valid after {@link #build}. Floor-local X/Z, the same space as
     * {@code RoomData#getDoorways}.
     *
     * <p>Exists so the room's props can stand somewhere else. A projecting pilaster occupies exactly
     * the inner-ring cells a loot pot wants, for every strip on the wall &mdash; not an authoring
     * slip but the shape of the feature &mdash; and a pot spawned inside trim is invisible until
     * someone walks into the room. Reporting the cells lets the two coexist, where the alternative
     * was forbidding the combination in a scheme.</p>
     *
     * <p>Empty by default: a generator that projects nothing has nothing to declare, and one that
     * never learns about this keeps the old behaviour rather than silently under-reporting.</p>
     */
    default Set<Coords2D> occupiedFloorCells() {
        return Set.of();
    }
}
