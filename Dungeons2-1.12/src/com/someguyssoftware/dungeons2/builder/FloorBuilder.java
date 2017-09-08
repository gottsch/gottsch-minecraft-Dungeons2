package com.someguyssoftware.dungeons2.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.model.AboveRoom;
import com.someguyssoftware.dungeons2.model.Dimensions;
import com.someguyssoftware.dungeons2.model.Floor;
import com.someguyssoftware.dungeons2.model.FloorConfig;
import com.someguyssoftware.dungeons2.model.IRoom;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.positional.Intersect;
import com.someguyssoftware.gottschcore.random.RandomHelper;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Builds a floor. Different from a LevelBuilder as this supports the building of above ground floors and rooms.
 * 
 * @author Mark Gottschling on Sep 7, 2017
 *
 */
public class FloorBuilder {
	private static final Logger logger = LogManager.getLogger(FloorBuilder.class);

	public static final Floor EMPTY_FLOOR = new Floor();
	public static final List<AboveRoom> EMPTY_ROOMS = new ArrayList<>();
	/*
	 * The coords that is used to calculate the force.
	 */
	private static final ICoords FORCE_SOURCE_COORDS = new Coords(0, 0, 0);
	/**
	 * 
	 */
	private static final double DEFAULT_FORCE_MODIFIER = 0.5;
	
	private FloorConfig config;
	
	/**
	 * 
	 */
	public FloorBuilder() {
		this.config = new FloorConfig();
	}
	
	/**
	 * 
	 * @param config
	 */
	public FloorBuilder(FloorConfig config) {
		this.config = config;
	}
	
	/**
	 * 
	 * @param rand
	 * @param startRoom
	 * @return
	 */
	public Floor build(final World world, final Random rand, IRoom startRoom) {
		return build(world, rand, startRoom, getConfig());
	}
	
	/**
	 * 
	 * @param rand
	 * @param startRoom
	 * @return
	 */
	public Floor build(final World world, final Random rand, IRoom startRoom, final FloorConfig config) {		
		/*
		 *  create a new floor
		 */
		Floor floor = new Floor();
		
		/*
		 * special rooms which are designed as <em>fixed position</em>. ex. ladder rooms, treasure rooms, boss rooms.
		 * these rooms' positions will typically be pre-determined in a location that meets all criteria.
		 * these rooms <em>will</em> be included in the resultant level.
		 */
		List<IRoom> anchors = new ArrayList<>();
		
		/*
		 * resultant list of buffered/spaced rooms on a single level.
		 */
		List<IRoom> rooms = null;
		
		// add the start room to the anchors list
		anchors.add(startRoom);
		
		// generate the other rooms in the floor
		List<IRoom> spawned = spawnRooms(rand, startRoom, config);
		
		// sort working array based on distance
		Collections.sort(spawned, IRoom.distanceComparator);
		
		// move apart any intersecting rooms (uses anti-grav method)
		rooms = applyDistanceBuffering(rand, startRoom.getCenter(), anchors, spawned, config);
		Dungeons2.log.debug("After Apply Distance Buffering Rooms.size=" + rooms.size());
		
		// TODO move rooms back towards center so there are no gaps
		
		// select rooms to use ie. filter out rooms that don't meet criteria
		rooms = selectValidRooms(world, rand, rooms, config);
		Dungeons2.log.debug("After select valid rooms Rooms.size=" + rooms.size());
		
		// add all the rooms to the floor
		floor.getRooms().addAll(rooms);
		return floor;
	}
	
	/**
	 * 
	 * @param rand
	 * @param startRoom
	 * @return
	 */
	protected List<IRoom> spawnRooms(final Random rand, final IRoom startRoom) {
		return spawnRooms(rand, startRoom, getConfig());
	}
	
	/**
	 * 
	 * @param random
	 * @param startPoint
	 * @param config
	 * @return
	 */
	protected List<IRoom> spawnRooms(final Random rand, final IRoom startRoom, final FloorConfig config) {
		List<IRoom> rooms = new ArrayList<>();

		int floorSize = RandomHelper.randomInt(rand, config.getNumberOfRooms().getMinInt(), config.getNumberOfRooms().getMaxInt());
		logger.info("# of rooms (floorSize):" + floorSize);
		// generate rooms
		for (int i = 0; i < floorSize; i++) {
			IRoom room = new AboveRoom(i);
			room = randomizeRoom(rand, room, startRoom, config);

			// add to the working list that contains all the rooms sorted on distance (farthest to closest)
			rooms.add(room);
		}
		return rooms;
	}
	
