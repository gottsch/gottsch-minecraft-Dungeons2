/**
 * 
 */
package com.someguyssoftware.dungeons2.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.someguyssoftware.gottschcore.world.WorldInfo.*;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.model.Room.Type;
import com.someguyssoftware.dungeons2.model.Shaft;
import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * This class is responsible for building an entire dungeon.
 * @author Mark Gottschling on Jul 27, 2016
 *
 */
public class DungeonBuilderTopDown implements IDungeonBuilder {
	private static final Logger logger = LogManager.getLogger(Dungeons2.LOGGER_NAME);

	private static final int MIN_ENTRANCE_XZ = 5;
	private static final int MAX_ENTRANCE_XZ = 9;
	private static final int MIN_ENTRANCE_Y= 7;
	private static final int MAX_ENTRANCE_Y = 13;

	private static final int TOP_LEVEL = 0;
	
	private LevelBuilder levelBuilder;
	
	private transient AxisAlignedBB field;
	private transient ICoords startPoint;
	
	/**
	 * 
	 */
	public DungeonBuilderTopDown() {
		this.levelBuilder = new LevelBuilder();
	}
	
	/**
	 * 
	 * @param builder
	 */
	public DungeonBuilderTopDown(LevelBuilder builder) {
		this.levelBuilder = builder;
	}
	
	/**
	 * 
	 * @param field
	 * @return
	 */
	@Override
	public DungeonBuilderTopDown withField(AxisAlignedBB field) {
		this.field = field;
		return this;
	}
	
	public DungeonBuilderTopDown withStartPoint(ICoords startPoint) {
		this.startPoint = startPoint;
		return this;
	}
	
