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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.maze;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.data.TemplateEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 deliverable: verify that the POJO planner produces deterministic,
 * Minecraft-free output. The planner core depends only on the data POJOs,
 * the maze-grid types, and GottschCore's {@code ICoords}; no
 * {@code net.minecraft.*} types are referenced.
 *
 * @author Mark Gottschling on May 25, 2026
 */
class DungeonStackPlannerTest {

    private static final long SEED = 0xD2_0BADC0DE_CAFEL;
    private static final ICoords ANCHOR = new Coords(128, 0, 256);
    private static final int SURFACE_Y = 72;

    /** Build a small catalog with one entrance + one transition template, so picks are deterministic. */
    private TemplateCatalog buildCatalog() {
        TemplateCatalog catalog = new TemplateCatalog();

        TemplateEntry entrance = new TemplateEntry("dungeons2:entrances/classic_stair", 9, 9, 12);
        entrance.setMotifTags(java.util.List.of("classic"));
        catalog.add(TemplateCatalog.Category.ENTRANCE, entrance);

        TemplateEntry transition = new TemplateEntry("dungeons2:transitions/classic_ladder", 7, 7, 13);
        transition.setMotifTags(java.util.List.of("classic"));
        catalog.add(TemplateCatalog.Category.TRANSITION, transition);

        return catalog;
    }

    @Test
    void planSucceedsAndPopulatesAllLayers() {
        DungeonStackPlanner planner = new DungeonStackPlanner(
                SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.MEDIUM);

        Optional<DungeonLayout> result = planner.plan();
        assertTrue(result.isPresent(), "Planner should produce a layout for a MEDIUM dungeon");
        DungeonLayout layout = result.get();

        assertNotNull(layout.getEntrance(), "Entrance should be set");
        assertNotNull(layout.getAnchor(), "Anchor should be set");
        assertNotNull(layout.getBboxMin(), "BBox min should be computed");
        assertNotNull(layout.getBboxMax(), "BBox max should be computed");
        assertEquals(DungeonSize.MEDIUM, layout.getSize());
        assertEquals(SEED, layout.getSeed());

        assertFalse(layout.getFloors().isEmpty(), "At least one floor expected");
        // Multi-floor: transitions == floors - 1 (single linear main path).
        assertEquals(layout.getFloors().size() - 1, layout.getTransitions().size(),
                "One transition per inter-floor link");

        // Floor 0 must contain at least one START room (the entrance's reserved slot).
        FloorLayout floor0 = layout.getFloors().get(0);
        boolean hasStart = floor0.getRooms().stream().anyMatch(r -> r.getRole() == RoomRole.START);
        assertTrue(hasStart, "Floor 0 should contain a START room (entrance reservation)");
    }

    @Test
    void sameSeedProducesIdenticalLayout() {
        TemplateCatalog catalog = buildCatalog();
        DungeonLayout a = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", catalog)
                .withSize(DungeonSize.MEDIUM).plan().orElseThrow();
        DungeonLayout b = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", catalog)
                .withSize(DungeonSize.MEDIUM).plan().orElseThrow();

        // describe() walks every node in the layout tree; identical text means identical structure.
        assertEquals(a.describe(), b.describe(),
                "Same seed must produce byte-identical layout");
    }

    @Test
    void differentSeedsProduceDifferentLayouts() {
        TemplateCatalog catalog = buildCatalog();
        DungeonLayout a = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", catalog)
                .withSize(DungeonSize.MEDIUM).plan().orElseThrow();
        DungeonLayout b = new DungeonStackPlanner(SEED ^ 0xFFFFFFFFL, ANCHOR, SURFACE_Y, "classic", catalog)
                .withSize(DungeonSize.MEDIUM).plan().orElseThrow();

        // Sanity: if both seeds produced the same layout, the RNG wiring is broken.
        assertNotEquals(a.describe(), b.describe(),
                "Different seeds should produce different layouts");
    }

    @Test
    void floorCountDistributionIsBalanced() {
        // Sanity check that sequential seeds don't all roll the same floor count.
        // (java.util.Random.nextInt(small_bound) is biased for sequential small
        // seeds; the planner pre-mixes with SplitMix64 to compensate.)
        TemplateCatalog catalog = buildCatalog();
        int oneFloor = 0;
        for (long s = 0; s < 100; s++) {
            DungeonLayout layout = new DungeonStackPlanner(s, ANCHOR, SURFACE_Y, "classic", catalog)
                    .withSize(DungeonSize.SMALL).plan().orElseThrow();
            if (layout.getFloors().size() == 1) oneFloor++;
        }
        // SMALL is 1..2 floors. With 100 seeds we expect ~50/50; allow 25..75 as a wide band.
        assertTrue(oneFloor >= 25 && oneFloor <= 75,
                "Expected balanced floor distribution; got 1-floor=" + oneFloor + " out of 100");
    }