	/**
	 * 
	 * @param rand
	 * @param roomIn
	 * @param startPoint
	 * @param config
	 * @return
	 */
	protected IRoom randomizeRoom(final Random rand, final IRoom roomIn, final IRoom startRoom, final FloorConfig config) {
		AboveRoom room = new AboveRoom((AboveRoom)roomIn);
		
		// randomize dimensions
		Dimensions dims = randomizeDimensions(rand, config);
		room.setDimensions(dims);
		
		// randomize the rooms
		ICoords coords = randomizeRoomXZCoords(rand, room, config);
		// offset and center room from starting point
		room.setCoords(coords
				.add(startRoom.getCenter().getX() - (room.getDimensions().getWidth()/2), 
						startRoom.getCenter().getY(), startRoom.getCenter().getZ()-(room.getDimensions().getDepth()/2)));
		// calculate distance squared
		room.setDistance(room.getCenter().getDistanceSq(startRoom.getCenter()));

		// TODO calculate a direction opposite to startPoint
//		room.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));

		return room;
	}
	
	/**
	 * NOTE The y value is not set as the surface position still needs to be determined.
	 * @param random
	 * @param roomIn
	 * @param config
	 * @return
	 */
	protected ICoords randomizeRoomXZCoords(final Random random, final IRoom room, final FloorConfig config) {
		// generate a ranom set of coords
		ICoords c = randomizeXZCoords(random, config);
		// center room using the random coords
		c.add(-(room.getDimensions().getWidth()/2), 0, -(room.getDimensions().getDepth()/2));
		return c;
	}
	
	/**
	 * 
	 * @param random
	 * @param config
	 * @return
	 */
	protected ICoords randomizeXZCoords(final Random random, final FloorConfig config) {
		int x = RandomHelper.randomInt(random, config.getXDistance().getMinInt(), config.getXDistance().getMaxInt());
		int z = RandomHelper.randomInt(random, config.getZDistance().getMinInt(), config.getZDistance().getMaxInt());
		return new Coords(x, 0, z);
	}
	
	/**
	 * 
	 * @param rand
	 * @param roomIn
	 * @param config
	 * @return
	 */
	protected Dimensions randomizeDimensions(final Random rand, final FloorConfig config) {
		Dimensions dims = new Dimensions();
		dims.setWidth(Math.max(Room.MIN_WIDTH, RandomHelper.randomInt(rand, config.getWidth().getMinInt(), config.getWidth().getMaxInt())));
		dims.setDepth(Math.max(Room.MIN_DEPTH, RandomHelper.randomInt(rand, config.getDepth().getMinInt(), config.getDepth().getMaxInt())));
		dims.setHeight(Math.max(Room.MIN_HEIGHT, RandomHelper.randomInt(rand, config.getHeight().getMinInt(), config.getHeight().getMaxInt())));		
		return dims;
	}

