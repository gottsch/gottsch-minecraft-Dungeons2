/**
 * 
 */
package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.dungeonsengine.enums.RoomTag;
import com.someguyssoftware.dungeonsengine.model.Boundary;
import com.someguyssoftware.dungeonsengine.model.IRoom;
import com.someguyssoftware.dungeonsengine.model.Room;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

/**
 * @author Mark Gottschling on Sep 22, 2018
 *
 */
public class RoomBuilder implements IRoomBuilder {
	public static Logger logger = LogManager.getLogger("DungeonsEngine");
	
	public static final IRoom EMPTY_ROOM = new Room();	
	public static final ICoords EMPTY_COORDS = new Coords(0, 0, 0);
	
	private ILevelConfig config;
	private Random random;
	private Boundary boundary;
	
	/**
	 * 
	 * @param boundary
	 */
	public RoomBuilder(Random random, Boundary boundary, ILevelConfig config) {
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
	public IRoom randomizeDimensions(IRoom spaceIn) {
		IRoom space = spaceIn.copy();
		space.setWidth(Math.max(IRoom.MIN_WIDTH, RandomHelper.randomInt(random, config.getWidth().getMinInt(), config.getWidth().getMaxInt())));
		space.setDepth(Math.max(IRoom.MIN_DEPTH, RandomHelper.randomInt(random, config.getDepth().getMinInt(), config.getDepth().getMaxInt())));
		space.setHeight(Math.max(IRoom.MIN_HEIGHT, RandomHelper.randomInt(random, config.getHeight().getMinInt(), config.getHeight().getMaxInt())));		
		return space;
	}

//	/**
//	 * 
//	 * @param random
//	 * @param field
//	 * @param config
//	 * @return
//	 */
//	public ICoords randomizeCoords() {
//		int x = RandomHelper.randomInt(random, 0, (int) (boundary.getMaxCoords().getX() - boundary.getMinCoords().getX()));
////		int y = RandomHelper.randomInt(random, config.getYVariance().getMinInt(), config.getYVariance().getMaxInt());
//		int z = RandomHelper.randomInt(random, 0, (int) (boundary.getMaxCoords().getZ() - boundary.getMinCoords().getZ()));
//		return new Coords((int)boundary.getMinCoords().getX(), 0, (int)boundary.getMinCoords().getZ()).add(x, 0, z);
//	}
	
	/**
	 * 
	 * @param random
	 * @param field
	 * @param config
	 * @param roomIn
	 * @return
	 */
	public IRoom randomizeCoords(IRoom roomIn) {
//		Space room = new Space(roomIn);
		IRoom room = roomIn.copy();
		// generate a ranom set of coords
		ICoords c = GenUtil.randomizeCoords(this.random, this.boundary);
		if (c == EMPTY_COORDS) return EMPTY_ROOM;
		// center room using the random coords
		room.setCoords(c.add(-(room.getWidth()/2), 0, -(room.getDepth()/2)));
		return room;
	}

	/**
	 * 
	 * @param roomIn
	 * @return
	 */
	
	public IRoom buildRoom(ICoords startPoint, IRoom roomIn) {
		// randomize dimensions
		IRoom room = randomizeDimensions(roomIn);
		if (room == EMPTY_ROOM) return room;
		
		// randomize the coords
		room = randomizeCoords(room);
		if (room == EMPTY_ROOM) return room;
		
		// set the degrees (number of edges)
		room.setDegrees(RandomHelper.randomInt(random, 
				config.getDegrees().getMinInt(), 
				config.getDegrees().getMaxInt()));
		
		// randomize a direction
		room.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));

		return room;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ISpaceBuilder#buildStartSpace()
	 */
	@Override
	public IRoom buildStartRoom(ICoords startPoint) {
		/*
		 * the start of the level
		 */
		IRoom start = new Room().setStart(true).setAnchor(true);
		start = randomizeDimensions(start);
		// ensure min dimensions are met for start room
		start.setWidth(Math.max(IRoom.MIN_SPECIAL_WIDTH, start.getWidth()));
		start.setDepth(Math.max(IRoom.MIN_SPECIAL_DEPTH, start.getDepth()));
		
		// ensure that start room's dimensions are odd in length
		if (start.getWidth() % 2 == 0) start.setWidth(start.getWidth()+1);
		if (start.getDepth() % 2 == 0) start.setDepth(start.getDepth()+1);
		
		// set the starting room coords to be in the middle of the start point
//		startSpace.setCoords(
//				new Coords(startPoint.getX()-(startSpace.getWidth()/2),
//						startPoint.getY(),
//						startPoint.getZ()-(startSpace.getDepth()/2)));
		start.centerOn(startPoint);
		
		// randomize a direction
		start.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));
		return start;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.builder.ISpaceBuilder#buildEndSpace(java.util.List)
	 */
	@Override
	public IRoom buildEndRoom(ICoords startPoint, List<IRoom> predefinedSpaces) {
		/*
		 * the end room of the level.
		 */
	
		// build the end room
		IRoom end  = buildPlannedRoom(startPoint, predefinedSpaces).setEnd(true).setAnchor(true);
		// ensure min dimensions are met for start room
		end.setWidth(Math.max(IRoom.MIN_SPECIAL_WIDTH, end.getWidth()));
		end.setDepth(Math.max(IRoom.MIN_SPECIAL_DEPTH, end.getDepth()));
		
		// ensure that the room's dimensions are odd in length
		if (end.getWidth() % 2 == 0) end.setWidth(end.getWidth()+1);
		if (end.getDepth() % 2 == 0) end.setDepth(end.getDepth()+1);
		
		return end;
	}

	/**
	 * 
	 */
	@Override
	public IRoom buildTreasureRoom(ICoords startPoint, List<IRoom> predefinedSpaces) {
		final int SPACE_MIN_XZ = 10;
		final int SPACE_MIN_Y = 10;
		
		IRoom space = buildEndRoom(startPoint, predefinedSpaces)
				.setDegrees(1);
				space.getTags().add(RoomTag.TREASURE);
		
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
	public IRoom buildPlannedRoom(ICoords startPoint, List<IRoom> predefinedSpaces) {
		IRoom predefinedSpace = new Room();		
		/* 
		 * check to make sure predefined rooms don't intersect.
		 * test up to 10 times for a successful position
		 */
		boolean checkSpaces = true;
		int endCheckIndex = 0;
		checkingSpaces:
		do {
			predefinedSpace = buildRoom(startPoint, predefinedSpace);
			if (predefinedSpace == EMPTY_ROOM) return predefinedSpace;
			logger.debug("New Planned Space:" + predefinedSpace);
			endCheckIndex++;
			if (endCheckIndex > 10) {
				logger.warn("Unable to position Planned Space that meets positional criteria.");
				return EMPTY_ROOM;
			}
			for (IRoom space : predefinedSpaces) {
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
