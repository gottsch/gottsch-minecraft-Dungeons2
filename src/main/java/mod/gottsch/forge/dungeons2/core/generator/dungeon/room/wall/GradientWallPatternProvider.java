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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Two materials mixed with a vertical bias &mdash; one dominating the bottom of the wall and giving
 * way to the other as the wall rises.
 *
 * <h2>What it is for</h2>
 * <p>The mud stratum reads as a dungeon the ground is reclaiming. That is not a band of one material
 * under a band of another, which {@code courses} already draws and which reads as deliberate
 * masonry: it is a wall whose lower courses have been swallowed and whose upper ones have not, with
 * no line anywhere you could point at. A probability that falls with height gives exactly that,
 * because the boundary is different in every column.</p>
 *
 * <h2>The first FILL pattern, and why that is not a problem</h2>
 * <p>Every other wall pattern is sparse: it marks the cells it cares about and leaves the rest for
 * the surface's base block. This one marks <strong>every</strong> cell, which is the point &mdash;
 * it is the wall's material, not a treatment applied over one. Composition still works the way
 * {@code ISurfacePatternProvider} describes, and in the author's favour: list this first in a
 * scheme's {@code patterns} and the pilasters, courses and panels after it all overlay it, because
 * a later non-null cell wins. Listing it last would erase them.</p>
 *
 * <h2>Why a hold rather than a plain ramp</h2>
 * <p>A bare linear ramp from 1 to 0 starts falling at the second row, so "mostly mud at the bottom"
 * is only ever true of one row. {@link #holdRows} keeps the bottom of the wall at its full bias
 * before the ramp begins, which is what makes a visible base course of the bottom material rather
 * than a gradient that starts immediately. Zero gives the plain ramp back.</p>
 *
 * <h2>Randomness</h2>
 * <p>Drawn from the room's own {@link RandomSource}, like a course's {@code alternate} mix. It is
 * therefore NOT a pure function of {@code (u, v)} &mdash; deliberately, since a wall computed purely
 * from its coordinates would come out identically speckled in every room in the dungeon. Each of a
 * room's four walls draws separately and so gets its own scatter, which is right: a shared pattern
 * would line up across the corners and read as tiling.</p>
 *
 * @author Mark Gottschling on Aug 29, 2026
 */
public class GradientWallPatternProvider implements ISurfacePatternProvider {

    private final BlockState bottom;
    private final BlockState top;
    private final double bottomProbability;
    private final double topProbability;
    private final int holdRows;

    public GradientWallPatternProvider(BlockState bottom, BlockState top, double bottomProbability,
                                       double topProbability, int holdRows) {
        this.bottom = Objects.requireNonNull(bottom, "bottom");
        this.top = Objects.requireNonNull(top, "top");
        this.bottomProbability = bottomProbability;
        this.topProbability = topProbability;
        this.holdRows = Math.max(0, holdRows);
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        for (int v = 0; v < vSize; v++) {
            double probability = probabilityAt(v, vSize);
            for (int u = 0; u < uSize; u++) {
                plan.set(u, v, random.nextDouble() < probability ? bottom : top);
            }
        }
        return plan;
    }

    /**
     * The chance of the BOTTOM material on row {@code v}, where {@code v} 0 is the wall's lowest row
     * &mdash; the same orientation {@code CoursesWallPatternProvider.rowFor} anchors against.
     *
     * <p>Package-visible and pure so the ramp can be tested directly. A wall is only 3 to 8 rows
     * tall, so an off-by-one in here is a third of the gradient and would be very hard to see in
     * game against the scatter it produces.</p>
     */
    double probabilityAt(int v, int vSize) {
        if (v < holdRows) {
            return bottomProbability;
        }
        // The ramp spans from the first row after the hold to the TOP row inclusive, so the top row
        // lands exactly on topProbability rather than one step short of it.
        int span = vSize - 1 - holdRows;
        if (span <= 0) {
            // The hold ate the whole wall -- every row keeps the full bias. Correct rather than
            // degenerate: a 3-row wall with holdRows 3 was authored as "all bottom material", and a
            // room too short for the gradient should not silently invert it.
            return bottomProbability;
        }
        double t = (double) (v - holdRows) / span;
        return bottomProbability + t * (topProbability - bottomProbability);
    }
}
