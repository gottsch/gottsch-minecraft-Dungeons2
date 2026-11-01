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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.secret;

import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.SecretDoorway;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which rooms can be secret rooms, and where each one's hidden door and lever would go.
 *
 * <p>A room is <em>hideable</em> when all of these hold:</p>
 * <ul>
 *   <li>it is a NORMAL room built procedurally (no prefab, no boss or terminal room);</li>
 *   <li>it has exactly ONE doorway cell &mdash; one way in, one block wide. A 2-wide dead end is out:
 *       a lever powers the block it hangs on, which touches one door leaf, not two;</li>
 *   <li>that cell is a real door (it has a {@link DoorData}; a template's connector does not) in a
 *       straight run of wall, not a corner;</li>
 *   <li>the cell straight outside it is CORRIDOR, so the door is seen from a passage and the lever
 *       has air to hang in &mdash; a door straight into another room would put that room's wall and
 *       scheme where the lever goes;</li>
 *   <li>on at least one side, the room's own wall cell beside the door has corridor in front of it
 *       too. That is where the lever goes. With both sides free, the side is rolled and the other
 *       gets the decoy torch sconce.</li>
 * </ul>
 *
 * <p>Whether the room's WALL STONE has a hidden door, and whether any secret scheme fits the room,
 * are the Forge shell's half of the question ({@code SecretRooms}) &mdash; this class stays pure.</p>
 *
 * <p>Seeded from the layout seed, salted and SplitMix-mixed, per room: the lever side is the first
 * draw, and a legacy {@code RandomSource}'s first draw barely moves between neighbouring seeds.
 * Room ids restart per floor, so the floor is half the key.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class SecretRoomPlanner {

    private static final long SEED_SALT = 0x5345435245544CL; // "SECRETL"

    private SecretRoomPlanner() {}

    /** A room's key across the whole dungeon: room ids restart on every floor. */
    public record RoomKey(int floorIndex, int roomId) {}

    /** Every hideable room in the layout, in floor then room order. */
    public static Map<RoomKey, SecretDoorway> candidates(DungeonLayout layout) {
        Map<RoomKey, SecretDoorway> out = new LinkedHashMap<>();
        for (FloorLayout floor : layout.getFloors()) {
            Set<Coords2D> corridor = new HashSet<>();
            for (CorridorData data : floor.getCorridors()) {
                corridor.addAll(data.getCells());
            }
            Set<Coords2D> doors = new HashSet<>();
            for (DoorData door : floor.getDoors()) {
                doors.add(new Coords2D(door.getX(), door.getZ()));
            }
            for (RoomData room : floor.getRooms()) {
                long seed = layout.getSeed() ^ SEED_SALT
                        ^ ((long) floor.getFloorIndex() << 32) ^ room.getId();
                candidate(room, corridor, doors, seed).ifPresent(secret ->
                        out.put(new RoomKey(floor.getFloorIndex(), room.getId()), secret));
            }
        }
        return out;
    }

    /**
     * Where this room's hidden door and lever go, or empty when it is not hideable.
     *
     * @param corridor every corridor cell on the room's floor
     * @param doors    every cell on the floor that has a door piece
     * @param seed     picks the lever's side when both are free; mixed here before use
     */
    public static Optional<SecretDoorway> candidate(RoomData room, Set<Coords2D> corridor,
                                                    Set<Coords2D> doors, long seed) {
        if (room.getRole() != RoomRole.NORMAL || room.getTemplateId() != null) {
            return Optional.empty();
        }
        if (room.getDoorways().size() != 1) {
            return Optional.empty();
        }
        Coords2D door = room.getDoorways().get(0);
        if (!doors.contains(door)) {
            return Optional.empty();
        }
        Direction2D inward = inwardFor(room, door);
        if (inward == null) {
            return Optional.empty();
        }
        SecretDoorway plus = new SecretDoorway(door.getX(), door.getY(), inward, 1, false);
        if (!corridor.contains(plus.outside())) {
            return Optional.empty();
        }
        boolean plusFree = leverFits(room, corridor, plus.leverWall(), plus.lever());
        boolean minusFree = leverFits(room, corridor, plus.decoyWall(), plus.decoyCell());
        if (plusFree && minusFree) {
            int side = (mix(seed) & 1L) == 0L ? 1 : -1;
            return Optional.of(new SecretDoorway(door.getX(), door.getY(), inward, side, true));
        }
        if (plusFree || minusFree) {
            return Optional.of(new SecretDoorway(door.getX(), door.getY(), inward,
                    plusFree ? 1 : -1, false));
        }
        return Optional.empty();
    }

    /**
     * The direction from the corridor into the room for a doorway on its perimeter, or null for a
     * corner cell or a cell not on the perimeter at all. The room's footprint includes its wall
     * ring, so the perimeter is {@code origin} and {@code origin + size - 1}.
     */
    static Direction2D inwardFor(RoomData room, Coords2D door) {
        int minX = room.getOriginX();
        int maxX = room.getOriginX() + room.getWidth() - 1;
        int minZ = room.getOriginZ();
        int maxZ = room.getOriginZ() + room.getDepth() - 1;
        boolean westOrEast = door.getX() == minX || door.getX() == maxX;
        boolean northOrSouth = door.getY() == minZ || door.getY() == maxZ;
        if (westOrEast == northOrSouth) {
            return null; // a corner, or not on the wall at all
        }
        if (westOrEast) {
            if (door.getY() < minZ || door.getY() > maxZ) {
                return null;
            }
            return door.getX() == minX ? Direction2D.EAST : Direction2D.WEST;
        }
        if (door.getX() < minX || door.getX() > maxX) {
            return null;
        }
        return door.getY() == minZ ? Direction2D.SOUTH : Direction2D.NORTH;
    }

    /**
     * Whether a lever can hang on {@code wall} in front of {@code cell}: the wall cell is this
     * room's own perimeter (solid stone the room wrote), and the cell in front of it is corridor.
     */
    private static boolean leverFits(RoomData room, Set<Coords2D> corridor, Coords2D wall,
                                     Coords2D cell) {
        boolean onPerimeter = wall.getX() >= room.getOriginX()
                && wall.getX() <= room.getOriginX() + room.getWidth() - 1
                && wall.getY() >= room.getOriginZ()
                && wall.getY() <= room.getOriginZ() + room.getDepth() - 1;
        return onPerimeter && corridor.contains(cell);
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
