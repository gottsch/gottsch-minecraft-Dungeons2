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

import mod.gottsch.forge.dungeons2.core.config.partition.StripPartitionShape.Axis;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * One straight run wall to wall, dividing the room in two, with a way through it. Backlog #74.
 *
 * <p>Where {@link CornerPartitionShapeProvider} makes a cell, this makes an
 * <strong>antechamber</strong>: you come in, and the rest of the room is on the far side of a grate.</p>
 *
 * <h2>It always leaves a cell on each side</h2>
 * <p>The run is clamped to the interior's second row at the earliest and its second-to-last at the
 * latest. A run hard against a wall is not a partition, it is a second skin on the wall, and the
 * only thing it would divide the room into is the room and nothing.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public class StripPartitionShapeProvider implements IPartitionShapeProvider {

    private final Axis axis;
    private final Integer offset;

    /**
     * @param axis   which way the run lies, possibly {@link Axis#ANY} to roll one per room
     * @param offset the index the run sits at, or null for the middle of the room
     */
    public StripPartitionShapeProvider(Axis axis, Integer offset) {
        this.axis = axis;
        this.offset = offset;
    }

    @Override
    public PartitionPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        // Rolled here for CornerPartitionShapeProvider's reason: `plan` is handed the room's own
        // source, so the draw is a pure function of the piece's seed.
        boolean alongX = axis.resolve(random).alongX();

        // `across` is the axis the run divides; `span` the axis it runs along.
        int across = alongX ? interiorDepth : interiorWidth;
        int span = alongX ? interiorWidth : interiorDepth;
        // A cell on each side of the run, plus the run itself.
        if (across < 3 || span < 1) {
            return PartitionPlan.EMPTY;
        }

        int line = offset == null ? across / 2 : offset;
        // Clamped, not rejected: the same scheme has to stay valid in a room the author never
        // measured, and keeping the partition is closer to what they asked for than dropping it.
        line = Math.min(Math.max(line, 1), across - 2);

        int gapIndex = span / 2;
        List<Coords2D> wallCells = new ArrayList<>();
        Coords2D gap = null;
        for (int i = 0; i < span; i++) {
            Coords2D cell = alongX ? new Coords2D(i, line) : new Coords2D(line, i);
            if (i == gapIndex) {
                gap = cell;
            } else {
                wallCells.add(cell);
            }
        }

        // Perpendicular to the run, as any door through it must be. The two sides of a strip are
        // symmetric, so WHICH of the two perpendiculars is arbitrary -- but it has to be the same
        // arbitrary one on every render, which is why it is a constant and not a roll.
        String facing = alongX ? "south" : "east";
        return gap == null ? PartitionPlan.EMPTY
                : new PartitionPlan(wallCells, List.of(gap), facing);
    }
}
