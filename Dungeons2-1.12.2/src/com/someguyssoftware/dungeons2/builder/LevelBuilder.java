/**
 * 
 */
package com.someguyssoftware.dungeons2.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Stack;
import java.util.Vector;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.graph.Wayline;
import com.someguyssoftware.dungeons2.graph.Waypoint;
import com.someguyssoftware.dungeons2.graph.mst.Edge;
import com.someguyssoftware.dungeons2.graph.mst.EdgeWeightedGraph;
import com.someguyssoftware.dungeons2.graph.mst.LazyPrimMST;
import com.someguyssoftware.dungeons2.model.Door;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.model.Room.Type;
import com.someguyssoftware.dungeons2.model.Shaft;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.enums.Alignment;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.enums.Rotate;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.positional.Intersect;
import com.someguyssoftware.gottschcore.random.RandomHelper;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import io.github.jdiemke.triangulation.DelaunayTriangulator;
import io.github.jdiemke.triangulation.NotEnoughPointsException;
import io.github.jdiemke.triangulation.Triangle2D;
import io.github.jdiemke.triangulation.Vector2D;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;


/**
 * @author Mark Gottschling on Jul 9, 2016
 * @version 2.0
 */
public class LevelBuilder {
	/**
	 * 
	 */
	private static final double DEFAULT_FORCE_MODIFIER = 0.85;

	/**
	 * TODO should this be with or without walls?
	 */
	public static final int MIN_HORIZONTAL_DIMENSION = 3;
	/**
	 * 
	 */
	public static final int MIN_VERTICAL_DIMENSION = 2;

	/**
	 * Minimum level size
	 */
	public static final int MIN_NUMBER_OF_ROOMS = 5;
	
	/*
	 * The coords that is used to calculate the force.
	 */
	private static final ICoords FORCE_SOURCE_COORDS = new Coords(0, 0, 0);

	public static final Room EMPTY_ROOM = new Room();
	
	/*
	 * empty level
	 */
	public static final Level EMPTY_LEVEL = new Level();

	public static final List<Room> EMPTY_ROOMS = new ArrayList<>();

	public static final List<Wayline> EMPTY_WAYLINES = new ArrayList<>();

	public static final Shaft EMPTY_SHAFT = new Shaft();

	private static final int MIN_START_ROOM_SIZE = 7;

	/**
	 * @since 2.0
	 */
	private IDungeonBuilder dungeonBuilder; 
	
	@Deprecated
	private LevelConfig config;
	
	private AxisAlignedBB field;
	private AxisAlignedBB roomBoundary;
	
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
	public LevelBuilder() {
		this.config = new LevelConfig();
	}
	
	/**
	 * 
	 * @param config
	 */
	public LevelBuilder(LevelConfig config) {
		this.config = config;
	}

	/**
	 * TODO this doesn't belong in LevelBuilder but should be part of Boundary class
	 * @param boundary
	 * @return
	 */
	public AxisAlignedBB resizeBoundary(final AxisAlignedBB boundary, final double factor) {
		/*
		 * AxisAlignedBB.getCenter() is @ClientSide so must calculate the center
		 */
//		Vec3d center = new Vec3d(boundary.minX + (boundary.maxX - boundary.minX) * 0.5D, boundary.minY + (boundary.maxY - boundary.minY) * 0.5D, boundary.minZ + (boundary.maxZ - boundary.minZ) * 0.5D);
//		AxisAlignedBB newBoundary = new AxisAlignedBB(new BlockPos(center)).grow(30);
//		
		// resize field
		if (factor < 1.0D) {
			int xAmount = (int) (((boundary.maxX - boundary.minX) * (1.0 - factor)) / 2);
			int zAmount = (int) (((boundary.maxZ - boundary.minZ) * (1.0 - factor)) / 2);
			AxisAlignedBB newBoundary = boundary.grow(-xAmount, 0, -zAmount);
			Dungeons2.log.debug("boundary shrunk by -> {} {}, to new size -> {}", xAmount, zAmount, newBoundary);
			return newBoundary;
		}
		else return boundary;
	}
	
	/**
	 * 
	 * @param random
	 * @param startPoint
	 * @param config
	 * @return
	 */
	@Deprecated
	protected List<Room> spawnRooms(Random rand, ICoords startPoint, LevelConfig config) {
		List<Room> rooms = new ArrayList<>();

		int levelSize = Math.max(
				MIN_NUMBER_OF_ROOMS,
				RandomHelper.randomInt(rand, config.getNumberOfRooms().getMinInt(), config.getNumberOfRooms().getMaxInt())
			);
		
		// generate rooms
		for (int i = 0; i < levelSize; i++) {
			Room room = new Room(i);
			// TODO where should the min width check take place?? - inside the setWidth, setDepth of LevelConfig
			room = randomizeRoom(rand, room, startPoint /*, xOffset, zOffset, */, config);

			// add to the working list that contains all the rooms sorted on distance (farthest to closest)
			rooms.add(room);
		}
		
		return rooms;
	}

	/**
	 * 
	 * @param rand
	 * @param field
	 * @param startPoint
	 * @param config
	 * @return
	 */
	@Deprecated
	protected List<Room> spawnRooms(Random rand, AxisAlignedBB field, ICoords startPoint, LevelConfig config) {
		List<Room> rooms = new ArrayList<>();

		int levelSize = Math.max(
				MIN_NUMBER_OF_ROOMS,
				RandomHelper.randomInt(rand, config.getNumberOfRooms().getMinInt(), config.getNumberOfRooms().getMaxInt())
				);

		// generate rooms
		for (int i = 0; i < levelSize; i++) {
			Room room = new Room(i);
			// TODO where should the min width check take place?? - inside the setWidth, setDepth of LevelConfig
			//				room = randomizeRoom(rand, room, startPoint /*, xOffset, zOffset, */, config);
			room = randomizeRoom(rand, room, field, startPoint, config);
			// add to the working list that contains all the rooms sorted on distance (farthest to closest)
			rooms.add(room);
		}

		return rooms;
	}

	protected List<Room> spawnRooms(Random rand, AxisAlignedBB field, ICoords startPoint, ILevelConfig config) {
		List<Room> rooms = new ArrayList<>();

		int levelSize = Math.max(
				MIN_NUMBER_OF_ROOMS,
				RandomHelper.randomInt(rand, config.getNumRooms().getMinInt(), config.getNumRooms().getMaxInt())
				);

		// generate rooms
		for (int i = 0; i < levelSize; i++) {
			Room room = new Room(i);
			// TODO where should the min width check take place?? - inside the setWidth, setDepth of LevelConfig
			//			room = randomizeRoom(rand, room, startPoint /*, xOffset, zOffset, */, config);
			room = randomizeRoom(rand, room, field, startPoint, config);
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
	protected Room randomizeRoom(Random rand, Room roomIn, ICoords startPoint, LevelConfig config) {
		// randomize dimensions
		Room room = randomizeDimensions(rand, roomIn, config);

		// randomize the rooms
		room = randomizeRoomCoords(rand, room, config);
//		logger.info("room: " + room);
		// offset and center room from starting point
		room.setCoords(room.getCoords().add(startPoint.getX() - (room.getWidth()/2), startPoint.getY(), startPoint.getZ()-(room.getDepth()/2)));
		// calculate distance squared
		room.setDistance(room.getCenter().getDistanceSq(startPoint));
		// set the degrees (number of edges)
		room.setDegrees(RandomHelper.randomInt(rand, config.getDegrees().getMinInt(), config.getDegrees().getMaxInt()));
		// randomize a direction
		room.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));

		return room;
	}

