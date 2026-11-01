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

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The two digging profiles, offered to providers so each does not reinvent them.
 *
 * <p>Shared rather than shipped in a base class: a provider is free to compute its depths any way
 * it likes, and these are simply the two that shipped content wants.</p>
 *
 * @author Mark Gottschling on Aug 27, 2026
 */
public final class PitPlans {

    private PitPlans() {}

    /**
     * A sunken court: depth is a cell's distance from the edge of the footprint, capped at
     * {@code maxDepth}, so the pit descends one block per ring inward.
     *
     * <p><strong>Every step is exactly one block</strong>, which is what makes a court walkable at
     * all &mdash; a player can only jump onto a block one high, so a two-block step anywhere is a
     * place they fall in and cannot climb out of. That is not an authoring convention here, it is
     * arithmetic: a distance field changes by at most one between neighbours, and capping preserves
     * that.</p>
     *
     * <p><strong>{@code maxDepth} is a ceiling, not a promise.</strong> A footprint too narrow to
     * hold that many rings reaches its own middle and stops &mdash; a 3x3 court is two deep however
     * deep it was asked to be. The alternative is a stepped rim around a sheer drop, which is the
     * trap this profile exists to avoid.</p>
     *
     * <p>Neighbours are the four orthogonal ones, not the eight: a diagonal across a corner is not
     * a step a player can take, and 8-adjacency would let a corner cell claim a shallower level
     * than the cells you must actually walk through to reach it.</p>
     */
    public static Map<Coords2D, Integer> terraced(Set<Coords2D> footprint, int maxDepth) {
        Map<Coords2D, Integer> depths = new HashMap<>();
        if (maxDepth < 1) {
            return depths;
        }
        Deque<Coords2D> queue = new ArrayDeque<>();
        for (Coords2D cell : footprint) {
            if (!neighbours(cell).stream().allMatch(footprint::contains)) {
                depths.put(cell, 1); // touches the rim, so it is the first step down
                queue.add(cell);
            }
        }
        while (!queue.isEmpty()) {
            Coords2D cell = queue.poll();
            int next = depths.get(cell) + 1;
            if (next > maxDepth) {
                continue;
            }
            for (Coords2D neighbour : neighbours(cell)) {
                if (footprint.contains(neighbour) && !depths.containsKey(neighbour)) {
                    depths.put(neighbour, next);
                    queue.add(neighbour);
                }
            }
        }
        // A cell the sweep never reached sits inside a footprint wider than maxDepth: it is the
        // flat bottom of the court.
        for (Coords2D cell : footprint) {
            depths.putIfAbsent(cell, maxDepth);
        }
        return depths;
    }

    /**
     * A sheer shaft: every cell at the full depth, with walls straight down.
     *
     * <p><strong>Deliberately not walkable</strong>, and that is the feature &mdash; anything two or
     * more deep is something a player falls into and cannot climb out of. A provider choosing this
     * is choosing a hazard, and should be named so an author knows it before they place one.</p>
     */
    public static Map<Coords2D, Integer> sheer(Set<Coords2D> footprint, int depth) {
        Map<Coords2D, Integer> depths = new HashMap<>();
        if (depth < 1) {
            return depths;
        }
        for (Coords2D cell : footprint) {
            depths.put(cell, depth);
        }
        return depths;
    }