	/**
	 * TODO move to IBuilder, AbstractBuilder taking in additional params for force etc
	 * @param rand
	 * @param startPoint
	 * @param anchors
	 * @param rooms
	 * @param config
	 * @return
	 */
	protected List<IRoom> applyDistanceBuffering(Random rand, ICoords startPoint, List<IRoom> anchors, List<IRoom> rooms, FloorConfig config) {
		List<IRoom> bufferedRooms = new ArrayList<>();
		/*
		 * a count of the number times a single room is processed against the list of buffered rooms
		 */
		int processCount = 0;

		// add anchors to buffereds
		bufferedRooms.addAll(anchors);

		/*
		 * process the room against all the rooms in buffered level to see if there is an overlap
		 */


		/*
		 * process all the unbuffered rooms that were added for this level
		 */
		rooms:
			for (IRoom room : rooms) {
				if (room.isReject()) {
					Dungeons2.log.info(String.format("Ignoring... room is flagged as rejected."));
					continue;
				}
				processCount = 0;
				AxisAlignedBB roomBB = room.getXZBoundingBox();

				if (bufferedRooms != null && bufferedRooms.size() > 0) {

					boolean processBufferedRooms = true;
					bufferedRooms:					
						while(processBufferedRooms) {
							// increment the process count
							processCount++;
							if (processCount > 50) {
								Dungeons2.log.trace("Detected endless loop when positioning room ==> room REJECTED.");
								room.setReject(true);
								continue rooms;
							}
							for (IRoom bufferedRoom : bufferedRooms) {
								AxisAlignedBB bufferedBB = bufferedRoom.getXZBoundingBox();

								// test if intersect
								int failSafeCount = 0;

								while (roomBB.intersects(bufferedBB)) {
									Dungeons2.log.info(String.format("Room[%d] intersects with processed room[%d]: %s; %s", room.getID(), bufferedRoom.getID(), roomBB, bufferedBB));
									// testing whether room is anchored
									if (room.isAnchor()) {
										Dungeons2.log.info("Room is anchored. Remove from level as it can not change position.");
										room.setReject(true);
										continue rooms;
									}
									
									/* determine vector from start point.
									 * this produces an "explosion" vector moving away from the start (epicenter).
									 * ** seems to take more processing cycles as there are still overlaps after one round
									 */
									double angle = room.getCenter().getXZAngle(startPoint);
//									Dungeons2.log.info(String.format("Calculating angle from %s to %s", room.getCenter(), bufferedRoom.getCenter()));
//									double angle = room.getCenter().getXZAngle(bufferedRoom.getCenter());


									/*
									 * this produces an anti-grav vector moving away from the intersected room.
									 * ** seems to work with minimal cycles
									 * NOTE this could process and endless cycle if the room insects two other rooms which just push
									 * back and forth.
									 */
//									 double angle = room.getCenter().getXZAngle(bufferedRoom.getCenter());
									 Dungeons2.log.info("Angle: " + angle);

									// determine force - relative distance from 0,0,0 to difference in overlap
									Intersect intersect = Intersect.getIntersect2(roomBB, bufferedBB);
									Dungeons2.log.debug("intersect: " + intersect);
									// TODO here add the min(x,z) of the insect value to the room instead of calculating a force
									// ie if x > z intersect then move in the x direction
//									int adjustAmount = Math.max(0, Math.min(intersect, b));
									
									/**
									 * Calculate a force value that is equals to the distance from 0 to the amount of intersection between the two rooms being compared.
									 * Add an additional 10% of force  applied to a caridnal directions helps ensure that the amount of adjustment is more than the amount of intersect.  
									 */
									double force = FORCE_SOURCE_COORDS.getDistance(intersect.getX(), 0, intersect.getZ());// * DEFAULT_FORCE_MODIFIER;
									double xForce = Math.sin(angle) * force;
							        double zForce = Math.cos(angle) * force;							        
							        Dungeons2.log.info("xForce:" + xForce);
							        Dungeons2.log.info("zForce:" + zForce);
							        
//							        Dungeons2.log.info("Before Move:" + room.getCoords().toShortString());
									// apply force vector to room
									room = addXZForce(room, angle, force);
//									Dungeons2.log.info("After Move:" + room.getCoords().toShortString());
									
									// update distance
									room.setDistance(room.getCenter().getDistanceSq(startPoint));
									roomBB = room.getXZBoundingBox();

									// check again if still intersect
									if (roomBB.intersects(bufferedBB)) {
										failSafeCount++;
										if (failSafeCount >= 5) {
											// stop processing this room (ie drop altogether)
											Dungeons2.log.info("Unable to position room... rejecting room.");
											room.setReject(true);
											continue rooms;
										}
									}
									else {
										// no longer intersects; will exit the while loop
										continue bufferedRooms;
									}
								}
								// reset the fail safe
								failSafeCount = 0;
							}
							/*
							 * flag the endless while loop to stop looping.
							 * this is the typical action to take when all the buffered rooms have been processed.
							 */
							processBufferedRooms = false;
						}
				}
				// add to the level list
				bufferedRooms.add(room);
			}

		return bufferedRooms;
	}
	
	/**
	 * LevelVisualizer against all build criteria. Rooms that don't meet criteria are removed from the list.
	 * @param rand
	 * @param rooms
	 * @param config
	 * @return
	 */
	protected List<IRoom> selectValidRooms(World world, Random rand, List<IRoom> rooms, FloorConfig config) {
		List<IRoom> met = new ArrayList<>();
		int roomId = 0;

		for (IRoom room : rooms) {

			// NOTE at this point it is assumed any anchors are pre-validated and meet all criteria
			if (room.isAnchor()) {
				room.setID(roomId++);
				met.add(room);
				continue;
			}

			// check if room meets all criteria/constraints for generation
			boolean isValid = meetsRoomConstraints(world, room, config);
			
			if (isValid) {
				// assign a new id to room
				room.setID(roomId++);
				// add room
				met.add(room);			
			}
		}
		return met;
	}
	
	/**
	 * Ensure the room meets are criteria to be built.
	 * @param world 
	 * @param room
	 * @return
	 */
	protected boolean meetsRoomConstraints(World world, IRoom room, FloorConfig config) {
		if (room == null || room.isReject()) return false;
		if (!config.isMinecraftConstraintsOn()) return true;

		// get percentage of solid base blocks
		double percentSolid = WorldInfo.getSolidBasePercent(world, room.getCoords(), room.getDimensions().getWidth(), room.getDimensions().getDepth());
//		Dungeons2.log.debug("Percent solid base:" + percentSolid);

		// TODO test the base if it meets criteria 40% ground %50 air
	
		return true;
	}
	
	/**
	 * TODO add to interface / abstract
	 * @param roomIn
	 * @param angle
	 * @param force
	 * @return
	 */
	public IRoom addXZForce(IRoom roomIn, double angle, double force) {
		double xForce = Math.sin(angle) * force;
        double zForce = Math.cos(angle) * force;
        
        IRoom room = roomIn.copy();
        room.setCoords(room.getCoords().add((int)xForce, 0, (int)zForce));
        return room;
	}
	
	/**
	 * @return the config
	 */
	public FloorConfig getConfig() {
		return config;
	}

	/**
	 * @param config the config to set
	 */
	public void setConfig(FloorConfig config) {
		this.config = config;
	}
}
