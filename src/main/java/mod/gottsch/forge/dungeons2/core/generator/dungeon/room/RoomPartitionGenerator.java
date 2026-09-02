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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.PartitionPatternEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.PartitionPlan;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a room's {@code partition}: an interior wall, and the way through it. Backlog #74.
 *
 * <h2>Interior-local in, floor-local out</h2>
 * <p>The shape works in interior-local cells &mdash; {@code (0, 0)} is floor-local
 * {@code (originX + 1, originZ + 1)} &mdash; so a provider structurally cannot run a partition
 * through the cells the outer walls stand on. The translation happens here, once, which is also why
 * a provider never has to know the room's origin.</p>
 *
 * <h2>What it does about the doorways</h2>
 * <p>It refuses to build at all if the run would cross a doorway approach. Not "it skips those
 * cells": a partition with a hole in it where a door happens to be is not a partition, it is a
 * broken one, and it would seal or fail to seal depending on where the maze put a door &mdash; which
 * is the sort of thing that looks fine in ninety-nine rooms and absurd in the hundredth. Refusing is
 * the honest degrade, and it is the same "an empty plan is an answer" convention the shapes follow.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public final class RoomPartitionGenerator {

    /** Vanilla's double-block half property; see {@link PartitionPatternEntry} on {@code gap_block}. */
    static final String HALF = "half";
    /** Vanilla's horizontal facing property. */
    static final String FACING = "facing";

    private RoomPartitionGenerator() {}

    /**
     * Emits this room's partition, returning the cells it took.
     *
     * @param occupied cells already claimed by architecture; the partition refuses to cross them
     *                 for the same reason it refuses to cross a doorway
     */
    public static Set<Coords2D> build(RoomData room, int floorY, PartitionPatternEntry config,
                                      Set<Coords2D> occupied, RandomSource random,
                                      List<BlockPlacement> out) {
        int rows = config.heightWithin(room.getHeight());
        if (rows <= 0) {
            return Set.of();
        }

        int interiorWidth = room.getWidth() - 2;
        int interiorDepth = room.getDepth() - 2;
        if (interiorWidth < 1 || interiorDepth < 1) {
            return Set.of();
        }

        PartitionPlan plan = config.shape().provider()
                .plan(interiorWidth, interiorDepth, random);
        if (plan.isEmpty()) {
            return Set.of();
        }

        Map<Coords2D, Boolean> cells = new LinkedHashMap<>();
        for (Coords2D cell : plan.wallCells()) {
            cells.put(toFloorLocal(room, cell), Boolean.FALSE);
        }
        for (Coords2D cell : plan.gapCells()) {
            cells.put(toFloorLocal(room, cell), Boolean.TRUE);
        }

        Set<Coords2D> blocked = RoomInterior.cellsInsideDoorways(room);
        for (Coords2D cell : cells.keySet()) {
            if (blocked.contains(cell) || occupied.contains(cell)) {
                return Set.of();
            }
        }
        // And refuse a room whose doorway opens straight into the cage. Content inside an enclosure
        // is fine -- the shape always cuts a gap, so it is content behind a door -- but a player
        // arriving inside one reads as a generation fault whether or not it is one.
        for (Coords2D cell : plan.enclosedCells()) {
            if (blocked.contains(toFloorLocal(room, cell))) {
                return Set.of();
            }
        }

        for (Map.Entry<Coords2D, Boolean> entry : cells.entrySet()) {
            Coords2D cell = entry.getKey();
            if (entry.getValue()) {
                emitGap(cell, floorY, config, plan.gapFacing(), out);
            } else {
                for (int row = 0; row < rows; row++) {
                    // floorY + 1 is the walking plane, the same row every other slot stands on.
                    out.add(new BlockPlacement(cell.getX(), floorY + 1 + row, cell.getY(),
                            config.block()));
                }
            }
        }
        return new LinkedHashSet<>(cells.keySet());
    }

    /**
     * The way through. An unauthored {@code gap_block} leaves it open, which is a doorway and is the
     * shipped default; an authored one is written on the two rows a player walks through, with
     * {@code half} set so a vanilla door hangs correctly.
     *
     * <p>Nothing is emitted for the open case rather than explicit air. The interior was already
     * hollowed out before this runs, so writing air would be a no-op that only made the placement
     * list longer &mdash; and a placement list that carries cells it does not change is one that
     * misleads anything counting it.</p>
     */
    private static void emitGap(Coords2D cell, int floorY, PartitionPatternEntry config,
                                String facing, List<BlockPlacement> out) {
        if (config.gapBlock().isEmpty()) {
            return;
        }
        String block = config.gapBlock().get();
        out.add(new BlockPlacement(cell.getX(), floorY + 1, cell.getY(), block,
                properties(facing, "lower")));
        out.add(new BlockPlacement(cell.getX(), floorY + 2, cell.getY(), block,
                properties(facing, "upper")));
    }

    private static Map<String, String> properties(String facing, String half) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(FACING, facing);
        properties.put(HALF, half);
        return properties;
    }

    /** Interior-local to floor-local. The room box includes its wall ring, so the interior is +1. */
    private static Coords2D toFloorLocal(RoomData room, Coords2D cell) {
        return new Coords2D(room.getOriginX() + 1 + cell.getX(),
                room.getOriginZ() + 1 + cell.getY());
    }
}
