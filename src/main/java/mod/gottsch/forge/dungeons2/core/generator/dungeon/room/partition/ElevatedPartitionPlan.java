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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link IElevatedPartitionProvider}'s answer: each cell of the run as a column of rows, in
 * <strong>interior-local</strong> cells and rows counted from the walking plane (row 0).
 *
 * <h2>Two parts to a column: fixed, then fill</h2>
 * <p>{@link Column#stones} are the rows the shape fixes &mdash; a pier's foot, a corbel, a
 * keystone. From {@link Column#fillFrom} upward the column is the partition's own block <em>to the
 * ceiling</em>, and only the generator knows where that is: it reads it from what the ceiling step
 * built, so a vault that rose over this cell is filled up into, and nothing here has to know about
 * vaults. Rows below {@code fillFrom} that hold no stone are left as the hollowed air &mdash; that
 * is the opening.</p>
 *
 * <p>{@link #rows} is how many rows the fixed part needs. A room with fewer is refused whole, for
 * the reason a partition is never built with a hole in it: half an arch is not an arch.</p>
 *
 * @author Mark Gottschling on 10/30/2026
 */
public record ElevatedPartitionPlan(List<Column> columns, int rows) {

    public static final ElevatedPartitionPlan EMPTY = new ElevatedPartitionPlan(List.of(), 0);

    public ElevatedPartitionPlan {
        columns = List.copyOf(columns);
    }

    public boolean isEmpty() {
        return columns.isEmpty();
    }

    /**
     * One cell of the run.
     *
     * @param stones   row to stone, for the rows the shape fixes
     * @param fillFrom the first row of wall that carries on to the ceiling
     */
    public record Column(Coords2D cell, Map<Integer, Stone> stones, int fillFrom) {
        public Column {
            stones = Map.copyOf(stones);
        }
    }

    /**
     * A fixed block. An empty {@code block} is the partition's own, which is how a shape names
     * "wall here" without knowing what the entry's wall is made of.
     */
    public record Stone(Optional<String> block, Map<String, String> properties) {
        public static final Stone WALL = new Stone(Optional.empty(), Map.of());

        public Stone {
            properties = Map.copyOf(properties);
        }
    }
}
