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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.config.MiningConfig;
import mod.gottsch.forge.dungeons2.core.config.MiningConfigHelper;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.mining.MiningChestPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.mining.MiningHaul;
import mod.gottsch.forge.dungeons2.diagnostic.FakeWorldGenLevel;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.dungeons2.diagnostic.TestRegistries;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Mining Chest's path from the plan to a block in the world &mdash; backlog #7.
 *
 * <p>{@code MiningChestPlannerTest} covers what the plan says; this covers everything downstream of
 * it: the emitter handing it to one piece, that piece carrying it through the NBT round trip, the
 * room generator turning it into a placement, and {@code postProcess} turning that into a block.
 * Four hand-offs, each of which would fail silently &mdash; a dungeon with no Mining Chest looks
 * exactly like a dungeon whose config paid back nothing.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
class MiningChestPlacementTest {

    private static final int ANCHOR_X = 0;
    private static final int ANCHOR_Z = 0;
    private static final String MOTIF = "classic";
    private static final int SEEDS = 8;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MiningConfig config() {
        return MiningConfigHelper.get(TestRegistries.get());
    }

    private static DungeonLayout plan(long seed) {
        return new DungeonStackPlanner(seed, new Coords(ANCHOR_X, 0, ANCHOR_Z), 72, MOTIF,
                new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withCorridorWidth(3)
                .withCorridorStyles(DungeonStructure.corridorStyleWeights(
                        MotifConfigs.load(MOTIF).corridor()))
                .plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
    }

    /** The room pieces the emitter produces for this layout, with the Mining Chest handed out. */
    private static List<DungeonRoomPiece> roomPieces(DungeonLayout layout,
                                                     MiningChestPlanner.MiningChestPlan plan) {
        List<DungeonRoomPiece> rooms = new ArrayList<>();
        for (StructurePiece piece : DungeonPieceEmitter.emitTerrain(layout, ANCHOR_X, ANCHOR_Z, 5, plan)) {
            if (piece instanceof DungeonRoomPiece room) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    /**
     * <strong>Exactly one room piece in the dungeon carries the haul.</strong>
     *
     * <p>The failure this guards is not "none" &mdash; that would be noticed. It is "all of them":
     * the match is on floor index AND room id, and room ids restart at each floor
     * ({@code MazeLevelGenerator2D} resets its generator per floor), so dropping the floor half of
     * the key would put a Mining Chest on every floor of the dungeon, each holding the whole
     * dungeon's haul.</p>
     */
    @Test
    void exactlyOneRoomPieceCarriesTheHaul() {
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonLayout layout = plan(seed);
            Optional<MiningChestPlanner.MiningChestPlan> plan =
                    MiningChestPlanner.plan(layout, config());
            if (plan.isEmpty()) {
                continue;
            }
            long carrying = roomPieces(layout, plan.get()).stream()
                    .filter(room -> room.getMiningHaul() != null)
                    .count();
            assertEquals(1L, carrying,
                    "seed " + seed + ": " + carrying + " room pieces carry the Mining Chest");
        }
    }

    /** No plan, no chest: every piece comes back empty-handed rather than defaulting to something. */
    @Test
    void withoutAPlanNoRoomCarriesAnything() {
        DungeonLayout layout = plan(0L);
        assertTrue(roomPieces(layout, null).stream().allMatch(room -> room.getMiningHaul() == null),
                "a room piece was handed a Mining Chest with no plan to hand it");
    }

    /**
     * The carrying room emits a chest, and the chest carries the haul's own SNBT.
     *
     * <p>Asserted on the placement rather than on the world because this is where the contents live:
     * {@code FakeWorldGenLevel} has no block entities, so the {@code Items} tag has nowhere to land
     * downstream of here. What the world can confirm is the block, which the test below does.</p>
     */
    @Test
    void theCarryingRoomEmitsAChestHoldingTheHaul() {
        int checked = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonLayout layout = plan(seed);
            Optional<MiningChestPlanner.MiningChestPlan> plan =
                    MiningChestPlanner.plan(layout, config());
            if (plan.isEmpty()) {
                continue;
            }
            DungeonRoomPiece carrier = carrier(layout, plan.get());
            BlockPlacement chest = miningChestPlacement(carrier);
            assertNotNull(chest, "seed " + seed + " carried a haul but emitted no Mining Chest");
            checked++;

            assertEquals("minecraft:chest", chest.getBlockId());
            assertEquals(plan.get().haul().itemsSnbt(),
                    chest.getBlockEntityNbt().getNbtValues().get("Items"),
                    "the chest's Items tag is not this dungeon's haul");
            assertTrue(chest.getProperties().containsKey("facing"),
                    "the Mining Chest has no facing, so it would render pointing north into a wall");
        }
        assertTrue(checked > 0, "no seed produced a Mining Chest, so this test proved nothing");
    }

    /**
     * <strong>The chest block actually reaches the world.</strong>
     *
     * <p>The last hand-off, and the one with the most between the intent and the result: the
     * placement goes through {@code safePlaceAll}, the weathering processor list and the decoration
     * sweep before anything is written. A chest is not in any weathering rule, so it should arrive
     * untouched &mdash; and "should" is exactly the kind of claim this harness exists to check
     * rather than assume.</p>
     */
    @Test
    void theChestBlockLandsInTheWorld() {
        int checked = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            DungeonLayout layout = plan(seed);
            Optional<MiningChestPlanner.MiningChestPlan> plan =
                    MiningChestPlanner.plan(layout, config());
            if (plan.isEmpty()) {
                continue;
            }
            DungeonRoomPiece carrier = carrier(layout, plan.get());
            BlockPlacement intended = miningChestPlacement(carrier);
            assertNotNull(intended);

            FakeWorldGenLevel level = FakeWorldGenLevel.create();
            postProcessPerChunk(carrier, level, seed);

            BlockPos pos = new BlockPos(ANCHOR_X + intended.getX(), intended.getY(),
                    ANCHOR_Z + intended.getZ());
            assertTrue(level.blockAt(pos).getBlock() instanceof ChestBlock,
                    "seed " + seed + ": expected the Mining Chest at " + pos.toShortString()
                            + " but the world holds " + level.blockAt(pos));
            checked++;
        }
        assertTrue(checked > 0, "no seed produced a Mining Chest, so this test proved nothing");
    }

