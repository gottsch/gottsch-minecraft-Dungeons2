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
package mod.gottsch.forge.dungeons2.core.world;

import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import java.util.Optional;

/**
 * Finds the nearest dungeon entrance to a position, by vanilla's own ring search over the structure
 * set's placement. Vanilla's {@code findNearestMapStructure} answers the start chunk's CORNER; a D2
 * entrance is assembled on the chunk's MIDDLE, and at the entrance that difference swings a needle.
 *
 * <p>Works from the structure set's PLACEMENT, not from chunk references, so it sees dungeons that
 * have not generated yet and is not blind past eight chunks of a start the way
 * {@code getStructureAt} is. A candidate whose biome refused the dungeon is checked by generating
 * its chunk to {@code STRUCTURE_STARTS}, as vanilla's own locate does.</p>
 *
 * @author Mark Gottschling on 10/26/2026
 */
public final class DungeonLocator {

    public static final ResourceKey<StructureSet> DUNGEONS =
            ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(Dungeons.MOD_ID, "dungeons"));

    private DungeonLocator() {}

    /**
     * The entrance (start chunk's middle, at the searcher's Y) of the nearest dungeon to
     * {@code from}, searching {@code maxRings} placement regions out.
     */
    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos from, int maxRings) {
        Optional<StructureSet> set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .getOptional(DUNGEONS);
        if (set.isEmpty() || !(set.get().placement() instanceof RandomSpreadStructurePlacement placement)) {
            return Optional.empty();
        }
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        StructureManager structures = level.structureManager();
        long seed = state.getLevelSeed();
        int spacing = placement.spacing();
        int cx = SectionPos.blockToSectionCoord(from.getX());
        int cz = SectionPos.blockToSectionCoord(from.getZ());

        for (int ring = 0; ring <= maxRings; ring++) {
            BlockPos best = null;
            long bestDistance = Long.MAX_VALUE;
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue; // inner regions were searched by an earlier ring
                    }
                    ChunkPos candidate = placement.getPotentialStructureChunk(seed, cx + spacing * i, cz + spacing * j);
                    if (!placement.isStructureChunk(state, candidate.x, candidate.z)) {
                        continue; // frequency or exclusion zone said no
                    }
                    // the entrance is assembled on the start chunk's middle (DungeonStructure
                    // #findGenerationPoint), not on the corner vanilla's getLocatePos answers
                    BlockPos at = new BlockPos(candidate.getMiddleBlockX(), from.getY(), candidate.getMiddleBlockZ());
                    long dx = at.getX() - from.getX();
                    long dz = at.getZ() - from.getZ();
                    long distance = dx * dx + dz * dz;
                    if (distance >= bestDistance || !generates(level, structures, set.get(), candidate)) {
                        continue;
                    }
                    best = at;
                    bestDistance = distance;
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    private static boolean generates(ServerLevel level, StructureManager structures, StructureSet set, ChunkPos chunk) {
        for (StructureSet.StructureSelectionEntry entry : set.structures()) {
            StructureCheckResult result = structures.checkStructurePresence(chunk, entry.structure().value(), false);
            if (result == StructureCheckResult.START_PRESENT) {
                return true;
            }
            if (result == StructureCheckResult.CHUNK_LOAD_NEEDED) {
                ChunkAccess access = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS);
                StructureStart start = structures.getStartForStructure(SectionPos.bottomOf(access),
                        entry.structure().value(), access);
                if (start != null && start.isValid()) {
                    return true;
                }
            }
        }
        return false;
    }
}
