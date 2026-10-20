/**
 *
 */
package com.someguyssoftware.dungeons2.generator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import com.someguyssoftware.dungeons2.model.Room;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Weathers a generated room (the surface entrance) into a ruin.
 * <p>
 * Material decay (mossy, cracked, cobbled) never changes a building's silhouette, and removing blocks at a flat rate
 * only gives swiss cheese with courses left hanging. Real ruins lose their tops, so:
 * <ol>
 * <li>masonry is removed with a probability that rises with height (thirds of the room's height: 5% / 25% / 75%).
 * The bands are measured from the room's geometry, not from the surviving blocks, so earlier holes don't shift them.</li>
 * <li>a connectivity sweep removes anything no longer connected to the floor or the surrounding terrain.
 * Attachments (ladders, torches, vines, webs) may be supported but never support anything else, otherwise a ladder
 * would hold a cornice in the sky.</li>
 * </ol>
 * Tile entities (chests, spawners) are never removed. Blocks are removed with flag 2 (no neighbor updates), so
 * nothing drops as items. The room's chunks must be loaded.
 *
 * @author Mark Gottschling on 10/19/2026
 */
public final class RuinProcessor {
	// removal probability for the bottom, middle and top thirds of the room
	private static final double[] REMOVAL_BY_THIRD = {0.05D, 0.25D, 0.75D};
	private static final IBlockState AIR = Blocks.AIR.getDefaultState();

	private RuinProcessor() {}

	/**
	 *
	 * @param world
	 * @param random
	 * @param room
	 */
	public static void ruin(World world, Random random, Room room) {
		// +1 x/z for trim/cornice overhangs, +1 y for the cornice/crown above the walls
		int minX = room.getMinX() - 1;
		int maxX = room.getMaxX() + 1;
		int minZ = room.getMinZ() - 1;
		int maxZ = room.getMaxZ() + 1;
		int floorY = room.getMinY();
		int maxY = room.getMaxY() + 1;
		int height = maxY - floorY;
		if (height <= 0) {
			return;
		}
		Bounds bounds = new Bounds(minX, floorY, minZ, maxX, maxY, maxZ);

		// 1. height gradient. the floor course is left intact (it covers the shaft)
		for (int y = floorY + 1; y <= maxY; y++) {
			int third = Math.min(2, (y - floorY - 1) * 3 / height);
			double chance = REMOVAL_BY_THIRD[third];
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (isRemovable(world, pos, room) && random.nextDouble() < chance) {
						world.setBlockState(pos, AIR, 2);
					}
				}
			}
		}

		// 2. connectivity sweep. flood from the grounded masonry through masonry only.
		Set<BlockPos> supported = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		for (int y = floorY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (isMasonry(world, pos) && isGrounded(world, pos, bounds)) {
						supported.add(pos);
						queue.add(pos);
					}
				}
			}
		}
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			for (EnumFacing facing : EnumFacing.VALUES) {
				BlockPos next = pos.offset(facing);
				if (bounds.contains(next) && !supported.contains(next) && isMasonry(world, next)) {
					supported.add(next);
					queue.add(next);
				}
			}
		}

		// remove unsupported masonry first, then any attachment left without a supported neighbor
		for (int y = floorY + 1; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (isRemovable(world, pos, room) && !supported.contains(pos)) {
						world.setBlockState(pos, AIR, 2);
					}
				}
			}
		}
		for (int y = floorY + 1; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (isAttachment(world, pos, room) && !hasSupportedNeighbor(pos, supported)) {
						world.setBlockState(pos, AIR, 2);
					}
				}
			}
		}
	}

	/**
	 * Masonry can be removed and conducts support: stone, bricks, stairs, slabs, facades, pillars.
	 * @param world
	 * @param pos
	 * @return
	 */
	private static boolean isMasonry(World world, BlockPos pos) {
		IBlockState state = world.getBlockState(pos);
		return state.getMaterial() == Material.ROCK && !state.getBlock().hasTileEntity(state);
	}

	/**
	 * Masonry inside the room's footprint, or a non-full block (trim, cornice overhang) in the 1 block ring around it.
	 * Full blocks in the ring may be natural terrain (ie a hillside), which is never removed.
	 * @param world
	 * @param pos
	 * @param room
	 * @return
	 */
	private static boolean isRemovable(World world, BlockPos pos, Room room) {
		if (!isMasonry(world, pos)) {
			return false;
		}
		boolean inFootprint = pos.getX() >= room.getMinX() && pos.getX() <= room.getMaxX()
				&& pos.getZ() >= room.getMinZ() && pos.getZ() <= room.getMaxZ();
		return inFootprint || !world.getBlockState(pos).isFullCube();
	}

	/**
	 * Attachments (ladders, torches, levers, vines, webs) inside the room's footprint need support but never conduct it.
	 * Terrain and plants around the room (dirt, grass, leaves) are never attachments.
	 * @param world
	 * @param pos
	 * @param room
	 * @return
	 */
	private static boolean isAttachment(World world, BlockPos pos, Room room) {
		boolean inFootprint = pos.getX() >= room.getMinX() && pos.getX() <= room.getMaxX()
				&& pos.getZ() >= room.getMinZ() && pos.getZ() <= room.getMaxZ();
		if (!inFootprint) {
			return false;
		}
		IBlockState state = world.getBlockState(pos);
		Material material = state.getMaterial();
		return (material == Material.CIRCUITS || material == Material.VINE || material == Material.WEB)
				&& !state.getBlock().hasTileEntity(state);
	}

	/**
	 * Masonry is grounded if it is part of the floor course, or touches solid terrain outside the room.
	 * @param world
	 * @param pos
	 * @param bounds
	 * @return
	 */
	private static boolean isGrounded(World world, BlockPos pos, Bounds bounds) {
		if (pos.getY() == bounds.minY) {
			return true;
		}
		for (EnumFacing facing : EnumFacing.VALUES) {
			BlockPos next = pos.offset(facing);
			if (!bounds.contains(next)) {
				IBlockState state = world.getBlockState(next);
				if (state.isFullCube() && isTerrain(state.getMaterial())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isTerrain(Material material) {
		return material == Material.ROCK || material == Material.GROUND || material == Material.GRASS
				|| material == Material.SAND || material == Material.CLAY;
	}

	private static boolean hasSupportedNeighbor(BlockPos pos, Set<BlockPos> supported) {
		for (EnumFacing facing : EnumFacing.VALUES) {
			if (supported.contains(pos.offset(facing))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * An inclusive block box.
	 */
	private static final class Bounds {
		final int minX, minY, minZ, maxX, maxY, maxZ;

		Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
		}

		boolean contains(BlockPos pos) {
			return pos.getX() >= minX && pos.getX() <= maxX
					&& pos.getY() >= minY && pos.getY() <= maxY
					&& pos.getZ() >= minZ && pos.getZ() <= maxZ;
		}
	}
}
