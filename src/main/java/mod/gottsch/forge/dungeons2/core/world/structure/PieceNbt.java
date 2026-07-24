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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * NBT (de)serialization for the layout POJOs the Phase 3 procedural pieces
 * carry. Keeping the encode/decode here means the {@code core/data} POJOs stay
 * Minecraft-free while the pieces round-trip them through {@link CompoundTag}.
 *
 * <p>{@link Coords2D} lists are flattened to {@code int[]} pairs (x0,z0,x1,z1,…)
 * &mdash; compact and order-preserving. Enum fields serialize by {@code name()}
 * and tolerate unknown values by falling back to a sensible default.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public final class PieceNbt {

    private PieceNbt() {}

    // -------- RoomData --------

    public static CompoundTag writeRoom(RoomData room) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", room.getId());
        tag.putInt("OX", room.getOriginX());
        tag.putInt("OZ", room.getOriginZ());
        tag.putInt("W", room.getWidth());
        tag.putInt("D", room.getDepth());
        tag.putInt("H", room.getHeight());
        tag.putString("Role", room.getRole() != null ? room.getRole().name() : RoomRole.NORMAL.name());
        tag.putIntArray("Doorways", flatten(room.getDoorways()));
        if (room.getTemplateId() != null) {
            tag.putString("Template", room.getTemplateId());
        }
        return tag;
    }

    public static RoomData readRoom(CompoundTag tag) {
        RoomData room = new RoomData(
                tag.getInt("Id"),
                tag.getInt("OX"),
                tag.getInt("OZ"),
                tag.getInt("W"),
                tag.getInt("D"),
                tag.getInt("H"),
                readRole(tag.getString("Role")));
        room.setDoorways(unflatten(tag.getIntArray("Doorways")));
        if (tag.contains("Template")) {
            room.setTemplateId(tag.getString("Template"));
        }
        return room;
    }

    // -------- CorridorData --------

    public static CompoundTag writeCorridor(CorridorData corridor) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", corridor.getId());
        tag.putIntArray("Cells", flatten(corridor.getCells()));
        tag.putIntArray("Walls", flatten(corridor.getWallCells()));
        if (corridor.getTemplateId() != null) {
            tag.putString("Template", corridor.getTemplateId());
        }
        return tag;
    }

    public static CorridorData readCorridor(CompoundTag tag) {
        CorridorData corridor = new CorridorData(tag.getInt("Id"));
        corridor.setCells(unflatten(tag.getIntArray("Cells")));
        corridor.setWallCells(unflatten(tag.getIntArray("Walls")));
        if (tag.contains("Template")) {
            corridor.setTemplateId(tag.getString("Template"));
        }
        return corridor;
    }

    // -------- DoorData --------

    public static CompoundTag writeDoor(DoorData door) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", door.getX());
        tag.putInt("Z", door.getZ());
        tag.putInt("A", door.getRegionA());
        tag.putInt("B", door.getRegionB());
        tag.putString("Facing", door.getFacing() != null ? door.getFacing().name() : Direction2D.NONE.name());
        return tag;
    }

    public static DoorData readDoor(CompoundTag tag) {
        return new DoorData(
                tag.getInt("X"),
                tag.getInt("Z"),
                tag.getInt("A"),
                tag.getInt("B"),
                readFacing(tag.getString("Facing")));
    }

    // -------- helpers --------

    private static RoomRole readRole(String name) {
        try {
            return RoomRole.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return RoomRole.NORMAL;
        }
    }

    private static Direction2D readFacing(String name) {
        try {
            return Direction2D.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Direction2D.NONE;
        }
    }

    /** Flattens a coord list to (x0,z0,x1,z1,…). The {@code y} field of Coords2D is the grid Z. */
    static int[] flatten(List<Coords2D> coords) {
        int[] out = new int[coords.size() * 2];
        for (int i = 0; i < coords.size(); i++) {
            Coords2D c = coords.get(i);
            out[2 * i] = c.getX();
            out[2 * i + 1] = c.getY();
        }
        return out;
    }

    /** Inverse of {@link #flatten}. Trailing odd element (corrupt data) is ignored. */
    static List<Coords2D> unflatten(int[] flat) {
        List<Coords2D> out = new ArrayList<>(flat.length / 2);
        for (int i = 0; i + 1 < flat.length; i += 2) {
            out.add(new Coords2D(flat[i], flat[i + 1]));
        }
        return out;
    }
}
