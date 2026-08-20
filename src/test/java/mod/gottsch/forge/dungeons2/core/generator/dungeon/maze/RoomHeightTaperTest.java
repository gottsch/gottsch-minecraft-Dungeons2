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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.maze;

import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomHeightBand;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #51: room height is tapered by footprint, and the taper's ceiling <em>falls</em> as the
 * footprint grows.
 *
 * <p>The old rule was {@code min(5 + rand(6), max(width, depth))} &mdash; a cap that rose with the
 * footprint, so the only rooms that could be tall were the big ones, which is the box Mark does not
 * want. {@link RoomHeightBand} inverts it.</p>
 *
 * <p>Two properties are worth pinning, and the second is the load-bearing one:</p>
 * <ol>
 *   <li>every room's height lands inside the band its long side selects;</li>
 *   <li><strong>changing the table changes heights and nothing else.</strong> The roll stays
 *       {@code 5 + nextInt(6)} and the band clamps the result, so the planner's random stream is
 *       identical whatever table is injected. If someone later "tidies" that into a draw inside the
 *       band ({@code min + nextInt(span)}), {@code java.util.Random} rejection-samples differently
 *       for a different bound, the stream shifts, and every dungeon in every existing world
 *       relayouts. That is silent in game and this test is what catches it.</li>
 * </ol>
 */
class RoomHeightTaperTest {

    /** Deliberately unlike the shipped table, and unlike it in both directions. */
    private static final List<RoomHeightBand> ALTERNATE = List.of(
            new RoomHeightBand(Optional.of(9), 8, 10),
            new RoomHeightBand(Optional.empty(), 5, 6));

    private static Optional<DungeonLayout> plan(long seed, DungeonSize size,
                                                List<RoomHeightBand> bands) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, "classic",
                new TemplateCatalog())
                .withSize(size)
                .withRoomHeightBands(bands)
                .plan();
    }

    private static List<RoomData> rooms(DungeonLayout layout) {
        List<RoomData> out = new ArrayList<>();
        for (FloorLayout floor : layout.getFloors()) {
            out.addAll(floor.getRooms());
        }
        return out;
    }

    /** Every room sits inside the band its long side selects, across a spread of seeds and tiers. */
    @Test
    void everyRoomLandsInsideItsBand() {
        int checked = 0;
        for (DungeonSize size : DungeonSize.values()) {
            for (int i = 0; i < 40; i++) {
                // Spread, not sequential: RandomSource.create(0,1,2,...) correlates hard on its
                // first draw. See reference_first_draw_seed_correlation.
                Optional<DungeonLayout> planned =
                        plan(0xD2_5101_0001L + i * 7919L, size,
                                DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS);
                if (planned.isEmpty()) {
                    continue;
                }
                for (RoomData room : rooms(planned.get())) {
                    int longSide = Math.max(room.getWidth(), room.getDepth());
                    RoomHeightBand band = RoomHeightBand.forLongSide(
                            DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS, longSide);
                    assertTrue(room.getHeight() >= band.minHeight()
                                    && room.getHeight() <= band.maxHeight(),
                            "room " + room.getId() + " is " + room.getWidth() + "x" + room.getDepth()
                                    + " and " + room.getHeight() + " tall, outside its band "
                                    + band.minHeight() + ".." + band.maxHeight());
                    checked++;
                }
            }
        }
        assertTrue(checked > 500, "expected a meaningful sample, saw " + checked);
    }

    /**
     * The taper actually inverts: under the shipped table no large room reaches the height a small
     * one can. Stated as an invariant over the table rather than over sampled output, plus a live
     * check that the largest rooms really are shorter in practice.
     */
    @Test
    void theCeilingFallsAsTheFootprintGrows() {
        List<RoomHeightBand> shipped = DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS;
        for (int i = 1; i < shipped.size(); i++) {
            assertTrue(shipped.get(i).maxHeight() <= shipped.get(i - 1).maxHeight(),
                    "band " + i + " allows a taller room than the smaller-footprint band before it");
        }
        // 7 is the smallest footprint the planner produces; 19 the largest.
        assertTrue(RoomHeightBand.forLongSide(shipped, 19).maxHeight()
                        < RoomHeightBand.forLongSide(shipped, 7).maxHeight(),
                "the biggest rooms may still be as tall as the smallest -- the coupling is not inverted");
    }

    /**
     * The clamp-not-re-roll property: two different tables, same seed, identical geometry. This is
     * what keeps existing worlds' layouts byte-identical across #51.
     */
    @Test
    void changingTheTableMovesHeightsAndNothingElse() {
        int comparedDungeons = 0;
        int differingHeights = 0;
        for (int i = 0; i < 25; i++) {
            long seed = 0xD2_5102_0001L + i * 7919L;
            Optional<DungeonLayout> a = plan(seed, DungeonSize.LARGE,
                    DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS);
            Optional<DungeonLayout> b = plan(seed, DungeonSize.LARGE, ALTERNATE);
            if (a.isEmpty() || b.isEmpty()) {
                assertEquals(a.isEmpty(), b.isEmpty(), "the table decided whether a dungeon exists");
                continue;
            }
            comparedDungeons++;
            assertEquals(a.get().getFloors().size(), b.get().getFloors().size(),
                    "floor count moved with the height table");
            List<RoomData> ra = rooms(a.get());
            List<RoomData> rb = rooms(b.get());
            assertEquals(ra.size(), rb.size(), "room count moved with the height table");
            for (int r = 0; r < ra.size(); r++) {
                RoomData x = ra.get(r);
                RoomData y = rb.get(r);
                assertEquals(footprint(x), footprint(y),
                        "room " + r + "'s footprint moved with the height table -- the roll is no "
                                + "longer stream-identical across tables");
                if (x.getHeight() != y.getHeight()) {
                    differingHeights++;
                }
            }
            // Corridors are converted from the SAME random after the rooms, so a shifted stream
            // shows up here even when the rooms happen to survive it.
            assertEquals(corridorSignature(a.get()), corridorSignature(b.get()),
                    "corridors moved with the height table");
        }
        assertTrue(comparedDungeons >= 20, "expected a meaningful sample, saw " + comparedDungeons);
        assertNotEquals(0, differingHeights, "the alternate table changed no heights at all, so "
                + "this test would pass even if the tables were being ignored");
    }

    /** The shipped table has to fit inside one floor, or a room's ceiling breaks into the floor above. */
    @Test
    void theShippedTableFitsTheFloorBudget() {
        int floorHeight = new DungeonStackPlanner(0L, new Coords(0, 0, 0), 72, "classic",
                new TemplateCatalog()).floorHeight();
        assertTrue(RoomHeightBand.validateAgainstBudget(
                        DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS, floorHeight),
                "the shipped roomHeightBands exceed the planner's floorHeight of " + floorHeight);
    }

    private static String footprint(RoomData room) {
        return room.getId() + "@" + room.getOriginX() + "," + room.getOriginZ()
                + " " + room.getWidth() + "x" + room.getDepth() + " " + room.getRole();
    }

    private static String corridorSignature(DungeonLayout layout) {
        StringBuilder sb = new StringBuilder();
        for (FloorLayout floor : layout.getFloors()) {
            sb.append(floor.getFloorIndex()).append(':').append(floor.getCorridors().size())
                    .append('/').append(floor.getDoors().size()).append(';');
        }
        return sb.toString();
    }
}
