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

import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry.PillarEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomInterior;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a room's free-standing columns.
 *
 * <h2>What a column is</h2>
 * <p>One interior cell, filled from the row above the floor to the row below the ceiling &mdash;
 * {@code floorY + 1} through {@code floorY + height - 2}. That is exactly the span
 * {@code RoomVolumeGenerator} clears as interior air, which is why this runs after it: the column
 * simply wins those cells back.</p>
 *
 * <p>The bottom row takes {@code baseBlock} and the top row {@code capBlock}, both defaulting to the
 * shaft. A column only two rows tall is <strong>all plinth and capital with no shaft</strong>, which
 * is what a short column looks like rather than a case needing special handling. A one-row column
 * takes the base alone; a room with no interior rows gets nothing.</p>
 *
 * <h2>What stops a column being placed</h2>
 * <p>A column whose cell is a doorway approach is <strong>dropped whole</strong> &mdash; the same
 * rule a projecting wall strip follows at a doorway, and for the same reason. Clipping out just the
 * two door rows leaves the rest of the column hanging in mid-air above the opening: a missing column
 * in a lattice reads as a room, a floating one reads as a bug.</p>
 *
 * <h2>Several layouts at once</h2>
 * <p>Layouts are applied in list order and a later one wins a shared cell, the same
 * ordering-is-execution-order convention every other slot uses. Cells are deduplicated so two
 * layouts overlapping never emit the same column twice &mdash; which matters because these are
 * solid blocks in open air rather than sparse marks on a surface, and a duplicate would be a real
 * second write rather than a harmless overlay.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public class BasicPillarGenerator implements IDungeonPillarGenerator {

    /** Empty means no columns: the interior stays as {@code RoomVolumeGenerator} left it. */
    private List<PillarLayout> layouts = List.of();

    private Set<Coords2D> occupied = Set.of();

    /**
     * Injects the layouts for this room, already chosen and gated by {@link PillarPatternSelector}.
     * Empty &mdash; the default &mdash; is a room with no columns.
     */
    public BasicPillarGenerator withPillarLayouts(List<PillarLayout> layouts) {
        this.layouts = layouts == null ? List.of() : layouts;
        return this;
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                      List<BlockPlacement> out) {
        if (layouts.isEmpty()) {
            return;
        }
        int interiorWidth = room.getWidth() - 2;
        int interiorDepth = room.getDepth() - 2;
        int interiorRows = room.getHeight() - 2;
        if (interiorWidth < 1 || interiorDepth < 1 || interiorRows < 1) {
            return;
        }

        Set<Coords2D> doorways = RoomInterior.cellsInsideDoorways(room);
        Set<Coords2D> taken = new LinkedHashSet<>();

        for (PillarLayout layout : layouts) {
            for (Coords2D cell : layout.provider().footprint(interiorWidth, interiorDepth)) {
                // Interior-local -> floor-local: the interior starts one cell in from the room's
                // origin on both axes. Doing the shift here, once, is why a layout never has to know
                // the wall ring exists.
                Coords2D at = new Coords2D(room.getOriginX() + 1 + cell.getX(),
                        room.getOriginZ() + 1 + cell.getY());
                if (doorways.contains(at) || !taken.add(at)) {
                    continue;
                }
                emitColumn(at.getX(), at.getY(), floorY, interiorRows, layout.entry(), out);
            }
        }
        this.occupied = taken;
    }

    private void emitColumn(int x, int z, int floorY, int interiorRows, PillarEntry entry,
                            List<BlockPlacement> out) {
        for (int row = 0; row < interiorRows; row++) {
            String block;
            Map<String, String> properties;
            // Base wins a one-row column: a lone capital with nothing under it reads as a mistake,
            // where a lone plinth reads as a stub -- which is what a column with no room is.
            if (row == 0) {
                block = entry.baseBlockOrBase();
                properties = entry.basePropertiesOrBase();
            } else if (row == interiorRows - 1) {
                block = entry.capBlockOrBase();
                properties = entry.capPropertiesOrBase();
            } else {
                block = entry.block();
                properties = entry.properties();
            }
            out.add(new BlockPlacement(x, floorY + 1 + row, z, block, properties));
        }
    }

    @Override
    public Set<Coords2D> occupiedFloorCells() {
        return occupied;
    }
}
