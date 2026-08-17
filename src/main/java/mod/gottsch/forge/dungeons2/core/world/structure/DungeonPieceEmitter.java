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
 * doc). {@link RoomRole#TERMINAL} <em>is</em> emitted: nothing covers the bottom
 * floor's final room, and skipping it left a door opening into raw terrain. Likewise, a {@link RoomRole#NORMAL} room whose {@link RoomData#getTemplateId()}
 * is non-null (Phase 8: jigsaw-assembled interior room) is skipped too. In every
 * such case {@link DungeonStructure} adds the real assembled pieces to the worldgen
 * builder directly since they're real vanilla {@code PoolElementStructurePiece}s,
 * not something this class constructs.</p>
 *
 * @author Mark Gottschling on Jun 19, 2026
 */
public final class DungeonPieceEmitter {

    private DungeonPieceEmitter() {}

    /**
     * Emits every procedural piece for {@code layout} (corridors, rooms, doors), in render order.
     *
     * <p>{@link DungeonStructure} does <strong>not</strong> use this: it needs to slot the
     * jigsaw-assembled prefab rooms in between the terrain and the doors, so it calls
     * {@link #emitTerrain} and {@link #emitDoors} separately. This whole-list form is kept for
     * callers that just want the procedural pieces &mdash; the floor-plan exporter and the
     * emitter's own tests &mdash; and must stay equal to the two halves concatenated.</p>
     */
    public static List<StructurePiece> emit(DungeonLayout layout, int anchorX, int anchorZ) {
        List<StructurePiece> pieces = new ArrayList<>(emitTerrain(layout, anchorX, anchorZ));
        pieces.addAll(emitDoors(layout, anchorX, anchorZ));
        return pieces;
    }

    /**
     * Corridors and procedural rooms, in that order &mdash; everything except the doors.
     *
     * <p>Split out from {@link #emit} so the caller can render the authored prefab rooms
     * <em>after</em> these but <em>before</em> the doors. Both halves of that sandwich matter, and
     * for different reasons: see {@link #emitDoors}.</p>
     */
    public static List<StructurePiece> emitTerrain(DungeonLayout layout, int anchorX, int anchorZ) {
        List<StructurePiece> pieces = new ArrayList<>();
        String motif = layout.getMotifValue();

        // The entrance and transitions are no longer emitted here. Both are
        // assembled by vanilla JigsawPlacement in DungeonStructure.findGenerationPoint
        // and their pieces are added to the builder directly; the planner's START/END
        // slots are reserved from that assembled geometry (see withAssembledEntrance
        // and DungeonStackPlanner.TransitionAssembler).

        for (FloorLayout floor : layout.getFloors()) {
            int floorY = floor.getFloorY();
            int floorIndex = floor.getFloorIndex();
            // Corridors BEFORE rooms, and that order is load-bearing. A room's perimeter is
            // CellType.WALL, so a corridor beside it emits a wall column over the very same
            // cells -- unconditionally, with the motif's plain wall block and no knowledge of
            // the room's scheme. Pieces are written in this order, last writer wins, so with
            // rooms first the corridor erased the room's own wall: measured across SMALL,
            // MEDIUM and LARGE, only ~35% of room wall sides were owned by their own room and
            // corridors owned ~50%. Reversing it takes the room's share to ~85%.
            //
            // This is narrower than it looks. A room only ever writes inside its own box and a
            // corridor never writes a room's interior (that is CellType.ROOM, which
            // isWallElement rejects), so the cells in contention are exactly the room's own
            // perimeter. The rule this encodes is just "a room owns its own wall".
            //
            // Doors are no longer emitted here -- see emitDoors, and the sandwich note above.
            for (CorridorData corridor : floor.getCorridors()) {
                pieces.add(new DungeonCorridorPiece(corridor, motif, floorY, floorIndex, anchorX, anchorZ));
            }
            for (RoomData room : floor.getRooms()) {
                // START / END slots are the template pieces' job; skip them here.
                // TERMINAL is not one of those -- the bottom floor has no downstairs
                // transition to cover it, so this mod builds it (see RoomRole).
                // A NORMAL room that got a Phase 8 jigsaw-assembled prefab instead of a
                // procedural build (templateId non-null) is skipped for the same reason.
                if (room.getRole().isProcedurallyBuilt() && room.getTemplateId() == null) {
                    pieces.add(new DungeonRoomPiece(room, motif, floorY, floorIndex, anchorX, anchorZ));
                }
            }
        }

        return pieces;
    }

    /**
     * The door pieces, which must be rendered <strong>after everything else</strong>.
     *
     * <p>Both a room and its corridor deliberately leave the two door-half rows as air, but from
     * different sources ({@code RoomData#getDoorways} vs the grid's {@code DOOR} cells), and the
     * door piece is what actually hangs the door.
     *
     * <p><strong>This is also what makes rendering prefab rooms late safe.</strong> A prefab writes
     * its own {@code dungeons2:door} marker cells from the jigsaw's {@code final_state} &mdash; it
     * has no idea whether the maze opened that candidate &mdash; so a prefab rendered after its
     * doors would seal them again. Prefabs go between the terrain and this. ({@code
     * dungeons2:connector} cells are exempt from the whole question: they never get a door piece,
     * because the template already has a real built door there.)</p>
     */
    public static List<StructurePiece> emitDoors(DungeonLayout layout, int anchorX, int anchorZ) {
        List<StructurePiece> pieces = new ArrayList<>();
        String motif = layout.getMotifValue();
        for (FloorLayout floor : layout.getFloors()) {
            for (DoorData door : floor.getDoors()) {
                pieces.add(new DungeonDoorPiece(door, motif, floor.getFloorY(), floor.getFloorIndex(),
                        anchorX, anchorZ));
            }
        }
        return pieces;
    }
}
