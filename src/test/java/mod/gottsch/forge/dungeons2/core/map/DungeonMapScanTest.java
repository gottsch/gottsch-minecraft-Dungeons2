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

    /** A crafted map knows no stairs room; a ladder down a hole is the way down all the same. */
    @Test
    void aLadderDownIsTheStairsDown() {
        Map<BlockPos, BlockState> blocks = blocks();
        blocks.remove(new BlockPos(2, 0, 2));
        blocks.put(new BlockPos(2, 0, 2), Blocks.LADDER.defaultBlockState());
        blocks.put(new BlockPos(2, -1, 2), Blocks.LADDER.defaultBlockState());
        Map<Long, Byte> drawn = scan(getter(blocks), new BlockPos(4, 1, 4));
        assertEquals(DungeonMaps.STAIRS, colour(drawn, 2, 2));
    }

    /**
     * The sewer transition's bottom: the ladder up starts down in the pool beside the ledge, its foot
     * level with the floor, and rises out of the room. It is the way up.
     */
    @Test
    void aLadderUpFromAPoolIsTheWayUp() {
        Map<BlockPos, BlockState> blocks = blocks();
        blocks.remove(new BlockPos(2, 0, 2));
        for (int y = -1; y <= 6; y++) {
            blocks.put(new BlockPos(2, y, 2), Blocks.LADDER.defaultBlockState());
        }
        Map<Long, Byte> drawn = scan(getter(blocks), new BlockPos(4, 1, 4));
        assertEquals(DungeonMaps.STAIRS_UP, colour(drawn, 2, 2));
    }

    /** A pool a block below the floor is drawn as water, not as a pit. */
    @Test
    void aPoolBelowTheFloorIsWater() {
        Map<BlockPos, BlockState> blocks = blocks();
        blocks.put(new BlockPos(2, 0, 2), Blocks.WATER.defaultBlockState());
        blocks.put(new BlockPos(2, -1, 2), Blocks.WATER.defaultBlockState());
        blocks.put(new BlockPos(2, -2, 2), Blocks.STONE.defaultBlockState());
        Map<Long, Byte> drawn = scan(getter(blocks), new BlockPos(4, 1, 4));
        assertEquals(DungeonMaps.WATER, colour(drawn, 2, 2));
    }

    /**
     * On a large dungeon's map a pixel covers 2x2 blocks: the ladder's pixel must survive the floor
     * beside it painted after, and spread onto the floor pixels round it but never a wall.
     */
    @Test
    void aWayOffTheFloorKeepsItsPixelAndSpreadsOntoFloorOnly() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("dimension", "minecraft:overworld");
        tag.putInt("xCenter", 0);
        tag.putInt("zCenter", 0);
        tag.putByte("scale", (byte) 1);
        tag.putBoolean("locked", true);
        net.minecraft.world.level.saveddata.maps.MapItemSavedData data =
                net.minecraft.world.level.saveddata.maps.MapItemSavedData.load(tag);
        // pixel (64,64) holds blocks 0..1; its neighbours hold 2..3 and -2..-1
        DungeonMaps.paint(data, 2, 0, DungeonMaps.FLOOR);
        DungeonMaps.paint(data, -2, 0, DungeonMaps.WALL);
        DungeonMaps.paint(data, 0, -2, DungeonMaps.WATER);
        DungeonMaps.paint(data, 0, 0, DungeonMaps.STAIRS_UP);
        DungeonMaps.paint(data, 1, 0, DungeonMaps.FLOOR);
        assertEquals(DungeonMaps.STAIRS_UP, data.colors[64 + 64 * 128], "the floor beside it does not win the pixel");

        DungeonMaps.halo(data, 0, 0, DungeonMaps.STAIRS_UP);
        assertEquals(DungeonMaps.STAIRS_UP, data.colors[65 + 64 * 128], "floor next to it takes the colour");
        assertEquals(DungeonMaps.STAIRS_UP, data.colors[64 + 63 * 128], "so does water: a ladder out of a pool");
        assertEquals(DungeonMaps.WALL, data.colors[63 + 64 * 128], "a wall next to it does not");
        assertEquals(0, data.colors[64 + 65 * 128], "nor does unexplored parchment");
    }

    /**
     * Steps that go on down past a corridor's deepest descent lead to the next floor: the first
     * step out of the floor's reach is drawn red, and the walk goes no further.
     */
    @Test
    void aWalkThatKeepsGoingDownIsTheStairsDown() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = 0; x <= 12; x++) {
            int y = x < 4 ? 0 : 3 - x;
            blocks.put(new BlockPos(x, y, 0), Blocks.STONE.defaultBlockState());
        }
        FloorMapStamp stamp = new FloorMapStamp(-1, 0, -60, -60, 60, 60, null);
        Map<Long, Byte> drawn = new HashMap<>();
        DungeonMaps.scan(getter(blocks), stamp, new BlockPos(1, 1, 0), (x, z, colour) -> drawn.put(key(x, z), colour));
        assertEquals(DungeonMaps.FLOOR, colour(drawn, 8, 0), "4 down is still this floor");
        assertEquals(DungeonMaps.STAIRS, colour(drawn, 9, 0), "6 down is the way to the next");
        assertFalse(drawn.containsKey(key(10, 0)), "and the walk stops there");
    }

    /**
     * Only what can be seen is drawn: along an L of corridor, the leg round the corner is in reach
     * on foot but out of sight, so it stays blank until the holder turns it.
     */
    @Test
    void roundACornerIsNotDrawn() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = -1; x <= 8; x++) {
            for (int z = -1; z <= 8; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
                boolean corridor = (z == 0 && x >= 0 && x <= 6) || (x == 6 && z >= 0 && z <= 6);
                if (!corridor) {
                    blocks.put(new BlockPos(x, 1, z), Blocks.STONE_BRICKS.defaultBlockState());
                    blocks.put(new BlockPos(x, 2, z), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        }
        Map<Long, Byte> drawn = scan(getter(blocks), new BlockPos(0, 1, 0));
        assertEquals(DungeonMaps.FLOOR, colour(drawn, 6, 0), "the corner, in sight down the corridor");
        assertEquals(DungeonMaps.WALL, colour(drawn, 3, 1), "the corridor's wall");
        assertFalse(drawn.containsKey(key(6, 4)), "the leg round the corner is out of sight");
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
        return getter(blocks());
    }

    private static Map<BlockPos, BlockState> blocks() {
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
        return blocks;
    }

    private static BlockGetter getter(Map<BlockPos, BlockState> blocks) {
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
                // a water block's own fluid state is empty off a running game: Forge fills the
                // LiquidBlock's cache lazily, and nothing does in a unit test
                BlockState state = getBlockState(pos);
                return state.is(Blocks.WATER) ? net.minecraft.world.level.material.Fluids.WATER.getSource(false)
                        : state.getFluidState();
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
