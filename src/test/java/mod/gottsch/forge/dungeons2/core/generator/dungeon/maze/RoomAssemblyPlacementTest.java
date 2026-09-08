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
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same measure-before-reserve question as
 * {@link TransitionAssemblyPlacementTest}, for Phase 8 interior room prefabs.
 *
 * <p>A room is a single piece, so it can't sprawl the way a transition chain does
 * — but vanilla still rotates it, and rotation moves the bounding box's min corner
 * off the position the planner asked for. Every one of the four shipped prefabs is
 * 7x7, so three of the four rotations displace the footprint 6 blocks west and/or
 * north of the assembly point. A slot whose real footprint lands out of bounds is
 * silently dropped and covered by an ordinary procedural fill room, so the loss is
 * invisible in game — only measurable.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
class RoomAssemblyPlacementTest {

    private static final int SEEDS = 200;
    private static final int FLOORS = 3;
    /** The planner's own default, which this test drives explicitly rather than relying on. */
    private static final int ATTEMPTS_PER_FLOOR = 2;
    /**
     * A fixed count near the top of what the shipped density asks of a big floor, so the crowding
     * question is still pinned now that no single number is "the shipped attempt count".
     *
     * <p>It used to be literally {@code generation_config}'s value (backlog #16, 4). That field is a
     * DENSITY since 2026-09-07, so a LARGE floor now asks for around 8 and a SMALL one for 2 &mdash;
     * and the thing worth guarding is the top of that range, where attempts compete hardest for
     * floor area.</p>
     */
    private static final int CROWDED_ATTEMPTS_PER_FLOOR = 8;

    /** What {@code generation_config/default.json} ships: floor cells per prefab attempt. */
    private static final int SHIPPED_CELLS_PER_TEMPLATE =
            DungeonGenerationConfig.DEFAULT_FLOOR_CELLS_PER_ROOM_TEMPLATE;

    @Test
    void aRotatedRoomPrefabIsAlmostAlwaysAdopted() {
        assertAdoptionHolds(ATTEMPTS_PER_FLOOR);
    }

    /**
     * Adoption has to survive a CROWDED floor, not just the planner's default. Attempts on one floor
     * compete for the same area &mdash; each adopted prefab reserves its footprint against the next
     * &mdash; so a rate measured at 2 says nothing about 8. This is the check that would catch
     * lowering {@code floor_cells_per_room_template} too far.
     */
    @Test
    void adoptionSurvivesACrowdedFloor() {
        assertAdoptionHolds(CROWDED_ATTEMPTS_PER_FLOOR);
    }

