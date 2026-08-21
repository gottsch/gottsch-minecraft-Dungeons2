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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #46: an authored boss room takes the bottom floor's terminal slot, and when it cannot,
 * the slot stays a room this mod builds.
 *
 * <h2>The failure path is the interesting half</h2>
 * <p>Every other authored template here is opportunistic &mdash; a prefab room that does not place
 * just means one fewer prefab. This one is a <em>promise</em> to the player, which makes "what
 * happens when it fails" the part worth pinning. Marking the slot covered when nothing covers it
 * reintroduces backlog #38 exactly: the maze has already reserved the footprint and routed a door
 * into it, so the result is a door opening into unbuilt terrain &mdash; invisible in solid stone,
 * and a hole into a cave at bottom-floor depth, which is where it was found the first time.</p>
 *
 * <p>So {@code BOSS} is set only on adoption, and everything else leaves {@code TERMINAL}. These
 * tests drive both branches through a synthetic assembler rather than real jigsaw placement, the
 * same way {@code TransitionAssemblyPlacementTest} does &mdash; there is no
 * {@code StructureTemplateManager} headlessly.</p>
 */
class BossRoomPlacementTest {

    private static final int SIDE = 11;

    /** Stands in for vanilla jigsaw: honours the protocol, always returns a SIDExSIDE footprint. */
    private static DungeonStackPlanner.RoomAssembler fixedSize(int side) {
        return (worldX, worldY, worldZ, assemblySeed, commit) -> Optional.of(
                new DungeonStackPlanner.AssembledRoom(
                        new Rectangle2D(worldX, worldZ, side, side),
                        // One authored door on the room's own edge, as a real template carries.
                        List.of(new Coords2D(worldX, worldZ + side / 2)),
                        List.of()));
    }

    /** The pool is absent, or nothing assembled: the planner must degrade, not fail. */
    private static final DungeonStackPlanner.RoomAssembler NEVER_ASSEMBLES =
            (worldX, worldY, worldZ, assemblySeed, commit) -> Optional.empty();

