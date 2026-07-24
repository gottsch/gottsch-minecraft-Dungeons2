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

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.BasicCorridorGenerator;
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
 * Procedural piece wrapping one {@link CorridorData} region. Renders with the
 * grid-free {@link BasicCorridorGenerator} overload, reading wall columns from
 * {@link CorridorData#getWallCells()} (folded in by the planner) rather than the
 * transient maze grid &mdash; so the piece round-trips through NBT without it.
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public class DungeonCorridorPiece extends DungeonPiece {

    /** Corridor walls are 5 blocks tall (floorY .. floorY+4). */
    private static final int CORRIDOR_WALL_HEIGHT = 5;

    private CorridorData corridor;

    public DungeonCorridorPiece(CorridorData corridor, String motifValue, int floorY, int anchorX, int anchorZ) {
        super(StructurePieces.CORRIDOR, motifValue, floorY, anchorX, anchorZ,
                computeBox(corridor, floorY, anchorX, anchorZ));
        this.corridor = corridor;
    }

    public DungeonCorridorPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieces.CORRIDOR, tag);
        this.corridor = PieceNbt.readCorridor(tag.getCompound("Corridor"));
    }

    /** World bounding box: covers every corridor cell and wall cell; Y = floor .. floor+4. */
    private static BoundingBox computeBox(CorridorData corridor, int floorY, int anchorX, int anchorZ) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Coords2D c : allCells(corridor)) {
            minX = Math.min(minX, c.getX());
            maxX = Math.max(maxX, c.getX());
            minZ = Math.min(minZ, c.getY());
            maxZ = Math.max(maxZ, c.getY());
        }
        if (minX == Integer.MAX_VALUE) {
            // Degenerate empty corridor: a unit box at the anchor avoids an invalid bbox.
            return new BoundingBox(anchorX, floorY, anchorZ,
                    anchorX, floorY + CORRIDOR_WALL_HEIGHT - 1, anchorZ);
        }
        return new BoundingBox(
                anchorX + minX, floorY, anchorZ + minZ,
                anchorX + maxX, floorY + CORRIDOR_WALL_HEIGHT - 1, anchorZ + maxZ);
    }

    private static List<Coords2D> allCells(CorridorData corridor) {
        List<Coords2D> all = new ArrayList<>(corridor.getCells().size() + corridor.getWallCells().size());
        all.addAll(corridor.getCells());
        all.addAll(corridor.getWallCells());
        return all;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.put("Corridor", PieceNbt.writeCorridor(corridor));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        logChunkTouch(level, chunkPos, box);
        // Render from a piece-stable seed, not the chunk-seeded `random`.
        placeAll(level, box, renderPlacements());
    }

    /** Builds this corridor's placements deterministically (no external RNG). */
    public List<BlockPlacement> renderPlacements() {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().build(corridor, floorY, motif(), deterministicRandom(corridor.getId()), out);
        return out;
    }

    public CorridorData getCorridor() {
        return corridor;
    }
}
