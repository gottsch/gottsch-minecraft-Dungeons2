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

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Base for the three procedural Phase 3 pieces ({@link DungeonRoomPiece},
 * {@link DungeonCorridorPiece}, {@link DungeonDoorPiece}).
 *
 * <p>Holds the fields every procedural piece needs to re-run its Phase 2
 * builder at {@code postProcess} time: the dungeon {@code motif}, the floor's
 * walking-plane {@code floorY} (absolute world Y), and the dungeon's world-space
 * {@code anchor} XZ. The piece's {@link #getBoundingBox() world bounding box} is
 * computed by each subclass from its layout data and round-trips automatically
 * through {@link StructurePiece}'s base NBT (the {@code "BB"} tag).</p>
 *
 * <h2>Coordinate flow</h2>
 * <p>Phase 2 builders emit placements in <em>floor-local grid XZ</em> with
 * <em>absolute world Y</em>. This base translates each placement to world space
 * ({@code anchorX + x}, {@code y}, {@code anchorZ + z}), then to piece-local space
 * for {@link #placeBlock} (which clips to the chunk {@code box} the engine hands
 * us). Pieces are constructed with {@code NORTH} orientation: X and Y are a plain
 * min-offset, but vanilla {@link StructurePiece#getWorldZ} <em>mirrors</em> Z
 * around the piece's own bounding box for that orientation, so piece-local Z is
 * {@code pieceBox.maxZ() - worldZ}, not a min-offset (see {@link #placeAll}).</p>
 *
 * <p><strong>Chunk-safety invariant:</strong> {@code postProcess} never reads or
 * writes outside {@code box}. {@link #placeAll} hands the piece's <em>whole</em>
 * placement list to {@link PieceProcessors#decorate}, which clips between its two
 * passes: the neighbour-aware half needs the whole piece and is contractually barred
 * from reading the level, while the half that may read the existing world block
 * (vanilla's {@code RuleProcessor}) only ever sees positions inside {@code box}.</p>
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
public abstract class DungeonPiece extends StructurePiece {

    protected String motifValue;
    protected int floorY;
    protected int anchorX;
    protected int anchorZ;

    /** Planning constructor. */
    protected DungeonPiece(StructurePieceType type, String motifValue, int floorY,
                           int anchorX, int anchorZ, BoundingBox box) {
        super(type, 0, box);
        this.motifValue = motifValue;
        this.floorY = floorY;
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
        // NORTH = no rotation; piece-local maps to world by a pure min-offset.
        setOrientation(net.minecraft.core.Direction.NORTH);
    }

    /** Load constructor. {@code super} restores genDepth / orientation / bounding box. */
    protected DungeonPiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
        this.motifValue = tag.getString("Motif");
        this.floorY = tag.getInt("FloorY");
        this.anchorX = tag.getInt("AnchorX");
        this.anchorZ = tag.getInt("AnchorZ");
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context,
                                         CompoundTag tag) {
        tag.putString("Motif", motifValue == null ? "" : motifValue);
        tag.putInt("FloorY", floorY);
        tag.putInt("AnchorX", anchorX);
        tag.putInt("AnchorZ", anchorZ);
    }

    /** TEMP (Jul 24): dedup set for {@link #logChunkTouch}, one line per (piece, chunk). */
    private static final java.util.Set<String> CHUNK_TOUCH_LOGGED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * TEMP (Jul 24): "door into untouched terrain" investigation. Logs every chunk
     * a procedural piece's {@code postProcess} is ACTUALLY invoked for, with that
     * chunk's current status, so it can be diffed against {@code DungeonStructure}'s
     * {@code [D2-EXPECT]} line (the full chunk range every piece's bounding box SAYS
     * it should touch) to find a chunk that was silently skipped — i.e. {@code
     * /place} not calling postProcess at all for a chunk that had already finished
     * generating before the command ran. Remove once resolved.
     */
    protected void logChunkTouch(WorldGenLevel level, ChunkPos chunkPos, BoundingBox box) {
        String key = getClass().getSimpleName() + "@anchor(" + anchorX + "," + anchorZ + ")@chunk("
                + chunkPos.x + "," + chunkPos.z + ")";
        if (!CHUNK_TOUCH_LOGGED.add(key)) {
            return;
        }
        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY, false);
        Dungeons.LOGGER.warn(
                "[D2-TOUCH] {} anchor=({},{}) chunk=({},{}) status={} box=[{}..{},{}..{},{}..{}]",
                getClass().getSimpleName(), anchorX, anchorZ, chunkPos.x, chunkPos.z,
                chunk == null ? "NULL" : chunk.getStatus(),
                box.minX(), box.maxX(), box.minY(), box.maxY(), box.minZ(), box.maxZ());
    }

    /** Resolves the stored motif string to an enum, defaulting to CLASSIC. */
    protected IDungeonMotif motif() {
        if (motifValue != null) {
            DungeonMotif motif = DungeonMotif.getByValue(motifValue);
            if (motif != null && motif != DungeonMotif.UNKNOWN) {
                return motif;
            }
        }
        return DungeonMotif.CLASSIC;
    }

    /**
     * Writes every placement into the world, translated from floor-local to
     * world to piece-local, clipped to the chunk {@code box}. Applies any
     * {@link BlockEntityData} for positions that actually landed inside the box.
     */
    protected void placeAll(WorldGenLevel level, BoundingBox box, List<BlockPlacement> placements) {
        BoundingBox pieceBox = getBoundingBox();
        // Origin the processor pass works relative to. Placements are floor-local XZ
        // with absolute Y, so relative-to-origin is (x, y - floorY, z) and vanilla's
        // own `origin + relative` gives back exactly the world position below.
        BlockPos origin = new BlockPos(anchorX, floorY, anchorZ);

        // Deliberately UNCLIPPED: the neighbour-aware half of the decoration pass has to
        // see the whole piece or it decorates the two sides of a chunk seam differently.
        // PieceProcessors.decorate clips between its two passes, before the half that
        // may read the existing world block (RuleProcessor's location_predicate does,
        // and reading outside the current WorldGenRegion during worldgen is illegal).
        List<StructureTemplate.StructureBlockInfo> infos = new ArrayList<>(placements.size());
        for (BlockPlacement p : placements) {
            int worldX = anchorX + p.getX();
            int worldY = p.getY();
            int worldZ = anchorZ + p.getZ();

            BlockState state = BlockStateCodec.resolve(p);
            BlockEntityData be = p.getBlockEntityNbt();
            if (be != null) {
                // Block-entity placements (chests / spawners / signs) bypass the
                // processor pass entirely, preserving the guarantee the procedural
                // decorator pass always had: authored container content is never
                // swapped out from under itself. They are written straight out, so they
                // are the one thing still clipped here.
                BlockPos worldPos = new BlockPos(worldX, worldY, worldZ);
                if (!box.isInside(worldPos)) {
                    continue;
                }
                placeBlock(level, state, worldX - pieceBox.minX(), worldY - pieceBox.minY(),
                        pieceBox.maxZ() - worldZ, box);
                applyBlockEntity(level, worldPos, be);
                continue;
            }
            infos.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(p.getX(), worldY - floorY, p.getZ()), state, null));
        }

        // Decoration pass: the motif's vanilla processor_list, the same datapack file
        // the jigsaw pool JSONs point their "processors" field at, so a prefab room
        // and the procedural room next to it weather identically. Absent list = no
        // decoration, matching the "pool absent" degradation convention.
        List<StructureTemplate.StructureBlockInfo> processed =
                PieceProcessors.decorate(level, origin, box, infos, motifValue);

        List<BlockPos> jointed = new ArrayList<>();
        for (StructureTemplate.StructureBlockInfo info : processed) {
            BlockPos worldPos = info.pos();
            // Vanilla StructurePiece#getWorldZ mirrors Z for NORTH orientation
            // (`boundingBox.maxZ() - z`), not a plain `minZ() + z` offset like X and Y
            // get. This piece is never rotated, so undo exactly that reflection here;
            // getWorldPos then round-trips back to the true worldZ.
            int localX = worldPos.getX() - pieceBox.minX();
            int localY = worldPos.getY() - pieceBox.minY();
            int localZ = pieceBox.maxZ() - worldPos.getZ();
            placeBlock(level, info.state(), localX, localY, localZ, box);

            if (settlesJoinShapes() && hasJoinShape(info.state()) && box.isInside(worldPos)) {
                jointed.add(worldPos.immutable());
            }
        }
        settleJoinShapes(level, box, jointed);
    }

    /**
     * Whether vanilla's neighbour-derived corner shapes may be applied to this piece's blocks.
     *
     * <p>{@code true} for a piece that authors no {@code shape} of its own and wants the mitre &mdash;
     * a room's cornice ring, where vanilla's rule is exactly right because a room is a rectangle with
     * four runs.</p>
     *
     * <p><strong>{@code false} for a piece that derives its own shapes</strong>, and that is not
     * merely an optimisation. {@link #settleJoinShapes} reads a block's <em>neighbours</em>, and
     * {@code postProcess} runs once per chunk a piece spans &mdash; so at a chunk boundary the
     * neighbours on the far side have not been written yet and vanilla derives from air. The same
     * cell therefore settles differently depending on which chunk got there first, leaving a seam
     * running dead straight through a corridor at a multiple of 16. Measured at 5 cells in 75,227
     * before this opt-out existed, all of them arch haunches on a boundary.</p>
     *
     * <p>Deriving the shape from the layout instead makes the answer chunk-independent by
     * construction, which is the only fix available: there is no point during per-chunk placement at
     * which every neighbour exists.</p>
     */
    protected boolean settlesJoinShapes() {
        return true;
    }

    /** True for blocks whose model corners depend on their neighbours (stairs, cornices, mouldings). */
    private static boolean hasJoinShape(BlockState state) {
        return state.getBlock().getStateDefinition().getProperty("shape") != null;
    }

    /**
     * True when the generator already set a corner shape deliberately, in which case vanilla's
     * derivation must not get a vote.
     *
     * <p>Vanilla only reaches the right answer for blocks oriented the way a <em>player</em> would
     * place them. A corridor arch haunch faces into its wall, so
     * {@code StairBlock.getStairsShape} finds a solid wall where it looks for an outer corner and
     * can never produce one &mdash; it would quietly reset an authored {@code outer_*} back to
     * {@code straight}, which is the notch this check exists to stop. Anything still sitting at
     * {@code straight} was placed without an opinion and is settled as before.</p>
     */
    private static boolean hasAuthoredShape(BlockState state) {
        Property<?> shape = state.getBlock().getStateDefinition().getProperty("shape");
        if (shape == null) {
            return false;
        }
        Comparable<?> value = state.getValue(shape);
        String name = value instanceof StringRepresentable named
                ? named.getSerializedName() : String.valueOf(value);
        return !"straight".equalsIgnoreCase(name);
    }

    /**
     * Reconciles corner shapes after the whole piece is written.
     *
     * <h2>Why this is not computed when the block is planned</h2>
     * <p>A stairs-like block's {@code shape} (straight / inner_* / outer_*) is not a property of the
     * block on its own &mdash; it is a function of its neighbours, which vanilla normally derives
     * when a player places one. Writing block states straight into the world during worldgen skips
     * that entirely, so an authored cornice ring comes out {@code straight} at every cell and its
     * four corners render as notches instead of mitred joins.</p>
     *
     * <p>{@link Block#updateFromNeighbourShapes} is vanilla's own derivation, so using it here means
     * the corner rule is never reimplemented (and never subtly wrong) on this side &mdash; and it
     * works for any mod block that follows the same contract, not just vanilla stairs.</p>
     *
     * <p>Runs as a second pass because a cell's neighbours must already exist: a mitre needs both
     * arms of the corner placed. Positions are pre-filtered to the chunk box, so this only reads
     * around cells this chunk owns.</p>
     *
     * <h2>Why a block's own updateShape is not trusted to survive worldgen</h2>
     * <p>"Follows the same contract" is an assumption about someone else's code, and a block that
     * breaks it takes the whole chunk with it: {@code updateShape} receives a {@link LevelAccessor},
     * which during worldgen is a {@code WorldGenRegion} and <strong>not</strong> a {@code Level}. A
     * block that casts it to {@code Level} (dungeonblocks' {@code FacadeShapeBlock} did, 3.0.0)
     * throws {@code ClassCastException} from inside vanilla's own derivation, and the resulting
     * "Feature placement" ReportedException kills chunk generation outright.</p>
     *
     * <p>Cosmetics are not worth a dead chunk, so a throwing block is skipped and keeps the shape it
     * was authored with &mdash; a notch instead of a mitre. Logged once per block so the offending
     * block is named without spamming a line per corner per chunk.</p>
     */
    private void settleJoinShapes(WorldGenLevel level, BoundingBox box, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockState current = level.getBlockState(pos);
            if (!hasJoinShape(current)) {
                continue; // the decoration pass may have weathered it into something else
            }
            if (hasAuthoredShape(current)) {
                continue; // the generator already knew the answer -- see hasAuthoredShape
            }
            BlockState settled;
            try {
                settled = Block.updateFromNeighbourShapes(current, level, pos);
            } catch (RuntimeException brokenContract) {
                warnUnsettleableOnce(current, brokenContract);
                continue;
            }
            if (settled != current) {
                level.setBlock(pos, settled, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Block ids already reported by {@link #warnUnsettleableOnce}; worldgen runs on many threads. */
    private static final Set<String> UNSETTLEABLE_BLOCKS = ConcurrentHashMap.newKeySet();

    private static void warnUnsettleableOnce(BlockState state, RuntimeException cause) {
        String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
        if (UNSETTLEABLE_BLOCKS.add(id)) {
            Dungeons.LOGGER.warn("{} threw from updateShape during worldgen, so its corner shapes are "
                    + "left as authored. The block's updateShape must accept a LevelAccessor "
                    + "(WorldGenRegion), not assume a Level: {}", id, cause.toString());
        }
    }

    /**
     * Spawns entity placements, <strong>clipped to the chunk box</strong>.
     *
     * <h2>The clip is the whole point</h2>
     * <p>A piece's {@code postProcess} is invoked once for every chunk its bounding box overlaps,
     * and the placement plan is identical on each of those runs by construction (see
     * {@code DungeonPiece#deterministicRandom}). For blocks that is harmless &mdash; each run
     * rewrites the same states, and the ones outside the current chunk are skipped downstream. For
     * entities it is not: {@code addFreshEntity} has no such idempotence, so an unclipped spawn
     * would add one copy of every entity per overlapping chunk and a room spanning four chunks
     * would end up with four pots stacked in each spot.</p>
     *
     * <p>Testing {@code box.isInside} on the entity's <em>cell</em> gives each placement exactly one
     * owning chunk, because chunk boxes partition the world by whole blocks. Deriving the test from
     * the fractional entity position instead would put a cell-centre coordinate on a boundary and
     * make ownership depend on rounding. This mirrors the same guard {@link #placeAll} already
     * applies to block-entity placements.</p>
     *
     * <p>Failures are logged and skipped rather than thrown: an unresolvable entity id is a
     * datapack problem, and losing a decorative pot is not worth aborting a dungeon over. Same
     * degrade-don't-abort convention an unresolved block id gets.</p>
     */
    protected void placeEntities(WorldGenLevel level, BoundingBox box, List<EntityPlacement> placements) {
        for (EntityPlacement placement : placements) {
            int worldX = anchorX + placement.getX();
            int worldY = placement.getY();
            int worldZ = anchorZ + placement.getZ();

            if (!box.isInside(new BlockPos(worldX, worldY, worldZ))) {
                continue;
            }
            try {
                EntitySpawner.spawn(level, placement, worldX, worldY, worldZ);
            } catch (Exception e) {
                Dungeons.LOGGER.warn("Failed to spawn {} at ({},{},{}): {}",
                        placement.getEntityId(), worldX, worldY, worldZ, e.getMessage());
            }
        }
    }

    /**
     * Best-effort block-entity application. Builds a full BE tag (id + position +
     * stringified fields) and loads it into the placed block entity. No Phase 2
     * builder populates {@link BlockEntityData} yet, so this path is dormant; it
     * is wired now so spawner / chest content lights up the moment a builder
     * starts emitting it, without another piece change.
     */
    protected void applyBlockEntity(WorldGenLevel level, BlockPos pos, BlockEntityData data) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        try {
            CompoundTag tag = blockEntity.saveWithFullMetadata();
            if (data.getType() != null) {
                tag.putString("id", data.getType());
            }
            for (Map.Entry<String, String> entry : data.getData().entrySet()) {
                putParsed(tag, entry.getKey(), entry.getValue());
            }
            blockEntity.load(tag);
            blockEntity.setChanged();
        } catch (Exception e) {
            Dungeons.LOGGER.warn("Failed to apply block-entity data {} at {}: {}",
                    data, pos, e.getMessage());
        }
    }

    /**
     * Builds placements (via {@code placementsSupplier}, which runs the Phase 2
     * builder) and writes them, logging and rethrowing any exception through our
     * own logger first. Vanilla's command dispatcher (e.g. {@code /place
     * structure}) swallows an exception thrown mid-command with just a generic
     * chat message and nothing in the logs, so without this, a bug in a builder
     * (wall/floor/ceiling generator, etc.) is nearly impossible to diagnose from
     * a live game session.
     */
    protected void safePlaceAll(WorldGenLevel level, BoundingBox box, Supplier<List<BlockPlacement>> placementsSupplier) {
        try {
            placeAll(level, box, placementsSupplier.get());
        } catch (RuntimeException e) {
            Dungeons.LOGGER.error("{} postProcess threw at anchor=({},{}) floorY={} box={}",
                    getClass().getSimpleName(), anchorX, anchorZ, floorY, box, e);
            throw e;
        }
    }

    /**
     * A {@link RandomSource} seeded purely from <em>chunk-independent</em> piece state
     * (world anchor XZ + floor Y + a per-piece {@code discriminator}). Procedural
     * pieces must render from this, <strong>not</strong> the {@code RandomSource}
     * {@code postProcess} is handed: that one is seeded per chunk, so a piece that
     * straddles a chunk border would draw different block-set variants on each side
     * (visible seam) when a pattern has more than one block set. Seeding from stable
     * state makes the full placement list byte-identical across every per-chunk re-run,
     * so clipping just shows different windows of the same result.
     *
     * @param discriminator a stable, chunk-independent id for this piece (e.g. a room
     *                      or corridor id, or a door's packed XZ) so sibling pieces on
     *                      the same floor still roll independent materials.
     */
    protected RandomSource deterministicRandom(long discriminator) {
        long h = mix(0x243F6A8885A308D3L ^ ((long) anchorX * 0x9E3779B97F4A7C15L));
        h = mix(h ^ ((long) floorY * 0xC2B2AE3D27D4EB4FL));
        h = mix(h ^ ((long) anchorZ * 0x165667B19E3779F9L));
        h = mix(h ^ (discriminator * 0xD1B54A32D192ED03L));
        return RandomSource.create(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Stores a value as an int when it parses cleanly, otherwise as a string. */
    private static void putParsed(CompoundTag tag, String key, String value) {
        try {
            tag.putInt(key, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            tag.putString(key, value);
        }
    }
}
