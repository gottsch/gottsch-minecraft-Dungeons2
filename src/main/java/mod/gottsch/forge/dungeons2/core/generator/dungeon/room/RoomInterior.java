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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;

import java.util.HashSet;
import java.util.Set;

/**
 * Facts about a room's interior that more than one generator needs to agree on.
 *
 * <p>Exists because {@link #cellsInsideDoorways} was private to {@code RoomPropGenerator} and is now
 * also the rule free-standing pillars follow. Two copies of "which cell is a doorway approach" would
 * drift the first time either changed, and the failure would be quiet in both directions &mdash; a
 * pot you get shoved into, or a column standing in a doorway that a player simply walks around
 * without ever registering as a bug.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public final class RoomInterior {

    private RoomInterior() {}

    /**
     * The four orthogonal neighbours of every doorway cell, in floor-local coordinates.
     *
     * <p>Doorways sit on the perimeter ring, so no interior cell is ever <em>in</em> one &mdash; but
     * the cell immediately inside a door is where a player walks through, and anything standing
     * there is in the way. Only the neighbour that lands in the interior can ever match, so there is
     * no need to work out which side of the room the door is on.</p>
     *
     * <p>This is deliberately one cell deep and not a traced path between doors. A room with four
     * doors has no interior cell that is not on some route across it, so anything wider would
     * forbid interior features outright rather than keeping them out of the way.</p>
     */
    public static Set<Coords2D> cellsInsideDoorways(RoomData room) {
        Set<Coords2D> blocked = new HashSet<>();
        for (Coords2D door : room.getDoorways()) {
            // Coords2D's second axis is named Y but is the Z axis -- the 2D maze grid's "height"
            // is the 3D depth, the same aliasing DungeonStackPlanner notes at room conversion.
            blocked.add(new Coords2D(door.getX() + 1, door.getY()));
            blocked.add(new Coords2D(door.getX() - 1, door.getY()));
            blocked.add(new Coords2D(door.getX(), door.getY() + 1));
            blocked.add(new Coords2D(door.getX(), door.getY() - 1));
        }
        return blocked;
    }
}
