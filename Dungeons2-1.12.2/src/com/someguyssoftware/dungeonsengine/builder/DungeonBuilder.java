/**
 * 
 */
package com.someguyssoftware.dungeonsengine.builder;

import java.util.Random;

import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Dec 23, 2018
 *
 */
public class DungeonBuilder {

	private World world;
	private Random random;
	private IDungeonConfig config;
	private Boundary boundary;
	private int numLevels;
	// private ISpace entrance;
	
	/**
	 * 
	 */
	public DungeonBuilder(World world) {
		this.world = world;
		this.random = new Random();
	}

	/**
	 * 
	 * @param world
	 * @param random
	 */
	public DungeonBuilder(World world, Random random) {
		this.world = world;
		this.random = random;
	}
	
	/**
	 * 
	 * @param config
	 * @return
	 */
	public DungeonBuilder with(IDungeonConfig config) {
		this.config = config;
		return this;
	}
	
	/**
	 * 
	 * @param boundary
	 * @return
	 */
	public DungeonBuilder with(Boundary boundary) {
		this.boundary = boundary;
		return this;
	}
	
	/**
	 * 
	 * @param spawnCoords
	 * @return
	 */
	public boolean build(ICoords spawnCoords) {
		
		return true;
	}
}