    private void assertAdoptionHolds(int attemptsPerFloor) {
        int adopted = 0;
        int normalRooms = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Optional<DungeonLayout> opt = new DungeonStackPlanner(
                    seed, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    .withFloorCount(FLOORS)
                    .withRoomTemplateAttempts(attemptsPerFloor)
                    .withRoomAssembler(ROTATED_7X7)
                    .plan();
            if (opt.isEmpty()) {
                continue;
            }
            for (FloorLayout floor : opt.get().getFloors()) {
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() == RoomRole.NORMAL) {
                        normalRooms++;
                    }
                    if (room.getTemplateId() == null || !room.getTemplateId().contains("rooms/assembled")) {
                        continue;
                    }
                    adopted++;
                    // The slot is reserved ROOM_EDGE_MARGIN clear of the boundary, so
                    // no adopted prefab may sit flush against it -- a door candidate
                    // on such a room's edge lands on the grid's boundary row/column.
                    assertTrue(room.getOriginX() > 0 && room.getOriginZ() > 0
                                    && room.getOriginX() + room.getWidth() < floor.getFootprint().getWidth()
                                    && room.getOriginZ() + room.getDepth() < floor.getFootprint().getHeight(),
                            "an adopted prefab must stay clear of the floor's own boundary");
                }
            }
        }

        int slots = SEEDS * FLOORS * attemptsPerFloor;
        double rate = (double) adopted / slots;
        System.out.printf("room-prefab adoption at %d attempts/floor: %d of %d (%.1f%%), "
                        + "%.1f prefabs and %.1f rooms per %d-floor dungeon -> %.1f%% prefab share%n",
                attemptsPerFloor, adopted, slots, rate * 100, (double) adopted / SEEDS,
                (double) normalRooms / SEEDS, FLOORS, 100.0 * adopted / normalRooms);
        assertTrue(rate >= 0.90, String.format(
                "only %.0f%% of room-prefab slots were filled (%d of %d) at %d attempts/floor -- a "
                        + "rotated prefab's real footprint sits up to 6 blocks west/north of the "
                        + "assembly point, so a slot picked before the footprint is known lands out "
                        + "of bounds and is dropped",
                rate * 100, adopted, slots, attemptsPerFloor));
    }

    /**
     * The invariant the density knob was bought for: <strong>a LARGE dungeon is no more procedural
     * than a SMALL one.</strong>
     *
     * <h2>The defect, which was silent and had been shipping for a month</h2>
     * <p>{@code floor_cells_per_room_template} used to be a flat
     * {@code room_template_attempts_per_floor}, so every floor got the same handful of attempts
     * however big it was &mdash; while {@code DungeonStackPlanner#pickNumberOfRooms} has always
     * scaled room COUNT with area. A fixed numerator over a growing denominator, and measured over
     * 300 seeds per tier at the shipped 4 the authored share ran <strong>31.3% / 20.6% /
     * 11.0%</strong> across SMALL / MEDIUM / LARGE. Exactly backwards: the dungeon a player spends
     * longest in was the one that felt the most generated, and nothing in the game or the logs said
     * so &mdash; a floor that adopts no prefab is simply covered by procedural fill.</p>
     *
     * <h2>Why this asserts a SPREAD and not a value</h2>
     * <p>Pinning "MEDIUM is 20%" would fail the day anyone retunes room count, which moves the share
     * without touching this knob at all &mdash; the project has been caught by exactly that twice,
     * which is why the config comment says re-measure and never quote. The levelling is the
     * property that actually has to hold, so the assertion is on the spread between tiers. The band
     * is deliberately wide enough to survive ordinary retuning and far too tight to survive the flat
     * count coming back, which spread 20 points.</p>
     */
    @Test
    void theAuthoredShareIsLevelAcrossSizeTiers() {
        Map<DungeonSize, Double> share = new EnumMap<>(DungeonSize.class);
        for (DungeonSize size : DungeonSize.values()) {
            int floors = switch (size) {
                case SMALL -> 2;
                case MEDIUM -> 3;
                case LARGE -> 4;
            };
            int adopted = 0;
            int normalRooms = 0;
            for (long seed = 0; seed < SEEDS; seed++) {
                Optional<DungeonLayout> opt = new DungeonStackPlanner(
                        seed, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                        .withSize(size)
                        .withFloorCount(floors)
                        .withRoomTemplateDensity(SHIPPED_CELLS_PER_TEMPLATE)
                        .withRoomAssembler(ROTATED_7X7)
                        .plan();
                if (opt.isEmpty()) {
                    continue;
                }
                for (FloorLayout floor : opt.get().getFloors()) {
                    for (RoomData room : floor.getRooms()) {
                        if (room.getRole() == RoomRole.NORMAL) {
                            normalRooms++;
                        }
                        if (room.getTemplateId() != null
                                && room.getTemplateId().contains("rooms/assembled")) {
                            adopted++;
                        }
                    }
                }
            }
            share.put(size, 100.0 * adopted / normalRooms);
        }

        System.out.printf("authored share at %d cells/template: SMALL %.1f%%  MEDIUM %.1f%%  "
                        + "LARGE %.1f%%%n", SHIPPED_CELLS_PER_TEMPLATE, share.get(DungeonSize.SMALL),
                share.get(DungeonSize.MEDIUM), share.get(DungeonSize.LARGE));

        double min = Collections.min(share.values());
        double max = Collections.max(share.values());
        assertTrue(max - min <= 6.0, String.format(
                "the authored share should be level across size tiers, but spread %.1f points: %s."
                        + " A flat per-floor attempt count spreads about 20 -- if that is what this"
                        + " is measuring, floor_cells_per_room_template has stopped being applied"
                        + " per floor area", max - min, share));
        // And it has to be a real share, not level at zero -- which is what a density that stopped
        // being injected would look like, and would otherwise pass the spread check above.
        assertTrue(min > 5.0,
                "authored share collapsed to " + share + "; the density is not reaching the planner");
    }

    /**
     * Where a 7x7 piece's bounding-box min corner lands relative to the position it
     * was assembled at, per rotation. Vanilla rotates about the anchor, so e.g.
     * CLOCKWISE_90 maps {@code (x,z) -> (-z,x)} and the piece's {@code x 0..6}
     * becomes {@code x -6..0}.
     */
    private static final int[][] ROTATION_OFFSET = {
            {0, 0},    // NONE
            {-6, 0},   // CW 90
            {-6, -6},  // 180
            {0, -6},   // CCW 90
    };


    /**
     * Models a real shipped room prefab: 7x7 (all four of
     * {@code rooms/classic/*.nbt} are), rotated one of four ways, with mid-wall
     * {@code dungeons2:door} markers on each side like {@code 7x7_junction_1}.
     *
     * <p>Because the piece is square with symmetric doors, the marker cells stay at
     * the same offsets from the footprint's min corner under rotation; what rotation
     * changes is where that min corner lands relative to the assembly point.</p>
     */
    private static final DungeonStackPlanner.RoomAssembler ROTATED_7X7 =
            (wx, wy, wz, floorIndex, seed, commit) -> {
                int[] o = ROTATION_OFFSET[Math.floorMod(new java.util.Random(seed).nextInt(), 4)];
                Rectangle2D fp = new Rectangle2D(wx + o[0], wz + o[1], 7, 7);
                int minX = fp.getMinX();
                int minZ = fp.getMinY();
                return Optional.of(new DungeonStackPlanner.AssembledRoom(fp,
                        List.of(new Coords2D(minX, minZ + 3), new Coords2D(minX + 3, minZ),
                                new Coords2D(minX + 3, minZ + 6), new Coords2D(minX + 6, minZ + 3)),
                        List.of()));
            };


    /**
     * #45 step 3: the planner asks for a room with the <strong>floor's own index</strong>.
     *
     * <p>This is the fault the parameter exists to prevent, and it is why {@code floorIndex} is a
     * parameter rather than a setter on the assembler ({@code #10}'s rule). The implementation
     * resolves the rooms pool through the motif's stratum for that depth, so a value stuck at 0
     * would assemble every floor of the dungeon out of the entrance floor's rooms &mdash; silently,
     * correctly-looking, and only for packs that author strata.
     *
     * <p>Asserts both directions: every index the planner asked for is a real floor of the plan,
     * and more than one distinct index was asked for at all. Without the second half the test would
     * pass on a planner that only ever asked for floor 0.
     */
    @Test
    void theAssemblerIsToldWhichFloorItIsAssemblingFor() {
        java.util.Set<Integer> asked = new java.util.HashSet<>();
        DungeonStackPlanner.RoomAssembler recording = (wx, wy, wz, floorIndex, seed, commit) -> {
            asked.add(floorIndex);
            return ROTATED_7X7.assemble(wx, wy, wz, floorIndex, seed, commit);
        };

        Optional<DungeonLayout> opt = new DungeonStackPlanner(
                7L, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withFloorCount(FLOORS)
                .withRoomTemplateAttempts(CROWDED_ATTEMPTS_PER_FLOOR)
                .withRoomAssembler(recording)
                .plan();

        assertTrue(opt.isPresent(), "the fixture seed must produce a plan for this to mean anything");
        java.util.Set<Integer> floors = opt.get().getFloors().stream()
                .map(FloorLayout::getFloorIndex).collect(java.util.stream.Collectors.toSet());
        assertTrue(floors.containsAll(asked),
                "the planner asked for floors " + asked + " but the plan only has " + floors);
        assertTrue(asked.size() > 1,
                "only floor " + asked + " was ever asked for, so this test could not tell a real"
                        + " floorIndex from a hardcoded 0");
    }
}
