/**
 * 
 */
package com.someguyssoftware.dungeonsengine.builder;

import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeonsengine.config.DungeonConfigManager;
import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.dungeonsengine.model.Dungeon;
import com.someguyssoftware.gottschcore.positional.BBox;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Dec 23, 2018
 *
 */
public class DungeonBuilder {
	private static int MIN_BOUNDARY_RADIUS = 25;
	private static int MAX_BOUNDARY_RADIUS = 256;

	public Dungeon EMPTY_DUNGEON = new Dungeon();
	public Boundary EMPTY_BOUNDARY = new Boundary(new BBox(new Coords(0,0,0)));
	
	/*
	 * A world is required.
	 */
	private World world;
	private Random random;
	private IDungeonConfig config;
	private Boundary boundary;
	private ILevelBuilder levelBuilder;
	
	/*
	 * The only required option to build a dungeon.
	 */
	private ICoords spawnCoords;
	
	private ICoords startPoint;
	
	/*
	 * Generated properties
	 */
	// private ISpace entrance;
	private int numLevels;
	
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
	 * @param builder
	 * @return
	 */
	public DungeonBuilder with(ILevelBuilder builder) {
		this.levelBuilder = builder;
		return this;
	}
	
	/**
	 * Required.
	 * @param spawnCoords
	 * @return
	 */
	public DungeonBuilder withSpawnPoint(ICoords spawnCoords) {
		this.spawnCoords = spawnCoords;
		return this;
	}
	
	/**
	 * 
	 * @param coords
	 * @return
	 */
	public DungeonBuilder withStartPoint(ICoords coords) {
		this.startPoint = coords;
		return this;
	}
	
	/**
	 * 
	 * @param spawnCoords
	 * @return
	 */
	public Dungeon build() {
		Dungeon dungeon = EMPTY_DUNGEON;
		
		/*
		 * Test all options
		 */
		if (this.spawnCoords == null) {
			Dungeons2.log.error("A spawn coordinate is required to build a dungeon.");
			return EMPTY_DUNGEON;
		}
		
		if (this.config == null) {
			Dungeons2.log.debug("A dungeon config was not provided. Using default config.");
			this.config = DungeonConfigManager.DEFAULT_CONFIG;
		}
		
		if (this.levelBuilder == null) {
			Dungeons2.log.debug("A level builder was not provided. Creating level builder...");
			this.levelBuilder = new LevelBuilder(this.getWorld(), this.getRandom())
					.with(this.getConfig().getLevelConfigs()[0]);
		}
		
		if (this.boundary == null) {
			Dungeons2.log.debug("Calculating dungeon boundary...");
			this.boundary = calculateDungeonBoundary();
			if (this.boundary == EMPTY_BOUNDARY) return EMPTY_DUNGEON;
		}
		
		/*
		 * Calculate spawn boundary
		 */
		Boundary levelBoundary = this.boundary.resize(this.config.getLevelConfigs()[0].getBoundaryFactor(), 25); // TODO update 25
//		AxisAlignedBB lb = getLevelBuilder()
//				.resizeBoundary(dungeonBoundary, defaultLevelConfig.getBoundaryFactor());
		Dungeons2.log.debug("init level boundary -> {}, factor -> {}", levelBoundary, this.config.getLevelConfigs()[0].getBoundaryFactor());
		Boundary spawnBoundary = levelBoundary.resize(this.config.getLevelConfigs()[0].getSpawnBoundaryFactor(), 25);
//		AxisAlignedBB roomBoundary = getLevelBuilder()
//				.resizeBoundary(lb, defaultLevelConfig.getSpawnBoundaryFactor());
		Dungeons2.log.debug("init spawn boundary -> {}, factor -> {}", 
				spawnBoundary, this.config.getLevelConfigs()[0].getSpawnBoundaryFactor());
		
		/*
		 * Select startPoint in room boundary
		 */
		if (getStartPoint() == null) {
			ICoords startPoint = GenUtil.randomizeCoords(getRandom(), spawnBoundary);
			// check if the start point is in a loaded chunk
			ChunkPos startChunk = startPoint.toChunkPos();
			if (!world.isChunkGeneratedAt(startChunk.x, startChunk.z)) {
				Dungeons2.log.debug("startPoint is not in a loaded chunk -> {}", startChunk);
				return EMPTY_DUNGEON;
			}
			// get a valid Y value
			this.startPoint = startPoint.resetY(WorldInfo.getHeightValue(world, startPoint));
		}
		
		/*
		 * Perform tests
		 */
		
		// return the dungeon
		return dungeon;
	}

