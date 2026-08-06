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
 * Columns on an even lattice across the room's interior &mdash; a hypostyle hall, a crypt, a
 * cistern.
 *
 * <h2>Centred on the usable window, not counted from the origin</h2>
 * <p>The positions along each axis are laid out <strong>symmetric about the interior's own
 * centre</strong>, not stepped from cell 0. This is the lesson the projecting pilasters taught the
 * hard way: a fixed stride from the origin gives a different phase depending on the room's size, so
 * the columns crowd one wall and leave a gap at the other, and the result reads as the generator
 * doing something odd rather than as a rhythm anyone chose. It also means a square room's grid is
 * symmetric under rotation, which is what makes a hypostyle hall look built.</p>
 *
 * <p>{@code inset} keeps the lattice clear of the wall. It defaults to {@value #DEFAULT_INSET}
 * rather than 0 for two reasons: a column hard against the wall reads as a clumsy pilaster rather
 * than as a free-standing one, and the inner ring is where loot pots stand and where projecting wall
 * trim lands, so staying out of it keeps the common case free of collisions rather than relying on
 * the reservation to sort them out.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public record GridPillarPatternProvider(int spacing, int inset) implements IPillarPatternProvider {

    /**
     * Four cells between columns. Wide enough to walk between comfortably and to leave the lattice
     * reading as separate columns rather than as a broken wall.
     */
    public static final int DEFAULT_SPACING = 4;

    /** One clear cell against the wall. See the class note for why this is not 0. */
    public static final int DEFAULT_INSET = 2;

    public GridPillarPatternProvider(int spacing) {
        this(spacing, DEFAULT_INSET);
    }

    @Override
    public Set<Coords2D> footprint(int interiorWidth, int interiorDepth) {
        int[] xs = positions(interiorWidth, spacing, inset);
        int[] zs = positions(interiorDepth, spacing, inset);
        Set<Coords2D> cells = new LinkedHashSet<>(xs.length * zs.length);
        for (int x : xs) {
            for (int z : zs) {
                cells.add(new Coords2D(x, z));
            }
        }
        return cells;
    }

    /**
     * Evenly spaced positions along one axis, centred within {@code [inset, length - 1 - inset]}.
     *
     * <p>Returns empty when the window cannot hold a single column. A lattice of one column per axis
     * &mdash; a single pillar in the middle of a small room &mdash; is deliberately allowed: it is
     * a legitimate look, and suppressing it would mean a room either side of the threshold gets
     * wildly different treatment from the same authored entry.</p>
     */
    static int[] positions(int length, int spacing, int inset) {
        int usable = length - 2 * inset;
        if (usable < 1 || spacing < 1) {
            return new int[0];
        }
        int count = (usable - 1) / spacing + 1;
        int span = (count - 1) * spacing;
        // Integer division bias is deliberate and consistent: with an odd remainder the lattice sits
        // one cell toward the low edge on BOTH axes, so a square room stays square rather than
        // acquiring a diagonal skew from rounding the two axes differently.
        int start = inset + (usable - span - 1) / 2;
        int[] positions = new int[count];
        for (int i = 0; i < count; i++) {
            positions[i] = start + i * spacing;
        }
        return positions;
    }
}
