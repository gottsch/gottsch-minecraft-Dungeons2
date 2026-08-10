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

import java.util.Set;

/**
 * A single position at the room's centre.
 *
 * <p>Trivial on its own, and that is the point: the layout providers are the shared answer to
 * <em>where</em> in a room something goes, and what gets built there is the generator's business.
 * A centre layout carrying a column is a single pier; carrying a dais it is a raised platform in
 * the middle of the room.</p>
 *
 * <p>Uses {@code inset} only as a veto &mdash; a room whose interior is too small to keep the
 * required clearance draws nothing rather than putting the feature hard against a wall.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public record CentrePillarPatternProvider(int inset) implements IPillarPatternProvider {

    @Override
    public Set<Coords2D> footprint(int interiorWidth, int interiorDepth) {
        if (interiorWidth < 1 + 2 * inset || interiorDepth < 1 + 2 * inset) {
            return Set.of();
        }
        return Set.of(new Coords2D((interiorWidth - 1) / 2, (interiorDepth - 1) / 2));
    }
}