	/**
	 * 
	 * @return
	 */
	public Boundary calculateDungeonBoundary() {
		/*
		 * Calculate dungeon boundary
		 */
		Boundary dungeonBoundary = null;
		ICoords closestCoords = null;
		if (this.getBoundary() == null) {
			// get the closest player
			closestCoords = WorldInfo.getClosestPlayerCoords(world, spawnCoords);
			if (closestCoords == null) {
				Dungeons2.log.warn("Unable to locate closest player - using World spawn point");
				closestCoords = new Coords(world.getSpawnPoint());
			}
			// get the boundary based on the player position and spawn
			dungeonBoundary = calculateDungeonBoundary(closestCoords);
			if (dungeonBoundary == null) {
				Dungeons2.log.warn("Unable to calculate dungeon boundary from spawn pos-> {}, and player pos -> {}", 
						spawnCoords.toShortString(), closestCoords.toShortString());				
				return EMPTY_BOUNDARY;
			}
			// update the boundary property to the dungeon boundary
			this.boundary = dungeonBoundary;
		}
		else dungeonBoundary = this.getBoundary();		

		Dungeons2.log.debug("Dungeon boundary -> {}", dungeonBoundary);
		Dungeons2.log.debug("Dungeon boundary factor -> {}", config.getBoundaryFactor());
		// resize boundary
		if (config.getBoundaryFactor() > 0D && config.getBoundaryFactor() < 1.0D) {
			int xAmount = (int) (((dungeonBoundary.getMaxCoords().getX() - dungeonBoundary.getMinCoords().getX()) * (1.0 - config.getBoundaryFactor())) / 2);
			int zAmount = (int) (((dungeonBoundary.getMaxCoords().getZ() - dungeonBoundary.getMinCoords().getZ()) * (1.0 - config.getBoundaryFactor())) / 2);
			dungeonBoundary = dungeonBoundary.grow(-xAmount, 0, -zAmount);
			Dungeons2.log.debug("Dungeon shrunk by factor -> {} [{} {}], to new size -> {}", config.getBoundaryFactor(), xAmount, zAmount, dungeonBoundary);
			// update the boundary of this
			this.boundary = dungeonBoundary;
		}
		return dungeonBoundary;
	}
	
	/**
	 * 
	 * @param coords
	 */
	protected Boundary calculateDungeonBoundary(ICoords coords) {
		Boundary dungeonBoundary = null;		
		
		ICoords deltaCoords = this.spawnCoords.delta(coords);
		Dungeons2.log.debug("spawnCoords -> {}", this.spawnCoords);
		Dungeons2.log.debug("deltaCoords -> {}", deltaCoords.toShortString());
		// get max. distance of the axises, ensuring a min/max size is met
		int dist = Math.max(
				Math.min(
						Math.max(
								Math.abs(deltaCoords.getX()), 
								Math.abs(deltaCoords.getZ())),
						MAX_BOUNDARY_RADIUS), // 256 = MAX_FIELD_RADIUS
				MIN_BOUNDARY_RADIUS);  // 25 = MIN_FIELD_RADIUS
		Dungeons2.log.debug("dist from player to spawn -> {}", dist);

		dungeonBoundary = new Boundary(coords, coords.add(1, 1, 1));

		EnumFacing fieldFacing = null;
		if (Math.abs(deltaCoords.getX()) >= Math.abs(deltaCoords.getZ())) {
			Dungeons2.log.debug("deltaX -> {} >= deltaZ -> {}", Math.abs(deltaCoords.getX()), Math.abs(deltaCoords.getZ()));
			// WEST/EAST
			if (deltaCoords.getX() < 0 ) {
				// west
				Dungeons2.log.debug("field facing west");
				fieldFacing = EnumFacing.WEST;
				dungeonBoundary = dungeonBoundary.expand(-(dist), 0, 0).grow(0, 0, dist);
			}
			else {
				// east
				Dungeons2.log.debug("field facing east");
				fieldFacing = EnumFacing.EAST;
				dungeonBoundary = dungeonBoundary.expand(dist, 0, 0).grow(0, 0, dist);
			}
		}
		else {			
			Dungeons2.log.debug("deltaX -> {} < deltaZ -> {}", Math.abs(deltaCoords.getX()), Math.abs(deltaCoords.getZ()));
			// NORTH/SOUTH
			if (deltaCoords.getZ() < 0)  {
				// north
				Dungeons2.log.debug("field facing north");
				fieldFacing = EnumFacing.NORTH;
				dungeonBoundary = dungeonBoundary.expand(0, 0, -(dist)).grow(dist, 0, 0);
			}
			else {
				// south
				Dungeons2.log.debug("field facing south");
				fieldFacing = EnumFacing.SOUTH;
				dungeonBoundary = dungeonBoundary.expand(0, 0, dist).grow(dist, 0, 0);
			}
		}
		return dungeonBoundary;
	}
	
	/**
	 * @return the world
	 */
	public World getWorld() {
		return world;
	}

	/**
	 * @return the boundary
	 */
	public Boundary getBoundary() {
		return boundary;
	}

	/**
	 * @return the levelBuilder
	 */
	public ILevelBuilder getLevelBuilder() {
		return levelBuilder;
	}

	/**
	 * @param levelBuilder the levelBuilder to set
	 */
	public void setLevelBuilder(ILevelBuilder levelBuilder) {
		this.levelBuilder = levelBuilder;
	}

	/**
	 * @return the random
	 */
	public Random getRandom() {
		return random;
	}

	/**
	 * @return the config
	 */
	public IDungeonConfig getConfig() {
		return config;
	}

	/**
	 * @return the startPoint
	 */
	public ICoords getStartPoint() {
		return startPoint;
	}
}
