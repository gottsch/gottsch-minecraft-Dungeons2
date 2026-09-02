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

import mod.gottsch.forge.dungeons2.core.config.partition.CornerPartitionShape.Corner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An <strong>L</strong> of bars fencing a rectangular cell off in one corner of the room. Backlog
 * #74, and the shape the entry was actually asking for: a prison cell.
 *
 * <h2>Why an L and not a diagonal</h2>
 * <p>"Cutting a corner off" reads as a diagonal, and a diagonal is the one thing this cannot be.
 * Blocks are axis-aligned: a 45&deg; run of bars is a staircase of disconnected panels with gaps a
 * player can see through and a mob can path through. Two axis-aligned legs meeting at a corner fence
 * off the same rectangle, connect properly, and are what a cell block looks like anyway.</p>
 *
 * <h2>The corner cell is part of the line</h2>
 * <p>The two legs share the cell where they meet, which is why the plan is built through a
 * {@link LinkedHashSet}. Without the dedupe that cell would appear twice; harmless for a block, but
 * it would also be counted twice by anything measuring the partition, and it is the sort of thing
 * that stays invisible until something downstream divides by it.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public class CornerPartitionShapeProvider implements IPartitionShapeProvider {

    private final int cellWidth;
    private final int cellDepth;
    private final Corner corner;

    /**
     * @param cellWidth the fenced cell's extent along X, in cells
     * @param cellDepth its extent along Z
     * @param corner    which corner, possibly {@link Corner#ANY} to roll one per room
     */
    public CornerPartitionShapeProvider(int cellWidth, int cellDepth, Corner corner) {
        this.cellWidth = cellWidth;
        this.cellDepth = cellDepth;
        this.corner = corner;
    }

    @Override
    public PartitionPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        if (cellWidth < 1 || cellDepth < 1) {
            return PartitionPlan.EMPTY;
        }
        // The line sits one cell OUTSIDE the fenced rectangle, so the room needs the rectangle's own
        // cells plus the line's, and at least one left over on the far side -- a partition with
        // nothing on the other side of it is just a smaller room.
        if (cellWidth + 2 > interiorWidth || cellDepth + 2 > interiorDepth) {
            return PartitionPlan.EMPTY;
        }

        // Rolled here rather than at config time: `plan` is handed the room's own source, so the
        // draw is a pure function of the piece's seed and comes out identical on every one of the
        // repeated postProcess calls a room straddling two chunks gets.
        Corner drawn = corner.resolve(random);
        boolean west = drawn.west();
        boolean north = drawn.north();

        int lineX = west ? cellWidth : interiorWidth - 1 - cellWidth;
        int lineZ = north ? cellDepth : interiorDepth - 1 - cellDepth;

        // Leg A runs along Z at lineX; leg B along X at lineZ. Both start at the room wall the
        // corner touches and end at the cell where they meet.
        Set<Coords2D> cells = new LinkedHashSet<>();
        for (int i = 0; i <= cellDepth; i++) {
            cells.add(new Coords2D(lineX, north ? i : interiorDepth - 1 - i));
        }
        for (int i = 0; i <= cellWidth; i++) {
            cells.add(new Coords2D(west ? i : interiorWidth - 1 - i, lineZ));
        }

        // The way in goes in the LONGER leg: a longer run has more room to put a door somewhere that
        // is not immediately against something. Ties go to leg A, arbitrarily but deterministically.
        Coords2D gap;
        String facing;
        if (cellDepth >= cellWidth) {
            // Half the leg's length, which is never the corner cell -- that is index cellDepth, and
            // cellDepth / 2 < cellDepth for every cellDepth >= 1. A door in the corner would have
            // bars meeting it on two sides.
            int i = cellDepth / 2;
            gap = new Coords2D(lineX, north ? i : interiorDepth - 1 - i);
            // The run is along Z, so the way through is along X: out of the cell, into the room.
            facing = west ? "east" : "west";
        } else {
            int i = cellWidth / 2;
            gap = new Coords2D(west ? i : interiorWidth - 1 - i, lineZ);
            facing = north ? "south" : "north";
        }

        // The rectangle the L shuts off. Reported so the generator can refuse a room whose
        // doorway opens straight into the cage -- see PartitionPlan#enclosedCells for why that is
        // the ONLY thing it is used for.
        List<Coords2D> enclosed = new ArrayList<>();
        for (int dx = 0; dx < cellWidth; dx++) {
            for (int dz = 0; dz < cellDepth; dz++) {
                enclosed.add(new Coords2D(west ? dx : interiorWidth - 1 - dx,
                        north ? dz : interiorDepth - 1 - dz));
            }
        }

        List<Coords2D> wallCells = new ArrayList<>(cells);
        wallCells.remove(gap);
        return new PartitionPlan(wallCells, List.of(gap), enclosed, facing);
    }
}
