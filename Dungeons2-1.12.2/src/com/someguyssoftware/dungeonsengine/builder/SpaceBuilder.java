/**
 * 
 */
package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.dungeonsengine.enums.SpaceTag;
import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.dungeonsengine.model.ISpace;
import com.someguyssoftware.dungeonsengine.model.Space;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

/**
 * @author Mark Gottschling on Sep 22, 2018
 *
 */
public class SpaceBuilder implements ISpaceBuilder {
	public static Logger logger = LogManager.getLogger("DungeonsEngine");
	
	public static final ISpace EMPTY_SPACE = new Space();	
	public static final ICoords EMPTY_COORDS = new Coords(0, 0, 0);
	
	private ILevelConfig config;
	private Random random;
	private Boundary boundary;
	
	/**
	 * 
	 * @param boundary
	 */
	public SpaceBuilder(Random random, Boundary boundary, ILevelConfig config) {
		this.random = random;
		this.config = config;
		this.boundary = config.getSpawnBoundaryFactor() < 1.0D ? boundary.shrink(config.getSpawnBoundaryFactor()) : boundary;
	}
	
	/**
	 * 
	 * @param rand
	 * @param spaceIn
	 * @param config
	 * @return
	 */
	public ISpace randomizeDimensions(ISpace spaceIn) {
		ISpace space = spaceIn.copy();
		space.setWidth(Math.max(ISpace.MIN_WIDTH, RandomHelper.randomInt(random, config.getWidth().getMinInt(), config.getWidth().getMaxInt())));
		space.setDepth(Math.max(ISpace.MIN_DEPTH, RandomHelper.randomInt(random, config.getDepth().getMinInt(), config.getDepth().getMaxInt())));
		space.setHeight(Math.max(ISpace.MIN_HEIGHT, RandomHelper.randomInt(random, config.getHeight().getMinInt(), config.getHeight().getMaxInt())));		
		return space;
	}

	/**
	 * 
	 * @param random
	 * @param field
	 * @param config
	 * @return
	 */
	public ICoords randomizeCoords() {
		int x = RandomHelper.randomInt(random, 0, (int) (boundary.getMaxCoords().getX() - boundary.getMinCoords().getX()));
//		int y = RandomHelper.randomInt(random, config.getYVariance().getMinInt(), config.getYVariance().getMaxInt());
		int z = RandomHelper.randomInt(random, 0, (int) (boundary.getMaxCoords().getZ() - boundary.getMinCoords().getZ()));
		return new Coords((int)boundary.getMinCoords().getX(), 0, (int)boundary.getMinCoords().getZ()).add(x, 0, z);
	}
	
	/**
	 * 
	 * @param random
	 * @param field
	 * @param config
	 * @param roomIn
	 * @return
	 */
	public ISpace randomizeCoords(ISpace roomIn) {
//		Space room = new Space(roomIn);
		ISpace room = roomIn.copy();
		// generate a ranom set of coords
		ICoords c = randomizeCoords();
		if (c == EMPTY_COORDS) return EMPTY_SPACE;
		// center room using the random coords
		room.setCoords(c.add(-(room.getWidth()/2), 0, -(room.getDepth()/2)));
		return room;
	}

	/**
	 * 
	 * @param roomIn
	 * @return
	 */
	
