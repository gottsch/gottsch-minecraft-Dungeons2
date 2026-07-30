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
package mod.gottsch.forge.dungeons2.core.world.structure;

import com.mojang.serialization.Codec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfigHelper;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.data.TransitionData;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The dungeon's worldgen {@link Structure} &mdash; the Phase 4 planning entry
 * point. Once per placement it seeds the RNG from the chunk, resolves a surface
 * Y, runs the {@link DungeonStackPlanner}, and emits every {@link DungeonLayout}
 * node as a {@code StructurePiece} via {@link DungeonPieceEmitter}.
 *
 * <p><strong>No block writes.</strong> All world mutation happens later, in each
 * piece's {@code postProcess}, clipped to the chunk box. This class is pure
 * layout.</p>
 *
 * <h2>Determinism</h2>
 * <p>The only randomness is the planner's (seeded from
 * {@code chunkPos.toLong() ^ worldSeed}) and vanilla {@link JigsawPlacement}'s
 * own chunk-seeded assembly. Same chunk + same world seed &rArr; identical
 * piece list.</p>
 *
 * <h2>Phase 4b &mdash; jigsaw-assembled entrance</h2>
 * <p>The entrance is assembled up front by vanilla {@link JigsawPlacement} from
 * the {@code dungeons2:entrance/surface_exit} start pool. Its {@code dungeons2:door}
 * jigsaw markers are read back off the assembled pieces and drive floor 0's
 * walking-plane Y, reserved START footprint, and candidate doorways &mdash; all fed
 * into the planner via {@link DungeonStackPlanner#withAssembledEntrance}. The
 * assembled pieces are added to the builder alongside the procedural pieces; vanilla
 * handles converting unconnected door jigsaws to their {@code final_state}. When no
 * entrance assembles (e.g. the pool is absent) the planner falls back to a
 * chunk-centered synthetic layout with no rendered entrance.</p>
 *
 * @author Mark Gottschling on Jun 19, 2026
 */
public class DungeonStructure extends Structure {

    public static final Codec<DungeonStructure> CODEC = simpleCodec(DungeonStructure::new);

    /**
     * Phase 4b entrance assembly parameters. The start pool is the surface
     * building; vanilla {@link JigsawPlacement} chains it down into the descent
     * pool(s). {@code maxDepth} is kept small so assembly never recurses into
     * the dungeon body.
     *
     * <p><strong>Not motif-parametrized (unlike transitions/rooms below).</strong>
     * {@code surface_exit.nbt}/{@code descent_1.nbt} have their own jigsaw
     * assembly-joint {@code pool}/{@code target} fields baked into their NBT,
     * cross-referencing each other by exact resource-location string
     * ({@code dungeons2:entrance/descent}, {@code .../descent_top},
     * {@code .../surface_exit}). Moving this pool under a motif subfolder would
     * require rewriting those embedded strings too -- safe to do by re-saving the
     * jigsaw blocks in-game (Target Pool field), not something to blind-edit in
     * compressed NBT. Rooms/transitions don't have this problem: their current
     * content has no cross-pool references at all (see each's own doc below), so
     * only the entrance is held back from motif support this pass.</p>
     */
    private static final ResourceLocation ENTRANCE_START_POOL =
            new ResourceLocation(Dungeons.MOD_ID, "entrance/surface_exit");
    private static final int ENTRANCE_MAX_DEPTH = 5;
    private static final int ENTRANCE_MAX_DISTANCE = 116;

    /** Jigsaw {@code name} that marks a maze door candidate (vs. an assembly joint). */
    private static final String DOOR_JIGSAW_NAME = Dungeons.MOD_ID + ":door";

    /**
     * Jigsaw {@code name} that marks a "premade door" -- a candidate the maze may
     * pick exactly like {@link #DOOR_JIGSAW_NAME}, but whose template already has
     * a real, fully-built door at that cell, so no {@code DungeonDoorPiece} gets
     * generated for it (see {@code DungeonStackPlanner.convertLevel}'s
     * {@code premadeCells} handling). Authored the same way as a door candidate
     * (front faces outward, local Y=0, &ge;2 from corners) except the wall/door
     * itself should be built as a real, already-open doorway rather than solid.
     */
    private static final String CONNECTOR_JIGSAW_NAME = Dungeons.MOD_ID + ":connector";

    /**
     * Transition assembly parameters. Unlike the entrance (anchored at the
     * surface, chaining down), transitions are anchored at the LOWER floor's
     * walking plane and chain UPWARD -- {@code ladder1}/{@code stairs_1} are
     * authored with local Y=0 at the lower floor's plane, so assembly must start
     * there. The start pool mixes those complete, self-contained templates with
     * -- once authored -- bottom/segment/top chains; see the "transition jigsaw
     * pools" section of {@code data/dungeons2/structures/README.md}. {@code
     * maxDepth} is a safety cap only, not a design constraint: real chain length
     * is bounded by whatever the author actually builds into the pools, not by us.
     *
     * <p>Motif-parametrized: {@code ladder1.nbt}/{@code stairs_1.nbt} are complete,
     * self-contained pieces with no outgoing joint, so they carry no cross-pool
     * references -- safe to relocate under a motif subfolder, unlike the entrance
     * (see its doc above). A future chained (segmented) transition author is
     * responsible for pointing their own joint at whatever motif-scoped pool name
     * they're building against, same as any other authoring hygiene.</p>
     */
    private static ResourceLocation transitionStartPool(String motifValue) {
        return new ResourceLocation(Dungeons.MOD_ID, "transitions/" + motifValue + "/shaft_bottom");
    }
    private static final int TRANSITION_MAX_DEPTH = 6;
    private static final int TRANSITION_MAX_DISTANCE = 32;

    /**
     * Phase 8: jigsaw-assembled interior ("NORMAL") room prefabs. Unlike transitions,
     * a room is a single self-contained piece at one Y anchor (the floor's own
     * walking plane) -- no chaining, so {@code maxDepth} is a safety cap only, not a
     * design constraint the author needs to size a chain against.
     *
     * <p>Motif-parametrized: a room is never chained (no outgoing joint at all,
     * by design -- see the Phase 8 plan), so there's no cross-pool reference risk
     * moving it under a motif subfolder.</p>
     */
    private static ResourceLocation roomStartPool(String motifValue) {
        return new ResourceLocation(Dungeons.MOD_ID, "rooms/" + motifValue + "/normal");
    }
    private static final int ROOM_MAX_DEPTH = 1;
    private static final int ROOM_MAX_DISTANCE = 16;

    /**
     * Valid range for an assembled transition's realized height. Diagnostic only,
     * for the height-mismatch warning in {@link #scanTransitionGeometry} -- kept
     * in sync by hand since this class doesn't have a live reference to the
     * planner's (currently un-customized) floor constants.
     *
     * <p>The floor-to-floor pitch a transition bridges is {@code floorHeight +
     * gapBetweenFloors} (10+2=12 with current defaults) -- that's the MINIMUM a
     * transition must reach to actually connect the two floor planes. It can be
     * taller than that, up to {@code floorHeight*2 + gapBetweenFloors} (10*2+2=22):
     * the transition's own footprint is reserved as the upper floor's START room,
     * which already budgets a full {@code floorHeight} of vertical room at that XZ
     * column regardless of what's built there, so the transition can use any of
     * that slack without overflowing into unreserved territory. 22 is a max, not
     * an exact target -- a shorter (but still &ge; 12) transition is fine.</p>
     */
    private static final int MIN_TRANSITION_HEIGHT = 12;
    private static final int MAX_TRANSITION_HEIGHT = 22;

    public DungeonStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        // Chunk center; the assembled entrance is placed here, and floor 0's grid
        // is anchored to wherever its descent actually lands (see below).
        int chunkCenterX = chunkPos.getMiddleBlockX();
        int chunkCenterZ = chunkPos.getMiddleBlockZ();

        // Worldgen-time surface height (WG heightmap never triggers neighbor loads).
        int surfaceY = context.chunkGenerator().getFirstFreeHeight(
                chunkCenterX, chunkCenterZ, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());

        long seed = chunkPos.toLong() ^ context.seed();
        // Motif is fixed for now (CLASSIC is the only authored motif); size and
        // floor count are rolled deterministically inside the planner from seed.
        String motifValue = DungeonMotif.CLASSIC.getValue();
        StructureTemplateManager templateManager = context.structureTemplateManager();
        BlockPos position = new BlockPos(chunkCenterX, surfaceY, chunkCenterZ);

        // Phase 4b: assemble the jigsaw entrance FIRST — its dungeons2:door markers
        // define floor 0's walking-plane Y, footprint, and candidate doorways, all
        // of which the maze planner needs as inputs.
        List<StructurePiece> entrancePieces = assembleEntrance(context, position);
        EntranceGeometry geo = scanEntranceGeometry(entrancePieces, templateManager, seed);

        // Transitions assemble lazily, one per inter-floor link, as the planner
        // works out where each one should go -- see DungeonStackPlanner's
        // TransitionAssembler. Real pieces accumulate here (mirroring
        // entrancePieces) so they can be added to the builder directly, the same
        // way the assembled entrance bypasses DungeonPieceEmitter.
        // STAGED, not committed: the planner may reject the geometry we hand back
        // (out of the floor's grid bounds, or overlapping the reserved start slot)
        // and fall back to its synthetic placeholder. A rejected transition is one
        // the maze knows nothing about -- no reserved footprint, and none of its
        // dungeons2:connector cells registered -- so if its pieces were placed
        // anyway, corridors and rooms would be carved straight through the built
        // template. Keyed by world footprint so accepted groups can be picked out
        // once plan() has decided; see commitStagedTransitions.
        List<StagedTransition> stagedTransitions = new ArrayList<>();
        DungeonStackPlanner.TransitionAssembler transitionAssembler = (worldX, worldY, worldZ, assemblySeed, commit) -> {
            // Seed the WorldgenRandom that JigsawPlacement.addPieces draws EVERY
            // choice from -- the start template, the rotation, the shuffled child
            // templates and their rotations. Without this the context's random just
            // advances call to call, and the planner could not measure a chain's
            // footprint with one call and then reproduce that same chain with
            // another (see TransitionAssembler's contract). Reseeding also makes
            // assembly independent of how many calls came before it, which is what
            // keeps the whole dungeon deterministic for a given chunk seed.
            context.random().setSeed(assemblySeed);
            // Vanilla's SinglePoolElement.getGroundLevelDelta() defaults to 1 (never
            // overridden for our single_pool_element entries), and JigsawPlacement.
            // addPieces uses it to move the placed piece DOWN by exactly 1 block
            // relative to the Y passed in here: it computes
            // l = boundingBox.minY() + groundLevelDelta (= worldY + 1 for the first
            // piece, since minY starts out equal to the position we pass) and
            // k = position.getY() (unchanged, no heightmap projection), then calls
            // piece.move(0, k - l, 0) = move(0, -1, 0). Request one block higher so
            // the piece's real local Y=0 lands exactly at worldY after that shift.
            BlockPos candidatePos = new BlockPos(worldX, worldY + 1, worldZ);
            List<StructurePiece> assembled = assembleTransition(context, candidatePos, motifValue);
            TransitionGeometry tgeo = scanTransitionGeometry(assembled, templateManager, seed, worldY);
            if (tgeo == null) {
                return Optional.empty();
            }
            if (commit) {
                // A measuring probe (commit == false) is read for its geometry and
                // discarded -- staging it would put a second copy of the chain in
                // the world at the probe position if its footprint happened to match
                // the one the planner ends up adopting.
                stagedTransitions.add(new StagedTransition(tgeo.worldFootprint(), worldY, assembled));
            }
            return Optional.of(new DungeonStackPlanner.AssembledTransition(
                    tgeo.worldFootprint(), tgeo.topDoorWorldCells(), tgeo.bottomDoorWorldCells(),
                    tgeo.topPremadeWorldCells(), tgeo.bottomPremadeWorldCells()));
        };

        // Phase 8: interior rooms assemble lazily too, two attempts per floor --
        // and are STAGED rather than placed outright, for exactly the reasons
        // stagedTransitions are (see above). The planner may still reject a
        // committed prefab, and a prefab room the maze reserved nothing for gets
        // corridors and other rooms carved straight through it.
        List<StagedRoom> stagedRooms = new ArrayList<>();
        DungeonStackPlanner.RoomAssembler roomAssembler = (worldX, worldY, worldZ, assemblySeed, commit) -> {
            // Same reseeding as transitions -- it is what lets the planner measure a
            // prefab (including whichever rotation vanilla picked) with one call and
            // then reproduce that same prefab with another.
            context.random().setSeed(assemblySeed);
            // Same SinglePoolElement.getGroundLevelDelta()==1 compensation as
            // transitions (see transitionAssembler above) -- request one block
            // higher so the piece's real local Y=0 lands exactly at worldY.
            BlockPos candidatePos = new BlockPos(worldX, worldY + 1, worldZ);
            List<StructurePiece> assembled = assembleRoom(context, candidatePos, motifValue);
            RoomGeometry rgeo = scanRoomGeometry(assembled, templateManager, seed);
            if (rgeo == null) {
                return Optional.empty();
            }
            if (commit) {
                stagedRooms.add(new StagedRoom(rgeo.worldFootprint(), worldY, assembled));
            }
            return Optional.of(new DungeonStackPlanner.AssembledRoom(
                    rgeo.worldFootprint(), rgeo.doorWorldCells(), rgeo.premadeWorldCells()));
        };

        // Hand the entrance's world geometry to the planner, which sizes floor 0's
        // grid (>= the size tier's rolled footprint), maps the door cells to grid
        // space, and returns the world anchor via DungeonLayout#getAnchor. Falls
        // back to a chunk-centered synthetic layout (no rendered entrance) when
        // nothing assembled with door markers.
        DungeonStackPlanner planner =
                new DungeonStackPlanner(seed, new Coords(chunkCenterX, 0, chunkCenterZ),
                        surfaceY, motifValue, new TemplateCatalog());
        planner.withCorridorWidth(DungeonGenerationConfigHelper.get(context.registryAccess()).corridorWidth());
        planner.withTransitionAssembler(transitionAssembler);
        planner.withRoomAssembler(roomAssembler);
        if (geo != null) {
            Rectangle2D entranceWorldRect = new Rectangle2D(geo.minX(), geo.minZ(),
                    geo.maxX() - geo.minX() + 1, geo.maxZ() - geo.minZ() + 1);
            List<Coords2D> doorWorldCells = new ArrayList<>(geo.doorsX().size());
            for (int k = 0; k < geo.doorsX().size(); k++) {
                doorWorldCells.add(new Coords2D(geo.doorsX().get(k), geo.doorsZ().get(k)));
            }
            List<Coords2D> premadeWorldCells = new ArrayList<>(geo.premadeX().size());
            for (int k = 0; k < geo.premadeX().size(); k++) {
                premadeWorldCells.add(new Coords2D(geo.premadeX().get(k), geo.premadeZ().get(k)));
            }
            planner.withAssembledEntrance(entranceWorldRect, doorWorldCells, premadeWorldCells, geo.floor0Y());
        }

        Optional<DungeonLayout> layoutOpt;
        try {
            layoutOpt = planner.plan();
        } catch (RuntimeException e) {
            // Vanilla's command dispatcher swallows exceptions thrown while
            // executing /place structure (or any command) with just a generic
            // chat message -- nothing reaches the logs otherwise. Log the real
            // exception here (our own logger, which does hit logs/debug.log)
            // before rethrowing so the user-visible behavior is unchanged.
            Dungeons.LOGGER.error("DungeonStackPlanner.plan() threw for chunk {}", chunkPos, e);
            throw e;
        }
        if (layoutOpt.isEmpty()) {
            return Optional.empty();
        }
        DungeonLayout layout = layoutOpt.get();

        // Emit anchor comes from the layout (chunk center in synthetic mode, or the
        // entrance-derived anchor in assembled mode). Assembled entrance/transition
        // pieces go in first so later procedural pieces (doors) overwrite shared cells.
        final int emitAnchorX = layout.getAnchor().getX();
        final int emitAnchorZ = layout.getAnchor().getZ();

        return Optional.of(new GenerationStub(position, builder -> {
            try {
                List<StructurePiece> allPieces = new ArrayList<>(entrancePieces);
                allPieces.addAll(commitStagedTransitions(stagedTransitions, layout));
                allPieces.addAll(commitStagedRooms(stagedRooms, layout));
                allPieces.addAll(DungeonPieceEmitter.emit(layout, emitAnchorX, emitAnchorZ));

                // TEMP (Jul 24): "door into untouched terrain" investigation. Logs the
                // full chunk range every piece's bounding box says it should touch, so
                // it can be diffed against DungeonPiece's [D2-TOUCH] lines (every chunk
                // that ACTUALLY got a postProcess call) to find a chunk that's expected
                // but was silently skipped by vanilla's /place-into-already-generated-
                // chunk handling. Remove once resolved.
                int minCx = Integer.MAX_VALUE, maxCx = Integer.MIN_VALUE;
                int minCz = Integer.MAX_VALUE, maxCz = Integer.MIN_VALUE;
                for (StructurePiece p : allPieces) {
                    BoundingBox bb = p.getBoundingBox();
                    minCx = Math.min(minCx, bb.minX() >> 4);
                    maxCx = Math.max(maxCx, bb.maxX() >> 4);
                    minCz = Math.min(minCz, bb.minZ() >> 4);
                    maxCz = Math.max(maxCz, bb.maxZ() >> 4);
                }
                Dungeons.LOGGER.warn(
                        "[D2-EXPECT] anchor=({},{}) pieceCount={} expectedChunkRange=x[{}..{}] z[{}..{}]",
                        emitAnchorX, emitAnchorZ, allPieces.size(), minCx, maxCx, minCz, maxCz);

                allPieces.forEach(builder::addPiece);
            } catch (RuntimeException e) {
                // Same rationale as the planner.plan() try/catch above -- log through
                // our own logger before rethrowing so a bug here is actually visible.
                Dungeons.LOGGER.error("Piece-building lambda threw for chunk {} anchor=({},{})",
                        chunkPos, emitAnchorX, emitAnchorZ, e);
                throw e;
            }
        }));
    }

    /**
     * Scans the assembled entrance pieces for {@code dungeons2:door} and
     * {@code dungeons2:connector} jigsaw markers and returns their world cells +
     * the marker-carrying piece(s)' XZ extent + floor-0 walking-plane Y. Returns
     * {@code null} if no markers are found at all (e.g. assembly produced
     * nothing), signalling the synthetic fallback.
     */
    private static EntranceGeometry scanEntranceGeometry(List<StructurePiece> pieces,
                                                         StructureTemplateManager templateManager, long seed) {
        List<Integer> doorsX = new ArrayList<>();
        List<Integer> doorsZ = new ArrayList<>();
        List<Integer> premadeX = new ArrayList<>();
        List<Integer> premadeZ = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Integer floor0Y = null;
        // Shuffle order is irrelevant (we take every marker); seed only for
        // determinism hygiene.
        RandomSource random = RandomSource.create(seed);

        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            List<StructureTemplate.StructureBlockInfo> jigsaws = pool.getElement()
                    .getShuffledJigsawBlocks(templateManager, pool.getPosition(), pool.getRotation(), random);
            boolean carriesMarker = false;
            for (StructureTemplate.StructureBlockInfo info : jigsaws) {
                CompoundTag nbt = info.nbt();
                if (nbt == null) {
                    continue;
                }
                String name = nbt.getString("name");
                BlockPos p = info.pos();
                if (DOOR_JIGSAW_NAME.equals(name)) {
                    doorsX.add(p.getX());
                    doorsZ.add(p.getZ());
                    floor0Y = (floor0Y == null) ? p.getY() : Math.min(floor0Y, p.getY());
                    carriesMarker = true;
                } else if (CONNECTOR_JIGSAW_NAME.equals(name)) {
                    premadeX.add(p.getX());
                    premadeZ.add(p.getZ());
                    floor0Y = (floor0Y == null) ? p.getY() : Math.min(floor0Y, p.getY());
                    carriesMarker = true;
                }
            }
            if (carriesMarker) {
                BoundingBox bb = pool.getBoundingBox();
                minX = Math.min(minX, bb.minX());
                maxX = Math.max(maxX, bb.maxX());
                minZ = Math.min(minZ, bb.minZ());
                maxZ = Math.max(maxZ, bb.maxZ());
            }
        }
        if (floor0Y == null) {
            return null;
        }
        return new EntranceGeometry(doorsX, doorsZ, premadeX, premadeZ, minX, minZ, maxX, maxZ, floor0Y);
    }

    /** World geometry read off the assembled entrance's door/connector jigsaw markers. */
    private record EntranceGeometry(List<Integer> doorsX, List<Integer> doorsZ,
                                    List<Integer> premadeX, List<Integer> premadeZ,
                                    int minX, int minZ, int maxX, int maxZ, int floor0Y) {
    }

    /**
     * Runs vanilla {@link JigsawPlacement#addPieces} from the entrance start pool
     * and returns the assembled pieces (empty if the pool is absent or nothing
     * assembles). Assembly is run once here and the resulting piece instances are
     * reused, keeping the result deterministic.
     */
    private static List<StructurePiece> assembleEntrance(GenerationContext context, BlockPos position) {
        Registry<StructureTemplatePool> poolRegistry =
                context.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Optional<Holder.Reference<StructureTemplatePool>> startPool = poolRegistry.getHolder(
                ResourceKey.create(Registries.TEMPLATE_POOL, ENTRANCE_START_POOL));
        if (startPool.isEmpty()) {
            return List.of();
        }

        Optional<GenerationStub> stub = JigsawPlacement.addPieces(
                context,
                startPool.get(),
                Optional.empty(),          // start jigsaw name: let vanilla pick
                ENTRANCE_MAX_DEPTH,
                position,
                false,                     // useExpansionHack
                Optional.empty(),          // projectStartToHeightmap: Y controlled via position
                ENTRANCE_MAX_DISTANCE);

        return stub.map(s -> s.getPiecesBuilder().build().pieces()).orElse(List.of());
    }

    /**
     * Runs vanilla {@link JigsawPlacement#addPieces} from the transitions start
     * pool at {@code position} (the candidate the planner picked). Same shape as
     * {@link #assembleEntrance}, just parameterized on the transition pool/depth/
     * distance and callable more than once per chunk (once per inter-floor link).
     */
    private static List<StructurePiece> assembleTransition(GenerationContext context, BlockPos position,
                                                            String motifValue) {
        Registry<StructureTemplatePool> poolRegistry =
                context.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Optional<Holder.Reference<StructureTemplatePool>> startPool = poolRegistry.getHolder(
                ResourceKey.create(Registries.TEMPLATE_POOL, transitionStartPool(motifValue)));
        if (startPool.isEmpty()) {
            return List.of();
        }

        Optional<GenerationStub> stub = JigsawPlacement.addPieces(
                context,
                startPool.get(),
                Optional.empty(),
                TRANSITION_MAX_DEPTH,
                position,
                false,
                Optional.empty(),
                TRANSITION_MAX_DISTANCE);

        return stub.map(s -> s.getPiecesBuilder().build().pieces()).orElse(List.of());
    }

    /**
     * Scans assembled transition pieces for {@code dungeons2:door} and
     * {@code dungeons2:connector} jigsaw markers, bucketing each into the upper
     * floor's candidates vs. the lower floor's, plus the combined XZ footprint
     * across every piece in the chain. Unlike the entrance (one anchor Y), a
     * transition has markers at BOTH ends, many blocks apart -- splitting at the
     * midpoint between the lowest and highest marker Y (across door AND connector
     * markers together) unambiguously separates the two floors' candidates
     * regardless of how many pieces (or which roles) the chain assembled from.
     *
     * <p>Returns {@code null} if no markers are found at all (assembly produced
     * nothing, or the pool is absent), signalling the planner's synthetic
     * fallback -- same convention as {@link #scanEntranceGeometry}.</p>
     */
    private static TransitionGeometry scanTransitionGeometry(List<StructurePiece> pieces,
                                                             StructureTemplateManager templateManager,
                                                             long seed, int placementY) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        boolean any = false;
        RandomSource random = RandomSource.create(seed);
        List<BlockPos> doorPositions = new ArrayList<>();
        List<BlockPos> premadePositions = new ArrayList<>();

        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            any = true;
            BoundingBox bb = pool.getBoundingBox();
            minX = Math.min(minX, bb.minX());
            maxX = Math.max(maxX, bb.maxX());
            minZ = Math.min(minZ, bb.minZ());
            maxZ = Math.max(maxZ, bb.maxZ());
            minY = Math.min(minY, bb.minY());
            maxY = Math.max(maxY, bb.maxY());

            List<StructureTemplate.StructureBlockInfo> jigsaws = pool.getElement()
                    .getShuffledJigsawBlocks(templateManager, pool.getPosition(), pool.getRotation(), random);
            for (StructureTemplate.StructureBlockInfo info : jigsaws) {
                CompoundTag nbt = info.nbt();
                if (nbt == null) {
                    continue;
                }
                String name = nbt.getString("name");
                if (DOOR_JIGSAW_NAME.equals(name)) {
                    doorPositions.add(info.pos());
                } else if (CONNECTOR_JIGSAW_NAME.equals(name)) {
                    premadePositions.add(info.pos());
                }
            }
        }
        if (!any || (doorPositions.isEmpty() && premadePositions.isEmpty())) {
            return null;
        }

        int minMarkerY = Integer.MAX_VALUE, maxMarkerY = Integer.MIN_VALUE;
        for (BlockPos p : doorPositions) {
            minMarkerY = Math.min(minMarkerY, p.getY());
            maxMarkerY = Math.max(maxMarkerY, p.getY());
        }
        for (BlockPos p : premadePositions) {
            minMarkerY = Math.min(minMarkerY, p.getY());
            maxMarkerY = Math.max(maxMarkerY, p.getY());
        }
        int splitY = (minMarkerY + maxMarkerY) / 2;

        List<Coords2D> topDoors = new ArrayList<>();
        List<Coords2D> bottomDoors = new ArrayList<>();
        for (BlockPos p : doorPositions) {
            (p.getY() >= splitY ? topDoors : bottomDoors).add(new Coords2D(p.getX(), p.getZ()));
        }
        List<Coords2D> topPremade = new ArrayList<>();
        List<Coords2D> bottomPremade = new ArrayList<>();
        for (BlockPos p : premadePositions) {
            (p.getY() >= splitY ? topPremade : bottomPremade).add(new Coords2D(p.getX(), p.getZ()));
        }

        int realizedHeight = maxY - minY + 1;
        if (realizedHeight < MIN_TRANSITION_HEIGHT) {
            Dungeons.LOGGER.warn(
                    "assembled transition height {} < minimum {} at placementY={} -- too short to bridge "
                            + "the two floor planes; check the authored template heights",
                    realizedHeight, MIN_TRANSITION_HEIGHT, placementY);
        } else if (realizedHeight > MAX_TRANSITION_HEIGHT) {
            Dungeons.LOGGER.warn(
                    "assembled transition height {} > maximum {} at placementY={} -- taller than the upper "
                            + "floor's own reserved room-height budget at that XZ column; check the authored "
                            + "template heights",
                    realizedHeight, MAX_TRANSITION_HEIGHT, placementY);
        }

        Rectangle2D worldFootprint = new Rectangle2D(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
        return new TransitionGeometry(worldFootprint, topDoors, bottomDoors, topPremade, bottomPremade);
    }

    /**
     * An assembled transition chain held back until the planner accepts its
     * footprint. {@code assemblyY} is the lower floor's walking plane the chain was
     * anchored at — part of the identity because two links can legitimately reserve
     * the same XZ slot on different floors, and only one of them may have been
     * adopted.
     */
    private record StagedTransition(Rectangle2D worldFootprint, int assemblyY, List<StructurePiece> pieces) {
    }

    /**
     * Keeps only the staged transition chains the planner actually adopted.
     *
     * <p>{@code DungeonStackPlanner} asks the assembler for real geometry, then
     * <strong>may reject it</strong> — if the assembled footprint falls outside the
     * floor's own grid bounds (vanilla can rotate a chain into a very different
     * XZ extent) or overlaps the reserved start slot, it keeps its synthetic
     * placeholder instead. A rejected chain is invisible to the maze: no reserved
     * footprint, and none of its {@code dungeons2:connector} cells registered as
     * premade. Placing it anyway means corridors and rooms get carved straight
     * through a fully built staircase — doors walled over, nothing attached.
     *
     * <p>An adopted transition is tagged {@code .../assembled} and carries the real
     * footprint in floor-local coords, so world = layout anchor + local recovers
     * exactly what the assembler handed back. Anything with no match was rejected
     * and must not be placed.</p>
     */
    private static List<StructurePiece> commitStagedTransitions(List<StagedTransition> staged,
                                                                DungeonLayout layout) {
        if (staged.isEmpty()) {
            return List.of();
        }
        int anchorX = layout.getAnchor().getX();
        int anchorZ = layout.getAnchor().getZ();
        Set<String> adopted = new HashSet<>();
        for (TransitionData t : layout.getTransitions()) {
            Rectangle2D fp = t.getFootprint();
            if (fp == null || t.getTemplateId() == null || !t.getTemplateId().contains("assembled")) {
                continue;
            }
            adopted.add(footprintKey(anchorX + fp.getMinX(), anchorZ + fp.getMinY(),
                    fp.getWidth(), fp.getHeight(), t.getLowerY()));
        }

        List<StructurePiece> out = new ArrayList<>();
        for (StagedTransition s : staged) {
            Rectangle2D fp = s.worldFootprint();
            if (adopted.contains(footprintKey(fp.getMinX(), fp.getMinY(), fp.getWidth(), fp.getHeight(),
                    s.assemblyY()))) {
                out.addAll(s.pieces());
            } else {
                Dungeons.LOGGER.debug(
                        "discarding assembled transition at world ({},{},{}) {}x{} -- the planner did not "
                                + "adopt this footprint, so the maze reserved nothing for it",
                        fp.getMinX(), s.assemblyY(), fp.getMinY(), fp.getWidth(), fp.getHeight());
            }
        }
        return out;
    }

    private static String footprintKey(int minX, int minZ, int width, int height, int assemblyY) {
        return minX + "," + minZ + "," + width + "," + height + "@" + assemblyY;
    }

    /**
     * An assembled interior-room prefab held back until the planner accepts its
     * footprint. {@code assemblyY} is the floor's walking plane it was anchored at
     * — part of the identity because two floors can legitimately place a prefab at
     * the same XZ, and only one of them may have been adopted.
     */
    private record StagedRoom(Rectangle2D worldFootprint, int assemblyY, List<StructurePiece> pieces) {
    }

    /**
     * Keeps only the staged room prefabs the planner actually adopted &mdash; the
     * room-side counterpart of {@link #commitStagedTransitions}, and needed for the
     * same reason.
     *
     * <p>The planner rejects a committed prefab only when the assembler broke its
     * contract (the placement came back a different shape than the measuring probe,
     * so it missed the slot reserved for it), but the consequence of placing one
     * anyway is severe and silent: the maze reserved nothing at that footprint, so
     * corridors and other rooms get carved straight through a fully built prefab.</p>
     *
     * <p>An adopted prefab is a {@code NORMAL} room carrying a non-null
     * {@code templateId}; its floor's {@code floorY} is the same walking plane the
     * prefab was assembled at, and is part of the key because two floors can
     * legitimately place a prefab at the same XZ.</p>
     */
    private static List<StructurePiece> commitStagedRooms(List<StagedRoom> staged, DungeonLayout layout) {
        if (staged.isEmpty()) {
            return List.of();
        }
        int anchorX = layout.getAnchor().getX();
        int anchorZ = layout.getAnchor().getZ();
        Set<String> adopted = new HashSet<>();
        for (FloorLayout floor : layout.getFloors()) {
            for (RoomData room : floor.getRooms()) {
                if (room.getTemplateId() == null) {
                    continue;
                }
                adopted.add(footprintKey(anchorX + room.getOriginX(), anchorZ + room.getOriginZ(),
                        room.getWidth(), room.getDepth(), floor.getFloorY()));
            }
        }

        List<StructurePiece> out = new ArrayList<>();
        for (StagedRoom s : staged) {
            Rectangle2D fp = s.worldFootprint();
            if (adopted.contains(footprintKey(fp.getMinX(), fp.getMinY(),
                    fp.getWidth(), fp.getHeight(), s.assemblyY()))) {
                out.addAll(s.pieces());
            } else {
                Dungeons.LOGGER.debug(
                        "discarding assembled room at world ({},{},{}) {}x{} -- the planner did not "
                                + "adopt this footprint, so the maze reserved nothing for it",
                        fp.getMinX(), s.assemblyY(), fp.getMinY(), fp.getWidth(), fp.getHeight());
            }
        }
        return out;
    }

    /** World geometry read off an assembled transition's door/connector jigsaw markers. */
    private record TransitionGeometry(Rectangle2D worldFootprint, List<Coords2D> topDoorWorldCells,
                                      List<Coords2D> bottomDoorWorldCells, List<Coords2D> topPremadeWorldCells,
                                      List<Coords2D> bottomPremadeWorldCells) {
    }

    /**
     * Runs vanilla {@link JigsawPlacement#addPieces} from the Phase 8 rooms start
     * pool at {@code position}. Same shape as {@link #assembleTransition}, just
     * parameterized on the rooms pool/depth/distance and callable more than once
     * per chunk (once per candidate interior-room slot the planner tries).
     */
    private static List<StructurePiece> assembleRoom(GenerationContext context, BlockPos position,
                                                     String motifValue) {
        Registry<StructureTemplatePool> poolRegistry =
                context.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Optional<Holder.Reference<StructureTemplatePool>> startPool = poolRegistry.getHolder(
                ResourceKey.create(Registries.TEMPLATE_POOL, roomStartPool(motifValue)));
        if (startPool.isEmpty()) {
            return List.of();
        }

        Optional<GenerationStub> stub = JigsawPlacement.addPieces(
                context,
                startPool.get(),
                Optional.empty(),
                ROOM_MAX_DEPTH,
                position,
                false,
                Optional.empty(),
                ROOM_MAX_DISTANCE);

        return stub.map(s -> s.getPiecesBuilder().build().pieces()).orElse(List.of());
    }

    /**
     * Scans assembled room pieces for {@code dungeons2:door} and
     * {@code dungeons2:connector} jigsaw markers, plus the combined XZ footprint
     * across every piece. Unlike a transition (markers at both ends, many blocks
     * apart), a room has a single Y anchor, so there's no top/bottom split --
     * every marker just becomes a candidate doorway for this one room. Returns
     * {@code null} if no markers are found at all (assembly produced nothing, or
     * the pool is absent), signalling the planner to skip this candidate slot --
     * same convention as {@link #scanTransitionGeometry}.
     */
    private static RoomGeometry scanRoomGeometry(List<StructurePiece> pieces,
                                                 StructureTemplateManager templateManager, long seed) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean any = false;
        RandomSource random = RandomSource.create(seed);
        List<Coords2D> doorCells = new ArrayList<>();
        List<Coords2D> premadeCells = new ArrayList<>();

        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            any = true;
            BoundingBox bb = pool.getBoundingBox();
            minX = Math.min(minX, bb.minX());
            maxX = Math.max(maxX, bb.maxX());
            minZ = Math.min(minZ, bb.minZ());
            maxZ = Math.max(maxZ, bb.maxZ());

            List<StructureTemplate.StructureBlockInfo> jigsaws = pool.getElement()
                    .getShuffledJigsawBlocks(templateManager, pool.getPosition(), pool.getRotation(), random);
            for (StructureTemplate.StructureBlockInfo info : jigsaws) {
                CompoundTag nbt = info.nbt();
                if (nbt == null) {
                    continue;
                }
                String name = nbt.getString("name");
                BlockPos p = info.pos();
                if (DOOR_JIGSAW_NAME.equals(name)) {
                    doorCells.add(new Coords2D(p.getX(), p.getZ()));
                } else if (CONNECTOR_JIGSAW_NAME.equals(name)) {
                    premadeCells.add(new Coords2D(p.getX(), p.getZ()));
                }
            }
        }
        if (!any || (doorCells.isEmpty() && premadeCells.isEmpty())) {
            return null;
        }

        Rectangle2D worldFootprint = new Rectangle2D(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
        return new RoomGeometry(worldFootprint, doorCells, premadeCells);
    }

    /** World geometry read off an assembled room's door/connector jigsaw markers. */
    private record RoomGeometry(Rectangle2D worldFootprint, List<Coords2D> doorWorldCells,
                                List<Coords2D> premadeWorldCells) {
    }

    @Override
    public StructureType<?> type() {
        return Registration.DUNGEON.get();
    }
}