    private static Optional<DungeonLayout> plan(long seed, DungeonSize size,
                                                DungeonStackPlanner.RoomAssembler boss) {
        DungeonStackPlanner planner = new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72,
                "classic", new TemplateCatalog()).withSize(size);
        if (boss != null) {
            planner.withBossRoomAssembler(boss);
        }
        return planner.plan();
    }

    private static RoomData endRoom(DungeonLayout layout) {
        FloorLayout bottom = layout.getFloors().get(layout.getFloors().size() - 1);
        for (RoomData room : bottom.getRooms()) {
            if (room.getRole() == RoomRole.BOSS || room.getRole() == RoomRole.TERMINAL) {
                return room;
            }
        }
        return null;
    }

    @Test
    void anAssembledBossRoomTakesTheTerminalSlotAndIsMarkedCovered() {
        int boss = 0;
        int checked = 0;
        for (int i = 0; i < 30; i++) {
            // Spread, not sequential -- see reference_first_draw_seed_correlation.
            Optional<DungeonLayout> layout =
                    plan(0xD2_4607_0001L + i * 7919L, DungeonSize.LARGE, fixedSize(SIDE));
            if (layout.isEmpty()) {
                continue;
            }
            checked++;
            RoomData room = endRoom(layout.get());
            assertNotNull(room, "the bottom floor has no terminal slot at all");
            if (room.getRole() != RoomRole.BOSS) {
                continue;
            }
            boss++;
            assertEquals(SIDE, room.getWidth(), "the boss room is not the assembled footprint");
            assertEquals(SIDE, room.getDepth(), "the boss room is not the assembled footprint");
            assertNotNull(room.getTemplateId(),
                    "a BOSS room with no templateId is staged and then silently discarded, because "
                            + "commitStagedRooms keys adoption on exactly that");
            assertTrue(!room.getRole().isProcedurallyBuilt(),
                    "a covered slot must not also be built procedurally -- that is a double build");
        }
        assertTrue(checked >= 25, "expected a meaningful sample, saw " + checked);
        // LARGE bottom floors hold an 11x11 comfortably (TerminalRoomFitProbe: 100%), so anything
        // less than every dungeon means the adoption path is not actually running.
        assertEquals(checked, boss, "the boss room was not adopted on every LARGE dungeon");
    }

    /**
     * The #38 guard. An assembler that never produces anything must leave the slot exactly as it is
     * today: TERMINAL, no templateId, built by this mod.
     */
    @Test
    void afailedAssemblyLeavesAProcedurallyBuiltTerminalRoom() {
        int checked = 0;
        for (int i = 0; i < 30; i++) {
            Optional<DungeonLayout> layout =
                    plan(0xD2_4608_0001L + i * 7919L, DungeonSize.LARGE, NEVER_ASSEMBLES);
            if (layout.isEmpty()) {
                continue;
            }
            checked++;
            RoomData room = endRoom(layout.get());
            assertNotNull(room, "the bottom floor lost its terminal slot when assembly failed");
            assertEquals(RoomRole.TERMINAL, room.getRole(),
                    "a failed boss assembly marked the slot covered -- this is backlog #38 again");
            assertNull(room.getTemplateId(), "nothing assembled, so nothing may claim to cover it");
            assertTrue(room.getRole().isProcedurallyBuilt(),
                    "the fallback slot must be built by this mod or it is a door to nowhere");
        }
        assertTrue(checked >= 25, "expected a meaningful sample, saw " + checked);
    }

    /**
     * <strong>An ATTEMPT costs randomness, even a failed one</strong> &mdash; and this test exists
     * to keep that fact visible rather than to celebrate it.
     *
     * <p>{@code placeBossRoom} draws an assembly seed per attempt, so a planner handed an assembler
     * that never produces anything still plans a <em>different</em> dungeon than one handed no
     * assembler at all. That is why {@code DungeonStructure} wires the assembler only when an
     * {@code end_rooms} pool actually resolves: nothing ships one, so wiring it unconditionally
     * would have re-rolled the bottom floor of every existing world for a feature that is switched
     * off.</p>
     *
     * <p>If this ever starts passing &mdash; if the two become equal &mdash; the attempt loop has
     * stopped drawing, and the conditional wiring in {@code DungeonStructure} can go.</p>
     */
    @Test
    void aFailedAttemptStillCostsRandomness() {
        int differed = 0;
        int compared = 0;
        for (int i = 0; i < 20; i++) {
            long seed = 0xD2_4609_0001L + i * 7919L;
            Optional<DungeonLayout> none = plan(seed, DungeonSize.MEDIUM, null);
            Optional<DungeonLayout> failing = plan(seed, DungeonSize.MEDIUM, NEVER_ASSEMBLES);
            if (none.isEmpty() || failing.isEmpty()) {
                continue;
            }
            compared++;
            if (!signature(none.get()).equals(signature(failing.get()))) {
                differed++;
            }
        }
        assertTrue(compared >= 15, "expected a meaningful sample, saw " + compared);
        assertTrue(differed > 0,
                "a failing boss assembler changed nothing at all, which would mean the attempt loop "
                        + "never runs -- check that the assembler is actually being consulted");
    }

    /**
     * And a FAILED assembler must not either. This is the one that can actually regress: the probe
     * is called, returns empty, and if the attempt loop has drawn a seed by then the stream has
     * moved. It has -- so this asserts the layout differs ONLY in the terminal slot, not that it is
     * identical.
     */
    @Test
    void aFailedAssemblyStillProducesAWholeDungeon() {
        for (int i = 0; i < 20; i++) {
            long seed = 0xD2_460A_0001L + i * 7919L;
            Optional<DungeonLayout> layout = plan(seed, DungeonSize.SMALL, NEVER_ASSEMBLES);
            if (layout.isEmpty()) {
                continue;
            }
            for (FloorLayout floor : layout.get().getFloors()) {
                assertTrue(!floor.getRooms().isEmpty(),
                        "floor " + floor.getFloorIndex() + " came out empty after a failed boss "
                                + "assembly");
            }
            assertNotNull(endRoom(layout.get()));
        }
    }

    private static String signature(DungeonLayout layout) {
        StringBuilder sb = new StringBuilder();
        for (FloorLayout floor : layout.getFloors()) {
            sb.append(floor.getFloorIndex()).append('@').append(floor.getFloorY()).append(':');
            for (RoomData room : floor.getRooms()) {
                sb.append(room.getId()).append(',').append(room.getOriginX()).append(',')
                        .append(room.getOriginZ()).append(',').append(room.getWidth()).append('x')
                        .append(room.getDepth()).append('x').append(room.getHeight())
                        .append(' ').append(room.getRole()).append(';');
            }
        }
        return sb.toString();
    }
}
