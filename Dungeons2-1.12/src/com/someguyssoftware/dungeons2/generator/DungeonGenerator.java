/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.chest.ChestSheetLoader;
import com.someguyssoftware.dungeons2.generator.blockprovider.CheckedFloorRoomBlockProvider;
import com.someguyssoftware.dungeons2.graph.Wayline;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.model.Room.Type;
import com.someguyssoftware.dungeons2.model.Shaft;
import com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import com.someguyssoftware.dungeons2.spawner.SpawnSheetLoader;
import com.someguyssoftware.dungeons2.style.BossRoomDecorator;
import com.someguyssoftware.dungeons2.style.IRoomDecorator;
import com.someguyssoftware.dungeons2.style.LayoutAssigner;
import com.someguyssoftware.dungeons2.style.LibraryRoomDecorator;
import com.someguyssoftware.dungeons2.style.RoomDecorator;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.StyleSheetLoader;

import net.minecraft.world.World;

/**
 * This class is responsible for building the dungeon in game.
 * 
 * @author Mark Gottschling on Jul 27, 2016
 *
 */
public class DungeonGenerator {
	// TODO either these shouldn't be static or they need to be loaded by static {}, not constructor or this class is singleton
	/*
	 * default stylesheet that is within the classpath of the mod jar
	 */
	private static StyleSheet defaultStyleSheet;

	/*
	 * default chestSheet that is within the classpath of the mod jar
	 */
	private static ChestSheet defaultChestSheet;
	
	/*
	 * default spawnSheet that is within the classpath of the mod jar
	 */
	private static SpawnSheet defaultSpawnSheet;
	
	// TODO should this be part of the RoomGeneratorFactory? - makes more sense
	private static Multimap<String, IRoomGenerator> roomGenerators = ArrayListMultimap.create();
	
	/**
	 * TODO should throw custom exception
	 * @throws Exception 
	 * 
	 */
	public DungeonGenerator() throws Exception {
		// load the default style sheet
		if (defaultStyleSheet == null) {
			setDefaultStyleSheet(StyleSheetLoader.load());
		}
		if (defaultChestSheet == null) {
			setDefaultChestSheet(ChestSheetLoader.load());
		}
		if (defaultSpawnSheet == null) {
			setDefaultSpawnSheet(SpawnSheetLoader.load());
		}		
	}

	/**
	 * TODO return false if error or gen fails
	 * @param world
	 * @param random
	 * @param dungeon
	 * @param styleSheet
	 * @param chestSheet
	 * @param spawnSheet
	 * @return
	 * @throws FileNotFoundException
	 */
	public boolean generate(World world, Random random, Dungeon dungeon, StyleSheet styleSheet, ChestSheet chestSheet, SpawnSheet spawnSheet) throws FileNotFoundException {

		// if styleSheet is null then use the default style sheet
		if (styleSheet == null) {
			Dungeons2.log.warn("Provided style sheet is null. Using default style sheet.");
			styleSheet = DungeonGenerator.getDefaultStyleSheet();
		}
		
		/*
		 *  create a room generator factory
		 */
		RoomGeneratorFactory factory = new RoomGeneratorFactory(roomGenerators);
		/*
		 * 
		 */
		IRoomGenerator roomGen = null;

		/*
		 * a layout assigner. it determine the layout to use for each room and what design elements to enable
		 */
		LayoutAssigner layoutAssigner = new LayoutAssigner(styleSheet);
		
		/*
		 * create the room decorators
		 */
		IRoomDecorator roomDecorator = new RoomDecorator(chestSheet, spawnSheet);
		IRoomDecorator bossRoomDecorator = new BossRoomDecorator(chestSheet);
		IRoomDecorator libraryDecorator = new LibraryRoomDecorator(chestSheet, spawnSheet);
		
		/*
		 *  NOTE careful here. IRoomGenerator can alter the state of the IGenerationStrategy with a
		 *  IDungeonsBlockProvider of it's choosing. Don't share between generators or have to synchronize
		 */		

		// build the entrance
		buildEntrance(world, random, dungeon, layoutAssigner, factory, roomDecorator, styleSheet);

		/*
		 * build all the levels 
		 */
		int levelCount = 0;
		int libraryCount = 0;
		// generate all the rooms
		for (Level level : dungeon.getLevels()) {
			Dungeons2.log.debug("Level: " + levelCount);
//			Dungeons2.log.debug("Is Level Support On? " + level.getConfig().isSupportOn());
			// build the rooms for the level
			for (Room room : level.getRooms()) {
				// assign a layout to the room
				layoutAssigner.assign(random, room);
				
				// get the room generator
				roomGen = factory.createRoomGenerator(random, room, level.getConfig().isSupportOn());
//				if (roomGen.getGenerationStrategy().getBlockProvider() instanceof CheckedFloorRoomBlockProvider) {
//					Dungeons2.log.debug("Generating Checked Room @ " + room.getCoords().toShortString());
//				}
				
				// generate the room into the world
				roomGen.generate(world, random, room, dungeon.getTheme(), styleSheet, level.getConfig());
				
				// TODO need a decorator factory
				if (room.getType() == Type.BOSS) {
					bossRoomDecorator.decorate(world, random, roomGen.getGenerationStrategy().getBlockProvider(), room, level.getConfig());
				}
				
				/*
				 * TODO this should be a random selection of special rooms
				 * TODO there should be something to describe where a special room can occur (ex levels 4-6)
				 *  select any special decorators
				 */					
				// ensure room fits the criteria to host a library	
				else if (room.getWidth() > 5
						&& room.getDepth() > 5
						&& room.getHeight() >=5
						&& !room.hasPillar()
						&& random.nextInt(100) < 10
						&& libraryCount < 3) {
					Dungeons2.log.debug("Using library decorator for room @ " + room.getCoords().toShortString());
						libraryDecorator.decorate(world, random, roomGen.getGenerationStrategy().getBlockProvider(), room, level.getConfig());
						libraryCount++;
				}
				else {
					// decorate the room (spawners, chests, webbings, etc)
					roomDecorator.decorate(world, random, roomGen.getGenerationStrategy().getBlockProvider(), room, level.getConfig());
				}
			
				// TODO add to JSON output

			}
			// create a list of generated hallways
			List<Room> hallways = new ArrayList<>();
			// generate the hallways
			for (Wayline wayline : level.getWaylines()) {
				// build a hallway (room) from a wayline
				Hallway hallway = Hallway.fromWayline(wayline, level.getRooms());
				// assign a layout
				layoutAssigner.assign(random, hallway);
				roomGen = factory.createHallwayGenerator(hallway, level.getRooms(), hallways, level.getConfig().isSupportOn());
				roomGen.generate(world, random, hallway, dungeon.getTheme(), styleSheet, level.getConfig());
				// add the hallway to the list of generated hallways
				hallways.add(hallway);
			}
			
			// generate the shafts
			for (Shaft shaft : level.getShafts()) {
//				Dungeons2.log.debug("Building Shaft: " + shaft);
				// assign the layout
				shaft.setLayout(shaft.getParent().getLayout());
				roomGen = factory.createShaftGenerator(shaft, level.getConfig().isSupportOn());
				roomGen.generate(world, random, shaft, dungeon.getTheme(), styleSheet, level.getConfig());
			}
		}
		return true;
	}