    @Test
    void smallSingleFloorDungeonHasNoTransitions() {
        // Force SMALL with min=1 floors via the seed: SMALL's minFloors=1, maxFloors=2.
        // Try seeds until we hit floors=1; deterministic so this won't flake.
        TemplateCatalog catalog = buildCatalog();
        for (long s = 0; s < 100; s++) {
            DungeonLayout layout = new DungeonStackPlanner(s, ANCHOR, SURFACE_Y, "classic", catalog)
                    .withSize(DungeonSize.SMALL).plan().orElseThrow();
            if (layout.getFloors().size() == 1) {
                assertTrue(layout.getTransitions().isEmpty(),
                        "Single-floor dungeon should have zero transitions");
                // Bottom floor still has an END room (the dungeon's terminal/boss room);
                // it's a marked endpoint but not linked to anything downstairs.
                FloorLayout only = layout.getFloors().get(0);
                boolean hasEnd = only.getRooms().stream().anyMatch(r -> r.getRole() == RoomRole.END);
                assertTrue(hasEnd, "Even single-floor dungeon must have a terminal END room");
                return;
            }
        }
        // If we never found a 1-floor roll in 100 seeds, something's wrong with the size enum.
        throw new AssertionError("Expected at least one 1-floor roll in 100 seeds with SMALL tier");
    }

    @Test
    void roomCountsAreReasonable() {
        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.LARGE).plan().orElseThrow();

