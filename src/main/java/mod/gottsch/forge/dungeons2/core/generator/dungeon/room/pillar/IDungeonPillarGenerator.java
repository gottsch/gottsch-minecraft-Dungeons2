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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Set;

/**
 * Renders a room's free-standing columns as {@link BlockPlacement}s. Same builder contract as the
 * wall, floor and ceiling generators.
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public interface IDungeonPillarGenerator {

    /**
     * Append this room's columns to {@code out}, standing off every cell in {@code excluded}.
     * See {@code IDungeonWallGenerator#build}.
     *
     * <p><strong>{@code excluded} is the room's pit</strong> &mdash; backlog #58. A column is drawn
     * from the walking plane UPWARD, so one standing in an excavated cell hangs over the hole with
     * nothing under it. The pit is not derivable from {@link RoomData}: it is rolled per room by
     * {@code RoomPitGenerator}, which is why it arrives as an argument rather than being worked out
     * here the way the doorway approaches are.</p>
     *
     * <p>Excluding a cell drops <em>that column</em> and leaves the rest of the layout standing,
     * which is the same granularity the doorway exclusion already has and is right for the same
     * reason: a colonnade missing one column reads as a colonnade, and the alternative is dropping
     * a whole layout because one of its cells fell in a hole.</p>
     */
    void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
               List<BlockPlacement> out, Set<Coords2D> excluded);

    /**
     * Convenience for a room with nothing to avoid.
     *
     * <p>The exclusion-taking form is the one an implementation has to write, deliberately: making
     * the no-exclusion form the abstract one would let a new generator be written without ever
     * seeing the parameter, and #58 is exactly a fault of a generator not consulting something it
     * should have.</p>
     */
    default void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                       List<BlockPlacement> out) {
        build(room, floorY, motif, random, out, Set.of());
    }

    /**
     * The floor-level interior cells this generator's columns took, valid after {@link #build}.
     * Floor-local X/Z, the same space and the same purpose as
     * {@code IDungeonWallGenerator#occupiedFloorCells} &mdash; the room's props stand somewhere else
     * rather than inside a column.
     */
    default Set<Coords2D> occupiedFloorCells() {
        return Set.of();
    }
}
