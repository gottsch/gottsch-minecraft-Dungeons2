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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.SecretDoorway;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.HiddenDoors;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.counter.CounterItemPlanner.CounterChestPlan;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomDoorways;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.secret.SecretRoomPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.secret.SecretRoomPlanner.RoomKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Secret rooms, at generation: which rooms may be one, and handing the pieces what they need.
 *
 * <h2>Why here, and why the scheme is rolled twice</h2>
 * <p>A secret room is two pieces that never see each other. The ROOM rolls its scheme at render,
 * per chunk, from a piece-stable seed; the DOOR is a separate piece rendered after it, and it is
 * the door that must become hidden and grow a lever. Nothing at render time connects them. So the
 * decision is made here, once, where the layout is in hand: each hideable room's piece is told so,
 * its scheme is rolled with {@link DungeonRoomPiece#rolledScheme} &mdash; the same call, the same
 * seed and the same secret eligibility the render makes, so it is the answer and not a guess
 * &mdash; and when it comes out secret the door piece is told too. Both carry the result in NBT.</p>
 *
 * <p>Two halves of "hideable" are decided here rather than in the pure {@link SecretRoomPlanner}:
 * the floor's wall stone must have a hidden door registered, and at least one secret scheme must fit
 * the room. The second is what lets {@code CounterItemPlanner} force a room secret and know the
 * force will land.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class SecretRooms {

    private SecretRooms() {}

    /** Every room in the layout that can be built as a secret room, with where its door goes. */
    public static Map<RoomKey, SecretDoorway> hideable(DungeonLayout layout, MotifConfig motif) {
        return hideable(layout, motif, HiddenDoors::isRegistered);
    }

    /** As above, asking {@code registered} whether a hidden door exists; the seam is for tests. */
    public static Map<RoomKey, SecretDoorway> hideable(DungeonLayout layout, MotifConfig motif,
                                                       Predicate<String> registered) {
        Map<RoomKey, SecretDoorway> candidates = SecretRoomPlanner.candidates(layout);
        Map<RoomKey, SecretDoorway> out = new LinkedHashMap<>();
        if (candidates.isEmpty()) {
            return out;
        }
        Map<Integer, RoomData> rooms = new HashMap<>();
        for (FloorLayout floor : layout.getFloors()) {
            MotifConfig floorMotif = motif.forFloor(floor.getFloorIndex());
            if (DungeonDoorPiece.hiddenDoorFor(floorMotif, registered).isEmpty()) {
                continue;
            }
            rooms.clear();
            floor.getRooms().forEach(room -> rooms.put(room.getId(), room));
            for (Map.Entry<RoomKey, SecretDoorway> entry : candidates.entrySet()) {
                RoomKey key = entry.getKey();
                if (key.floorIndex() != floor.getFloorIndex()) {
                    continue;
                }
                RoomData room = rooms.get(key.roomId());
                if (room != null && anySecretSchemeFits(floorMotif, room, floor.getFloorIndex())) {
                    out.put(key, entry.getValue());
                }
            }
        }
        return out;
    }

    /** Whether any secret scheme passes this room's gates, before weights. */
    static boolean anySecretSchemeFits(MotifConfig floorMotif, RoomData room, int floorIndex) {
        int doors = RoomDoorways.count(room);
        for (RoomScheme scheme : floorMotif.schemes()) {
            if (scheme.isSecret() && scheme.fitsFloor(floorIndex) && scheme.fitsDoors(doors)
                    && scheme.fits(room.getWidth(), room.getDepth(), room.getHeight())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hands every hideable room piece its doorway (and the counter-item, if it was planned here),
     * rolls each one's scheme, and makes the door of each room that came out secret a hidden one.
     *
     * @return how many rooms came out secret
     */
    public static int apply(List<StructurePiece> pieces, Map<RoomKey, SecretDoorway> hideable,
                            Optional<CounterChestPlan> counter, MotifConfig motif) {
        if (hideable.isEmpty()) {
            return 0;
        }
        Map<String, DungeonDoorPiece> doors = new HashMap<>();
        for (StructurePiece piece : pieces) {
            if (piece instanceof DungeonDoorPiece door) {
                doors.put(doorKey(door.getFloorIndex(), door.getDoor().getX(), door.getDoor().getZ()),
                        door);
            }
        }
        int secretRooms = 0;
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof DungeonRoomPiece room)) {
                continue;
            }
            int floorIndex = room.getFloorIndex();
            RoomKey key = new RoomKey(floorIndex, room.getRoom().getId());
            SecretDoorway secret = hideable.get(key);
            if (secret == null) {
                continue;
            }
            room.withSecretDoorway(secret);
            counter.filter(plan -> plan.secret() && plan.floorIndex() == floorIndex
                            && plan.roomId() == key.roomId())
                    .ifPresent(plan -> room.withCounterItem(plan.item()));

            RoomScheme scheme = room.rolledScheme(motif.forFloor(floorIndex));
            if (!scheme.isSecret()) {
                continue;
            }
            DungeonDoorPiece door = doors.get(doorKey(floorIndex, secret.x(), secret.z()));
            if (door == null) {
                // SecretRoomPlanner only accepts a doorway that has a door piece, so this is a
                // planner/emitter disagreement. The room still builds -- with its ordinary door.
                Dungeons.LOGGER.error("[D2-SECRET] floor {} room {} rolled {} but its door piece at"
                        + " ({},{}) is missing", floorIndex, key.roomId(), scheme.name(), secret.x(),
                        secret.z());
                continue;
            }
            door.withSecret(secret);
            secretRooms++;
            //   grep "D2-SECRET" run/logs/dungeons2.log
            BlockPos lever = new BlockPos(door.anchorX + secret.lever().getX(), door.floorY + 2,
                    door.anchorZ + secret.lever().getY());
            Dungeons.LOGGER.info("[D2-SECRET] floor {} room {} is {}: door at {}, lever at {}{}",
                    floorIndex, key.roomId(), scheme.name(),
                    new BlockPos(door.anchorX + secret.x(), door.floorY + 1, door.anchorZ + secret.z())
                            .toShortString(),
                    lever.toShortString(),
                    room.getCounterItem() == null ? "" : ", pedestal holds " + room.getCounterItem());
        }
        return secretRooms;
    }

    private static String doorKey(int floorIndex, int x, int z) {
        return floorIndex + ":" + x + ":" + z;
    }
}
