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
import mod.gottsch.forge.dungeons2.core.data.TransitionData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The Phase 4 bridge: turns a planned {@link DungeonLayout} into the flat list of
 * {@link StructurePiece}s that {@link DungeonStructure} hands to the worldgen
 * {@code StructurePiecesBuilder}.
 *
 * <p>Pure function of its inputs &mdash; no randomness, no block writes, no
 * Minecraft worldgen context. That keeps it unit-testable without a
 * {@code GenerationContext}: the only Minecraft dependency is the (optional)
 * {@link StructureTemplateManager} needed to construct the template-backed
 * entrance / transition pieces.</p>
 *
 * <h2>Coordinate model</h2>
 * <p>The dungeon's floor-local grid origin {@code (0,0)} maps to world XZ
 * {@code (anchorX, anchorZ)} &mdash; the same convention {@link DungeonPiece}
 * uses ({@code worldX = anchorX + localX}). Procedural pieces receive the anchor
 * directly; template pieces are positioned at their footprint's min corner in
 * world space.</p>
 *
 * <h2>What is skipped</h2>
 * <p>{@link RoomRole#START} / {@link RoomRole#END} rooms are <em>not</em> emitted
 * as procedural room pieces &mdash; those slots are covered by the entrance and
 * transition template pieces respectively (see {@link RoomData}'s role doc).</p>
 *
 * <h2>4a note (synthetic templates)</h2>
 * <p>No entrance / transition {@code .nbt} prefabs ship yet, so those pieces load
 * an empty template and place nothing at {@code postProcess}; they are still
 * emitted so the bridge is complete and the jigsaw-assembled entrance (Phase 4b)
 * can replace the entrance emission at a single seam ({@link #emitEntrance}).</p>
 *
 * @author Mark Gottschling on Jun 19, 2026
 */
public final class DungeonPieceEmitter {

    private DungeonPieceEmitter() {}

    /**
     * Emits every piece for {@code layout}.
     *
     * @param templateManager used to build the entrance / transition template
     *                         pieces; pass {@code null} (e.g. in unit tests) to
     *                         emit only the procedural room / corridor / door
     *                         pieces and skip the template-backed ones.
     */
    public static List<StructurePiece> emit(DungeonLayout layout, int anchorX, int anchorZ,
                                            StructureTemplateManager templateManager) {
        List<StructurePiece> pieces = new ArrayList<>();
        String motif = layout.getMotifValue();

        // The entrance is no longer emitted here. As of Phase 4b it is assembled
        // by vanilla JigsawPlacement in DungeonStructure.findGenerationPoint and
        // its pieces are added to the builder directly; the planner's START slot
        // is reserved from that assembled geometry (see withAssembledEntrance).

        // Transition template pieces go in FIRST, same reason as the assembled
        // entrance: their authored dungeons2:door candidates are solid wall, and
        // the maze's chosen door for that slot must be placed AFTER so it overwrites
        // the template's column rather than the other way around.
        if (templateManager != null) {
            for (TransitionData transition : layout.getTransitions()) {
                Rectangle2D fp = transition.getFootprint();
                ResourceLocation templateId = new ResourceLocation(transition.getTemplateId());
                Rotation rotation = toRotation(transition.getRotation());
                // The planner reserved fp as a plain axis-aligned box in the maze
                // grid; correct for rotation-around-corner so the PLACED piece's
                // bounding box actually lands there too (see
                // TemplateLoader#correctedOriginForRotation for why the naive min
                // corner is wrong for 3 of every 4 rotations).
                BlockPos desiredMin = new BlockPos(
                        anchorX + fp.getMinX(), transition.getLowerY(), anchorZ + fp.getMinY());
                BlockPos pos = TemplateLoader.correctedOriginForRotation(
                        templateManager, templateId, rotation, desiredMin);
                pieces.add(new DungeonTransitionPiece(templateManager, templateId, pos, rotation, motif,
                        transition.getUpperFloorIndex(), transition.getLowerFloorIndex()));
            }
        }

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

    /** Maps a 0/90/180/270 degree rotation to the vanilla {@link Rotation} enum. */
    private static Rotation toRotation(int degrees) {
        return switch (((degrees % 360) + 360) % 360) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
