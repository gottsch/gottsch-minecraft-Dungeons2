/**
 * 
 */
package com.someguyssoftware.dungeonsengine.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.dungeonsengine.model.ILevel;
import com.someguyssoftware.dungeonsengine.model.IVoid;
import com.someguyssoftware.dungeonsengine.model.Level;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Dec 27, 2018
 *
 */
public class LevelBuilder implements ILevelBuilder {
	public static Logger logger = LogManager.getLogger("DungeonsEngine");
	
	/*
	 * empty level
	 */
//	public static final ILevel EMPTY_LEVEL = new Level();

	public static final List<IVoid> EMPTY_VOIDS = new ArrayList<>(1);

//	public static final List<Wayline> EMPTY_WAYLINES = new ArrayList<>(1);

	/**
	 * 
	 */
	private static final double DEFAULT_FORCE_MODIFIER = 0.85;
	
	private World world;	
	private Random random;
	private ILevelConfig config;
	private Boundary boundary;
	/*
	 * where the start room should generate if it is not provided
	 */
	private ICoords startPoint;
	
	private IVoidBuilder voidBuilder;
	private List<IVoid> plannedVoids;
	
	/*
	 * the number of rooms lost as a result of distance buffering
	 */
	int roomLossToDistanceBuffering = 0;
	
	/*
	 * the number of rooms lost as a result of world validation
	 */
	int roomLossToValidation = 0;
	
	/**
	 * 
	 */
	public LevelBuilder(World world, Random random, Boundary boundary, ICoords startPoint, ILevelConfig config) {
		this.world = world;
		this.random = random;
		this.boundary = boundary;
		this.startPoint = startPoint;
		this.config = config;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ILevelBuilder#build()
	 */
	@Override
	public ILevel build() {
		/*
		 * local handle to the start room
		 */
		IVoid start = null;
		
		/*
		 *  local handle to the end room
		 */
		IVoid end = null;
		
		/*
		 * return object containing all the rooms that meet build criteria and the locations of the special rooms.
		 */
		ILevel level = new Level();
		
		return level;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ILevelBuilder#getConfig()
	 */
	@Override
	public ILevelConfig getConfig() {
		return this.config;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ILevelBuilder#with(com.someguyssoftware.dungeonsengine.config.ILevelConfig)
	 */
	@Override
	public ILevelBuilder with(ILevelConfig config) {
		if (config != null) this.config = config;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ILevelBuilder#getSpaceBuilder()
	 */
	@Override
	public IVoidBuilder getVoidBuilder() {
		return this.voidBuilder;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ILevelBuilder#setSpaceBuilder(com.someguyssoftware.dungeonsengine.builder.ISpaceBuilder)
	 */
	@Override
	public ILevelBuilder with(IVoidBuilder builder) {
		if (builder != null) this.voidBuilder = builder;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ILevelBuilder#getPlannedSpaces()
	 */
	@Override
	public List<IVoid> getPlannedVoids() {
		if (this.plannedVoids == null) this.plannedVoids = new ArrayList<>();
		return this.plannedVoids;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ILevelBuilder#withSpace(com.someguyssoftware.dungeonsengine.model.ISpace)
	 */
	@Override
	public ILevelBuilder with(IVoid voidSpace) {
		getPlannedVoids().add(voidSpace);
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ILevelBuilder#reset()
	 */
	@Override
	public void reset() {
		// TODO Auto-generated method stub

	}

	/**
	 * @return the world
	 */
	public World getWorld() {
		return world;
	}

	/**
	 * @return the random
	 */
	public Random getRandom() {
		return random;
	}

	/**
	 * @return the boundary
	 */
	public Boundary getBoundary() {
		return boundary;
	}

	/**
	 * 
	 * @param boundary
	 * @return
	 */
	public ILevelBuilder with(Boundary boundary) {
		if (boundary != null) this.boundary = boundary;
		return this;
	}

	/**
	 * @return the startPoint
	 */
	public ICoords getStartPoint() {
		return startPoint;
	}
	
	/**
	 * 
	 * @param startPoint
	 * @return
	 */
	public ILevelBuilder with(ICoords startPoint) {
		this.startPoint = startPoint;
		return this;
	}
}
