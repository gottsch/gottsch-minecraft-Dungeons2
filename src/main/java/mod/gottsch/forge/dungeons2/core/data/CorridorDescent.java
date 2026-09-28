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
package mod.gottsch.forge.dungeons2.core.data;

/**
 * How a corridor style sinks into its floor's {@code sink_offset} band (#108): runs that step down
 * away from their doors and back up to the next.
 *
 * <p>Geometry only. The stair a riser is built from is a <em>material</em> and is re-resolved at
 * render time like every other corridor block ({@code CorridorConfig#stepStateFor}); this record is
 * what the planner needs to decide depths, and it lives here rather than in {@code config} because
 * {@code DungeonStackPlanner} has no {@code net.minecraft} imports &mdash; the same reason
 * {@link CorridorStyleWeight} exists.</p>
 *
 * <ul>
 *   <li>{@code chance} &mdash; rolled once per corridor REGION, so a floor's corridors share a style
 *       but not all of them dip.</li>
 *   <li>{@code minDepth}..{@code maxDepth} &mdash; the deepest this corridor may go, rolled per
 *       region, and clamped to the floor's {@code sinkOffset} by the planner.</li>
 *   <li>{@code run} &mdash; cells per one-block step. 1 is a staircase, 4 a gentle slope.</li>
 *   <li>{@code landing} &mdash; cells kept level in front of every door and connector. At least 1,
 *       which is what keeps the cell diagonal to a door (and the lever sconce's face) level.</li>
 * </ul>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public record CorridorDescent(double chance, int minDepth, int maxDepth, int run, int landing) {
}
