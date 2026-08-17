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
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 3 deliverable: the procedural pieces serialize round-trip cleanly.
 *
 * <p>Mirrors {@code StructurePiece.createTag} (minus the registry {@code "id"}
 * field, which would require registering the piece types post-bootstrap) so the
 * test exercises the real {@code addAdditionalSaveData} and load constructors
 * without depending on the frozen {@code STRUCTURE_PIECE} registry.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
class DungeonPieceRoundTripTest {

    /**
     * Deliberately NOT 0. A round-trip assertion on a zero-valued int passes whether the field is
     * written and read or dropped entirely -- {@code getInt} answers 0 for an absent key -- so the
     * default value is the one number that cannot test this.
     */
    private static final int TEST_FLOOR_INDEX = 3;

    private static final String MOTIF = "classic";
    private static final int FLOOR_Y = 40;
    private static final int ANCHOR_X = 128;
    private static final int ANCHOR_Z = 256;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Reconstructs the save tag StructurePiece.createTag would build (sans "id"). */
    private static CompoundTag save(DungeonPiece piece) {
        CompoundTag tag = new CompoundTag();
        BoundingBox.CODEC.encodeStart(NbtOps.INSTANCE, piece.getBoundingBox())
                .resultOrPartial(err -> {})
                .ifPresent(t -> tag.put("BB", t));
        Direction orientation = piece.getOrientation();
        tag.putInt("O", orientation == null ? -1 : orientation.get2DDataValue());
        tag.putInt("GD", piece.getGenDepth());
        // Context is unused by the procedural pieces' addAdditionalSaveData.
        piece.addAdditionalSaveData(null, tag);
        return tag;
    }

    @Test
    void roomPieceRoundTrips() {
        RoomData room = new RoomData(7, 4, 6, 5, 9, 8, RoomRole.NORMAL);
        room.getDoorways().add(new Coords2D(4, 8));
        room.getDoorways().add(new Coords2D(8, 6));

        DungeonRoomPiece original = new DungeonRoomPiece(room, MOTIF, FLOOR_Y, TEST_FLOOR_INDEX, ANCHOR_X, ANCHOR_Z);
        DungeonRoomPiece loaded = new DungeonRoomPiece(null, save(original));

        assertEquals(original.getBoundingBox(), loaded.getBoundingBox(), "bounding box");
        assertEquals(TEST_FLOOR_INDEX, loaded.getFloorIndex(), "floor index");
        RoomData a = original.getRoom();
        RoomData b = loaded.getRoom();
        assertEquals(a.getId(), b.getId());
        assertEquals(a.getOriginX(), b.getOriginX());
        assertEquals(a.getOriginZ(), b.getOriginZ());
        assertEquals(a.getWidth(), b.getWidth());
        assertEquals(a.getDepth(), b.getDepth());
        assertEquals(a.getHeight(), b.getHeight());
        assertEquals(a.getRole(), b.getRole());
        assertEquals(a.getDoorways(), b.getDoorways(), "doorways");
    }

    @Test
    void corridorPieceRoundTrips() {
        CorridorData corridor = new CorridorData(3);
        corridor.getCells().add(new Coords2D(5, 5));
        corridor.getCells().add(new Coords2D(5, 6));
        corridor.getCells().add(new Coords2D(5, 7));
        corridor.getWallCells().add(new Coords2D(4, 5));
        corridor.getWallCells().add(new Coords2D(6, 5));
        corridor.getWallCells().add(new Coords2D(-1, 7)); // out-of-bounds wall is legal
        // Door cells drive the pierced-column render, so they must survive too --
        // the deserialized piece has no grid to re-derive them from.
        corridor.getDoorCells().add(new Coords2D(6, 7));

        DungeonCorridorPiece original = new DungeonCorridorPiece(corridor, MOTIF, FLOOR_Y, TEST_FLOOR_INDEX, ANCHOR_X, ANCHOR_Z);
        DungeonCorridorPiece loaded = new DungeonCorridorPiece(null, save(original));

        assertEquals(original.getBoundingBox(), loaded.getBoundingBox(), "bounding box");
        assertEquals(TEST_FLOOR_INDEX, loaded.getFloorIndex(), "floor index");
        assertEquals(original.getCorridor().getId(), loaded.getCorridor().getId());
        assertEquals(original.getCorridor().getCells(), loaded.getCorridor().getCells(), "cells");
        assertEquals(original.getCorridor().getWallCells(), loaded.getCorridor().getWallCells(), "wall cells");
        assertEquals(original.getCorridor().getDoorCells(), loaded.getCorridor().getDoorCells(), "door cells");
    }

    @Test
    void doorPieceRoundTrips() {
        DoorData door = new DoorData(12, 9, 2, 5, Direction2D.NORTH);

        DungeonDoorPiece original = new DungeonDoorPiece(door, MOTIF, FLOOR_Y, TEST_FLOOR_INDEX, ANCHOR_X, ANCHOR_Z);
        DungeonDoorPiece loaded = new DungeonDoorPiece(null, save(original));

        assertEquals(original.getBoundingBox(), loaded.getBoundingBox(), "bounding box");
        assertEquals(TEST_FLOOR_INDEX, loaded.getFloorIndex(), "floor index");
        DoorData a = original.getDoor();
        DoorData b = loaded.getDoor();
        assertEquals(a.getX(), b.getX());
        assertEquals(a.getZ(), b.getZ());
        assertEquals(a.getRegionA(), b.getRegionA());
        assertEquals(a.getRegionB(), b.getRegionB());
        assertEquals(a.getFacing(), b.getFacing());
    }

    @Test
    void motifAndAnchorSurviveRoundTrip() {
        RoomData room = new RoomData(1, 0, 0, 5, 5, 6, RoomRole.NORMAL);
        DungeonRoomPiece original = new DungeonRoomPiece(room, MOTIF, FLOOR_Y, TEST_FLOOR_INDEX, ANCHOR_X, ANCHOR_Z);
        CompoundTag tag = save(original);

        assertEquals(MOTIF, tag.getString("Motif"));
        assertEquals(FLOOR_Y, tag.getInt("FloorY"));
        assertEquals(ANCHOR_X, tag.getInt("AnchorX"));
        assertEquals(ANCHOR_Z, tag.getInt("AnchorZ"));

        // And the bounding box uses the anchor: min corner = anchor + origin - 1 margin.
        BoundingBox box = original.getBoundingBox();
        assertEquals(ANCHOR_X - 1, box.minX());
        assertEquals(ANCHOR_Z - 1, box.minZ());
        assertEquals(FLOOR_Y, box.minY());
    }

    @Test
    void roomDoorwaysSurviveWhenEmpty() {
        RoomData room = new RoomData(2, 2, 2, 7, 7, 7, RoomRole.NORMAL);
        DungeonRoomPiece loaded = new DungeonRoomPiece(null,
                save(new DungeonRoomPiece(room, MOTIF, FLOOR_Y, TEST_FLOOR_INDEX, ANCHOR_X, ANCHOR_Z)));
        List<Coords2D> doorways = loaded.getRoom().getDoorways();
        assertEquals(0, doorways.size(), "empty doorways should round-trip as empty");
    }
}
