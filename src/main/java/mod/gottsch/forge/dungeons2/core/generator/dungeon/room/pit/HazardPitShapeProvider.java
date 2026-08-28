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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A sheer-sided shaft with spikes at the bottom &mdash; a trap rather than a room feature.
 *
 * <h2>What makes it a hazard is the SIDES, not the spikes</h2>
 * <p>A player can only jump onto a block one high, so any sheer pit two or more deep is something
 * they fall into and cannot climb out of. That is the whole mechanism; the spikes are what turn a
 * nuisance into a threat. This is exactly the profile the terraced court providers avoid, which is
 * why it is its own provider and not a flag &mdash; an author placing one should have had to name
 * it.</p>
 *
 * <h2>Spikes point UP</h2>
 * <p>Minecraft has one block for both ends of a dripstone: {@code pointed_dripstone} with
 * {@code vertical_direction=up} is a stalagmite and {@code down} is a stalactite. <strong>Up is
 * also the one that does the damage</strong> &mdash; landing on an upward tip multiplies fall
 * damage, so a shaft floored with downward ones is decorative and harmless. Nothing here enforces
 * the property; {@code spikeProperties} is authored, because a pack may want a different block
 * entirely and the codec cannot know what its states mean.</p>
 *
 * <p>{@code offsetX}/{@code offsetZ} shift the shaft off centre. A trap in the exact middle of
 * every room announces itself; one to the side of the doorway line does not.</p>
 */
public class HazardPitShapeProvider implements IPitShapeProvider {

    private final int width;
    private final int depth;
    private final int offsetX;
    private final int offsetZ;
    private final String spikeBlock;
    private final Map<String, String> spikeProperties;
    private final double spikeProbability;

    public HazardPitShapeProvider(int width, int depth, int offsetX, int offsetZ,
                                  String spikeBlock, Map<String, String> spikeProperties,
                                  double spikeProbability) {
        this.width = width;
        this.depth = depth;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.spikeBlock = spikeBlock;
        this.spikeProperties = spikeProperties;
        this.spikeProbability = spikeProbability;
    }

    @Override
    public PitPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        // Same walkable-ring rule as the court: a trap you cannot walk PAST is a blocked room, not
        // a trap. It is the reason to fall in that differs, not the room's usability around it.
        int fit = Math.min(width, Math.min(interiorWidth - 2, interiorDepth - 2));
        if (fit < 1) {
            return PitPlan.empty();
        }
        int startX = clamp((interiorWidth - fit) / 2 + offsetX, interiorWidth, fit);
        int startZ = clamp((interiorDepth - fit) / 2 + offsetZ, interiorDepth, fit);

        Set<Coords2D> footprint = new HashSet<>();
        for (int x = 0; x < fit; x++) {
            for (int z = 0; z < fit; z++) {
                footprint.add(new Coords2D(startX + x, startZ + z));
            }
        }
        Map<Coords2D, Integer> depths = PitPlans.sheer(footprint, depth);

        Map<Coords2D, BlockState> fills = new HashMap<>();
        if (spikeBlock != null && !spikeBlock.isEmpty() && spikeProbability > 0) {
            BlockState spike = BlockStateCodec.withProperties(
                    BlockStateCodec.block(spikeBlock, Blocks.POINTED_DRIPSTONE), spikeProperties);
            // Sorted, so the draw order is a function of the plan rather than of HashSet iteration
            // -- the same determinism every other generator here is careful about.
            footprint.stream()
                    .sorted((a, b) -> a.getX() != b.getX()
                            ? Integer.compare(a.getX(), b.getX())
                            : Integer.compare(a.getY(), b.getY()))
                    .forEach(cell -> {
                        if (random.nextDouble() < spikeProbability) {
                            fills.put(cell, spike);
                        }
                    });
        }
        return new PitPlan(depths, fills);
    }

    /** Keeps the offset shaft inside the interior's walkable ring rather than under a wall. */
    private static int clamp(int start, int interior, int fit) {
        return Math.max(1, Math.min(start, interior - fit - 1));
    }
}
