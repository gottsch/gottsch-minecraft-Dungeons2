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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.mining;

import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;

import java.util.ArrayList;
import java.util.List;

/**
 * How much stone a planned dungeon removes, and at what depths &mdash; the accounting half of
 * backlog #7.
 *
 * <p>Pure function of a {@link DungeonLayout}: no world, no randomness, no Minecraft imports. That
 * is the whole point. The tally has to be identical no matter which chunk generates first, and the
 * only description of the dungeon that is complete before any block is written is the plan.</p>
 *
 * <h2>One entry per room and per corridor, not one per floor</h2>
 * <p>Each is emitted with <em>its own</em> Y range rather than folded into a floor total, because
 * the ore table is keyed on depth and a floor is 20 blocks tall. A floor straddling Y 16 is half in
 * diamond country; collapsing it to a single number would make it all one or all the other. Rooms on
 * one floor also differ in height from each other (#51's taper), so even within a floor there is no
 * single Y range to collapse to.</p>
 *
 * <h2>What counts as excavated</h2>
 * <p>The <strong>interior air</strong> a piece hollows out: a room's {@code width * depth * height},
 * a corridor's cell count times its wall height. Not the walls, floors or ceilings &mdash; those
 * are blocks the dungeon <em>puts back</em>, and a stone brick standing where stone stood destroyed
 * nothing. Not the shell around the dungeon either, for the same reason.</p>
 *
 * <p>Every room is counted, including the ones no procedural piece builds. A START room is a hole in
 * the ground whoever dug it; that the entrance template fills it rather than
 * {@code BasicRoomGenerator} is a rendering detail, and the ore that used to be there is gone
 * either way.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
public final class ExcavationLedger {

    private ExcavationLedger() {}

    /**
     * One excavated volume at one depth.
     *
     * @param baseY  world Y of the lowest excavated row
     * @param height how many rows up from {@code baseY} were removed
     * @param volume blocks removed, {@code footprint * height}
     */
    public record Excavation(int baseY, int height, long volume) {}

    /** Every excavation this layout performs, in floor order. */
    public static List<Excavation> of(DungeonLayout layout) {
        List<Excavation> out = new ArrayList<>();
        if (layout == null) {
            return out;
        }
        for (FloorLayout floor : layout.getFloors()) {
            // floorY is the walking plane -- the row a player stands ON -- so the air a room
            // hollows out starts one above it and runs for `height` rows. Off by one here would
            // shift every excavation a block deeper than it is, which at a band boundary is the
            // difference between an ore appearing and not.
            int interiorBaseY = floor.getFloorY() + 1;
            for (RoomData room : floor.getRooms()) {
                int height = Math.max(1, room.getHeight());
                long footprint = (long) Math.max(0, room.getWidth()) * Math.max(0, room.getDepth());
                if (footprint > 0) {
                    out.add(new Excavation(interiorBaseY, height, footprint * height));
                }
            }
            for (CorridorData corridor : floor.getCorridors()) {
                int height = Math.max(1, corridor.getWallHeight());
                long footprint = corridor.getCells().size();
                if (footprint > 0) {
                    out.add(new Excavation(interiorBaseY, height, footprint * height));
                }
            }
        }
        return out;
    }

    /** Total blocks removed, for logging and for the diagnostics that calibrate the ore table. */
    public static long totalVolume(List<Excavation> excavations) {
        long total = 0L;
        for (Excavation excavation : excavations) {
            total += excavation.volume();
        }
        return total;
    }
}