    /**
     * The haul survives being written to the save and read back.
     *
     * <p>It has to be carried rather than recomputed: everything else a room piece renders from is a
     * pure function of its {@code RoomData}, but the haul is a function of the whole layout, and a
     * piece loaded from disk has never seen one. Get this wrong and the chest is empty on every world
     * after the first load.</p>
     */
    @Test
    void theHaulSurvivesTheNbtRoundTrip() {
        DungeonLayout layout = plan(0L);
        MiningChestPlanner.MiningChestPlan plan =
                MiningChestPlanner.plan(layout, config()).orElseThrow();
        DungeonRoomPiece original = carrier(layout, plan);

        DungeonRoomPiece loaded = new DungeonRoomPiece(null, save(original));
        assertNotNull(loaded.getMiningHaul(), "the haul did not survive the round trip");
        assertEquals(original.getMiningHaul().stacks(), loaded.getMiningHaul().stacks());
        assertEquals(original.getMiningHaul().itemsSnbt(), loaded.getMiningHaul().itemsSnbt());
    }

    /** A room piece with no haul writes no key at all, rather than an empty compound. */
    @Test
    void aRoomWithNoHaulWritesNoKey() {
        DungeonLayout layout = plan(0L);
        DungeonRoomPiece plain = roomPieces(layout, null).get(0);
        assertTrue(!save(plain).contains("MiningHaul"),
                "every room piece in every dungeon would carry an empty MiningHaul compound");
    }

    // -------- helpers --------

    private static DungeonRoomPiece carrier(DungeonLayout layout,
                                            MiningChestPlanner.MiningChestPlan plan) {
        return roomPieces(layout, plan).stream()
                .filter(room -> room.getMiningHaul() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no room piece carried the planned haul"));
    }

    /**
     * The Mining Chest among the room's placements, found by the {@code Items} tag.
     *
     * <p>Not by block id: a scheme's own {@code chests} slot places {@code minecraft:chest} too, and
     * the whole difference between the two is that one carries contents and the other a loot
     * table.</p>
     */
    private static BlockPlacement miningChestPlacement(DungeonRoomPiece piece) {
        // forFloor, exactly as postProcess does. Without it this reads the UNPROJECTED motif, and
        // on a floor carrying a stratum (#45) that is a different motif config -- a different
        // scheme roll, and therefore a chest in a different cell. Cost the first run of this test:
        // the position asserted below was a cell the world had put a cobweb in, because the world
        // had built a different room.
        MotifConfig motif = MotifConfigs.load(MOTIF).forFloor(piece.getFloorIndex());
        for (BlockPlacement placement : piece.renderRoom(motif, 5).getBlocks()) {
            if (placement.getBlockEntityNbt() != null
                    && placement.getBlockEntityNbt().getNbtValues().containsKey("Items")) {
                return placement;
            }
        }
        return null;
    }

    private static CompoundTag save(DungeonPiece piece) {
        CompoundTag tag = new CompoundTag();
        BoundingBox.CODEC.encodeStart(NbtOps.INSTANCE, piece.getBoundingBox())
                .resultOrPartial(err -> {})
                .ifPresent(t -> tag.put("BB", t));
        Direction orientation = piece.getOrientation();
        tag.putInt("O", orientation == null ? -1 : orientation.get2DDataValue());
        tag.putInt("GD", piece.getGenDepth());
        piece.addAdditionalSaveData(null, tag);
        return tag;
    }

    /** Mirrors {@code CorridorPostProcessTest.postProcessPerChunk}; see that method's note. */
    private static void postProcessPerChunk(DungeonRoomPiece piece, FakeWorldGenLevel level, long seed) {
        BoundingBox pieceBox = piece.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(pieceBox.minX()),
                SectionPos.blockToSectionCoord(pieceBox.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(pieceBox.maxX()),
                SectionPos.blockToSectionCoord(pieceBox.maxZ()));
        BlockPos origin = new BlockPos(pieceBox.getCenter().getX(), pieceBox.minY(),
                pieceBox.getCenter().getZ());

        ChunkPos.rangeClosed(min, max).forEach(chunkPos -> {
            BoundingBox chunkBox = new BoundingBox(
                    chunkPos.getMinBlockX(), -64, chunkPos.getMinBlockZ(),
                    chunkPos.getMaxBlockX(), 320, chunkPos.getMaxBlockZ());
            if (!pieceBox.intersects(chunkBox)) {
                return;
            }
            piece.postProcess(level.level(), null, null, RandomSource.create(seed),
                    chunkBox, chunkPos, origin);
        });
    }
}
