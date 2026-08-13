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
import mod.gottsch.forge.dungeons2.core.data.TransitionData;
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
                // The bottom floor's endpoint is TERMINAL, not END: END means "a downstairs
                // transition fills this slot", and a single-floor dungeon has none.
                FloorLayout only = layout.getFloors().get(0);
                boolean hasEnd = only.getRooms().stream().anyMatch(r -> r.getRole() == RoomRole.TERMINAL);
                assertTrue(hasEnd, "Even single-floor dungeon must have a terminal room");
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

    /**
     * A {@code dungeons2:connector} (premade-door) cell must get NOTHING from a
     * neighbouring corridor -- no wall column and no pierced doorway column.
     * The template already built a real door there, and {@code DungeonStructure}
     * adds assembled pieces BEFORE procedural ones, so anything a corridor emits
     * lands on top of that door. An ordinary DOOR cell tolerates this only
     * because a {@code DungeonDoorPiece} runs last and rebuilds over it; a
     * premade cell has no such piece by design.
     */
    @Test
    void aPremadeDoorCellGetsNothingFromAneighbouringCorridor() {
        boolean sawAdjacentCorridor = false;

        for (long seed = 0; seed < 40; seed++) {
            DungeonLayout layout = new DungeonStackPlanner(
                    seed, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM).withFloorCount(1)
                    // All three markers are premade doors this time, not plain doors.
                    .withAssembledEntrance(ASM_ENTRANCE_WORLD_RECT, List.of(),
                            ASM_DOOR_WORLD_CELLS, ASM_FLOOR0_Y)
                    .plan().orElseThrow();

            int anchorX = layout.getAnchor().getX();
            int anchorZ = layout.getAnchor().getZ();
            Set<Coords2D> premadeLocal = ASM_DOOR_WORLD_CELLS.stream()
                    .map(c -> new Coords2D(c.getX() - anchorX, c.getY() - anchorZ))
                    .collect(Collectors.toSet());

            FloorLayout floor0 = layout.getFloors().get(0);
            for (var corridor : floor0.getCorridors()) {
                for (Coords2D premade : premadeLocal) {
                    assertFalse(corridor.getWallCells().contains(premade),
                            "seed " + seed + ": corridor walled over premade door " + premade);
                    assertFalse(corridor.getDoorCells().contains(premade),
                            "seed " + seed + ": corridor pierced premade door " + premade);
                }
                // Track whether this test is actually exercising the skip: a corridor
                // cell 8-adjacent to a premade cell is what would have emitted a column.
                for (Coords2D cell : corridor.getCells()) {
                    for (Coords2D premade : premadeLocal) {
                        if (Math.abs(cell.getX() - premade.getX()) <= 1
                                && Math.abs(cell.getY() - premade.getY()) <= 1) {
                            sawAdjacentCorridor = true;
                        }
                    }
                }
            }
        }

        assertTrue(sawAdjacentCorridor,
                "no seed put a corridor next to a premade door -- the assertion above never "
                        + "had anything to catch, so this test would pass vacuously");
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
    private static final DungeonStackPlanner.RoomAssembler FAKE_ROOM_ASSEMBLER = (worldX, worldY, worldZ, seed, commit) -> {
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
        //
        // The planner now reserves room slots ROOM_EDGE_MARGIN clear of the
        // boundary, so an assembler that HONOURS its contract can no longer land
        // there at all. What this stub does instead is ignore the position it is
        // asked for -- a contract violation -- which is the only remaining way to
        // reach the boundary, and must still be caught.
        DungeonStackPlanner.RoomAssembler flushAgainstOrigin = (worldX, worldY, worldZ, seed, commit) ->
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
        DungeonStackPlanner.RoomAssembler refusing = (worldX, worldY, worldZ, seed, commit) -> Optional.empty();
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
    void aRoomPrefabOffsetFromItsAssemblyPointIsCompensatedFor() {
        // Vanilla is free to ROTATE a prefab, which shifts the real bounding box's
        // min-corner away from the requested world XZ -- here exaggerated to 1000
        // blocks. This used to mean the slot was simply dropped (measured: 44% of
        // all room slots lost). The planner now measures that displacement with a
        // probe and anchors the real placement to compensate for it, so an offset
        // prefab is adopted rather than thrown away.
        DungeonStackPlanner.RoomAssembler rotatedOffscreen = (worldX, worldY, worldZ, seed, commit) ->
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
        assertTrue(anyTemplated,
                "A prefab offset from its assembly point must be compensated for, not dropped");

        // Compensated, not merely accepted: it still has to land inside the floor.
        for (FloorLayout floor : layout.getFloors()) {
            for (RoomData room : floor.getRooms()) {
                if (room.getTemplateId() == null) {
                    continue;
                }
                assertTrue(room.getOriginX() > 0 && room.getOriginZ() > 0
                                && room.getOriginX() + room.getWidth() < floor.getFootprint().getWidth()
                                && room.getOriginZ() + room.getDepth() < floor.getFootprint().getHeight(),
                        "A compensated prefab must land inside the floor, clear of its boundary");
            }
        }
    }

    @Test
    void transitionAssemblerFootprintTooLargeForTheGridFallsBackGracefully() {
        // Same failure mode as above, but for transitions. Note that a merely
        // OFFSET footprint is no longer a failure: the planner measures the offset
        // with a probe and compensates for it (that is how a chained transition
        // gets placed at all). What can still fail is a footprint too big to
        // reserve anywhere in the link's placement bound. It must NOT be handed to
        // MazeLevelGenerator2D as the fixed END/START room slot -- that would abort
        // the entire floor's maze, and thus the whole dungeon's planning.
        DungeonStackPlanner.TransitionAssembler tooBig = (worldX, worldY, worldZ, seed, commit) ->
                Optional.of(new DungeonStackPlanner.AssembledTransition(
                        new Rectangle2D(worldX, worldZ, 1000, 1000),
                        List.of(), List.of(), List.of(), List.of()));

        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withFloorCount(3)
                .withTransitionAssembler(tooBig)
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
    void aTransitionAssemblerThatIgnoresItsSeedIsNeverAdopted() {
        // The planner reserves the slot from what the measuring probe reported, then
        // relies on the commit call reproducing that same shape (TransitionAssembler's
        // contract) to land on it. An implementation that doesn't -- a pool switched
        // to terrain_matching projection, say, or a future refactor that stops
        // seeding the WorldgenRandom -- must be caught by the planner's guard and
        // dropped to the synthetic placeholder. Adopting a footprint the maze never
        // reserved is the fault that had corridors carved through a built template.
        DungeonStackPlanner.TransitionAssembler unstable = (worldX, worldY, worldZ, seed, commit) ->
                Optional.of(new DungeonStackPlanner.AssembledTransition(
                        commit ? new Rectangle2D(worldX - 1000, worldZ - 1000, 9, 9)
                               : new Rectangle2D(worldX, worldZ, 9, 9),
                        List.of(), List.of(), List.of(), List.of()));

        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withFloorCount(3)
                .withTransitionAssembler(unstable)
                .plan().orElseThrow();

        for (TransitionData t : layout.getTransitions()) {
            assertFalse(t.getTemplateId() != null && t.getTemplateId().contains("assembled"),
                    "A transition whose commit call contradicted its probe must not be adopted");
        }
    }

    @Test
    void templatedRoomsStayWithinFloorFootprint() {
        // Templated rooms specifically, against the floor's own bounds. General
        // pairwise room separation is covered by roomInteriorsNeverOverlap below.
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

    /** Shared cells between two rooms along X (0 = disjoint, 1 = one shared wall column). */
    private static int sharedCellsX(RoomData a, RoomData b) {
        return Math.min(a.getOriginX() + a.getWidth(), b.getOriginX() + b.getWidth())
                - Math.max(a.getOriginX(), b.getOriginX());
    }

    private static int sharedCellsZ(RoomData a, RoomData b) {
        return Math.min(a.getOriginZ() + a.getDepth(), b.getOriginZ() + b.getDepth())
                - Math.max(a.getOriginZ(), b.getOriginZ());
    }

    @Test
    void roomInteriorsNeverOverlap() {
        // A room's footprint INCLUDES its 1-cell wall ring, so two rooms sharing a
        // single wall line (exactly 1 shared cell on one axis) is normal and
        // deliberate -- MazeLevelGenerator2D.placeFillRooms scans a void grid that
        // counts WALL cells as free precisely so fill rooms pack wall-to-wall
        // against their neighbours, and a shared wall is where the door between
        // them goes. What must NEVER happen is two rooms eating into each other's
        // interiors: more than one shared cell on BOTH axes at once.
        //
        // Swept across every size tier, many seeds, with and without a
        // RoomAssembler, because the earlier "procedural rooms can overlap"
        // report turned out to be this shared-wall case counted as an overlap by
        // a plain box-intersects check. Keep the >1-on-both-axes criterion.
        for (DungeonSize size : DungeonSize.values()) {
            for (int s = 0; s < 25; s++) {
                assertNoInteriorOverlaps(size, SEED + s, false);
                assertNoInteriorOverlaps(size, SEED + s, true);
            }
        }
    }

    private void assertNoInteriorOverlaps(DungeonSize size, long seed, boolean withRoomAssembler) {
        DungeonStackPlanner planner = new DungeonStackPlanner(seed, ANCHOR, SURFACE_Y, "classic", buildCatalog())
                .withSize(size);
        if (withRoomAssembler) {
            planner.withRoomAssembler(FAKE_ROOM_ASSEMBLER);
        }
        DungeonLayout layout = planner.plan().orElseThrow();

        for (FloorLayout floor : layout.getFloors()) {
            List<RoomData> rooms = floor.getRooms();
            for (int i = 0; i < rooms.size(); i++) {
                for (int j = i + 1; j < rooms.size(); j++) {
                    RoomData a = rooms.get(i);
                    RoomData b = rooms.get(j);
                    boolean interiorsOverlap = sharedCellsX(a, b) > 1 && sharedCellsZ(a, b) > 1;
                    assertFalse(interiorsOverlap,
                            "Rooms " + a.getId() + " and " + b.getId() + " overlap beyond a shared wall"
                                    + " (size=" + size + ", seed=" + seed
                                    + ", assembler=" + withRoomAssembler
                                    + ", floor=" + floor.getFloorIndex() + "): "
                                    + a.getOriginX() + "," + a.getOriginZ() + " " + a.getWidth() + "x" + a.getDepth()
                                    + " vs "
                                    + b.getOriginX() + "," + b.getOriginZ() + " " + b.getWidth() + "x" + b.getDepth());
                }
            }
        }
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
