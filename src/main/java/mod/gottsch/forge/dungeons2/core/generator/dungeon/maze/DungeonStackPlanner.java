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

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.CorridorStyleWeight;
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

import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomHeightBand;
import mod.gottsch.forge.dungeons2.core.config.TemplateLimit;

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
import java.util.concurrent.ConcurrentHashMap;

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
    // Delegated, not copied. These and the datapack's defaults are the same number, and a second
    // literal here is exactly the drift that made the old MIN_TRANSITION_HEIGHT wrong (#52).
    private static final int DEFAULT_FLOOR_HEIGHT = DungeonGenerationConfig.DEFAULT_FLOOR_HEIGHT;
    private static final int DEFAULT_GAP_BETWEEN_FLOORS =
            DungeonGenerationConfig.DEFAULT_GAP_BETWEEN_FLOORS;
    private static final int DEFAULT_ENTRANCE_DROP = 12;
    /** Max attempts to place a START/END room within a footprint without overlap. */
    private static final int PLACEMENT_ATTEMPTS = 20;
    /** The synthetic terminal room this mod has always built. See {@link #terminalRoomWidth}. */
    private static final int DEFAULT_TERMINAL_ROOM_SIZE = 7;

    /**
     * Overworld's floor, used when no caller supplies one. Worldgen always does &mdash; see
     * {@code DungeonStructure#findGenerationPoint} &mdash; so this only serves tests and the
     * floor-plan harness, which have no {@code LevelHeightAccessor} to ask.
     */
    private static final int DEFAULT_MIN_BUILD_Y = -64;

    /**
     * Blocks left clear above {@link #minBuildY}. The overworld's bedrock band occupies the lowest
     * five layers in a randomised pattern, so a floor slab any lower than this is cutting into
     * bedrock rather than standing on it.
     */
    private static final int BEDROCK_MARGIN = 5;

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
    private int minBuildY = DEFAULT_MIN_BUILD_Y;
    /**
     * #51's room-height taper. Defaults to the shipped table rather than to "uncapped", because an
     * uncapped roll is the tall-box outcome the taper exists to prevent; a caller that never
     * injects one (tests, the floor-plan harness) should still get the dungeon players see.
     */
    private List<RoomHeightBand> roomHeightBands = DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS;
    /**
     * Footprint reserved for the bottom floor's terminal (boss) room &mdash; backlog #46. 7x7 is the
     * synthetic placeholder the mod has always built there; an authored boss template will hand over
     * its own measured size instead.
     */
    private int terminalRoomWidth = DEFAULT_TERMINAL_ROOM_SIZE;
    private int terminalRoomDepth = DEFAULT_TERMINAL_ROOM_SIZE;
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
    /**
     * Corridor wall height in blocks, stamped onto every {@link CorridorData} this planner
     * produces. Same "resolve where RegistryAccess is available, inject the value" shape as
     * {@link #corridorCells}: production worldgen and the debug command read it from the motif's
     * {@code CorridorConfig}, tests keep the historical 5. It has to be decided here rather than
     * at render time because {@code DungeonCorridorPiece} sizes its bounding box from it at
     * construction, long before it can reach a datapack registry.
     */
    private int corridorHeight = CorridorData.DEFAULT_WALL_HEIGHT;
    /**
     * The corridor styles to roll among, one roll per floor (see {@link #rollCorridorStyle}). Empty
     * is the historical behaviour: every floor gets {@link #corridorHeight} and the motif's baseline
     * geometry. Injected by the caller for the same reason {@link #corridorHeight} is &mdash; the
     * planner cannot reach a datapack registry, and it is the only place that both knows the floor
     * and runs before the pieces are constructed.
     */
    private List<CorridorStyleWeight> corridorStyles = List.of();
    private int minRoomGap = 0;
    /** See {@link #DEFAULT_ROOM_TEMPLATE_ATTEMPTS_PER_FLOOR}; injected by {@link #withRoomTemplateAttempts}. */
    private int roomTemplateAttempts = DEFAULT_ROOM_TEMPLATE_ATTEMPTS_PER_FLOOR;

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
     * The world's floor, from {@code LevelHeightAccessor#getMinBuildHeight}. Floors that would
     * stack below it are dropped &mdash; see the clamp in {@link #plan()}.
     *
     * <p>Worth passing rather than assuming: the overworld's -64 is not universal, and a dimension
     * with a shallower floor would otherwise have dungeons generating into nothing.</p>
     */
    public DungeonStackPlanner withMinBuildY(int minBuildY) {
        this.minBuildY = minBuildY;
        return this;
    }

    /**
     * Floor-to-floor drop: the walking plane of floor {@code i} to that of floor {@code i+1}.
     *
     * <p>{@code floorHeight} is the budget above a walking plane and {@code gapBetweenFloors} is the
     * stone buffer below it, so the two always travel together &mdash; and this is the number a
     * transition template has to span. 12 with the shipped defaults.</p>
     */
    public int pitch() {
        return floorHeight + gapBetweenFloors;
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
    /**
     * The vertical budget one floor gets, floor block through ceiling block. No room may exceed it
     * without pushing its ceiling into the floor above, which is why {@code RoomHeightBand}'s table
     * is checked against this at the call site rather than at load (a datapack cannot see it).
     */
    public int floorHeight() {
        return floorHeight;
    }

    /**
     * Reserve a differently-sized terminal (boss) room on the bottom floor &mdash; #46.
     *
     * <p><strong>A size the bottom floor cannot fit fails the whole dungeon</strong>, not just the
     * boss room: {@code plan()} returns empty when the terminal slot cannot be placed, because every
     * floor is required to have an end. That is the number {@code TerminalRoomFitProbe} measures,
     * and it is why an authored boss template needs a fallback rather than a bigger footprint and
     * hope.</p>
     *
     * <p>Defaults to the synthetic 7x7, so a caller that never sets it plans byte-identically to
     * before this existed.</p>
     */
    /**
     * Supplies the boss-room assembler (#46). Absent &mdash; no {@code end_rooms} pool authored, or
     * no Forge shell to assemble in &mdash; and the bottom floor's terminal room is built
     * procedurally exactly as it is today. Degrade toward generating, the convention every
     * missing-content path here follows.
     */
    public DungeonStackPlanner withBossRoomAssembler(RoomAssembler assembler) {
        this.bossRoomAssembler = assembler;
        return this;
    }

    public DungeonStackPlanner withTerminalRoomSize(int width, int depth) {
        this.terminalRoomWidth = width;
        this.terminalRoomDepth = depth;
        warnIfEvenSided("terminal (boss) room", width, depth);
        return this;
    }

    public DungeonStackPlanner withCorridorWidth(int cells) {
        this.corridorCells = Math.max(1, cells);
        return this;
    }

    /**
     * Override how many jigsaw-assembled rooms this planner tries to place per floor. Zero is a
     * legitimate value &mdash; it turns prefab rooms off without emptying the pool &mdash; so this
     * floors at 0 rather than 1. The codec is where an out-of-range value becomes a load error;
     * there is deliberately no upper clamp here that could turn a bad datapack into a slow dungeon
     * with nothing saying why.
     */
    public DungeonStackPlanner withRoomTemplateAttempts(int attempts) {
        this.roomTemplateAttempts = Math.max(0, attempts);
        return this;
    }

    /**
     * Override the room-height taper (#51). Bands are matched in order against a room's long side;
     * see {@link RoomHeightBand}. The caller hands over an already-validated table &mdash;
     * {@code RoomHeightBand.LIST_CODEC} is where a mis-ordered or non-total one becomes a load
     * error &mdash; so there is deliberately no repair here that could turn a bad datapack into a
     * quietly different dungeon.
     *
     * <p>A null or empty list restores the shipped table rather than removing the cap, for the
     * reason on {@link #roomHeightBands}.</p>
     */
    public DungeonStackPlanner withRoomHeightBands(List<RoomHeightBand> bands) {
        this.roomHeightBands = (bands == null || bands.isEmpty())
                ? DungeonGenerationConfig.DEFAULT_ROOM_HEIGHT_BANDS
                : List.copyOf(bands);
        return this;
    }

    /**
     * Override corridor wall height in blocks (floor row + air + ceiling row). The caller is
     * expected to hand over an already-validated value &mdash; {@code CorridorConfig}'s codec is
     * where an out-of-range height becomes a load error, so there is deliberately no clamp here
     * that could quietly turn a bad datapack into a subtly wrong dungeon.
     */
    public DungeonStackPlanner withCorridorHeight(int blocks) {
        this.corridorHeight = blocks;
        return this;
    }

    /**
     * Roll a corridor style per floor from this weighted list instead of applying one height to the
     * whole dungeon. A single-entry list is equivalent to {@link #withCorridorHeight(int)} with that
     * entry's height, and an empty (or null) list restores it.
     *
     * <p>Same contract as {@code withCorridorHeight}: the values are expected to be pre-validated,
     * because {@code CorridorConfig}'s codec is where a bad one becomes a load error. Weights are
     * assumed positive &mdash; the codec enforces that too.</p>
     */
    /**
     * How many times an authored template may be placed, keyed by its full id &mdash; backlog #44,
     * resolved from the motif's {@code templateLimits}. Same "resolve where {@code RegistryAccess}
     * is available, inject the resolved value" shape as {@link #withCorridorWidth}.
     *
     * <p>Absent (the default) means every template is unlimited, and in that state this feature
     * consumes <strong>no</strong> randomness and takes no branch &mdash; a dungeon planned without
     * limits is byte-identical to one planned before they existed. That matters because the check
     * sits in front of {@code placeAvoidingReserved}, which draws from {@code random}: a limit that
     * rejects an attempt necessarily shifts the stream and re-rolls existing seeds. Declaring one is
     * therefore a world-changing edit, exactly as adding a {@code minSize} to a shipped scheme is.
     * </p>
     */
    public DungeonStackPlanner withTemplateLimits(Map<String, TemplateLimit> limits) {
        this.templateLimits = limits == null ? Map.of() : Map.copyOf(limits);
        return this;
    }

    public DungeonStackPlanner withCorridorStyles(List<CorridorStyleWeight> styles) {
        this.corridorStyles = styles == null ? List.of() : List.copyOf(styles);
        return this;
    }

    /**
     * Minimum clear cells required between rooms. {@code 0} (the default) is the historical
     * behaviour, where rooms may overlap by one cell and share that column as a wall.
     *
     * <p>This is the only lever that lets a dilated corridor reach full width <em>between</em> two
     * rooms: the maze runs on a 2-cell lattice, so room boxes always sit an even distance apart and
     * the free cells between them are {@code gap - 1}. A 3-wide corridor therefore needs a gap of 4.
     * It is not free -- space per room grows roughly with the square of the gap. See
     * {@code SpacingSweep} in the test sources for the measured cost.</p>
     */
    public DungeonStackPlanner withMinRoomGap(int cells) {
        this.minRoomGap = Math.max(0, cells);
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
    // See the PARITY NOTE on roomAssembler below before changing anything here: the
    // two paths share a pipeline, and every fault found in one has so far existed in
    // the other too.
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
        /**
         * Assembles a transition anchored at the given world position.
         *
         * <p><strong>Contract the planner relies on.</strong> The planner calls this
         * twice per attempt: once to <em>measure</em> ({@code commit == false}) and
         * once to <em>place</em> ({@code commit == true}). For that to work the
         * implementation must guarantee two things:</p>
         * <ol>
         *   <li><strong>Reproducible from the seed.</strong> The same
         *       {@code assemblySeed} must produce the same assembly &mdash; same
         *       templates, same rotation, same relative arrangement of the pieces.
         *       The Minecraft-facing implementation gets this by seeding the
         *       {@code WorldgenRandom} that {@code JigsawPlacement} draws from.</li>
         *   <li><strong>Translation-invariant.</strong> Changing only the requested
         *       position must translate the whole result and change nothing else.
         *       True for {@code rigid} projection (which every transition pool entry
         *       uses); a {@code terrain_matching} entry would break it, because the
         *       heightmap it snaps to varies with position.</li>
         * </ol>
         *
         * <p>Together these let the planner discover a chain's <em>real</em>
         * footprint before committing to a slot for it &mdash; see the
         * probe/measure/reserve/place loop in {@link #plan()}.</p>
         *
         * @param assemblySeed seeds the assembly; the same value must reproduce the
         *                     same shape
         * @param commit       {@code false} for the measuring probe: the result is
         *                     read for its geometry and thrown away, so the
         *                     implementation must NOT keep (stage, place, or
         *                     otherwise remember) the pieces it built.
         *                     {@code true} for the real placement.
         */
        Optional<AssembledTransition> assemble(int worldX, int worldY, int worldZ,
                                               long assemblySeed, boolean commit);
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
    //
    // PARITY NOTE. This and the transition path above are two instances of ONE
    // pipeline: measure a prefab, reserve a slot, place it, hand its authored door
    // markers to the maze, connect it up. Every fault found in either during 2026-07
    // turned out to exist in the other as well -- a guessed slot size, a rejected
    // prefab still being built, a prefab flush against the grid boundary, and an
    // authored door nothing ever routed a corridor to. Each was found on one side by
    // accident of which one was being looked at.
    //
    // So: when changing one, check the other. Neither fault class announces itself --
    // a dropped prefab is quietly replaced by procedural fill, and a sealed door
    // still leaves a reachable dungeon.
    //
    // They do genuinely differ in three ways, so don't copy blindly: a transition has
    // TWO ends at different Y (and its footprint serves as the upper floor's END and
    // the lower floor's START, so connectivity must be checked at both), a transition
    // can be a multi-piece chain whose union sprawls while a room is always one piece
    // displaced only by rotation, and rooms additionally reject a footprint touching
    // the floor edge. The entrance is a THIRD variant and is not part of this parity:
    // it has no assembler and drives the layout anchor rather than fitting a slot.
    private Map<String, TemplateLimit> templateLimits = Map.of();

    /**
     * Assembles the authored boss room for the bottom floor's terminal slot &mdash; backlog #46.
     * Same {@link RoomAssembler} contract as the interior-room path, drawing from a different pool
     * ({@code end_rooms/<motif>/}); a separate interface of identical shape would have been two
     * names for one protocol.
     *
     * <p><strong>Null consumes no randomness and takes no branch</strong>, so a dungeon planned
     * without a boss assembler is byte-identical to one planned before this existed. That is
     * load-bearing: the probe below draws from {@code random}, so a boss room that is merely
     * <em>attempted</em> already shifts the stream. Same contract as {@link #templateLimits}.</p>
     */
    private RoomAssembler bossRoomAssembler;

    /**
     * How many pool draws the boss slot gets before falling back to the procedural terminal room.
     *
     * <p>The draw is random, not size-ordered, so "the largest template that fits" is not something
     * one probe can ask for &mdash; each attempt is a fresh draw that may or may not fit the floor.
     * Measured (see {@code TerminalRoomFitProbe}), MEDIUM and LARGE bottom floors hold anything up
     * to 19x19, so retries only matter on SMALL, where a large template genuinely will not fit and
     * more attempts buy a smaller draw rather than luck.</p>
     */
    private static final int BOSS_ASSEMBLY_ATTEMPTS = 4;

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
        /**
         * Assembles an interior-room prefab anchored at the given world position.
         * Same measure-then-place protocol, and the same two guarantees required of
         * the implementation, as {@link TransitionAssembler#assemble} &mdash; see
         * there for the reasoning.
         *
         * <p>A room can't sprawl the way a transition chain can (it's one piece),
         * but vanilla still <em>rotates</em> it, and rotation moves the bounding
         * box's min corner off the requested position: all four shipped prefabs are
         * 7x7, so three of the four rotations displace the footprint 6 blocks west
         * and/or north. Measured 2026-07-30 across 200 seeds, choosing the slot
         * before knowing where the footprint would land cost <strong>44% of all
         * prefab room slots</strong>.</p>
         */
        Optional<AssembledRoom> assemble(int worldX, int worldY, int worldZ,
                                         long assemblySeed, boolean commit);
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
                               List<Coords2D> premadeWorldCells,
                               List<String> elementIds) {

        /** The shape before the assembler could say which template it drew. */
        public AssembledRoom(Rectangle2D worldFootprint, List<Coords2D> doorWorldCells,
                             List<Coords2D> premadeWorldCells) {
            this(worldFootprint, doorWorldCells, premadeWorldCells, List.of());
        }

        /**
         * The template this room actually is, or empty when the assembler could not say &mdash; see
         * {@code PoolElementIds.locationOf} for when that happens and why it is not an error.
         *
         * <p>The <strong>first</strong> element, because jigsaw assembly starts from the piece
         * placed at the requested position; a room is normally one piece anyway. This is the
         * identity backlog #44 counts, and it is deliberately a plain {@code String} so this record
         * stays free of Minecraft types like the rest of the planner's data.</p>
         */
        public java.util.Optional<String> rootElementId() {
            return elementIds.isEmpty()
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(elementIds.get(0));
        }
    }

    /**
     * Attempts per floor to place a jigsaw-assembled room before falling back to procedural fill
     * rooms. Same "resolve where {@code RegistryAccess} is available, inject the value" shape as
     * {@link #corridorCells}: this default only applies to callers that never call
     * {@link #withRoomTemplateAttempts(int)} (i.e. tests), while production worldgen and the debug
     * command both read it from the {@code dungeons2:generation_config} registry.
     *
     * <p>It is kept at the historical <strong>2</strong> rather than tracking the shipped value,
     * because a great many tests were written against the layouts it produces and silently
     * re-rolling all of them is not a change this constant should be able to make on its own.</p>
     */
    private static final int DEFAULT_ROOM_TEMPLATE_ATTEMPTS_PER_FLOOR = 2;

    /**
     * Assembly attempts per inter-floor transition. Each attempt draws a fresh
     * {@code assemblySeed}, so it re-rolls which template(s) the start pool hands
     * back as well as the rotation.
     *
     * <p>With the probe/measure/reserve/place loop in {@link #plan()} the first
     * attempt almost always succeeds, because the slot is now sized and positioned
     * from the real assembled footprint rather than validated against a guess.
     * Retries cover the one case that remains genuinely unsatisfiable: an assembly
     * whose real footprint <em>cannot fit</em> in this link's placement bound
     * alongside the reserved start slot at all. Re-rolling then naturally settles a
     * cramped link on a smaller, self-contained single-piece transition — so no
     * per-pool-entry size bookkeeping is needed.
     */
    private static final int TRANSITION_ASSEMBLY_ATTEMPTS = 12;
    // ROOM_TEMPLATE_MIN_SIZE / MAX_SIZE used to roll a guessed room size to place
    // before assembling. Gone: the slot is now sized from the assembled prefab
    // itself, so the planner never guesses how big a prefab is.

    /**
     * How far a jigsaw-assembled slot (interior room or transition) is kept clear
     * of its floor's own outer boundary. Must be EVEN (see
     * {@link #placeAvoidingReserved}); 2 is the smallest value that keeps the
     * reserved rect's own edge — and therefore any authored door candidate on it —
     * off the grid's boundary row/column.
     *
     * <p>A candidate flush against the boundary has <strong>no cell on its far
     * side</strong>, so it can never bridge two regions and never becomes a door:
     * the template's authored doorway is simply sealed. Measured 2026-07-30, before
     * transitions got this margin, 15% of adopted transitions sat flush against the
     * boundary and a third of those ended up with no doorway at all.</p>
     */
    private static final int PREFAB_EDGE_MARGIN = 2;

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

            // Offset the entrance within the grid on an even origin (the maze rejects
            // odd-origin rooms), then derive the world anchor so grid-local (0,0)
            // maps to world such that the entrance lands where it was assembled.
            int startMinX = entranceStart(gridW, ew, 0);
            int startMinZ = entranceStart(gridH, ed, 1);
            entranceLocalFootprint = new Rectangle2D(startMinX, startMinZ, ew, ed);
            warnIfEvenSided("assembled entrance", ew, ed);
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
        // Backlog #50 -- drop the floors that would not fit above the world's floor.
        //
        // WHY IT IS HERE AND NOT AT THE ROLL. floorCount was rolled ~80 lines up, before floor 0's Y
        // was known: on the assembled-entrance path it comes from the entrance's own bottom door
        // marker, which is not resolved until the branch above. It is tempting to move the roll down
        // to join this, and it must not move -- rolling in place and then taking a min consumes
        // exactly the same randomness, so every seed that already fits stays byte-identical and only
        // the ones that were generating into bedrock change. Moving the roll would relayout every
        // dungeon in every existing world.
        //
        // Everything downstream indexes off floorCount, so shrinking it here is enough: the extra
        // rolled footprints simply go unused, and the two Y arrays are over-allocated but only ever
        // read up to floorCount. The transition lists below are sized from it, so this has to happen
        // before them.
        int safeBottom = minBuildY + BEDROCK_MARGIN;

        // Floor 0 is not clampable. Its Y is fixed -- by the assembled entrance's bottom door on
        // the shipped path, by surfaceY on the synthetic one -- so if IT does not clear the bedrock
        // band there is no shorter dungeon to fall back to, only no dungeon. Declining is the
        // honest outcome and callers already handle it: an entrance footprint that will not fit
        // returns empty a few lines above, and the structure simply does not place.
        //
        // Needs a surface around Y=9 to trigger at the shipped pitch, so in practice this is a
        // guard rather than a behaviour, and the shipped biome tag makes it rarer still. It exists
        // because the alternative is a dungeon buried in bedrock, and because #29's proposed pitch
        // moves every threshold in this method upward.
        if (floorFloors[0] < safeBottom) {
            Dungeons.LOGGER.info(
                    "[D2-DEPTH] declining to place: floor 0 would sit at Y={}, below the world"
                            + " floor {} plus {} clear of bedrock (surfaceY {})",
                    floorFloors[0], minBuildY, BEDROCK_MARGIN, surfaceY);
            return Optional.empty();
        }

        int floorsThatFit = 1 + Math.max(0, (floorFloors[0] - safeBottom) / pitch());
        if (floorsThatFit < floorCount) {
            // INFO, not debug: the mod's own logging level ships at "info", so a debug line here
            // would be invisible to the one person who could act on it. This is rare by
            // construction -- it needs a low surface -- and one line when it happens is the
            // difference between "that dungeon is short" and knowing why.
            Dungeons.LOGGER.info(
                    "[D2-DEPTH] floor count {} -> {} at floor0Y={} (pitch {}, world floor {},"
                            + " keeping {} clear of bedrock): the full stack would reach {}",
                    floorCount, Math.max(1, floorsThatFit), floorFloors[0], pitch(), minBuildY,
                    BEDROCK_MARGIN, floorFloors[0] - (floorCount - 1) * pitch());
            floorCount = Math.max(1, floorsThatFit);
        }

        for (int i = 1; i < floorCount; i++) {
            floorCeilings[i] = floorFloors[i - 1] - gapBetweenFloors - 1;
            floorFloors[i] = floorCeilings[i] - floorHeight + 1;
        }

        // Resolve transitions up front for floors 0..N-2 (each link). A transition's
        // XZ rect must fit in BOTH its upper and lower floor grids (since the same
        // rect is reused as floor i's END and floor i+1's START).
        //
        // When a transitionAssembler is supplied, the real footprint is measured
        // BEFORE a slot is reserved for it (see the probe/measure/reserve/place loop
        // below), so the size below is only the synthetic placeholder used when no
        // assembler is set or every attempt fails to assemble — the same graceful
        // degradation hasAssembledEntrance() has.
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

            // MEASURE FIRST, THEN RESERVE. A transition's real footprint is only
            // knowable after it assembles: vanilla places the chain's FIRST piece at
            // the position we ask for and lets the rest sprawl outward, so a chain's
            // union rect is both bigger than any guess we could make AND offset from
            // the assembly point by a rotation-dependent amount (the real 3-piece
            // stairs_2 reaches 11 blocks negative). Reserving a slot up front and
            // validating the real footprint against it afterwards therefore fails
            // essentially always for a chain, and even when it happens to pass, the
            // resulting origin is not even-aligned, which MazeLevelGenerator2D
            // rejects outright (isRoomValid) and the whole floor dies with it.
            //
            // So: assemble once to MEASURE (nothing is kept -- commit == false),
            // reserve a slot that fits what was measured, then assemble again with
            // the SAME seed anchored so the footprint lands exactly on that slot.
            // The assembler's contract (same seed => same shape, position only
            // translates) is what makes the second assembly land where computed;
            // see TransitionAssembler. Retries -- see TRANSITION_ASSEMBLY_ATTEMPTS.
            for (int attempt = 0; transitionAssembler != null
                    && attempt < TRANSITION_ASSEMBLY_ATTEMPTS; attempt++) {
                long assemblySeed = random.nextLong();
                // Anchor at the LOWER floor's walking plane (floorFloors[i + 1]),
                // not the upper floor's -- ladder1/stairs_1 are authored with local
                // Y=0 at the lower floor's plane (confirmed working in-game before
                // this migration), so assembly must start there and chain UPWARD,
                // not start at the upper floor and chain down.
                final int assemblyY = floorFloors[i + 1];
                // The probe's XZ is arbitrary (grid-local 0,0 for tidiness) -- only
                // the DELTA between what we ask for and what comes back is read off
                // it, and that delta is position-independent by contract.
                int probeWorldX = planAnchor.getX();
                int probeWorldZ = planAnchor.getZ();
                Optional<AssembledTransition> probe = transitionAssembler.assemble(
                        probeWorldX, assemblyY, probeWorldZ, assemblySeed, false);
                if (probe.isEmpty()) {
                    continue;
                }
                Rectangle2D probeRect = probe.get().worldFootprint();
                int offsetX = probeRect.getMinX() - probeWorldX;
                int offsetZ = probeRect.getMinY() - probeWorldZ;

                // Reserve using the real, measured size. placeAvoidingStart also
                // even-aligns the origin, which the maze requires of any room.
                Rectangle2D slot = placeAvoidingReserved(placementBound,
                        probeRect.getWidth(), probeRect.getHeight(),
                        startReserved == null ? List.of() : List.of(startReserved),
                        random, PREFAB_EDGE_MARGIN);
                if (slot == null) {
                    // This assembly simply cannot fit this link alongside the start
                    // slot. Retrying re-rolls the pool pick, so a cramped link gets
                    // a chance to settle on a smaller single-piece transition.
                    continue;
                }

                Optional<AssembledTransition> assembled = transitionAssembler.assemble(
                        planAnchor.getX() + slot.getMinX() - offsetX, assemblyY,
                        planAnchor.getZ() + slot.getMinY() - offsetZ, assemblySeed, true);
                if (assembled.isEmpty()) {
                    continue;
                }
                AssembledTransition at = assembled.get();
                warnIfEvenSided("assembled transition", at.worldFootprint().getWidth(),
                        at.worldFootprint().getHeight());
                Rectangle2D wf = at.worldFootprint();
                Rectangle2D realFootprint = new Rectangle2D(
                        wf.getMinX() - planAnchor.getX(), wf.getMinY() - planAnchor.getZ(),
                        wf.getWidth(), wf.getHeight());
                if (!withinLocalBounds(realFootprint, placementBound)
                        || (startReserved != null && realFootprint.intersects(startReserved))) {
                    // Only reachable if the assembler did NOT honour its contract
                    // (the placement came back as a different shape than the probe,
                    // so it missed the slot). Kept as a hard guard rather than an
                    // assertion: adopting a footprint the maze never reserved is the
                    // fault that had corridors carved straight through a built
                    // template. Try again; if every attempt fails, the synthetic
                    // placeholder computed above stands.
                    continue;
                }
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
                break;
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

        // Backlog #44: how many of each authored template the whole dungeon has committed. Per-floor
        // counts live inside the loop below, so they reset with each floor.
        Map<String, Integer> templatesInDungeon = new HashMap<>();

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
            // #46: set only when an authored boss template actually assembled AND was adopted. The
            // role follows this rather than the floor index, which is what keeps #38's invariant
            // intact -- an attempted-but-failed boss room must leave a slot this mod still builds.
            BossSlot bossSlot = null;
            if (i < floorCount - 1) {
                endFootprint = transitionLocalFootprints.get(i);
            } else {
                bossSlot = placeBossRoom(footprint, startFootprint, planAnchor, floorFloors[i],
                        random);
                if (bossSlot != null) {
                    endFootprint = bossSlot.footprint();
                } else {
                    // Bottom-floor terminal room. 7x7 synthetic by default; an authored boss
                    // template hands over its own measured footprint instead (#46).
                    endFootprint = placeAvoidingStart(footprint, terminalRoomWidth,
                            terminalRoomDepth, startFootprint, random);
                    if (endFootprint == null) {
                        return Optional.empty();
                    }
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
            } else if (bossSlot != null && !bossSlot.doorLocalCells().isEmpty()) {
                // An authored boss room's own dungeons2:door markers, exactly where a transition's
                // go. degrees stays 1 above: one path in is the point of the room.
                endRoom.setCandidateDoorways(bossSlot.doorLocalCells());
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
            Map<String, Integer> templatesOnFloor = new HashMap<>();
            Map<IRoom2D, String> templateIdByRoom = new IdentityHashMap<>();
            if (bossSlot != null) {
                // Tags the END room as template-covered, which is what makes commitStagedRooms
                // adopt the staged boss pieces -- it keys adoption on a non-null templateId, so a
                // boss room without one would be staged, never matched, and silently discarded.
                templateIdByRoom.put(endRoom, "dungeons2:end_rooms/assembled");
            }
            Set<Coords2D> templateRoomPremadeLocalCells = new HashSet<>();
            if (roomAssembler != null) {
                List<Rectangle2D> roomReserved = new ArrayList<>();
                roomReserved.add(startFootprint);
                roomReserved.add(endFootprint);
                // MEASURE FIRST, THEN RESERVE -- exactly as for transitions above, and
                // for the same reason: vanilla may ROTATE the prefab, which moves its
                // bounding box's min corner off the position we asked for (6 blocks,
                // for the 7x7 prefabs that ship). A slot picked before that is known
                // is a guess, and 44% of them were being thrown away.
                for (int attempt = 0; attempt < roomTemplateAttempts; attempt++) {
                    long assemblySeed = random.nextLong();
                    int probeWorldX = planAnchor.getX();
                    int probeWorldZ = planAnchor.getZ();
                    Optional<AssembledRoom> probe = roomAssembler.assemble(
                            probeWorldX, floorFloors[i], probeWorldZ, assemblySeed, false);
                    if (probe.isEmpty()) {
                        continue;
                    }
                    // #44: reject a capped template HERE, on the probe, rather than after
                    // committing. The probe and the commit use the same assemblySeed and therefore
                    // draw the same prefab, so the identity is already known -- and rejecting now
                    // avoids staging a room in the Forge shell that would only be discarded, which
                    // is the kind of half-placed state the staging list exists to prevent.
                    //
                    // A capped draw COSTS AN ATTEMPT rather than re-rolling within one. Deliberate:
                    // re-rolling needs a bound anyway (a pool whose every template is capped out
                    // would spin), and spending the attempt degrades the right way -- fewer prefab
                    // rooms, with ordinary procedural fill covering the gap.
                    Optional<String> templateId = probe.get().rootElementId();
                    if (!allowsAnotherCopy(templateId, templatesOnFloor, templatesInDungeon)) {
                        continue;
                    }

                    Rectangle2D probeRect = probe.get().worldFootprint();
                    int offsetX = probeRect.getMinX() - probeWorldX;
                    int offsetZ = probeRect.getMinY() - probeWorldZ;

                    // Reserve at the real size, PREFAB_EDGE_MARGIN clear of the floor's
                    // own outer boundary -- a door candidate on a room's edge would
                    // otherwise sit exactly on the grid's boundary row/column, which
                    // used to crash MazeLevelGenerator2D.generateConnector's unbounded
                    // neighbor lookup (now fixed defensively there too, but a room
                    // shouldn't visually abut the raw map edge regardless).
                    Rectangle2D slot = placeAvoidingReserved(footprint,
                            probeRect.getWidth(), probeRect.getHeight(), roomReserved, random,
                            PREFAB_EDGE_MARGIN);
                    if (slot == null) {
                        continue;
                    }

                    Optional<AssembledRoom> assembledRoom = roomAssembler.assemble(
                            planAnchor.getX() + slot.getMinX() - offsetX, floorFloors[i],
                            planAnchor.getZ() + slot.getMinY() - offsetZ, assemblySeed, true);
                    if (assembledRoom.isEmpty()) {
                        continue;
                    }
                    AssembledRoom ar = assembledRoom.get();
                    warnIfEvenSided("assembled template room", ar.worldFootprint().getWidth(),
                            ar.worldFootprint().getHeight());
                    Rectangle2D wf = ar.worldFootprint();
                    Rectangle2D realFootprint = new Rectangle2D(
                            wf.getMinX() - planAnchor.getX(), wf.getMinY() - planAnchor.getZ(),
                            wf.getWidth(), wf.getHeight());
                    boolean touchesFloorEdge = realFootprint.getMinX() <= 0 || realFootprint.getMinY() <= 0
                            || realFootprint.getMinX() + realFootprint.getWidth() >= footprint.getWidth()
                            || realFootprint.getMinY() + realFootprint.getHeight() >= footprint.getHeight();
                    if (!withinLocalBounds(realFootprint, footprint) || touchesFloorEdge
                            || !noIntersections(realFootprint, roomReserved)) {
                        // Only reachable if the assembler did NOT honour its contract
                        // (see RoomAssembler): the committed prefab came back a
                        // different shape than the probe, so it missed the slot
                        // reserved for it. Skip it -- a prefab room the maze reserved
                        // nothing for gets corridors carved straight through it.
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
                    // Counted HERE, at adoption, not at the commit call above: the planner may
                    // still reject a committed prefab (the bounds check a few lines up), and a
                    // prefab nothing was reserved for never becomes a room a player can walk into.
                    // Counting an attempt that produced nothing would spend a budget on a room that
                    // does not exist.
                    templateId.ifPresent(id -> {
                        templatesOnFloor.merge(id, 1, Integer::sum);
                        templatesInDungeon.merge(id, 1, Integer::sum);
                    });
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
                    .with($ -> $.minRoomGap = minRoomGap)
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
            if (bossSlot != null) {
                // The boss template's dungeons2:connector cells: it built those doors itself, so
                // the emitter must not put a DungeonDoorPiece on top of them.
                premadeCells.addAll(bossSlot.premadeLocalCells());
            }

            FloorLayout floor = convertLevel(
                    levelOpt.get(), i, floorFloors[i], floorCeilings[i],
                    footprint, random, premadeCells, templateIdByRoom, i == floorCount - 1,
                    bossSlot != null);
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

    /**
     * The corridor style for one floor, or the injected single height when no styles were supplied.
     * Every corridor on the floor gets this one &mdash; a style is what a floor's passages
     * <em>are</em>, and rolling per corridor would put an 8-high arched run and a 5-high flat one on
     * either side of the same door.
     *
     * <h2>Why its own Random</h2>
     * <p>Rolled from a salted {@link #mixSeed} rather than off the shared {@code random} the rest of
     * {@code convertLevel} draws from. Drawing from the shared stream would shift every subsequent
     * draw, so simply <em>authoring</em> a styles list would relayout every floor of every existing
     * seed &mdash; and a motif that authors none would still pay for the check. This way a motif
     * without styles generates byte-identically to before, which is also what makes the existing
     * regression suite a meaningful check on this change.</p>
     *
     * <p>The salt keeps this uncorrelated with the maze seed for the same floor, which is
     * {@code mixSeed(seed, floorIndex)} unsalted.</p>
     */
    /**
     * Whether one more copy of this template may be placed &mdash; backlog #44.
     *
     * <p><strong>An unidentifiable template is unlimited, not blocked.</strong>
     * {@code rootElementId} comes back empty for an empty pool element, a feature element, or a
     * pool holding an inline template that vanilla itself refuses to serialise (see
     * {@code PoolElementIds}). Treating "I could not tell what this is" as "do not place it" would
     * turn an obscure pool-authoring choice into a dungeon with no prefab rooms at all, whereas an
     * uncapped room is a cosmetic disappointment. Degrade toward generating.</p>
     */
    private boolean allowsAnotherCopy(Optional<String> templateId,
                                      Map<String, Integer> onFloor, Map<String, Integer> inDungeon) {
        if (templateId.isEmpty() || templateLimits.isEmpty()) {
            return true;
        }
        TemplateLimit limit = templateLimits.get(templateId.get());
        if (limit == null) {
            return true;
        }
        return limit.allows(onFloor.getOrDefault(templateId.get(), 0),
                inDungeon.getOrDefault(templateId.get(), 0));
    }

    private CorridorStyleWeight rollCorridorStyle(int floorIndex) {
        if (corridorStyles.isEmpty()) {
            return new CorridorStyleWeight(CorridorData.BASELINE_STYLE, 1, corridorHeight);
        }
        if (corridorStyles.size() == 1) {
            return corridorStyles.get(0);
        }
        int total = 0;
        for (CorridorStyleWeight style : corridorStyles) {
            total += style.weight();
        }
        // A list ordered by the datapack, indexed by an int -- deliberately not a Map lookup.
        // See the planner's EnumMap fix: iterating a HashMap keyed by anything without a stable
        // hash made prims() pick a different direction from one JVM run to the next, and no
        // in-JVM test could see it.
        int roll = new Random(CORRIDOR_STYLE_SALT ^ mixSeed(seed, floorIndex)).nextInt(total);
        for (CorridorStyleWeight style : corridorStyles) {
            roll -= style.weight();
            if (roll < 0) {
                return style;
            }
        }
        return corridorStyles.get(corridorStyles.size() - 1);
    }

    /** Keeps the style roll uncorrelated with the maze roll that shares {@link #mixSeed}. */
    private static final long CORRIDOR_STYLE_SALT = 0x5CB1D025791E5L;

    /** As {@link #CORRIDOR_STYLE_SALT}, for the entrance offset. See {@link #entranceStart}. */
    private static final long ENTRANCE_OFFSET_SALT = 0xE27A9CE0FF5E7L;

    /**
     * Where the entrance sits along one axis of floor 0's grid &mdash; rolled, not centred.
     *
     * <h2>What this buys</h2>
     * <p>A centred entrance means every dungeon is entered at its dead middle and spreads equally in
     * all four directions, which is the one thing about a floor's shape that never varies. Offset,
     * you come down into a corner or an edge and the maze runs away from you: same rooms, same
     * corridors, different dungeon to walk.</p>
     *
     * <p>Note the entrance does <strong>not</strong> move in the world &mdash; it is assembled first
     * and the grid's world anchor is derived backwards from it. What moves is the <em>grid around
     * it</em>, so this changes the dungeon's shape rather than its location.</p>
     *
     * <h2>The bound is derived, not authored</h2>
     * <p>The entrance may sit anywhere that still leaves {@link #ENTRANCE_MARGIN} clear on
     * <em>both</em> sides &mdash; which is exactly the invariant the margin was introduced for, and
     * is why this needs no new constant and no new config knob. It falls out with two useful
     * properties: a floor only just big enough for the entrance stays centred (slack is 0, so the
     * roll is a no-op), and a roomy floor gets a large range. The grid is sized
     * {@code max(rolled, ew + 2 * ENTRANCE_MARGIN)}, so the slack can never be negative.</p>
     *
     * <h2>Its own Random, for the reason {@code rollCorridorStyle} documents</h2>
     * <p>Drawing from the shared {@code random} would shift every subsequent draw and relayout every
     * floor of every existing seed &mdash; which would also make the before/after measurement of
     * this very change meaningless, since every number would move for reasons unrelated to the
     * entrance. Salted off {@link #mixSeed} instead, so the <em>only</em> thing that differs from a
     * previous run is where the entrance sits.</p>
     *
     * @param axis 0 for X, 1 for Z, so the two axes roll independently rather than in lockstep
     */
    private int entranceStart(int gridExtent, int entranceExtent, int axis) {
        int slack = gridExtent - entranceExtent - 2 * ENTRANCE_MARGIN;
        if (slack <= 0) {
            return makeEven(Math.max(0, (gridExtent - entranceExtent) / 2));
        }
        int offset = new Random(ENTRANCE_OFFSET_SALT ^ mixSeed(seed, axis)).nextInt(slack + 1);
        // makeEven rounds DOWN, so the near-side margin can only grow; the far side is bounded by
        // the slack itself. Both stay >= ENTRANCE_MARGIN.
        return makeEven(ENTRANCE_MARGIN + offset);
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
    /**
     * Footprints already reported, so an even-sided template is named once rather than once per
     * chunk. Keyed by what-and-size, not a one-shot latch: a pack with two such templates has two
     * things to be told about. Same shape as {@code DungeonStructure#WARNED_PITCHES}.
     */
    private static final Set<String> WARNED_EVEN_FOOTPRINTS = ConcurrentHashMap.newKeySet();

    /**
     * Reports an <strong>authored</strong> footprint with an even side.
     *
     * <h2>Why this is a log and not a rejection</h2>
     * <p>Two different parity rules get conflated as "rooms must be odd", and only one of them is
     * real. A room's <em>origin</em> must be even and {@code MazeLevelGenerator2D.isRoomValid}
     * enforces that. A room's <em>size</em> is a different matter: {@code generateRoomSize} forces
     * odd, but only for rooms the maze generates itself, and {@code isRoomValid} never checks size
     * at all &mdash; so a supplied footprint with an even side passes straight through, silently.</p>
     *
     * <p>Measured over 200 seeds, an even-sided supplied room plans, gets its doorway and renders a
     * complete wall ring every time. It is <strong>not broken</strong>, which is exactly why this
     * refuses nothing. What it costs is one wasted cell per axis: an even origin plus an odd size
     * puts the far edge on an even cell, while an even size puts it on the odd <em>passage</em>
     * lane, so the maze's own wall column lands alongside the room's and the two read as a doubled
     * wall.</p>
     *
     * <p>There is a second cost this does <em>not</em> warn about, because it does not apply to
     * authored content: an even interior has no centre cell, so the centred pattern providers
     * ({@code CentrePillar}, {@code Quartet}, {@code CentreSurface}, {@code CrossFloor}) compute
     * {@code (n-1)/2} and sit one cell off. An authored template lays out its own interior, so it
     * never meets them. That is why an even boss room is a reasonable thing to author and an even
     * procedural room is not.</p>
     *
     * <p>Per the PARITY NOTE above, this covers every authored footprint &mdash; entrance,
     * transition, template room and terminal room &mdash; rather than only the one that prompted
     * it.</p>
     */
    private static void warnIfEvenSided(String what, int width, int depth) {
        if ((width & 1) == 1 && (depth & 1) == 1) {
            return;
        }
        if (!WARNED_EVEN_FOOTPRINTS.add(what + " " + width + "x" + depth)) {
            return;
        }
        Dungeons.LOGGER.warn(
                "[D2-PARITY] {} footprint is {}x{} -- {} even. Rooms are laid out on an odd-cell "
                        + "maze lattice, so an even side puts the far wall on a corridor lane and "
                        + "the maze's own wall column lands beside it: a doubled wall, and one "
                        + "wasted cell on that axis. Nothing breaks and nothing is rejected -- use "
                        + "odd sides if you want the cell back.",
                what, width, depth,
                (width & 1) == 0 && (depth & 1) == 0 ? "both sides are"
                        : ((width & 1) == 0 ? "the width is" : "the depth is"));
    }

    /**
     * An adopted boss room: its floor-local footprint, the door cells the maze may open, and the
     * {@code dungeons2:connector} cells whose doors the template already built.
     */
    private record BossSlot(Rectangle2D footprint, List<Coords2D> doorLocalCells,
                            List<Coords2D> premadeLocalCells) {}

    /**
     * Tries to seat an authored boss room in the bottom floor's terminal slot &mdash; #46. Returns
     * null when there is no assembler, no draw fits, or the assembler broke its contract; the caller
     * then reserves the synthetic terminal room and the slot stays {@code TERMINAL}.
     *
     * <p>Measure-then-reserve, the same protocol the transition and interior-room paths use and for
     * the same reason: vanilla rotates the prefab, which moves its bounding box's min corner off the
     * position asked for, so a slot chosen before the real footprint is known is a guess.</p>
     *
     * <p><strong>The direction of constraint is the one difference.</strong> A transition proposes a
     * position and the planner accepts or rejects it; here the footprint is measured first and then
     * {@link #placeAvoidingStart} finds it a home on a floor whose size is already known. That is a
     * simpler problem, not a new one.</p>
     */
    private BossSlot placeBossRoom(Rectangle2D footprint, Rectangle2D startFootprint,
                                   ICoords planAnchor, int floorY, Random random) {
        if (bossRoomAssembler == null) {
            // Before any draw from `random` -- see the field's note on byte-identical plans.
            return null;
        }
        for (int attempt = 0; attempt < BOSS_ASSEMBLY_ATTEMPTS; attempt++) {
            long assemblySeed = random.nextLong();
            Optional<AssembledRoom> probe = bossRoomAssembler.assemble(
                    planAnchor.getX(), floorY, planAnchor.getZ(), assemblySeed, false);
            if (probe.isEmpty()) {
                continue;
            }
            Rectangle2D probeRect = probe.get().worldFootprint();
            warnIfEvenSided("boss room", probeRect.getWidth(), probeRect.getHeight());
            // No PREFAB_EDGE_MARGIN: unlike an interior prefab this slot is the floor's END, and the
            // maze reserves it before routing anything, so its door candidates are the template's
            // own rather than cells the corridor carver has to find room beside.
            Rectangle2D slot = placeAvoidingStart(footprint, probeRect.getWidth(),
                    probeRect.getHeight(), startFootprint, random);
            if (slot == null) {
                // Too big for this floor. Costs an attempt rather than re-rolling within one, the
                // same bound the interior-room path applies to a capped template.
                continue;
            }
            int offsetX = probeRect.getMinX() - planAnchor.getX();
            int offsetZ = probeRect.getMinY() - planAnchor.getZ();
            Optional<AssembledRoom> placed = bossRoomAssembler.assemble(
                    planAnchor.getX() + slot.getMinX() - offsetX, floorY,
                    planAnchor.getZ() + slot.getMinY() - offsetZ, assemblySeed, true);
            if (placed.isEmpty()) {
                continue;
            }
            Rectangle2D wf = placed.get().worldFootprint();
            Rectangle2D real = new Rectangle2D(wf.getMinX() - planAnchor.getX(),
                    wf.getMinY() - planAnchor.getZ(), wf.getWidth(), wf.getHeight());
            if (!withinLocalBounds(real, footprint)
                    || !noIntersections(real, List.of(startFootprint))) {
                // Only reachable if the assembler did not honour its contract: the committed prefab
                // came back a different shape than the probe and missed the slot reserved for it.
                continue;
            }
            List<Coords2D> premadeLocal = toLocalCells(placed.get().premadeWorldCells(), planAnchor);
            List<Coords2D> doorsLocal = new ArrayList<>(
                    toLocalCells(placed.get().doorWorldCells(), planAnchor));
            doorsLocal.addAll(premadeLocal);
            return new BossSlot(real, doorsLocal, premadeLocal);
        }
        return null;
    }

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
        return placeAvoidingReserved(footprint, w, d, reserved, random, 0);
    }

    /**
     * Variant that also keeps the rect {@code margin} cells clear of the footprint's
     * own outer boundary on every side.
     *
     * <p>Used for jigsaw-assembled interior rooms, which must not sit flush against
     * the grid edge (a door candidate on such a room's edge lands on the grid's
     * boundary row/column). Building that into the placement means the slot is
     * <em>born</em> valid instead of being placed and then thrown away &mdash; which
     * matters now that choosing a slot costs a real assembly to measure.</p>
     *
     * <p>{@code margin} must be EVEN: the maze rejects odd-origin rooms, and the
     * origins this returns are the even-aligned offsets shifted by {@code margin}.</p>
     */
    private Rectangle2D placeAvoidingReserved(Rectangle2D footprint, int w, int d,
                                               List<Rectangle2D> reserved, Random random, int margin) {
        int xRange = footprint.getWidth() - w - 2 * margin;
        int zRange = footprint.getHeight() - d - 2 * margin;
        if (xRange < 0 || zRange < 0) return null;
        // Phase 1: random attempts.
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            int x = (xRange == 0) ? 0 : random.nextInt(xRange + 1);
            int z = (zRange == 0) ? 0 : random.nextInt(zRange + 1);
            if ((x & 1) != 0) x--;
            if ((z & 1) != 0) z--;
            if (x < 0) x = 0;
            if (z < 0) z = 0;
            Rectangle2D candidate = new Rectangle2D(x + margin, z + margin, w, d);
            if (noIntersections(candidate, reserved)) {
                return candidate;
            }
        }
        // Phase 2: exhaustive even-aligned scan (deterministic, finds a slot if any exists).
        for (int x = 0; x <= xRange; x += 2) {
            for (int z = 0; z <= zRange; z += 2) {
                Rectangle2D candidate = new Rectangle2D(x + margin, z + margin, w, d);
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
                                      Map<IRoom2D, String> templateIdByRoom, boolean bottomFloor,
                                      boolean bossAdopted) {
        FloorLayout floor = new FloorLayout(floorIndex, floorY, ceilingY, footprint);
        // Stash the maze grid (transient) so the renderer's corridor builder can
        // resolve neighbor wall cells. Not serialized; see FloorLayout#grid.
        floor.setGrid(level.getGrid());

        // Rooms.
        for (IRoom2D room2D : level.getRooms()) {
            // The bottom floor's end room is TERMINAL, not END: END means "a downstairs transition
            // occupies this slot", and there is no downstairs from the bottom floor. Marked here
            // rather than tested for in the emitter, so the one place that knows a floor is the
            // last one is the one place that decides.
            RoomRole role = room2D.isStart() ? RoomRole.START
                    : (room2D.isEnd()
                        ? (bottomFloor
                            // #46: BOSS only when the authored template assembled AND was adopted.
                            // TERMINAL otherwise -- see RoomRole, and #38 for what happens if a
                            // slot is marked covered when nothing covers it.
                            ? (bossAdopted ? RoomRole.BOSS : RoomRole.TERMINAL)
                            : RoomRole.END)
                        : RoomRole.NORMAL);
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
        CorridorStyleWeight corridorStyle = rollCorridorStyle(floorIndex);
        for (CorridorData cd : corridorMap.values()) {
            cd.setWallHeight(corridorStyle.height());
            cd.setStyleName(corridorStyle.name());
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
                        // A premade (dungeons2:connector) cell gets NOTHING from the
                        // corridor -- not a wall column, not a pierced one. Its
                        // template already built a real door there, and assembled
                        // pieces are added to the builder BEFORE procedural ones
                        // (see DungeonStructure), so anything emitted here lands on
                        // top of that door. An ordinary DOOR cell survives the same
                        // treatment only because a DungeonDoorPiece runs last and
                        // rebuilds sill/door/lintel over it; a premade cell has no
                        // such piece by design, so the damage would be permanent.
                        if (premadeCells.contains(new Coords2D(nx, nz))) {
                            continue;
                        }
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

    /**
     * Rolls a room's interior height and tapers it by footprint &mdash; backlog #51.
     *
     * <p>This used to be {@code min(rolled, max(width, depth))}, a cap that <em>rose</em> with the
     * footprint, so the only rooms that could be tall were the big ones. That is the wrong way
     * round: a big tall room is a box, a small tall room is a shaft. {@link RoomHeightBand} carries
     * the inverted table.</p>
     *
     * <p><strong>The roll stays where it is and the band clamps it.</strong> Same argument as #50's
     * world-bottom clamp: {@code 5 + nextInt(6)} consumes an identical amount of the stream
     * whatever band matches, so the maze, the footprints and the corridors of every existing seed
     * come out byte-identical and only the heights move. Drawing inside the band instead
     * (`min + nextInt(span)`) would draw a different amount &mdash; {@code java.util.Random}
     * rejection-samples for a non-power-of-two bound &mdash; and relayout every dungeon in every
     * existing world.</p>
     */
    private int pickRoomHeight(Random random, int width, int depth) {
        int rolled = 5 + random.nextInt(6); // 5..10 inclusive
        return RoomHeightBand.forLongSide(roomHeightBands, Math.max(width, depth)).clamp(rolled);
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