	/**
	 * 
	 * @param world
	 * @param random
	 * @param dungeon
	 * @param layoutAssigner
	 * @param factory
	 * @param roomDecorator
	 */
	private void buildEntrance(World world, Random random,
			Dungeon dungeon, LayoutAssigner layoutAssigner, RoomGeneratorFactory factory,
			IRoomDecorator roomDecorator, StyleSheet styleSheet) {
		
		Room entranceRoom = dungeon.getEntrance();
		// create and setup a config for entrance
		LevelConfig entranceLevelConfig = dungeon.getLevels().get(0).getConfig().copy();
		entranceLevelConfig.setDecayMultiplier(Math.min(5, entranceLevelConfig.getDecayMultiplier())); // increase the decay multiplier to a minimum of 5
		// assign a layout to the entrance room
		layoutAssigner.assign(random, entranceRoom);
		IRoomGenerator roomGen = factory.createRoomGenerator(random, entranceRoom, dungeon.getLevels().get(0).getConfig().isSupportOn());
		// TODO need to provide the entrance room generator with a different level config that uses a higher decay multiplier
		// to create a much more decayed surface structure.
		roomGen.generate(world, random, entranceRoom, dungeon.getTheme(), styleSheet, entranceLevelConfig);
		roomDecorator.decorate(world, random, roomGen.getGenerationStrategy().getBlockProvider(), entranceRoom, entranceLevelConfig);
	}

	/**
	 * @return the defaultStyleSheet
	 */
	public static StyleSheet getDefaultStyleSheet() {
		return defaultStyleSheet;
	}

	/**
	 * @param defaultStyleSheet the defaultStyleSheet to set
	 */
	private void setDefaultStyleSheet(StyleSheet defaultStyleSheet) {
		DungeonGenerator.defaultStyleSheet = defaultStyleSheet;
	}

	/**
	 * @return the defaultChestSheet
	 */
	public static ChestSheet getDefaultChestSheet() {
		return defaultChestSheet;
	}

	/**
	 * @param defaultChestSheet the defaultChestSheet to set
	 */
	public static void setDefaultChestSheet(ChestSheet defaultChestSheet) {
		DungeonGenerator.defaultChestSheet = defaultChestSheet;
	}

	/**
	 * @return the defaultSpawnSheet
	 */
	public static SpawnSheet getDefaultSpawnSheet() {
		return defaultSpawnSheet;
	}

	/**
	 * @param defaultSpawnSheet the defaultSpawnSheet to set
	 */
	public static void setDefaultSpawnSheet(SpawnSheet defaultSpawnSheet) {
		DungeonGenerator.defaultSpawnSheet = defaultSpawnSheet;
	}
}
