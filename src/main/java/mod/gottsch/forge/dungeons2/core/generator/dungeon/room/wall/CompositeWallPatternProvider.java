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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Draws a wall slot's patterns in order, each over the last.
 *
 * <p>There is deliberately no {@code "composite"} <em>type</em> for an author to write: the wall
 * slot is already an ordered list, and surface plans are sparse, so composition is just
 * {@link SurfacePlan#overlay} applied in sequence. This class is the mechanical consequence of that,
 * not a feature &mdash; it exists because {@code BasicWallGenerator} holds one provider, and the
 * same reasoning is spelled out on {@code CeilingPatternEntry}.</p>
 *
 * <h2>Ordering is the author's answer to "which one wins"</h2>
 * <p>Later patterns overwrite earlier ones cell by cell, so a pilaster listed after a course
 * interrupts the band where the two cross, and listed before it is crossed by the band. Both are
 * reasonable looks and neither is a default worth guessing at, which is exactly why the list is
 * ordered rather than a set.</p>
 *
 * <h2>Projected layers merge per depth</h2>
 * <p>Two patterns can project to the <em>same</em> depth &mdash; a cornice at depth 1 and pilasters
 * at depth 1 are the ordinary case, since both stand one cell off the wall. Their plans are overlaid
 * into one plan per depth, in list order, so the same "later wins" rule holds there too. Handing
 * {@code WallSurface} two separate plans at one depth would instead make the winner depend on map
 * iteration order.</p>
 *
 * @author Mark Gottschling on Aug 5, 2026
 */
public class CompositeWallPatternProvider implements ISurfacePatternProvider, IProjectingPatternProvider {

    private final List<ISurfacePatternProvider> providers;

    public CompositeWallPatternProvider(List<ISurfacePatternProvider> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    /**
     * The composed providers, in draw order &mdash; earlier ones are overlaid by later ones, so the
     * last to claim a cell is the one that draws there. Exposed so a test can assert the ORDER,
     * which is what decides who wins where a band's pattern and a scheme's meet.
     */
    public List<ISurfacePatternProvider> providers() {
        return providers;
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan combined = SurfacePlan.of(uSize, vSize);
        for (ISurfacePatternProvider provider : providers) {
            combined.overlay(provider.plan(uSize, vSize, facing, random));
        }
        return combined;
    }

    @Override
    public Map<Integer, SurfacePlan> projectedPlans(int uSize, int vSize, Direction facing,
                                                    RandomSource random) {
        Map<Integer, SurfacePlan> merged = new LinkedHashMap<>();
        for (ISurfacePatternProvider provider : providers) {
            if (!(provider instanceof IProjectingPatternProvider projecting)) {
                continue;
            }
            for (Map.Entry<Integer, SurfacePlan> layer
                    : projecting.projectedPlans(uSize, vSize, facing, random).entrySet()) {
                merged.computeIfAbsent(layer.getKey(), depth -> SurfacePlan.of(uSize, vSize))
                        .overlay(layer.getValue());
            }
        }
        return merged;
    }
}
