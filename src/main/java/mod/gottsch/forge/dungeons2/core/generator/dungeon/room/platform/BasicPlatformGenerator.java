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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform;

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry.PlatformEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomInterior;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a room's raised platforms.
 *
 * <h2>A dais is blocks in the air, not a raised floor</h2>
 * <p>Everything here goes in the row above the finished floor ({@code floorY + 1}), which is
 * interior air {@code RoomVolumeGenerator} has already cleared &mdash; the same cells a column
 * stands in. That is why this runs late and why the floor generator never learns platforms exist: a
 * dais layers over a decorated floor rather than replacing part of it.</p>
 *
 * <h2>Dropped whole at a doorway</h2>
 * <p>A dais whose footprint touches a doorway approach is skipped entirely, the same rule columns
 * follow. Half a platform across a doorway is worse than none, and a step up into a door frame is
 * worse still.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public class BasicPlatformGenerator implements IDungeonPlatformGenerator {

    private List<PlatformLayout> layouts = List.of();
    private Set<Coords2D> occupied = Set.of();

    public BasicPlatformGenerator withPlatformLayouts(List<PlatformLayout> layouts) {
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
        if (interiorWidth < 1 || interiorDepth < 1 || room.getHeight() - 2 < 1) {
            return;
        }

        Set<Coords2D> doorways = RoomInterior.cellsInsideDoorways(room);
        Set<Coords2D> taken = new LinkedHashSet<>();

        for (PlatformLayout layout : layouts) {
            PlatformEntry entry = layout.entry();
            int half = entry.size() / 2;
            for (Coords2D at : layout.provider().footprint(interiorWidth, interiorDepth)) {
                Map<Coords2D, String> dais = plan(at, half, entry, room, interiorWidth, interiorDepth);
                // The whole footprint has to be clear: inside the interior, off every doorway
                // approach, out of the pit (#58), and not already taken by an earlier platform.
                // All-or-nothing on purpose -- a dais with a bite out of it over a hole is worse
                // than no dais, where a colonnade missing one column is still a colonnade.
                if (dais == null || dais.keySet().stream().anyMatch(
                        cell -> doorways.contains(cell) || excluded.contains(cell)
                                || taken.contains(cell))) {
                    continue;
                }
                emit(dais, at, entry, room, floorY, out);
                taken.addAll(dais.keySet());
            }
        }
        this.occupied = taken;
    }

    /**
     * The dais's cells in floor-local coords, each mapped to the block that goes there, or
     * {@code null} when the dais will not fit inside the interior at all.
     */
    private Map<Coords2D, String> plan(Coords2D centre, int half, PlatformEntry entry,
                                       RoomData room, int interiorWidth, int interiorDepth) {
        if (centre.getX() - half < 0 || centre.getX() + half > interiorWidth - 1
                || centre.getY() - half < 0 || centre.getY() + half > interiorDepth - 1) {
            return null;
        }
        Map<Coords2D, String> cells = new HashMap<>();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                boolean onEdge = Math.abs(dx) == half || Math.abs(dz) == half;
                boolean onCorner = Math.abs(dx) == half && Math.abs(dz) == half;
                String block;
                if (dx == 0 && dz == 0) {
                    block = entry.centreBlockOrBase();
                } else if (onEdge && !onCorner && half > 0) {
                    // The straight runs of the outer ring are the steps up; the corners stay full
                    // blocks because a stair there would have to face two ways at once.
                    block = entry.stairBlockOrBase();
                } else {
                    block = entry.block();
                }
                cells.put(new Coords2D(room.getOriginX() + 1 + centre.getX() + dx,
                        room.getOriginZ() + 1 + centre.getY() + dz), block);
            }
        }
        return cells;
    }

    private void emit(Map<Coords2D, String> dais, Coords2D centre, PlatformEntry entry,
                      RoomData room, int floorY, List<BlockPlacement> out) {
        int centreX = room.getOriginX() + 1 + centre.getX();
        int centreZ = room.getOriginZ() + 1 + centre.getY();
        String stair = entry.stairBlockOrBase();

        for (Map.Entry<Coords2D, String> cell : dais.entrySet()) {
            Map<String, String> properties = entry.properties();
            if (stair.equals(cell.getValue()) && entry.orient() != SurfaceOrient.NONE) {
                Direction facing = facing(cell.getKey(), centreX, centreZ, entry.orient());
                if (facing != null) {
                    properties = new HashMap<>(properties);
                    properties.put("facing", facing.getSerializedName());
                }
            }
            out.add(new BlockPlacement(cell.getKey().getX(), floorY + 1, cell.getKey().getY(),
                    cell.getValue(), properties));
        }

        entry.topBlock().ifPresent(top -> out.add(new BlockPlacement(
                centreX, floorY + 2, centreZ, top, entry.topPropertiesOrBase())));
    }

    /**
     * Which way a step faces. {@link SurfaceOrient#INWARD} points a vanilla stair's solid half at
     * the dais centre, so its low edge meets the room and a player walks up.
     *
     * <p>The names describe where a <strong>vanilla</strong> block's solid side ends up.
     * {@code dungeonblocks}' directional trim is modelled facing-inverted, so the same visual result
     * needs the opposite value there -- the same note {@code CourseOrient} and {@code SurfaceOrient}
     * both carry, and the reason this is authored rather than inferred.</p>
     */
    private static Direction facing(Coords2D cell, int centreX, int centreZ, SurfaceOrient orient) {
        int dx = cell.getX() - centreX;
        int dz = cell.getY() - centreZ;
        Direction outward;
        if (Math.abs(dx) > Math.abs(dz)) {
            outward = dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (Math.abs(dz) > Math.abs(dx)) {
            outward = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            return null; // a corner or the centre: no single outward direction
        }
        return orient == SurfaceOrient.OUTWARD ? outward : outward.getOpposite();
    }

    @Override
    public Set<Coords2D> occupiedFloorCells() {
        return occupied;
    }
}
