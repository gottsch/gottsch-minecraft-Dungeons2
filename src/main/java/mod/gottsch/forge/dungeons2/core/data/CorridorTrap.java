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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One corridor trap option (#108 stage 3): a trench across the full width of a straight run.
 *
 * <ul>
 *   <li>{@code kind} &mdash; {@link #HAZARD}, an open spiked trench you jump; or {@link #FALSE_FLOOR},
 *       the same trench lidded with a crumbling copy of the corridor's floor.</li>
 *   <li>{@code length} &mdash; cells along the run. 2 is a sprint-jump; 3 is a real risk.</li>
 *   <li>{@code depth} &mdash; below the LOCAL floor (which may itself be sunk), clamped to the floor's
 *       {@code sink_offset} band like every pit.</li>
 *   <li>{@code spikeBlock}/{@code spikeProperties}/{@code spikeProbability} &mdash; as a room hazard.</li>
 *   <li>{@code rimBlock} &mdash; {@code hazard} only: the cells either side of the trench, as a tell.</li>
 * </ul>
 *
 * <p>Pure POJO &mdash; block ids are strings, resolved at render. The planner reads the geometry;
 * the generator reads the materials.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public record CorridorTrap(String kind, int weight, int length, int depth, Optional<String> spikeBlock,
                           Map<String, String> spikeProperties, double spikeProbability,
                           Optional<String> rimBlock) {

    public static final String HAZARD = "hazard";
    public static final String FALSE_FLOOR = "false_floor";

    public boolean isFalseFloor() {
        return FALSE_FLOOR.equals(kind);
    }

    /** A style's traps: rolled once per corridor at {@code chance}, then one option by weight. */
    public record Options(double chance, List<CorridorTrap> options) {}
}
