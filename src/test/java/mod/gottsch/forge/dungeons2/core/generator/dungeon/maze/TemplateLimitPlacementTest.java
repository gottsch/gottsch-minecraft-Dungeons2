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

import mod.gottsch.forge.dungeons2.core.config.TemplateLimit;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #44's counting half: {@code templateLimits} actually constrains how often a prefab is
 * adopted, driven through the real {@link DungeonStackPlanner}.
 *
 * <h2>Why a counting assembler rather than assertions on the layout</h2>
 * <p>The layout records a single constant for every adopted prefab ({@code dungeons2:rooms/assembled}),
 * so it cannot say <em>which</em> template a room is &mdash; that identity exists only inside the
 * assembler callback. The stub below therefore records what it was asked to commit, which is also
 * the only way to distinguish "the planner never asked" from "the planner asked and then rejected".
 * </p>
 */
class TemplateLimitPlacementTest {

    private static final int FLOORS = 3;
    private static final int ATTEMPTS = 4;
    private static final String ALPHA = "dungeons2:rooms/classic/7x7/alpha";
    private static final String BETA = "dungeons2:rooms/classic/7x7/beta";

    /**
     * A 7x7 assembler that alternates between two templates by seed parity, and records every
     * committed placement per floor. Deliberately unrotated: rotation is
     * {@code RoomAssemblyPlacementTest}'s subject, and mixing the two would make a failure here
     * ambiguous between "the cap did not apply" and "the prefab missed its slot".
     */
    private static final class CountingAssembler implements DungeonStackPlanner.RoomAssembler {
        private final List<String> committed = new ArrayList<>();
        /** Every floorIndex the planner asked for -- #45 step 3's contract, see the test below. */
        private final List<Integer> floorsAsked = new ArrayList<>();
        private final List<String> only;

        CountingAssembler(List<String> only) {
            this.only = only;
        }

        @Override
        public Optional<DungeonStackPlanner.AssembledRoom> assemble(int worldX, int worldY,
                                                                    int worldZ, int floorIndex,
                                                                    long assemblySeed,
                                                                    boolean commit) {
            floorsAsked.add(floorIndex);
            String id = only.get(Math.floorMod(Long.hashCode(assemblySeed), only.size()));
            Rectangle2D footprint = new Rectangle2D(worldX, worldZ, 7, 7);
            if (commit) {
                committed.add(id);
            }
            return Optional.of(new DungeonStackPlanner.AssembledRoom(footprint,
                    List.of(new Coords2D(worldX, worldZ + 3), new Coords2D(worldX + 3, worldZ)),
                    List.of(), List.of(id)));
        }

        int countOf(String id) {
            return (int) committed.stream().filter(id::equals).count();
        }
    }

