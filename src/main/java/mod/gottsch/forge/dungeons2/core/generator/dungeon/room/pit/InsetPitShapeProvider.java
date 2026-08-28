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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.Set;

/**
 * The interior sunk but for a walkway {@code inset} cells wide around it &mdash; a sunken court
 * with a ledge, terraced inward.
 *
 * <p>The inset starts at <strong>1</strong>, not 0: an inset of 0 sinks the cells in front of the
 * doorways too, and while the terracing means a player could still walk out, a doorway opening onto
 * a step down is a threshold nobody authored. Nothing else about the pit slot knows where the doors
 * are, so the walkway is what keeps them ordinary.</p>
 */
public class InsetPitShapeProvider implements IPitShapeProvider {

    private final int inset;
    private final int depth;

    public InsetPitShapeProvider(int inset, int depth) {
        this.inset = inset;
        this.depth = depth;
    }

    @Override
    public PitPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        int width = interiorWidth - 2 * inset;
        int courtDepth = interiorDepth - 2 * inset;
        if (width < 1 || courtDepth < 1) {
            return PitPlan.empty();
        }
        Set<Coords2D> footprint = new HashSet<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < courtDepth; z++) {
                footprint.add(new Coords2D(inset + x, inset + z));
            }
        }
        return new PitPlan(PitPlans.terraced(footprint, depth));
    }
}
