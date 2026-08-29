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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Set;

/**
 * Renders a room's raised platforms. Same builder contract as the wall, floor, ceiling and pillar
 * generators.
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public interface IDungeonPlatformGenerator {

    /**
     * Append this room's daises to {@code out}, keeping clear of every cell in {@code excluded}.
     *
     * <p><strong>{@code excluded} is the room's pit</strong> &mdash; backlog #58, the same argument
     * and the same reason as {@code IDungeonPillarGenerator#build}. A dais is all-or-nothing here
     * where a colonnade is per-cell: its whole footprint must be clear, so one overlapping a pit is
     * dropped entirely rather than built with a bite out of it. That asymmetry is not new &mdash; it
     * is how the doorway exclusion has always worked in each &mdash; and it is right in both places.</p>
     */
    void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
               List<BlockPlacement> out, Set<Coords2D> excluded);

    /** Convenience for a room with nothing to avoid. See {@code IDungeonPillarGenerator}. */
    default void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                       List<BlockPlacement> out) {
        build(room, floorY, motif, random, out, Set.of());
    }

    /**
     * The floor-level cells this generator's daises took, valid after {@link #build}. Same space and
     * purpose as {@code IDungeonWallGenerator#occupiedFloorCells} -- props stand somewhere else
     * rather than on top of (or inside) a platform.
     */
    default Set<Coords2D> occupiedFloorCells() {
        return Set.of();
    }
}
