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

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSubstitutor;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Base for the three procedural Phase 3 pieces ({@link DungeonRoomPiece},
 * {@link DungeonCorridorPiece}, {@link DungeonDoorPiece}).
 *
 * <p>Holds the fields every procedural piece needs to re-run its Phase 2
 * builder at {@code postProcess} time: the dungeon {@code motif}, the floor's
 * walking-plane {@code floorY} (absolute world Y), and the dungeon's world-space
 * {@code anchor} XZ. The piece's {@link #getBoundingBox() world bounding box} is
 * computed by each subclass from its layout data and round-trips automatically
 * through {@link StructurePiece}'s base NBT (the {@code "BB"} tag).</p>
 *
 * <h2>Coordinate flow</h2>
 * <p>Phase 2 builders emit placements in <em>floor-local grid XZ</em> with
 * <em>absolute world Y</em>. This base translates each placement to world space
 * ({@code anchorX + x}, {@code y}, {@code anchorZ + z}), then to piece-local space
 * for {@link #placeBlock} (which clips to the chunk {@code box} the engine hands
 * us). Pieces are constructed with {@code NORTH} orientation: X and Y are a plain
 * min-offset, but vanilla {@link StructurePiece#getWorldZ} <em>mirrors</em> Z
 * around the piece's own bounding box for that orientation, so piece-local Z is
 * {@code pieceBox.maxZ() - worldZ}, not a min-offset (see {@link #placeAll}).</p>
 *
 * <p><strong>Chunk-safety invariant:</strong> {@code postProcess} never reads or
 * writes outside {@code box}. {@link #placeBlock} drops any placement whose world
 * position is outside the chunk box; block-entity application is guarded by the
 * same {@code box.isInside} check.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public abstract class DungeonPiece extends StructurePiece {

    protected String motifValue;
    protected int floorY;
    protected int anchorX;
    protected int anchorZ;

    /** Planning constructor. */
    protected DungeonPiece(StructurePieceType type, String motifValue, int floorY,
                           int anchorX, int anchorZ, BoundingBox box) {
        super(type, 0, box);
        this.motifValue = motifValue;
        this.floorY = floorY;
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
        // NORTH = no rotation; piece-local maps to world by a pure min-offset.
        setOrientation(net.minecraft.core.Direction.NORTH);
    }

    /** Load constructor. {@code super} restores genDepth / orientation / bounding box. */
    protected DungeonPiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
        this.motifValue = tag.getString("Motif");
        this.floorY = tag.getInt("FloorY");
        this.anchorX = tag.getInt("AnchorX");
        this.anchorZ = tag.getInt("AnchorZ");
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context,
                                         CompoundTag tag) {
        tag.putString("Motif", motifValue == null ? "" : motifValue);
        tag.putInt("FloorY", floorY);
        tag.putInt("AnchorX", anchorX);
        tag.putInt("AnchorZ", anchorZ);
    }

    /** TEMP (Jul 24): dedup set for {@link #logChunkTouch}, one line per (piece, chunk). */
    private static final java.util.Set<String> CHUNK_TOUCH_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * TEMP (Jul 24): "door into untouched terrain" investigation. Logs every chunk
     * a procedural piece's {@code postProcess} is ACTUALLY invoked for, with that
     * chunk's current status, so it can be diffed against {@code DungeonStructure}'s
     * {@code [D2-EXPECT]} line (the full chunk range every piece's bounding box SAYS
     * it should touch) to find a chunk that was silently skipped — i.e. {@code
     * /place} not calling postProcess at all for a chunk that had already finished
     * generating before the command ran. Remove once resolved.
     */
    protected void logChunkTouch(WorldGenLevel level, ChunkPos chunkPos, BoundingBox box) {
        String key = getClass().getSimpleName() + "@anchor(" + anchorX + "," + anchorZ + ")@chunk("
                + chunkPos.x + "," + chunkPos.z + ")";
        if (!CHUNK_TOUCH_LOGGED.add(key)) {
            return;
        }
        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY, false);
        Dungeons.LOGGER.warn(
                "[D2-TOUCH] {} anchor=({},{}) chunk=({},{}) status={} box=[{}..{},{}..{},{}..{}]",
                getClass().getSimpleName(), anchorX, anchorZ, chunkPos.x, chunkPos.z,
                chunk == null ? "NULL" : chunk.getStatus(),
                box.minX(), box.maxX(), box.minY(), box.maxY(), box.minZ(), box.maxZ());
    }

    /** Resolves the stored motif string to an enum, defaulting to CLASSIC. */
    protected IDungeonMotif motif() {
        if (motifValue != null) {
            DungeonMotif motif = DungeonMotif.getByValue(motifValue);
            if (motif != null && motif != DungeonMotif.UNKNOWN) {
                return motif;
            }
        }
        return DungeonMotif.CLASSIC;
    }

    /**
     * Writes every placement into the world, translated from floor-local to
     * world to piece-local, clipped to the chunk {@code box}. Applies any
     * {@link BlockEntityData} for positions that actually landed inside the box.
     */
    protected void placeAll(WorldGenLevel level, BoundingBox box, List<BlockPlacement> placements) {
        // Position-seeded weathering pass (the procedural-side StructureProcessor
        // analogue). Keyed on absolute world position so it's identical across the
        // per-chunk re-runs of postProcess; mutates the freshly-built list in place.
        BlockSubstitutor.substitute(placements, motifValue, anchorX, anchorZ);

        BoundingBox pieceBox = getBoundingBox();
        for (BlockPlacement p : placements) {
            int worldX = anchorX + p.getX();
            int worldY = p.getY();
            int worldZ = anchorZ + p.getZ();

            // Vanilla StructurePiece#getWorldZ mirrors Z for NORTH orientation
            // (`boundingBox.maxZ() - z`), not a plain `minZ() + z` offset like X and Y
            // get. This piece is never rotated, so undo exactly that reflection here;
            // getWorldPos then round-trips back to the true worldZ.
            int localX = worldX - pieceBox.minX();
            int localY = worldY - pieceBox.minY();
            int localZ = pieceBox.maxZ() - worldZ;

            BlockState state = BlockStateCodec.resolve(p);
            placeBlock(level, state, localX, localY, localZ, box);

            BlockEntityData be = p.getBlockEntityNbt();
            if (be != null) {
                BlockPos worldPos = new BlockPos(worldX, worldY, worldZ);
                if (box.isInside(worldPos)) {
                    applyBlockEntity(level, worldPos, be);
                }
            }
        }
    }

    /**
     * Best-effort block-entity application. Builds a full BE tag (id + position +
     * stringified fields) and loads it into the placed block entity. No Phase 2
     * builder populates {@link BlockEntityData} yet, so this path is dormant; it
     * is wired now so spawner / chest content lights up the moment a builder
     * starts emitting it, without another piece change.
     */
    protected void applyBlockEntity(WorldGenLevel level, BlockPos pos, BlockEntityData data) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        try {
            CompoundTag tag = blockEntity.saveWithFullMetadata();
            if (data.getType() != null) {
                tag.putString("id", data.getType());
            }
            for (Map.Entry<String, String> entry : data.getData().entrySet()) {
                putParsed(tag, entry.getKey(), entry.getValue());
            }
            blockEntity.load(tag);
            blockEntity.setChanged();
        } catch (Exception e) {
            Dungeons.LOGGER.warn("Failed to apply block-entity data {} at {}: {}",
                    data, pos, e.getMessage());
        }
    }

    /**
     * Builds placements (via {@code placementsSupplier}, which runs the Phase 2
     * builder) and writes them, logging and rethrowing any exception through our
     * own logger first. Vanilla's command dispatcher (e.g. {@code /place
     * structure}) swallows an exception thrown mid-command with just a generic
     * chat message and nothing in the logs, so without this, a bug in a builder
     * (wall/floor/ceiling generator, etc.) is nearly impossible to diagnose from
     * a live game session.
     */
    protected void safePlaceAll(WorldGenLevel level, BoundingBox box, Supplier<List<BlockPlacement>> placementsSupplier) {
        try {
            placeAll(level, box, placementsSupplier.get());
        } catch (RuntimeException e) {
            Dungeons.LOGGER.error("{} postProcess threw at anchor=({},{}) floorY={} box={}",
                    getClass().getSimpleName(), anchorX, anchorZ, floorY, box, e);
            throw e;
        }
    }

    /**
     * A {@link RandomSource} seeded purely from <em>chunk-independent</em> piece state
     * (world anchor XZ + floor Y + a per-piece {@code discriminator}). Procedural
     * pieces must render from this, <strong>not</strong> the {@code RandomSource}
     * {@code postProcess} is handed: that one is seeded per chunk, so a piece that
     * straddles a chunk border would draw different block-set variants on each side
     * (visible seam) when a pattern has more than one block set. Seeding from stable
     * state makes the full placement list byte-identical across every per-chunk re-run,
     * so clipping just shows different windows of the same result.
     *
     * @param discriminator a stable, chunk-independent id for this piece (e.g. a room
     *                      or corridor id, or a door's packed XZ) so sibling pieces on
     *                      the same floor still roll independent materials.
     */
    protected RandomSource deterministicRandom(long discriminator) {
        long h = mix(0x243F6A8885A308D3L ^ ((long) anchorX * 0x9E3779B97F4A7C15L));
        h = mix(h ^ ((long) floorY * 0xC2B2AE3D27D4EB4FL));
        h = mix(h ^ ((long) anchorZ * 0x165667B19E3779F9L));
        h = mix(h ^ (discriminator * 0xD1B54A32D192ED03L));
        return RandomSource.create(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Stores a value as an int when it parses cleanly, otherwise as a string. */
    private static void putParsed(CompoundTag tag, String key, String value) {
        try {
            tag.putInt(key, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            tag.putString(key, value);
        }
    }
}
