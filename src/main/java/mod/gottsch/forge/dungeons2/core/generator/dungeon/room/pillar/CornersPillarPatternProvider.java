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
 * Four positions, one in each corner of the interior, {@code inset} from both walls.
 *
 * <h2>How this differs from {@code quartet}</h2>
 * <p>A quartet marks a square about the room's <em>centre</em> and never grows. Corners <em>hug the
 * walls</em>, so the arrangement spreads as the room does and the middle of the room is left
 * completely open. That is what makes it the layout for something you want out of the way &mdash;
 * four braziers lighting a hall from its edges rather than an obstacle in the middle of it.</p>
 *
 * <p>Draws nothing when the two corners on an axis would meet or cross, since that is a room with no
 * corners left to speak of.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public record CornersPillarPatternProvider(int inset) implements IPillarPatternProvider {

    @Override
    public Set<Coords2D> footprint(int interiorWidth, int interiorDepth) {
        int lowX = inset;
        int highX = interiorWidth - 1 - inset;
        int lowZ = inset;
        int highZ = interiorDepth - 1 - inset;
        if (highX - lowX < 1 || highZ - lowZ < 1) {
            return Set.of();
        }
        Set<Coords2D> cells = new LinkedHashSet<>(4);
        for (int x : new int[]{lowX, highX}) {
            for (int z : new int[]{lowZ, highZ}) {
                cells.add(new Coords2D(x, z));
            }
        }
        return cells;
    }
}
