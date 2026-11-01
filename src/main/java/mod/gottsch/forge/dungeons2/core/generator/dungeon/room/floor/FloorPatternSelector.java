/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.floor.PlainFloorPattern;

import java.util.Optional;

/**
 * Resolves a scheme's floor slot, the motif-or-stratum default under it, and the plain floor under
 * that, into the {@link IDungeonFloorGenerator} a room actually draws with.
 *
 * <h2>What is left of this class</h2>
 * <p>It used to hold the {@code switch} over every {@code type} string, and the block resolution
 * and degrade-to-plain rules for each. All of that moved onto the patterns themselves when they
 * became registry entries &mdash; a pattern now builds its own generator, so adding one no longer
 * means editing this file, which is the whole point of the registry. What remains is the one thing
 * that genuinely belongs here: <strong>precedence</strong>.</p>
 */
public class FloorPatternSelector {

    /**
     * The generator for a scheme's floor slot, resolved against the motif-or-stratum default
     * underneath it. Three tiers, first match wins:
     *
     * <ol>
     *   <li>the <strong>scheme's</strong> own {@code floor} entry, when it has one &mdash; a room
     *       that asked for a mosaic asked for it at every depth, so a band never overrides it;</li>
     *   <li>the {@link FloorConfig}'s own {@code pattern}, i.e. what this <strong>motif or
     *       stratum</strong> paves with by default. This is what lets the mud band ship speckled
     *       cobble without every scheme having to name it;</li>
     *   <li>{@link #plain} &mdash; the {@code base}/{@code alternateBase} roll, unchanged.</li>
     * </ol>
     *
     * <p>This is the ONLY place the two are combined. A nested {@code generators} entry inside a
     * composite resolves through the pattern itself and so cannot reach back into the band
     * default.</p>
     */
    public static IDungeonFloorGenerator generatorFor(Optional<FloorPatternEntry> entry, FloorConfig config) {
        return entry.or(config::pattern)
                .map(e -> e.pattern().generator(config))
                .orElseGet(() -> plain(config));
    }

    /** The plain floor, carrying the motif's own base blocks. */
    public static IDungeonFloorGenerator plain(FloorConfig config) {
        return PlainFloorPattern.INSTANCE.generator(config);
    }

    /** The generator one authored entry draws, ignoring precedence. */
    public static IDungeonFloorGenerator toGenerator(FloorPatternEntry entry, FloorConfig config) {
        return entry.pattern().generator(config);
    }
}
