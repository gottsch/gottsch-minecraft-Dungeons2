/**
 *
 */
package com.someguyssoftware.dungeons2.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

/**
 * Chunk queries that never load or generate a chunk.
 * <p>
 * NOTE world.getChunkFromBlockCoords(pos).isLoaded() is NOT a safe "is loaded" test - getChunkFromBlockCoords()
 * loads (or generates) the chunk first, so the test always passes and forces chunk generation.
 * Always go through IChunkProvider.getLoadedChunk(), which returns null instead of loading.
 *
 * @author Mark Gottschling on 10/19/2026
 */
public final class ChunkUtil {

	private ChunkUtil() {}

	/**
	 *
	 * @param world
	 * @param chunkX
	 * @param chunkZ
	 * @return true if the chunk is currently loaded. Never loads the chunk.
	 */
	public static boolean isChunkLoaded(World world, int chunkX, int chunkZ) {
		return world.getChunkProvider().getLoadedChunk(chunkX, chunkZ) != null;
	}

	/**
	 *
	 * @param world
	 * @param pos
	 * @return true if the chunk containing pos is currently loaded. Never loads the chunk.
	 */
	public static boolean isChunkLoaded(World world, BlockPos pos) {
		return isChunkLoaded(world, pos.getX() >> 4, pos.getZ() >> 4);
	}

	/**
	 * A chunk is ready for dungeon generation when it is loaded and has already been populated (decorated).
	 * Writing into an unpopulated chunk lets its later population (trees, lakes, vanilla dungeons, other mods)
	 * stamp over the dungeon.
	 * @param world
	 * @param chunkX
	 * @param chunkZ
	 * @return
	 */
	public static boolean isChunkReady(World world, int chunkX, int chunkZ) {
		Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
		return chunk != null && chunk.isTerrainPopulated();
	}

	/**
	 * Determines if every chunk overlapping the block area (inclusive) is loaded and populated.
	 * @param world
	 * @param minX
	 * @param minZ
	 * @param maxX
	 * @param maxZ
	 * @return
	 */
	public static boolean isAreaReady(World world, int minX, int minZ, int maxX, int maxZ) {
		for (int chunkX = Math.min(minX, maxX) >> 4; chunkX <= Math.max(minX, maxX) >> 4; chunkX++) {
			for (int chunkZ = Math.min(minZ, maxZ) >> 4; chunkZ <= Math.max(minZ, maxZ) >> 4; chunkZ++) {
				if (!isChunkReady(world, chunkX, chunkZ)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Determines if every chunk within radius blocks (x/z) of the center is loaded and populated.
	 * @param world
	 * @param center
	 * @param radius
	 * @return
	 */
	public static boolean isAreaReady(World world, BlockPos center, int radius) {
		return isAreaReady(world, center.getX() - radius, center.getZ() - radius, center.getX() + radius, center.getZ() + radius);
	}
}
