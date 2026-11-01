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

import java.util.Set;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;

/**
 * A free-standing pillar layout: which interior cells carry a column.
 *
 * <h2>Why this returns a footprint and not a plan of block states</h2>
 * <p>The surface providers return a {@code SurfacePlan} of nullable {@code BlockState} because a
 * surface pattern varies <em>across</em> the surface &mdash; a border's corner differs from its
 * edge. A pillar does not: every column in a layout is the same column, and what varies is the
 * <em>row</em> (plinth, shaft, capital), which is the same for all of them. So the layout is a pure
 * 2D question and the materials are the generator's, which keeps a new layout to an arithmetic
 * problem with nothing to get wrong about blocks.</p>
 *
 * <p>Coordinates are <strong>interior-local</strong>: {@code (0, 0)} is the interior cell at the
 * room's minimum X and Z corner, i.e. floor-local {@code (originX + 1, originZ + 1)}. Working in
 * interior space rather than room space means a layout never has to remember to skip the wall ring,
 * which is exactly the class of off-by-one that made projecting pilasters land in discarded columns.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public interface IPillarPatternProvider {

    /**
     * The interior cells carrying a column, in interior-local coordinates.
     *
     * @param interiorWidth  interior extent along X ({@code room width - 2})
     * @param interiorDepth  interior extent along Z ({@code room depth - 2})
     */
    Set<Coords2D> footprint(int interiorWidth, int interiorDepth);
}
