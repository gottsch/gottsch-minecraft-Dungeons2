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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Where a corridor trap's trench goes (#108 stage 3): straight across a plain, level stretch.
 *
 * <p>A trench is {@code length} consecutive CROSS-SECTIONS of a straight run &mdash; the full width of
 * the corridor, so it cannot be walked round, only crossed. A position qualifies when:</p>
 * <ul>
 *   <li>the trench's sections AND one approach section either side are the same span, and no
 *       wider than the carved width &mdash; a straight run, not a junction, a turn or a widening;</li>
 *   <li>every one of those cells is at the same depth, so nobody jumps off a stair into it or lands
 *       on one out of it;</li>
 *   <li>none of them is blocked (the planner passes every cell beside a door or connector);</li>
 *   <li>the trench is still at least two deep once clamped to the floor's band.</li>
 * </ul>
 *
 * <p>Pure &mdash; no Minecraft imports; the Random is the planner's, salted per corridor.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class CorridorTrapPlacer {

    /** The shallowest trench worth digging: one deep is a step, not a fall. */
    public static final int MIN_FALL = 2;

    private CorridorTrapPlacer() {}

    /** A chosen trench: its cells and how far below the walking plane its floor sits. */
    public record Trench(List<Coords2D> cells, int floorDepth) {}

    /**
     * @param depthOf    each cell's descent depth (0 when level)
     * @param blocked    cells that may be neither trench nor approach
     * @param maxWidth   the carved corridor width
     * @param length     sections along the run
     * @param trapDepth  authored depth below the local floor
     * @param sinkOffset the floor's band; the trench floor never goes below it
     * @return the trench, or {@code null} when this corridor has nowhere to put one
     */
    public static Trench place(List<Coords2D> cells, ToIntFunction<Coords2D> depthOf, Set<Coords2D> blocked,
                               int maxWidth, int length, int trapDepth, int sinkOffset, Random random) {
        Set<Coords2D> set = new HashSet<>(cells);
        List<Trench> candidates = new ArrayList<>();
        // X-running then Z-running, in list order: the draw is a function of the corridor alone.
        int[][] axes = {{1, 0}, {0, 1}};
        for (int[] along : axes) {
            int ax = along[0];
            int az = along[1];
            // Across the run is the other axis.
            int cx = az;
            int cz = ax;
            for (Coords2D start : cells) {
                // One candidate per section: only from the section's low end.
                if (set.contains(new Coords2D(start.getX() - cx, start.getY() - cz))) continue;
                int width = 1;
                while (set.contains(new Coords2D(start.getX() + cx * width, start.getY() + cz * width))) width++;
                if (width > maxWidth) continue;
                int depth = depthOf.applyAsInt(start);
                int floorDepth = Math.min(sinkOffset, depth + trapDepth);
                if (floorDepth - depth < MIN_FALL) continue;

                List<Coords2D> trench = new ArrayList<>();
                boolean ok = true;
                // k = -1 and k = length are the approaches; 0..length-1 is the trench.
                for (int k = -1; k <= length && ok; k++) {
                    int sx = start.getX() + ax * k;
                    int sz = start.getY() + az * k;
                    // The section must be exactly this span: bounded by walls at both ends.
                    if (set.contains(new Coords2D(sx - cx, sz - cz))
                            || set.contains(new Coords2D(sx + cx * width, sz + cz * width))) {
                        ok = false;
                        break;
                    }
                    for (int w = 0; w < width; w++) {
                        Coords2D c = new Coords2D(sx + cx * w, sz + cz * w);
                        if (!set.contains(c) || blocked.contains(c) || depthOf.applyAsInt(c) != depth) {
                            ok = false;
                            break;
                        }
                        if (k >= 0 && k < length) {
                            trench.add(c);
                        }
                    }
                }
                if (ok) {
                    candidates.add(new Trench(trench, floorDepth));
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
