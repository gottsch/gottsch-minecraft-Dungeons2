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

import net.minecraft.util.RandomSource;

/**
 * A pit provider: which interior cells are excavated, how deep each one is, and what stands on it.
 *
 * <h2>A provider owns its whole geometry</h2>
 * <p>It returns a {@link PitPlan}, not a footprint. That is what lets "a sunken court" and "a sheer
 * hazard shaft" be two PROVIDERS rather than one provider plus a profile flag &mdash; and it is how
 * the floor, wall and ceiling providers already work, each owning its own arrangement and its own
 * blocks. {@link PitPlans} offers the two shipped profiles so a provider need not reinvent them.</p>
 *
 * <p>The one thing a provider does not get the last word on is DEPTH: {@code RoomPitGenerator}
 * clamps every cell to the floor's {@code sinkOffset} as it writes, so no config can dig past the
 * floor's own budget into the gap between floors. See {@link PitPlan}.</p>
 *
 * <p>Coordinates are <strong>interior-local</strong>, the same convention the pillar providers use:
 * {@code (0, 0)} is the interior cell at the room's minimum X and Z, i.e. floor-local
 * {@code (originX + 1, originZ + 1)}. A provider therefore never has to remember to skip the wall
 * ring, and structurally cannot excavate the cells the walls stand on.</p>
 *
 * @author Mark Gottschling on Aug 27, 2026
 */
public interface IPitShapeProvider {

    /**
     * The plan for a room of this interior size. An empty plan is legal and means no pit &mdash; a
     * shape too big for the room says so this way rather than by throwing.
     *
     * @param interiorWidth interior extent along X ({@code room width - 2})
     * @param interiorDepth interior extent along Z ({@code room depth - 2})
     * @param random        the room's own source, for a provider that scatters something
     */
    PitPlan plan(int interiorWidth, int interiorDepth, RandomSource random);
}
