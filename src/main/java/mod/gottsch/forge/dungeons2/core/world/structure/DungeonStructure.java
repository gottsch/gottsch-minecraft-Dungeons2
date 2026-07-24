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
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
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
import java.util.List;
import java.util.Optional;

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
     */
    private static final ResourceLocation ENTRANCE_START_POOL =
            new ResourceLocation(Dungeons.MOD_ID, "entrance/surface_exit");
    private static final int ENTRANCE_MAX_DEPTH = 5;
    private static final int ENTRANCE_MAX_DISTANCE = 116;

    /** Jigsaw {@code name} that marks a maze door candidate (vs. an assembly joint). */
    private static final String DOOR_JIGSAW_NAME = Dungeons.MOD_ID + ":door";

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

        // Hand the entrance's world geometry to the planner, which sizes floor 0's
        // grid (>= the size tier's rolled footprint), maps the door cells to grid
        // space, and returns the world anchor via DungeonLayout#getAnchor. Falls
        // back to a chunk-centered synthetic layout (no rendered entrance) when
        // nothing assembled with door markers.
        DungeonStackPlanner planner =
                new DungeonStackPlanner(seed, new Coords(chunkCenterX, 0, chunkCenterZ),
                        surfaceY, motifValue, new TemplateCatalog());
        if (geo != null) {
            Rectangle2D entranceWorldRect = new Rectangle2D(geo.minX(), geo.minZ(),
                    geo.maxX() - geo.minX() + 1, geo.maxZ() - geo.minZ() + 1);
            List<Coords2D> doorWorldCells = new ArrayList<>(geo.doorsX().size());
            for (int k = 0; k < geo.doorsX().size(); k++) {
                doorWorldCells.add(new Coords2D(geo.doorsX().get(k), geo.doorsZ().get(k)));
            }
            planner.withAssembledEntrance(entranceWorldRect, doorWorldCells, geo.floor0Y());
        }

        Optional<DungeonLayout> layoutOpt = planner.plan();
        if (layoutOpt.isEmpty()) {
            return Optional.empty();
        }
        DungeonLayout layout = layoutOpt.get();

        // Emit anchor comes from the layout (chunk center in synthetic mode, or the
        // entrance-derived anchor in assembled mode). Assembled entrance pieces go
        // in first so later procedural pieces (doors) overwrite shared cells.
        final int emitAnchorX = layout.getAnchor().getX();
        final int emitAnchorZ = layout.getAnchor().getZ();

        return Optional.of(new GenerationStub(position, builder -> {
            List<StructurePiece> allPieces = new ArrayList<>(entrancePieces);
            allPieces.addAll(DungeonPieceEmitter.emit(layout, emitAnchorX, emitAnchorZ, templateManager));

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
        }));
    }

    /**
     * Scans the assembled entrance pieces for {@code dungeons2:door} jigsaw markers
     * and returns their world cells + the door-carrying piece(s)' XZ extent +
     * floor-0 walking-plane Y. Returns {@code null} if no door markers are found
     * (e.g. assembly produced nothing), signalling the synthetic fallback.
     */
    private static EntranceGeometry scanEntranceGeometry(List<StructurePiece> pieces,
                                                         StructureTemplateManager templateManager, long seed) {
        List<Integer> doorsX = new ArrayList<>();
        List<Integer> doorsZ = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        Integer floor0Y = null;
        // Shuffle order is irrelevant (we take every door marker); seed only for
        // determinism hygiene.
        RandomSource random = RandomSource.create(seed);

        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece pool)) {
                continue;
            }
            List<StructureTemplate.StructureBlockInfo> jigsaws = pool.getElement()
                    .getShuffledJigsawBlocks(templateManager, pool.getPosition(), pool.getRotation(), random);
            boolean carriesDoor = false;
            for (StructureTemplate.StructureBlockInfo info : jigsaws) {
                CompoundTag nbt = info.nbt();
                if (nbt != null && DOOR_JIGSAW_NAME.equals(nbt.getString("name"))) {
                    BlockPos p = info.pos();
                    doorsX.add(p.getX());
                    doorsZ.add(p.getZ());
                    floor0Y = (floor0Y == null) ? p.getY() : Math.min(floor0Y, p.getY());
                    carriesDoor = true;
                }
            }
            if (carriesDoor) {
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
        return new EntranceGeometry(doorsX, doorsZ, minX, minZ, maxX, maxZ, floor0Y);
    }

    /** World geometry read off the assembled entrance's door jigsaw markers. */
    private record EntranceGeometry(List<Integer> doorsX, List<Integer> doorsZ,
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

    @Override
    public StructureType<?> type() {
        return Registration.DUNGEON.get();
    }
}
