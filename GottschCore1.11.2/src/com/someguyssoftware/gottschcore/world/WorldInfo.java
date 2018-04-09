/**
 * 
 */
package com.someguyssoftware.gottschcore.world;

import com.someguyssoftware.gottschcore.cube.Cube;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on May 6, 2017
 *
 */
public class WorldInfo {
	private static final int MAX_HEIGHT = 256;
	private static final int MIN_HEIGHT = 1;
	public static final ICoords EMPTY_COORDS = new Coords(-1, -1, -1);
	public static final int INVALID_SURFACE_POS = -255;
	
	/*
	 * =========================================
	 * Find the topmost block position methods 
	 * =========================================
	 */
	
	/**
	 * Finds the topmost block position at an Coords position in the world.  Wrapper for BlockPos version.
	 * @param world
	 * @param coords
	 * @return
	 */
    public static int getHeightValue(final World world, final ICoords coords) { 
	     return getHeightValue(world, coords.toPos());
   }
    
	/**
	 * Finds the topmost block position at an BlockPos position in the world
	 * @param world
	 * @param pos
	 * @return
	 */
    private static int getHeightValue(final World world, final BlockPos pos) { 
//	     int y = world.getChunkFromBlockCoords(pos).getHeight(pos);
//    	GottschCore.logger.debug("Get height for blockpos:" + pos.toString());
//    	int y = world.getHeight(pos.getX(), pos.getY());
    	BlockPos p = world.getHeight(pos);
//    	GottschCore.logger.debug("Get height(pos):" + p.toString());
	   return p.getY();
    }
    
    /*
	 * =========================================
     * Determine valid height (y-pos) methods
	 * =========================================
     */
    
    /**
     * 
     * @param coords
     * @return
     */
	public static boolean isValidY(final ICoords coords) {
		return isValidY(coords.toPos());
	}
	
	/**
	 * 
	 * @param blockPos
	 * @return
	 */
	private static boolean isValidY(final BlockPos blockPos) {
		if ((blockPos.getY() < MIN_HEIGHT || blockPos.getY() > MAX_HEIGHT)) {
			return false;
		}
		return true;
	}
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @return
	 */
	public static boolean isValidY(final World world, final ICoords coords) {
		return isValidY(world, coords.toPos());
	}

	/**
	 * 
	 * @param world
	 * @param blockPos
	 * @return
	 */
	private static boolean isValidY(final World world, final BlockPos blockPos) {
		if ((blockPos.getY() < MIN_HEIGHT || blockPos.getY() > world.getActualHeight())) {
			return false;
		}
		return true;
	}
	
	/*
	 * =========================================
	 * Surface location methods
	 * =========================================
	 */
	
	/**
	 * Gets the surface block at a given position (x,z).
	 * Any aboveground land surface or surface of body of water/lava, ie. excludes any vegatation or replacement material.
	 * @param world
	 * @param coords
	 * @return
	 */
	public static ICoords getSurfaceCoords(final World world, final ICoords coords) {

		boolean isSurfaceBlock = false;
		ICoords newCoords = coords;
		
		while (!isSurfaceBlock) {		
			Cube cube = new Cube(world, newCoords.down(1));
			
			// exit if not valid Y coordinate
			if (!isValidY(cube.getCoords())) {
				return null;
			}		
			
			if (cube.equalsMaterial(Material.AIR) || cube.isReplaceable()
					|| cube.equalsMaterial(Material.LEAVES) || cube.equalsBlock(Blocks.LOG) || cube.equalsBlock(Blocks.LOG2) 
					|| cube.isBurning(world)) {
				newCoords = newCoords.down(1);
			}
			else {
				isSurfaceBlock = true;
			}
			
	
		}
		return newCoords;
	}
	
//	/**
//	 * 
//	 * @param world
//	 * @param pos
//	 * @return
//	 */
//	public static ICoords getSurfaceCoords(final World world, final BlockPos pos) {
//		return getSurfaceCoords(world, new Coords(pos));
//	}
	
