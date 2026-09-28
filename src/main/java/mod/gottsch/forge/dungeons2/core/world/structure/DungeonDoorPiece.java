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
import mod.gottsch.forge.dungeons2.core.data.SecretDoorway;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.HiddenDoors;
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
import java.util.Optional;
import java.util.function.Predicate;

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
    /** Non-null when this is a secret room's doorway; see {@link #withSecret}. */
    private SecretDoorway secret;

    public DungeonDoorPiece(DoorData door, String motifValue, int floorY, int floorIndex,
                            int anchorX, int anchorZ) {
        super(StructurePieces.DOOR, motifValue, floorY, floorIndex, anchorX, anchorZ,
                computeBox(door, floorY, anchorX, anchorZ));
        this.door = door;
    }

    public DungeonDoorPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieces.DOOR, tag);
        this.door = PieceNbt.readDoor(tag.getCompound("Door"));
        if (tag.contains("Secret")) {
            this.secret = PieceNbt.readSecretDoorway(tag.getCompound("Secret"));
        }
    }

    /**
     * Makes this a secret room's doorway: a hidden door, a wall-block lintel and a lever sconce on
     * the corridor wall beside it. Set by {@code SecretRoomPlanner} at generation, once the room's
     * scheme has been rolled secret.
     *
     * <p><strong>Widens the bounding box</strong> to the lever's and decoy's cells beside the door,
     * and that is load-bearing rather than tidy: {@code postProcess} is only called for the chunks
     * a piece's box overlaps, so a lever one cell across a chunk border from its door would never be
     * written at all.</p>
     */
    public DungeonDoorPiece withSecret(SecretDoorway secret) {
        this.secret = secret;
        if (secret != null) {
            this.boundingBox = computeSecretBox(secret, floorY, anchorX, anchorZ);
        }
        return this;
    }

    public SecretDoorway getSecret() {
        return secret;
    }

    /** The door column, the two wall cells beside it and the two corridor cells in front of those. */
    private static BoundingBox computeSecretBox(SecretDoorway secret, int floorY, int anchorX,
                                                int anchorZ) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Coords2D cell : List.of(secret.door(), secret.leverWall(), secret.lever(),
                secret.decoyWall(), secret.decoyCell())) {
            minX = Math.min(minX, cell.getX());
            maxX = Math.max(maxX, cell.getX());
            minZ = Math.min(minZ, cell.getY());
            maxZ = Math.max(maxZ, cell.getY());
        }
        return new BoundingBox(anchorX + minX, floorY, anchorZ + minZ,
                anchorX + maxX, floorY + DOOR_COLUMN_HEIGHT - 1, anchorZ + maxZ);
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
        if (secret != null) {
            tag.put("Secret", PieceNbt.writeSecretDoorway(secret));
        }
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        // #45: the motif AS BUILT ON THIS FLOOR. A motif with no strata hands back itself,
        // so this is a no-op for everything shipped today. Build time, not plan time -- both
        // inputs are in hand right here and nothing needs serialising.
        MotifConfig motif = MotifConfigHelper.get(level.registryAccess(), motifValue);
        // #45 step 4: asked of the UNPROJECTED motif -- forFloor clears the strata table, so a
        // projection has no band left to name. Same reason DungeonStructure asks it of the motif.
        Optional<String> stratum = motif.stratumNameFor(floorIndex);
        MotifConfig motifConfig = motif.forFloor(floorIndex);
        // Render from a piece-stable seed, not the chunk-seeded `random`.
        safePlaceAll(level, box, stratum, () -> renderPlacements(motifConfig));
    }

    /** Builds this door's placements deterministically (no external RNG), motif defaults. */
    public List<BlockPlacement> renderPlacements() {
        return renderPlacements(MotifConfig.DEFAULT);
    }

    /** Builds this door's placements deterministically (no external RNG). */
    public List<BlockPlacement> renderPlacements(MotifConfig motifConfig) {
        return renderPlacements(motifConfig, HiddenDoors::isRegistered);
    }

    /**
     * As above, asking {@code registered} whether a hidden door block exists. The seam is for
     * tests, which run without DungeonBlocks' registry.
     */
    public List<BlockPlacement> renderPlacements(MotifConfig motifConfig, Predicate<String> registered) {
        List<BlockPlacement> out = new ArrayList<>();
        BasicDoorGenerator generator = new BasicDoorGenerator().withMotifConfig(motifConfig);
        if (secret != null) {
            // Resolved against THIS floor's wall, as the room's wall is. The planner only made the
            // room secret where this resolved, so empty here means the pack changed under a saved
            // structure; an ordinary door is the degrade, because a hidden door that is missing is a
            // room sealed for good.
            Optional<String> hiddenDoor = hiddenDoorFor(motifConfig, registered);
            if (hiddenDoor.isPresent()) {
                generator.withSecret(secret, hiddenDoor.get());
            }
        }
        generator.build(door, floorY, motif(), deterministicRandom(doorDiscriminator()), out);
        return out;
    }

    /**
     * The hidden door matching this floor's wall stone, when DungeonBlocks has one registered. The
     * one check {@code SecretRoomPlanner} makes before calling a room hideable, so the two cannot
     * disagree about which rooms may be secret.
     */
    public static Optional<String> hiddenDoorFor(MotifConfig motifConfig, Predicate<String> registered) {
        String wallId = BlockStateCodec.placement(0, 0, 0, motifConfig.wall().wallState()).getBlockId();
        return HiddenDoors.forWall(wallId).filter(registered);
    }

    /** Packs the door's floor-local XZ into a stable per-piece seed discriminator. */
    private long doorDiscriminator() {
        return ((long) door.getX() << 32) ^ (door.getZ() & 0xFFFFFFFFL);
    }

    public DoorData getDoor() {
        return door;
    }
}