	/**
	 * 
	 * @param rand
	 * @param roomIn
	 * @param field
	 * @param startPoint
	 * @param config
	 * @return
	 */
	protected Room randomizeRoom(Random rand, Room roomIn, AxisAlignedBB field, 
			ICoords startPoint, ILevelConfig config) {
		// randomize dimensions
		Room room = randomizeDimensions(rand, roomIn, config);

		// randomize the rooms
//		room = randomizeRoomCoords(rand, room, config);
		room = randomizeRoomCoords(rand, room, field, config);
//		logger.info("room: " + room);
		// offset and center room from starting point
//		room.setCoords(room.getCoords().add(startPoint.getX() - (room.getWidth()/2), startPoint.getY(), startPoint.getZ()-(room.getDepth()/2)));
		// reset the room y position
		room.setCoords(room.getCoords().resetY(startPoint.getY()));
		// calculate distance squared
		room.setDistance(room.getCenter().getDistanceSq(startPoint));
		// set the degrees (number of edges)
		room.setDegrees(RandomHelper.randomInt(rand, config.getDegrees().getMinInt(), config.getDegrees().getMaxInt()));
		// randomize a direction
		room.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));

		return room;
	}

	@Deprecated
	protected Room randomizeRoom(Random rand, Room roomIn, AxisAlignedBB field, ICoords startPoint, LevelConfig config) {
		// randomize dimensions
		Room room = randomizeDimensions(rand, roomIn, config);

		// randomize the rooms
//		room = randomizeRoomCoords(rand, room, config);
		room = randomizeRoomCoords(rand, room, field, config);
//		logger.info("room: " + room);
		// offset and center room from starting point
//		room.setCoords(room.getCoords().add(startPoint.getX() - (room.getWidth()/2), startPoint.getY(), startPoint.getZ()-(room.getDepth()/2)));
		// reset the room y position
		room.setCoords(room.getCoords().resetY(startPoint.getY()));
		// calculate distance squared
		room.setDistance(room.getCenter().getDistanceSq(startPoint));
		// set the degrees (number of edges)
		room.setDegrees(RandomHelper.randomInt(rand, config.getDegrees().getMinInt(), config.getDegrees().getMaxInt()));
		// randomize a direction
		room.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));

		return room;
	}

	/**
	 * 
	 * @param random
	 * @param roomIn
	 * @param config
	 * @return
	 */
	protected Room randomizeRoomCoords(Random random, Room roomIn, /*int xOffset, int zOffset,*/ LevelConfig config) {
		Room room = new Room(roomIn);
		// generate a ranom set of coords
		ICoords c = randomizeCoords(random, /*xOffset, zOffset,*/ config);
		// center room using the random coords
		room.setCoords(c.add(-(room.getWidth()/2), 0, -(room.getDepth()/2)));
		
		// Y Variance
//		room.getCoords().setY(RandomHelper.randomInt(random, config.getYVariance().getMinInt(), config.getYVariance().getMaxInt()));
		return room;
	}

	/**
	 * 
	 * @param random
	 * @param roomIn
	 * @param field
	 * @param config
	 * @return
	 */
	protected Room randomizeRoomCoords(Random random, Room roomIn, AxisAlignedBB field, ILevelConfig config) {
		Room room = new Room(roomIn);
		// generate a ranom set of coords
		ICoords c = randomizeCoords(random, field);
		// center room using the random coords
//		room.setCoords(c.add(-(room.getWidth()/2), 0, -(room.getDepth()/2)));
		room.centerOn(c);
		return room;
	}

	
	/**
	 * 
	 * @param random
	 * @param roomIn
	 * @param config
	 * @return
	 */
	@Deprecated
	protected Room randomizeRoomCoords(Random random, Room roomIn, AxisAlignedBB field, LevelConfig config) {
		Room room = new Room(roomIn);
		// generate a ranom set of coords
		ICoords c = randomizeCoords(random, field, config);
		// center room using the random coords
		room.setCoords(c.add(-(room.getWidth()/2), 0, -(room.getDepth()/2)));
		
		// Y Variance
//		room.getCoords().setY(RandomHelper.randomInt(random, config.getYVariance().getMinInt(), config.getYVariance().getMaxInt()));
		return room;
	}
	
	/**
	 * 
	 * @param random
	 * @param config
	 * @return
	 */
	protected ICoords randomizeCoords(Random random, /*int xOffset, int zOffset,*/ LevelConfig config) {
		int x = RandomHelper.randomInt(random, config.getXDistance().getMinInt(), config.getXDistance().getMaxInt());// - xOffset;
		int y = RandomHelper.randomInt(random, config.getYVariance().getMinInt(), config.getYVariance().getMaxInt());
		int z = RandomHelper.randomInt(random, config.getZDistance().getMinInt(), config.getZDistance().getMaxInt());// - zOffset;
		return new Coords(x, y, z);
	}
	
	/**
	 * 
	 * @param random
	 * @param field
	 * @param config
	 * @return
	 */
	protected ICoords randomizeCoords(Random random, AxisAlignedBB field, LevelConfig config) {
		int x = RandomHelper.randomInt(random, 0, (int) (field.maxX - field.minX));
		int y = RandomHelper.randomInt(random, 0, (int) (field.maxY - field.minY));
		int z = RandomHelper.randomInt(random, 0, (int) (field.maxZ - field.minZ));
		return new Coords((int)field.minX, (int)field.minY, (int)field.minZ).add(x, y, z);
	}
	
	protected ICoords randomizeCoords(Random random, AxisAlignedBB field) {
		int x = RandomHelper.randomInt(random, 0, (int) (field.maxX - field.minX));
		int y = RandomHelper.randomInt(random, 0, (int) (field.maxY - field.minY));
		int z = RandomHelper.randomInt(random, 0, (int) (field.maxZ - field.minZ));
		return new Coords((int)field.minX, (int)field.minY, (int)field.minZ).add(x, y, z);
	}
	
	/**
	 * 
	 * @param rand
	 * @param roomIn
	 * @param config
	 * @return
	 */
	protected Room randomizeDimensions(Random rand, Room roomIn, ILevelConfig config) {
		Room room = new Room(roomIn);
		room.setWidth(Math.max(Room.MIN_WIDTH, RandomHelper.randomInt(rand, config.getWidth().getMinInt(), config.getWidth().getMaxInt())));
		room.setDepth(Math.max(Room.MIN_DEPTH, RandomHelper.randomInt(rand, config.getDepth().getMinInt(), config.getDepth().getMaxInt())));
		room.setHeight(Math.max(Room.MIN_HEIGHT, RandomHelper.randomInt(rand, config.getHeight().getMinInt(), config.getHeight().getMaxInt())));		
		return room;
	}
	
	/**
	 * 
	 * @param rand
	 * @param roomIn
	 * @param config
	 * @return
	 */
	@Deprecated
	protected Room randomizeDimensions(Random rand, Room roomIn, LevelConfig config) {
		Room room = new Room(roomIn);
		room.setWidth(Math.max(Room.MIN_WIDTH, RandomHelper.randomInt(rand, config.getWidth().getMinInt(), config.getWidth().getMaxInt())));
		room.setDepth(Math.max(Room.MIN_DEPTH, RandomHelper.randomInt(rand, config.getDepth().getMinInt(), config.getDepth().getMaxInt())));
		room.setHeight(Math.max(Room.MIN_HEIGHT, RandomHelper.randomInt(rand, config.getHeight().getMinInt(), config.getHeight().getMaxInt())));		
		return room;
	}

	/**
	 * 
	 * @param rand
	 * @param startPoint
	 * @param anchors
	 * @param rooms
	 * @param config
	 * @return
	 */
	protected List<Room> applyDistanceBuffering(Random rand, ICoords startPoint, final List<Room> anchors, final List<Room> rooms/*, LevelConfig config*/) {
		List<Room> bufferedRooms = new ArrayList<>();
		/*
		 * a count of the number times a single room is processed against the list of buffered rooms
		 */
		int processCount = 0;

		// add anchors to buffereds
		bufferedRooms.addAll(anchors);
//		int i = 0;
		/*
		 * process the room against all the rooms in buffered level to see if there is an overlap
		 */
//		logger.info("Rooms.size:" + rooms.size());

		/*
		 * process all the unbuffered rooms that were added for this level
		 */
		rooms:
			for (Room room : rooms) {
				if (room.isReject()) {
//					Dungeons2.log.info(String.format("Ignoring... room is flagged as rejected."));
					incrementLossToDistanceBuffering(1);
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
								incrementLossToDistanceBuffering(1);
								continue rooms;
							}
							for (Room bufferedRoom : bufferedRooms) {
								//Room bufferedRoom = bufferedRooms.get(bufferedRoomIndex);
//																Dungeons2.log.info("\n-------------------\nTesting against processed room: " + bufferedRoom.getId());
								AxisAlignedBB bufferedBB = bufferedRoom.getXZBoundingBox();

								// test if intersect
								int failSafeCount = 0;

								while (roomBB.intersects(bufferedBB)) {
									// TODO need to ensure that an endless loop doesn't occur of a room toggling back and forth

//									Dungeons2.log.info("Room intersects with processed room:" + roomBB + "; " + bufferedBB);
									// testing whether room is anchored
									if (room.isAnchor()) {
										Dungeons2.log.info("Room is anchored. Remove from level as it can not change position.");
										room.setReject(true);
										incrementLossToDistanceBuffering(1);
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
//									 Dungeons2.log.info("Angle: " + angle);

									// determine force - relative distance from 0,0,0 to difference in overlap
									Intersect intersect = Intersect.getIntersect(roomBB, bufferedBB);
//									Dungeons2.log.info("intersect:" + intersect);

									/**
									 * Calculate a force value that is equals to the distance from 0 to the amount of intersection between the two rooms being compared.
									 * Add an additional 10% of force  applied to a caridnal directions helps ensure that the amount of adjustment is more than the amount of intersect.  
									 */
									double force = FORCE_SOURCE_COORDS.getDistance(intersect.getX(), 0, intersect.getZ()) * DEFAULT_FORCE_MODIFIER;
//									Dungeons2.log.info("Force:" + force);

									double xForce = Math.sin(angle) * force;
							        double zForce = Math.cos(angle) * force;
							        
//							        Dungeons2.log.info("xForce:" + xForce);
//							        Dungeons2.log.info("zForce:" + zForce);
									// apply force vector to room
									room = room.addXZForce(angle, force);
									// update distance
									room.setDistance(room.getCenter().getDistanceSq(startPoint));
									roomBB = room.getXZBoundingBox();
									//									Dungeons2.log.info("New Room:" +  room);

									// check again if still intersect
									if (roomBB.intersects(bufferedBB)) {
										//										Dungeons2.log.info("Still intersects! Moving again ...");
										failSafeCount++;
										if (failSafeCount >= 5) {
											// stop processing this room (ie drop altogether)
//											Dungeons2.log.info("Unable to position room... rejecting room.");
											room.setReject(true);
											incrementLossToDistanceBuffering(1);
											continue rooms;
										}
									}
									else {
										// no longer intersects; will exit the while loop
										// test against all buffered rooms again to ensure that it doesn't intersect another buffered room now
										//										Dungeons2.log.info("No more intersection... testing against all buffered rooms again.");
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
//				Dungeons2.log.info(i + "] Adding room "+ room.getId());
				bufferedRooms.add(room);
//				i++;
			}
//		Dungeons2.log.info("BufferedRooms.size=:" + bufferedRooms.size());
		return bufferedRooms;
	}

	/**
	 * 
	 * @param sourceLevel the originating level to join from. This level owns the shaft that is generated.
	 * @param destLevel the destination level to join to.
	 * @return
	 */
	public Shaft join(Level sourceLevel, Level destLevel) {
		Shaft shaft = EMPTY_SHAFT;
		
		List<Room> destRooms = destLevel.getRooms().stream().filter(room -> room.isEnd()).collect(Collectors.toList());
		List<Room> sourceRooms = sourceLevel.getRooms().stream().filter(room -> room.isStart()).collect(Collectors.toList());
		Dungeons2.log.debug("destRooms.size=" + destRooms.size());
		Dungeons2.log.debug("sourceRooms.size=" + sourceRooms.size());
		
		// TODO this line should throw and error
		// check if either list is null
		if (destRooms == null || sourceRooms == null || destRooms.size() == 0 || sourceRooms.size() == 0) return shaft;
		
		Room destRoom = destRooms.get(0);
		Room sourceRoom = sourceRooms.get(0);
		Dungeons2.log.debug("destRoom: " + destRoom);
		Dungeons2.log.debug("sourceRoom: " + sourceRoom);
		
		shaft =  join(sourceRoom, destRoom);
		Dungeons2.log.debug("shaft: " + shaft);
		// add the shaft to the list
		if (shaft != EMPTY_SHAFT) {
			sourceLevel.getShafts().add(shaft);
		}
		
		return shaft;
	}
	
	/**
	 * TODO needs to throw an error if errors
	 * @param sourceRoom
	 * @param destRoom
	 * @return
	 */
	public Shaft join(Room sourceRoom, Room destRoom) {
		Shaft shaft = EMPTY_SHAFT;
		// built the shaft from start room (-1) to end room (+0)
		if (destRoom.getMinY() - sourceRoom.getMaxY() > 1) {
			ICoords center = destRoom.getCenter();
			Dungeons2.log.debug("center of dest room: " + center);

			shaft = (Shaft) new Shaft()
				.setDirection(sourceRoom.getDirection())
				.setDegrees(0)
				.setType(Type.LADDER);

			shaft.setWidth(3)
				.setDepth(3)
				.setHeight(destRoom.getMinY() - sourceRoom.getMaxY()-1);
			shaft.setParent(sourceRoom);
			
			// set the coords - it depends on the direction the room is facing
			switch(shaft.getDirection()) {
			case NORTH:
				shaft.setCoords(new Coords(center.getX()-1, sourceRoom.getMaxY()+1, center.getZ()));
				break;
			case EAST:
				shaft.setCoords(new Coords(center.getX()-2, sourceRoom.getMaxY()+1, center.getZ()-1));
				break;
			case SOUTH:
				shaft.setCoords(new Coords(center.getX()-1, sourceRoom.getMaxY()+1, center.getZ()-2));
				break;
			case WEST:
				shaft.setCoords(new Coords(center.getX(), sourceRoom.getMaxY()+1, center.getZ()-1));
				break;
			default:
			}			
		}	
		Dungeons2.log.debug("shaft: " + shaft);
		return shaft;
	}
	
	/**
	 * 
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @return
	 */
	public Level build(World world, Random rand, ICoords startPoint) {
		return build(world, rand, startPoint, this.config);
	}
	
	/**
	 * Minecraft starts coordinates in the top left, postive growing to the right (east) and down (south).
	 * Therefor:
	 * quadrant 1 = bottom right,
	 * quadrant 2 = bottom left,
	 * quadrant 3 = top left,
	 * quadrant 4 = top right
	 * @param config
	 * @return
	 */
	public Level build(World world, Random rand, ICoords startPoint, LevelConfig config) {
		/*
		 * special rooms which are designed as <em>fixed position</em>. ex. ladder rooms, treasure rooms, boss rooms.
		 * these rooms' positions will typically be pre-determined in a location that meets all criteria.
		 * these rooms <em>will</em> be included in the resultant level.
		 */
//		List<Room> anchors = new ArrayList<>();

		/*
		 * a list of manualy/pre-generated rooms to be used in the level
		 */
		List<Room> predefinedRooms = new ArrayList<>();
		
		/*
		 * the start of the level
		 */
		Room startRoom = buildStartRoom(world, rand, getBoundary(), startPoint, config);
		if (startRoom == EMPTY_ROOM) {
			if (Dungeons2.log.isWarnEnabled()) {
				Dungeons2.log.warn(String.format("Start Room has invalid Minecraft world room conditions: %s", startRoom.toString()));
			}
			return EMPTY_LEVEL;
		}
		predefinedRooms.add(startRoom);
		
		/*
		 * the end room of the level.
		 * only one way into the end room - only if boss/treasure room
		 */
		Room endRoom = buildEndRoom(world, rand, getBoundary(), startPoint, predefinedRooms, config);
		if (endRoom == EMPTY_ROOM) {
			return EMPTY_LEVEL;
		}
		predefinedRooms.add(endRoom);
		
		// add some obstacles to build more randomness to the level
		// TODO check config for number of obstacles
		Room obstacle = new Room();
		obstacle.setAnchor(true);
		obstacle.setObstacle(true);
		obstacle = randomizeRoom(rand, obstacle, startPoint,
//				RandomHelper.randomInt(rand, config.getXOffset().getMinInt(), config.getXOffset().getMaxInt()),
//				RandomHelper.randomInt(rand, config.getZOffset().getMinInt(), config.getZOffset().getMaxInt()),
				config);
//		anchors.add(obstacle);

		/**
		 * build the level
		 */
		return build(world, rand, startPoint, predefinedRooms, config);
	}

	/**
	 * 
	 * @param world
	 * @param random
	 * @param startPoint
	 * @param plannedRooms
	 * @return
	 */
	public Level build(World world, Random random, ICoords startPoint, List<Room> plannedRooms) {
		return build(world, random, startPoint, plannedRooms, this.config);
	}
	
	/**
	 * 
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @param plannedRooms
	 * @param config
	 * @return
	 */
	public Level build(World world, Random rand, ICoords startPoint, List<Room> plannedRooms, ILevelConfig config) {
		List<Room> anchors = new ArrayList<>();
		List<Room> spawned = null;
		List<Room> rooms = null;
		List<Edge> edges = null;
		List<Edge> paths = null;
		List<Wayline> waylines = null;
		List<Hallway> hallways = null;
		Room startRoom = null;
		Room endRoom = null;
		Level level = new Level();	
		
		// NOTE at this point the level boundary and spawn boundarys should already be set.
		
		spawned = spawnRooms(rand, getRoomBoundary(), startPoint, config);		
		Dungeons2.log.debug("Spawned.size=" + spawned.size());
		
		// TODO new method
		// process all predefined rooms and categorize
		for (Room room : plannedRooms) {
			if (room.isStart() && startRoom == null) startRoom = room;
			else if (room.isEnd() && endRoom == null) endRoom = room;
			if (room.isAnchor())
				anchors.add(room);
			else
				spawned.add(room);
		}
		
		// sort working array based on distance
		Collections.sort(spawned, Room.distanceComparator);
				
		// move apart any intersecting rooms (uses anti-grav method)
		Dungeons2.log.debug("Before Apply Distance Buffering Rooms.size -> {}", anchors.size() + spawned.size());
		rooms = applyDistanceBuffering(rand, startPoint, anchors, spawned);
		Dungeons2.log.debug("After Apply Distance Buffering Rooms.size -> {}, room loss -> {}", rooms.size(), getRoomLossToDistanceBuffering());
//		System.out.println("After Apply Distance Buffering Rooms.size -> {}, room loss -> {}", rooms.size(), getRoomLossToDistanceBuffering());
		
		// select rooms to use ie. filter out rooms that don't meet criteria
		rooms = selectValidRooms(world, rand, rooms, config);
		Dungeons2.log.debug("After select valid rooms Rooms.size -> {}, room loss -> {}", rooms.size(), getRoomLossToValidation());
		if (rooms == null || rooms.size() < MIN_NUMBER_OF_ROOMS) {
			return EMPTY_LEVEL;
		}
		
		// TODO record as a value pair, move to own method
		// record minimum dimensions of all the rooms
		int mx = 0;
		int mz = 0;
		for (int i = 0; i < rooms.size(); i++) {
			if (rooms.get(i).getMinX() < mx) mx = rooms.get(i).getMinX();
			if (rooms.get(i).getMinZ() < mz) mz = rooms.get(i).getMinZ();
		}
		
		// TODO move own method
		// if dimensions are negative, offset all rooms by positive (Math.abs()) amount +1
		if (mx < 0 || mz < 0) {
			for (Room room : rooms) {
				room.setCoords(room.getCoords().add(Math.abs(mx)+1, 0, Math.abs(mz)+1));
			}
		}
		
		// triangulate valid rooms
		edges = triangulate(rooms);
		if (edges == null) {
			return EMPTY_LEVEL;
		}
		
		// get the mst
		paths = calculatePaths(rand, edges, rooms);
		
		Dungeons2.log.debug("StartRoom.id=" + startRoom.getId());
		Dungeons2.log.debug("EndRoom.id=" + endRoom.getId());
		if (!BFS(startRoom.getId(), endRoom.getId(), rooms, paths)) {
			Dungeons2.log.debug("A path doesn't exist from start room to end room on level.");
			return EMPTY_LEVEL;
		}
		
		// calculate room waypoints - the coords that build a hallway (edge) between to rooms (vertice)
		waylines = calculateWaylines(rand, paths, rooms);
		if (waylines == EMPTY_WAYLINES) return EMPTY_LEVEL;

		// revert room dimensions and generated waylines back to original values by removing offset.
		if (mx < 0 || mz < 0) {
			for (Room room : rooms) {
				room.setCoords(room.getCoords().add(mx-1, 0, mz-1));
			}
			for (Wayline line : waylines) {
				line.getPoint1().setCoords(line.getPoint1().getCoords().add(mx-1, 0, mz-1));
				line.getPoint2().setCoords(line.getPoint2().getCoords().add(mx-1, 0, mz-1));
			}
		}
		
		/*
		 * build the hallways
		 */
		hallways = new ArrayList<>();
		// a list to hold waylines from an L-shaped (elbow join) set of waylines
		List<Wayline> processedJoins = new ArrayList<>(10);
		// process each wayline
		for (Wayline line : waylines) {
			// build a hallway (room) from a wayline
			//Hallway hallway = Hallway.fromWayline(line, level.getRooms());
			Hallway hallway = buildHallway(line, rooms);
			
			// add the hallway to the list of generated hallways
			hallways.add(hallway);
			addDoorsToRoom(hallway);
			// if an L-shaped ie. multiple connected waylines.
			if (line.getWayline() != null) {				
				// check if second wayline is in process joins list
				if (!processedJoins.contains(line.getWayline())) {
					Hallway hallway2 = buildHallway(line.getWayline(), rooms);
					hallway2.setHallway(hallway);
					hallway.setHallway(hallway2);
					addDoorsToRoom(hallway2);
					hallways.add(hallway2);
					
					// add first wayline to processed joins
					processedJoins.add(line);
				}
			}
		}
		
		// setup the level
		Room room = rooms.get(0);
		int minX = room.getMinX();
		int maxX = room.getMaxX();
		int minY = room.getMinY();
		int maxY = room.getMaxY();
		int minZ = room.getMinZ();
		int maxZ = room.getMaxZ();
		// record min and max dimension values for level
		for (int i = 1; i < rooms.size(); i++) {
			if (rooms.get(i).getMinX() < minX) minX = rooms.get(i).getMinX();
			if (rooms.get(i).getMaxX() > maxX) maxX = rooms.get(i).getMaxX();
			if (rooms.get(i).getMinY() < minY) minY = rooms.get(i).getMinY();
			if (rooms.get(i).getMaxY() > maxY) maxY = rooms.get(i).getMaxY();
			if (rooms.get(i).getMinZ() < minZ) minZ = rooms.get(i).getMinZ();
			if (rooms.get(i).getMaxZ() > maxZ) maxZ = rooms.get(i).getMaxZ();
		}
		
		// update the level
		level.setStartPoint(startPoint);
		level.setStartRoom(startRoom);
		level.setEndRoom(endRoom);
		level.setRooms(rooms);
		level.setEdges(edges);
		level.setPaths(paths);
		level.setWaylines(waylines);
		level.setHallways(hallways);
		level.setMinX(minX);
		level.setMaxX(maxX);
		level.setMinY(minY);
		level.setMaxY(maxY);
		level.setMinZ(minZ);
		level.setMaxZ(maxZ);
		level.setConfig(config);
		
		return level;
	}
	
	/**
	 * 
	 * @param rand
	 * @param startPoint
	 * @param startRooms
	 * @param endRooms
	 * @param config
	 * @return
	 */
	@Deprecated
	public Level build(World world, Random rand, ICoords startPoint, List<Room> plannedRooms, LevelConfig config) {
		/*
		 * special rooms which are designed as <em>fixed position</em>. ex. ladder rooms, treasure rooms, boss rooms.
		 * these rooms' positions will typically be pre-determined in a location that meets all criteria.
		 * these rooms <em>will</em> be included in the resultant level.
		 */
		List<Room> anchors = new ArrayList<>();

		/*
		 * rooms that are randomly generated
		 */
		List<Room> spawned = null;

		/*
		 * resultant list of buffered/spaced rooms on a single level.
		 */
		List<Room> rooms = null;

		/*
		 * resultant list of edges from triangulation of rooms.
		 */
		List<Edge> edges = null;

		/*
		 * resultant list of edges from performing minimum spanning tree on edges
		 */
		List<Edge> paths = null;

		/*
		 * resultant list of horizontal and vertical lines representing hallways that connect all the rooms together
		 * by "squaring off" the paths
		 */
		List<Wayline> waylines = null;
		
		/*
		 * resultant list of hallways derived from waylines
		 */
		List<Hallway> hallways = null;
		
		/*
		 * return object containing all the rooms that meet build criteria and the locations of the special rooms.
		 */
		Level level = new Level();
		
		Room startRoom = null;
		Room endRoom = null;
		
		// add randomly generated rooms
//		spawned = spawnRooms(rand,  startPoint, config);
		spawned = spawnRooms(rand, getRoomBoundary(), startPoint, config);
		
		Dungeons2.log.debug("Spawned.size=" + spawned.size());
		
		// process all predefined rooms and categorize
		for (Room room : plannedRooms) {
			if (room.isStart() && startRoom == null) startRoom = room;
			else if (room.isEnd() && endRoom == null) endRoom = room;
			if (room.isAnchor())
				anchors.add(room);
			else
				spawned.add(room);
		}
		
		// sort working array based on distance
		Collections.sort(spawned, Room.distanceComparator);
				
		// move apart any intersecting rooms (uses anti-grav method)
		Dungeons2.log.debug("Before Apply Distance Buffering Rooms.size -> {}", spawned.size());
		rooms = applyDistanceBuffering(rand, startPoint, anchors, spawned/*, config*/);
		Dungeons2.log.debug("After Apply Distance Buffering Rooms.size -> {}, room loss -> {}", rooms.size(), getRoomLossToDistanceBuffering());
//		System.out.println("After Apply Distance Buffering Rooms.size -> {}, room loss -> {}", rooms.size(), getRoomLossToDistanceBuffering());
		
		// select rooms to use ie. filter out rooms that don't meet criteria
		rooms = selectValidRooms(world, rand, rooms, config);
		Dungeons2.log.debug("After select valid rooms Rooms.size -> {}, room loss -> {}", rooms.size(), getRoomLossToValidation());
//		System.out.println("After select valid rooms Rooms.size -> {}, room loss -> {}", rooms.size(), getRoomLossToValidation());
		
		if (rooms == null || rooms.size() < MIN_NUMBER_OF_ROOMS) {
			return EMPTY_LEVEL;
		}
		// TODO record as a value pair, move to own method
		// record minimum dimensions of all the rooms
		int mx = 0;
		int mz = 0;
		for (int i = 0; i < rooms.size(); i++) {
			if (rooms.get(i).getMinX() < mx) mx = rooms.get(i).getMinX();
			if (rooms.get(i).getMinZ() < mz) mz = rooms.get(i).getMinZ();
		}
//		Dungeons2.log.debug("Min X/Z values=" + mx + ", " + mz);
		
		// TODO move own method
		// if dimensions are negative, offset all rooms by positive (Math.abs()) amount +1
		if (mx < 0 || mz < 0) {
			for (Room room : rooms) {
				room.setCoords(room.getCoords().add(Math.abs(mx)+1, 0, Math.abs(mz)+1));
			}
		}
		
		/*
		 * NOTE triangulate can only operate on a positive plane of vertices.
		 * NOTE triangulate requires at least 3 points (rooms)
		 * therefor all room must be offset into the positive x/z plane.
		 */
		// triangulate valid rooms
		edges = triangulate(rooms);
		if (edges == null) {
			return EMPTY_LEVEL;
		}
		
		// get the mst
		paths = calculatePaths(rand, edges, rooms, config);

		// TODO a BFS from start to end to ensure a path still exists
		// path = findPath(start, end);
		Dungeons2.log.debug("StartRoom.id=" + startRoom.getId());
		Dungeons2.log.debug("EndRoom.id=" + endRoom.getId());
		if (!BFS(startRoom.getId(), endRoom.getId(), rooms, paths)) {
			Dungeons2.log.debug("A path doesn't exist from start room to end room on level.");
			return EMPTY_LEVEL;
		}
		
		// calculate room waypoints - the coords that build a hallway (edge) between to rooms (vertice)
		waylines = calculateWaylines(rand, paths, rooms/*, config*/);
		if (waylines == EMPTY_WAYLINES) return EMPTY_LEVEL;

//				Collections.sort(rooms, Room.distanceComparator);

		// revert room dimensions and generated waylines back to original values by removing offset.
		if (mx < 0 || mz < 0) {
			for (Room room : rooms) {
				room.setCoords(room.getCoords().add(mx-1, 0, mz-1));
			}
			for (Wayline line : waylines) {
				line.getPoint1().setCoords(line.getPoint1().getCoords().add(mx-1, 0, mz-1));
				line.getPoint2().setCoords(line.getPoint2().getCoords().add(mx-1, 0, mz-1));
				// NOTE this might be easier to accomplish if ALL waylines (joints included) were added to the list
				// BUT still refererncing each other in joint. Then in buildHalls() create a list of ref'ed and check against it so that
				// double halls aren't built.
//				if (line.getWayline() != null) {
//					line.getWayline().getPoint1().setCoords(line.getWayline().getPoint1().getCoords().add(mx-1, 0, mz-1));
//					line.getWayline().getPoint2().setCoords(line.getWayline().getPoint2().getCoords().add(mx-1, 0, mz-1));					
//				}
			}
		}
		
		/*
		 * build the hallways
		 */
		// initialize hallways
		hallways = new ArrayList<>();
		
		// a list to hold waylines from an L-shaped (elbow join) set of waylines
		List<Wayline> processedJoins = new ArrayList<>(10);
		
		// process each wayline
		for (Wayline line : waylines) {
			// build a hallway (room) from a wayline
			//Hallway hallway = Hallway.fromWayline(line, level.getRooms());
			Hallway hallway = buildHallway(line, rooms);
			
			// add the hallway to the list of generated hallways
			hallways.add(hallway);

			addDoorsToRoom(hallway);
			
			// TODO make this its own method
			// create doors for the rooms based on the hallway doors, but on the opposite side of the room (direction)
//			for (Door d : hallway.getDoors()) {
//				// create a new door instance and flip the direction
//				Door door = new Door(d.getCoords(), d.getRoom(), d.getHallway(), d.getDirection().rotate(Rotate.ROTATE_180));
//				d.getRoom().getDoors().add(door);
//			}
			
			// TODO how to cross-ref L-shaped hallways together from waylines... they both need to be built first ?
			// if an L-shaped ie. multiple connected waylines.
			if (line.getWayline() != null) {				
				// check if second wayline is in process joins list
				if (!processedJoins.contains(line.getWayline())) {
					Hallway hallway2 = buildHallway(line.getWayline(), rooms);
					hallway2.setHallway(hallway);
					hallway.setHallway(hallway2);
					addDoorsToRoom(hallway2);
					hallways.add(hallway2);
					
					// add first wayline to processed joins
					processedJoins.add(line);
				}
			}
		}
		
		// setup the level
		Room room = rooms.get(0);
		int minX = room.getMinX();
		int maxX = room.getMaxX();
		int minY = room.getMinY();
		int maxY = room.getMaxY();
		int minZ = room.getMinZ();
		int maxZ = room.getMaxZ();
		
		// record min and max dimension values for level
		for (int i = 1; i < rooms.size(); i++) {
			if (rooms.get(i).getMinX() < minX) minX = rooms.get(i).getMinX();
			if (rooms.get(i).getMaxX() > maxX) maxX = rooms.get(i).getMaxX();
			if (rooms.get(i).getMinY() < minY) minY = rooms.get(i).getMinY();
			if (rooms.get(i).getMaxY() > maxY) maxY = rooms.get(i).getMaxY();
			if (rooms.get(i).getMinZ() < minZ) minZ = rooms.get(i).getMinZ();
			if (rooms.get(i).getMaxZ() > maxZ) maxZ = rooms.get(i).getMaxZ();
		}
		
		// update the level
		level.setStartPoint(startPoint);
		level.setStartRoom(startRoom);
		level.setEndRoom(endRoom);
		level.setRooms(rooms);
		level.setEdges(edges);
		level.setPaths(paths);
		level.setWaylines(waylines);
		level.setHallways(hallways);
		level.setMinX(minX);
		level.setMaxX(maxX);
		level.setMinY(minY);
		level.setMaxY(maxY);
		level.setMinZ(minZ);
		level.setMaxZ(maxZ);
//		level.setConfig(config);

		return level;
	}
	
	/**
	 * 
	 * @param hallway
	 */
	private void addDoorsToRoom(Hallway hallway) {
		for (Door d : hallway.getDoors()) {
			// create a new door instance and flip the direction
			Door door = new Door(d.getCoords(), d.getRoom(), d.getHallway(), d.getDirection().rotate(Rotate.ROTATE_180));
			d.getRoom().getDoors().add(door);
		}
	}

	protected List<Edge> calculatePaths(Random rand, List<Edge> edges, List<Room> rooms) {
		/*
		 * holds are the reduced edges generated by the Minimun Spanning Tree
		 */
		List<Edge> paths = new ArrayList<>();
		/**
		 * counts the number of edges are assigned to each node/vertex
		 */
		int[] edgeCount = new int[rooms.size()];

		// reduce all edges to MST
		EdgeWeightedGraph graph = new EdgeWeightedGraph(rooms.size(), edges);
		LazyPrimMST mst = new LazyPrimMST(graph);
		for (Edge e : mst.edges()) {
			if (e.v < rooms.size() && e.w < rooms.size()) {
				Room room1 = rooms.get(e.v);
				Room room2 = rooms.get(e.w);	
				paths.add(e);
				edgeCount[room1.getId()]++;
				edgeCount[room2.getId()]++;
			}
			else {
				Dungeons2.log.warn(String.format("Ignored Room: array out-of-bounds: v: %d, w: %d", e.v, e.w));
			}
		}

		// add more edges
		int addtionalEdges = (int) (edges.size() * 0.25); // TODO get the % from config
		for (int i = 0 ; i < addtionalEdges; i++) {
			int pos = rand.nextInt(edges.size());
			Edge e = edges.get(pos);
			// TODO ensure that only non-used edges are selected (and doesn't increment the counter)
			Room room1 = rooms.get(e.v);
			Room room2 = rooms.get(e.w);
			if (!room1.isEnd() && !room2.isEnd() &&
					edgeCount[room1.getId()] < room1.getDegrees() && edgeCount[room2.getId()] < room2.getDegrees()) {
				paths.add(e);
				edgeCount[room1.getId()]++;
				edgeCount[room2.getId()]++;				
			}

		}
		return paths;
	}

	
	/**
	 * 
	 * @param rand
	 * @param edges
	 * @param rooms
	 * @param config
	 * @return
	 */
	@Deprecated
	protected List<Edge> calculatePaths(Random rand, List<Edge> edges, List<Room> rooms, LevelConfig config) {
		/*
		 * holds are the reduced edges generated by the Minimun Spanning Tree
		 */
		List<Edge> paths = new ArrayList<>();
		/**
		 * counts the number of edges are assigned to each node/vertex
		 */
		int[] edgeCount = new int[rooms.size()];

		// reduce all edges to MST
		EdgeWeightedGraph graph = new EdgeWeightedGraph(rooms.size(), edges);
		LazyPrimMST mst = new LazyPrimMST(graph);
		for (Edge e : mst.edges()) {
			if (e.v < rooms.size() && e.w < rooms.size()) {
				Room room1 = rooms.get(e.v);
				Room room2 = rooms.get(e.w);	
				paths.add(e);
				edgeCount[room1.getId()]++;
				edgeCount[room2.getId()]++;
			}
			else {
				Dungeons2.log.warn(String.format("Ignored Room: array out-of-bounds: v: %d, w: %d", e.v, e.w));
			}
		}
		
//		for (int i = 0; i < edgeCount.length; i++) {
//			Dungeons2.log.info("Room " + i + " has asigned edges: " + edgeCount[i]);
//			
//		}

		// add more edges
		int addtionalEdges = (int) (edges.size() * 0.25); // TODO get the % from config
		for (int i = 0 ; i < addtionalEdges; i++) {
			int pos = rand.nextInt(edges.size());
			Edge e = edges.get(pos);
			// TODO ensure that only non-used edges are selected (and doesn't increment the counter)
			Room room1 = rooms.get(e.v);
			Room room2 = rooms.get(e.w);
			if (!room1.isEnd() && !room2.isEnd() &&
					edgeCount[room1.getId()] < room1.getDegrees() && edgeCount[room2.getId()] < room2.getDegrees()) {
				paths.add(e);
				edgeCount[room1.getId()]++;
				edgeCount[room2.getId()]++;				
//				Dungeons2.log.info("Adding path from " + room1.getId() + " to " + room2.getId());
			}
//			else {
//				Dungeons2.log.info("Rejected additional path due to exceeded degrees from " + room1.getId() + " to " + room2.getId());
//			}
		}
		return paths;
	}
	
	/**
	 * 
	 * @param wayline
	 * @param rooms
	 * @return
	 */
	public Hallway buildHallway(Wayline wayline, List<Room> rooms) {
		int width = 3;
		int depth = 3;
		
		// work with temp way points
		Waypoint startPoint = null;
		Waypoint endPoint = null;
		ICoords startCoords = null;
		boolean isElbowJoint = false;
		
		// HORIZONTAL (WEST <--> EAST)
		if (wayline.getAlignment() == Alignment.HORIZONTAL) {
			// determine which point is the "start point" - having the smallest coords
			if (wayline.getPoint1().getX() < wayline.getPoint2().getX()) {
				startPoint = wayline.getPoint1();
				endPoint = wayline.getPoint2();
			}
			else {
				startPoint = wayline.getPoint2();
				endPoint = wayline.getPoint1();
			}

			// determine if this is a "elbow joint" wayline
			if (!startPoint.isTerminated() || !endPoint.isTerminated()) {
				isElbowJoint = true;
			}
			
			/*
			 * update start/end point depending on isTerminal
			 * this makes the elbow joint 1 block longer so that they line up correctly
			 */
			if (isElbowJoint) {
				if (!startPoint.isTerminated()) {
					startPoint.setCoords(startPoint.getCoords().add(-1, 0, 0));
				}
				
				if (!endPoint.isTerminated()) {
					endPoint.setCoords(endPoint.getCoords().add(1, 0, 0));
				}
			}
			// update the width
			width = Math.abs(startPoint.getX() - endPoint.getX()) + 1;
			
			/*
			 *  this is to maintain the actual hallway (air part) to still be along the wayline,
			 *  since the hallway is 3 wide (2 walls and 1 air)
			 */
			startCoords = startPoint.getCoords();
			startCoords = startCoords.add(0, 0, -1);
			
			// shift if non-terminal (ie an elbow joint)

		}
		// VERTICAL (NORTH <--> SOUTH)
		else {
			// determine which point is the "start point" - having the smallest coords
			if (wayline.getPoint1().getZ() < wayline.getPoint2().getZ()) {
				startPoint = wayline.getPoint1();
				endPoint = wayline.getPoint2();
			}
			else {
				startPoint = wayline.getPoint2();
				endPoint = wayline.getPoint1();
			}
			
			/*
			 * update start/end point depending on isTerminal
			 * this makes the elbow joint 1 block longer so that they line up correctly
			 */		
			if (isElbowJoint) {
				if (!startPoint.isTerminated()) {
					startPoint.setCoords(startPoint.getCoords().add(0, 0, -1));
				}				
				if (!endPoint.isTerminated()) {
					endPoint.setCoords(endPoint.getCoords().add(0, 0, 1));
				}
			}
			// update the depth
			depth = Math.abs(startPoint.getZ( ) - endPoint.getZ()) + 1;
			
			// left-shift by one since horiztonal hallways are 3 depth
			// this is to maintain the actual hallway (air part) to still be along the wayline.
			startCoords = startPoint.getCoords();
			startCoords = startCoords.add(-1, 0, 0);
		}
		
		// get the rooms referenced by the waypoints
		Room room1 = rooms.get(startPoint.getId());
		Room room2 = rooms.get(endPoint.getId());		

		 // the start/end points y-vlaue isn't set, so update them.
		startPoint.setCoords(startPoint.getCoords().resetY(room1.getCoords().getY()));
		endPoint.setCoords(endPoint.getCoords().resetY(room2.getCoords().getY()));

		// calculate what the dimensions should be
		int height = Math.abs(
				Math.min(room1.getMinY(), room2.getMinY()) - 
				Math.max(room1.getMinY(), room2.getMinY())
				) + 1+ 3; // NOTE why 3? because a doorway is 2 + ceiling block
		
		// create a temp room out of the dimensions
		Hallway hallway = (Hallway) new Hallway().setCoords(
				new Coords(
						startCoords.getX(),
						startPoint.getCoords().getY(),
						startCoords.getZ()))
				.setWidth(width)
				.setDepth(depth)
				.setHeight(height)
				.setType(Type.HALLWAY);
		// update the alignment (Hallway specific property)
		hallway.setAlignment(wayline.getAlignment());
		
		// store the start/end point as doorCoords iff they are terminated.
		if (startPoint.isTerminated()) {
			Direction d = calculateDirection(hallway, startPoint.getCoords(), room1);
			hallway.getDoors().add(new Door(startPoint.getCoords(), room1, hallway, d));
		}
		if (endPoint.isTerminated()) {
			Direction d = calculateDirection(hallway, endPoint.getCoords(), room2);
			hallway.getDoors().add(new Door(endPoint.getCoords(), room2, hallway, d));
		}
		// TODO else { add and set Hallway property. ie this is an elbow join, and this hallway points to another hallway)
		// can't here as it is needs to process the entire wayline first, then produces the hallway
		return hallway;
	}

	/**
	 * Determines which side/direction the door is on.
	 * @param hw
	 * @param doorCoords
	 * @param room
	 */
	public Direction calculateDirection(Hallway hw, ICoords coords, Room room) {
		if (hw.getAlignment() == Alignment.HORIZONTAL) {
			// test which side the door is on
			if (coords.getX() == hw.getMinX()) {
				return Direction.WEST;
			}
			if (coords.getX() == hw.getMaxX()) {
				return Direction.EAST;
			}
		}
		else {
			if (coords.getZ() == hw.getMinZ()) {
				return Direction.NORTH;
			}
			if (coords.getZ() == hw.getMaxZ()) {
				return Direction.SOUTH;
			}
		}
		return null;
	}
	
	
	/**
	 * perform a breadth first search against the list of edges to determine if a path exists
	 * from one node to another.
	 * @param start
	 * @param end
	 * @param rooms
	 * @param edges
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected boolean BFS(int start, int end, List<Room> rooms, List<Edge> edges) {
		// build an adjacency list
		LinkedList<Integer> adj[];

		adj = new LinkedList[rooms.size()];
		for (Room r : rooms) {
			adj[r.getId()] = new LinkedList<>();
		}
		
        for (Edge e : edges) {
//        	if (adj[e.v] == null) adj[e.v] = new LinkedList<>();
        	adj[e.v].add(e.w);
        	// add both directions to ensure all adjacencies are covered
        	adj[e.w].add(e.v);
//        	Dungeons2.log.debug("Adding edge " + e.v + " <-->  " + e.w);        	
        }

		// Mark all the vertices as not visited(By default
		// set as false)
		boolean visited[] = new boolean[rooms.size()];

		// Create a queue for BFS
		LinkedList<Integer> queue = new LinkedList<Integer>();

		// Mark the current node as visited and enqueue it
		visited[start]=true;
		queue.add(start);

		while (queue.size() != 0) {
			// Dequeue a vertex from queue and print it
			int s = queue.poll();
//			Dungeons2.log.debug("polling edge id: " + s);

			// Get all adjacent vertices of the dequeued vertex s
			// If a adjacent has not been visited, then mark it
			// visited and enqueue it
			Iterator<Integer> i = adj[s].listIterator();
			while (i.hasNext()) {
				int n = i.next();
				if (n == end) return true;
				
				if (!visited[n]) {
					visited[n] = true;
					queue.add(n);
				}
			}
		}		
		return false;
	}
	
	/**
	 * 
	 * @param rooms
	 * @return
	 */
	protected List<Edge> triangulate(List<Room> rooms) {
		/*
		 * maps all rooms by XZ plane (ie x:z)
		 * this is required for the Delaunay Triangulation library because it only returns edges without any identifying properties, only points
		 */
		Map<String, Room> map = new HashMap<>();
		/*
		 * holds all rooms in Vector2D format.
		 * used for the Delaunay Triangulation library to calculate all the edges between rooms.
		 * 
		 */
		Vector<Vector2D> pointSet = new Vector<>();		
		/*
		 * holds all the edges that are produced from triangulation
		 */
		List<Edge> edges = new ArrayList<>();
		/*
		 *  weight/cost array of all rooms
		 */
		double[][] matrix = LevelBuilder.getDistanceMatrix(rooms);
		/**
		 * a flag to indicate that an edge leading to the "end" room is created
		 */
		boolean isEndEdgeMet = false;
		int endEdgeCount = 0;

		// sort rooms by id
		Collections.sort(rooms, Room.idComparator);

		// map all rooms by XZ plane and build all edges.
		for (Room room : rooms) {
			ICoords center = room.getCoords();
			// map out the rooms by IDs
			map.put(center.getX() + ":" + center.getZ(), room);
			// convert coords into vector2d for triangulation
			Vector2D v = new Vector2D(center.getX(), center.getZ());
//			Dungeons2.log.debug(String.format("Room.id: %d = Vector2D: %s", room.getId(), v.toString()));
			pointSet.add(v);
		}

		// triangulate the set of points
		DelaunayTriangulator triangulator = null;
		try {
			triangulator = new DelaunayTriangulator(pointSet);
			triangulator.triangulate();
		}
		catch(NotEnoughPointsException e) {
			Dungeons2.log.warn("Not enough points where provided for triangulation. Level generation aborted.");
			return null; // TODO return empty list
		}
		catch(Exception e) {
			if (rooms !=null) Dungeons2.log.debug("rooms.size=" + rooms.size());
			else Dungeons2.log.debug("Rooms is NULL!");
			if (pointSet != null) Dungeons2.log.debug("Pointset.size=" + pointSet.size());
			else Dungeons2.log.debug("Pointset is NULL!");
			
			Dungeons2.log.error("Unable to triangulate: ", e);
		}

		// retrieve all the triangles from triangulation
		List<Triangle2D> triangles = triangulator.getTriangles();

		for(Triangle2D triangle : triangles) {
			// locate the corresponding rooms from the points of the triangles
			Room r1 = map.get((int)triangle.a.x + ":" + (int)triangle.a.y);
			Room r2 = map.get((int)triangle.b.x + ":" + (int)triangle.b.y);
			Room r3 = map.get((int)triangle.c.x + ":" + (int)triangle.c.y);

			// build an edge based on room distance matrix
			// begin Minimum Spanning Tree calculations
			Edge e = new Edge(r1.getId(), r2.getId(), matrix[r1.getId()][r2.getId()]);
			
			// TODO for boss room, not necessarily end room
			// remove any edges that lead to the end room if the end room already has one edge
			// remove (or don't add) any edges that lead to the end room if the end room already has it's maximum edges (degrees)
			if (!r1.isEnd() && !r2.isEnd()) {
//			if (!r1.getType().equals(Type.BOSS) && !r2.getType().equals(Type.BOSS)) {
				edges.add(e);
			}
			else if (r1.isStart() || r2.isStart()) {
				// skip if start joins the end
			}
			else if (!isEndEdgeMet) {
				// add the edge
				edges.add(e);
				// increment the number of edges leading to the end room
				endEdgeCount++;
				// get the end room
				Room end = r1.isEnd() ? r1 : r2;
				if (endEdgeCount >= end.getDegrees()) {
					isEndEdgeMet = true;
				}
			}
			
			e = new Edge(r2.getId(), r3.getId(), matrix[r2.getId()][r3.getId()]);
			if (!r2.isEnd() && !r3.isEnd()) {
				edges.add(e);
			}
			else if (r1.isStart() || r2.isStart()) {
				// skip
			}
			else if (!isEndEdgeMet) {
				edges.add(e);
				isEndEdgeMet = true;
			}
			
			e = new Edge(r1.getId(), r3.getId(), matrix[r1.getId()][r3.getId()]);
			if (!r1.isEnd() && !r3.isEnd()) {
				edges.add(e);
			}
			else if (r1.isStart() || r2.isStart()) {
				// skip
			}
			else if (!isEndEdgeMet) {
				edges.add(e);
				isEndEdgeMet = true;
			}
		}
		return edges;
	}

	/**
	 * It is assumed that the rooms list is sorted in some fashion or the caller has a method to map the matrix indices back to a room object
	 * @param rooms
	 * @return
	 */
	protected static double[][] getDistanceMatrix(List<Room> rooms) {
		double[][] matrix = new double[rooms.size()][rooms.size()];

		for (int i = 0; i < rooms.size(); i++) {
			Room room = rooms.get(i);
			for (int j = 0; j < rooms.size(); j++) {
				Room node = rooms.get(j);
				if (room == node) {
					matrix[i][j] = 0.0;
				}
				else {
					if (matrix[i][j] == 0.0) {
						// calculate distance;
						double dist = room.getCenter().getDistance(node.getCenter());
						matrix[i][j] = dist;
						matrix[j][i] = dist;
					}
				}
			}
		}
		return matrix;
	}

	/**
	 * @param rand
	 * @param paths
	 * @param rooms
	 * @param config
	 */
	protected List<Wayline> calculateWaylines(Random rand, List<Edge> paths, List<Room> rooms/*, LevelConfig config*/) {
		List<Wayline> resolvedWaylines = null;
		
		/*
		 * a list of a the waylines constructed from paths
		 */
		List<Wayline> waylines = new ArrayList<>();		

		for (Edge path : paths) {
			// get the rooms
			Room room1 = rooms.get(path.v);
			Room room2 = rooms.get(path.w);
//			Dungeons2.log.info(String.format("Connecting: [%d] with [%d]", room1.getId(), room2.getId()));
			
			// get the midpoint between room1 and room2
			ICoords midpoint = room1.getCenter().add(room2.getCenter());
			midpoint = new Coords(midpoint.getX()/2, midpoint.getY()/2, midpoint.getZ()/2);
//			midpoint.resetX(midpoint.getX()/2);
//			midpoint.resetY(midpoint.getY()/2);
//			midpoint.resetZ(midpoint.getZ()/2);
			
			/*
			 * a group of maps that relate the room's min/max values with the room
			 * NOTE if it is not necessary to record the ID of the room in the Waypoint, all the maps are unnecessary as well.
			 * UPDATE 8-5-2016 it IS necessary to record the ID of the room as the Y value of the room needs to be inspected
			 * when building the hallway
			 */
			Map<Integer, Room> minXMap = new HashMap<>(5);
			minXMap.put(new Integer(room1.getMinX()), room1);
			minXMap.put(new Integer(room2.getMinX()), room2);
			
			Map<Integer, Room> maxXMap = new HashMap<>(5);
			maxXMap.put(new Integer(room1.getMaxX()), room1);
			maxXMap.put(new Integer(room2.getMaxX()), room2);
			
			Map<Integer, Room> minZMap = new HashMap<>(5);
			minZMap.put(new Integer(room1.getMinZ()), room1);
			minZMap.put(new Integer(room2.getMinZ()), room2);
			
			Map<Integer, Room> maxZMap = new HashMap<>(5);
			maxZMap.put(new Integer(room1.getMaxZ()), room1);
			maxZMap.put(new Integer(room2.getMaxZ()), room2);			
			
			// get the min of the max  x -axis
			int innerMaxX = Math.min(room1.getMaxX(),  room2.getMaxX());
			// get the max of the min x-axis
			int innerMinX = Math.max(room1.getMinX(),  room2.getMinX());
			int innerMaxZ = Math.min(room1.getMaxZ(), room2.getMaxZ());
			int innerMinZ = Math.max(room1.getMinZ(), room2.getMinZ());

			/*
			 * a stack to contain all the waylines (and sub-waylines) that need to be checked for intersection
			 */
			Stack<Wayline> stack = new Stack<>();
			
//			Dungeons2.log.info(String.format("minX: %d, maxX: %d, minZ: %d, maxZ: %d", innerMinX, innerMaxX, innerMinZ, innerMaxZ));
			
			/*
			 * -------------------------------------------------------
			 *  test the horizontal "closeness"
			 *  -------------------------------------------------------
			 */
			
			/*
			 * if the rooms overlap each other on a single axis, they are "close" enough
			 */
			// horizontal wayline
			if ((room1.getMaxZ() < room2.getMaxZ() && room1.getMaxZ() > room2.getMinZ()) ||
					(room2.getMaxZ() < room1.getMaxZ() && room2.getMaxZ() > room1.getMinZ()) ||
					(room1.getMinZ() >= room2.getMinZ() && room1.getMaxZ() <= room2.getMaxZ()) ||
					(room2.getMinZ() >= room1.getMinZ() && room2.getMaxZ() <= room1.getMaxZ())) {
				int z = (innerMaxZ + innerMinZ)/2;
				
				// TODO need some sort of check that if rooms are close by 1 block, which creates a horz wayline that runs from
				// one room fine, but ends in a wall of the second room, which theoretically should be fine, as the door generator
				// should continue to remove wall until the air of the room is met ??
				
				Wayline wayline = new Wayline(new Waypoint(minXMap.get(innerMinX).getId(), innerMinX-1, 0, z),
						new Waypoint(maxXMap.get(innerMaxX).getId(), innerMaxX+1, 0, z), Alignment.HORIZONTAL);

//				Dungeons2.log.info(String.format("Horz line from [%d, %d] to [%d, %d[", innerMinX, z, innerMaxX, z));
				if (wayline.getPoint1().getCoords().equals(wayline.getPoint2().getCoords())) {
					Dungeons2.log.trace("Wayline's points are equal !!: " + wayline);
				}
				
				stack.add(wayline);
				// check if EMPTY_WAYLINES is return.
				resolvedWaylines = resolveWaylineRoomIntersections(rooms, stack);
				if (resolvedWaylines == EMPTY_WAYLINES) return resolvedWaylines;
				waylines.addAll(resolvedWaylines);
				continue;
			}
			// vertical wayline
			if ((room1.getMaxX() < room2.getMaxX() && room1.getMaxX() > room2.getMinX()) ||
					(room2.getMaxX() < room1.getMaxX() && room2.getMaxX() > room1.getMinX()) ||
					(room1.getMinX() >= room2.getMinX() && room1.getMaxX() <= room2.getMaxX()) ||
					(room2.getMinX() >= room1.getMinX() && room2.getMaxX() <= room1.getMaxX())) {
				int x = (innerMaxX + innerMinX)/2;
				Wayline wayline = new Wayline(new Waypoint(minZMap.get(innerMinZ).getId(), x, 0, innerMinZ-1),
						new Waypoint(maxZMap.get(innerMaxZ).getId(), x, 0, innerMaxZ+1), Alignment.VERTICAL);
//				Dungeons2.log.info(String.format("Vert line from [%d, %d] to [%d, %d[", x, innerMinZ, x, innerMaxZ));

				if (wayline.getPoint1().getCoords().equals(wayline.getPoint2().getCoords())) {
					Dungeons2.log.trace("Wayline's points are equal !!: " + wayline);
				}
				stack.add(wayline);
				waylines.addAll(resolveWaylineRoomIntersections(rooms, stack));
				continue;
			}	

			// TODO special case for <1 lenth rooms.... needs to be a 1 block which would just be the door.
			// or indicated something special
			
			/*
			 *  build L-shaped line
			 */
			ICoords r1c = room1.getCenter();
			Wayline wayline = null;
//			if (room2.getMinX() + 1 > room1.getMaxX() - 1) {
			if (room2.getCenter().getX() > room1.getCenter().getX()) {
				// room2 is to the right (positive-x) of room 1
				/*
				 * NOTE 8/31/2017 this comment only  concerning rooms where the Y-value is not the same - which
				 * has not been implemented yet.
				 * 
				 * this part is incorrect or just doesn't contain enough data.  this is a L-shaped wayline and therefor
				 * point2 doesn't connect to a room by default.  however it is assigned the room2's ID.
				 * this will cause incorrect calculations when attempting to generate hallsways as room2's Y value
				 * will be looked at and  not any intermediate intersecting room's Y (the hallway doesn't need to "climb back"
				 * up to room2's Y). needs to be flagged with a value to indicate that it is not terminal and therefor it can be
				 * generated like a corner. as well need a reference to the next piece of the wayline corner so no overlap of blocks
				 * is generated
				 */
				wayline = new Wayline(new Waypoint(room1.getId(), room1.getMaxX()+1, 0, r1c.getZ()), 
						new Waypoint(room2.getId(), room2.getCenter().getX(), 0, r1c.getZ(), false));
			}
			else {
				wayline = new Wayline(new Waypoint(room1.getId(), room1.getMinX()-1, 0, r1c.getZ()), 
						new Waypoint(room2.getId(), room2.getCenter().getX(), 0, r1c.getZ(), false));
			}
			
			if (wayline.getPoint1().getCoords().equals(wayline.getPoint2().getCoords())) {
				Dungeons2.log.warn("Wayline's points are equal !!: " + wayline);
			}
			stack.add(wayline);
			List<Wayline> segmented = resolveWaylineRoomIntersections(rooms, stack);
			waylines.addAll(segmented);
			// search the list for the non-terminated wayline
			Optional<Wayline> arm1 = segmented.stream()
				.filter(x -> !x.getPoint1().isTerminated() || !x.getPoint2().isTerminated()).findFirst();
						
			// room2 is down (postivie-z) of room 1
			if (room2.getCenter().getZ() > room1.getCenter().getZ()) {
				wayline = new Wayline(new Waypoint(room1.getId(), room2.getCenter().getX(), 0, r1c.getZ(), false),
						new Waypoint(room2.getId(), room2.getCenter().getX(), 0, room2.getMinZ()-1));
			}
			// room2 is up (negative-z) of room 1
			else {
				wayline = new Wayline(new Waypoint(room1.getId(), room2.getCenter().getX(), 0, r1c.getZ(), false),
						new Waypoint(room2.getId(), room2.getCenter().getX(), 0, room2.getMaxZ()+1));
			}
			
			if (wayline.getPoint1().getCoords().equals(wayline.getPoint2().getCoords())) {
				Dungeons2.log.warn("Wayline's points are equal !!: " + wayline);
			}
			
			stack.add(wayline);
			segmented = resolveWaylineRoomIntersections(rooms, stack);
			waylines.addAll(segmented);
			
			// search the list for the non-terminated wayline
			Optional<Wayline> arm2 = segmented.stream()
					.filter(x -> !x.getPoint1().isTerminated() || !x.getPoint2().isTerminated()).findFirst();
			
			// if both parts of L-shaped (elbow join) wayline are found, then set to reference each other
			if (arm1.isPresent() && arm2.isPresent() && arm1.get() != null && arm2.get() != null) {
				arm1.get().setWayline(arm2.get());
				arm2.get().setWayline(arm1.get());
			}
		}

		return waylines;
	}

	/**
	 * @param rooms
	 * @param stack
	 * @return 
	 */
	protected List<Wayline> resolveWaylineRoomIntersections(List<Room> rooms, Stack<Wayline> stack) {
		List<Wayline> waylines = new ArrayList<>();
		List<Wayline> result = new ArrayList<>();
		int failSafeLimit = rooms.size() * 3;
		int failSafeCount = 0;
		do {
			failSafeCount++;
			Wayline wayline = stack.pop();
			//=======
			for (Room room : rooms) {
//				Dungeons2.log.info(String.format("Checking against room [%d]", room.getId()));
				if (wayline == null) {
					Dungeons2.log.trace("Wayline is null on room:" + room.getId());
					break;
					}
				// construct a BB for line
				AxisAlignedBB bb1 = new AxisAlignedBB(
						wayline.getPoint1().getX(), 0, wayline.getPoint1().getZ(),
						wayline.getPoint2().getX(), 1, wayline.getPoint2().getZ());
				
				/*
				 * TODO NOTE encountered a special use case where a wayline appears to connect start room to end room.
				 * the wayline intersects both rooms and enters an endless loop. this needs to be solved.
				 * The wayline that are generated are 0-length and 1-length.  the zero length should be removed.
				 * Probably means that two rooms intersect on a wall and a wayline is build between them.
				 * In the meantime and for good measure in general, add a failsafe
				 */
				// test intersection with room
				if (wayline.getPoint1().getCoords().getDistance(wayline.getPoint2().getCoords()) > 0 && bb1.intersects(room.getXZBoundingBox())) {
					Dungeons2.log.trace(String.format("Room [%d] intersection with wayline %s", room.getId(), wayline));
//					wayline = resolveWaylineRoomIntersection(room, wayline, waylines);
					waylines = resolveWaylineRoomIntersection(room, wayline);
					if (waylines != null && waylines.size() > 0) {
//						Dungeons2.log.debug("Adding new waylines to stack.");
						stack.addAll(waylines);
						wayline = null;
					}
					break;
				}
//				else {
//					result.add(wayline);
//				}
			}
			if (wayline != null) result.add(wayline);
			
			//===========
			if (failSafeCount >= failSafeLimit) {
				return EMPTY_WAYLINES;
			}
		} while (!stack.isEmpty());
		return result;
	}

	/**
	 * @param room
	 * @param wayline
	 * @return
	 */
	protected List<Wayline> resolveWaylineRoomIntersection(Room room, Wayline wayline) {
		List<Wayline> waylines = new ArrayList<>();
		Wayline remainderWayline1 = null;
		Wayline remainderWayline2 = null;

		// determine if horizontal or vertical line
		boolean isHorz = false;
//		if (wayline.getPoint1().getZ() == wayline.getPoint2().getZ()) isHorz = true;
		isHorz = wayline.getAlignment() == Alignment.HORIZONTAL ? true : false;
		
		// create new waylines
		Waypoint p1 = wayline.getPoint1();
		Waypoint p2 = wayline.getPoint2();
		Waypoint terminatedPoint = null;
		if (isHorz) {

			// determine if a point terminates in room
			if (wayline.getPoint1().getX() >= room.getMinX() && wayline.getPoint1().getX() <= room.getMaxX()) {
				terminatedPoint = wayline.getPoint1();
				p1 = wayline.getPoint2();
				p2 = wayline.getPoint1();
			}
			else if (wayline.getPoint2().getX() >= room.getMinX() && wayline.getPoint2().getX() <= room.getMaxX()) {
				terminatedPoint = wayline.getPoint2();
				p1 = wayline.getPoint1();
				p2 = wayline.getPoint2();
			}
			else {
				// no termination - line right through
			}
			
			// left to right
			if (p1.getX() < p2.getX() /*&& wayline.getPoint1() != terminatedPoint*/) {
				remainderWayline1 = new Wayline(p1, new Waypoint(room.getId(), room.getMinX(), p1.getY(), p1.getZ()), Alignment.HORIZONTAL);
				if (terminatedPoint == null) {
					remainderWayline2 = new Wayline(new Waypoint(room.getId(), room.getMaxX()+1, p2.getY(), p2.getZ(), p2.isTerminated()), p2, Alignment.HORIZONTAL);
				}
			}
			// right to left
			else {				
				remainderWayline1 = new Wayline(p1, new Waypoint(room.getId(), room.getMaxX()+1, p1.getY(), p1.getZ()), Alignment.HORIZONTAL);
				if (terminatedPoint == null) {
					remainderWayline2 = new Wayline(new Waypoint(room.getId(), room.getMinX(), p2.getY(), p2.getZ(), p2.isTerminated()), p2, Alignment.HORIZONTAL);
				}
			}
		}
		// vertical
		else {
			
			// determine if a point terminates in room
			if (wayline.getPoint1().getZ() >= room.getMinZ() && wayline.getPoint1().getZ() <= room.getMaxZ()) {
				terminatedPoint = wayline.getPoint1();
				p1 = wayline.getPoint2();
				p2 = wayline.getPoint1();
			}
			else if (wayline.getPoint2().getZ() >= room.getMinZ() && wayline.getPoint2().getZ() <= room.getMaxZ()) {
				terminatedPoint = wayline.getPoint2();
				p1 = wayline.getPoint1();
				p2 = wayline.getPoint2();
			}
			else {
			}
			
			// up
			if (p1.getZ() > p2.getZ()) {
				remainderWayline1 = new Wayline(p1, new Waypoint(room.getId(), p1.getX(), p1.getY(), room.getMaxZ()+1), Alignment.VERTICAL);
				if (terminatedPoint == null)
					remainderWayline2 = new Wayline(new Waypoint(room.getId(), p2.getX(), p2.getY(), room.getMinZ(), p2.isTerminated()), p2, Alignment.VERTICAL);
			}
			// down
			else {
				remainderWayline1 = new Wayline(p1, new Waypoint(room.getId(), p1.getX(), p1.getY(), room.getMinZ()), Alignment.VERTICAL);
				if (terminatedPoint == null)
					remainderWayline2 = new Wayline(new Waypoint(room.getId(), p2.getX(), p2.getY(), room.getMaxZ()+1, p2.isTerminated()), p2, Alignment.VERTICAL);
			}		
		}
			
		// add the new wayline to the list
		if (remainderWayline1 != null) {
			if (remainderWayline1.getPoint1().getCoords().equals(wayline.getPoint2().getCoords())) {
				Dungeons2.log.trace("Remainder Wayline1's points are equal !!: " + remainderWayline1);
			}
			waylines.add(remainderWayline1);
		}
		
		if (remainderWayline2 != null) {
			if (remainderWayline2.getPoint1().getCoords().equals(wayline.getPoint2().getCoords())) {
				Dungeons2.log.trace("Remainder Wayline2's points are equal !!: " + remainderWayline2);
			}
			waylines.add(remainderWayline2);
		}
		return waylines;
	}

	/**
	 * 
	 * @param rooms
	 * @param wayline
	 * @return
	 */
	@Deprecated
	protected List<Wayline> resolveWaylineRoomIntersections(List<Room> rooms, Wayline wayline) {
		List<Wayline> waylines = new ArrayList<>();
		
		for (Room room : rooms) {
			Dungeons2.log.trace(String.format("Checking against room [%d]", room.getId()));
			if (wayline == null) {
				Dungeons2.log.debug("Wayline is null on room:" + room.getId());
				break;
				}
			// construct a BB for line
			AxisAlignedBB bb1 = new AxisAlignedBB(
					wayline.getPoint1().getX(), 0, wayline.getPoint1().getZ(),
					wayline.getPoint2().getX(), 1, wayline.getPoint2().getZ());
			
			// test intersection with room
			if (bb1.intersects(room.getBoundingBox())) {
				Dungeons2.log.trace(String.format("Room [%d] intersection with wayline %s", room.getId(), wayline));
				wayline = resolveWaylineRoomIntersection(room, wayline, waylines);
			}
		}
		if (wayline != null) waylines.add(wayline);	
		return waylines;
	}
	
	/**
	 * 
	 * @param room
	 * @param wayline
	 * @param waylines
	 * @return
	 */
	@Deprecated
	protected Wayline resolveWaylineRoomIntersection(Room room, Wayline wayline, List<Wayline> waylines) {
		Wayline newWayline = null;
		Wayline remainderWayline = null;
		
		// determine if horizontal or vertical line
		boolean isHorz = false;
		if (wayline.getPoint1().getZ() == wayline.getPoint2().getZ()) isHorz = true;

		
		// create new waylines
//		Dungeons2.log.info("WP1: " + wayline.getPoint1());
//		Dungeons2.log.info("WP2: " + wayline.getPoint2());
//		Dungeons2.log.info("Room: " + room);
		Waypoint p1 = wayline.getPoint1();
		Waypoint p2 = wayline.getPoint2();
		Waypoint terminatedPoint = null;
//		Waypoint startPoint = null;
		if (isHorz) {

			// determine if a point terminates in room
			if (wayline.getPoint1().getX() >= room.getMinX() && wayline.getPoint1().getX() <= room.getMaxX()) {
				terminatedPoint = wayline.getPoint1();
//				Dungeons2.log.info("Terminating H point:"  + terminatedPoint);
				p1 = wayline.getPoint2();
				p2 = wayline.getPoint1();
			}
			else if (wayline.getPoint2().getX() >= room.getMinX() && wayline.getPoint2().getX() <= room.getMaxX()) {
				terminatedPoint = wayline.getPoint2();
//				Dungeons2.log.info("Terminating H Else point:"  + terminatedPoint);
				p1 = wayline.getPoint1();
				p2 = wayline.getPoint2();
			}
			else {
//				Dungeons2.log.info("Skipped H termination point altogether somehow!");
				// no termination - line right through
			}
			
//			if (pgood.getX() > pbad.getX()) {
			if (p1.getX() < p2.getX() /*&& wayline.getPoint1() != terminatedPoint*/) {
				newWayline = new Wayline(p1, new Waypoint(room.getId(), room.getMinX(), p1.getY(), p1.getZ()));
//				Dungeons2.log.info("Building wayline (R) from P1 -> Room.MinX");
				if (terminatedPoint == null) {
					remainderWayline = new Wayline(new Waypoint(room.getId(), room.getMaxX(), p2.getY(), p2.getZ()), p2);
				}
			}
			else {				
				newWayline = new Wayline(p1, new Waypoint(room.getId(), room.getMaxX(), p1.getY(), p1.getZ()));
//				Dungeons2.log.info("Building wayline (L) from P1 -> Room.MaxX");
				if (terminatedPoint == null) {
					remainderWayline = new Wayline(new Waypoint(room.getId(), room.getMinX(), p2.getY(), p2.getZ()), p2);
				}
			}
		}
		// vertical
		else {
			
			// determine if a point terminates in room
			if (wayline.getPoint1().getZ() >= room.getMinZ() && wayline.getPoint1().getZ() <= room.getMaxZ()) {
				terminatedPoint = wayline.getPoint1();
//				Dungeons2.log.info("Terminating V point:"  + terminatedPoint);
				p1 = wayline.getPoint2();
				p2 = wayline.getPoint1();
			}
			else if (wayline.getPoint2().getZ() >= room.getMinZ() && wayline.getPoint2().getZ() <= room.getMaxZ()) {
				terminatedPoint = wayline.getPoint2();
//				Dungeons2.log.info("Terminating V Else point:"  + terminatedPoint);
				p1 = wayline.getPoint1();
				p2 = wayline.getPoint2();
			}
			else {
//				Dungeons2.log.info("Skipped V termination point altogether somehow!");
			}
			
			// up
			if (p1.getZ() > p2.getZ()) {
				newWayline = new Wayline(p1, new Waypoint(room.getId(), p1.getX(), p1.getY(), room.getMaxZ()));
				if (terminatedPoint == null)
					remainderWayline = new Wayline(new Waypoint(room.getId(), p2.getX(), p2.getY(), room.getMinZ()), p2);
			}
			// down
			else {
				newWayline = new Wayline(p1, new Waypoint(room.getId(), p1.getX(), p1.getY(), room.getMinZ()));
				if (terminatedPoint == null)
					remainderWayline = new Wayline(new Waypoint(room.getId(), p2.getX(), p2.getY(), room.getMaxZ()), p2);
			}		
		}
			
		// add the new wayline to the list
		waylines.add(newWayline);
		
		// return the remainer wayline
		return remainderWayline;
	}
	
	/**
	 * 
	 * @param m
	 */
	public static void printMatrix(double[][] m){
		try{
			int rows = m.length;
			int columns = m[0].length;
			String str = "|\t";

			for(int i=0;i<rows;i++){
				for(int j=0;j<columns;j++){
					str += ((int)m[i][j]) + "\t";
				}

				System.out.println(str + "|");
				str = "|\t";
			}

		}catch(Exception e){System.out.println("Matrix is empty!!");}
	}

	/**
	 * 
	 * @param world
	 * @param rand
	 * @param rooms
	 * @param config
	 * @return
	 */
	protected List<Room> selectValidRooms(World world, Random rand, List<Room> rooms, ILevelConfig config) {
		List<Room> met = new ArrayList<>();
		int roomId = 0;
		AxisAlignedBB dbb = getDungeonBuilder().getBoundary();
		AxisAlignedBB lbb = getBoundary(); // getDungeonBuilder().getBoundary();
		
		for (Room room : rooms) {
			if (room.isObstacle()) continue;

			// NOTE at this point it is assumed any anchors are pre-validated and meet all criteria
			if (room.isAnchor()) {
				room.setId(roomId++);
				met.add(room);
				continue;
			}

			boolean isValid = false;

			// check if the room is inside the level bounding box
			AxisAlignedBB rbb = room.getXZBoundingBox();
			if ((rbb.minX >= lbb.minX && rbb.maxX <= lbb.maxX) &&
					(rbb.minZ >= lbb.minZ && rbb.maxZ <= lbb.maxZ)) {
				isValid = true;
			}
			else {
				Dungeons2.log.debug("Removing room for being outside level bounds -> {}", room);
				System.out.println("Removing room for being outside level bounds -> " +  room);
				incrementLossToValidation(1);
			}
			
			if (isValid &&
					(rbb.minX >= dbb.minX && rbb.maxX <= dbb.maxX) &&
					(rbb.minZ >= dbb.minZ && rbb.maxZ <= dbb.maxZ)) {
				isValid = true;
			}
			else {
				Dungeons2.log.debug("Removing room for being outside dungeon bounds -> {}", room.getId());
				System.out.println("Removing room for being outside dungeon bounds -> " +  room.getId());
				incrementLossToValidation(1);
			}
			
			// TODO move to method
			// check if the chunk is loaded
			if (isValid && getDungeonBuilder().getConfig().isMinecraftConstraints()) {
				isValid = false;
				
				if (isRoomInLoadedChunks(world, room)) {
					isValid = true;
//					Dungeons2.log.debug("room[{}] is VALID at chuck(s) -> {} {}", room.getId(), room.getXZCenter(), Dungeons2.toChunk(room.getXZCenter()));
				}
				else {
//					Dungeons2.log.debug("room[{}] is NOT valid at chuck(s) -> {}", room.getId(), room.getXZCenter());
					Dungeons2.log.debug("Removing room for residing in unloaded chunk -> {}", room.getId());
					incrementLossToValidation(1);
				}

			}
			
			// check if room meets all criteria/constraints for generation
			if (isValid) {
				isValid = validateRoomConstraints(world, room, config);
			}
			else {
				Dungeons2.log.debug("Removing room for failing constraints -> {}", room);
				incrementLossToValidation(1);
			}
			
			if (isValid) {
				// assign a new id to room
				room.setId(roomId++);
				// add room
				met.add(room);		
			}
			else {
				Dungeons2.log.debug("Removing room for failing constraints -> {}", room);
				incrementLossToValidation(1);
			}
		}
		return met;
	}
	
	/**
	 * LevelVisualizer against all build criteria. Rooms that don't meet criteria are removed from the list.
	 * @param rand
	 * @param rooms
	 * @param config
	 * @return
	 */
	@Deprecated
	protected List<Room> selectValidRooms(World world, Random rand, List<Room> rooms, LevelConfig config) {
		List<Room> met = new ArrayList<>();
		int roomId = 0;
		AxisAlignedBB lbb = getBoundary(); // <-- should be passed in.
		
		for (Room room : rooms) {
			if (room.isObstacle()) {
				continue;
			}

			// NOTE at this point it is assumed any anchors are pre-validated and meet all criteria
			if (room.isAnchor()) {
				room.setId(roomId++);
				met.add(room);
				continue;
			}

			boolean isValid = false;

			// check if the room is inside the level bounding box
//			AxisAlignedBB lbb = getField();
			AxisAlignedBB rbb = room.getXZBoundingBox();
			if (rbb.minX > lbb.minX
					|| rbb.maxX < lbb.maxX) {
				isValid = true;
			}
			else {
				Dungeons2.log.debug("Removing room for being outside field bounds -> {}", room);
				System.out.println("Removing room for being outside field bounds -> " +  room);
				incrementLossToValidation(1);
			}
			
			// TODO move to method
			// check if the chunk is loaded
			if (isValid && getConfig().isMinecraftConstraintsOn()) {
				isValid = false;
				
				if (isRoomInLoadedChunks(world, room)) {
					isValid = true;
//					Dungeons2.log.debug("room[{}] is VALID at chuck(s) -> {} {}", room.getId(), room.getXZCenter(), Dungeons2.toChunk(room.getXZCenter()));
				}
				else {
//					Dungeons2.log.debug("room[{}] is NOT valid at chuck(s) -> {}", room.getId(), room.getXZCenter());
					Dungeons2.log.debug("Removing room for residing in unloaded chunk -> {}", room.getId());
					incrementLossToValidation(1);
				}

			}
			
			// check if room meets all criteria/constraints for generation
			if (isValid) {
				isValid = meetsRoomConstraints(world, room, config);
			}
			else {
				Dungeons2.log.debug("Removing room for failing constraints -> {}", room);
				incrementLossToValidation(1);
			}
			
			if (isValid) {
				// assign a new id to room
				room.setId(roomId++);
				// add room
				met.add(room);			
			}
		}
		return met;
	}

	/**
	 * 
	 * @param world
	 * @param room
	 * @return
	 */
	public boolean isRoomInLoadedChunks(World world, Room room) {
		boolean isValid = false;
		// check each corner
		ChunkPos[] cp = room.getCornersInChunkPos();
		
		if (
				world.isChunkGeneratedAt(cp[0].x, cp[0].z) &&
				world.isChunkGeneratedAt(cp[1].x, cp[1].z) &&
				world.isChunkGeneratedAt(cp[2].x, cp[2].z) &&
				world.isChunkGeneratedAt(cp[3].x, cp[3].z)				
				) isValid = true;			
		
		return isValid;
	}
	
	/**
	 * Ensure the room meets are criteria to be built.
	 * @param world 
	 * @param room
	 * @return
	 */
	protected boolean validateRoomConstraints(World world, Room room, ILevelConfig config) {
		if (room == null || room.isReject()) return false;
		if (!getDungeonBuilder().getConfig().isMinecraftConstraints()) return true; 
		// ensure that the room is above the bottom
		if (room.getCoords().getY() <= getDungeonBuilder().getConfig().getBottomLimit()) {
			if (Dungeons2.log.isDebugEnabled()) {
				Dungeons2.log.debug("Room bottom [{}] is below min y constraint [{}]", 
						room.getCoords().getY(), 
						getDungeonBuilder().getConfig().getBottomLimit());
			}
			return false;
		}

		// ensure the room is below the y max threshold
		if (room.getCoords().getY() + room.getHeight() > getDungeonBuilder().getConfig().getTopLimit()) {
			if (Dungeons2.log.isDebugEnabled()) {
				Dungeons2.log.debug(
					String.format("Room top [%d] is above max y constraint [%d]", 
							(room.getCoords().getY() + room.getHeight()), 
							getDungeonBuilder().getConfig().getTopLimit()));
			}
			return false;
		}

		// NOTE these next 3 calls a) take ~1 second to complete and b) cause cascading worldgen lag because they access the world in non-loaded chunks.
		// get percentage of solid base blocks
		double percentSolid = WorldInfo.getSolidBasePercent(world, room.getCoords(), room.getWidth(), room.getDepth());
//		Dungeons2.log.debug("Percent solid base:" + percentSolid);
		
		// get the depth from the surface to top of the room
		int surfaceRoomDepth = WorldInfo.getDifferenceWithSurface(world, room.getCenter());
//		Dungeons2.log.debug("The surface/room depth =" + surfaceRoomDepth);
		if (surfaceRoomDepth == WorldInfo.INVALID_SURFACE_POS) {
			Dungeons2.log.debug("Unable to locate the surface position.");
			return false;
		}
		
		// check if the top y valueof the node is above sea level
		if (room.getCoords().getY() + room.getHeight() > world.getSeaLevel()) {
			
			Dungeons2.log.trace("Room is above sea level -> {}", room.getCenter());
			/*
			 *  if surfaceRoomDepth is greater than a [x] negative amount.
			 *  negative implies the room is higher than the surface, ie the room is exposed.
			 */
			if (surfaceRoomDepth < -3) { // TODO make -3 a constant or a config value
				Dungeons2.log.debug("Room rejected due to exposure -> {}", room.getCenter());
				return false;
			}			
			else if (percentSolid < 50.0f) {
				Dungeons2.log.debug("Room has less than 50 % base @ " + room.getCenter());
				/*
				 * there is less than 50% solid base
				 */
				return false;
			}
		}
		else {
//			Dungeons2.log.debug("Room is below sea level @ " + room.getCenter());
			if (percentSolid < 20.0f) {
				Dungeons2.log.debug("Room has less than 20 % base @ " + room.getCenter());
				/*
				 * 0-20% = mostly likely suspended over a chasm/pit/ravine
				 */
				return false;
			}
			else if (percentSolid < 50.0f) {
				Dungeons2.log.debug("Room has less than 50 % base @ " + room.getCenter());
				/*
				 * 21-40 = overrhanging a chasm/pit/ravine
				 */
				return false;
			}
		}		
		return true;
	}
	
	/**
	 * Ensure the room meets are criteria to be built.
	 * @param world 
	 * @param room
	 * @return
	 */
	@Deprecated
	protected boolean meetsRoomConstraints(World world, Room room, LevelConfig config) {
		if (room == null || room.isReject()) return false;
		if (!config.isMinecraftConstraintsOn()) return true;
		
		// ensure that the room is above the bottom
		if (room.getCoords().getY() <= config.getYRange().getMinInt()) {
			if (Dungeons2.log.isDebugEnabled()) {
				Dungeons2.log.debug("Room bottom [{}] is below min y constraint [{}]", room.getCoords().getY(), config.getYRange().getMinInt());
			}
			return false;
		}

		// ensure the room is below the y max threshold
		if (room.getCoords().getY() + room.getHeight() > config.getYRange().getMaxInt()) {
			if (Dungeons2.log.isDebugEnabled()) {
				Dungeons2.log.debug(
					String.format("Room top [%d] is above max y constraint [%d]", (room.getCoords().getY() + room.getHeight()), config.getYRange().getMaxInt()));
			}
			return false;
		}

		// NOTE these next 3 calls a) take ~1 second to complete and b) cause cascading worldgen lag because they access the world in non-loaded chunks.
		// get percentage of solid base blocks
		double percentSolid = WorldInfo.getSolidBasePercent(world, room.getCoords(), room.getWidth(), room.getDepth());
//		Dungeons2.log.debug("Percent solid base:" + percentSolid);
		
		// get the depth from the surface to top of the room
		int surfaceRoomDepth = WorldInfo.getDifferenceWithSurface(world, room.getCenter());
//		Dungeons2.log.debug("The surface/room depth =" + surfaceRoomDepth);
		if (surfaceRoomDepth == WorldInfo.INVALID_SURFACE_POS) {
			Dungeons2.log.debug("Unable to locate the surface position.");
			return false;
		}
		
		// check if the top y valueof the node is above sea level
		if (room.getCoords().getY() + room.getHeight() > config.getSeaLevel()) {
			Dungeons2.log.trace("Room is above sea level -> {}", room.getCenter());
			/*
			 *  if surfaceRoomDepth is greater than a [x] negative amount.
			 *  negative implies the room is higher than the surface, ie the room is exposed.
			 */
			if (surfaceRoomDepth < -3) { // TODO make -3 a constant or a config value
				Dungeons2.log.debug("Room rejected due to exposure -> {}", room.getCenter());
				return false;
			}			
			else if (percentSolid < 50.0f) {
				Dungeons2.log.debug("Room has less than 50 % base @ " + room.getCenter());
				/*
				 * there is less than 50% solid base
				 */
				return false;
			}
		}
		else {
//			Dungeons2.log.debug("Room is below sea level @ " + room.getCenter());
			if (percentSolid < 20.0f) {
				Dungeons2.log.debug("Room has less than 20 % base @ " + room.getCenter());
				/*
				 * 0-20% = mostly likely suspended over a chasm/pit/ravine
				 */
				return false;
			}
			else if (percentSolid < 50.0f) {
				Dungeons2.log.debug("Room has less than 50 % base @ " + room.getCenter());
				/*
				 * 21-40 = overrhanging a chasm/pit/ravine
				 */
				return false;
			}
		}		
		return true;
	}
	
	/**
	 * Builds a room at the centered on the startPoint.
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @param config
	 * @return
	 */
	protected Room buildStartRoom(World world, Random rand, ICoords startPoint, LevelConfig config) {
		/*
		 * the start of the level
		 */
		Room startRoom = new Room().setStart(true).setAnchor(true).setType(Type.LADDER);
		startRoom = randomizeDimensions(rand, startRoom, config);
		// ensure min dimensions are met for start room
		startRoom.setWidth(Math.max(7, startRoom.getWidth()));
		startRoom.setDepth(Math.max(7,  startRoom.getDepth()));
		// ensure that start room's dimensions are odd in length
		if (startRoom.getWidth() % 2 == 0) startRoom.setWidth(startRoom.getWidth()+1);
		if (startRoom.getDepth() % 2 == 0) startRoom.setDepth(startRoom.getDepth()+1);
		
		// set the starting room coords to be in the middle of the start point
		startRoom.setCoords(
				new Coords(startPoint.getX()-(startRoom.getWidth()/2),
						startPoint.getY(),
						startPoint.getZ()-(startRoom.getDepth()/2)));
		//startRoom.setDistance(startRoom.getCoords().getDistanceSq(startPoint));
		startRoom.setDistance(0.0);
		// randomize a direction
		startRoom.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));
		// test if the room meets conditions to be placed in the minecraft world
		if (!meetsRoomConstraints(world, startRoom, config)) {
			Dungeons2.log.debug("Start Room failed room constraints @ " + startRoom.getCenter());
			if (Dungeons2.log.isWarnEnabled()) {
				Dungeons2.log.warn(String.format("Start Room has invalid Minecraft world room conditions: %s", startRoom.toString()));
			}
			return EMPTY_ROOM;
		}
		return startRoom;
	}
	
	/**
	 * Builds a room at the centered on the startPoint.
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @param config
	 * @return
	 */
	protected Room buildStartRoom(World world, Random rand, AxisAlignedBB field, ICoords startPoint, ILevelConfig config) {
		/*
		 * the start of the level
		 */
		Room startRoom = new Room().setStart(true).setAnchor(true).setType(Type.LADDER);
		startRoom = randomizeDimensions(rand, startRoom, config);
		// ensure min dimensions are met for start room
		startRoom.setWidth(Math.max(MIN_START_ROOM_SIZE, startRoom.getWidth()));
		startRoom.setDepth(Math.max(MIN_START_ROOM_SIZE,  startRoom.getDepth()));
		// ensure that start room's dimensions are odd in length
		if (startRoom.getWidth() % 2 == 0) startRoom.setWidth(startRoom.getWidth()+1);
		if (startRoom.getDepth() % 2 == 0) startRoom.setDepth(startRoom.getDepth()+1);
		
		// set the starting room coords to be in the middle of the start point
		startRoom.centerOn(startPoint);
//		.setCoords(
//				new Coords(startPoint.getX()-(startRoom.getWidth()/2),
//						startPoint.getY(),
//						startPoint.getZ()-(startRoom.getDepth()/2)));
		//startRoom.setDistance(startRoom.getCoords().getDistanceSq(startPoint));
		startRoom.setDistance(0.0);
		// randomize a direction
		startRoom.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));
		// test if the room meets conditions to be placed in the minecraft world
		// TEMP remove
		if (!validateRoomConstraints(world, startRoom, config)) {
			Dungeons2.log.debug("Start Room failed room constraints @ " + startRoom.getCenter());
			if (Dungeons2.log.isWarnEnabled()) {
				Dungeons2.log.warn(String.format("Start Room has invalid Minecraft world room conditions: %s", startRoom.toString()));
			}
			return EMPTY_ROOM;
		}
		
		return startRoom;
	}
	
	/**
	 * Builds a room at the centered on the startPoint.
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @param config
	 * @return
	 */
	@Deprecated
	protected Room buildStartRoom(World world, Random rand, AxisAlignedBB field, ICoords startPoint, LevelConfig config) {
		/*
		 * the start of the level
		 */
		Room startRoom = new Room().setStart(true).setAnchor(true).setType(Type.LADDER);
		startRoom = randomizeDimensions(rand, startRoom, config);
		// ensure min dimensions are met for start room
		startRoom.setWidth(Math.max(7, startRoom.getWidth()));
		startRoom.setDepth(Math.max(7,  startRoom.getDepth()));
		// ensure that start room's dimensions are odd in length
		if (startRoom.getWidth() % 2 == 0) startRoom.setWidth(startRoom.getWidth()+1);
		if (startRoom.getDepth() % 2 == 0) startRoom.setDepth(startRoom.getDepth()+1);
		
		// set the starting room coords to be in the middle of the start point
		startRoom.setCoords(
				new Coords(startPoint.getX()-(startRoom.getWidth()/2),
						startPoint.getY(),
						startPoint.getZ()-(startRoom.getDepth()/2)));
		//startRoom.setDistance(startRoom.getCoords().getDistanceSq(startPoint));
		startRoom.setDistance(0.0);
		// randomize a direction
		startRoom.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));
		// test if the room meets conditions to be placed in the minecraft world
		// TEMP remove
		if (!meetsRoomConstraints(world, startRoom, config)) {
			Dungeons2.log.debug("Start Room failed room constraints @ " + startRoom.getCenter());
			if (Dungeons2.log.isWarnEnabled()) {
				Dungeons2.log.warn(String.format("Start Room has invalid Minecraft world room conditions: %s", startRoom.toString()));
			}
			return EMPTY_ROOM;
		}
		
		return startRoom;
	}

	/**
	 * 
	 * @param world
	 * @param rand
	 * @param field
	 * @param startPoint
	 * @param plannedRooms
	 * @param config
	 * @return
	 */
	protected Room buildEndRoom(World world, Random rand,
			AxisAlignedBB field, ICoords startPoint, List<Room> plannedRooms, ILevelConfig config) {
		/*
		 * the end room of the level.
		 */

		/*
		 * change the distance that the end room can be from startpoint.
		 * (this chance only affects the end room).
		 */
		// NOTE distance no longer matters as using the field
//		double factor = 2.0;
//		ILevelConfig c2 = new LevelConfig(config);
//		Quantity qx = new Quantity(c2.getXDistance().getMin(), c2.getXDistance().getMax()*factor);
//		Quantity qz = new Quantity(c2.getZDistance().getMin(), c2.getZDistance().getMax()*factor);
//		c2.setXDistance(qx);
//		c2.setZDistance(qz);
		
		// build the end room
		Room endRoom  = buildPlannedRoom(world, rand, field, startPoint, plannedRooms, config)
				.setEnd(true)
				.setAnchor(true)
				.setType(Type.LADDER);
		
		// ensure min dimensions are met for start room
		endRoom.setWidth(Math.max(MIN_START_ROOM_SIZE, endRoom.getWidth()));
		endRoom.setDepth(Math.max(MIN_START_ROOM_SIZE,  endRoom.getDepth()));
		// ensure that the room's dimensions are odd in length
		if (endRoom.getWidth() % 2 == 0) endRoom.setWidth(endRoom.getWidth()+1);
		if (endRoom.getDepth() % 2 == 0) endRoom.setDepth(endRoom.getDepth()+1);
		
		return endRoom;
	}

	
	/**
	 * @param world
	 * @param rand
	 * @param levelBuilder 
	 * @param plannedRooms 
	 * @param levelConfig
	 * @return
	 */
	@Deprecated
	protected Room buildEndRoom(World world, Random rand,
			AxisAlignedBB field, ICoords startPoint, List<Room> plannedRooms, LevelConfig config) {
		/*
		 * the end room of the level.
		 */

		/*
		 * change the distance that the end room can be from startpoint.
		 * (this chance only affects the end room).
		 */
		double factor = 2.0;
		LevelConfig c2 = new LevelConfig(config);
		Quantity qx = new Quantity(c2.getXDistance().getMin(), c2.getXDistance().getMax()*factor);
		Quantity qz = new Quantity(c2.getZDistance().getMin(), c2.getZDistance().getMax()*factor);
		c2.setXDistance(qx);
		c2.setZDistance(qz);
		
		// build the end room
		Room endRoom  = buildPlannedRoom(world, rand, field, startPoint, plannedRooms, c2).setEnd(true).setAnchor(true).setType(Type.LADDER);
		// ensure min dimensions are met for start room
		endRoom.setWidth(Math.max(7, endRoom.getWidth()));
		endRoom.setDepth(Math.max(7,  endRoom.getDepth()));
		// ensure that the room's dimensions are odd in length
		if (endRoom.getWidth() % 2 == 0) endRoom.setWidth(endRoom.getWidth()+1);
		if (endRoom.getDepth() % 2 == 0) endRoom.setDepth(endRoom.getDepth()+1);
		
		return endRoom;
	}

