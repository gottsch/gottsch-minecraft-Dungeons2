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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit;

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A square sunken court in the middle of the room, {@code size} cells on a side, terraced.
 *
 * <p><strong>It never touches the interior's edge</strong>: a size that would reach the wall ring
 * is shrunk until at least one walkable cell survives on every side, and a size that cannot fit at
 * all yields no court. A court flush against a wall is a different feature (you step into it
 * leaving the door) and would want its own provider rather than being what this degrades into.</p>
 */
public class CentrePitShapeProvider implements IPitShapeProvider {

    private final int size;
    private final int depth;
    private final String rimBlock;
    private final SurfaceOrient rimOrient;

    public CentrePitShapeProvider(int size, int depth) {
        this(size, depth, null, SurfaceOrient.OUTWARD);
    }

    public CentrePitShapeProvider(int size, int depth, String rimBlock, SurfaceOrient rimOrient) {
        this.size = size;
        this.depth = depth;
        this.rimBlock = rimBlock;
        this.rimOrient = rimOrient;
    }

    @Override
    public PitPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        // Leave a walkable ring: the widest court an interior can hold is two cells short of it.
        int fit = Math.min(size, Math.min(interiorWidth - 2, interiorDepth - 2));
        if (fit < 1) {
            return PitPlan.empty();
        }
        Set<Coords2D> footprint = new HashSet<>();
        int startX = (interiorWidth - fit) / 2;
        int startZ = (interiorDepth - fit) / 2;
        for (int x = 0; x < fit; x++) {
            for (int z = 0; z < fit; z++) {
                footprint.add(new Coords2D(startX + x, startZ + z));
            }
        }
        if (rimBlock == null || rimBlock.isEmpty()) {
            return new PitPlan(PitPlans.terraced(footprint, depth));
        }
        return new PitPlan(PitPlans.terraced(footprint, depth), Map.of(),
                PitPlans.stairRim(footprint,
                        BlockStateCodec.block(rimBlock, Blocks.COBBLESTONE_STAIRS), rimOrient));
    }
}
