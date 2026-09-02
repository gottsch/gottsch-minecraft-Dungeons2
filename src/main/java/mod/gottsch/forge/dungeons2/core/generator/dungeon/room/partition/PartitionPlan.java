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

/**
 * Where a {@code partition} runs, in <strong>interior-local</strong> cells. Backlog #74.
 *
 * <p>{@code (0, 0)} is the interior cell at the room's minimum X and Z &mdash; floor-local
 * {@code (originX + 1, originZ + 1)} &mdash; the same convention {@code IPitShapeProvider} uses, and
 * for the same reason: a shape structurally cannot run through the cells the outer walls stand on.</p>
 *
 * <h2>Two lists, not one with a flag</h2>
 * <p>{@link #wallCells} get the partition's own block, full height. {@link #gapCells} are the way
 * through, and get at most a two-high door. They are separate lists rather than one list of
 * {@code (cell, isGap)} because the generator does genuinely different things with them &mdash;
 * different block, different number of rows &mdash; and because a cell in both is a contradiction
 * the type should not be able to express.</p>
 *
 * <h2>{@link #gapFacing} belongs to the SHAPE</h2>
 * <p>A door has to open <em>through</em> the line, so its facing is perpendicular to the run, and
 * which of the two perpendiculars it is depends on which side the enclosed area is on. Only the
 * shape knows that. Working it out in the generator would mean re-deriving the line's axis from its
 * cells, which is both harder and a second place to get it wrong.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public record PartitionPlan(List<Coords2D> wallCells, List<Coords2D> gapCells,
                            List<Coords2D> enclosedCells, String gapFacing) {

    /** No partition at all. A shape too big for the room says so this way rather than by throwing. */
    public static final PartitionPlan EMPTY =
            new PartitionPlan(List.of(), List.of(), List.of(), "north");

    public PartitionPlan {
        wallCells = List.copyOf(wallCells);
        gapCells = List.copyOf(gapCells);
        enclosedCells = List.copyOf(enclosedCells);
    }

    /** A partition with no distinct enclosed side; see {@link #enclosedCells}. */
    public PartitionPlan(List<Coords2D> wallCells, List<Coords2D> gapCells, String gapFacing) {
        this(wallCells, gapCells, List.of(), gapFacing);
    }

    public boolean isEmpty() {
        return wallCells.isEmpty() && gapCells.isEmpty();
    }

    /**
     * The cells the partition shuts off from the rest of the room &mdash; the inside of the cell.
     * Empty when the shape has no distinct inside: a {@code strip} divides a room into two halves
     * and neither is "the enclosure", so it reports none.
     *
     * <p>What this is FOR is one check, in {@code RoomPartitionGenerator}: a doorway that opens
     * straight into the enclosure is refused. Every other consequence of enclosing cells is
     * <strong>fine</strong>, and it is worth being explicit about why, because it looks like it
     * should not be. The shape always cuts a gap, so an enclosure is <em>permeable</em>: a chest,
     * a pot or a spawner inside it is content behind a door, not content lost. Only the door that
     * a player arrives through is different, and only because arriving inside the cage reads as a
     * generation fault whether or not it is one.</p>
     */
    public List<Coords2D> enclosedCells() {
        return enclosedCells;
    }
}