//	/**
//	 * 
//	 * @param world
//	 * @param rand
//	 * @param startPoint
//	 * @param config
//	 * @return
//	 */
//	protected Room buildPlannedRoom(World world, Random rand, ICoords startPoint, List<Room> plannedRooms, LevelConfig config) {
//		Room plannedRoom = new Room();
//		
//		/* 
//		 * check to make sure planned rooms don't intersect.
//		 * test up to 10 times for a successful position
//		 */
//		boolean checkRooms = true;
//		int endCheckIndex = 0;
//		checkingRooms:
//		do {
//			plannedRoom = randomizeRoom(rand, plannedRoom, startPoint, config);
//			Dungeons2.log.debug("New Planned Room:" + plannedRoom);
//			endCheckIndex++;
//			if (endCheckIndex > 10) {
//				Dungeons2.log.warn("Unable to position Planned Room that meets positional criteria.");
//				return EMPTY_ROOM;
//			}
//			for (Room room : plannedRooms) {
//				if (room.getXZBoundingBox().intersects(plannedRoom.getXZBoundingBox())) {
//					Dungeons2.log.debug("New Planned room intersects with planned list room.");
//					continue checkingRooms;
//				}
//			}
//			// test if the room meets conditions to be placed in the minecraft world
//			if (!meetsRoomConstraints(world, plannedRoom, config)) {
//				break;
//			}			
//			checkRooms = false;			
//		} while (checkRooms);		
//		return plannedRoom;
//	}

	/**
	 * 
	 * @param world
	 * @param rand
	 * @param field
	 * @param startPoint
	 * @param plannedRooms
	 * @param config
	 * @return
	 */
	protected Room buildPlannedRoom(World world, Random rand, AxisAlignedBB field, 
			ICoords startPoint, List<Room> plannedRooms, ILevelConfig config) {
		Room plannedRoom = new Room();
		
		/* 
		 * check to make sure planned rooms don't intersect.
		 * test up to 10 times for a successful position
		 */
		boolean checkRooms = true;
		int endCheckIndex = 0;
		checkingRooms:
		do {
			plannedRoom = randomizeRoom(rand, plannedRoom, field, startPoint, config);
			Dungeons2.log.debug("New Planned Room:" + plannedRoom);
			endCheckIndex++;
			if (endCheckIndex > 10) {
				Dungeons2.log.warn("Unable to position Planned Room that meets positional criteria.");
				return EMPTY_ROOM;
			}
			for (Room room : plannedRooms) {
				if (room.getXZBoundingBox().intersects(plannedRoom.getXZBoundingBox())) {
					Dungeons2.log.debug("New Planned room intersects with planned list room.");
					continue checkingRooms;
				}
			}
			// test if the room meets conditions to be placed in the minecraft world
			if (!validateRoomConstraints(world, plannedRoom, config)) {
				break;
			}			
			checkRooms = false;			
		} while (checkRooms);		
		return plannedRoom;
	}

	
	/**
	 * 
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @param config
	 * @return
	 */
	@Deprecated
	protected Room buildPlannedRoom(World world, Random rand, AxisAlignedBB field, ICoords startPoint, List<Room> plannedRooms, LevelConfig config) {
		Room plannedRoom = new Room();
		
		/* 
		 * check to make sure planned rooms don't intersect.
		 * test up to 10 times for a successful position
		 */
		boolean checkRooms = true;
		int endCheckIndex = 0;
		checkingRooms:
		do {
			plannedRoom = randomizeRoom(rand, plannedRoom, field, startPoint, config);
			Dungeons2.log.debug("New Planned Room:" + plannedRoom);
			endCheckIndex++;
			if (endCheckIndex > 10) {
				Dungeons2.log.warn("Unable to position Planned Room that meets positional criteria.");
				return EMPTY_ROOM;
			}
			for (Room room : plannedRooms) {
				if (room.getXZBoundingBox().intersects(plannedRoom.getXZBoundingBox())) {
					Dungeons2.log.debug("New Planned room intersects with planned list room.");
					continue checkingRooms;
				}
			}
			// test if the room meets conditions to be placed in the minecraft world
			if (!meetsRoomConstraints(world, plannedRoom, config)) {
				break;
			}			
			checkRooms = false;			
		} while (checkRooms);		
		return plannedRoom;
	}
	
	/**
	 * 
	 * @param world
	 * @param rand
	 * @param field
	 * @param startPoint
	 * @param predefinedRooms
	 * @param config
	 * @return
	 */
	protected Room buildBossRoom(World world, Random rand, AxisAlignedBB field,
			ICoords startPoint, List<Room> predefinedRooms, ILevelConfig config) {
		final int BOSS_ROOM_MIN_XZ = 10;
		final int BOSS_ROOM_MIN_Y = 10;
		
		Room bossRoom = buildEndRoom(world, rand, field, startPoint, predefinedRooms, config).setType(Type.BOSS).setDegrees(1);	
		// ensure min dimensions are met for start room
		bossRoom.setWidth(Math.max(BOSS_ROOM_MIN_XZ, bossRoom.getWidth()));
		bossRoom.setDepth(Math.max(BOSS_ROOM_MIN_XZ, bossRoom.getDepth()));
		bossRoom.setHeight(Math.max(Math.min(BOSS_ROOM_MIN_Y, config.getHeight().getMaxInt()),  bossRoom.getHeight()));
		return bossRoom;
	}
	
	/**
	 * 
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @param predefinedRooms
	 * @param config
	 * @return
	 */
	@Deprecated
	protected Room buildBossRoom(World world, Random rand, AxisAlignedBB field,
			ICoords startPoint, List<Room> predefinedRooms, LevelConfig config) {
		final int BOSS_ROOM_MIN_XZ = 10;
		final int BOSS_ROOM_MIN_Y = 10;
		
		Room bossRoom = buildEndRoom(world, rand, field, startPoint, predefinedRooms, config).setType(Type.BOSS).setDegrees(1);	
		// ensure min dimensions are met for start room
		bossRoom.setWidth(Math.max(BOSS_ROOM_MIN_XZ, bossRoom.getWidth()));
		bossRoom.setDepth(Math.max(BOSS_ROOM_MIN_XZ, bossRoom.getDepth()));
		bossRoom.setHeight(Math.max(Math.min(BOSS_ROOM_MIN_Y, config.getHeight().getMaxInt()),  bossRoom.getHeight()));
		return bossRoom;
	}
	
	/**
	 * @param world
	 * @param rand
	 * @param surfaceCoords the coords of the surface position
	 * @param startRoom the level room that the entrance is connected to
	 * @param levelConfig
	 * @return
	 */
	@Deprecated
	protected Room buildEntranceRoom(World world, Random rand, ICoords surfaceCoords, Room startRoom,
			LevelConfig levelConfig) {
		
		Room entranceRoom = new Room(startRoom);
		entranceRoom.setAnchor(true).setType(Type.ENTRANCE);
		entranceRoom.setCoords(entranceRoom.getCoords().resetY(surfaceCoords.getY()));

		return null;
	}

	
	/**
	 * @return the config
	 */
	public LevelConfig getConfig() {
		return config;
	}

	/**
	 * @param config the config to set
	 */
	public void setConfig(LevelConfig config) {
		this.config = config;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "LevelBuilder []";
	}

	/**
	 * @return the field
	 */
	protected AxisAlignedBB getBoundary() {
		return field;
	}

	/**
	 * @param field the field to set
	 */
	protected void setBoundary(AxisAlignedBB field) {
		this.field = field;
	}

	/**
	 * @return the roomBoundary
	 */
	protected AxisAlignedBB getRoomBoundary() {
		return roomBoundary;
	}

	/**
	 * @param roomBoundary the roomBoundary to set
	 */
	protected void setRoomBoundary(AxisAlignedBB roomBoundary) {
		this.roomBoundary = roomBoundary;
	}
	
	/**
	 * 	
	 * @param i
	 */
	public void incrementLossToDistanceBuffering(int i) {
		this.roomLossToDistanceBuffering += i;		
	}
	
	/**
	 * 
	 * @param i
	 */
	public void incrementLossToValidation(int i) {
		this.roomLossToValidation += i;
	}

	/**
	 * @return the roomLossToDistanceBuffering
	 */
	protected int getRoomLossToDistanceBuffering() {
		return roomLossToDistanceBuffering;
	}

	/**
	 * @param roomLossToDistanceBuffering the roomLossToDistanceBuffering to set
	 */
	protected void setRoomLossToDistanceBuffering(int roomLossToDistanceBuffering) {
		this.roomLossToDistanceBuffering = roomLossToDistanceBuffering;
	}

	/**
	 * @return the roomLossToValidation
	 */
	protected int getRoomLossToValidation() {
		return roomLossToValidation;
	}

	/**
	 * @param roomLossToValidation the roomLossToValidation to set
	 */
	protected void setRoomLossToValidation(int roomLossToValidation) {
		this.roomLossToValidation = roomLossToValidation;
	}

	/**
	 * @since 2.0
	 * @return
	 */
	public IDungeonBuilder getDungeonBuilder() {
		return dungeonBuilder;
	}

	/**
	 * @since 2.0
	 * @param dungeonBuilder
	 */
	public void setDungeonBuilder(IDungeonBuilder dungeonBuilder) {
		this.dungeonBuilder = dungeonBuilder;
	}
	
}
