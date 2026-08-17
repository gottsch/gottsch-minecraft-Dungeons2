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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingProcessor;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.BlockMatch;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.DecorationProcessor;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.DecorationRule;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.WallGrowthRule;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wiring test: does a real procedurally-built room actually come out of
 * {@link DecorationProcessor} with cobwebs and lichen on it?
 *
 * <p>{@link DecorationProcessorTest} covers the processor's rules against hand-built
 * block lists. That proves the logic but not that Dungeons2's own geometry satisfies it
 * &mdash; wall growth and cobwebs both write into <strong>air the piece itself places</strong>,
 * so a builder that left its interior as "no block" would decorate to nothing while every
 * unit test still passed. This runs the real {@link BasicRoomGenerator} output through the
 * real shipped probabilities instead.</p>
 *
 * <p>What it does <em>not</em> cover: {@code PieceProcessors.decorate}'s two-pass split and
 * the datapack lookup, both of which need a {@code WorldGenLevel}.</p>
 *
 * @author Mark Gottschling on Jul 28, 2026
 */
class DecorationOnRealRoomTest {

    /** Every piece here is built on the entrance floor; depth is not what these cases are about. */
    private static final int TEST_FLOOR_INDEX = 0;

    /** No processor is ever serialized here, so the type is never asked for. */
    private static final java.util.function.Supplier<
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType<?>> NO_TYPE = () -> null;

    /** The values shipped in {@code classic_weathering.json}. */
    private static final float COBWEBS = 0.02F;
    private static final float WALL_GROWTH = 0.04F;
    private static final float WALL_GROWTH_BONUS = 0.22F;
    private static final float WALL_GROWTH_MAX = 0.55F;

    private static final int FLOOR_Y = 40;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A room as {@code DungeonRoomPiece} would build it, converted the way
     * {@code DungeonPiece.placeAll} converts it: origin-relative XZ, world Y, no clipping.
     */
    private static List<StructureTemplate.StructureBlockInfo> buildRoom(int width, int depth, int height) {
        RoomData room = new RoomData(1, 0, 0, width, depth, height, RoomRole.NORMAL);
        RoomPlacements roomPlacements = new RoomPlacements();
        new BasicRoomGenerator().build(room, FLOOR_Y, TEST_FLOOR_INDEX, DungeonMotif.CLASSIC,
                RandomSource.create(1234L), roomPlacements);
        List<BlockPlacement> placements = roomPlacements.getBlocks();

        List<StructureTemplate.StructureBlockInfo> infos = new ArrayList<>(placements.size());
        for (BlockPlacement placement : placements) {
            infos.add(new StructureTemplate.StructureBlockInfo(
                    new BlockPos(placement.getX(), placement.getY(), placement.getZ()),
                    BlockStateCodec.resolve(placement), null));
        }
        return infos;
    }

    @Test
    void aRealRoomComesOutDecorated() {
        // Deliberately the SHIPPED probabilities, not certainties: this is also a check
        // that 0.02 / 0.04+0.22 are not so low that a room-sized piece gets nothing.
        DecorationProcessor processor = new DecorationProcessor(NO_TYPE, 
                new DecorationRule(COBWEBS, List.of(Blocks.COBWEB)),
                new WallGrowthRule(WALL_GROWTH, WALL_GROWTH_BONUS, WALL_GROWTH_MAX,
                        List.of(Blocks.GLOW_LICHEN)),
                BlockMatch.NONE, DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE,
                DecorationRule.NONE, BlockMatch.NONE);

        List<StructureTemplate.StructureBlockInfo> room = buildRoom(15, 15, 6);
        List<StructureTemplate.StructureBlockInfo> decorated = processor.finalizeProcessing(
                null, BlockPos.ZERO, BlockPos.ZERO, room, room, new StructurePlaceSettings());

        long webs = count(decorated, Blocks.COBWEB.defaultBlockState().getBlock());
        long lichen = count(decorated, Blocks.GLOW_LICHEN);

        // For reference: at the time of writing this room yields 5 cobwebs and 13 lichen
        // out of 1254 blocks. Asserted as ">0" rather than exactly, since the shipped
        // probabilities are meant to be tuned without breaking a test.
        assertTrue(webs > 0, "A 15x15x6 room produced no cobwebs at the shipped rate");
        assertTrue(lichen > 0, "A 15x15x6 room produced no wall growth at the shipped rate");

        // Sanity the other way: decoration must not eat the room. Every replacement lands
        // in a cell the builder placed as air, so the solid shell is untouched.
        assertTrue(webs + lichen < decorated.size() / 10,
                "Decoration covered more than a tenth of the room (" + (webs + lichen)
                        + " of " + decorated.size() + ") -- the rates are not what they look like");
    }

    @Test
    void growthAttachesToRoomWallsNotToOpenFloorSpace() {
        // The reason it works at all: a room's interior air ring touches the wall ring, so
        // the growth candidates are exactly the cells beside a wall. If a builder ever
        // stopped emitting interior air this would go to zero and say so.
        DecorationProcessor certain = new DecorationProcessor(NO_TYPE, 
                DecorationRule.NONE,
                new WallGrowthRule(1.0F, 0.0F, 1.0F, List.of(Blocks.GLOW_LICHEN)),
                BlockMatch.NONE, DecorationRule.NONE, DecorationRule.NONE, DecorationRule.NONE,
                DecorationRule.NONE, BlockMatch.NONE);

        int width = 11;
        int depth = 11;
        List<StructureTemplate.StructureBlockInfo> room = buildRoom(width, depth, 6);
        List<StructureTemplate.StructureBlockInfo> decorated = certain.finalizeProcessing(
                null, BlockPos.ZERO, BlockPos.ZERO, room, room, new StructurePlaceSettings());

        long lichen = count(decorated, Blocks.GLOW_LICHEN);
        assertTrue(lichen > 0, "No growth at all -- the room has no air adjacent to its walls");

        // Every lichen must be on the interior ring next to the wall, never further in.
        boolean allOnTheWall = decorated.stream()
                .filter(info -> info.state().is(Blocks.GLOW_LICHEN))
                .allMatch(info -> {
                    int x = info.pos().getX();
                    int z = info.pos().getZ();
                    return x == 1 || z == 1 || x == width - 2 || z == depth - 2;
                });
        assertTrue(allOnTheWall, "Growth appeared away from the walls");
    }

