package mod.gottsch.forge.dungeons2.core.map;

import mod.gottsch.forge.dungeonblocks.core.block.HiddenDoorBlock;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The Dungeon Map's drawing, on a small built floor: it fills the room the holder stands in, draws
 * its walls and its door, goes no further than the door, never through a wall, and shows a hidden
 * door as wall.
 *
 * <pre>
 *   z\x 0 1 2 3 4 5 6 7 8 9 ...15
 *   0   W W W W W W W W W W W W W W W W    floor everywhere at y 0; walls at y 1-2
 *   1   W . . . . . . . W . . . . . . W    the second room (x 9-14) has no way in
 *   2   W . . . . . . . W . . . . . . W
 *   3   W . . . . . . . W W W W W W W W
 *   4   W . . . @ . . . D . . . . . . .    D: an oak door; the corridor beyond runs east
 *   5   W . . . . . . . W W W W W W W W
 *   6   W . . . . . . . H                  H: a hidden door, shut
 *   7   W . . . . . . . W
 *   8   W W W W W W W W W
 * </pre>
 *
 * @author Mark Gottschling on 10/25/2026
 */
class DungeonMapScanTest {

    private static Block hiddenDoor;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ((MappedRegistry<Block>) BuiltInRegistries.BLOCK).unfreeze();
        hiddenDoor = new HiddenDoorBlock(BlockBehaviour.Properties.copy(Blocks.STONE_BRICKS));
    }

    @Test
    void itDrawsTheRoomItsHolderIsIn() {
        Map<Long, Byte> drawn = scan(world(), new BlockPos(4, 1, 4));

        assertEquals(DungeonMaps.FLOOR, colour(drawn, 4, 4), "where the holder stands");
        assertEquals(DungeonMaps.FLOOR, colour(drawn, 1, 1), "a corner of the room");
        assertEquals(DungeonMaps.WALL, colour(drawn, 0, 4), "the west wall");
        assertEquals(DungeonMaps.DOOR, colour(drawn, 8, 4), "the door out");
        assertEquals(DungeonMaps.WALL, colour(drawn, 8, 6), "a hidden door is wall");
        assertFalse(drawn.containsKey(key(10, 4)), "nothing past a door");
        assertFalse(drawn.containsKey(key(10, 1)), "nothing through a wall");
    }

    @Test
    void theStairsDownAreRed() {
        FloorMapStamp stamp = new FloorMapStamp(0, 0, 0, 0, 15, 8, new int[]{6, 6, 7, 7});
        Map<Long, Byte> drawn = new HashMap<>();
        DungeonMaps.scan(world(), stamp, new BlockPos(4, 1, 4), (x, z, colour) -> drawn.put(key(x, z), colour));
        assertEquals(DungeonMaps.STAIRS, colour(drawn, 7, 7));
        assertEquals(DungeonMaps.FLOOR, colour(drawn, 5, 5));
    }

    private static Map<Long, Byte> scan(BlockGetter world, BlockPos feet) {
        FloorMapStamp stamp = new FloorMapStamp(0, 0, 0, 0, 15, 8, null);
        Map<Long, Byte> drawn = new HashMap<>();
        DungeonMaps.scan(world, stamp, feet, (x, z, colour) -> drawn.put(key(x, z), colour));
        return drawn;
    }

    private static Byte colour(Map<Long, Byte> drawn, int x, int z) {
        return drawn.get(key(x, z));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static BlockGetter world() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x <= 15; x++) {
            for (int z = 0; z <= 8; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
        String[] plan = {
                "WWWWWWWWWWWWWWWW",
                "W.......W......W",
                "W.......W......W",
                "W.......WWWWWWWW",
                "W.......D.......",
                "W.......WWWWWWWW",
                "W.......H",
                "W.......W",
                "WWWWWWWWW"};
        for (int z = 0; z < plan.length; z++) {
            for (int x = 0; x < plan[z].length(); x++) {
                char c = plan[z].charAt(x);
                if (c == 'W') {
                    blocks.put(new BlockPos(x, 1, z), Blocks.STONE_BRICKS.defaultBlockState());
                    blocks.put(new BlockPos(x, 2, z), Blocks.STONE_BRICKS.defaultBlockState());
                } else if (c == 'D' || c == 'H') {
                    Block door = c == 'D' ? Blocks.OAK_DOOR : hiddenDoor;
                    blocks.put(new BlockPos(x, 1, z), door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    blocks.put(new BlockPos(x, 2, z), door.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                }
            }
        }
        return new BlockGetter() {
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return getBlockState(pos).getFluidState();
            }

            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinBuildHeight() {
                return -64;
            }
        };
    }
}
