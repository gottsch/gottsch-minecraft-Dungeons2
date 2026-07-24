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
     * Corridor width in cells. 2 = 2-wide via dilation (re-enabled Jul 23 after the
     * Z-mirror render bug fix and re-verified dilation-safety regression tests, see
     * RoomCorridorAdjacencyTest#dilatedCorridorsAreStillNeverAdjacentToRooms and
     * CorridorRoomSealTest#dilatedCorridorsStillDoNotReachRoomInteriors). 1 = classic
     * 1-wide, still available via withCorridorWidth(1).
     */
    private int corridorCells = 2;

    // -------- Phase 4b: assembled-entrance overrides (all-or-nothing) --------
    // When set (see withAssembledEntrance), floor 0 is driven by the jigsaw-
    // assembled entrance instead of the catalog pick + surfaceY-derived drop.
    // Inputs are in WORLD coordinates; the planner sizes floor 0's grid (>= the
    // size-tier's rolled footprint so room density matches the other floors),
    // centers the entrance in it, maps the door cells into grid space, and hands
    // the resulting world anchor back via DungeonLayout#getAnchor.
    private Rectangle2D assembledEntranceWorldRect;   // world (minX, minZ, w, d)
    private List<Coords2D> assembledDoorWorldCells;   // world (x, z) of dungeons2:door markers
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
        this.assembledEntranceWorldRect = entranceWorldRect;
        this.assembledDoorWorldCells = doorWorldCells;
        this.assembledFloor0Y = floor0WalkingPlaneY;
        return this;
    }

    private boolean hasAssembledEntrance() {
        return assembledEntranceWorldRect != null;
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

            assembledCandidateCells = new ArrayList<>(assembledDoorWorldCells.size());
            for (Coords2D d : assembledDoorWorldCells) {
                assembledCandidateCells.add(new Coords2D(d.getX() - worldAnchorX, d.getY() - worldAnchorZ));
            }

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

        // Pick transition templates up front for floors 0..N-2 (each link).
        // A transition's XZ rect must fit in BOTH its upper and lower floor grids
        // (since the same rect is reused as floor i's END and floor i+1's START).
        // Constrain placement to the intersection footprint.
        List<TemplateEntry> transitionTemplates = new ArrayList<>(floorCount - 1);
        List<Rectangle2D> transitionLocalFootprints = new ArrayList<>(floorCount - 1);
        for (int i = 0; i < floorCount - 1; i++) {
            TemplateEntry tt = catalog.pick(
                    TemplateCatalog.Category.TRANSITION, motifValue, size, random);
            int tw = tt != null ? tt.getWidth() : 7;
            int td = tt != null ? tt.getDepth() : 7;
            transitionTemplates.add(tt);
            int boundW = Math.min(footprints.get(i).getWidth(), footprints.get(i + 1).getWidth());
            int boundH = Math.min(footprints.get(i).getHeight(), footprints.get(i + 1).getHeight());
            Rectangle2D placementBound = new Rectangle2D(0, 0, boundW, boundH);
            // Per-floor grids share origin (0,0), so a rect from floor i-1 is
            // directly usable as a reservation for floor i's overlap check.
            Rectangle2D startReserved = (i == 0) ? entranceLocalFootprint
                    : transitionLocalFootprints.get(i - 1);
            Rectangle2D end = placeAvoidingStart(placementBound, tw, td, startReserved, random);
            if (end == null) {
                return Optional.empty();
            }
            transitionLocalFootprints.add(end);
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
            // entrance's dungeons2:door marker cells; the maze opens up to
            // getDegrees() of them and walls off the rest.
            if (i == 0 && assembledCandidateCells != null) {
                startRoom.setCandidateDoorways(assembledCandidateCells);
            }
            IRoom2D endRoom = new Room2D(endFootprint);
            endRoom.setEnd(true);
            // The terminal/boss room keeps a single entrance by design (one path in).
            // Reachability is guaranteed by MazeLevelGenerator2D's connectivity pass,
            // not by adding extra doors.
            endRoom.setDegrees(1);

            final int floorIndex = i;
            final IRoom2D startRef = startRoom;
            final IRoom2D endRef = endRoom;
            MazeLevelGenerator2D mazeGen = new MazeLevelGenerator2D.Builder()
                    .with($ -> {
                        $.width = footprint.getWidth();
                        $.height = footprint.getHeight();
                        $.numberOfRooms = pickNumberOfRooms(size, footprint);
                        $.startRoom = startRef;
                        $.endRoom = endRef;
                    })
                    .corridorWidth(corridorCells)
                    .seed(mixSeed(seed, floorIndex))
                    .build();

            Optional<ILevel2D> levelOpt = mazeGen.generate();
            if (levelOpt.isEmpty()) {
                return Optional.empty();
            }

            FloorLayout floor = convertLevel(
                    levelOpt.get(), i, floorFloors[i], floorCeilings[i],
                    footprint, random);
            layout.getFloors().add(floor);
        }

        // TransitionData (one per inter-floor link).
        for (int i = 0; i < floorCount - 1; i++) {
            TemplateEntry tt = transitionTemplates.get(i);
            // Transition's XZ in lower floor's coords = same XZ as in upper floor
            // (the planner inherits the same footprint rect across the two grids).
            TransitionData transition = new TransitionData(
                    tt != null ? tt.getId() : "dungeons2:transitions/synthetic",
                    i,
                    i + 1,
                    transitionLocalFootprints.get(i),
                    floorFloors[i],
                    floorFloors[i + 1],
                    rollRotation(random));
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
            if (reserved == null || !candidate.intersects(reserved)) {
                return candidate;
            }
        }
        // Phase 2: exhaustive even-aligned scan (deterministic, finds a slot if any exists).
        for (int x = 0; x <= xRange; x += 2) {
            for (int z = 0; z <= zRange; z += 2) {
                Rectangle2D candidate = new Rectangle2D(x, z, w, d);
                if (reserved == null || !candidate.intersects(reserved)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Walks the planned level and emits a populated {@link FloorLayout}. */
    private FloorLayout convertLevel(ILevel2D level, int floorIndex, int floorY, int ceilingY,
                                      Rectangle2D footprint, Random random) {
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
            for (Coords2D cell : cd.getCells()) {
                int x = cell.getX();
                int z = cell.getY();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        int nx = x + dx;
                        int nz = z + dz;
                        if (isCorridorWallCell(grid, nx, nz)) {
                            walls.add(new Coords2D(nx, nz));
                        }
                    }
                }
            }
            cd.getWallCells().addAll(walls);
        }
        floor.getCorridors().addAll(corridorMap.values());

        // Doors: figure out which two regions each connects by looking at neighbors.
        for (int[] dc : doorCells) {
            int x = dc[0], z = dc[1];
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
