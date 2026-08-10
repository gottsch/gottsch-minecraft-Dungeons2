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
 * Two rows of columns running the length of the room, leaving a clear aisle down the middle &mdash;
 * a basilica, a great hall.
 *
 * <h2>The one thing no other layout has: an axis</h2>
 * <p>{@link GridPillarPatternProvider} is symmetric in X and Z, so it treats a room as a field. A
 * colonnade is <strong>directional</strong>: it runs along the room's <em>longer</em> axis and puts
 * its two rows across the shorter one, which is what gives the room a nave to walk down instead of a
 * lattice to pick through. That makes it the layout that actually reads differently in a long thin
 * room, where a grid just looks like a grid that ran out of space.</p>
 *
 * <p>The axis is chosen from the room's proportions and <strong>never from the RNG</strong>: a
 * square room always picks X. Rooms render once per overlapping chunk and every run must agree, so
 * a coin-flip here would give a room a different colonnade per chunk and tear it along the seam
 * &mdash; the same class of fault the chunk-seam arch settling turned out to be.</p>
 *
 * <p>Because the axis is the point, this layout <strong>declines a room that is not elongated</strong>
 * &mdash; see {@link #minimumRun}. Note that means a scheme whose only pillar pattern is a colonnade
 * draws no columns at all in a square room. Listing a {@code grid} beside it does <em>not</em> give
 * a fallback: the patterns list composes rather than choosing, so both would draw wherever both
 * fit. A colonnade scheme is a scheme for long rooms, and a room it declines is simply a room
 * without columns.</p>
 *
 * <h2>What {@code inset} and {@code spacing} mean here</h2>
 * <p>{@code inset} is the gap between a row and the wall it runs beside, and it is also the margin
 * at each end of the run &mdash; one knob, the same meaning it has on the grid ("how far in from the
 * edge"). {@code spacing} is the stride <em>along</em> the colonnade, which is the only axis it
 * applies to.</p>
 *
 * <p>The run itself reuses {@link GridPillarPatternProvider#positions}, so it inherits the centring
 * guarantee rather than restating the arithmetic that has already been got wrong once in this
 * package.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public record ColonnadePillarPatternProvider(int spacing, int inset) implements IPillarPatternProvider {

    /**
     * The narrowest cross-axis that can carry a colonnade: a row at {@code inset} from each side and
     * <strong>at least one clear cell of aisle between them</strong>. Below that the two rows meet
     * in the middle, which is a wall across the room rather than a colonnade &mdash; so the layout
     * draws nothing instead, the same way the grid declines a window too small for one column.
     */
    static int minimumCrossAxis(int inset) {
        return 2 * inset + 3;
    }

    /**
     * The shortest run that still reads as a colonnade: <strong>at least one full bay longer than
     * the room is wide</strong>.
     *
     * <p>A colonnade is a length you walk down. In a room that is roughly square the two rows end up
     * against opposite walls with a void between them, and the result is indistinguishable from a
     * grid that lost its middle row &mdash; so this declines rather than drawing something that
     * reads as a mistake. Square rooms are what {@link GridPillarPatternProvider} is for.</p>
     *
     * <p>Expressed in bays ({@code spacing}) rather than as a ratio because that is the unit the
     * layout is actually built from: one more bay along the run than across it is exactly the point
     * where the rows stop looking like two sides of a rectangle and start looking like a corridor of
     * columns. A 15&times;9 room draws; a 15&times;15 does not.</p>
     */
    static int minimumRun(int crossLength, int spacing) {
        return crossLength + spacing;
    }

    @Override
    public Set<Coords2D> footprint(int interiorWidth, int interiorDepth) {
        // Along the longer axis; X wins a tie, deterministically.
        boolean alongX = interiorWidth >= interiorDepth;
        int runLength = alongX ? interiorWidth : interiorDepth;
        int crossLength = alongX ? interiorDepth : interiorWidth;

        if (crossLength < minimumCrossAxis(inset) || runLength < minimumRun(crossLength, spacing)) {
            return Set.of();
        }
        int[] along = GridPillarPatternProvider.positions(runLength, spacing, inset);
        if (along.length == 0) {
            return Set.of();
        }
        int nearRow = inset;
        int farRow = crossLength - 1 - inset;

        Set<Coords2D> cells = new LinkedHashSet<>(along.length * 2);
        for (int position : along) {
            cells.add(alongX ? new Coords2D(position, nearRow) : new Coords2D(nearRow, position));
            cells.add(alongX ? new Coords2D(position, farRow) : new Coords2D(farRow, position));
        }
        return cells;
    }
}
