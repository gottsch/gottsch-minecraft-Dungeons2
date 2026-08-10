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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Four columns marking a square at the room's centre &mdash; a baldachin, a canopy over whatever the
 * middle of the room holds.
 *
 * <h2>How this differs from a grid, and how it doesn't</h2>
 * <p>Structurally this is <strong>a grid capped at two columns per axis</strong>, and it is worth
 * being honest that in a room small enough for the grid to produce exactly two per axis anyway, the
 * two layouts converge. Measured against real planner output they land on the same footprint about
 * one room in eight at the shipped settings.</p>
 *
 * <p>What keeps it a distinct layout is that its square <strong>does not grow with the room</strong>.
 * A grid tiles whatever space it is given, so a big room gets more columns; a quartet always marks
 * one centre, so a big room gets the same four columns with more space around them. That is the
 * difference between a field of columns and a canopy over something, and it is why the two read
 * differently in exactly the rooms where a room needs to read differently.</p>
 *
 * <p>The lever that separates them is {@code spacing}, not a size gate: authored wider than the
 * grid's, the square sits outside the lattice the grid would have drawn. Gating this to the largest
 * rooms also removes the overlap, but at the shipped room distribution it removes almost all of the
 * feature with it &mdash; roughly 1 room in 100 rather than 1 in 4.</p>
 *
 * <h2>It is a square, not a rectangle</h2>
 * <p>The half-side is shrunk to whatever <em>both</em> axes can carry, so a long thin room gets a
 * square in the middle rather than a rectangle stretched to fit. Four columns marking a square is
 * the whole concept; a rectangle that happens to have four corners is just a sparse grid, which is
 * the layout this already risks collapsing into.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public record QuartetPillarPatternProvider(int spacing, int inset) implements IPillarPatternProvider {

    @Override
    public Set<Coords2D> footprint(int interiorWidth, int interiorDepth) {
        int half = Math.min(spacing / 2, Math.min(maxHalf(interiorWidth), maxHalf(interiorDepth)));
        if (half < 1) {
            return Set.of();
        }
        int centreX = (interiorWidth - 1) / 2;
        int centreZ = (interiorDepth - 1) / 2;

        Set<Coords2D> cells = new LinkedHashSet<>(4);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                cells.add(new Coords2D(centreX + dx * half, centreZ + dz * half));
            }
        }
        return cells;
    }

    /**
     * The largest half-side this axis can carry without breaking the {@code inset} clearance.
     *
     * <p>Shrinking rather than declining is deliberate: a room too small for the authored square
     * still has a centre worth marking, and declining would hand the commonest room size back to the
     * grid's lone central column &mdash; which is the weakest output the pillar system has.</p>
     */
    private int maxHalf(int length) {
        int centre = (length - 1) / 2;
        return Math.min(centre - inset, (length - 1 - inset) - centre);
    }
}
