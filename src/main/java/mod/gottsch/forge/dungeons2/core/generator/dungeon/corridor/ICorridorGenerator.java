/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Renders one {@link CorridorData corridor region} as a list of
 * {@link BlockPlacement}s.
 *
 * <p>The {@link #build(CorridorData, Grid2D, int, IDungeonMotif, RandomSource, List)
 * grid-based} overload reads neighbor wall cells from the live floor
 * {@link Grid2D}. The {@link #build(CorridorData, int, IDungeonMotif, RandomSource, List)
 * grid-free} overload reads them from {@link CorridorData#getWallCells()} instead,
 * so it works on a deserialized corridor (Phase 3 pieces, where the transient
 * grid is gone). Both overloads emit identical placements when the corridor's
 * {@code wallCells} were populated from the same grid.</p>
 */
public interface ICorridorGenerator {
    void build(CorridorData corridor, Grid2D grid, int floorY,
               IDungeonMotif motif, RandomSource random, List<BlockPlacement> out);

    /**
     * Grid-free render: uses {@link CorridorData#getWallCells()} for wall columns.
     * For corridors whose wall cells were folded in by the planner, this produces
     * the same set of placements as the grid-based overload.
     */
    void build(CorridorData corridor, int floorY,
               IDungeonMotif motif, RandomSource random, List<BlockPlacement> out);
}
