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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a room's free-standing columns.
 *
 * <h2>What a column is</h2>
 * <p>{@code thickness} x {@code thickness} interior cells &mdash; one by default &mdash; filled
 * from the row above the floor to the row below the ceiling, {@code floorY + 1} through
 * {@code floorY + height - 2}. That is exactly the span {@code RoomVolumeGenerator} clears as
 * interior air, which is why this runs after it: the column simply wins those cells back.</p>
 *
 * <p>A layout's footprint is therefore a set of <strong>anchors</strong> rather than of columns.
 * Thickness is applied here and not in the provider because it is orthogonal to arrangement: doing
 * it once at draw time gives every layout thick columns, where a provider-side implementation would
 * be four copies. Odd thicknesses centre on the anchor; even ones lean toward +x/+z, because they
 * have to lean somewhere.</p>
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
 * <p>The same rule decides a thick column, over the whole of its footprint: if <em>any</em> of its
 * cells is a doorway approach, is excavated by a pit, or falls outside the interior, the entire
 * column is skipped. A pier truncated by a wall reads exactly as wrong as one hanging over a door,
 * so a thickness the room cannot carry costs the column rather than producing half of it.</p>
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
                      List<BlockPlacement> out, Set<Coords2D> excluded) {
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
            int thickness = layout.entry().thickness();
            for (Coords2D anchor : layout.provider().footprint(interiorWidth, interiorDepth)) {
                List<Coords2D> shaft = shaftCells(room, anchor, thickness, interiorWidth,
                        interiorDepth);
                // Whole or not at all -- see the class note. `shaft` is empty when any cell of it
                // left the interior, and the two set tests below are applied across the WHOLE
                // footprint for the same reason.
                //
                // #58: `excluded` is the pit. A column drawn over an excavated cell would start
                // at the walking plane with the hole beneath it, so it is dropped and the rest of
                // the layout stands -- the same treatment, and the same set, as a doorway approach.
                if (shaft.isEmpty() || shaft.stream().anyMatch(
                        c -> doorways.contains(c) || excluded.contains(c))) {
                    continue;
                }
                for (Coords2D at : shaft) {
                    // Per CELL, not per column: an earlier layout that already claimed this cell
                    // has drawn it, and emitting again would be a second real write into open air.
                    // At thickness 1 this is byte-for-byte the behaviour that came before.
                    if (!taken.add(at)) {
                        continue;
                    }
                    emitColumn(at.getX(), at.getY(), floorY, interiorRows, layout.entry(), out);
                }
            }
        }
        this.occupied = taken;
    }

    /**
     * The floor-local cells one column occupies, or empty when the shaft will not fit the interior.
     *
     * <p>Interior-local -> floor-local: the interior starts one cell in from the room's origin on
     * both axes. Doing the shift here, once, is why a layout never has to know the wall ring
     * exists.</p>
     *
     * <p>{@code -(thickness - 1) / 2} puts an odd shaft exactly on its anchor and leans an even one
     * toward +x/+z. An even shaft in an odd room is off-centre by half a cell whatever this does;
     * leaning consistently at least makes it the SAME half-cell everywhere.</p>
     */
    private static List<Coords2D> shaftCells(RoomData room, Coords2D anchor, int thickness,
                                             int interiorWidth, int interiorDepth) {
        int start = -(thickness - 1) / 2;
        List<Coords2D> cells = new ArrayList<>(thickness * thickness);
        for (int dx = start; dx < start + thickness; dx++) {
            for (int dz = start; dz < start + thickness; dz++) {
                int x = anchor.getX() + dx;
                int z = anchor.getY() + dz;
                if (x < 0 || z < 0 || x >= interiorWidth || z >= interiorDepth) {
                    return List.of();
                }
                cells.add(new Coords2D(room.getOriginX() + 1 + x, room.getOriginZ() + 1 + z));
            }
        }
        return cells;
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
