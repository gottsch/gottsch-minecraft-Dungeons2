/**
 * 
 */
package com.someguyssoftware.dungeonsengine.builder;

import java.util.Random;

import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Jan 13, 2019
 *
 */
public class SurfaceLevelBuilder extends LevelBuilder {

	/**
	 * 
	 * @param world
	 */
	public SurfaceLevelBuilder(World world) {
		super(world);
	}

	/**
	 * 
	 * @param world
	 * @param random
	 */
	public SurfaceLevelBuilder(World world, Random random) {
		super(world, random);
	}
}
