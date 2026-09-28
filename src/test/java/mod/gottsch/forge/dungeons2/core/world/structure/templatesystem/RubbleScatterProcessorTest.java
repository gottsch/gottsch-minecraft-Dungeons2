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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.BlockMatch;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code dungeons2:rubble_scatter}: a hole weathering knocked in a wall sheds onto the floor at its
 * foot, and nothing else does.
 *
 * <p>The fixture is one room: a stone floor at y 0, a wall along x = 0 from y 1 to 4, and air
 * inside (x 1..4, y 1..3). The wall's far side (x = -1) is not the piece's &mdash; terrain.</p>
 *
 * <p>DungeonBlocks is off the test classpath, so the scatter here is a vanilla carpet: the
 * processor refuses to write a block that does not resolve, and that refusal is one of the cases.</p>
 */
class RubbleScatterProcessorTest {

    // Before the constants below: a Blocks field read ahead of bootstrap throws "Not bootstrapped",
    // which only stays hidden when another test class happens to bootstrap the JVM first.
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockState STONE = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState CARPET = Blocks.MOSS_CARPET.defaultBlockState();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Map<BlockPos, BlockState> room() {
        Map<BlockPos, BlockState> room = new HashMap<>();
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                room.put(new BlockPos(x, 0, z), STONE);
                for (int y = 1; y <= 4; y++) {
                    room.put(new BlockPos(x, y, z), x == 0 || y == 4 ? STONE : AIR);
                }
            }
        }
        return room;
    }

    private static List<StructureBlockInfo> list(Map<BlockPos, BlockState> blocks) {
        List<StructureBlockInfo> list = new ArrayList<>();
        blocks.entrySet().stream()
                .sorted((a, b) -> Long.compare(a.getKey().asLong(), b.getKey().asLong()))
                .forEach(e -> list.add(new StructureBlockInfo(e.getKey(), e.getValue(), null)));
        return list;
    }

    /** Nowhere near the world origin: the original list's positions only line up with the
     *  processed list's when the piece sits at (0,0,0), which hid a real bug. */
    private static final BlockPos ORIGIN = new BlockPos(1000, 64, -2000);

    /**
     * Runs the processor through VANILLA's own processing loop, after a stub "aging" step that turns
     * each cell of {@code aged} into its aged state -- so the original list reaches
     * finalizeProcessing exactly as it does in game. Positions in and out are piece-relative.
     */
    private static Map<BlockPos, BlockState> run(Map<BlockPos, BlockState> authored,
                                                 Map<BlockPos, BlockState> aged, String scatter,
                                                 double probability) {
        StructureProcessor aging = new StructureProcessor() {
            @Override
            public StructureBlockInfo processBlock(net.minecraft.world.level.LevelReader level, BlockPos offset,
                    BlockPos pos, StructureBlockInfo original, StructureBlockInfo current,
                    StructurePlaceSettings settings) {
                BlockState state = aged.get(original.pos());
                return state == null ? current : new StructureBlockInfo(current.pos(), state, null);
            }

            @Override
            protected StructureProcessorType<?> getType() {
                return null;
            }
        };
        RubbleScatterProcessor processor = new RubbleScatterProcessor(() -> null,
                new BlockMatch(List.of(Blocks.AIR), List.of()), scatter, probability, 8);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.addProcessor(aging);
        settings.addProcessor(processor);
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (StructureBlockInfo info : StructureTemplate.processBlockInfos(null, ORIGIN, ORIGIN, settings,
                list(authored), null)) {
            out.put(info.pos().subtract(ORIGIN), info.state());
        }
        return out;
    }

    private static int scatters(Map<BlockPos, BlockState> world) {
        return (int) world.values().stream().filter(s -> s.is(Blocks.MOSS_CARPET)).count();
    }

    @Test
    void aHoleInTheWallShedsOntoTheFloorAtItsFoot() {
        Map<BlockPos, BlockState> aged = Map.of(new BlockPos(0, 2, 2), AIR);
        Map<BlockPos, BlockState> out = run(room(), aged, "minecraft:moss_carpet", 1.0);
        assertEquals(CARPET, out.get(new BlockPos(1, 1, 2)), "the foot of the hole, on the floor");
        assertEquals(1, scatters(out), "one hole, one foot: the far side is terrain");
    }

    @Test
    void aHoleHighInTheWallStillLandsOnTheFloor() {
        Map<BlockPos, BlockState> aged = Map.of(new BlockPos(0, 3, 2), AIR);
        Map<BlockPos, BlockState> out = run(room(), aged, "minecraft:moss_carpet", 1.0);
        assertEquals(CARPET, out.get(new BlockPos(1, 1, 2)));
    }

    /** A pot or chest marker emptying on the floor has room air above: that is not a broken wall. */
    @Test
    void somethingEmptiedOnTheFloorIsNotABrokenWall() {
        Map<BlockPos, BlockState> authored = room();
        authored.put(new BlockPos(2, 1, 2), STONE);
        Map<BlockPos, BlockState> aged = Map.of(new BlockPos(2, 1, 2), AIR);
        assertEquals(0, scatters(run(authored, aged, "minecraft:moss_carpet", 1.0)));
    }

    @Test
    void anUnbrokenRoomGetsNothing() {
        assertEquals(0, scatters(run(room(), Map.of(), "minecraft:moss_carpet", 1.0)));
    }

    @Test
    void probabilityZeroOrAnUnresolvedBlockWritesNothing() {
        Map<BlockPos, BlockState> aged = Map.of(new BlockPos(0, 2, 2), AIR);
        assertEquals(0, scatters(run(room(), aged, "minecraft:moss_carpet", 0.0)));
        Map<BlockPos, BlockState> expected = room();
        expected.putAll(aged);
        assertEquals(expected, run(room(), aged, "notamod:rubble", 1.0),
                "an unresolved scatter block must not touch the list");
    }

    /** Rolls are seeded from the foot cell, so every chunk pass over the piece agrees. */
    @Test
    void theSameInputAlwaysScattersTheSameWay() {
        Map<BlockPos, BlockState> aged = new HashMap<>();
        for (int z = 0; z <= 4; z++) aged.put(new BlockPos(0, 2, z), AIR);
        Map<BlockPos, BlockState> first = run(room(), aged, "minecraft:moss_carpet", 0.5);
        for (int i = 0; i < 5; i++) {
            assertEquals(first, run(room(), aged, "minecraft:moss_carpet", 0.5));
        }
        assertTrue(scatters(first) <= 5);
    }

    /** Only ever writes into the piece's own air. */
    @Test
    void itOnlyEverReplacesAir() {
        Map<BlockPos, BlockState> aged = new HashMap<>();
        for (int z = 0; z <= 4; z++) aged.put(new BlockPos(0, 2, z), AIR);
        Map<BlockPos, BlockState> out = run(room(), aged, "minecraft:moss_carpet", 1.0);
        Map<BlockPos, BlockState> before = room();
        before.putAll(aged);
        for (Map.Entry<BlockPos, BlockState> e : out.entrySet()) {
            if (e.getValue().is(Blocks.MOSS_CARPET)) {
                assertTrue(before.get(e.getKey()).isAir(), "wrote over " + before.get(e.getKey()) + " at " + e.getKey());
            }
        }
    }
}
