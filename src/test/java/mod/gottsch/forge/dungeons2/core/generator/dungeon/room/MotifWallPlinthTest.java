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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.WallConfig;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAlternate;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseEntry;
import mod.gottsch.forge.dungeons2.core.config.wall.CoursesWallPattern;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.BasicWallGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall.WallPatternSelector;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The look every {@code classic} room falls back to when no scheme dresses it: stone brick walls
 * with one course of {@code left}/{@code right_large_stone_brick} along the bottom.
 *
 * <p>The 2026-09-09 do-over deleted classic's fourteen sandbox schemes and rebuilt it on two
 * ({@code chamber} and {@code hall}), both of which leave their {@code wall} slot unfilled a good
 * share of the time. So the motif tier is load bearing for the first time: before, ten of eleven
 * schemes named a {@code wall} of their own and it drew in a measured ~0% of rooms, which is the
 * reason wall tiers compose rather than replace.</p>
 *
 * <h2>Why this is two tests and not one</h2>
 * <p>The plinth cannot be seen end to end headlessly. Its blocks are {@code dungeonblocks:} ids,
 * which resolve to nothing under a bare bootstrap, and {@code CoursesWallPattern#provider} degrades
 * the <em>whole</em> entry to a plain wall when any course block will not resolve &mdash; so a run
 * of the real generator against the shipped config draws bare stone brick here, and would draw it
 * whether the config were right or wrong. This is the same limitation the shipped-scheme tests
 * documented. So the authored intent and the plumbing are asserted separately: what classic
 * declares, and that a motif-tier pattern reaches a room whose scheme names no wall at all.</p>
 *
 * @author Mark Gottschling on Sep 9, 2026
 */
class MotifWallPlinthTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int SQUARE = 11;
    private static final int HEIGHT = 7;
    private static final int FLOOR_Y = 60;
    private static final int ORIGIN = 10;

    /**
     * Floor 1, not the raw file. Bands 1 and 2 declare no wall and no palette, so the motif's own
     * apply -- and {@code forFloor} is where a {@code $role} becomes a block id, so the unprojected
     * config would carry {@code $plinth_left} through to a lookup that draws air with no error.
     * Floor 0 would read the mud band's wall, which replaces this section whole.
     */
    private static MotifConfig classic() {
        return MotifConfigs.load("classic").forFloor(1);
    }

    @Test
    void classicAuthorsOneBottomCourseOfLargeStoneBrick() {
        WallPatternEntry pattern = classic().wall().pattern().orElseThrow(() -> new AssertionError(
                "classic's motif-level wall names no pattern; with no schemes shipped, every room "
                        + "is now bare stone brick"));

        assertEquals("minecraft:stone_bricks", classic().wall().wall(),
                "the field of the wall is meant to stay plain stone brick");

        List<WallPatternEntry.PatternEntry> patterns = pattern.patterns();
        // TWO patterns of one course each, not one pattern of two courses, and the difference is
        // load bearing: CoursesWallPattern#provider degrades the WHOLE pattern to plain wall when
        // any one of its course blocks will not resolve. Sharing a pattern made the vanilla-stair
        // cornice depend on `dungeonblocks:left_large_stone_brick` resolving -- which cost every
        // room its cornice in any environment missing that block, and was caught only because it
        // took the stair-mitring guard down with it. Separate patterns degrade separately.
        assertEquals(2, patterns.size(),
                "the motif wall is a plinth pattern and a cornice pattern; merging them couples "
                        + "their degradation");
        CoursesWallPattern courses = assertInstanceOf(CoursesWallPattern.class,
                patterns.get(0).pattern(), "the plinth is authored as a courses pattern");
        assertEquals(1, courses.courses().size(), "one course: the plinth");

        CourseEntry plinth = courses.courses().get(0);
        assertEquals("dungeonblocks:left_large_stone_brick", plinth.block(),
                "$plinth_left did not resolve to the left half of the large brick");
        assertEquals(Optional.of("dungeonblocks:right_large_stone_brick"), plinth.alternateBlock(),
                "$plinth_right did not resolve to the right half");
        // The two halves are one block cut in two, so they have to pair in order along a run.
        // RANDOM would scatter left halves against left halves and read as rubble.
        assertEquals(CourseAlternate.STRICT, plinth.alternate(),
                "the two halves must alternate strictly, not randomly");
        assertEquals(CourseAnchor.BOTTOM, plinth.anchor(), "the plinth sits at the bottom");
        assertEquals(0, plinth.offset(), "on the first row above the floor, with no gap");
    }

    /**
     * The plumbing half: a motif-tier pattern must reach a room whose scheme names no wall. Uses a
     * vanilla stand-in for the plinth for the reason in the class docs -- what is under test is the
     * empty-slot composition path in {@code WallPatternSelector}, not which block is named.
     */
    @Test
    void aMotifTierCourseDrawsInARoomWithNoSchemeWall() {
        String json = "{\"wall\": \"minecraft:stone_bricks\", \"pattern\": {\"patterns\": ["
                + "{\"type\": \"dungeons2:courses\", \"config\": {\"courses\": ["
                + "{\"block\": \"minecraft:polished_andesite\"}]}}]}}";
        WallConfig substitute = WallConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, error -> {
                    throw new AssertionError("the stand-in config no longer decodes: " + error);
                });

        assertNotNull(WallPatternSelector.providerFor(
                        Optional.empty(), substitute, SQUARE, SQUARE, HEIGHT),
                "an empty scheme wall slot dropped the motif's own pattern -- with no schemes "
                        + "shipped that would be every room in the dungeon");

        Map<String, String> world = build(substitute);
        long plinthCells = world.entrySet().stream()
                .filter(cell -> cell.getKey().startsWith((FLOOR_Y + 1) + ":"))
                .filter(cell -> "minecraft:polished_andesite".equals(cell.getValue()))
                .count();
        assertTrue(plinthCells > 0,
                "the composed provider was built but drew nothing in the room's bottom course");
    }

    /**
     * The plinth's reach, now that classic ships schemes again. Every scheme rolled at any depth
     * below the mud band must either name no wall -- in which case the motif's plinth is the whole
     * treatment -- or name one that layers ABOVE it, because the two tiers compose and the scheme
     * wins any cell both claim. A scheme whose own wall put something in the bottom course would
     * take the plinth away in exactly the rooms it rolled, silently.
     */
    @Test
    void noShippedSchemeClaimsTheBottomCourseBackFromTheMotif() {
        List<RoomScheme> schemes = classic().schemes();
        assertFalse(schemes.isEmpty(), "classic ships no schemes at all");

        for (RoomScheme scheme : schemes) {
            // resolve() collapses the weighted slot alternatives (#65) to one treatment, so every
            // option has to be reachable to be checked -- hence the seed sweep rather than one draw.
            for (long seed = 0; seed < 64; seed++) {
                RoomScheme resolved = scheme.resolve(SQUARE, SQUARE, HEIGHT, RandomSource.create(seed));
                Optional<WallPatternEntry> wall = resolved.wallFor(SQUARE, SQUARE, HEIGHT);
                if (wall.isEmpty()) {
                    continue;
                }
                for (WallPatternEntry.PatternEntry entry : wall.get().patterns()) {
                    if (!(entry.pattern() instanceof CoursesWallPattern courses)) {
                        continue;
                    }
                    for (CourseEntry course : courses.courses()) {
                        assertTrue(course.anchor() != CourseAnchor.BOTTOM || course.offset() > 0,
                                "scheme '" + scheme.name() + "' authors a course at the bottom of "
                                        + "the wall, which overwrites the motif's plinth in every "
                                        + "room that rolls it");
                    }
                }
            }
        }
    }

    /**
     * A crown that actually stands proud of the wall (Mark, 2026-09-09: "i don't think any room has
     * a crown molding course").
     *
     * <p>He was right, and it was invisible in every existing check: a {@code courses} pattern with
     * {@code projection} left at its default 0 decodes, validates, gates and draws &mdash; it just
     * draws flush in the wall plane, where a cornice reads as a stripe rather than as moulding. The
     * only projecting course the whole motif shipped was the mud band's. So this asserts the thing
     * that was missing rather than the thing that was present: a course that reaches into the room.
     * </p>
     *
     * <p>The crown's blocks are all vanilla ({@code $stair} / {@code $inlay} resolve to stone brick
     * stairs and polished andesite), so unlike the plinth this one CAN be watched end to end
     * headlessly &mdash; and is, below.</p>
     */
    @Test
    void theCorniceIsOnTheMotifTierSoNearlyEveryRoomGetsOne() {
        CourseEntry cornice = classic().wall().pattern().orElseThrow().patterns().stream()
                .map(WallPatternEntry.PatternEntry::pattern)
                .filter(CoursesWallPattern.class::isInstance)
                .map(CoursesWallPattern.class::cast)
                .flatMap(pattern -> pattern.courses().stream())
                .filter(course -> course.projection() > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the motif wall authors no projecting course, so crown moulding is back to "
                                + "whatever the rolled scheme happens to carry -- measured at 16.6% "
                                + "of rooms when it lived in the scheme slots"));

        assertEquals(CourseAnchor.TOP, cornice.anchor(),
                "a projecting course anchored at the bottom is a trip hazard at floor level, not a "
                        + "cornice");
        assertEquals(0, cornice.offset(), "the cornice sits on the top wall row");
        // The one gate that decides the incidence, and the reason it is on the COURSE and not the
        // pattern: the plinth beside it belongs on every wall in the dungeon, and a slot-level
        // gate would take both away from a short room. Every height from 6 up is 100%; 5 is 11%
        // of rooms and deliberately left plain, since a plinth and a cornice in a 5-high room
        // leave exactly one undressed row between them.
        assertEquals(6, cornice.gate().minHeight(),
                "the cornice's height gate moved; re-run CorniceIncidenceProbe, because this is "
                        + "the number that decides how many rooms get one");
    }

    /**
     * And that no scheme has quietly grown its own, which would double the moulding on the top row
     * of every room that rolled it. The scheme crowns are a FRIEZE one row down, on purpose.
     */
    @Test
    void noSchemeAuthorsASecondCorniceOnTopOfTheMotifs() {
        for (RoomScheme scheme : classic().schemes()) {
            for (long seed = 0; seed < 64; seed++) {
                RoomScheme resolved = scheme.resolve(SQUARE, SQUARE, HEIGHT, RandomSource.create(seed));
                Optional<WallPatternEntry> wall = resolved.wallFor(SQUARE, SQUARE, HEIGHT);
                if (wall.isEmpty()) {
                    continue;
                }
                for (WallPatternEntry.PatternEntry entry : wall.get().patterns()) {
                    if (!(entry.pattern() instanceof CoursesWallPattern courses)) {
                        continue;
                    }
                    for (CourseEntry course : courses.courses()) {
                        assertEquals(0, course.projection(), "scheme '" + scheme.name() + "' "
                                + "projects a course of its own; the motif already crowns every "
                                + "wall, and the scheme wins the row it shares");
                    }
                }
            }
        }
    }

    /**
     * And that the projection reaches a cell the wall plane does not own. Built through the real
     * generator, with the crown forced, because "projection: 1 is in the JSON" and "a block lands
     * one cell into the room" are different claims -- the first was true of the mud band for weeks
     * while nothing checked the second for stone.
     */
    @Test
    void theCornicePutsABlockInsideTheRoomVolume() {
        String json = "{\"wall\": \"minecraft:stone_bricks\", \"pattern\": {\"patterns\": ["
                + "{\"type\": \"dungeons2:courses\", \"config\": {\"courses\": ["
                + "{\"block\": \"minecraft:stone_brick_stairs\", \"anchor\": \"top\","
                + " \"projection\": 1, \"orient\": \"toward_wall\","
                + " \"properties\": {\"half\": \"top\"}}]}}]}}";
        WallConfig crown = WallConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, error -> {
                    throw new AssertionError("the crown config no longer decodes: " + error);
                });

        Map<String, String> world = build(crown);
        // The wall ring is the room's perimeter, so x = ORIGIN + 1 is the first cell INSIDE it.
        // A flush course would leave this column untouched all the way up.
        long inside = world.entrySet().stream()
                .filter(cell -> "minecraft:stone_brick_stairs".equals(cell.getValue()))
                .filter(cell -> {
                    String[] parts = cell.getKey().split(":");
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    return x > ORIGIN && x < ORIGIN + SQUARE - 1
                            && z > ORIGIN && z < ORIGIN + SQUARE - 1;
                })
                .count();
        assertTrue(inside > 0,
                "the cornice drew only in the wall plane -- projection is being dropped, and every "
                        + "crown in the motif is a flush stripe");
    }

    /**
     * The gate that keeps the stone schemes out of the mud band. Both ship
     * {@code min_floor_index: 1}; floor 0 is mud's, and a stone hall rolling there was the thing
     * the (now deleted) scaffolding gates existed to prevent.
     */
    @Test
    void neitherStoneSchemeRollsOnTheMudFloor() {
        List<String> onMud = MotifConfigs.load("classic").forFloor(0).schemes().stream()
                .filter(scheme -> scheme.fitsFloor(0))
                .map(RoomScheme::name)
                .toList();
        assertFalse(onMud.contains("chamber"), "'chamber' rolls on the mud floor: " + onMud);
        assertFalse(onMud.contains("hall"), "'hall' rolls on the mud floor: " + onMud);
        assertFalse(onMud.isEmpty(), "the mud band lost its own schemes");
    }

    /** Hollow the room, then run only the wall generator: nothing else is under test here. */
    private static Map<String, String> build(WallConfig wall) {
        RoomData room = new RoomData(1, ORIGIN, ORIGIN, SQUARE, SQUARE, HEIGHT, RoomRole.NORMAL);
        RoomPlacements out = new RoomPlacements();

        RoomVolumeGenerator.hollow(room, FLOOR_Y, out.getBlocks());
        new BasicWallGenerator()
                .withMotifConfig(classic())
                .withWallPattern(WallPatternSelector.providerFor(
                        Optional.empty(), wall, SQUARE, SQUARE, HEIGHT))
                .build(room, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out.getBlocks());

        // Keyed y-first so one row can be scanned with a prefix; last write wins the cell, since
        // the wall generator draws over the hollow shell.
        Map<String, String> world = new LinkedHashMap<>();
        for (BlockPlacement placement : out.getBlocks()) {
            world.put(placement.getY() + ":" + placement.getX() + ":" + placement.getZ(),
                    placement.getBlockId());
        }
        return world;
    }
}