	/**
	 * Gets the first valid dry land surface position (not on/under water or lava) from the given starting point.
	 * @param world
	 * @param coords
	 * @return
	 */
	public static ICoords getDryLandSurfaceCoords(final World world, final ICoords coords) {
		boolean isSurfaceBlock = false;
		ICoords newCoords = coords;
		
		while (!isSurfaceBlock) {
			// get the cube that is 1 below current position
			Cube cube = new Cube(world, newCoords.down(1));
			
			// exit if not valid Y coordinate
			if (!isValidY(cube.getCoords())) {
				return EMPTY_COORDS;
			}	
			
			// test if the block at position is water, lava or ice
			if (cube.equalsMaterial(Material.WATER)
					|| cube.equalsMaterial(Material.LAVA)
					|| cube.equalsMaterial(Material.ICE)) {
				return EMPTY_COORDS;
			}
			
			if (cube.equalsMaterial(Material.AIR) || cube.isReplaceable()
					|| cube.equalsMaterial(Material.LEAVES) ||cube.equalsMaterial(Material.WOOD) 
					|| cube.isBurning(world)) {
				newCoords = newCoords.down(1);
			}
			else {
				isSurfaceBlock = true;
			}		
		}
		return newCoords;
	}
	
	/**
	 * Gets the first valid land surface position (could be under water or lava) from the given starting point.
	 * @param world
	 * @param pos
	 * @return
	 */
	public static ICoords getAnyLandSurfaceCoords(final World world, final ICoords coords) {
		boolean isSurfaceBlock = false;
		ICoords newCoords = coords;
		
		while (!isSurfaceBlock) {
			Cube cube = new Cube(world, newCoords.down(1));
			
			// exit if not valid Y coordinate
			if (!isValidY(cube.getCoords())) {
				return EMPTY_COORDS;
			}			
			if (cube.equalsMaterial(Material.AIR) || cube.equalsMaterial(Material.WATER)||cube.isReplaceable()
					|| cube.equalsMaterial(Material.LEAVES) ||cube.equalsMaterial(Material.WOOD) 
					|| cube.equalsMaterial(Material.LAVA)
					|| cube.equalsMaterial(Material.ICE)
					|| cube.isBurning(world)) {
				newCoords = newCoords.down(1);
			}
			else {
				isSurfaceBlock = true;
			}
		}
		return newCoords;
	}
	
	/**
	 * 
	 * @param world
	 * @param pos
	 * @return
	 */
	public static ICoords getOceanFloorSurfaceCoords(final World world, final ICoords coords) {
		boolean isSurfaceBlock = false;
		ICoords newCoords = coords;
		
		while (!isSurfaceBlock) {
			Cube cube = new Cube(world, newCoords.down(1));
			// exit if not valid Y coordinate
			if (!isValidY(cube.getCoords())) {
				return EMPTY_COORDS;
			}	
			
			// test if the block at position is water, lava or ice
			if (cube.equalsMaterial(Material.LAVA)) {
				return EMPTY_COORDS;
			}
			
			if (cube.equalsMaterial(Material.AIR) || cube.equalsMaterial(Material.WATER)
					|| cube.equalsMaterial(Material.ICE) || cube.isReplaceable()
					|| cube.equalsMaterial(Material.LEAVES) ||cube.equalsMaterial(Material.WOOD) 
					|| cube.isBurning(world)) {
				newCoords = newCoords.down(1);
			}
			else {
				isSurfaceBlock = true;
			}		
		}
		return newCoords;
	}	
	
	/*
	 * =========================================
	 * Base check methods
	 * =========================================
	 */
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @param width
	 * @param depth
	 * @param groundPercentRequired
	 * @param airPercentRequired
	 * @return
	 */
	public static boolean isValidAboveGroundBase(final World world, final ICoords coords,
			final int width, final int depth, final double groundPercentRequired, final double airPercentRequired) {
		return isSolidBase(world, coords, width, depth, groundPercentRequired) && 
				isAirBase(world, coords.up(1), width, depth, airPercentRequired);
	}
	