	/**
	 * New(er) version that uses the new dungeonsEngine IDungeonConfig
	 */
	public Dungeon build(World world, Random rand, ICoords spawnCoords, IDungeonConfig config) {
		Dungeon dungeon = new Dungeon();
		List<Room> plannedRooms = new ArrayList<>();
		ILevelConfig defaultLevelConfig = config.getLevelConfigs()[0];

		/*
		 * Calculate dungeon field
		 */
		AxisAlignedBB dungeonField = null;
		ICoords closestCoords = null;
		if (this.getField() == null) {
			// get the closest player
			closestCoords = WorldInfo.getClosestPlayerCoords(world, spawnCoords);
			if (closestCoords == null) {
				Dungeons2.log.warn("Unable to locate closest player - using World spawn point");
				closestCoords = new Coords(world.getSpawnPoint());
			}
			// get the field based on the player position and spawn
			dungeonField = getDungeonField(spawnCoords, closestCoords);
			if (dungeonField == null) {
				Dungeons2.log.warn("Unable to calculate dungeon field from spawn pos-> {}, and player pos -> {}", 
						spawnCoords.toShortString(), closestCoords.toShortString());				
				return EMPTY_DUNGEON;
			}
		}
		else dungeonField = this.getField();
		
		// resize field
		if (config.getFieldFactor() < 1.0D) {
			int shrinkAmount = (int) ((dungeonField.maxX - dungeonField.minX) * (1.0 - config.getFieldFactor()) / 2);
			dungeonField = dungeonField.shrink(shrinkAmount);
			Dungeons2.log.debug("Dungeon shrunk by -> {}, to new size -> {}", shrinkAmount, dungeonField);
		}

		/*
		 * Calculate room field
		 */
		AxisAlignedBB roomField =getLevelBuilder().getRoomField(dungeonField, defaultLevelConfig.getFieldFactor());
		
		/*
		 * Select startPoint in room field
		 */
		ICoords startPoint = null;
		if (this.startPoint == null) {
			startPoint = getLevelBuilder().randomizeCoords(rand, roomField);
			// check if the start point is in a loaded chunk
			ChunkPos startChunk = startPoint.toChunkPos();
			if (!world.isChunkGeneratedAt(startChunk.x, startChunk.z)) {
				Dungeons2.log.debug("startPoint is not in a loaded chunk -> {}", startChunk);
				return EMPTY_DUNGEON;
			}
			// get a valid Y value
			startPoint = startPoint.resetY(WorldInfo.getHeightValue(world, startPoint));
		}
		else startPoint = this.startPoint;
		
		/*
		 * Setup level builder
		 */
		if (levelBuilder.getField() == null) levelBuilder.setField(dungeonField);
		if (levelBuilder.getRoomField() == null) levelBuilder.setRoomField(roomField);
		
		/*
		 * Perform all the minecraft world contraint checks
		 */
		// TODO somehow wrap all these checks into one function. a lot of these variables could be member properties
		
		// 1. determine if valid coords
		if (config.isMinecraftConstraints() && !WorldInfo.isValidY(startPoint)) {
			Dungeons2.log.debug(String.format("[%d] is not a valid y value.", startPoint.getY()));
			return EMPTY_DUNGEON;
		}
		
		//  2. get a valid surface location		
		ICoords surfaceCoords = null;
		if (config.isMinecraftConstraints()) {
			surfaceCoords = WorldInfo.getDryLandSurfaceCoords(world, startPoint);
			if (surfaceCoords == null || surfaceCoords == EMPTY_COORDS) {
				Dungeons2.log.debug(String.format("Not a valid dry land surface @ %s", startPoint.toShortString()));
				return EMPTY_DUNGEON;
			}
		}
		else {
			surfaceCoords = startPoint;
		}
		
		// 2b. Determine if surfaceCoords is within Dungeon constraints
		if (surfaceCoords.getY() < config.getBottomLimit() ||
				surfaceCoords.getY() > config.getTopLimit()) {
			Dungeons2.log.debug(String.format("Start position is outside Y constraints: Start %s, yBottom: %d, yTop: %d",
					surfaceCoords.toShortString(), config.getBottomLimit(), config.getTopLimit()));
			return EMPTY_DUNGEON;			
		}		
		
		// 3. determine if startPoint is deep enough to support at least one level
		if (config.isMinecraftConstraints()) {
			if (surfaceCoords.getY() - config.getSurfaceBuffer() - defaultLevelConfig.getHeight().getMax() < config.getBottomLimit()) {
				Dungeons2.log.debug(String.format("Start position is not deep enough to generate a dungeon @ %s", surfaceCoords.toShortString()));
				return EMPTY_DUNGEON;	
			}
		}
		
		// TODO 4,5,6 can go into a new method and return boolean or Room?
		// 4. determine if the entrance room can be build at this spot
		Room entranceRoom = buildEntranceRoom(world, rand, surfaceCoords);
		Dungeons2.log.debug("Entrance Room:" + entranceRoom);
		
		// 5. ensure the entrance is in a loaded chunk
		if (!levelBuilder.isRoomInLoadedChunks(world, entranceRoom)) {
			Dungeons2.log.debug("entrance room is NOT in valid chunks -> {} {}", surfaceCoords, surfaceCoords.toChunkPos());
			return EMPTY_DUNGEON;
		}
	
		// 6. ensure that the entrance has a valid base
		if (config.isMinecraftConstraints() &&
				!WorldInfo.isValidAboveGroundBase(world, entranceRoom.getCoords().resetY(surfaceCoords.getY()),
				entranceRoom.getWidth(), entranceRoom.getDepth(), 50, 20, 50)) {
			if (Dungeons2.log.isDebugEnabled())
				Dungeons2.log.debug(String.format("Surface area does not meet ground/air criteria @ %s", surfaceCoords));
			return EMPTY_DUNGEON;		
		}
		 // add entrance to dungeon
		dungeon.setEntrance(entranceRoom);
		///////////// end of minecraft constraint checks / positioning
		
		// update the startPoint to be below the surface by surfaceBuffer amount
		startPoint = surfaceCoords.add(0, -(config.getSurfaceBuffer() + defaultLevelConfig.getHeight().getMaxInt()), 0);

		Room startRoom = null;
		Room endRoom = null;
		boolean isBottomLevel = false;
		
		/*
		 *  determine the number of levels to attempt to build
		 */
		int numberOfLevels = RandomHelper.randomInt(rand, (int)config.getNumLevels().getMin(), (int)config.getNumLevels().getMax());
		Dungeons2.log.debug("number of levels:" + numberOfLevels);
		// for every n in numLevels
		for (int levelIndex = 0; levelIndex < numberOfLevels; levelIndex++) {
			logger.debug("Building level -> {}" + levelIndex);
			ILevelConfig levelConfig = null;
			// get the level config
			if (levelIndex <= config.getLevelConfigs().length-1) {
				levelConfig = config.getLevelConfigs()[levelIndex];
			}
			else {
				// get the last defined level config
				levelConfig = config.getLevelConfigs()[config.getLevelConfigs().length-1];
			}
			
			// determine if any levels can be made below this one
			if (startPoint.getY() - levelConfig.getHeight().getMax() < config.getBottomLimit()) {
				isBottomLevel = true;
			}
			
			if (levelIndex == TOP_LEVEL) {
				Dungeons2.log.debug("TOP LEVEL");
				// build start centered at startPoint
				startRoom = levelBuilder.buildStartRoom(world, rand, roomField, startPoint, levelConfig);

			}
		}
		
		// return the dungeon
		return dungeon;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeons2.builder.IDungeonBuilder#build(net.minecraft.world.World, java.util.Random, com.someguyssoftware.mod.ICoords, com.someguyssoftware.dungeons2.model.DungeonConfig)
	 */
	@Override
	public Dungeon build(World world, Random rand, ICoords spawnCoords, DungeonConfig config) {
		/*
		 * A dungeon object that contains all levels and the entrance of the dungeon.
		 */
		Dungeon dungeon = new Dungeon(config);

		/*
		 * A list of rooms that are pre-planned to be placed in the dungeons (not randomized).
		 */
		List<Room> plannedRooms = new ArrayList<>();
		
		/*
		 * 
		 */
		LevelConfig levelConfig = getLevelBuilder().getConfig();		

		/*
		 * Calculate dungeon field
		 */
		AxisAlignedBB dungeonField = null;
		ICoords closestCoords = null;
		if (this.getField() == null) {
			// get the closest player
			closestCoords = WorldInfo.getClosestPlayerCoords(world, spawnCoords);
			if (closestCoords == null) {
				Dungeons2.log.warn("Unable to locate closest player - using World spawn point");
				closestCoords = new Coords(world.getSpawnPoint());
			}
			// get the field based on the player position and spawn
			dungeonField = getDungeonField(spawnCoords, closestCoords);
			if (dungeonField == null) {
				Dungeons2.log.warn("Unable to calculate dungeon field from spawn pos-> {}, and player pos -> {}", 
						spawnCoords.toShortString(), closestCoords.toShortString());				
				return EMPTY_DUNGEON;
			}
		}
		else dungeonField = this.getField();
		
//		Dungeons2.log.debug("dungeon field -> {}", dungeonField);
//		// NOTE AxisAlignedBB.getCenter() is @ClientSide ... ugh
//		if (world.isRemote) {
//			Dungeons2.log.debug("dungeonField.center -> {}", dungeonField.getCenter());
//		}
		
		// TODO gottchcore Coords(IVec3)
		/*
		 * Calculate room field (based on size... don't know the size anymore :( )
		 */
		AxisAlignedBB roomField = getRoomField(dungeonField);
//		Dungeons2.log.debug("roomField -> {}", roomField);
		
		/*
		 * Select startPoint in room field
		 */
		ICoords startPoint = null;
		if (this.startPoint == null) {
			startPoint = getLevelBuilder().randomizeCoords(rand, roomField, getLevelBuilder().getConfig());
			// check if the start point is in a loaded chunk
			ChunkPos startChunk = startPoint.toChunkPos();
			if (!world.isChunkGeneratedAt(startChunk.x, startChunk.z)) {
				Dungeons2.log.debug("startPoint is not in a loaded chunk -> {}", startChunk);
				return EMPTY_DUNGEON;
			}
			// get a valid Y value
			startPoint = startPoint.resetY(WorldInfo.getHeightValue(world, startPoint));
		}
		else startPoint = this.startPoint;
//		Dungeons2.log.debug("startPoint -> {}; {}", startPoint, Dungeons2.toChunk(startPoint));
		
		/*
		 * Setup level builder
		 */
		if (levelBuilder.getField() == null) levelBuilder.setField(dungeonField);
		if (levelBuilder.getRoomField() == null) levelBuilder.setRoomField(roomField);
		
		/*
		 * Perform all the minecraft world contraint checks
		 */
		// 1. determine if valid coords
		if (levelBuilder.getConfig().isMinecraftConstraintsOn() && !WorldInfo.isValidY(startPoint)) {
			Dungeons2.log.debug(String.format("[%d] is not a valid y value.", startPoint.getY()));
			return EMPTY_DUNGEON;
		}
		
		//  2. get a valid surface location		
		ICoords surfaceCoords = null;
		if (levelBuilder.getConfig().isMinecraftConstraintsOn()) {
			surfaceCoords = WorldInfo.getDryLandSurfaceCoords(world, startPoint);
			if (surfaceCoords == null || surfaceCoords == EMPTY_COORDS) {
				Dungeons2.log.debug(String.format("Not a valid dry land surface @ %s", startPoint.toShortString()));
				return EMPTY_DUNGEON;
			}
		}
		else {
			surfaceCoords = startPoint;
		}
//		Dungeons2.log.debug("SurfaceCoords -> {} {}", surfaceCoords.toShortString(), Dungeons2.toChunk(surfaceCoords));
		// 2b. Determine if surfaceCoords is within Dungeon constraints
		if (surfaceCoords.getY() < config.getYBottom() ||
				surfaceCoords.getY() > config.getYTop()) {
			Dungeons2.log.debug(String.format("Start position is outside Y constraints: Start %s, yBottom: %d, yTop: %d",
					surfaceCoords.toShortString(), config.getYBottom(), config.getYTop()));
			return EMPTY_DUNGEON;			
		}
		
		// 3. determine if startPoint is deep enough to support at least one level
		if (levelConfig.isMinecraftConstraintsOn()) {
			if (surfaceCoords.getY() - levelConfig.getSurfaceBuffer() - levelConfig.getHeight().getMax() < config.getYBottom()) {
				Dungeons2.log.debug(String.format("Start position is not deep enough to generate a dungeon @ %s", surfaceCoords.toShortString()));
				return EMPTY_DUNGEON;	
			}
		}
		
		// 4. determine if the entrance room can be build at this spot
		Room entranceRoom = buildEntranceRoom(world, rand, surfaceCoords);
		Dungeons2.log.debug("Entrance Room:" + entranceRoom);

		/*
		 *  TODO 1. room is centered on surfaceCoords, and thus the isValidAboveGroundBase() should be taking in coords
		 *  adjusted to those of the room. ie entranceRoom.getCoords().
		 */
//		Dungeons2.log.debug("ischunkgen -> {}", world.isChunkGeneratedAt(surfaceChunk.getX(), surfaceChunk.getZ()));
//		if (!world.isChunkGeneratedAt(surfaceChunk.getX(), surfaceChunk.getZ())) {
		if (!levelBuilder.isRoomInLoadedChunks(world, entranceRoom)) {
			Dungeons2.log.debug("entrance room is NOT in valid chunks -> {} {}", surfaceCoords, surfaceCoords.toChunkPos());
			return EMPTY_DUNGEON;
		}
	
//		Dungeons2.log.debug("chunk EXISTS at entrance pos -> {},", surfaceChunk.toShortString());
		if (levelConfig.isMinecraftConstraintsOn() &&
				!WorldInfo.isValidAboveGroundBase(world, entranceRoom.getCoords().resetY(surfaceCoords.getY()),
				entranceRoom.getWidth(), entranceRoom.getDepth(), 50, 20, 50)) {
			if (Dungeons2.log.isDebugEnabled())
				Dungeons2.log.debug(String.format("Surface area does not meet ground/air criteria @ %s", surfaceCoords));
			return EMPTY_DUNGEON;		
		}
//		Dungeons2.log.debug("after entrance room base check...");
		 // add entrance to dungeon
		dungeon.setEntrance(entranceRoom);
		
		Room startRoom = null;
		Room endRoom = null;
		boolean isBottomLevel = false;
		
		// update the startPoint to be below the surface by surfaceBuffer amount
		startPoint = surfaceCoords.add(0, -(levelConfig.getSurfaceBuffer() + levelConfig.getHeight().getMaxInt()), 0);

		/*
		 *  determine the number of levels to attempt to build
		 */
		int numberOfLevels = RandomHelper.randomInt(rand, (int)config.getNumberOfLevels().getMin(), (int)config.getNumberOfLevels().getMax());
		Dungeons2.log.debug("number of levels:" + numberOfLevels);
		// for every n in numberOfLevels
		for (int levelIndex = 0; levelIndex < numberOfLevels; levelIndex++) {
			logger.debug("Building level (top-down): " + levelIndex);
			
			// determine if any levels can be made below this one
			if (startPoint.getY() - levelConfig.getHeight().getMax() < config.getYBottom()) {
				isBottomLevel = true;
			}
			
 			/* 
			 * determine if this is the top level - the type of start and end room depends on this.
			 * check from the startpoint adding the height of this current level +1, adding the height on the next level +1,
			 * adding the buffer. If this value is greater than the surface than the current level is the topmost level.
			 */
			if (levelIndex == 0) {
				Dungeons2.log.debug("TOP LEVEL");
				// build start centered at startPoint
				startRoom = levelBuilder.buildStartRoom(world, rand, roomField, startPoint, levelConfig);
				if (startRoom == LevelBuilder.EMPTY_ROOM) {
					Dungeons2.log.warn("Unable to generate Top level Start Room.");
					return EMPTY_DUNGEON;					
				}
				// update to same direction as entrance
				startRoom.setDirection(entranceRoom.getDirection());
				Dungeons2.log.debug("Top Level Start Room:" + startRoom);
				
				// add to the planned rooms
				plannedRooms.add(startRoom);
				
				// TODO this is probably wrong... dungeonbuilder shouldn't keep providing the room builder to the level
				// build planned end room
				endRoom = levelBuilder.buildEndRoom(world, rand, roomField, startPoint, plannedRooms, levelConfig);
				if (endRoom == LevelBuilder.EMPTY_ROOM) {
					logger.warn("Unable to generate Top level End Room.");
					return EMPTY_DUNGEON;
				}
				plannedRooms.add(endRoom);
			}
			// if bottom level, manually setup an end room for boss/treasure
			else if (levelIndex == numberOfLevels - 1 || isBottomLevel) {	
				Dungeons2.log.debug("BOTTOM LEVEL");
				// TODO start room same as else{}, so make a method out of it
				// build the start room
				startRoom = new Room(dungeon.getLevels().get(levelIndex-1).getEndRoom());
				if (startRoom == LevelBuilder.EMPTY_ROOM) {
					logger.warn("Unable to generate Bottom level Start Room");
					return EMPTY_DUNGEON;
				}
//				Dungeons2.log.debug("Start Room (Bottom Level): " + startRoom);
				Dungeons2.log.debug("StartPoint (Bottom Level): " + startPoint);
				// update end room settings
				startRoom.setCoords(startRoom.getCoords().resetY(startPoint.getY()));
				startRoom.setDistance(startRoom.getCenter().getDistance(startPoint));
				startRoom.setAnchor(true).setStart(true).setEnd(false);			
				plannedRooms.add(startRoom);
				
				endRoom = levelBuilder.buildBossRoom(world, rand, roomField, startPoint, plannedRooms, levelConfig);
				if (endRoom == LevelBuilder.EMPTY_ROOM) {
					logger.warn("Unable to generate Bottom level End Room.");
					return EMPTY_DUNGEON;
				}
				Dungeons2.log.debug("Boss Room (Bottom Level): " + endRoom);
				Dungeons2.log.debug("BossPoint (Bottom Level): " + endRoom.getCoords().toShortString());
				plannedRooms.add(endRoom);
			}
			else {
				Dungeons2.log.debug("Building Start Room for Level: " + levelIndex);
				// 1. create start room from previous level end room
				startRoom = new Room(dungeon.getLevels().get(levelIndex-1).getEndRoom());
				if (startRoom == LevelBuilder.EMPTY_ROOM) {
					logger.warn("Unable to generate level Start Room: " + levelIndex);
					return EMPTY_DUNGEON;
				}
				// update end room settings
				startRoom.setCoords(startRoom.getCoords().resetY(startPoint.getY()));
				startRoom.setDistance(startRoom.getCenter().getDistance(startPoint));
				startRoom.setAnchor(true).setStart(true).setEnd(false);
				plannedRooms.add(startRoom);
				Dungeons2.log.debug("Level Start Room:" + startRoom);

				// TODO again, DungeonBuilder shouldn't keep providing fields to the level builder on each call.
				// 2. create end room
				endRoom = levelBuilder.buildPlannedRoom(world, rand, roomField, startPoint, plannedRooms, levelConfig);
				if (endRoom == LevelBuilder.EMPTY_ROOM) {
					logger.warn("Unable to generate level End Room: " + levelIndex);
					return EMPTY_DUNGEON;
				}
				endRoom.setDistance(endRoom.getCenter().getDistance(startPoint));
				endRoom.setAnchor(true).setStart(false).setEnd(true).setType(Type.LADDER);
				plannedRooms.add(endRoom);						
			}
						
			// build a level
			Level level = levelBuilder.build(world, rand, startPoint, plannedRooms, levelConfig);
						
			Dungeons2.log.debug(String.format("Built level[%d]: %s", levelIndex, level));
			
			// add level
			if (level != LevelBuilder.EMPTY_LEVEL) {
				// update the min/max values for the dungeon
				if (dungeon.getMinX() == null || level.getMinX() < dungeon.getMinX()) dungeon.setMinX(level.getMinX());
				if (dungeon.getMaxX() == null || level.getMaxX() > dungeon.getMaxX()) dungeon.setMaxX(level.getMaxX());
				if (dungeon.getMinY() == null || level.getMinY() < dungeon.getMinY()) dungeon.setMinY(level.getMinY());
				if (dungeon.getMaxY() == null || level.getMaxY() > dungeon.getMaxY()) dungeon.setMaxY(level.getMaxY());
				if (dungeon.getMinZ() == null || level.getMinZ() < dungeon.getMinZ()) dungeon.setMinZ(level.getMinZ());
				if (dungeon.getMaxZ() == null || level.getMaxZ() > dungeon.getMaxZ()) dungeon.setMaxZ(level.getMaxZ());

				// add the level to the dungeon
				dungeon.getLevels().add(level);
				
				// build and add the shaft to the level
				if (levelIndex > 0) {
					Dungeons2.log.debug("Joing levels " + (levelIndex-1) + " to " + levelIndex);
					if (levelBuilder.join(level, dungeon.getLevels().get(levelIndex-1)) == LevelBuilder.EMPTY_SHAFT) {
						Dungeons2.log.warn("Levels don't require joining " + levelIndex + " to " + (levelIndex-1));
					}
				}
			}
			else {
				// TODO test if empty level and attempt to rebuild.
				logger.warn("Unable to generate Level: " + levelIndex);
				break;
			}
			
			// clear planned rooms
			plannedRooms.clear();
					
			if (isBottomLevel) {
				Dungeons2.log.debug("At bottom level... break...");
				break;
			}
			
			// update the start point to above the previous level
			startPoint = endRoom.getCenter();
			startPoint = startPoint.resetY(dungeon.getMinY() - (int)levelConfig.getHeight().getMax());
			// TEMP
//			break;
			
			// reset level builder stat properties
			getLevelBuilder().setRoomLossToDistanceBuffering(0);
			getLevelBuilder().setRoomLossToValidation(0);
		}
		
		Dungeons2.log.debug("Testing for empty dungeon...");
		if (dungeon == EMPTY_DUNGEON  || dungeon.getLevels().size() == 0) {
			logger.warn("Unable to build dungeon. 0 levels were built.");
			return EMPTY_DUNGEON;
		}
		
		Dungeons2.log.debug("Joining shaft from entrance to start room...");
		Dungeons2.log.debug("Start Room:" + dungeon.getLevels().get(0).getStartRoom());
		Dungeons2.log.debug("Entrance Room:" + entranceRoom);
		// join the top start room with the entrance room
		Shaft shaft = levelBuilder.join(dungeon.getLevels().get(0).getStartRoom(), entranceRoom);
		if (shaft == LevelBuilder.EMPTY_SHAFT) {
			Dungeons2.log.debug("Didn't join start room to entrance room");
		}
		
		Dungeons2.log.debug("Adding shaft to shaft list...");
		Dungeons2.log.debug("Dungeon level[0]:" + dungeon.getLevels().get(0));
		// add shaft to the top level
		dungeon.getLevels().get(0).getShafts().add(shaft);
		
		// NOTE Set at beginning of method
//		Dungeons2.log.debug("Setting dungeon config...");
		// add the config used on the dungeon
//		dungeon.setConfig(config);
		Dungeons2.log.debug("Returning dungeon...");
		return dungeon;
	}

	/**
	 * 
	 * @param dungeonField
	 * @return
	 */
	private AxisAlignedBB getRoomField(AxisAlignedBB dungeonField) {
		/*
		 * AxisAlignedBB.getCenter() is @ClientSide so must calculate the center
		 */
		Vec3d center = new Vec3d(dungeonField.minX + (dungeonField.maxX - dungeonField.minX) * 0.5D, dungeonField.minY + (dungeonField.maxY - dungeonField.minY) * 0.5D, dungeonField.minZ + (dungeonField.maxZ - dungeonField.minZ) * 0.5D);
		AxisAlignedBB roomField = new AxisAlignedBB(new BlockPos(center)).grow(30);
		return roomField;
	}

	/**
	 * 
	 * @param spawnCoords
	 * @param closestCoords
	 * @return
	 */
	private AxisAlignedBB getDungeonField(ICoords spawnCoords, ICoords closestCoords) {
		AxisAlignedBB dungeonField = null;
		
		
		ICoords deltaCoords = spawnCoords.delta(closestCoords);
		Dungeons2.log.debug("spawnCoords -> {}", spawnCoords);
		Dungeons2.log.debug("deltaCoords -> {}", deltaCoords.toShortString());
		// get max. distance of the axises, ensuring a min/max size is met
		int dist = Math.max(
				Math.min(
						Math.max(
								Math.abs(deltaCoords.getX()), 
								Math.abs(deltaCoords.getZ())),
						256), // 256 = MAX_FIELD_RADIUS
				25);  // 25 = MIN_FIELD_RADIUS
		Dungeons2.log.debug("dist from player to spawn -> {}", dist);

		dungeonField = new AxisAlignedBB(closestCoords.toPos());

		EnumFacing fieldFacing = null;
		if (Math.abs(deltaCoords.getX()) >= Math.abs(deltaCoords.getZ())) {
			Dungeons2.log.debug("deltaX -> {} >= deltaZ -> {}", Math.abs(deltaCoords.getX()), Math.abs(deltaCoords.getZ()));
			// WEST/EAST
			if (deltaCoords.getX() < 0 ) {
				// west
				Dungeons2.log.debug("field facing west");
				fieldFacing = EnumFacing.WEST;
				dungeonField = dungeonField.expand(-(dist), 0, 0).grow(0, 0, dist);
			}
			else {
				// east
				Dungeons2.log.debug("field facing east");
				fieldFacing = EnumFacing.EAST;
				dungeonField = dungeonField.expand(dist, 0, 0).grow(0, 0, dist);
			}
		}
		else {			
			Dungeons2.log.debug("deltaX -> {} < deltaZ -> {}", Math.abs(deltaCoords.getX()), Math.abs(deltaCoords.getZ()));
			// NORTH/SOUTH
			if (deltaCoords.getZ() < 0)  {
				// north
				Dungeons2.log.debug("field facing north");
				fieldFacing = EnumFacing.NORTH;
				dungeonField = dungeonField.expand(0, 0, -(dist)).grow(dist, 0, 0);
			}
			else {
				// south
				Dungeons2.log.debug("field facing south");
				fieldFacing = EnumFacing.SOUTH;
				dungeonField = dungeonField.expand(0, 0, dist).grow(dist, 0, 0);
			}
		}
		return dungeonField;
	}	

	/**
	 * 
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @param config
	 * @return
	 */
	@Override
	public Room buildEntranceRoom(World world, Random rand, ICoords startPoint) {
		/*
		 * the start of the dungeon
		 */
		Room room = new Room().setStart(true).setAnchor(true).setType(Type.ENTRANCE);
		
		/*
		 *  adjust the minimum dimension sizes for entrance room.
		 *  entrances will always be a square-based (xz axis) room and odd numbered (ex length/width=5)
		 */
		int xz = RandomHelper.randomInt(rand, MIN_ENTRANCE_XZ, MAX_ENTRANCE_XZ);
		if (xz % 2 == 0) xz++;
		room.setWidth(xz);
		room.setDepth(xz);
		room.setHeight(RandomHelper.randomInt(rand, MIN_ENTRANCE_Y, MAX_ENTRANCE_Y));

		// set the starting room coords to be in the middle of the start point
		room.setCoords(
				new Coords(startPoint.getX()-((room.getWidth()-1)/2),
						startPoint.getY(),
						startPoint.getZ()-((room.getDepth()-1)/2)));
		room.setDistance(0.0);
		// randomize a direction
		room.setDirection(Direction.getByCode(RandomHelper.randomInt(2, 5)));
		
		return room;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeons2.builder.IDungeonBuilder#getLevelBuilder()
	 */
	@Override
	public LevelBuilder getLevelBuilder() {
		return levelBuilder;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeons2.builder.IDungeonBuilder#setLevelBuilder(com.someguyssoftware.dungeons2.builder.LevelBuilder)
	 */
	@Override
	public void setLevelBuilder(LevelBuilder levelBuilder) {
		this.levelBuilder = levelBuilder;
	}

	/**
	 * @return the field
	 */
	protected AxisAlignedBB getField() {
		return field;
	}

	/**
	 * @param field the field to set
	 */
	protected void setField(AxisAlignedBB field) {
		this.field = field;
	}
}
