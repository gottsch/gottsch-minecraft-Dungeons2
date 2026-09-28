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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The depth of every cell of one sinking corridor (#108): a distance field from its doors.
 *
 * <p>Every cell beside a door or connector is an <em>anchor</em> at depth 0, cells within
 * {@code landing} of an anchor stay level, and beyond that the floor steps down one block every
 * {@code run} cells until {@code maxDepth}. So a run between two doors sinks and comes back up, a
 * dead end is the low point, and a run too short to clear its landings stays flat &mdash; none of
 * which is a special case.</p>
 *
 * <h2>The three invariants, and where each comes from</h2>
 * <ol>
 *   <li><strong>Doors are level.</strong> Anchors are distance 0 and the landing is at least 1.</li>
 *   <li><strong>No step is taller than one block</strong> between 4-neighbours. A BFS distance
 *       changes by at most 1 between neighbours, and so does {@code ceil(k / run)}. Arithmetic, not
 *       convention &mdash; the same reason a terraced pit is.</li>
 *   <li><strong>No cell is a trough</strong>, i.e. higher on both sides of one axis. A riser is a
 *       stair and a stair faces one way; a one-cell trough would need to face two. {@link #smooth}
 *       raises such cells, and re-applies (2) as it goes, until nothing moves.</li>
 * </ol>
 *
 * <h2>Distance is 8-connected</h2>
 * <p>A door sits in the middle of a 3-wide corridor. Measured 4-connected, the side cells are one
 * further from it than the centre, so every riser comes out as a chevron and every chevron cell
 * wants an inner stair. Measured 8-connected, a straight run's cross-section is equidistant and the
 * riser is a straight row of stairs. 4-neighbours are 8-neighbours, so (2) still holds.</p>
 *
 * <p>Pure &mdash; no Minecraft imports, no randomness. The planner rolls whether and how deep.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class CorridorDescentField {

    private static final int[][] ORTHOGONAL = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private CorridorDescentField() {}

    /**
     * @param cells    the corridor's cells, in {@code CorridorData} order; the result is parallel
     * @param anchors  the subset that must be level: every cell beside a door or connector
     * @param maxDepth the deepest any cell may go, already clamped to the floor's sink band
     * @param run      cells per one-block step, at least 1
     * @param landing  cells kept level beyond each anchor, at least 1
     * @return each cell's depth below the walking plane; all zeros when nothing can sink
     */
    public static int[] compute(List<Coords2D> cells, Set<Coords2D> anchors,
                                int maxDepth, int run, int landing) {
        return compute(cells, anchors, maxDepth, run, landing, Integer.MAX_VALUE);
    }

    /**
     * As above, with every cell WIDER than {@code maxWidth} held level too (Mark, 2026-09-28: "where
     * multiple corridors come together, or it's widened, shouldn't have the descent").
     *
     * <p>A cell's width is the shorter of its two straight-line extents through the corridor &mdash;
     * the run of corridor cells along its row, and along its column. A straight 3-wide run is 3 across
     * however long it is; a junction, a turn's corner square and a stretch dilation widened are wider
     * than that both ways. Such cells join the anchors, so they also get a {@code landing} and the
     * descent happens only in the plain runs between doors and junctions.</p>
     *
     * @param maxWidth the corridor width the planner carved; {@link Integer#MAX_VALUE} for no limit
     */
    public static int[] compute(List<Coords2D> cells, Set<Coords2D> anchors,
                                int maxDepth, int run, int landing, int maxWidth) {
        int[] depths = new int[cells.size()];
        if (maxDepth <= 0 || anchors.isEmpty() || cells.isEmpty()) {
            return depths;
        }
        Map<Coords2D, Integer> index = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            index.put(cells.get(i), i);
        }
        if (maxWidth != Integer.MAX_VALUE) {
            Set<Coords2D> held = new java.util.HashSet<>(anchors);
            for (Coords2D c : cells) {
                if (Math.min(extent(c, 1, 0, index), extent(c, 0, 1, index)) > maxWidth) {
                    held.add(c);
                }
            }
            anchors = held;
        }

        int[] distance = distances(cells, anchors, index);
        int step = Math.max(1, run);
        int level = Math.max(1, landing);
        for (int i = 0; i < depths.length; i++) {
            // Unreachable from any door (distance -1) stays level: there is nothing to descend FROM.
            int beyond = distance[i] - level;
            depths[i] = distance[i] < 0 || beyond <= 0 ? 0 : Math.min(maxDepth, (beyond + step - 1) / step);
        }
        smooth(cells, depths, index);
        return depths;
    }

    /** How many corridor cells in an unbroken line through {@code c} along ({@code dx},{@code dz}). */
    private static int extent(Coords2D c, int dx, int dz, Map<Coords2D, Integer> index) {
        int n = 1;
        for (int s = 1; index.containsKey(new Coords2D(c.getX() + dx * s, c.getY() + dz * s)); s++) n++;
        for (int s = 1; index.containsKey(new Coords2D(c.getX() - dx * s, c.getY() - dz * s)); s++) n++;
        return n;
    }

    /** Multi-source BFS over the corridor's own cells, 8-connected. -1 for a cell no anchor reaches. */
    private static int[] distances(List<Coords2D> cells, Set<Coords2D> anchors, Map<Coords2D, Integer> index) {
        int[] distance = new int[cells.size()];
        java.util.Arrays.fill(distance, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        // In list order, not set order, so the walk never depends on a hash.
        for (int i = 0; i < cells.size(); i++) {
            if (anchors.contains(cells.get(i))) {
                distance[i] = 0;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            Coords2D c = cells.get(i);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Integer n = index.get(new Coords2D(c.getX() + dx, c.getY() + dz));
                    if (n != null && distance[n] < 0) {
                        distance[n] = distance[i] + 1;
                        queue.add(n);
                    }
                }
            }
        }
        return distance;
    }

    /**
     * Raises troughs and re-levels until nothing moves. Only ever RAISES a cell (lowers its depth),
     * so anchors and landings stay at 0 and the loop terminates: every pass that changes anything
     * takes the total depth down by at least one.
     */
    static void smooth(List<Coords2D> cells, int[] depths, Map<Coords2D, Integer> index) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < depths.length; i++) {
                if (depths[i] == 0) continue;
                Coords2D c = cells.get(i);
                int shallowest = Integer.MAX_VALUE;
                boolean[] higher = new boolean[4];
                for (int k = 0; k < ORTHOGONAL.length; k++) {
                    Integer n = index.get(new Coords2D(c.getX() + ORTHOGONAL[k][0], c.getY() + ORTHOGONAL[k][1]));
                    if (n == null) continue;
                    shallowest = Math.min(shallowest, depths[n]);
                    higher[k] = depths[n] < depths[i];
                }
                int target = depths[i];
                // A neighbour two or more above: pull up to one below it.
                if (shallowest != Integer.MAX_VALUE && target > shallowest + 1) {
                    target = shallowest + 1;
                }
                // Higher on both sides of an axis: no single stair can face both ways.
                if ((higher[0] && higher[1]) || (higher[2] && higher[3])) {
                    target = Math.min(target, depths[i] - 1);
                }
                if (target != depths[i]) {
                    depths[i] = Math.max(0, target);
                    changed = true;
                }
            }
        }
    }
}
