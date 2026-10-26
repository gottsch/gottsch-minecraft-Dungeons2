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
package mod.gottsch.forge.dungeons2.core.map;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import mod.gottsch.forge.dungeonblocks.core.block.HiddenDoorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Dungeon Maps: a vanilla filled map of one dungeon floor that draws itself as you explore it.
 *
 * <h2>Why a vanilla map</h2>
 * <p>It is an ordinary {@code minecraft:filled_map}, not an item of this mod's, because vanilla
 * draws a map in the hand and in an item frame only for that item. Everything that makes a map a
 * map - the drawing, syncing it to the client, the player marker, copying it in a cartography table,
 * hanging it in a frame - is vanilla's. What is this mod's is only what goes into its pixels.</p>
 *
 * <p>Its data is <strong>locked</strong>, which is what stops vanilla drawing over it: a vanilla map
 * paints the world from above, which underground is the surface. A locked map is never updated by
 * vanilla, but its holder is still marked on it, and this class writes its colours itself.</p>
 *
 * <h2>What it shows</h2>
 * <p>The floor at its own walking plane, as its holder sees it: every {@link #REVEAL_INTERVAL}
 * ticks, the floor within {@link #REVEAL_RADIUS} blocks that the holder could both walk to and see -
 * a straight line to it over open floor - is filled in. A room fills in once you are in it or look
 * into it through its doorway; nothing round a corner or behind a wall is drawn (Mark, 2026-10-26:
 * at 12 blocks with no sight test, the map drew rooms he had only walked past). It stops at a door
 * and draws the door, so an unopened door shows as a way on; it draws a hidden door as wall, so a
 * map gives no secret away. Unexplored parchment stays bare.</p>
 *
 * <p>The way off the floor is drawn where the walking leaves it: a walk that goes on down past a
 * corridor's deepest descent, or a ladder down, is the stairs down and drawn red; the same upward is
 * the way up, drawn gold. The floor's own stairs-down room is drawn red too, from its stamp.</p>
 *
 * <h2>Two ways to get one</h2>
 * <p>Found in a chest, it maps the chest's floor, read off the chest. Crafted, a Blank Dungeon Map
 * maps the floor its user stands on, looked up in the {@code DungeonFloorIndex}. Either way the map
 * is made by {@link #create} from the floor's {@link FloorMapStamp}, centred on the floor and named
 * for its level.</p>
 *
 * <p>It only draws while held, as a vanilla map does, and only on its own floor: a holder well above
 * or below the walking plane (on the stairs, or on the next floor) draws nothing.</p>
 *
 * @author Mark Gottschling on 10/25/2026
 */
public final class DungeonMaps {

    /** The stack tag a Dungeon Map carries its floor under. */
    public static final String TAG = "D2DungeonMap";

    /** How far round the holder the floor is drawn, in blocks. */
    public static final int REVEAL_RADIUS = 8;
    /** Ticks between drawings - often enough to keep up with a running player. */
    public static final int REVEAL_INTERVAL = 5;

    /**
     * How far the holder may be from the walking plane and still be on this floor: a corridor descent
     * goes at most 4 below it, and a floor's own stairs and steps a block or two above. The next
     * floor is twenty blocks away.
     */
    private static final int BELOW = 6;
    private static final int ABOVE = 4;

    /**
     * How far the walking may drop below the walking plane, or rise above it, and still be this floor:
     * a corridor descends at most 4, a room's dais or steps rise a block or two. A walk that goes
     * further is on its way to another floor.
     */
    private static final int FLOOR_DROP = 5;
    private static final int FLOOR_RISE = 3;

    // the map's palette: dressed stone, dark walls, a wooden door, red stairs down
    static final byte FLOOR = MapColor.STONE.getPackedId(MapColor.Brightness.HIGH);
    static final byte WALL = MapColor.DEEPSLATE.getPackedId(MapColor.Brightness.LOW);
    static final byte DOOR = MapColor.WOOD.getPackedId(MapColor.Brightness.HIGH);
    static final byte STAIRS = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.HIGH);
    static final byte STAIRS_UP = MapColor.GOLD.getPackedId(MapColor.Brightness.HIGH);
    static final byte WATER = MapColor.WATER.getPackedId(MapColor.Brightness.NORMAL);
    static final byte LAVA = MapColor.FIRE.getPackedId(MapColor.Brightness.HIGH);
    static final byte PIT = MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.NORMAL);

    /** The item's own tint, a dark brown, so a Dungeon Map is told from a plain map in the hotbar. */
    private static final int MAP_TINT = 0x6B4A2B;

    private DungeonMaps() {
    }

    /** A blank Dungeon Map of this floor, with its data registered in the level. */
    public static ItemStack create(ServerLevel level, FloorMapStamp stamp) {
        int id = level.getFreeMapId();
        CompoundTag dataTag = new CompoundTag();
        dataTag.putString("dimension", level.dimension().location().toString());
        dataTag.putInt("xCenter", stamp.centerX());
        dataTag.putInt("zCenter", stamp.centerZ());
        dataTag.putByte("scale", stamp.scale());
        dataTag.putBoolean("trackingPosition", true);
        dataTag.putBoolean("unlimitedTracking", false);
        dataTag.putBoolean("locked", true);
        // load() rather than createFresh(): createFresh snaps the centre to vanilla's map grid, which
        // would cut a floor that straddles a grid line in two
        level.setMapData(MapItem.makeKey(id), MapItemSavedData.load(dataTag));

        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.getOrCreateTag().putInt("map", id);
        stack.getOrCreateTag().put(TAG, stamp.save());
        stack.getOrCreateTagElement("display").putInt("MapColor", MAP_TINT);
        stack.setHoverName(Component.translatable("map.dungeons2.dungeon_map", stamp.floorIndex() + 1)
                .withStyle(style -> style.withItalic(false)));
        return stack;
    }

    /** The floor a stack maps, or null if it is not a Dungeon Map. */
    @Nullable
    public static FloorMapStamp stampOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return stack.is(Items.FILLED_MAP) && tag != null && tag.contains(TAG)
                ? FloorMapStamp.load(tag.getCompound(TAG)) : null;
    }

    /** Draws the floor around a player holding this Dungeon Map. Server side. */
    public static void reveal(ServerLevel level, Player player, ItemStack stack, FloorMapStamp stamp) {
        MapItemSavedData data = MapItem.getSavedData(stack, level);
        if (data == null || data.dimension != level.dimension()) {
            return;
        }
        BlockPos feet = player.blockPosition();
        if (feet.getY() < stamp.floorY() - BELOW || feet.getY() > stamp.floorY() + ABOVE) {
            return;
        }
        scan(level, stamp, feet, (x, z, colour) -> paint(data, x, z, colour));
    }

    /** Where {@link #scan} puts a colour: a column and what it shows. */
    @FunctionalInterface
    interface Painter {
        void paint(int x, int z, byte colour);
    }

    /**
     * The drawing itself, apart from the map: floods outward from {@code feet} through walkable
     * columns within {@link #REVEAL_RADIUS}, colouring each, and colours the column where each walk
     * stops - door, wall or drop - without going on.
     */
    static void scan(BlockGetter level, FloorMapStamp stamp, BlockPos feet, Painter painter) {
        Integer start = walkable(level, feet.getX(), feet.getZ(), feet.getY() - 1);
        if (start == null) {
            // in the air, or on something odd: try again next time
            return;
        }

        // 1. walk: every column reachable on foot within the radius, and where each walk stops
        LongSet floor = new LongOpenHashSet();
        Long2ByteMap colours = new Long2ByteOpenHashMap();
        Deque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{feet.getX(), feet.getZ(), start});
        LongSet seen = new LongOpenHashSet();
        seen.add(key(feet.getX(), feet.getZ()));
        int radiusSqr = REVEAL_RADIUS * REVEAL_RADIUS;
        while (!open.isEmpty()) {
            int[] cell = open.poll();
            int x = cell[0];
            int z = cell[1];
            int y = cell[2];
            floor.add(key(x, z));
            colours.put(key(x, z), floorColour(level, stamp, x, y, z));
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + step[0];
                int nz = z + step[1];
                int dx = nx - feet.getX();
                int dz = nz - feet.getZ();
                if (dx * dx + dz * dz > radiusSqr || !seen.add(key(nx, nz))) {
                    continue;
                }
                Integer ny = walkable(level, nx, nz, y);
                if (ny != null && ny < stamp.floorY() - FLOOR_DROP) {
                    // walking on down, past any descent of this floor's own: the way to the floor below
                    colours.put(key(nx, nz), STAIRS);
                } else if (ny != null && ny > stamp.floorY() + FLOOR_RISE) {
                    colours.put(key(nx, nz), STAIRS_UP);
                } else if (ny != null) {
                    open.add(new int[]{nx, nz, ny});
                } else {
                    colours.put(key(nx, nz), edgeColour(level, nx, y, nz));
                }
            }
        }

        // 2. look: of the floor, only what the holder can see - a straight line to it over open floor
        LongSet seenFloor = new LongOpenHashSet();
        for (long cell : floor) {
            int x = (int) (cell >> 32);
            int z = (int) cell;
            if (inSight(floor, feet.getX(), feet.getZ(), x, z)) {
                seenFloor.add(cell);
                painter.paint(x, z, colours.get(cell));
            }
        }
        // and every wall, door or drop that edges a stretch of floor in sight - seen across that
        // floor, though at a corridor's grazing angle the line to the wall itself is cut by the wall
        for (Long2ByteMap.Entry entry : colours.long2ByteEntrySet()) {
            long cell = entry.getLongKey();
            if (floor.contains(cell)) {
                continue;
            }
            int x = (int) (cell >> 32);
            int z = (int) cell;
            if (seenFloor.contains(key(x + 1, z)) || seenFloor.contains(key(x - 1, z))
                    || seenFloor.contains(key(x, z + 1)) || seenFloor.contains(key(x, z - 1))) {
                painter.paint(x, z, entry.getByteValue());
            }
        }
    }

    /**
     * Whether a column can be seen from the holder's: every column a straight line between their
     * centres crosses, short of the far end, is open floor. Sampled every quarter block, which catches
     * both columns at a diagonal corner, so the line cannot slip between two walls that only touch
     * at a corner.
     */
    static boolean inSight(LongSet floor, int fromX, int fromZ, int toX, int toZ) {
        int dx = toX - fromX;
        int dz = toZ - fromZ;
        int steps = Math.max(Math.abs(dx), Math.abs(dz)) * 4;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(fromX + 0.5D + dx * t);
            int z = (int) Math.floor(fromZ + 0.5D + dz * t);
            if ((x != toX || z != toZ) && !floor.contains(key(x, z))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The floor height a player could stand on in this column, stepping at most one block up or down
     * from {@code from}: a block with something to stand on and two clear blocks above it. Null if
     * there is none.
     */
    @Nullable
    static Integer walkable(BlockGetter level, int x, int z, int from) {
        for (int y = from + 1; y >= from - 1; y--) {
            // a ladder's box is solid, but the top of a ladder is a way down, not a floor
            if (solid(level, x, y, z) && !climbable(level, x, y, z)
                    && !solid(level, x, y + 1, z) && !solid(level, x, y + 2, z)) {
                return y;
            }
        }
        return null;
    }

    /** A ladder - not anything climbable: vines hang in pits and on walls all through a dungeon. */
    private static boolean climbable(BlockGetter level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof LadderBlock;
    }

    private static boolean solid(BlockGetter level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static byte floorColour(BlockGetter level, FloorMapStamp stamp, int x, int y, int z) {
        if (stamp.isStairsDown(x, z)) {
            return STAIRS;
        }
        FluidState fluid = level.getFluidState(new BlockPos(x, y + 1, z));
        if (fluid.is(FluidTags.WATER)) {
            return WATER;
        }
        if (fluid.is(FluidTags.LAVA)) {
            return LAVA;
        }
        return FLOOR;
    }

    /** Where the walking stops: a ladder, a door, a wall, or a drop with no floor in reach. */
    private static byte edgeColour(BlockGetter level, int x, int y, int z) {
        // a ladder first: it has a collision box, so it would otherwise read as wall
        if (climbable(level, x, y, z) || climbable(level, x, y - 1, z)) {
            return STAIRS;
        }
        if (climbable(level, x, y + 1, z) || climbable(level, x, y + 2, z)) {
            return STAIRS_UP;
        }
        for (int up = 1; up <= 2; up++) {
            BlockPos pos = new BlockPos(x, y + up, z);
            BlockState state = level.getBlockState(pos);
            if (isDoor(level, pos, state)) {
                return DOOR;
            }
        }
        if (solid(level, x, y + 1, z) || solid(level, x, y + 2, z)) {
            return WALL;
        }
        return PIT;
    }

    /**
     * A door of any kind, as a pathfinder sees one - except a hidden door shut, which is wall to
     * anyone who has not found it.
     */
    private static boolean isDoor(BlockGetter level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.OPEN)) {
            return false;
        }
        if (state.getBlock() instanceof HiddenDoorBlock) {
            return state.getValue(BlockStateProperties.OPEN);
        }
        if (state.getBlock() instanceof DoorBlock) {
            return true;
        }
        BlockPathTypes type = state.getBlockPathType(level, pos, null);
        return type == BlockPathTypes.DOOR_WOOD_CLOSED || type == BlockPathTypes.DOOR_IRON_CLOSED
                || type == BlockPathTypes.DOOR_OPEN;
    }

    private static void paint(MapItemSavedData data, int x, int z, byte colour) {
        int size = 1 << data.scale;
        int px = Math.floorDiv(x - data.centerX, size) + 64;
        int pz = Math.floorDiv(z - data.centerZ, size) + 64;
        if (px >= 0 && px < 128 && pz >= 0 && pz < 128) {
            data.updateColor(px, pz, colour);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
