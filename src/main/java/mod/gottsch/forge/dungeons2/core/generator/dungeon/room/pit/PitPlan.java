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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;

/**
 * What a pit provider decided: how far below the walking plane each cell sits, and what (if
 * anything) stands on it.
 *
 * <h2>Why a plan and not a footprint</h2>
 * <p>The first version had a provider return only the SET of excavated cells, with the generator
 * deciding every cell's depth. That made the digging profile a property of the generator rather
 * than of the provider, so "a pit with sheer sides" could not be written as a provider at all
 * &mdash; it needed a flag. A provider owns its own geometry here, exactly as the floor, wall and
 * ceiling providers own theirs, and a new profile is a new provider rather than a new enum
 * value.</p>
 *
 * <h2>Depths are a REQUEST, and the generator has the last word</h2>
 * <p>{@code RoomPitGenerator} clamps every depth to the floor's {@code sinkOffset} as it writes, so
 * a provider cannot dig past the floor's own budget into the gap between floors however its config
 * is authored. That check moved <em>onto the output</em> precisely because providers are extensible:
 * a rule every third-party provider has to remember is a rule that gets forgotten, and the failure
 * it allows is a hole into the room below.</p>
 *
 * @param depths interior-local cell to blocks below the walking plane, 1 or more
 * @param fills  interior-local cell to a block standing ON that terrace (a stalagmite, say), for
 *               the cells that have one. Nullable states are not stored; a cell with nothing simply
 *               has no entry.
 * @param fillData block-entity data for a fill that needs some -- today only a chest, which is
 *               inert without the {@code LootTable} that says what is in it. A PARALLEL map rather
 *               than a richer fill type: exactly one provider populates it, one line reads it, and
 *               every other fill (a stalagmite, an altar stone) has no block entity at all.
 * @param rim    interior-local cells OUTSIDE the pit whose floor block is replaced, at the room's
 *               own walking plane &mdash; a ring of stairs around a sunken floor. These cells stay
 *               walkable and are NOT excavated: the pit is one block down, and the stair's low half
 *               gives the half-step between the two that makes it read as a step rather than a
 *               ledge.
 * @param flood  interior-local cell to a block filling every row the excavation OPENED in that
 *               cell &mdash; water or lava in a grated sump. Written after the terrace and before any
 *               fill, so a fill still stands in it. Under a {@code cover} it stops one row short of
 *               the walking plane; the cover owns that row.
 * @param cover  interior-local cell to a block laid over the pit AT the room's own walking plane
 *               &mdash; a grate. The cell is still excavated beneath it, and still claimed, so
 *               nothing is placed standing on it.
 *
 * @author Mark Gottschling on Aug 27, 2026
 */
public record PitPlan(Map<Coords2D, Integer> depths, Map<Coords2D, BlockState> fills,
                      Map<Coords2D, BlockState> rim,
                      Map<Coords2D, mod.gottsch.forge.dungeons2.core.data.BlockEntityData> fillData,
                      Map<Coords2D, BlockState> flood, Map<Coords2D, BlockState> cover) {

    /** The shape this record had before a pit could be flooded or covered. */
    public PitPlan(Map<Coords2D, Integer> depths, Map<Coords2D, BlockState> fills,
                   Map<Coords2D, BlockState> rim,
                   Map<Coords2D, mod.gottsch.forge.dungeons2.core.data.BlockEntityData> fillData) {
        this(depths, fills, rim, fillData, Map.of(), Map.of());
    }

    /** The shape this record had before a fill could carry block-entity data. */
    public PitPlan(Map<Coords2D, Integer> depths, Map<Coords2D, BlockState> fills,
                   Map<Coords2D, BlockState> rim) {
        this(depths, fills, rim, Map.of());
    }

    public PitPlan(Map<Coords2D, Integer> depths) {
        this(depths, Map.of(), Map.of());
    }

    public PitPlan(Map<Coords2D, Integer> depths, Map<Coords2D, BlockState> fills) {
        this(depths, fills, Map.of());
    }

    /** A provider that decided this room gets no pit. */
    public static PitPlan empty() {
        return new PitPlan(Map.of(), Map.of(), Map.of());
    }

    public boolean isEmpty() {
        return depths.isEmpty();
    }

    /** The excavated cells, which are exactly the cells with a depth. */
    public Set<Coords2D> cells() {
        return depths.keySet();
    }
}
