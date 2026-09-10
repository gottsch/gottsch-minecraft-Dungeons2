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

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.wall.CoursesWallPattern;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often a room actually gets <strong>projecting crown moulding</strong>, and where the ones
 * that miss are lost.
 *
 * <p>Mark, 2026-09-09, after walking a dungeon: <em>"for the projecting cornice, I saw about 1 room
 * in the dungeon. the instance rate needs to be greatly increased."</em> The cornice is not one
 * knob &mdash; a room reaches it only by rolling a scheme that HAS a wall slot, then rolling the
 * crown option out of that slot, then clearing the course's own {@code min_height}. Three
 * multiplied gates, and guessing which one is doing the damage is how you tune the wrong one. So
 * this reports the survival rate at each stage separately.</p>
 *
 * <p>Floor 0 is excluded: it is the mud band's, whose schemes are its own and carry no stone
 * cornice by design.</p>
 */
class CorniceIncidenceProbe {

    private static final int DUNGEONS = 60;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void howOftenDoesARoomGetProjectingCrownMoulding() {
        MotifConfig config = MotifConfigs.load("classic");
        // The motif tier is depth-independent below the mud band -- bands 1 and 2 declare no wall
        // -- so one projection is enough, and forFloor(1) is where a $role becomes a block id.
        Optional<WallPatternEntry> motifWall = MotifConfigs.load("classic").forFloor(1).wall().pattern();

        int rooms = 0;
        int withWallSlot = 0;   // the rolled scheme filled its wall slot at all
        int withCourses = 0;    // ... and that wall carries a courses pattern
        int withCornice = 0;    // ... and one of those courses projects
        Map<String, int[]> byScheme = new TreeMap<>();   // [rooms, cornices]
        Map<Integer, int[]> byHeight = new TreeMap<>();
        Map<Integer, int[]> byMinSide = new TreeMap<>();

        for (int i = 0; i < DUNGEONS; i++) {
            long seed = 0xD2_0BADC0DEL + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM).plan();
            if (planned.isEmpty()) {
                continue;
            }
            RandomSource random = RandomSource.create(seed);
            for (FloorLayout floor : planned.get().getFloors()) {
                if (floor.getFloorIndex() == 0) {
                    continue;
                }
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() != RoomRole.NORMAL) {
                        continue;
                    }
                    int w = room.getWidth();
                    int d = room.getDepth();
                    int h = room.getHeight();
                    rooms++;

                    RoomScheme scheme = RoomSchemeSelector.select(
                            config.schemes(), w, d, h, floor.getFloorIndex(), random);
                    int[] tally = byScheme.computeIfAbsent(scheme.name(), k -> new int[2]);
                    tally[0]++;
                    byHeight.computeIfAbsent(h, k -> new int[2])[0]++;
                    byMinSide.computeIfAbsent(Math.min(w, d), k -> new int[2])[0]++;

                    Optional<WallPatternEntry> wall = scheme.wallFor(w, d, h);
                    if (wall.isPresent()) {
                        withWallSlot++;
                    }
                    boolean courses = false;
                    boolean cornice = false;
                    // BOTH TIERS, composed the way WallPatternSelector does it -- the motif's own
                    // wall pattern first, the scheme's on top. Reading only the scheme's slot is
                    // what made the first cut of this probe report 16.6% when the plinth, which
                    // lives on the motif tier, was already in every room.
                    List<Optional<WallPatternEntry>> tiers =
                            List.of(motifWall.filter(e -> e.gate().fits(w, d, h)), wall);
                    for (Optional<WallPatternEntry> tier : tiers) {
                        if (tier.isEmpty()) {
                            continue;
                        }
                        // forRoom, not the raw entry: a course carries its own gate, and the whole
                        // point of this probe is that the course-level gate is invisible upstream.
                        for (WallPatternEntry.PatternEntry entry : tier.get().forRoom(w, d, h).patterns()) {
                            if (!(entry.pattern() instanceof CoursesWallPattern pattern)) {
                                continue;
                            }
                            courses = true;
                            for (WallPatternEntry.CourseEntry course : pattern.courses()) {
                                if (course.projection() > 0) {
                                    cornice = true;
                                }
                            }
                        }
                    }
                    if (courses) {
                        withCourses++;
                    }
                    if (cornice) {
                        withCornice++;
                        tally[1]++;
                        byHeight.get(h)[1]++;
                        byMinSide.get(Math.min(w, d))[1]++;
                    }
                }
            }
        }

        System.out.printf("%n=== projecting cornice, %d MEDIUM dungeons, %d NORMAL rooms below floor 0 ===%n",
                DUNGEONS, rooms);
        System.out.printf("  the scheme filled its wall slot    %5d (%.1f%%)%n",
                withWallSlot, 100.0 * withWallSlot / rooms);
        System.out.printf("  courses from EITHER tier           %5d (%.1f%%)%n",
                withCourses, 100.0 * withCourses / rooms);
        System.out.printf("  ... with a PROJECTING course       %5d (%.1f%%)%n",
                withCornice, 100.0 * withCornice / rooms);
        System.out.printf("  rooms per MEDIUM dungeon with one  %.1f%n",
                (double) withCornice / DUNGEONS);

        System.out.printf("%n  scheme                 rooms  cornice%n");
        byScheme.forEach((name, v) -> System.out.printf("  %-20s %5d %5d  (%5.1f%%)%n",
                name, v[0], v[1], 100.0 * v[1] / v[0]));
        System.out.printf("%n  height                 rooms  cornice%n");
        byHeight.forEach((k, v) -> System.out.printf("      %2d              %5d %5d  (%5.1f%%)%n",
                k, v[0], v[1], 100.0 * v[1] / v[0]));
        System.out.printf("%n  min(width,depth)       rooms  cornice%n");
        byMinSide.forEach((k, v) -> System.out.printf("      %2d              %5d %5d  (%5.1f%%)%n",
                k, v[0], v[1], 100.0 * v[1] / v[0]));

        assertTrue(rooms > 50, "need a meaningful sample");
    }
}