	/**
	 * Method to check if the ground (base) has a % of solid blocks, and checks the y+1, and y+2 to see if has a % of air blocks.
	 * @param world
	 * @param coords
	 * @param width
	 * @param depth
	 * @param groundPercentRequired
	 * @param airPercentRequired1
	 * @param airPercentRequired2
	 * @return
	 */
	public static boolean isValidAboveGroundBase(final World world, final ICoords coords,
			final int width, final int depth, 
			final double groundPercentRequired,
			final double airPercentRequired1,
			final double airPercentRequired2) {
		return isSolidBase(world, coords, width, depth, groundPercentRequired) && 
				isAirBase(world, coords.up(1), width, depth, airPercentRequired1) && 
				isAirBase(world, coords.up(2), width, depth, airPercentRequired2);
	}
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @param width
	 * @param depth
	 * @param percentRequired
	 * @return
	 */
	public static boolean isSolidBase(final World world, final ICoords coords, final int width, final int depth, final double percentRequired) {
		double percent = getSolidBasePercent(world, coords.down(1), width, depth);
		
		if (percent < percentRequired) {
			return false;
		}		
		return true;
	}
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @param width
	 * @param depth
	 * @return
	 */
	public static double getSolidBasePercent(final World world, final ICoords coords, final int width, final int depth) {
		int platformSize = 0;
		Cube cube = new Cube(world, coords);
		
		// process all z, x in base y (-1) to count the number of allowable blocks in the world platform
		for (int z = 0; z < depth; z++) {
			for (int x = 0; x < width; x++) {
				// get the cube
				cube = new Cube(world, cube.getCoords().add(x, 0, z));

				// test the cube
				if (cube.hasState() && cube.isSolid() && ! cube.isReplaceable()) {
					platformSize++;						
				}
			}
		}
		
		double base = depth * width;
		double percent = ((platformSize) / base) * 100.0D;
		return percent;
	}
	
	/**
	 * Gets the percent of blocks in an area (WxD) that are air/air-like (replaceable).
	 * @param world
	 * @param coords
	 * @param width
	 * @param depth
	 * @return
	 */
	public static double getAirBasePercent(final World world, final ICoords coords, final int width, final int depth) {
		double percent = 0.0D;
		int airBlocks = 0;
		Cube cube = new Cube(world, coords);
		
		// process all z, x in base y to count the number of allowable blocks in the world platform
		for (int z = 0; z < depth; z++) {
			for (int x = 0; x < width; x++) {
				// get the cube
				cube = new Cube(world, cube.getCoords().add(x, 0, z));
				
				if (cube.hasState() || cube.equalsMaterial(Material.AIR) || cube.isReplaceable()) {
					airBlocks++;		
				}
			}
		}		
		double base = depth * width;
		percent = (airBlocks / base) * 100.0D;		
		return percent;
	}
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @param width
	 * @param depth
	 * @param percentRequired
	 * @return
	 */
	public static boolean isAirBase(final World world, final ICoords coords, final int width, final int depth, double percentRequired) {
		double percent = getAirBasePercent(world, coords.down(1), width, depth);
		
		if (percent < percentRequired) {
			return false;
		}		
		return true;
	}
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @return
	 */
	public static int getDifferenceWithSurface(World world, ICoords coords) {
		int ySurface = 0;
		int diff = 0;
		
		// get a valid surface coords (whether on land or sea)
		ySurface = getHeightValue(world, coords);
//		GottschCore.logger.debug("ySurface:" + ySurface);
		ICoords surfaceCoords= getSurfaceCoords(world, coords.resetY(ySurface));
		if (surfaceCoords == null) {
			return INVALID_SURFACE_POS;
		}
		
		// get the difference betwen ySurface and the Coords Y
		diff = surfaceCoords.getY() - coords.getY();		
		
		return diff;	
	}
}
