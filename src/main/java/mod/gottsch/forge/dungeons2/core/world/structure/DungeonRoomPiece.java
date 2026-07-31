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

import mod.gottsch.forge.dungeons2.core.config.FloorPatternConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternConfigHelper;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural piece wrapping one {@link RoomData} (a NORMAL maze room). At
 * {@code postProcess} it re-runs {@link BasicRoomGenerator} and writes the
 * resulting placements clipped to the chunk box.
 *
 * <p>START / END rooms are <em>not</em> wrapped by this piece &mdash; those slots
 * are covered by the entrance / transition template pieces, and the Phase 4
 * emitter skips them. This piece simply renders whatever {@link RoomData} it is
 * handed, so it stays agnostic to role policy.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public class DungeonRoomPiece extends DungeonPiece {

    private RoomData room;

    public DungeonRoomPiece(RoomData room, String motifValue, int floorY, int anchorX, int anchorZ) {
        super(StructurePieces.ROOM, motifValue, floorY, anchorX, anchorZ,
                computeBox(room, floorY, anchorX, anchorZ));
        this.room = room;
    }

    public DungeonRoomPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieces.ROOM, tag);
        this.room = PieceNbt.readRoom(tag.getCompound("Room"));
    }

    /**
     * World bounding box: the room footprint with a 1-cell XZ margin for walls,
     * and Y from the floor plane up through the room's height.
     */
    private static BoundingBox computeBox(RoomData room, int floorY, int anchorX, int anchorZ) {
        int minX = anchorX + room.getOriginX() - 1;
        int minZ = anchorZ + room.getOriginZ() - 1;
        int maxX = anchorX + room.getOriginX() + room.getWidth();
        int maxZ = anchorZ + room.getOriginZ() + room.getDepth();
        int minY = floorY;
        int maxY = floorY + Math.max(1, room.getHeight()) - 1;
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.put("Room", PieceNbt.writeRoom(room));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        logChunkTouch(level, chunkPos, box);
        FloorPatternConfig floorPatternConfig = FloorPatternConfigHelper.get(level.registryAccess(), motifValue);
        // Render from a piece-stable seed, not the chunk-seeded `random` (see
        // DungeonPiece#deterministicRandom) so the result is identical in every chunk.
        safePlaceAll(level, box, () -> renderPlacements(floorPatternConfig));
    }

    /** Builds this room's placements deterministically (no external RNG), always plain floor. */
    public List<BlockPlacement> renderPlacements() {
        return renderPlacements(FloorPatternConfig.DEFAULT);
    }

    /** Builds this room's placements deterministically (no external RNG). */
    public List<BlockPlacement> renderPlacements(FloorPatternConfig floorPatternConfig) {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicRoomGenerator().withFloorPatternConfig(floorPatternConfig)
                .build(room, floorY, motif(), deterministicRandom(room.getId()), out);
        return out;
    }

    public RoomData getRoom() {
        return room;
    }
}
