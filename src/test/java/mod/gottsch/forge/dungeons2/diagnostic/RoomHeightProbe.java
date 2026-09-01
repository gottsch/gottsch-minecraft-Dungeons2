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
package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomHeightBand;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The footprint and height distribution of procedural rooms &mdash; the measurement backlog #51
 * asked for before its design ("re-measure, do not reason"), and the one to re-run whenever the
 * taper or the size tiers move.
 *
 * <h2>Why the shipped table cannot be read off the JSON</h2>
 * <p>A band's share of the dungeon is the share of rooms whose long side falls in it, and that is a
 * property of the maze's room placer, not of the config. The numbers this reports are what made the
 * taper's shape decidable:</p>
 * <ul>
 *   <li><strong>the smallest procedural room is 7x7, not 5x5.</strong> Every dimension is odd and
 *       at least 7, so the "5x5 chimney" the backlog warned severing the coupling would let through
 *       cannot occur &mdash; there is no 5x5;</li>
 *   <li>7x7 is <em>26%</em> of all rooms, which is why the old {@code max(width, depth)} cap piled
 *       31.6% of heights on exactly 7; and</li>
 *   <li>rooms with a long side of 10+ &mdash; 56% of them, including every 19x19 &mdash; were
 *       entirely <em>unconstrained</em> by the old rule. "Only big rooms can be tall" was literally
 *       true, and it is the half of the distribution a raised roll ceiling (#29) would have grown
 *       into.</li>
 * </ul>
 *
 * <h2>It reports, it does not gate</h2>
 * <p>The only assertion is that the sample is real and every room obeys its band. The distribution
 * itself is deliberately unasserted: it is calibration input for the scheme gates (whose
 * {@code min_height} thresholds were tuned against it) rather than a contract, and a probe that
 * fails when a tuning number moves is a probe people delete.</p>
 */
class RoomHeightProbe {

    private static final int DUNGEONS = 400;

    @Test
    void measure() {
        Map<Integer, Integer> shortSide = new TreeMap<>();
        Map<Integer, Integer> longSide = new TreeMap<>();
        Map<Integer, Integer> height = new TreeMap<>();
        Map<String, Integer> joint = new TreeMap<>();
        Map<String, Integer> byBand = new TreeMap<>();
        int rooms = 0;

        for (DungeonSize size : DungeonSize.values()) {
            for (int i = 0; i < DUNGEONS; i++) {
                // Spread, not sequential -- see reference_first_draw_seed_correlation.
                long seed = 0xD2_5100_0001L + i * 7919L;
                Optional<DungeonLayout> planned = new DungeonStackPlanner(
                        seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                        .withSize(size).plan();
                if (planned.isEmpty()) {
                    continue;
                }
                for (FloorLayout floor : planned.get().getFloors()) {
                    for (RoomData room : floor.getRooms()) {
                        // NORMAL only: START/END are covered by template pieces whose height is
                        // authored, so including them would dilute the rolled distribution.
                        if (room.getRole() != RoomRole.NORMAL) {
                            continue;
                        }
                        rooms++;
                        int s = Math.min(room.getWidth(), room.getDepth());
                        int l = Math.max(room.getWidth(), room.getDepth());
                        shortSide.merge(s, 1, Integer::sum);
                        longSide.merge(l, 1, Integer::sum);
                        height.merge(room.getHeight(), 1, Integer::sum);
                        joint.merge(s + "x" + l, 1, Integer::sum);

                        RoomHeightBand band = RoomHeightBand.forLongSide(
                                DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS, l);
                        byBand.merge(band.maxLongSide().map(String::valueOf).orElse("rest")
                                + " -> " + band.minHeight() + ".." + band.maxHeight(), 1, Integer::sum);
                        assertTrue(room.getHeight() >= band.minHeight()
                                        && room.getHeight() <= band.maxHeight(),
                                "room " + s + "x" + l + " is " + room.getHeight() + " tall, outside "
                                        + "its band " + band.minHeight() + ".." + band.maxHeight());
                    }
                }
            }
        }

        System.out.println("=== #51 room height taper, " + rooms + " procedural rooms ===");
        System.out.println("short side : " + pct(shortSide, rooms));
        System.out.println("long side  : " + pct(longSide, rooms));
        System.out.println("height     : " + pct(height, rooms));
        System.out.println("band share : " + pct(byBand, rooms));
        System.out.println("footprint  : " + pct(joint, rooms));

        assertTrue(rooms > 10_000, "expected a meaningful sample, saw " + rooms);
    }

    private static <K> String pct(Map<K, Integer> counts, int total) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) ->
                sb.append(k).append(':').append(String.format("%.1f%% ", 100.0 * v / total)));
        return sb.toString().trim();
    }
}
