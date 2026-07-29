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

import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.EntranceData;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.data.TemplateEntry;
import mod.gottsch.forge.dungeons2.core.data.TransitionData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Cell;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.ILevel2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Room2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Top-down planner that orchestrates a full multi-floor dungeon as a
 * {@link DungeonLayout} POJO.
 *
 * <p>Pure POJO planner &mdash; produces only data, writes no blocks, has no
 * Minecraft imports. Block placement is the Forge shell's job (Phase 2/3).</p>
 *
 * <h2>Algorithm</h2>
 * <ol>
 *     <li>Seed the RNG from the structure's chunk pos + world seed.</li>
 *     <li>Roll {@link DungeonSize size tier} (or use the provided one).</li>
 *     <li>Roll floor count from the tier's range.</li>
 *     <li>Roll per-floor XZ footprints (odd dimensions).</li>
 *     <li>Compute per-floor Y values descending from {@code surfaceY}.</li>
 *     <li>Pick an {@link EntranceData entrance template}; reserve its footprint
 *         as floor 0's {@link RoomRole#START} slot.</li>
 *     <li>For each floor: pick a {@link TransitionData transition template}
 *         (except the bottom floor), place its {@link RoomRole#END} footprint
 *         within the floor, then run {@link MazeLevelGenerator2D} with the
 *         start/end constraints derived from the upstairs and downstairs
 *         templates.</li>
 *     <li>Convert each maze's {@link ILevel2D} into a {@link FloorLayout}
 *         (rooms, corridors, doors).</li>
 *     <li>Emit one {@link TransitionData} per inter-floor link.</li>
 *     <li>Compute the overall 3D bounding box.</li>
 * </ol>
 *
 * <p><strong>Determinism:</strong> all randomness flows from a single seeded
 * {@link Random}. Same seed &rArr; same {@link DungeonLayout}, byte-identical.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class DungeonStackPlanner {

    // Tunable later; for Phase 1 these are reasonable defaults.
    private static final int DEFAULT_FLOOR_HEIGHT = 10;
    private static final int DEFAULT_GAP_BETWEEN_FLOORS = 2;
    private static final int DEFAULT_ENTRANCE_DROP = 12;
    /** Max attempts to place a START/END room within a footprint without overlap. */
    private static final int PLACEMENT_ATTEMPTS = 20;

    private final long seed;
    private final ICoords anchor;
    private final int surfaceY;
    private final String motifValue;
    private final TemplateCatalog catalog;
    private DungeonSize forcedSize;
    private Integer forcedFloorCount;
    private int floorHeight = DEFAULT_FLOOR_HEIGHT;
    private int gapBetweenFloors = DEFAULT_GAP_BETWEEN_FLOORS;
    private int entranceDrop = DEFAULT_ENTRANCE_DROP;
    /**
     * Corridor width in cells, achieved via dilation. This default (3) only
     * applies to callers that never call {@link #withCorridorWidth(int)} (e.g.
     * tests); production worldgen ({@code DungeonStructure}) and the debug
     * command ({@code SpawnDungeonCommand}) both override it from the
     * datapack-driven {@code DungeonGenerationConfigHelper} (backed by the
     * {@code dungeons2:generation_config} registry), making this config-driven
     * (bumped 2->3 Jul 24; both widths verified safe by the same regression
     * tests, see RoomCorridorAdjacencyTest#dilatedCorridorsAreStillNeverAdjacentToRooms
     * and CorridorRoomSealTest#dilatedCorridorsStillDoNotReachRoomInteriors). 1 =
     * classic 1-wide, still available via withCorridorWidth(1).
     */
    private int corridorCells = 3;

    // -------- Phase 4b: assembled-entrance overrides (all-or-nothing) --------
    // When set (see withAssembledEntrance), floor 0 is driven by the jigsaw-
    // assembled entrance instead of the catalog pick + surfaceY-derived drop.
    // Inputs are in WORLD coordinates; the planner sizes floor 0's grid (>= the
    // size-tier's rolled footprint so room density matches the other floors),
    // centers the entrance in it, maps the door cells into grid space, and hands
    // the resulting world anchor back via DungeonLayout#getAnchor.
    private Rectangle2D assembledEntranceWorldRect;   // world (minX, minZ, w, d)
    private List<Coords2D> assembledDoorWorldCells;   // world (x, z) of dungeons2:door markers
    private List<Coords2D> assembledPremadeWorldCells; // world (x, z) of dungeons2:connector markers
    private Integer assembledFloor0Y;                 // world Y of the walking plane
    /** Routing cells reserved around the entrance on each side of floor 0. */
    private static final int ENTRANCE_MARGIN = 8;

    public DungeonStackPlanner(long seed, ICoords anchor, int surfaceY,
                               String motifValue, TemplateCatalog catalog) {
        this.seed = seed;
        this.anchor = anchor;
        this.surfaceY = surfaceY;
        this.motifValue = motifValue;
        this.catalog = catalog;
    }

    /** Force a specific size tier (skip the random roll). Optional. */
    public DungeonStackPlanner withSize(DungeonSize size) {
        this.forcedSize = size;
        return this;
    }

    /**
     * Force a specific floor count (skip the per-tier random roll). Clamped to
     * at least 1. Useful for the debug command and single-floor visual tests.
     */
    public DungeonStackPlanner withFloorCount(int count) {
        this.forcedFloorCount = Math.max(1, count);
        return this;
    }

    public DungeonStackPlanner withFloorHeight(int floorHeight) {
        this.floorHeight = floorHeight;
        return this;
    }

    public DungeonStackPlanner withGapBetweenFloors(int gap) {
        this.gapBetweenFloors = gap;
        return this;
    }

    /**
     * Fallback drop used when the entrance catalog is empty &mdash; the planner
     * still emits a "synthetic" entrance with this height so v1 can boot before
     * template files exist.
     */
    public DungeonStackPlanner withEntranceDrop(int drop) {
        this.entranceDrop = drop;
        return this;
    }

    /**
     * Override corridor width in cells (1 = classic 1-wide, 2 = 2-wide,
     * 3 = 3-wide, etc.). Default is 2. Wider corridors look better but
     * change the maze's effective topology (parallel corridors may merge).
     */
    public DungeonStackPlanner withCorridorWidth(int cells) {
        this.corridorCells = Math.max(1, cells);
        return this;
    }

    /**
     * Phase 4b: drive floor 0 from the jigsaw-assembled entrance instead of the
     * catalog pick. Arguments are in WORLD coordinates (the caller reads them off
     * the assembled pieces, rotation already baked in); the planner does the grid
     * sizing and world&rarr;grid mapping so floor-0 room density still scales with
     * the dungeon's size tier.
     *
     * @param entranceWorldRect   the door-carrying piece's XZ extent in world cells
     * @param doorWorldCells      the {@code dungeons2:door} marker world cells; the
     *                            maze opens up to the START room's degrees of them
     * @param floor0WalkingPlaneY world Y of floor 0's walking plane (= the door
     *                            markers' Y), so the maze meets the entrance's bottom
     */
    public DungeonStackPlanner withAssembledEntrance(Rectangle2D entranceWorldRect,
                                                     List<Coords2D> doorWorldCells, int floor0WalkingPlaneY) {
        return withAssembledEntrance(entranceWorldRect, doorWorldCells, List.of(), floor0WalkingPlaneY);
    }

    /**
     * Full form of {@link #withAssembledEntrance(Rectangle2D, List, int)}, adding
     * {@code dungeons2:connector} ("premade door") marker cells: candidates that
     * participate in the maze's normal candidate-doorway selection exactly like
     * {@code dungeons2:door}, but whose template already has a fully-built door in
     * place, so no {@code DungeonDoorPiece} is generated for whichever ones the
     * maze picks (see {@link #convertLevel}).
     */
    public DungeonStackPlanner withAssembledEntrance(Rectangle2D entranceWorldRect, List<Coords2D> doorWorldCells,
                                                     List<Coords2D> premadeWorldCells, int floor0WalkingPlaneY) {
        this.assembledEntranceWorldRect = entranceWorldRect;
        this.assembledDoorWorldCells = doorWorldCells;
        this.assembledPremadeWorldCells = premadeWorldCells;
        this.assembledFloor0Y = floor0WalkingPlaneY;
        return this;
    }

    private boolean hasAssembledEntrance() {
        return assembledEntranceWorldRect != null;
    }

    // -------- jigsaw-assembled transitions (optional) --------
    private TransitionAssembler transitionAssembler;

    /**
     * Supplies a callback that assembles a transition (via real vanilla
     * {@code JigsawPlacement}, in the Forge shell) at a candidate world position
     * and returns its real geometry. Kept as a mod-owned-types-only interface so
     * the planner stays a pure POJO &mdash; the Minecraft-facing implementation
     * lives in {@code DungeonStructure}. When absent (or when a specific call
     * returns empty), the planner falls back to its synthetic placeholder
     * footprint, the same graceful degradation {@link #hasAssembledEntrance()}
     * already has for the entrance.
     */
    public DungeonStackPlanner withTransitionAssembler(TransitionAssembler assembler) {
        this.transitionAssembler = assembler;
        return this;
    }

    @FunctionalInterface
    public interface TransitionAssembler {
        Optional<AssembledTransition> assemble(int worldX, int worldY, int worldZ, Random random);
    }

    /**
     * Real geometry read back from an assembled transition; world-space.
     * {@code topPremadeWorldCells}/{@code bottomPremadeWorldCells} are
     * {@code dungeons2:connector} ("premade door") markers -- treated as extra
     * candidate doorways the maze may pick, but any it does pick get no
     * {@code DungeonDoorPiece} (see {@link #convertLevel}), since the template
     * already has a real door built in at that cell.
     */
    public record AssembledTransition(Rectangle2D worldFootprint,
                                      List<Coords2D> topDoorWorldCells,
                                      List<Coords2D> bottomDoorWorldCells,
                                      List<Coords2D> topPremadeWorldCells,
                                      List<Coords2D> bottomPremadeWorldCells) {
    }

    // -------- Phase 8: jigsaw-assembled interior ("NORMAL") rooms (optional) --------
    private RoomAssembler roomAssembler;

    /**
     * Supplies a callback that assembles an interior-room prefab (via real vanilla
     * {@code JigsawPlacement}, in the Forge shell) at a candidate world position and
     * returns its real geometry. Mirrors {@link #withTransitionAssembler} exactly,
     * except a room has a single Y anchor (the floor's own walking plane) and no
     * top/bottom split &mdash; it's a single, self-contained piece, not a chain.
     * When absent (or a specific attempt returns empty), that candidate slot is
     * simply skipped and covered by an ordinary procedural fill room instead, the
     * same graceful degradation {@link #hasAssembledEntrance()} already has.
     */
    public DungeonStackPlanner withRoomAssembler(RoomAssembler assembler) {
        this.roomAssembler = assembler;
        return this;
    }

    @FunctionalInterface
    public interface RoomAssembler {
        Optional<AssembledRoom> assemble(int worldX, int worldY, int worldZ, Random random);
    }

    /**
     * Real geometry read back from an assembled room; world-space.
     * {@code premadeWorldCells} are {@code dungeons2:connector} markers &mdash;
     * extra candidate doorways the maze may pick, but any it does pick get no
     * {@code DungeonDoorPiece} (see {@link #convertLevel}), since the template
     * already has a real door built in at that cell.
     */
    public record AssembledRoom(Rectangle2D worldFootprint,
                               List<Coords2D> doorWorldCells,
                               List<Coords2D> premadeWorldCells) {
    }

    /** Attempts per floor to place a jigsaw-assembled room before falling back to procedural fill rooms. */
    private static final int ROOM_TEMPLATE_ATTEMPTS_PER_FLOOR = 2;
    private static final int ROOM_TEMPLATE_MIN_SIZE = 7;
    private static final int ROOM_TEMPLATE_MAX_SIZE = 15;

    private static List<Coords2D> toLocalCells(List<Coords2D> worldCells, ICoords planAnchor) {
        List<Coords2D> local = new ArrayList<>(worldCells.size());
        for (Coords2D c : worldCells) {
            local.add(new Coords2D(c.getX() - planAnchor.getX(), c.getY() - planAnchor.getZ()));
        }
        return local;
    }

    private static int makeOdd(int v) {
        return (v & 1) == 0 ? v + 1 : v;
    }

    private static int makeEven(int v) {
        return v - (v & 1);
    }

    /**
     * Runs the full top-down planning pass. Returns {@link Optional#empty()} if
     * any floor's maze fails to generate.
     */
    public Optional<DungeonLayout> plan() {
        // Pre-mix the seed: java.util.Random's nextInt(small_bound) is biased
        // for sequential small seeds (the seed-scrambling step doesn't whiten
        // the high bits of the first call enough). SplitMix64 fixes this and
        // keeps the planner deterministic for any caller-supplied seed.
        Random random = new Random(mixSeed(seed, -1));

        DungeonSize size = forcedSize != null ? forcedSize : rollSize(random);
        int floorCount = forcedFloorCount != null
                ? forcedFloorCount
                : rollInRange(random, size.getMinFloors(), size.getMaxFloors());

        // Per-floor footprints (odd-sized so the maze planner accepts them).
        List<Rectangle2D> footprints = new ArrayList<>(floorCount);
        for (int i = 0; i < floorCount; i++) {
            int w = oddInRange(random, size.getMinFootprint(), size.getMaxFootprint());
            int h = oddInRange(random, size.getMinFootprint(), size.getMaxFootprint());
            footprints.add(new Rectangle2D(0, 0, w, h));
        }

        // Entrance footprint + floor-0 Y come from the assembled entrance when
        // present; otherwise from the catalog pick (or a synthetic fallback).
        Rectangle2D entranceLocalFootprint;
        List<Coords2D> assembledCandidateCells = null;   // grid space, floor 0 only
        Set<Coords2D> assembledPremadeLocalCells = Set.of(); // grid space subset of the above; no DungeonDoorPiece
        ICoords planAnchor = anchor;                      // world anchor for the layout
        int[] floorCeilings = new int[floorCount];
        int[] floorFloors = new int[floorCount];
        if (hasAssembledEntrance()) {
            int ew = assembledEntranceWorldRect.getWidth();
            int ed = assembledEntranceWorldRect.getHeight();
            // Floor 0's grid is the larger of the rolled footprint and the entrance
            // footprint plus routing margin, so room density matches other floors
            // while still fitting the entrance (and the terminal END room).
            int gridW = makeOdd(Math.max(footprints.get(0).getWidth(), ew + 2 * ENTRANCE_MARGIN));
            int gridH = makeOdd(Math.max(footprints.get(0).getHeight(), ed + 2 * ENTRANCE_MARGIN));
            footprints.set(0, new Rectangle2D(0, 0, gridW, gridH));

            // Center the entrance in the grid on an even origin (the maze rejects
            // odd-origin rooms), then derive the world anchor so grid-local (0,0)
            // maps to world such that the entrance lands where it was assembled.
            int startMinX = makeEven((gridW - ew) / 2);
            int startMinZ = makeEven((gridH - ed) / 2);
            entranceLocalFootprint = new Rectangle2D(startMinX, startMinZ, ew, ed);
            int worldAnchorX = assembledEntranceWorldRect.getMinX() - startMinX;
            int worldAnchorZ = assembledEntranceWorldRect.getMinY() - startMinZ;
            planAnchor = new Coords(worldAnchorX, 0, worldAnchorZ);

            assembledCandidateCells = new ArrayList<>(assembledDoorWorldCells.size() + assembledPremadeWorldCells.size());
            for (Coords2D d : assembledDoorWorldCells) {
                assembledCandidateCells.add(new Coords2D(d.getX() - worldAnchorX, d.getY() - worldAnchorZ));
            }
            Set<Coords2D> premadeSet = new HashSet<>();
            for (Coords2D d : assembledPremadeWorldCells) {
                Coords2D local = new Coords2D(d.getX() - worldAnchorX, d.getY() - worldAnchorZ);
                assembledCandidateCells.add(local);
                premadeSet.add(local);
            }
            assembledPremadeLocalCells = premadeSet;

            // The door markers' Y is floor 0's walking plane; ceiling is one
            // floor-height above it. Lower floors still stack downward below.
            floorFloors[0] = assembledFloor0Y;
            floorCeilings[0] = floorFloors[0] + floorHeight - 1;
        } else {
            TemplateEntry entranceTemplate = catalog.pick(
                    TemplateCatalog.Category.ENTRANCE, motifValue, size, random);
            int entranceFootprintW = entranceTemplate != null ? entranceTemplate.getWidth() : 9;
            int entranceFootprintD = entranceTemplate != null ? entranceTemplate.getDepth() : 9;
            int entranceHeight = entranceTemplate != null ? entranceTemplate.getHeight() : entranceDrop;

            floorCeilings[0] = surfaceY - entranceHeight;
            floorFloors[0] = floorCeilings[0] - floorHeight + 1;

            // Place the entrance footprint centered on floor 0.
            entranceLocalFootprint = centerWithin(
                    footprints.get(0), entranceFootprintW, entranceFootprintD);
            if (entranceLocalFootprint == null) {
                return Optional.empty();
            }
        }
        for (int i = 1; i < floorCount; i++) {
            floorCeilings[i] = floorFloors[i - 1] - gapBetweenFloors - 1;
            floorFloors[i] = floorCeilings[i] - floorHeight + 1;
        }

        // Resolve transitions up front for floors 0..N-2 (each link). A transition's
        // XZ rect must fit in BOTH its upper and lower floor grids (since the same
        // rect is reused as floor i's END and floor i+1's START).
        //
        // When a transitionAssembler is supplied, the candidate rect below is only
        // a rough anchor/overlap-avoidance guess (fixed 7x7) used to pick a world
        // position to assemble AT; the real, jigsaw-assembled footprint (whatever
        // size the actual template turns out to be) is authoritative once known.
        // Falls back to the guessed placeholder rect itself when no assembler is
        // set, or a specific transition fails to assemble (mirrors
        // hasAssembledEntrance()'s graceful degradation).
        final int fallbackTransitionSize = 7;
        List<Rectangle2D> transitionLocalFootprints = new ArrayList<>(floorCount - 1);
        List<List<Coords2D>> transitionTopDoorLocalCells = new ArrayList<>(floorCount - 1);
        List<List<Coords2D>> transitionBottomDoorLocalCells = new ArrayList<>(floorCount - 1);
        // Premade ("dungeons2:connector") cells are a subset of the door lists above --
        // real candidates the maze may pick, but never given a DungeonDoorPiece
        // (see convertLevel). Tracked separately per floor-side so convertLevel
        // knows which chosen doors to skip.
        List<Set<Coords2D>> transitionTopPremadeLocalCells = new ArrayList<>(floorCount - 1);
        List<Set<Coords2D>> transitionBottomPremadeLocalCells = new ArrayList<>(floorCount - 1);
        List<String> transitionTemplateIds = new ArrayList<>(floorCount - 1);
        for (int i = 0; i < floorCount - 1; i++) {
            int boundW = Math.min(footprints.get(i).getWidth(), footprints.get(i + 1).getWidth());
            int boundH = Math.min(footprints.get(i).getHeight(), footprints.get(i + 1).getHeight());
            Rectangle2D placementBound = new Rectangle2D(0, 0, boundW, boundH);
            // Per-floor grids share origin (0,0), so a rect from floor i-1 is
            // directly usable as a reservation for floor i's overlap check.
            Rectangle2D startReserved = (i == 0) ? entranceLocalFootprint
                    : transitionLocalFootprints.get(i - 1);
            Rectangle2D candidate = placeAvoidingStart(
                    placementBound, fallbackTransitionSize, fallbackTransitionSize, startReserved, random);
            if (candidate == null) {
                return Optional.empty();
            }

            Rectangle2D finalFootprint = candidate;
            List<Coords2D> topDoors = List.of();
            List<Coords2D> bottomDoors = List.of();
            Set<Coords2D> topPremade = Set.of();
            Set<Coords2D> bottomPremade = Set.of();
            String templateId = "dungeons2:transitions/synthetic";

            if (transitionAssembler != null) {
                int worldX = planAnchor.getX() + candidate.getMinX();
                int worldZ = planAnchor.getZ() + candidate.getMinY();
                // Anchor at the LOWER floor's walking plane (floorFloors[i + 1]),
                // not the upper floor's -- ladder1/stairs_1 are authored with local
                // Y=0 at the lower floor's plane (confirmed working in-game before
                // this migration), so assembly must start there and chain UPWARD,
                // not start at the upper floor and chain down.
                Optional<AssembledTransition> assembled =
                        transitionAssembler.assemble(worldX, floorFloors[i + 1], worldZ, random);
                if (assembled.isPresent()) {
                    AssembledTransition at = assembled.get();
                    Rectangle2D wf = at.worldFootprint();
                    Rectangle2D realFootprint = new Rectangle2D(
                            wf.getMinX() - planAnchor.getX(), wf.getMinY() - planAnchor.getZ(),
                            wf.getWidth(), wf.getHeight());
                    if (withinLocalBounds(realFootprint, placementBound)
                            && (startReserved == null || !realFootprint.intersects(startReserved))) {
                        finalFootprint = realFootprint;
                        List<Coords2D> topPremadeLocal = toLocalCells(at.topPremadeWorldCells(), planAnchor);
                        List<Coords2D> bottomPremadeLocal = toLocalCells(at.bottomPremadeWorldCells(), planAnchor);
                        List<Coords2D> topDoorsMerged = new ArrayList<>(toLocalCells(at.topDoorWorldCells(), planAnchor));
                        topDoorsMerged.addAll(topPremadeLocal);
                        List<Coords2D> bottomDoorsMerged = new ArrayList<>(toLocalCells(at.bottomDoorWorldCells(), planAnchor));
                        bottomDoorsMerged.addAll(bottomPremadeLocal);
                        topDoors = topDoorsMerged;
                        bottomDoors = bottomDoorsMerged;
                        topPremade = new HashSet<>(topPremadeLocal);
                        bottomPremade = new HashSet<>(bottomPremadeLocal);
                        templateId = "dungeons2:transitions/assembled";
                    }
                    // else: the real footprint collided with the reserved start
                    // slot, or (e.g. vanilla rotated the assembled piece) landed
                    // outside the floor's own grid bounds -- keep the synthetic
                    // placeholder computed above.
                }
            }

            transitionLocalFootprints.add(finalFootprint);
            transitionTopDoorLocalCells.add(topDoors);
            transitionBottomDoorLocalCells.add(bottomDoors);
            transitionTopPremadeLocalCells.add(topPremade);
            transitionBottomPremadeLocalCells.add(bottomPremade);
            transitionTemplateIds.add(templateId);
        }

        // Run the maze per floor.
        DungeonLayout layout = new DungeonLayout();
        layout.setMotifValue(motifValue);
        layout.setSize(size);
        layout.setAnchor(planAnchor);
        layout.setSeed(seed);

        // EntranceData (uses world-space Y). In assembled mode the entrance
        // geometry is authoritative and rotation is baked into the placed pieces;
        // otherwise it reflects the synthetic fallback (no entrance is rendered).
        EntranceData entrance = hasAssembledEntrance()
                ? new EntranceData("dungeons2:entrance/assembled", surfaceY,
                        floorFloors[0], entranceLocalFootprint, 0)
                : new EntranceData("dungeons2:entrances/synthetic", surfaceY,
                        floorCeilings[0], entranceLocalFootprint, rollRotation(random));
        layout.setEntrance(entrance);

        for (int i = 0; i < floorCount; i++) {
            Rectangle2D footprint = footprints.get(i);

            // START footprint: floor 0 uses entrance; lower floors inherit from the upstairs transition.
            Rectangle2D startFootprint = (i == 0)
                    ? entranceLocalFootprint
                    : transitionLocalFootprints.get(i - 1);
            // END footprint: a downstairs transition for non-bottom floors, OR a terminal
            // room (boss room) on the bottom floor. We always supply an end so the maze
            // planner doesn't have to auto-generate one (which often fails in small footprints
            // due to its hardcoded 5-attempt cap).
            Rectangle2D endFootprint;
            if (i < floorCount - 1) {
                endFootprint = transitionLocalFootprints.get(i);
            } else {
                // Bottom-floor terminal room. Synthetic 7x7 placeholder for now;
                // can become its own template category in a later phase.
                endFootprint = placeAvoidingStart(footprint, 7, 7, startFootprint, random);
                if (endFootprint == null) {
                    return Optional.empty();
                }
            }

            IRoom2D startRoom = new Room2D(startFootprint);
            startRoom.setStart(true);
            // The entrance room may branch into the maze, so give it a few door
            // candidates. A Room2D defaults to degrees=1, which made the supplied
            // anchor rooms fragile (a single connector that often got culled before
            // a door was built -> orphaned room).
            startRoom.setDegrees(3);
            // Floor 0's START room restricts its doorways to the assembled
            // entrance's dungeons2:door marker cells; other floors' START rooms
            // (inherited from the upstairs transition) do the same using that
            // transition's real bottom-floor door markers, when it assembled via
            // real jigsaw placement -- the maze opens up to getDegrees() of them
            // and walls off the rest.
            if (i == 0 && assembledCandidateCells != null) {
                startRoom.setCandidateDoorways(assembledCandidateCells);
            } else if (i > 0 && !transitionBottomDoorLocalCells.get(i - 1).isEmpty()) {
                startRoom.setCandidateDoorways(transitionBottomDoorLocalCells.get(i - 1));
            }
            IRoom2D endRoom = new Room2D(endFootprint);
            endRoom.setEnd(true);
            // The terminal/boss room keeps a single entrance by design (one path in).
            // Reachability is guaranteed by MazeLevelGenerator2D's connectivity pass,
            // not by adding extra doors.
            endRoom.setDegrees(1);
            // Non-bottom floors' END room is the downstairs transition; restrict its
            // doorways to that transition's real top-floor door markers the same way.
            if (i < floorCount - 1 && !transitionTopDoorLocalCells.get(i).isEmpty()) {
                endRoom.setCandidateDoorways(transitionTopDoorLocalCells.get(i));
            }

            // Phase 8: try to place a few jigsaw-assembled interior rooms before the
            // maze runs, feeding them in as MazeLevelGenerator2D's suppliedRooms --
            // a mechanism the maze already supports generically (validated against
            // boundary/overlap and folded into the region graph exactly like any
            // other room; see MazeLevelGenerator2D's suppliedRooms handling). Each
            // successfully-placed template room becomes a reservation for the next
            // attempt, on top of this floor's start/end footprints. Graceful
            // degradation: no assembler, or a failed/colliding attempt, just skips
            // that slot -- ordinary procedural fill rooms cover the gap as today.
            List<IRoom2D> templateRooms = new ArrayList<>();
            Map<IRoom2D, String> templateIdByRoom = new IdentityHashMap<>();
            Set<Coords2D> templateRoomPremadeLocalCells = new HashSet<>();
            if (roomAssembler != null) {
                List<Rectangle2D> roomReserved = new ArrayList<>();
                roomReserved.add(startFootprint);
                roomReserved.add(endFootprint);
                for (int attempt = 0; attempt < ROOM_TEMPLATE_ATTEMPTS_PER_FLOOR; attempt++) {
                    int rw = oddInRange(random, ROOM_TEMPLATE_MIN_SIZE, ROOM_TEMPLATE_MAX_SIZE);
                    int rd = oddInRange(random, ROOM_TEMPLATE_MIN_SIZE, ROOM_TEMPLATE_MAX_SIZE);
                    Rectangle2D roomCandidate = placeAvoidingReserved(footprint, rw, rd, roomReserved, random);
                    if (roomCandidate == null) {
                        continue;
                    }
                    int worldX = planAnchor.getX() + roomCandidate.getMinX();
                    int worldZ = planAnchor.getZ() + roomCandidate.getMinY();
                    Optional<AssembledRoom> assembledRoom = roomAssembler.assemble(worldX, floorFloors[i], worldZ, random);
                    if (assembledRoom.isEmpty()) {
                        continue;
                    }
                    AssembledRoom ar = assembledRoom.get();
                    Rectangle2D wf = ar.worldFootprint();
                    Rectangle2D realFootprint = new Rectangle2D(
                            wf.getMinX() - planAnchor.getX(), wf.getMinY() - planAnchor.getZ(),
                            wf.getWidth(), wf.getHeight());
                    // Reject a footprint flush against the floor's own outer boundary
                    // (min edge touching 0, or max edge touching the floor's own
                    // width/height) -- a door candidate on that room's edge would then
                    // sit exactly on the grid's boundary row/column, which used to crash
                    // MazeLevelGenerator2D.generateConnector's unbounded neighbor lookup
                    // (now fixed defensively there too, but a room shouldn't visually
                    // abut the raw map edge regardless).
                    boolean touchesFloorEdge = realFootprint.getMinX() <= 0 || realFootprint.getMinY() <= 0
                            || realFootprint.getMinX() + realFootprint.getWidth() >= footprint.getWidth()
                            || realFootprint.getMinY() + realFootprint.getHeight() >= footprint.getHeight();
                    if (!withinLocalBounds(realFootprint, footprint) || touchesFloorEdge
                            || !noIntersections(realFootprint, roomReserved)) {
                        // Real footprint (whatever size/position the actual template
                        // turned out to be, e.g. if vanilla rotated it) either landed
                        // outside this floor's grid (or flush against its boundary) or
                        // collided with an existing reservation -- skip this slot.
                        continue;
                    }

                    List<Coords2D> premadeLocal = toLocalCells(ar.premadeWorldCells(), planAnchor);
                    List<Coords2D> doorsLocal = new ArrayList<>(toLocalCells(ar.doorWorldCells(), planAnchor));
                    doorsLocal.addAll(premadeLocal);

                    IRoom2D templateRoom = new Room2D(realFootprint);
                    // A few candidate doors so the room can branch, mirroring the
                    // entrance's startRoom.setDegrees(3) rationale.
                    templateRoom.setDegrees(3);
                    templateRoom.setCandidateDoorways(doorsLocal);

                    templateRooms.add(templateRoom);
                    templateIdByRoom.put(templateRoom, "dungeons2:rooms/assembled");
                    templateRoomPremadeLocalCells.addAll(premadeLocal);
                    roomReserved.add(realFootprint);
                }
            }

            final int floorIndex = i;
            final IRoom2D startRef = startRoom;
            final IRoom2D endRef = endRoom;
            final List<IRoom2D> suppliedTemplateRooms = templateRooms;
            MazeLevelGenerator2D mazeGen = new MazeLevelGenerator2D.Builder()
                    .with($ -> {
                        $.width = footprint.getWidth();
                        $.height = footprint.getHeight();
                        $.numberOfRooms = pickNumberOfRooms(size, footprint);
                        $.startRoom = startRef;
                        $.endRoom = endRef;
                        $.suppliedRooms = suppliedTemplateRooms;
                    })
                    .corridorWidth(corridorCells)
                    .seed(mixSeed(seed, floorIndex))
                    .build();

            Optional<ILevel2D> levelOpt = mazeGen.generate();
            if (levelOpt.isEmpty()) {
                return Optional.empty();
            }

            // Cells the maze may open as a door but must NOT get a DungeonDoorPiece,
            // because the assembled template already has a real, pre-built door
            // there (dungeons2:connector markers) -- this floor's START slot (from
            // the entrance, floor 0 only, or the incoming transition's bottom side),
            // its END slot (the outgoing transition's top side), plus any
            // jigsaw-assembled interior rooms placed above.
            Set<Coords2D> premadeCells = new HashSet<>();
            if (i == 0) {
                premadeCells.addAll(assembledPremadeLocalCells);
            } else {
                premadeCells.addAll(transitionBottomPremadeLocalCells.get(i - 1));
            }
            if (i < floorCount - 1) {
                premadeCells.addAll(transitionTopPremadeLocalCells.get(i));
            }
            premadeCells.addAll(templateRoomPremadeLocalCells);

            FloorLayout floor = convertLevel(
                    levelOpt.get(), i, floorFloors[i], floorCeilings[i],
                    footprint, random, premadeCells, templateIdByRoom);
            layout.getFloors().add(floor);
        }

        // TransitionData (one per inter-floor link). Metadata only now -- the real
        // assembled pieces (when transitionAssembler produced one) are added to the
        // worldgen builder directly by DungeonStructure, the same way the assembled
        // entrance's pieces bypass DungeonPieceEmitter. Rotation is baked into
        // those pieces already (vanilla JigsawPlacement picks it), so it's not
        // tracked here anymore.
        for (int i = 0; i < floorCount - 1; i++) {
            // Transition's XZ in lower floor's coords = same XZ as in upper floor
            // (the planner inherits the same footprint rect across the two grids).
            TransitionData transition = new TransitionData(
                    transitionTemplateIds.get(i),
                    i,
                    i + 1,
                    transitionLocalFootprints.get(i),
                    floorFloors[i],
                    floorFloors[i + 1],
                    0);
            layout.getTransitions().add(transition);
        }

        computeBoundingBox(layout);
        return Optional.of(layout);
    }

    // -------- helpers --------

    private DungeonSize rollSize(Random random) {
        DungeonSize[] values = DungeonSize.values();
        return values[random.nextInt(values.length)];
    }

    private int rollInRange(Random random, int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    private int oddInRange(Random random, int min, int max) {
        int v = rollInRange(random, min, max);
        if ((v & 1) == 0) v++;
        if (v > max) v -= 2;
        if (v < min) v = min | 1;
        return v;
    }

    private int rollRotation(Random random) {
        return random.nextInt(4) * 90;
    }

    /** Floor-pos seed = base seed mixed with floor index, so each floor differs deterministically. */
    private long mixSeed(long base, int floorIndex) {
        // Splitmix-style mix; keeps adjacent floors uncorrelated but reproducible.
        long x = base + (0x9E3779B97F4A7C15L * (floorIndex + 1));
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }

    private int pickNumberOfRooms(DungeonSize size, Rectangle2D footprint) {
        // Loose heuristic: ~10% of cells become rooms, with size-tier bonus.
        int cells = footprint.getWidth() * footprint.getHeight();
        int base = Math.max(5, cells / 100);
        int roomCount = switch (size) {
            case SMALL -> base;
            case MEDIUM -> base + 5;
            case LARGE -> base + 10;
        };
        // The ~10% density above assumes 1-wide corridors. Each extra dilation
        // pass (corridorCells > 1) eats proportionally more of the footprint per
        // corridor, so packing in the same room count leaves too little room for
        // corridors to breathe -- scale room count down as corridor width grows.
        // 2-wide: 15% fewer rooms; 3-wide: 25% fewer; floor at 5 either way.
        double widthDiscount = switch (Math.max(1, corridorCells)) {
            case 1 -> 1.0;
            case 2 -> 0.85;
            default -> 0.75;
        };
        return Math.max(5, (int) Math.round(roomCount * widthDiscount));
    }

    /** Center an inner rectangle within {@code footprint}. Returns null if the inner won't fit. */
    private Rectangle2D centerWithin(Rectangle2D footprint, int innerW, int innerD) {
        if (innerW > footprint.getWidth() || innerD > footprint.getHeight()) return null;
        int x = (footprint.getWidth() - innerW) / 2;
        int z = (footprint.getHeight() - innerD) / 2;
        // Even-align (the maze planner expects rooms on even-grid).
        if ((x & 1) != 0) x--;
        if ((z & 1) != 0) z--;
        if (x < 0 || z < 0) return null;
        return new Rectangle2D(x, z, innerW, innerD);
    }

    /**
     * Pick an XZ rect within {@code footprint} that doesn't overlap {@code reserved}.
     *
     * <p>Two phases: random attempts (cheap, varied), then deterministic exhaustive
     * scan of every even-aligned slot. The exhaustive fallback guarantees we find
     * a slot if one exists, which matters for small footprints where the
     * entrance dominates the center and random tries often miss the few valid
     * edge positions.</p>
     */
    private Rectangle2D placeAvoidingStart(Rectangle2D footprint, int w, int d,
                                            Rectangle2D reserved, Random random) {
        return placeAvoidingReserved(footprint, w, d,
                reserved == null ? List.of() : List.of(reserved), random);
    }

    /**
     * Generalized form of {@link #placeAvoidingStart} that avoids a whole list of
     * reserved rects at once &mdash; needed when placing more than one jigsaw-
     * assembled room per floor, where each successfully-placed room becomes a new
     * reservation for the next attempt (see the Phase 8 room-template loop in
     * {@link #plan()}).
     */
    private Rectangle2D placeAvoidingReserved(Rectangle2D footprint, int w, int d,
                                               List<Rectangle2D> reserved, Random random) {
        int xRange = footprint.getWidth() - w;
        int zRange = footprint.getHeight() - d;
        if (xRange < 0 || zRange < 0) return null;
        // Phase 1: random attempts.
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            int x = (xRange == 0) ? 0 : random.nextInt(xRange + 1);
            int z = (zRange == 0) ? 0 : random.nextInt(zRange + 1);
            if ((x & 1) != 0) x--;
            if ((z & 1) != 0) z--;
            if (x < 0) x = 0;
            if (z < 0) z = 0;
            Rectangle2D candidate = new Rectangle2D(x, z, w, d);
            if (noIntersections(candidate, reserved)) {
                return candidate;
            }
        }
        // Phase 2: exhaustive even-aligned scan (deterministic, finds a slot if any exists).
        for (int x = 0; x <= xRange; x += 2) {
            for (int z = 0; z <= zRange; z += 2) {
                Rectangle2D candidate = new Rectangle2D(x, z, w, d);
                if (noIntersections(candidate, reserved)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean noIntersections(Rectangle2D candidate, List<Rectangle2D> reserved) {
        for (Rectangle2D r : reserved) {
            if (r != null && candidate.intersects(r)) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if {@code rect} lies entirely within {@code [0,0]..bounds.getWidth()/
     * getHeight()}. A jigsaw-assembled piece's REAL footprint isn't guaranteed to
     * land with its min-corner at the requested world XZ &mdash; vanilla is free
     * to rotate the first piece of an assembly, which shifts the bounding box's
     * min-corner relative to the pivot. Without this check, a rotated piece can
     * produce a footprint with a negative local origin (or one that overruns the
     * floor's grid on the far side), which the maze's own START/END-room
     * validation then rejects outright, aborting the entire floor instead of
     * gracefully falling back to the synthetic placeholder. Must be checked
     * alongside (not instead of) the existing reserved-rect overlap check.
     */
    private static boolean withinLocalBounds(Rectangle2D rect, Rectangle2D bounds) {
        return rect.getMinX() >= 0 && rect.getMinY() >= 0
                && rect.getMinX() + rect.getWidth() <= bounds.getWidth()
                && rect.getMinY() + rect.getHeight() <= bounds.getHeight();
    }

    /**
     * Walks the planned level and emits a populated {@link FloorLayout}.
     *
     * @param premadeCells door cells (floor-local grid coords) that must NOT get a
     *                     {@code DoorData}/{@code DungeonDoorPiece} even if the
     *                     maze opened a door there -- {@code dungeons2:connector}
     *                     markers, whose template already has a real door built in.
     * @param templateIdByRoom identity-keyed map from a jigsaw-assembled room's
     *                         {@link IRoom2D} (as supplied to the maze) to its
     *                         template id; identity-keyed because the maze may
     *                         lazily assign the room's id (see suppliedRooms
     *                         handling), so it can't be looked up by id here.
     */
    private FloorLayout convertLevel(ILevel2D level, int floorIndex, int floorY, int ceilingY,
                                      Rectangle2D footprint, Random random, Set<Coords2D> premadeCells,
                                      Map<IRoom2D, String> templateIdByRoom) {
        FloorLayout floor = new FloorLayout(floorIndex, floorY, ceilingY, footprint);
        // Stash the maze grid (transient) so the renderer's corridor builder can
        // resolve neighbor wall cells. Not serialized; see FloorLayout#grid.
        floor.setGrid(level.getGrid());

        // Rooms.
        for (IRoom2D room2D : level.getRooms()) {
            RoomRole role = room2D.isStart() ? RoomRole.START
                    : (room2D.isEnd() ? RoomRole.END : RoomRole.NORMAL);
            int width = room2D.getWidth();
            int depth = room2D.getHeight(); // 2D "height" = 3D depth
            int height = pickRoomHeight(random, width, depth);
            RoomData rd = new RoomData(
                    room2D.getId(),
                    room2D.getOrigin().getX(),
                    room2D.getOrigin().getY(),
                    width,
                    depth,
                    height,
                    role);
            String templateId = templateIdByRoom.get(room2D);
            if (templateId != null) {
                rd.setTemplateId(templateId);
            }
            // Copy doorways defensively.
            for (Coords2D door : room2D.getDoorways()) {
                rd.getDoorways().add(new Coords2D(door));
            }
            floor.getRooms().add(rd);
        }

        // Corridors: group cells by region id.
        Grid2D grid = level.getGrid();
        Map<Integer, CorridorData> corridorMap = new LinkedHashMap<>();
        // Doors: collect cells with type DOOR.
        List<int[]> doorCells = new ArrayList<>();
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int z = 0; z < grid.getHeight(); z++) {
                Cell cell = grid.get(x, z);
                if (cell == null) continue;
                CellType type = cell.getType();
                if (type == CellType.CORRIDOR) {
                    int rid = cell.getRegionId();
                    CorridorData cd = corridorMap.computeIfAbsent(rid, CorridorData::new);
                    cd.getCells().add(new Coords2D(x, z));
                } else if (type == CellType.DOOR) {
                    doorCells.add(new int[]{x, z});
                }
            }
        }
        // Fold each corridor's bordering wall cells into CorridorData so the
        // Phase 3 corridor piece can render walls without the transient grid
        // (which is null after NBT deserialization). This mirrors exactly the
        // 8-neighbor wall test BasicCorridorGenerator applies against the grid.
        for (CorridorData cd : corridorMap.values()) {
            Set<Coords2D> walls = new LinkedHashSet<>();
            Set<Coords2D> doors = new LinkedHashSet<>();
            for (Coords2D cell : cd.getCells()) {
                int x = cell.getX();
                int z = cell.getY();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        int nx = x + dx;
                        int nz = z + dz;
                        // Doors first: they are wall cells too, but render pierced
                        // at the door-half levels. Keeping the two sets disjoint
                        // is what stops a solid column overwriting the pierced one.
                        if (isCorridorDoorCell(grid, nx, nz)) {
                            doors.add(new Coords2D(nx, nz));
                        } else if (isCorridorWallCell(grid, nx, nz)) {
                            walls.add(new Coords2D(nx, nz));
                        }
                    }
                }
            }
            cd.getWallCells().addAll(walls);
            cd.getDoorCells().addAll(doors);
        }
        floor.getCorridors().addAll(corridorMap.values());

        // Doors: figure out which two regions each connects by looking at neighbors.
        for (int[] dc : doorCells) {
            int x = dc[0], z = dc[1];
            if (premadeCells.contains(new Coords2D(x, z))) {
                // dungeons2:connector cell -- the maze treated it as a real door for
                // connectivity/region-merging purposes, but the assembled template
                // already has a real door built in here; no DoorData means no
                // DungeonDoorPiece overwrites it.
                continue;
            }
            int northId = z > 0 ? grid.get(x, z - 1).getRegionId() : -1;
            int southId = z < grid.getHeight() - 1 ? grid.get(x, z + 1).getRegionId() : -1;
            int eastId = x < grid.getWidth() - 1 ? grid.get(x + 1, z).getRegionId() : -1;
            int westId = x > 0 ? grid.get(x - 1, z).getRegionId() : -1;
            int regionA;
            int regionB;
            Direction2D facing;
            if (northId != southId && northId > 0 && southId > 0) {
                regionA = northId;
                regionB = southId;
                facing = Direction2D.NORTH;
            } else {
                regionA = eastId;
                regionB = westId;
                facing = Direction2D.EAST;
            }
            floor.getDoors().add(new DoorData(x, z, regionA, regionB, facing));
        }

        return floor;
    }

    /**
     * True if the cell at (x,z) is a wall-equivalent for corridor-wall placement.
     * Kept byte-for-byte in sync with {@code BasicCorridorGenerator.isWallElement}:
     * out-of-bounds counts as a wall, as do ROCK / WALL / DOOR / CONNECTOR cells.
     */
    private static boolean isCorridorWallCell(Grid2D grid, int x, int z) {
        if (x < 0 || z < 0 || x >= grid.getWidth() || z >= grid.getHeight()) {
            return true;
        }
        CellType type = grid.get(x, z).getType();
        return type == CellType.ROCK || type == CellType.WALL
                || type == CellType.DOOR || type == CellType.CONNECTOR;
    }

    /**
     * True if the cell at (x,z) is an opened doorway bordering a corridor.
     * Kept in sync with {@code BasicCorridorGenerator.isDoorElement}: out-of-bounds
     * is not a door, and CONNECTOR is deliberately excluded.
     */
    private static boolean isCorridorDoorCell(Grid2D grid, int x, int z) {
        if (x < 0 || z < 0 || x >= grid.getWidth() || z >= grid.getHeight()) {
            return false;
        }
        return grid.get(x, z).getType() == CellType.DOOR;
    }

    private int pickRoomHeight(Random random, int width, int depth) {
        // Same heuristic the original DungeonGenerator.convertRooms used:
        // height = min(randomInt(5, 10), max(width, depth))
        int rolled = 5 + random.nextInt(6); // 5..10 inclusive
        return Math.min(rolled, Math.max(width, depth));
    }

    private void computeBoundingBox(DungeonLayout layout) {
        // World-space anchor XZ is the dungeon's reference point.
        int ax = layout.getAnchor().getX();
        int az = layout.getAnchor().getZ();

        int minX = ax, maxX = ax;
        int minZ = az, maxZ = az;
        int minY = layout.getEntrance().getSurfaceY();
        int maxY = layout.getEntrance().getSurfaceY();

        for (FloorLayout floor : layout.getFloors()) {
            Rectangle2D fp = floor.getFootprint();
            minX = Math.min(minX, ax + fp.getMinX());
            maxX = Math.max(maxX, ax + fp.getMaxX());
            minZ = Math.min(minZ, az + fp.getMinY());
            maxZ = Math.max(maxZ, az + fp.getMaxY());
            minY = Math.min(minY, floor.getFloorY());
            maxY = Math.max(maxY, floor.getCeilingY());
        }

        layout.setBboxMin(new Coords(minX, minY, minZ));
        layout.setBboxMax(new Coords(maxX, maxY, maxZ));
    }
}
