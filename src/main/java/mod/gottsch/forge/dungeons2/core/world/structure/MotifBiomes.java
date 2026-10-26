/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
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
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Paints a floor's mood biome (#129) over the dungeon's piece boxes, one chunk at a time.
 *
 * <p>Only the boxes, never the footprint: a cave crossing the rock between two rooms keeps its own
 * biome, and only where it meets a box does the 4&times;4&times;4 biome cell snap over a few blocks of
 * it. A cell is painted when it <em>overlaps</em> a box, so every walkable block is covered at the
 * price of up to three blocks of rock outside.
 *
 * <p>Called from {@link DungeonStructure#afterPlace}, which is handed the whole start once per chunk
 * &mdash; so it reaches the vanilla pool-element pieces (prefab rooms, transitions, end rooms) that
 * have no D2 {@code postProcess} to hook. It writes only the chunk being placed, so it is
 * order-independent. Which floor a pool piece's cell is on is decided by height; see {@link #plan}.
 * A prefab whose template the motif names in {@code template_biomes} paints its own mood instead
 * (#129 step 2).
 *
 * @author Mark Gottschling on 10/26/2026
 */
public final class MotifBiomes {

    /** Ids already reported as missing, so a broken pack warns once rather than once per chunk. */
    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();

    private MotifBiomes() {}

    /**
     * A piece's box and, for a D2 piece, the floor it was built for. Empty for a pool piece, whose
     * cells take their floor by height. {@code templateId} is a pool piece's template, when the
     * motif maps any templates to a biome; otherwise empty.
     *
     * <p>{@code parts}, when not empty, is what is painted instead of {@code box}: a corridor's box
     * is the bounding rectangle of every cell it winds through, which can take in whole rooms and
     * the rock between them, so a corridor is painted cell column by cell column.
     */
    public record PieceBox(BoundingBox box, OptionalInt floorIndex, Optional<String> templateId,
                           List<BoundingBox> parts) {

        public PieceBox(BoundingBox box, OptionalInt floorIndex, Optional<String> templateId) {
            this(box, floorIndex, templateId, List.of());
        }

        public PieceBox(BoundingBox box, OptionalInt floorIndex) {
            this(box, floorIndex, Optional.empty());
        }
    }

    /** One box of cells to paint, already clipped to the chunk, and what to paint it with. */
    public record Paint<T>(BoundingBox box, T biome) {}

    /**
     * Paints {@code chunkPos} for the pieces of one dungeon start. Does nothing for a motif that
     * names no biome on any floor.
     */
    public static void paint(WorldGenLevel level, BoundingBox chunkBox, ChunkPos chunkPos,
                             List<StructurePiece> pieces) {
        String motifValue = null;
        for (StructurePiece piece : pieces) {
            if (piece instanceof DungeonPiece dungeonPiece) {
                motifValue = dungeonPiece.getMotifValue();
                break;
            }
        }
        if (motifValue == null) {
            return;
        }
        MotifConfig motif = MotifConfigHelper.get(level.registryAccess(), motifValue);
        boolean anyTemplateBiomes = !motif.templateBiomes().isEmpty();

        // Paint order, a later list winning a shared cell: the rest, then rooms, then prefabs with a
        // mood of their own -- so neither a neighbouring corridor's cells nor a room's edge can
        // repaint a flooded sewer.
        List<PieceBox> boxes = new ArrayList<>(pieces.size());
        List<PieceBox> rooms = new ArrayList<>();
        List<PieceBox> moodPrefabs = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            if (piece instanceof DungeonPiece dungeonPiece) {
                OptionalInt floorIndex = OptionalInt.of(dungeonPiece.getFloorIndex());
                if (piece instanceof DungeonCorridorPiece corridor) {
                    boxes.add(new PieceBox(piece.getBoundingBox(), floorIndex, Optional.empty(),
                            piece.getBoundingBox().intersects(chunkBox) ? corridor.cellBoxes() : List.of()));
                } else {
                    (piece instanceof DungeonRoomPiece ? rooms : boxes).add(new PieceBox(piece.getBoundingBox(), floorIndex));
                }
            } else {
                // The template id costs a codec encode, so only for a piece in this chunk, and only
                // when the motif maps any template at all.
                Optional<String> templateId = anyTemplateBiomes
                        && piece instanceof PoolElementStructurePiece pool
                        && piece.getBoundingBox().intersects(chunkBox)
                        ? PoolElementIds.locationOf(pool.getElement())
                        : Optional.empty();
                PieceBox box = new PieceBox(piece.getBoundingBox(), OptionalInt.empty(), templateId);
                (templateId.flatMap(motif::biomeForTemplate).isPresent() ? moodPrefabs : boxes).add(box);
            }
        }
        boxes.addAll(rooms);
        boxes.addAll(moodPrefabs);

        List<Paint<ResourceLocation>> planned = plan(boxes, chunkBox, motif::biomeFor, motif::biomeForTemplate);
        if (planned.isEmpty()) {
            return;
        }
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<Paint<Holder<Biome>>> paints = new ArrayList<>(planned.size());
        for (Paint<ResourceLocation> paint : planned) {
            Optional<? extends Holder<Biome>> holder = registry.getHolder(ResourceKey.create(Registries.BIOME, paint.biome()));
            if (holder.isPresent()) {
                paints.add(new Paint<>(paint.box(), holder.get()));
            } else if (WARNED.add(paint.biome())) {
                Dungeons.LOGGER.warn("motif '{}' names biome {}, which is not registered; its pieces"
                        + " keep the ground's biome", motifValue, paint.biome());
            }
        }
        if (paints.isEmpty()) {
            return;
        }

        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);
        // The resolver reads the OLD cell for everything it does not paint, as /fillbiome does: the
        // section swaps its container only once it is full. The sampler is never consulted, which
        // is what lets this run headless, where there is no server to ask for one.
        chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> {
            Holder<Biome> painted = cellBiome(paints, qx, qy, qz);
            return painted != null ? painted : chunk.getNoiseBiome(qx, qy, qz);
        }, null);
        chunk.setUnsaved(true);
        // Placed into a chunk the client already has (/d2-generate, /place): it will not see the new
        // biomes until told. Ordinary generation writes a ProtoChunk that is sent later anyway.
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
        }
    }

    /**
     * Which boxes get which biome in one chunk, in paint order (a later entry wins a shared cell).
     *
     * <p>A piece with a floor takes that floor's biome. A piece without one (a pool element) is split
     * by height: each floor owns the band from its lowest D2 box up to the next floor's, the deepest
     * floor runs downward without limit, and nothing above the highest D2 box is painted at all
     * &mdash; which is what keeps the surface entrance and its shaft on the ground's own biome with
     * no list of entrance pools to maintain. A start with no D2 pieces paints nothing.
     *
     * <p>A pool piece whose template has a biome of its own paints its whole box with it, under the
     * same cap. A piece with {@code parts} paints those instead of its box.
     *
     * @param biomeFor a floor's biome, {@code MotifConfig#biomeFor}
     * @param biomeForTemplate a template's own biome, {@code MotifConfig#biomeForTemplate}
     */
    public static <T> List<Paint<T>> plan(List<PieceBox> pieces, BoundingBox chunkBox,
                                          IntFunction<Optional<T>> biomeFor,
                                          Function<String, Optional<T>> biomeForTemplate) {
        // floor index -> lowest Y of any of its boxes; and the top of the dungeon's own pieces
        Map<Integer, Integer> floorBottoms = new TreeMap<>();
        int top = Integer.MIN_VALUE;
        for (PieceBox piece : pieces) {
            if (piece.floorIndex().isPresent()) {
                floorBottoms.merge(piece.floorIndex().getAsInt(), piece.box().minY(), Math::min);
                top = Math.max(top, piece.box().maxY());
            }
        }
        if (floorBottoms.isEmpty()) {
            return List.of();
        }
        // Highest band first: [bottom, ceiling], each floor's ceiling the floor above's bottom - 1.
        List<Map.Entry<Integer, Integer>> byHeight = new ArrayList<>(floorBottoms.entrySet());
        byHeight.sort(Map.Entry.<Integer, Integer>comparingByValue(Comparator.reverseOrder()));

        List<Paint<T>> out = new ArrayList<>();
        for (PieceBox piece : pieces) {
            BoundingBox box = piece.box();
            if (!box.intersects(chunkBox)) {
                continue;
            }
            if (piece.floorIndex().isPresent()) {
                Optional<T> biome = biomeFor.apply(piece.floorIndex().getAsInt());
                if (biome.isPresent()) {
                    for (BoundingBox part : piece.parts().isEmpty() ? List.of(box) : piece.parts()) {
                        clip(part, chunkBox, Integer.MIN_VALUE, Integer.MAX_VALUE)
                                .ifPresent(clipped -> out.add(new Paint<>(clipped, biome.get())));
                    }
                }
                continue;
            }
            Optional<T> own = piece.templateId().flatMap(biomeForTemplate);
            if (own.isPresent()) {
                clip(box, chunkBox, Integer.MIN_VALUE, top)
                        .ifPresent(clipped -> out.add(new Paint<>(clipped, own.get())));
                continue;
            }
            int ceiling = top;
            for (int i = 0; i < byHeight.size(); i++) {
                int floorIndex = byHeight.get(i).getKey();
                int bottom = i == byHeight.size() - 1 ? Integer.MIN_VALUE : byHeight.get(i).getValue();
                int bandTop = ceiling;
                biomeFor.apply(floorIndex).ifPresent(biome ->
                        clip(box, chunkBox, bottom, bandTop)
                                .ifPresent(clipped -> out.add(new Paint<>(clipped, biome))));
                ceiling = byHeight.get(i).getValue() - 1;
            }
        }
        return out;
    }

    /**
     * The biome of the last paint whose box overlaps quart cell {@code (qx, qy, qz)}, or null.
     * Coordinates are absolute quart positions, as a {@code BiomeResolver} is handed them.
     */
    public static <T> T cellBiome(List<Paint<T>> paints, int qx, int qy, int qz) {
        int x = QuartPos.toBlock(qx);
        int y = QuartPos.toBlock(qy);
        int z = QuartPos.toBlock(qz);
        for (int i = paints.size() - 1; i >= 0; i--) {
            BoundingBox box = paints.get(i).box();
            if (box.minX() <= x + 3 && box.maxX() >= x
                    && box.minY() <= y + 3 && box.maxY() >= y
                    && box.minZ() <= z + 3 && box.maxZ() >= z) {
                return paints.get(i).biome();
            }
        }
        return null;
    }

    /** {@code box} clipped to the chunk's X/Z and to {@code [minY, maxY]}, or empty if nothing is left. */
    private static Optional<BoundingBox> clip(BoundingBox box, BoundingBox chunkBox, int minY, int maxY) {
        int x0 = Math.max(box.minX(), chunkBox.minX());
        int x1 = Math.min(box.maxX(), chunkBox.maxX());
        int y0 = Math.max(box.minY(), minY);
        int y1 = Math.min(box.maxY(), maxY);
        int z0 = Math.max(box.minZ(), chunkBox.minZ());
        int z1 = Math.min(box.maxZ(), chunkBox.maxZ());
        if (x0 > x1 || y0 > y1 || z0 > z1) {
            return Optional.empty();
        }
        return Optional.of(new BoundingBox(x0, y0, z0, x1, y1, z1));
    }
}
