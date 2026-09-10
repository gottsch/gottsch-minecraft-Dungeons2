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
package mod.gottsch.forge.dungeons2.core.config.floor;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A {@link FloorPattern} that can paint <strong>one cell at a time</strong>, needing nothing but
 * that cell's position and the random stream &mdash; no rectangle, no origin, no extent.
 *
 * <h2>What this is for</h2>
 * <p>Corridors have no floor pattern list, and the reason given in {@code CorridorConfig} is that a
 * border ring or a checkerboard needs a room-sized rectangle while a corridor is a 1-3 cell wide
 * run. That reason is right about {@code border}, {@code cross}, {@code spokes} and the rest &mdash;
 * and simply not true of {@code speckle}, whose answer for a cell depends on nothing outside it.
 * This interface is how a pattern says which of those it is, so a corridor can accept the second
 * kind and <strong>reject the first at load</strong> rather than drawing a quarter of a border ring
 * across a passage.</p>
 *
 * <p>It is the floor's counterpart to the exception walls already make: {@code courses} are allowed
 * on a corridor because a horizontal band sits at a constant row and runs along whatever the wall
 * does. Same argument, different axis.</p>
 *
 * <h2>Coordinates are WORLD coordinates, deliberately</h2>
 * <p>A room pattern indexes from the room's own origin, because a checkerboard that shifted phase
 * with the room's position in the world would look like a bug. A corridor has no origin to index
 * from &mdash; it is a path through a grid, emitted cell by cell &mdash; so an implementation is
 * handed the world x/z. For {@code speckle} that is invisible (it reads neither). For a
 * position-dependent pattern it means the phase is continuous along the whole run and across the
 * segments it is cut into, which is what a corridor wants and the opposite of what a room does.</p>
 *
 * <h2>A painter may decline a cell</h2>
 * <p>{@link CellFloor#at} returns {@code null} for "not mine", and the corridor's own
 * {@code floor}/{@code alternate_floor} roll stands. That is what lets a {@code speckle} authored
 * with no {@code primary_block} ACCENT a corridor rather than repaving it &mdash; the pair keeps
 * its 45/55 mix and the accent lands on top of it.</p>
 *
 * <h2>Exactly one draw per cell</h2>
 * <p>The corridor rolls its pair first and unconditionally, then calls the painter, so the stream
 * advances by two per cell whenever a pattern is authored and by one when none is. What matters is
 * that the count is CONSTANT: a corridor piece builds its whole placement list in one pass and is
 * only clipped to chunks afterwards, so a painter whose draw count varied with the cell (a
 * short-circuit at probability 0, say) would move every cell after it and put a seam in the
 * floor.</p>
 */
public interface CellLocalFloorPattern {

    /**
     * A painter for this pattern, or {@code null} when one of its block ids names nothing
     * registered.
     *
     * <p>Null rather than a painter that draws air: an unresolvable block degrades the whole
     * treatment and the caller falls back to the corridor's own {@code floor}/
     * {@code alternate_floor} pair, which is the same rule {@code FloorPattern#generator}
     * implementations already follow for rooms.</p>
     */
    CellFloor cellFloor();

    /** Paints a single floor cell. */
    @FunctionalInterface
    interface CellFloor {
        /**
         * @param x      world x of the cell
         * @param z      world z of the cell
         * @param random the corridor's stream; an implementation must consume the SAME number of
         *               values for every cell, accented or not
         * @return the block for this cell, or {@code null} to leave whatever is underneath
         */
        BlockState at(int x, int z, RandomSource random);
    }
}
