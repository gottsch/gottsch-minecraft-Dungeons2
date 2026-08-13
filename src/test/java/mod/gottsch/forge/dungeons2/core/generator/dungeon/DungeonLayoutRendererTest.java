/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 deliverable: a whole {@link DungeonLayout} renders to one
 * {@link BlockPlacement} list, deterministically, with no world running.
 *
 * @author Mark Gottschling on Jun 14, 2026
 */
class DungeonLayoutRendererTest {

    private static final long SEED = 0xD2_0BADC0DE_CAFEL;
    private static final ICoords ANCHOR = new Coords(128, 0, 256);
    private static final int SURFACE_Y = 72;

    @BeforeAll
    static void bootstrap() {
        // The builders resolve block states via the registry, so Minecraft must
        // be bootstrapped (same pattern as the Basic*GeneratorTests).
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private DungeonLayout planSmall(long seed) {
        // Empty catalog → synthetic entrance/transition; the renderer skips those
        // anyway, so it doesn't matter for procedural rendering.
        return new DungeonStackPlanner(seed, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                .withSize(DungeonSize.SMALL)
                .plan()
                .orElseThrow();
    }

    @Test
    void rendersNonEmptyPlacementsForALayout() {
        DungeonLayout layout = planSmall(SEED);
        List<BlockPlacement> placements = new DungeonLayoutRenderer()
                .render(layout, RandomSource.create(SEED));

        assertFalse(placements.isEmpty(), "A planned layout should render at least some blocks");
        assertTrue(placements.stream().allMatch(p -> p.getBlockId() != null),
                "Every placement must carry a block id");
    }

    @Test
    void renderIsDeterministicForSameSeed() {
        DungeonLayout layout = planSmall(SEED);
        List<BlockPlacement> a = new DungeonLayoutRenderer().render(layout, RandomSource.create(SEED));
        List<BlockPlacement> b = new DungeonLayoutRenderer().render(layout, RandomSource.create(SEED));

        assertEquals(a.size(), b.size(), "Same seed must render the same number of placements");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).toString(), b.get(i).toString(),
                    "Placement " + i + " should be identical across runs");
        }
    }

    /**
     * <strong>The positive half.</strong> {@code SlotCoverageTest} asserts the terminal room is
     * classified as ours to build; this asserts blocks actually come out of it. The bug it replaces
     * was a room that classified one way and rendered the other, so checking only the classification
     * would have reproduced the original mistake in test form.
     */
    @Test
    void theTerminalRoomIsActuallyBuilt() {
        DungeonLayout layout = planSmall(SEED);
        List<BlockPlacement> placements = new DungeonLayoutRenderer()
                .render(layout, RandomSource.create(SEED));

        FloorLayout bottom = layout.getFloors().get(layout.getFloors().size() - 1);
        RoomData terminal = bottom.getRooms().stream()
                .filter(room -> room.getRole() == RoomRole.TERMINAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the bottom floor has no TERMINAL room"));

        int x0 = terminal.getOriginX() + 1;
        int x1 = terminal.getOriginX() + terminal.getWidth() - 2;
        int z0 = terminal.getOriginZ() + 1;
        int z1 = terminal.getOriginZ() + terminal.getDepth() - 2;
        int floorY = bottom.getFloorY();
        long interiorFloor = placements.stream()
                .filter(p -> p.getY() == floorY
                        && p.getX() >= x0 && p.getX() <= x1
                        && p.getZ() >= z0 && p.getZ() <= z1)
                .count();

        long expected = (long) (x1 - x0 + 1) * (z1 - z0 + 1);
        assertEquals(expected, interiorFloor,
                "every interior floor cell of the terminal room should be built -- an unbuilt one is"
                        + " the hole that shipped");
    }

    @Test
    void startAndEndRoomsAreNotRendered() {
        DungeonLayout layout = planSmall(SEED);
        // Collect the XZ footprints of START/END rooms (template-covered, skipped).
        List<BlockPlacement> placements = new DungeonLayoutRenderer()
                .render(layout, RandomSource.create(SEED));

        for (FloorLayout floor : layout.getFloors()) {
            for (RoomData room : floor.getRooms()) {
                // TERMINAL is procedurally built and SHOULD be filled -- it is the bottom
                // floor's final room, and nothing else covers it. Asserting the opposite is
                // what this test used to do, and it is what let the hole ship.
                if (room.getRole().isProcedurallyBuilt()) {
                    continue;
                }
                // No placement should fall strictly inside a START/END room's interior
                // floor cell (border/walls can be shared with adjacent corridors, so we
                // only check the interior, which only that room's own builder would fill).
                int x0 = room.getOriginX() + 1, x1 = room.getOriginX() + room.getWidth() - 2;
                int z0 = room.getOriginZ() + 1, z1 = room.getOriginZ() + room.getDepth() - 2;
                int floorY = floor.getFloorY();
                boolean interiorFilled = placements.stream().anyMatch(p ->
                        p.getY() == floorY
                                && p.getX() >= x0 && p.getX() <= x1
                                && p.getZ() >= z0 && p.getZ() <= z1);
                assertFalse(interiorFilled,
                        "Renderer must not fill the interior floor of a " + room.getRole()
                                + " room (left for the template piece)");
            }
        }
    }

    @Test
    void everyFloorContributesPlacements() {
        DungeonLayout layout = planSmall(SEED);
        DungeonLayoutRenderer renderer = new DungeonLayoutRenderer();

        for (FloorLayout floor : layout.getFloors()) {
            List<BlockPlacement> out = new java.util.ArrayList<>();
            renderer.renderFloor(floor, mod.gottsch.forge.dungeons2.core.enums.DungeonMotif.CLASSIC,
                    RandomSource.create(SEED), out);
            assertFalse(out.isEmpty(),
                    "Floor " + floor.getFloorIndex() + " should render at least some blocks");
        }
    }
}