	public ISpace buildSpace(ICoords startPoint, ISpace roomIn) {
		// randomize dimensions
		ISpace space = randomizeDimensions(roomIn);
		if (space == EMPTY_SPACE) return space;
		
		// randomize the coords
		space = randomizeCoords(space);
		if (space == EMPTY_SPACE) return space;
		
		// set the degrees (number of edges)
		space.setDegrees(RandomHelper.randomInt(random, 
				config.getDegrees().getMinInt(), 
				config.getDegrees().getMaxInt()));
		
		// randomize a direction
		space.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));

		return space;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ISpaceBuilder#buildStartSpace()
	 */
	@Override
	public ISpace buildStartSpace(ICoords startPoint) {
		/*
		 * the start of the level
		 */
		ISpace startSpace = new Space().setStart(true).setAnchor(true);
		startSpace = randomizeDimensions(startSpace);
		// ensure min dimensions are met for start room
		startSpace.setWidth(Math.max(ISpace.MIN_SPECIAL_WIDTH, startSpace.getWidth()));
		startSpace.setDepth(Math.max(ISpace.MIN_SPECIAL_DEPTH, startSpace.getDepth()));
		
		// ensure that start room's dimensions are odd in length
		if (startSpace.getWidth() % 2 == 0) startSpace.setWidth(startSpace.getWidth()+1);
		if (startSpace.getDepth() % 2 == 0) startSpace.setDepth(startSpace.getDepth()+1);
		
		// set the starting room coords to be in the middle of the start point
//		startSpace.setCoords(
//				new Coords(startPoint.getX()-(startSpace.getWidth()/2),
//						startPoint.getY(),
//						startPoint.getZ()-(startSpace.getDepth()/2)));
		startSpace.centerOn(startPoint);
		
		// randomize a direction
		startSpace.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));
		return startSpace;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ISpaceBuilder#buildEndSpace(java.util.List)
	 */
	@Override
	public ISpace buildEndSpace(ICoords startPoint, List<ISpace> predefinedSpaces) {
		/*
		 * the end room of the level.
		 */
	
		// build the end room
		ISpace endSpace  = buildPlannedSpace(startPoint, predefinedSpaces).setEnd(true).setAnchor(true);
		// ensure min dimensions are met for start room
		endSpace.setWidth(Math.max(ISpace.MIN_SPECIAL_WIDTH, endSpace.getWidth()));
		endSpace.setDepth(Math.max(ISpace.MIN_SPECIAL_DEPTH, endSpace.getDepth()));
		
		// ensure that the room's dimensions are odd in length
		if (endSpace.getWidth() % 2 == 0) endSpace.setWidth(endSpace.getWidth()+1);
		if (endSpace.getDepth() % 2 == 0) endSpace.setDepth(endSpace.getDepth()+1);
		
		return endSpace;
	}

	/**
	 * 
	 */
	@Override
	public ISpace buildTreasureSpace(ICoords startPoint, List<ISpace> predefinedSpaces) {
		final int SPACE_MIN_XZ = 10;
		final int SPACE_MIN_Y = 10;
		
		ISpace space = buildEndSpace(startPoint, predefinedSpaces)
				.setDegrees(1);
				space.getTags().add(SpaceTag.TREASURE);
		
		// ensure min dimensions are met for start room
		space.setWidth(Math.max(SPACE_MIN_XZ, space.getWidth()));
		space.setDepth(Math.max(SPACE_MIN_XZ, space.getDepth()));
		space.setHeight(Math.max(Math.min(SPACE_MIN_Y, config.getHeight().getMaxInt()),  space.getHeight()));
		return space;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ISpaceBuilder#buildSpace(java.util.List)
	 */
	@Override
	public ISpace buildPlannedSpace(ICoords startPoint, List<ISpace> predefinedSpaces) {
		ISpace predefinedSpace = new Space();		
		/* 
		 * check to make sure predefined rooms don't intersect.
		 * test up to 10 times for a successful position
		 */
		boolean checkSpaces = true;
		int endCheckIndex = 0;
		checkingSpaces:
		do {
			predefinedSpace = buildSpace(startPoint, predefinedSpace);
			if (predefinedSpace == EMPTY_SPACE) return predefinedSpace;
			logger.debug("New Planned Space:" + predefinedSpace);
			endCheckIndex++;
			if (endCheckIndex > 10) {
				logger.warn("Unable to position Planned Space that meets positional criteria.");
				return EMPTY_SPACE;
			}
			for (ISpace space : predefinedSpaces) {
				if (space.getXZBoundingBox().intersects(predefinedSpace.getXZBoundingBox())) {
					logger.debug("New Planned room intersects with predefined list room.");
					continue checkingSpaces;
				}
			}
			checkSpaces = false;			
		} while (checkSpaces);		
		return predefinedSpace;
	}

	/**
	 * @return the field
	 */
	@Override
	public Boundary getBoundary() {
		return boundary;
	}

	/**
	 * @param field the field to set
	 */
	@Override
	public void setBoundary(Boundary field) {
		this.boundary = field;
	}

	/**
	 * @return the config
	 */
	protected ILevelConfig getConfig() {
		return config;
	}

	/**
	 * @return the random
	 */
	protected Random getRandom() {
		return random;
	}
}