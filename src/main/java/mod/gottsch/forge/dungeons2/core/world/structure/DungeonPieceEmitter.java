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
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.ArrayList;
import java.util.List;

/**
 * The Phase 4 bridge: turns a planned {@link DungeonLayout} into the flat list of
 * {@link StructurePiece}s that {@link DungeonStructure} hands to the worldgen
 * {@code StructurePiecesBuilder}.
 *
 * <p>Pure function of its inputs &mdash; no randomness, no block writes, no
 * Minecraft worldgen context, no Minecraft imports at all.</p>
 *
 * <h2>Coordinate model</h2>
 * <p>The dungeon's floor-local grid origin {@code (0,0)} maps to world XZ
 * {@code (anchorX, anchorZ)} &mdash; the same convention {@link DungeonPiece}
 * uses ({@code worldX = anchorX + localX}).</p>
 *
 * <h2>What is skipped</h2>
 * <p>{@link RoomRole#START} / {@link RoomRole#END} rooms are <em>not</em> emitted
 * as procedural room pieces here &mdash; those slots are covered by the assembled
 * entrance and transition jigsaw pieces respectively (see {@link RoomData}'s role
 * doc), which {@link DungeonStructure} adds to the worldgen builder directly since
 * they're real vanilla {@code PoolElementStructurePiece}s, not something this
 * class constructs.</p>
 *
 * @author Mark Gottschling on Jun 19, 2026
 */
public final class DungeonPieceEmitter {

    private DungeonPieceEmitter() {}

    /** Emits every procedural piece for {@code layout} (rooms, corridors, doors). */
    public static List<StructurePiece> emit(DungeonLayout layout, int anchorX, int anchorZ) {
        List<StructurePiece> pieces = new ArrayList<>();
        String motif = layout.getMotifValue();

        // The entrance and transitions are no longer emitted here. Both are
        // assembled by vanilla JigsawPlacement in DungeonStructure.findGenerationPoint
        // and their pieces are added to the builder directly; the planner's START/END
        // slots are reserved from that assembled geometry (see withAssembledEntrance
        // and DungeonStackPlanner.TransitionAssembler).

        for (FloorLayout floor : layout.getFloors()) {
            int floorY = floor.getFloorY();
            for (RoomData room : floor.getRooms()) {
                // START / END slots are the template pieces' job; skip them here.
                if (room.getRole() == RoomRole.NORMAL) {
                    pieces.add(new DungeonRoomPiece(room, motif, floorY, anchorX, anchorZ));
                }
            }
            for (CorridorData corridor : floor.getCorridors()) {
                pieces.add(new DungeonCorridorPiece(corridor, motif, floorY, anchorX, anchorZ));
            }
            for (DoorData door : floor.getDoors()) {
                pieces.add(new DungeonDoorPiece(door, motif, floorY, anchorX, anchorZ));
            }
        }

        return pieces;
    }
}