    private static Optional<DungeonLayout> plan(long seed, CountingAssembler assembler,
                                                Map<String, TemplateLimit> limits) {
        return new DungeonStackPlanner(seed, new Coords(128, 0, 256), 72, "classic",
                new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withFloorCount(FLOORS)
                .withRoomTemplateAttempts(ATTEMPTS)
                .withRoomAssembler(assembler)
                .withTemplateLimits(limits)
                .plan();
    }

    /** How many prefab rooms the layout actually adopted, across every floor. */
    private static int adoptedRooms(DungeonLayout layout) {
        int adopted = 0;
        for (FloorLayout floor : layout.getFloors()) {
            for (var room : floor.getRooms()) {
                if (room.getTemplateId() != null && room.getTemplateId().contains("rooms/assembled")) {
                    adopted++;
                }
            }
        }
        return adopted;
    }

    // ---------- the cap ----------

    @Test
    void aMaxPerDungeonOfOneIsNeverExceeded() {
        Map<String, TemplateLimit> limits = Map.of(
                ALPHA, new TemplateLimit(Optional.empty(), Optional.of(1)));

        for (long seed = 0; seed < 30; seed++) {
            CountingAssembler assembler = new CountingAssembler(List.of(ALPHA));
            if (plan(seed, assembler, limits).isEmpty()) {
                continue;
            }
            assertTrue(assembler.countOf(ALPHA) <= 1,
                    "seed " + seed + " committed " + assembler.countOf(ALPHA) + " copies of a"
                            + " template capped at 1 per dungeon");
        }

        // The control. Without it this test passes just as well against a planner that never
        // places two of anything, which is the difference between testing the cap and testing
        // nothing at all.
        int uncappedMax = 0;
        for (long seed = 0; seed < 30; seed++) {
            CountingAssembler assembler = new CountingAssembler(List.of(ALPHA));
            if (plan(seed, assembler, Map.of()).isPresent()) {
                uncappedMax = Math.max(uncappedMax, assembler.countOf(ALPHA));
            }
        }
        assertTrue(uncappedMax > 1, "no seed placed two copies even uncapped, so the assertion"
                + " above proves nothing -- raise the attempt count or the seed range");
    }

    /**
     * A per-floor cap resets with each floor, so a 3-floor dungeon may hold three copies of a
     * template capped at one per floor. That is the point of having two bounds rather than one.
     */
    @Test
    void aMaxPerFloorCapResetsOnEachFloor() {
        Map<String, TemplateLimit> limits = Map.of(
                ALPHA, new TemplateLimit(Optional.of(1), Optional.empty()));

        int sawMoreThanOne = 0;
        for (long seed = 0; seed < 30; seed++) {
            CountingAssembler assembler = new CountingAssembler(List.of(ALPHA));
            if (plan(seed, assembler, limits).isEmpty()) {
                continue;
            }
            assertTrue(assembler.countOf(ALPHA) <= FLOORS,
                    "a per-floor cap of 1 allowed more than one per floor at seed " + seed);
            if (assembler.countOf(ALPHA) > 1) {
                sawMoreThanOne++;
            }
        }
        assertTrue(sawMoreThanOne > 0,
                "no seed ever placed the template on two different floors, so this test cannot"
                        + " tell a per-floor cap from a per-dungeon one");
    }

    /** {@code maxPerDungeon: 0} keeps a template out entirely. */
    @Test
    void aZeroCapPlacesTheTemplateNowhere() {
        Map<String, TemplateLimit> limits = Map.of(
                ALPHA, new TemplateLimit(Optional.empty(), Optional.of(0)));

        for (long seed = 0; seed < 20; seed++) {
            CountingAssembler assembler = new CountingAssembler(List.of(ALPHA));
            if (plan(seed, assembler, limits).isEmpty()) {
                continue;
            }
            assertEquals(0, assembler.countOf(ALPHA),
                    "a template capped at 0 was still placed at seed " + seed);
        }
    }

    /** A cap on one template must not constrain another. */
    @Test
    void anUncappedTemplateIsUnaffectedByItsNeighboursCap() {
        Map<String, TemplateLimit> limits = Map.of(
                ALPHA, new TemplateLimit(Optional.empty(), Optional.of(0)));

        int betaTotal = 0;
        for (long seed = 0; seed < 20; seed++) {
            CountingAssembler assembler = new CountingAssembler(List.of(ALPHA, BETA));
            if (plan(seed, assembler, limits).isEmpty()) {
                continue;
            }
            assertEquals(0, assembler.countOf(ALPHA));
            betaTotal += assembler.countOf(BETA);
        }
        assertTrue(betaTotal > 0, "the uncapped template was never placed either, so the cap is"
                + " suppressing more than its own template");
    }

    // ---------- the no-limits guarantee ----------

    /**
     * <strong>With no limits declared, the plan must be byte-identical to one made without the
     * feature.</strong> The check sits in front of {@code placeAvoidingReserved}, which draws from
     * the planner's {@code random}, so a rejection necessarily shifts the stream and re-rolls the
     * layout. That is acceptable when an author declares a limit, and completely unacceptable as a
     * side effect of the feature merely existing -- it would silently re-roll every existing world.
     */
    @Test
    void declaringNoLimitsChangesNothing() {
        for (long seed = 0; seed < 20; seed++) {
            CountingAssembler withoutLimits = new CountingAssembler(List.of(ALPHA, BETA));
            CountingAssembler withEmptyMap = new CountingAssembler(List.of(ALPHA, BETA));

            Optional<DungeonLayout> a = plan(seed, withoutLimits, Map.of());
            Optional<DungeonLayout> b = plan(seed, withEmptyMap, new HashMap<>());

            assertEquals(a.isPresent(), b.isPresent(), "seed " + seed);
            if (a.isEmpty()) {
                continue;
            }
            assertEquals(adoptedRooms(a.get()), adoptedRooms(b.get()), "seed " + seed);
            assertEquals(withoutLimits.committed, withEmptyMap.committed,
                    "an empty limits map changed which prefabs were committed at seed " + seed);
        }
    }

    /**
     * A template the assembler cannot identify is treated as <strong>unlimited</strong>, not as
     * blocked -- see {@code DungeonStackPlanner#allowsAnotherCopy}. Degrading toward "no prefab
     * rooms at all" over an unreadable pool element would be much worse than an uncapped room.
     */
    @Test
    void anUnidentifiableTemplateIsStillPlaced() {
        DungeonStackPlanner.RoomAssembler anonymous = (wx, wy, wz, floorIndex, seed, commit) ->
                Optional.of(new DungeonStackPlanner.AssembledRoom(
                        new Rectangle2D(wx, wz, 7, 7),
                        List.of(new Coords2D(wx, wz + 3), new Coords2D(wx + 3, wz)),
                        List.of()));

        Map<String, TemplateLimit> limits = Map.of(
                ALPHA, new TemplateLimit(Optional.empty(), Optional.of(0)));

        int adopted = 0;
        for (long seed = 0; seed < 20; seed++) {
            Optional<DungeonLayout> layout = new DungeonStackPlanner(seed, new Coords(128, 0, 256),
                    72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    .withFloorCount(FLOORS)
                    .withRoomTemplateAttempts(ATTEMPTS)
                    .withRoomAssembler(anonymous)
                    .withTemplateLimits(limits)
                    .plan();
            if (layout.isPresent()) {
                adopted += adoptedRooms(layout.get());
            }
        }
        assertTrue(adopted > 0, "an assembler that names no template had every room rejected --"
                + " an unidentifiable element must be read as unlimited, not as capped");
    }
}
