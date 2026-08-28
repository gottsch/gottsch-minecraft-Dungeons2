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

import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfigHelper;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigHelper;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
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
import java.util.Optional;

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

    public DungeonRoomPiece(RoomData room, String motifValue, int floorY, int floorIndex,
                            int anchorX, int anchorZ) {
        this(room, motifValue, floorY, floorIndex, anchorX, anchorZ, 0);
    }

    /**
     * @param sinkOffset the floor's budget BELOW its walking plane (#29), which the box has to
     *                   cover whether or not this room's scheme digs a pit into it &mdash; see
     *                   {@link #computeBox}
     */
    public DungeonRoomPiece(RoomData room, String motifValue, int floorY, int floorIndex,
                            int anchorX, int anchorZ, int sinkOffset) {
        super(StructurePieces.ROOM, motifValue, floorY, floorIndex, anchorX, anchorZ,
                computeBox(room, floorY, anchorX, anchorZ, sinkOffset));
        this.room = room;
    }

    public DungeonRoomPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieces.ROOM, tag);
        this.room = PieceNbt.readRoom(tag.getCompound("Room"));
    }

    /**
     * World bounding box: the room footprint with a 1-cell XZ margin for walls, and Y from the
     * floor plane up through the room's height &mdash; extended DOWNWARD by {@code sinkOffset}.
     *
     * <h2>Why the sink is unconditional and the pit's actual depth is not consulted</h2>
     * <p>The box is computed at CONSTRUCTION, and a room's {@code pit} slot is not known then: the
     * scheme is rolled at render time, per piece, from a piece-stable seed. So the box cannot be
     * sized to the pit &mdash; it is sized to the floor's <em>budget</em> below its walking plane,
     * which is a property of the floor and is the same for every room on it.</p>
     *
     * <p>That is the right bound anyway. {@code sinkOffset} is exactly how far a pit may ever be
     * dug ({@code PitPatternEntry#depthWithin} clamps to it), so a box covering the budget covers
     * every pit that could ever be authored into this room, and a pit can never fall outside its
     * own piece. With the shipped {@code sinkOffset} of 0 the box is what it always was.</p>
     *
     * <p>It matters beyond block writes: the box is what {@code spawn_overrides} with
     * {@code bounding_box: piece} tests against, so a mob standing at the bottom of a pit needs the
     * pit inside it to count as being in the dungeon at all.</p>
     */
    private static BoundingBox computeBox(RoomData room, int floorY, int anchorX, int anchorZ,
                                          int sinkOffset) {
        int minX = anchorX + room.getOriginX() - 1;
        int minZ = anchorZ + room.getOriginZ() - 1;
        int maxX = anchorX + room.getOriginX() + room.getWidth();
        int maxZ = anchorZ + room.getOriginZ() + room.getDepth();
        int minY = floorY - Math.max(0, sinkOffset);
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
        // #45: the motif AS BUILT ON THIS FLOOR. A motif with no strata hands back itself,
        // so this is a no-op for everything shipped today. Build time, not plan time -- both
        // inputs are in hand right here and nothing needs serialising.
        MotifConfig motif = MotifConfigHelper.get(level.registryAccess(), motifValue);
        // #45 step 4: asked of the UNPROJECTED motif -- forFloor clears the strata table, so a
        // projection has no band left to name. Same reason DungeonStructure asks it of the motif.
        Optional<String> stratum = motif.stratumNameFor(floorIndex);
        MotifConfig motifConfig = motif.forFloor(floorIndex);
        // #3: how far this floor was sunk below its walking plane, which is the hard cap on any
        // pit a scheme may dig. Read here rather than serialised -- it is a datapack value and a
        // reload should move it.
        int sinkOffset = DungeonGenerationConfigHelper.get(level.registryAccess()).sinkOffset();
        // Render from a piece-stable seed, not the chunk-seeded `random` (see
        // DungeonPiece#deterministicRandom) so the result is identical in every chunk.
        RoomPlacements placements = renderRoom(motifConfig, sinkOffset);
        safePlaceAll(level, box, stratum, placements::getBlocks);
        // Entities are spawned separately and clipped to the chunk box -- unlike blocks they are
        // not idempotent across the per-chunk postProcess re-runs. See DungeonPiece#placeEntities.
        placeEntities(level, box, placements.getEntities());
    }

    /** Builds this room's block placements deterministically (no external RNG), always plain floor. */
    public List<BlockPlacement> renderPlacements() {
        return renderPlacements(MotifConfig.DEFAULT);
    }

    /** Builds this room's block placements deterministically (no external RNG). */
    public List<BlockPlacement> renderPlacements(MotifConfig motifConfig) {
        return renderRoom(motifConfig).getBlocks();
    }

    /**
     * Builds this room's full output -- blocks and entities -- deterministically. Seeded from
     * chunk-independent piece state, so every per-chunk re-run produces an identical plan; the
     * entity half depends on that far more than the block half does (see
     * {@code DungeonPiece#placeEntities}).
     */
    public RoomPlacements renderRoom(MotifConfig motifConfig) {
        return renderRoom(motifConfig, 0);
    }

    /** As above, on a floor sunk {@code sinkOffset} below its walking plane (#3/#29). */
    public RoomPlacements renderRoom(MotifConfig motifConfig, int sinkOffset) {
        RoomPlacements out = new RoomPlacements();
        new BasicRoomGenerator().withMotifConfig(motifConfig).withSinkOffset(sinkOffset)
                .build(room, floorY, floorIndex, motif(), deterministicRandom(room.getId()), out);
        return out;
    }

    /**
     * The scheme this room rolls &mdash; the very same roll {@link #renderRoom} makes, off the same
     * piece-stable seed, so it is exact rather than an estimate.
     *
     * <p>Diagnostics only (the floor-plan viewer labels rooms with it). Nothing in the render path
     * calls this; {@code BasicRoomGenerator} rolls its own from the random it is handed.</p>
     */
    public RoomScheme rolledScheme(MotifConfig motifConfig) {
        return new BasicRoomGenerator().withMotifConfig(motifConfig)
                .selectScheme(room, floorIndex, deterministicRandom(room.getId()));
    }

    public RoomData getRoom() {
        return room;
    }
}