    /**
     * A ring of stairs on the cells just OUTSIDE the footprint, at the room's own walking plane.
     *
     * <p>This is what turns a one-block drop into a step you can see. The floor's top sits one above
     * the pit's; a stair in between contributes a half, so a player descends floor &rarr; half
     * &rarr; pit instead of falling off a kerb. The stairs are NOT part of the pit &mdash; those
     * cells stay walkable floor and are never claimed.</p>
     *
     * <p><strong>Corners are left plain.</strong> A stair on a diagonal would have to face two ways
     * at once; the dais generator makes the same call for the same reason.</p>
     *
     * <p>{@code orient} names where a <strong>vanilla</strong> stair's solid half ends up, matching
     * {@code SurfaceOrient} everywhere else. {@code OUTWARD} puts the solid half away from the pit,
     * so the low edge meets the pit and a player walks down into it. {@code dungeonblocks}' trim is
     * modelled facing-inverted, so the same look needs the opposite value there &mdash; which is
     * why this is authored rather than inferred.</p>
     */
    public static Map<Coords2D, BlockState> stairRim(Set<Coords2D> footprint, BlockState stair,
                                                     SurfaceOrient orient) {
        Map<Coords2D, BlockState> rim = new HashMap<>();
        if (footprint.isEmpty() || orient == SurfaceOrient.NONE) {
            return rim;
        }
        int minX = footprint.stream().mapToInt(Coords2D::getX).min().orElseThrow();
        int maxX = footprint.stream().mapToInt(Coords2D::getX).max().orElseThrow();
        int minZ = footprint.stream().mapToInt(Coords2D::getY).min().orElseThrow();
        int maxZ = footprint.stream().mapToInt(Coords2D::getY).max().orElseThrow();

        for (Coords2D cell : footprint) {
            for (Coords2D neighbour : neighbours(cell)) {
                if (footprint.contains(neighbour) || rim.containsKey(neighbour)) {
                    continue;
                }
                Direction outward = outward(neighbour, minX, maxX, minZ, maxZ);
                if (outward == null) {
                    continue; // a corner: no single direction, so it stays plain floor
                }
                Direction facing = orient == SurfaceOrient.OUTWARD ? outward : outward.getOpposite();
                rim.put(neighbour, BlockStateCodec.withProperties(stair,
                        Map.of("facing", facing.getSerializedName())));
            }
        }
        return rim;
    }

    /** Which side of the pit a rim cell is on, or null on a diagonal. */
    private static Direction outward(Coords2D cell, int minX, int maxX, int minZ, int maxZ) {
        boolean west = cell.getX() < minX;
        boolean east = cell.getX() > maxX;
        boolean north = cell.getY() < minZ;
        boolean south = cell.getY() > maxZ;
        if ((west || east) && (north || south)) {
            return null;
        }
        if (west) {
            return Direction.WEST;
        }
        if (east) {
            return Direction.EAST;
        }
        if (north) {
            return Direction.NORTH;
        }
        return south ? Direction.SOUTH : null;
    }

    /**
     * A CLOSED ring of one block on every cell touching the footprint, at the room's own walking
     * plane &mdash; the lip around a shaft rather than the step down into a court.
     *
     * <p><strong>Diagonals are included here where {@link #stairRim} leaves them plain</strong>,
     * and the difference is what the two rings are FOR. A stair rim exists so a player can walk
     * down, which a corner cell cannot help with because a stair there would have to face two ways
     * at once. A block rim exists to be SEEN &mdash; it is the tell that says "this floor is not the
     * floor" before someone steps on it &mdash; and a ring with four gaps in it reads as four
     * unrelated strips rather than as an edge.</p>
     *
     * <p>The cells stay walkable and unexcavated, exactly as {@link PitPlan#rim} says: the lip is
     * the last solid ground before the drop, not part of it.</p>
     *
     * <p>No bounds check is needed. Every shipped provider keeps its footprint one cell inside the
     * interior (a trap you cannot walk past is a blocked room, not a trap), so the ring is inside
     * the interior by construction.</p>
     */
    public static Map<Coords2D, BlockState> blockRim(Set<Coords2D> footprint, BlockState block) {
        Map<Coords2D, BlockState> rim = new HashMap<>();
        if (footprint.isEmpty()) {
            return rim;
        }
        for (Coords2D cell : footprint) {
            for (Coords2D neighbour : surrounding(cell)) {
                if (footprint.contains(neighbour)) {
                    continue;
                }
                rim.put(neighbour, block);
            }
        }
        return rim;
    }

    /** The eight neighbours, for a ring that has to close rather than one you walk down. */
    private static List<Coords2D> surrounding(Coords2D cell) {
        List<Coords2D> cells = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    cells.add(new Coords2D(cell.getX() + dx, cell.getY() + dz));
                }
            }
        }
        return cells;
    }

    private static List<Coords2D> neighbours(Coords2D cell) {
        return List.of(new Coords2D(cell.getX() - 1, cell.getY()),
                new Coords2D(cell.getX() + 1, cell.getY()),
                new Coords2D(cell.getX(), cell.getY() - 1),
                new Coords2D(cell.getX(), cell.getY() + 1));
    }
}
