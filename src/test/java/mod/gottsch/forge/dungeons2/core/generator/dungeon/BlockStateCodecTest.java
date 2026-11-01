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
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Phase 2 {@link BlockStateCodec} round-trips
 * {@code BlockState <-> BlockPlacement} faithfully &mdash; this is what
 * Phase 3 piece renderers will rely on to recover the right
 * {@code BlockState} from saved NBT.
 */
class BlockStateCodecTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void plainBlockRoundTrips() {
        BlockState original = Blocks.STONE_BRICKS.defaultBlockState();
        BlockPlacement bp = BlockStateCodec.placement(1, 2, 3, original);
        assertEquals("minecraft:stone_bricks", bp.getBlockId());
        assertTrue(bp.getProperties().isEmpty(),
                "Default state should have no non-default properties");

        BlockState restored = BlockStateCodec.resolve(bp);
        assertEquals(original, restored, "Round-trip should preserve BlockState");
    }

    @Test
    void airRoundTrips() {
        BlockPlacement bp = BlockStateCodec.placement(0, 0, 0, Blocks.AIR.defaultBlockState());
        assertEquals("minecraft:air", bp.getBlockId());
        BlockState restored = BlockStateCodec.resolve(bp);
        assertEquals(Blocks.AIR.defaultBlockState(), restored);
    }

    @Test
    void blockWithCustomPropertiesPreservesAllNonDefaults() {
        BlockState doorState = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

        BlockPlacement bp = BlockStateCodec.placement(10, 64, 10, doorState);
        assertEquals("minecraft:oak_door", bp.getBlockId());
        // FACING default is NORTH for OAK_DOOR (matches our setValue), so it
        // may NOT appear in non-default props. HALF default is LOWER, so UPPER
        // should appear.
        assertEquals("upper", bp.getProperties().get("half"));

        BlockState restored = BlockStateCodec.resolve(bp);
        assertEquals(DoubleBlockHalf.UPPER, restored.getValue(DoorBlock.HALF));
        assertEquals(Direction.NORTH, restored.getValue(DoorBlock.FACING));
    }

    @Test
    void unknownBlockIdResolvesToAir() {
        BlockPlacement bp = new BlockPlacement(0, 0, 0, "minecraft:this_block_does_not_exist");
        BlockState resolved = BlockStateCodec.resolve(bp);
        // Either returns AIR's default state or the queried block's default; both safe.
        assertEquals(Blocks.AIR.defaultBlockState(), resolved,
                "Unknown block id should resolve to air");
    }

    @Test
    void unknownPropertyOnKnownBlockIsSilentlyDropped() {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockPlacement bp = BlockStateCodec.placement(0, 0, 0, dirt);
        bp.getProperties().put("nonsense", "value");
        BlockState resolved = BlockStateCodec.resolve(bp);
        // Should still resolve to dirt's default state; unknown property silently ignored.
        assertEquals(dirt, resolved);
    }
}
