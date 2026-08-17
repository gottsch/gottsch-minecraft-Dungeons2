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

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigHelper;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.BasicDoorGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedural piece wrapping one {@link DoorData} doorway. Renders the 4-block
 * door column (sill, two halves, lintel) via {@link BasicDoorGenerator}.
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public class DungeonDoorPiece extends DungeonPiece {

    /** A door column spans Y = floorY .. floorY+3 (sill, lower, upper, lintel). */
    private static final int DOOR_COLUMN_HEIGHT = 4;

    private DoorData door;

    public DungeonDoorPiece(DoorData door, String motifValue, int floorY, int floorIndex,
                            int anchorX, int anchorZ) {
        super(StructurePieces.DOOR, motifValue, floorY, floorIndex, anchorX, anchorZ,
                computeBox(door, floorY, anchorX, anchorZ));
        this.door = door;
    }

    public DungeonDoorPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieces.DOOR, tag);
        this.door = PieceNbt.readDoor(tag.getCompound("Door"));
    }

    /** World bounding box: the single door cell column. */
    private static BoundingBox computeBox(DoorData door, int floorY, int anchorX, int anchorZ) {
        int x = anchorX + door.getX();
        int z = anchorZ + door.getZ();
        return new BoundingBox(x, floorY, z, x, floorY + DOOR_COLUMN_HEIGHT - 1, z);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.put("Door", PieceNbt.writeDoor(door));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        MotifConfig motifConfig = MotifConfigHelper.get(level.registryAccess(), motifValue);
        // Render from a piece-stable seed, not the chunk-seeded `random`.
        safePlaceAll(level, box, () -> renderPlacements(motifConfig));
    }

    /** Builds this door's placements deterministically (no external RNG), motif defaults. */
    public List<BlockPlacement> renderPlacements() {
        return renderPlacements(MotifConfig.DEFAULT);
    }

    /** Builds this door's placements deterministically (no external RNG). */
    public List<BlockPlacement> renderPlacements(MotifConfig motifConfig) {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicDoorGenerator().withMotifConfig(motifConfig)
                .build(door, floorY, motif(), deterministicRandom(doorDiscriminator()), out);
        return out;
    }

    /** Packs the door's floor-local XZ into a stable per-piece seed discriminator. */
    private long doorDiscriminator() {
        return ((long) door.getX() << 32) ^ (door.getZ() & 0xFFFFFFFFL);
    }

    public DoorData getDoor() {
        return door;
    }
}