        int totalRooms = 0;
        for (FloorLayout floor : layout.getFloors()) {
            totalRooms += floor.getRooms().size();
            // Every floor that isn't the last should have an END room.
            boolean isLast = floor.getFloorIndex() == layout.getFloors().size() - 1;
            boolean hasEnd = floor.getRooms().stream().anyMatch(r -> r.getRole() == RoomRole.END);
            if (!isLast) {
                assertTrue(hasEnd, "Floor " + floor.getFloorIndex() + " should have END room");
            }
        }
        assertTrue(totalRooms >= layout.getFloors().size() * 2,
                "Each floor should produce at least START + a few rooms");
    }

    @Test
    void catalogPicksAreDeterministic() {
        TemplateCatalog catalog = new TemplateCatalog();
        // Add multiple entrance candidates so picking is non-trivial.
        for (int i = 0; i < 5; i++) {
            TemplateEntry e = new TemplateEntry("dungeons2:entrances/option_" + i, 9, 9, 12);
            e.setMotifTags(java.util.List.of("classic"));
            e.setSizeTags(EnumSet.of(DungeonSize.MEDIUM));
            catalog.add(TemplateCatalog.Category.ENTRANCE, e);
        }
        TemplateEntry t = new TemplateEntry("dungeons2:transitions/only", 7, 7, 13);
        t.setMotifTags(java.util.List.of("classic"));
        catalog.add(TemplateCatalog.Category.TRANSITION, t);

        DungeonLayout a = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", catalog)
                .withSize(DungeonSize.MEDIUM).plan().orElseThrow();
        DungeonLayout b = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", catalog)
                .withSize(DungeonSize.MEDIUM).plan().orElseThrow();

        assertEquals(a.getEntrance().getTemplateId(), b.getEntrance().getTemplateId(),
                "Same seed must pick the same entrance from a multi-option catalog");
    }

    @Test
    void synthesizesEntranceWhenCatalogIsEmpty() {
        // Bootstrapping case: before any .nbt files exist, planning must still succeed.
        TemplateCatalog emptyCatalog = new TemplateCatalog();
        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", emptyCatalog)
                .withSize(DungeonSize.SMALL).plan().orElseThrow();

        assertNotNull(layout.getEntrance());
        assertTrue(layout.getEntrance().getTemplateId().contains("synthetic"),
                "Empty catalog should fall back to synthetic entrance id");
    }

    // -------- Phase 4b: assembled-entrance branch --------

    // A 7x7 entrance placed at an arbitrary world XZ, with its three door markers
    // (west / north / east edge-centers) in world coords. The planner sizes the
    // floor-0 grid and maps these into grid space itself.
    private static final int ENT_WORLD_X = 100;
    private static final int ENT_WORLD_Z = 200;
    private static final Rectangle2D ASM_ENTRANCE_WORLD_RECT =
            new Rectangle2D(ENT_WORLD_X, ENT_WORLD_Z, 7, 7);
    private static final List<Coords2D> ASM_DOOR_WORLD_CELLS = List.of(
            new Coords2D(ENT_WORLD_X, ENT_WORLD_Z + 3),      // west wall
            new Coords2D(ENT_WORLD_X + 3, ENT_WORLD_Z),      // north wall
            new Coords2D(ENT_WORLD_X + 6, ENT_WORLD_Z + 3)); // east wall
    private static final int ASM_FLOOR0_Y = 40;

    @Test
    void assembledEntranceDrivesFloorZeroGeometry() {
        DungeonLayout layout = new DungeonStackPlanner(
                SEED, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                .withFloorCount(1)
                .withAssembledEntrance(ASM_ENTRANCE_WORLD_RECT, ASM_DOOR_WORLD_CELLS, ASM_FLOOR0_Y)
                .plan().orElseThrow();

        assertTrue(layout.getEntrance().getTemplateId().contains("assembled"),
                "Assembled mode should tag the entrance id");

        FloorLayout floor0 = layout.getFloors().get(0);
        assertEquals(ASM_FLOOR0_Y, floor0.getFloorY(),
                "Floor-0 walking plane must equal the door-marker Y");

        // START room preserves the entrance's 7x7 footprint.
        RoomData start = floor0.getRooms().stream()
                .filter(r -> r.getRole() == RoomRole.START).findFirst().orElseThrow();
        assertEquals(7, start.getWidth(), "START width = entrance width");
        assertEquals(7, start.getDepth(), "START depth = entrance depth");

        // Every opened START doorway, mapped back to world via the layout anchor,
        // must be one of the supplied door world cells.
        int anchorX = layout.getAnchor().getX();
        int anchorZ = layout.getAnchor().getZ();
        Set<String> doorWorldKeys = ASM_DOOR_WORLD_CELLS.stream()
                .map(c -> c.getX() + "," + c.getY()).collect(Collectors.toSet());
        for (Coords2D door : start.getDoorways()) {
            String worldKey = (door.getX() + anchorX) + "," + (door.getY() + anchorZ);
            assertTrue(doorWorldKeys.contains(worldKey),
                    "Opened START doorway maps to world " + worldKey
                            + ", which must be one of the entrance's door markers");
        }
    }

    @Test
    void assembledEntranceGivesFloorZeroComparableRoomDensity() {
        // Floor 0's grid must scale with the size tier (not shrink to the entrance),
        // so its room count is comparable to a non-assembled floor of the same tier.
        DungeonLayout assembled = new DungeonStackPlanner(
                SEED, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                .withSize(DungeonSize.LARGE).withFloorCount(1)
                .withAssembledEntrance(ASM_ENTRANCE_WORLD_RECT, ASM_DOOR_WORLD_CELLS, ASM_FLOOR0_Y)
                .plan().orElseThrow();
        DungeonLayout synthetic = new DungeonStackPlanner(
                SEED, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                .withSize(DungeonSize.LARGE).withFloorCount(1)
                .plan().orElseThrow();

        int assembledRooms = assembled.getFloors().get(0).getRooms().size();
        int syntheticRooms = synthetic.getFloors().get(0).getRooms().size();
        // Allow slack, but the assembled floor must not be starved of rooms.
        assertTrue(assembledRooms >= syntheticRooms - 3,
                "Assembled floor-0 room count (" + assembledRooms
                        + ") should be comparable to synthetic (" + syntheticRooms + ")");
    }

    @Test
    void assembledEntranceOpensAtLeastOneCandidateDoor() {
        // Across seeds the maze must be able to open the entrance's candidates
        // (a zero-door START would mean the entrance never connects to the maze).
        boolean anyOpened = false;
        for (long s = 0; s < 50 && !anyOpened; s++) {
            DungeonLayout layout = new DungeonStackPlanner(
                    s, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                    .withFloorCount(1)
                    .withAssembledEntrance(ASM_ENTRANCE_WORLD_RECT, ASM_DOOR_WORLD_CELLS, ASM_FLOOR0_Y)
                    .plan().orElseThrow();
            RoomData start = layout.getFloors().get(0).getRooms().stream()
                    .filter(r -> r.getRole() == RoomRole.START).findFirst().orElseThrow();
            if (!start.getDoorways().isEmpty()) {
                anyOpened = true;
            }
        }
        assertTrue(anyOpened,
                "The maze should open at least one entrance door candidate across seeds");
    }

    // -------- Phase 8: jigsaw-assembled interior rooms --------

    /** Always succeeds with a fixed 7x7 footprint at the requested world position. */
    private static final DungeonStackPlanner.RoomAssembler FAKE_ROOM_ASSEMBLER = (worldX, worldY, worldZ, rand) -> {
        Rectangle2D worldFootprint = new Rectangle2D(worldX, worldZ, 7, 7);
        List<Coords2D> doors = List.of(
                new Coords2D(worldX, worldZ + 3),
                new Coords2D(worldX + 6, worldZ + 3));
        return Optional.of(new DungeonStackPlanner.AssembledRoom(worldFootprint, doors, List.of()));
    };

    @Test
    void roomAssemblerTagsRoomsWithTemplateId() {
        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withRoomAssembler(FAKE_ROOM_ASSEMBLER)
                .plan().orElseThrow();

        boolean anyTemplated = layout.getFloors().stream()
                .flatMap(f -> f.getRooms().stream())
                .anyMatch(r -> r.getTemplateId() != null);
        assertTrue(anyTemplated,
                "At least one room should be tagged with a template id when a RoomAssembler is supplied");
    }

    @Test
    void noRoomAssemblerLeavesRoomsUntemplated() {
        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.MEDIUM)
                .plan().orElseThrow();

        boolean anyTemplated = layout.getFloors().stream()
                .flatMap(f -> f.getRooms().stream())
                .anyMatch(r -> r.getTemplateId() != null);
        assertFalse(anyTemplated,
                "Without a RoomAssembler, no room should ever be tagged with a template id");
    }

    @Test
    void roomFlushAgainstFloorBoundaryIsRejected() {
        // A real in-game crash traced to a room's real assembled footprint landing
        // flush against the floor's own grid boundary (min corner at local 0,0) --
        // its door candidates then sit on the boundary row/column, which used to
        // crash MazeLevelGenerator2D.generateConnector's unbounded neighbor lookup
        // (fixed there too; this asserts the planner also never hands such a
        // footprint to the maze in the first place).
        DungeonStackPlanner.RoomAssembler flushAgainstOrigin = (worldX, worldY, worldZ, rand) ->
                Optional.of(new DungeonStackPlanner.AssembledRoom(
                        new Rectangle2D(planAnchorX(), planAnchorZ(), 7, 7), List.of(), List.of()));

        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withRoomAssembler(flushAgainstOrigin)
                .plan().orElseThrow();

        boolean anyTemplated = layout.getFloors().stream()
                .flatMap(f -> f.getRooms().stream())
                .anyMatch(r -> r.getTemplateId() != null);
        assertFalse(anyTemplated,
                "A room footprint flush against the floor's boundary must never be accepted as templated");
    }

    /** The planner's world anchor for a synthetic (non-assembled-entrance) layout is just {@code ANCHOR}. */
    private static int planAnchorX() {
        return ANCHOR.getX();
    }

    private static int planAnchorZ() {
        return ANCHOR.getZ();
    }

    @Test
    void assemblerlessRoomAttemptDoesNotBreakPlanning() {
        // An assembler that always refuses must degrade gracefully -- planning still
        // succeeds, just with zero templated rooms (ordinary procedural fill instead).
        DungeonStackPlanner.RoomAssembler refusing = (worldX, worldY, worldZ, rand) -> Optional.empty();
        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withRoomAssembler(refusing)
                .plan().orElseThrow();

        assertFalse(layout.getFloors().isEmpty());
        boolean anyTemplated = layout.getFloors().stream()
                .flatMap(f -> f.getRooms().stream())
                .anyMatch(r -> r.getTemplateId() != null);
        assertFalse(anyTemplated, "A refusing RoomAssembler should never produce a templated room");
    }

    @Test
    void roomAssemblerFootprintOutsideGridBoundsIsSkippedGracefully() {
        // Reproduces an in-game failure: vanilla is free to rotate the first piece
        // of a jigsaw assembly, which can shift the REAL bounding box's min-corner
        // away from the requested world XZ -- here simulated by returning a
        // footprint wildly offset from where it was asked to assemble. Must be
        // skipped (not treated as authoritative), never handed to the maze as-is.
        DungeonStackPlanner.RoomAssembler rotatedOffscreen = (worldX, worldY, worldZ, rand) ->
                Optional.of(new DungeonStackPlanner.AssembledRoom(
                        new Rectangle2D(worldX - 1000, worldZ - 1000, 7, 7), List.of(), List.of()));

        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withRoomAssembler(rotatedOffscreen)
                .plan().orElseThrow();

        assertFalse(layout.getFloors().isEmpty());
        boolean anyTemplated = layout.getFloors().stream()
                .flatMap(f -> f.getRooms().stream())
                .anyMatch(r -> r.getTemplateId() != null);
        assertFalse(anyTemplated,
                "An out-of-bounds RoomAssembler result must never produce a templated room");
    }

    @Test
    void transitionAssemblerFootprintOutsideGridBoundsFallsBackGracefully() {
        // Same failure mode as above, but for transitions -- a rotated real
        // footprint landing outside the floor's grid must NOT be handed to
        // MazeLevelGenerator2D as the fixed END/START room slot (that would abort
        // the entire floor's maze, and thus the whole dungeon's planning).
        DungeonStackPlanner.TransitionAssembler rotatedOffscreen = (worldX, worldY, worldZ, rand) ->
                Optional.of(new DungeonStackPlanner.AssembledTransition(
                        new Rectangle2D(worldX - 1000, worldZ - 1000, 9, 9),
                        List.of(), List.of(), List.of(), List.of()));

        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withFloorCount(3)
                .withTransitionAssembler(rotatedOffscreen)
                .plan().orElseThrow();

        assertEquals(3, layout.getFloors().size());
        assertEquals(2, layout.getTransitions().size());
        for (FloorLayout floor : layout.getFloors()) {
            int fw = floor.getFootprint().getWidth();
            int fd = floor.getFootprint().getHeight();
            for (RoomData room : floor.getRooms()) {
                assertTrue(room.getOriginX() >= 0 && room.getOriginZ() >= 0,
                        "Room must never have a negative local origin");
                assertTrue(room.getOriginX() + room.getWidth() <= fw
                                && room.getOriginZ() + room.getDepth() <= fd,
                        "Room must stay within its floor's footprint");
            }
        }
    }

    @Test
    void templatedRoomsStayWithinFloorFootprint() {
        // NOTE: this only checks templated rooms against the floor's own bounds --
        // it does NOT assert general non-overlap between all rooms on a floor.
        // Exploration while writing this test found that plain procedural fill
        // rooms can already overlap an existing room (reproducible with zero
        // RoomAssembler involvement -- a pre-existing MazeLevelGenerator2D issue,
        // out of scope for Phase 8; flagged separately).
        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.LARGE)
                .withRoomAssembler(FAKE_ROOM_ASSEMBLER)
                .plan().orElseThrow();

        for (FloorLayout floor : layout.getFloors()) {
            int fw = floor.getFootprint().getWidth();
            int fd = floor.getFootprint().getHeight();
            for (RoomData room : floor.getRooms()) {
                if (room.getTemplateId() == null) {
                    continue;
                }
                assertTrue(room.getOriginX() + room.getWidth() <= fw,
                        "Templated room extends past floor width");
                assertTrue(room.getOriginZ() + room.getDepth() <= fd,
                        "Templated room extends past floor depth");
            }
        }
    }

    @Test
    void sameSeedWithRoomAssemblerProducesIdenticalLayout() {
        TemplateCatalog catalog = buildCatalog();
        DungeonLayout a = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", catalog)
                .withSize(DungeonSize.MEDIUM).withRoomAssembler(FAKE_ROOM_ASSEMBLER).plan().orElseThrow();
        DungeonLayout b = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", catalog)
                .withSize(DungeonSize.MEDIUM).withRoomAssembler(FAKE_ROOM_ASSEMBLER).plan().orElseThrow();

        assertEquals(a.describe(), b.describe(),
                "Same seed + same RoomAssembler behavior must produce byte-identical layout");
    }

    @Test
    void noRoomDataPositionExceedsFloorFootprint() {
        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(DungeonSize.MEDIUM).plan().orElseThrow();

        for (FloorLayout floor : layout.getFloors()) {
            int fw = floor.getFootprint().getWidth();
            int fd = floor.getFootprint().getHeight();
            for (RoomData room : floor.getRooms()) {
                assertTrue(room.getOriginX() + room.getWidth() <= fw,
                        "Room " + room.getId() + " on floor " + floor.getFloorIndex()
                                + " extends past floor footprint width");
                assertTrue(room.getOriginZ() + room.getDepth() <= fd,
                        "Room " + room.getId() + " on floor " + floor.getFloorIndex()
                                + " extends past floor footprint depth");
            }
        }
    }
}
