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

import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.Map;

/**
 * {@code hazard}'s spiked shaft, lidded with a crumbling copy of the floor: the pit you do not see
 * until you are in it.
 *
 * <p>The shaft is exactly the hazard's &mdash; same footprint, same sheer sides, same spike draw
 * &mdash; so it is planned by a {@link HazardPitShapeProvider} with no rim. A rim is the hazard's
 * TELL, and a false floor is the one pit that must not have one. Every excavated cell is then
 * marked {@link PitPlan#falseFloor}, and {@code RoomPitGenerator} lids it with the crumbling version
 * of whatever the floor laid there.</p>
 *
 * <p>The collapse is DungeonBlocks': anything living that steps on the lid sets it off, and it
 * spreads through every crumbling block touching it, so the whole lid drops at once. A rat will
 * spring it as readily as a player; Mark, 2026-09-28: "if they trigger it, they trigger it."</p>
 */
public class FalseFloorPitShapeProvider implements IPitShapeProvider {

    private final HazardPitShapeProvider shaft;

    public FalseFloorPitShapeProvider(int width, int depth, int offsetX, int offsetZ,
                                      String spikeBlock, Map<String, String> spikeProperties,
                                      double spikeProbability) {
        this.shaft = new HazardPitShapeProvider(width, depth, offsetX, offsetZ, spikeBlock,
                spikeProperties, spikeProbability, null);
    }

    @Override
    public PitPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        PitPlan plan = shaft.plan(interiorWidth, interiorDepth, random);
        if (plan.isEmpty()) {
            return plan;
        }
        return new PitPlan(plan.depths(), plan.fills(), plan.rim(), plan.fillData(), plan.flood(),
                plan.cover(), new HashSet<>(plan.depths().keySet()));
    }
}