    private static long count(List<StructureTemplate.StructureBlockInfo> blocks,
                              net.minecraft.world.level.block.Block block) {
        return blocks.stream().filter(info -> info.state().is(block)).count();
    }

    /**
     * The shipped aging rules for {@code minecraft:stone_bricks}, decoded from the real file.
     *
     * <p>Only that source block, and deliberately so: the file's other chains name
     * {@code dungeonblocks:*} blocks, and an unregistered id decodes to {@code minecraft:air}
     * under a bare bootstrap (backlog #13). Feeding those through would produce nonsense —
     * rules that match air and replace it. Stone bricks is all-vanilla, and is what
     * {@code BasicWallGenerator} builds with anyway.</p>
     */
    private static AgingProcessor shippedStoneBrickAging() {
        try (InputStream in = DecorationOnRealRoomTest.class.getResourceAsStream(
                "/data/dungeons2/worldgen/processor_list/classic_weathering.json")) {
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            JsonObject aging = null;
            for (var element : root.getAsJsonArray("processors")) {
                JsonObject processor = element.getAsJsonObject();
                if ("dungeons2:aging".equals(processor.get("processor_type").getAsString())) {
                    aging = processor;
                }
            }
            JsonArray stoneBrickRules = new JsonArray();
            for (var element : aging.getAsJsonArray("rules")) {
                if ("minecraft:stone_bricks".equals(
                        element.getAsJsonObject().get("block").getAsString())) {
                    stoneBrickRules.add(element);
                }
            }
            JsonObject filtered = new JsonObject();
            filtered.add("agings", aging.get("agings"));
            filtered.add("rules", stoneBrickRules);

            return AgingProcessor.codec(NO_TYPE).parse(JsonOps.INSTANCE, filtered)
                    .getOrThrow(false, msg -> {
                        throw new AssertionError("aging rules failed to decode: " + msg);
                    });
        } catch (Exception e) {
            throw new AssertionError("Could not build the shipped aging processor", e);
        }
    }

    @Test
    void agingAndDecorationTogetherOvergrowARealRoom() {
        // The end-to-end question the unit tests can't answer on their own: given the SHIPPED
        // numbers on a REAL room, does anything actually grow?
        //
        // It's a fair question because the two halves are useless apart. Classic authors no
        // dirt and no holes, so decoration's dirt behaviours have nothing to act on until
        // aging's deep-decay chains produce some -- which is exactly why floor growth was
        // invisible in game before those chains existed.
        //
        // One processBlockInfos call with both processors, which is how PieceProcessors' first
        // pass runs them: every processBlock first, then each finalizeProcessing.
        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.addProcessor(shippedStoneBrickAging());
        settings.addProcessor(new DecorationProcessor(NO_TYPE, 
                new DecorationRule(COBWEBS, List.of(Blocks.COBWEB)),
                new WallGrowthRule(WALL_GROWTH, WALL_GROWTH_BONUS, WALL_GROWTH_MAX,
                        List.of(Blocks.GLOW_LICHEN)),
                new BlockMatch(List.of(Blocks.DIRT), List.of()),
                new DecorationRule(0.35F, List.of(Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM)),
                new DecorationRule(0.3F, List.of(Blocks.HANGING_ROOTS)),
                DecorationRule.NONE, DecorationRule.NONE, BlockMatch.NONE));

        // A big room on purpose. At the shipped rates a 15x15 gives only a handful of dirt
        // blocks, and only those in the floor or ceiling have the air above/below they need,
        // so a small sample can legitimately come out at zero and say nothing useful.
        List<StructureTemplate.StructureBlockInfo> room = buildRoom(31, 31, 6);
        List<StructureTemplate.StructureBlockInfo> out = StructureTemplate.processBlockInfos(
                null, BlockPos.ZERO, BlockPos.ZERO, settings, room, null);

        long dirt = count(out, Blocks.DIRT);
        long mushrooms = count(out, Blocks.BROWN_MUSHROOM) + count(out, Blocks.RED_MUSHROOM);
        long roots = count(out, Blocks.HANGING_ROOTS);

        assertTrue(dirt > 0, "The deep-decay chain produced no dirt in a whole room");
        assertTrue(mushrooms + roots > 0,
                "Aging made " + dirt + " dirt blocks but nothing grew on any of them -- the"
                        + " aging-before-decoration ordering is what makes this work");

        // For reference, this room at the time of writing: 41 dirt, 323 cobblestone,
        // 6 mushrooms, 2 hanging roots -- and zero gravel, which is the point of the chain
        // ending in dirt rather than rubble (see classic_weathering.json).
        //
        // Note how few of the 41 dirt blocks grow anything. Dirt in a WALL is sandwiched
        // between other wall blocks, so it has no air above or below and is a dead end by
        // construction -- only floor dirt (air above) and ceiling dirt (air below) can
        // sprout. Budget for roughly half the dirt being inert when tuning the rates.
    }
}
