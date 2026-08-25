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
import java.util.Optional;

/**
 * Procedural piece wrapping one {@link CorridorData} region. Renders with the
 * grid-free {@link BasicCorridorGenerator} overload, reading wall columns from
 * {@link CorridorData#getWallCells()} (folded in by the planner) rather than the
 * transient maze grid &mdash; so the piece round-trips through NBT without it.
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public class DungeonCorridorPiece extends DungeonPiece {

    private CorridorData corridor;

    public DungeonCorridorPiece(CorridorData corridor, String motifValue, int floorY, int floorIndex,
                            int anchorX, int anchorZ) {
        super(StructurePieces.CORRIDOR, motifValue, floorY, floorIndex, anchorX, anchorZ,
                computeBox(corridor, floorY, anchorX, anchorZ));
        this.corridor = corridor;
    }

    public DungeonCorridorPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(StructurePieces.CORRIDOR, tag);
        this.corridor = PieceNbt.readCorridor(tag.getCompound("Corridor"));
    }

    /**
     * World bounding box: covers every corridor, wall and door cell;
     * Y = {@code floorY .. floorY + wallHeight - 1}.
     *
     * <p>The box and what {@link BasicCorridorGenerator} emits must agree exactly &mdash; a block
     * outside the piece's box is silently clipped by vanilla &mdash; so both read the height off
     * the same {@link CorridorData#getWallHeight()}, which the planner resolved from the motif.</p>
     */
    private static BoundingBox computeBox(CorridorData corridor, int floorY, int anchorX, int anchorZ) {
        int top = floorY + corridor.getWallHeight() - 1;
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
            return new BoundingBox(anchorX, floorY, anchorZ, anchorX, top, anchorZ);
        }
        return new BoundingBox(
                anchorX + minX, floorY, anchorZ + minZ,
                anchorX + maxX, top, anchorZ + maxZ);
    }

    private static List<Coords2D> allCells(CorridorData corridor) {
        List<Coords2D> all = new ArrayList<>(corridor.getCells().size()
                + corridor.getWallCells().size() + corridor.getDoorCells().size());
        all.addAll(corridor.getCells());
        all.addAll(corridor.getWallCells());
        all.addAll(corridor.getDoorCells());
        return all;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.put("Corridor", PieceNbt.writeCorridor(corridor));
    }

    /**
     * A corridor derives every corner shape it places, so vanilla never gets a vote.
     *
     * <p>Two independent reasons, and either alone would be enough.</p>
     *
     * <p><strong>Vanilla cannot get an arch right.</strong> {@code StairBlock.getStairsShape} looks
     * for a stair at {@code pos.relative(facing)} to decide OUTER; a haunch faces <em>into its
     * wall</em>, so that lookup always finds solid stone and the OUTER branch can never fire. Its
     * INNER branch meanwhile fires on a perpendicular haunch across a narrow corridor, overriding a
     * {@code straight} the generator chose deliberately. {@code BasicCorridorGenerator.haunchShape}
     * derives the shape from the wall layout, which is information vanilla does not have.</p>
     *
     * <p><strong>And its answer is not chunk-stable.</strong> See
     * {@link DungeonPiece#settlesJoinShapes()} &mdash; settling reads neighbours, {@code postProcess}
     * runs per chunk, and a cell on a boundary settles against whatever the far side happens to hold
     * at that moment. That produced a visible seam at multiples of 16.</p>
     *
     * <p>This covers the whole piece, not just haunches, so a datapack's corridor <em>course</em> of
     * stairs is not mitred either. That is deliberate and consistent: corner rules assume a
     * rectangle with four runs, and a corridor wall winds &mdash; the same reason
     * {@code cornerBlock} is rejected on a corridor course.</p>
     */
    @Override
    protected boolean settlesJoinShapes() {
        return false;
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
        // Render from a piece-stable seed, not the chunk-seeded `random`.
        safePlaceAll(level, box, stratum, () -> renderPlacements(motifConfig));
    }

    /** Builds this corridor's placements deterministically (no external RNG), motif defaults. */
    public List<BlockPlacement> renderPlacements() {
        return renderPlacements(MotifConfig.DEFAULT);
    }

    /** Builds this corridor's placements deterministically (no external RNG). */
    public List<BlockPlacement> renderPlacements(MotifConfig motifConfig) {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(motifConfig)
                .build(corridor, floorY, motif(), deterministicRandom(corridor.getId()), out);
        return out;
    }

    public CorridorData getCorridor() {
        return corridor;
    }
}
