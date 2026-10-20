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
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeonsengine.chest.BossLootLoader;
import com.someguyssoftware.dungeonsengine.chest.LootLoader;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;

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
	 * Generates the entire dungeon into the world at once.
	 * @param world
	 * @param random
	 * @param dungeon
	 * @param styleSheet
	 * @param chestSheet
	 * @param spawnSheet
	 * @return
	 * @throws FileNotFoundException
	 */
	public boolean generate(World world, Random random, 
			Dungeon dungeon, StyleSheet styleSheet, @Deprecated ChestSheet chestSheet, SpawnSheet spawnSheet) 
					throws FileNotFoundException {
		createSteps(world, random, dungeon, styleSheet, spawnSheet).forEach(Runnable::run);
		return true;
	}

	/**
	 * Breaks the generation of the dungeon into ordered steps (the entrance, then per level: each room, each hallway
	 * and each shaft), so that a dungeon can be written into the world over several ticks.
	 * The steps must be run in order: hallways test against the rooms and hallways already generated.
	 * @param world
	 * @param random
	 * @param dungeon
	 * @param styleSheetIn
	 * @param spawnSheet
	 * @return
	 * @throws FileNotFoundException
	 */
	public List<Runnable> createSteps(World world, Random random, 
			Dungeon dungeon, StyleSheet styleSheetIn, SpawnSheet spawnSheet) throws FileNotFoundException {

		List<Runnable> steps = new ArrayList<>();

		// if styleSheet is null then use the default style sheet
		final StyleSheet styleSheet;
		if (styleSheetIn == null) {
			Dungeons2.log.warn("Provided style sheet is null. Using default style sheet.");
			styleSheet = DungeonGenerator.getDefaultStyleSheet();
		}
		else {
			styleSheet = styleSheetIn;
		}
		
		/*
		 *  create a room generator factory
		 */
		RoomGeneratorFactory factory = new RoomGeneratorFactory(roomGenerators);

		/*
		 * a layout assigner. it determine the layout to use for each room and what design elements to enable
		 */
		LayoutAssigner layoutAssigner = new LayoutAssigner(styleSheet);
		
		/*
		 * create the room decorators
		 */
		// TODO is many more decorators, they all should share the same set of loaders instead of creating new ones each.
		IRoomDecorator roomDecorator = new RoomDecorator(new LootLoader(), spawnSheet);
		IRoomDecorator bossRoomDecorator = new BossRoomDecorator(new BossLootLoader());
		IRoomDecorator libraryDecorator = new LibraryRoomDecorator(new LootLoader(), spawnSheet);
		
		/*
		 *  NOTE careful here. IRoomGenerator can alter the state of the IGenerationStrategy with a
		 *  IDungeonsBlockProvider of it's choosing. Don't share between generators or have to synchronize
		 */		

		// build the entrance
		steps.add(() -> buildEntrance(world, random, dungeon, layoutAssigner, factory, roomDecorator, styleSheet));

		/*
		 * build all the levels 
		 */
		// shared between steps
		int[] libraryCount = {0};
		// generate all the rooms
		for (Level level : dungeon.getLevels()) {
			Theme theme = null;
			if (level.getConfig().getTheme() != null && !level.getConfig().getTheme().equals("")) {
				// TODO select the theme - themes need to be mapped.
				theme = styleSheet.getThemes().get(level.getConfig().getTheme());
			}
			// fall back to the dungeon theme if not set or the configured theme name is not in the style sheet
			final Theme levelTheme = theme != null ? theme : dungeon.getTheme();
			
			// build the rooms for the level
			for (Room room : level.getRooms()) {
				steps.add(() -> {
					// assign a layout to the room
					layoutAssigner.assign(random, room);
					
					// get the room generator
					IRoomGenerator roomGen = factory.createRoomGenerator(random, room, level.getConfig().isSupport());
					
					// generate the room into the world
					roomGen.generate(world, random, room, levelTheme, styleSheet, level.getConfig());
		
					// TODO need a decorator factory
					if (room.getType() == Type.BOSS) {
						Dungeons2.log.debug("Boss Room @ {}", room.getCoords());
						// TODO if going to integrate with Treasure2, need to know how big the dungeons is: size, # of levels
						// in order to select the correct chest/loot table rarity
						// TODO also should make it randomized ex 50% normal boss chest, 50% treasure2 chest
						// extend the bossRoomDecorator -> TreasureBossRoomDecorator and set more properties
						// TODO should return ICoords of boss chest so Dungeon can record
						bossRoomDecorator.decorate(world, random, dungeon, roomGen.getGenerationStrategy().getBlockProvider(), room, level.getConfig());
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
							&& libraryCount[0] < 3) {
						Dungeons2.log.debug("Using library decorator for room @ " + room.getCoords().toShortString());
						libraryDecorator.decorate(world, random, dungeon, roomGen.getGenerationStrategy().getBlockProvider(), room, level.getConfig());
						libraryCount[0]++;
					}
					else {
						// decorate the room (spawners, chests, webbings, etc)
						roomDecorator.decorate(world, random, dungeon, roomGen.getGenerationStrategy().getBlockProvider(), room, level.getConfig());
					}
				});
			}
			// create a list of generated hallways
			List<Hallway> generatedHallways = new ArrayList<>();
			// generate the hallways
			for (Hallway hallway : level.getHallways()) {
				steps.add(() -> {
					// assign a layout
					layoutAssigner.assign(random, hallway);
					// NOTE passing hallways here is a list of hallways (excluding the current one, to check if they intersect
					IRoomGenerator roomGen = factory.createHallwayGenerator(hallway, level.getRooms(), generatedHallways, level.getConfig().isSupport());
					roomGen.generate(world, random, hallway, levelTheme, styleSheet, level.getConfig());
					// add the hallway to the list of generated hallways
					generatedHallways.add(hallway);
				});
			}
			
			// generate the shafts
			for (Shaft shaft : level.getShafts()) {
				steps.add(() -> {
					// assign the layout
					shaft.setLayout(shaft.getParent().getLayout());
					IRoomGenerator roomGen = factory.createShaftGenerator(shaft, level.getConfig().isSupport());
					roomGen.generate(world, random, shaft, levelTheme, styleSheet, level.getConfig());
				});
			}
		}
		return steps;
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
//		Dungeons2.log.debug("Dungeon -> {}", dungeon);
//		Dungeons2.log.debug("Levels -> {}", dungeon.getLevels());
//		Dungeons2.log.debug("Levels.size -> {}", dungeon.getLevels().size());
//		Dungeons2.log.debug("Levels[0].config -> {}", dungeon.getLevels().get(0).getConfig());
		ILevelConfig entranceLevelConfig = dungeon.getLevels().get(0).getConfig().copy();
		entranceLevelConfig.setDecayMultiplier(Math.min(5, entranceLevelConfig.getDecayMultiplier())); // increase the decay multiplier to a minimum of 5
		// assign a layout to the entrance room
		layoutAssigner.assign(random, entranceRoom);
		IRoomGenerator roomGen = factory.createRoomGenerator(random, entranceRoom, dungeon.getLevels().get(0).getConfig().isSupport());
		// TODO need to provide the entrance room generator with a different level config that uses a higher decay multiplier
		// to create a much more decayed surface structure.
		roomGen.generate(world, random, entranceRoom, dungeon.getTheme(), styleSheet, entranceLevelConfig);
		roomDecorator.decorate(world, random, dungeon, roomGen.getGenerationStrategy().getBlockProvider(), entranceRoom, entranceLevelConfig);
		// weather the surface entrance into a ruin. material decay alone never changes its silhouette.
		RuinProcessor.ruin(world, random, entranceRoom);
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
