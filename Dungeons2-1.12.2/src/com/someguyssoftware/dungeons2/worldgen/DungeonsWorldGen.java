/**
 * 
 */
package com.someguyssoftware.dungeons2.worldgen;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.builder.DungeonBuilderTopDown;
import com.someguyssoftware.dungeons2.builder.IDungeonBuilder;
import com.someguyssoftware.dungeons2.builder.LevelBuilder;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.chest.ChestSheetLoader;
import com.someguyssoftware.dungeons2.config.BuildDirection;
import com.someguyssoftware.dungeons2.config.BuildPattern;
import com.someguyssoftware.dungeons2.config.BuildSize;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.config.PRESET_DUNGEON_CONFIGS;
import com.someguyssoftware.dungeons2.config.PRESET_LEVEL_CONFIGS;
import com.someguyssoftware.dungeons2.generator.DungeonGenerator;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.DungeonInfo;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;
import com.someguyssoftware.dungeons2.printer.DungeonPrettyPrinter;
import com.someguyssoftware.dungeons2.registry.DungeonRegistry;
import com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import com.someguyssoftware.dungeons2.spawner.SpawnSheetLoader;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.StyleSheetLoader;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.gottschcore.biome.BiomeHelper;
import com.someguyssoftware.gottschcore.biome.BiomeTypeHolder;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.IRandomProbabilityItem;
import com.someguyssoftware.gottschcore.random.RandomProbabilityCollection;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * 
 * @author Mark Gottschling on Oct 12, 2016
 *
 */
public class DungeonsWorldGen implements IWorldGenerator {
	// the number of blocks of half a chunk (radius) (a chunk is 16x16)
	public static final int CHUNK_RADIUS = 8;

	private static final double DEFAULT_GENERATION_PROXIMITY_SQAURED = 6400;
		
	/*
	 *  values that control the frequency of dungeon generation
	 *  persisted to game save data
	 */	
	private int chunksSinceLastDungeon = 0;
	private ICoords lastDungeonCoords = null;
	private boolean isGenerating = false;
	
	// biome white/black lists
	private List<BiomeTypeHolder> biomeWhiteList;
	private List<BiomeTypeHolder> biomeBlackList;
	
	// the dungeon geneator
	private DungeonGenerator generator;
	
	// sheets
	private StyleSheet styleSheet;
	private ChestSheet chestSheet;
	private SpawnSheet spawnSheet;
	
	private static RandomProbabilityCollection<IRandomProbabilityItem> patterns = new RandomProbabilityCollection<>();
	private static RandomProbabilityCollection<IRandomProbabilityItem> levelSizes = new RandomProbabilityCollection<>();
	private static RandomProbabilityCollection<IRandomProbabilityItem> dungeonSizes = new RandomProbabilityCollection<>();
	
	/**
	 * 
	 * @param generator
	 */
	public DungeonsWorldGen() {
		// setup the dungeon generator
		try {
			generator = new DungeonGenerator();
			init();
		} catch (Exception e) {
			Dungeons2.log.error("Unable to instantiate DungeonGenerator:", e);
		}
	}
	
	/**
	 * 
	 */
	public void init() {		
		// initialize white/black lists
		biomeWhiteList = new ArrayList<>(5);
		biomeBlackList = new ArrayList<>(5);

		// populate white/black lists with values from config
		BiomeHelper.loadBiomeList(ModConfig.generalDungeonBiomeWhiteList, biomeWhiteList);
		BiomeHelper.loadBiomeList(ModConfig.generalDungeonBiomeBlackList, biomeBlackList);
		
	    patterns.add(76, new RandomBuildPattern(BuildPattern.SQUARE));
	    patterns.add(12, new RandomBuildPattern(BuildPattern.HORZ));
	    patterns.add(12, new RandomBuildPattern(BuildPattern.VERT));
	    
	    levelSizes.add(50, new RandomBuildSize(BuildSize.SMALL));
	    levelSizes.add(25, new RandomBuildSize(BuildSize.MEDIUM));
	    levelSizes.add(15, new RandomBuildSize(BuildSize.LARGE));
	    levelSizes.add(10, new RandomBuildSize(BuildSize.VAST));
	    
	    dungeonSizes.add(30, new RandomBuildSize(BuildSize.SMALL));
	    dungeonSizes.add(25, new RandomBuildSize(BuildSize.MEDIUM));
	    dungeonSizes.add(25, new RandomBuildSize(BuildSize.LARGE));
	    dungeonSizes.add(20, new RandomBuildSize(BuildSize.VAST));

		try {	
			// TODO this should be static in own classes
			// add the directories if they don't exist
			Path folder = Paths.get(ModConfig.dungeonsFolder, StyleSheetLoader.BUILT_IN_STYLE_SHEET_SUB_FOLDER);
			try {
				Files.createDirectory(folder);
			}
			catch(FileAlreadyExistsException e) {;}
			
			folder = Paths.get(ModConfig.dungeonsFolder, ChestSheetLoader.BUILT_IN_CHEST_SHEET_SUB_FOLDER);
			try {
				Files.createDirectory(folder);
			}
			catch(FileAlreadyExistsException e) {;}
			
			folder = Paths.get(ModConfig.dungeonsFolder, SpawnSheetLoader.BUILT_IN_SPAWN_SHEET_SUB_FOLDER);
			try {
				Files.createDirectory(folder);
			}
			catch(FileAlreadyExistsException e) {;}
			
			// load the sheets
			styleSheet = StyleSheetLoader.loadAll();
			if (styleSheet == null || styleSheet.getStyles() == null || styleSheet.getStyles().size() == 0) {
				Dungeons2.log.debug("Stylesheet empty, loading default style sheet...");
				styleSheet = StyleSheetLoader.load();
			}
			this.chestSheet = ChestSheetLoader.loadAll();
			Dungeons2.log.debug("Returned Loaded chestSheet:" + this.chestSheet);
			if (this.chestSheet == null || this.chestSheet.getItems() == null || this.chestSheet.getItems().size() == 0) {
				Dungeons2.log.debug("Chestsheet empty, loading default chest sheet...");
				this.chestSheet = ChestSheetLoader.load();
			}			
			this.spawnSheet = SpawnSheetLoader.loadAll();
			if (this.spawnSheet == null || this.spawnSheet.getGroups() == null || this.spawnSheet.getGroups().size() == 0) {
				Dungeons2.log.debug("Spawnsheet empty, loading default spawn sheet...");
				spawnSheet = SpawnSheetLoader.load();
			}
		} catch (Exception e) {
			Dungeons2.log.error("Error loading Style/Chest/Spawn sheet: ", e);
			return;
		}		
	}
	
	/**
	 * This executes for every block in the chunk.
	 */
	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
		
		if (generator == null) return;
		
		// test for which overworld dimension
		switch(world.provider.getDimension()) {
		// surface/overworld
		case 0:
			break;
		default:
			return;
		}
		
		// result of generation
     	boolean isGenerated = false;
     	
	    // increment last dungeon chunk count
	    chunksSinceLastDungeon++;
	 
     	if (!isGenerating() && chunksSinceLastDungeon > ModConfig.minChunksPerDungeon) {
     		Dungeons2.log.debug(String.format("Gen: pass first test: chunksSinceLast: %d, minChunks: %d", chunksSinceLastDungeon, ModConfig.minChunksPerDungeon));
 			// clear count
			chunksSinceLastDungeon = 0;
			
     		/*
     		 * get current chunk position
     		 */
            
            // spawn @ middle of chunk
            int xSpawn = chunkX * 16 + 8;
            int zSpawn = chunkZ * 16 + 8;
            
            // the get first surface y (could be leaves, trunk, water, etc)
 //>>           int ySpawn = world.getChunkFromChunkCoords(chunkX, chunkZ).getHeightValue(8, 8);

            ICoords coords = new Coords(xSpawn, 64, zSpawn);
//     		Dungeons2.log.debug("Starting Coords:" + coords);
     		
            // TODO remove redux... dungeon generator will be responsible for determining field based on 
            // the spawn point            
//     		coords = getReduxCoords(world, coords);
//			Dungeons2.log.debug("New coords:" + coords.toShortString());
            
//			Dungeons2.log.debug("Last Dungeon dist^2:" + lastDungeonCoords.getDistanceSq(coords));
     		// check if  the min distance between dungeons is met
     		if (lastDungeonCoords == null || lastDungeonCoords.getDistanceSq(coords) > (ModConfig.minDistancePerDungeon * ModConfig.minDistancePerDungeon)) {

				// 1. test if dungeon meets the probability criteria
				if (random.nextInt(100) > ModConfig.dungeonGenProbability) {
					Dungeons2.log.debug("Dungeon fail generate probability.");
					return;
				}		

				// 2. test if correct biome
				Biome biome = world.getBiome(coords.toPos());

			    if (!BiomeHelper.isBiomeAllowed(biome, biomeWhiteList, biomeBlackList)) {
			    	if (world.isRemote) {
			    		Dungeons2.log.debug(String.format("[%s] is not a valid biome.", biome.getBiomeName()));
			    	}
			    	return;
			    }
			    
			    // TODO this distance is affected by fields as the coords does not necessarily refer to where the dungeon actually spawns
     			// 2.5 check against all registered dungeons
     			if (isRegisteredDungeonWithinDistance(world, coords, ModConfig.minDistancePerDungeon)) {
   					Dungeons2.log.debug("The distance to the nearest dungeon is less than the minimun required.");
     				return;
     			}
     			
			    // set the generating flag
			    this.setGenerating(true);
			    
			    // 3. get the sheets - NOTE see constructor

				// 4. select random theme, pattern, size and direction
//				Dungeons2.log.debug("StyleSheet:" + styleSheet);
//				Dungeons2.log.debug("Themes.size: " + styleSheet.getThemes().size());
			    
			    // TODO load the patterns and size into RandomProbabilityCollection with differing weights with small, square being more common
			    // TODO eventurally these values should be part of a dungeonSheet to be able to config it.
			    // get the dungeons for this biome
			    List<IDungeonConfig> dcList = Dungeons2.dgnCfgMgr.getByBiome(biome.getBiomeName());
			    // select one
			    IDungeonConfig dc = dcList.get(random.nextInt(dcList.size()));
			    Dungeons2.log.debug("selected dungeon config -> {}", dc);
			    
	   			Theme theme = styleSheet.getThemes().get(styleSheet.getThemes().keySet().toArray()[random.nextInt(styleSheet.getThemes().size())]);
//    			BuildPattern pattern = BuildPattern.values()[random.nextInt(BuildPattern.values().length)];
	   			BuildPattern pattern = ((RandomBuildPattern)patterns.next()).pattern;
//				BuildSize levelSize = BuildSize.values()[random.nextInt(BuildSize.values().length)];
//				BuildSize dungeonSize = BuildSize.values()[random.nextInt(BuildSize.values().length)];
	   			BuildSize levelSize = ((RandomBuildSize)levelSizes.next()).size;
	   			BuildSize dungeonSize = ((RandomBuildSize)dungeonSizes.next()).size;
				BuildDirection direction = BuildDirection.values()[random.nextInt(BuildDirection.values().length)];
				
				// TODO this should be inside of DungeonConfig
				// 5. determine a preset level config based on pattern and size
				LevelConfig levelConfig = PRESET_LEVEL_CONFIGS.getConfig(pattern, levelSize, direction);
				Dungeons2.log.debug(String.format("Using PRESET: dungeonSize: %s, pattern: %s, levelSize: %s, direction: %s",
						dungeonSize.name(), pattern.name(), levelSize.name(), direction.name()));
				
				// TODO this should be inside dungeon builder
				// get the level builder
				LevelBuilder levelBuilder = new LevelBuilder(levelConfig);
				
				// 6. create a dungeon builder using the defined level builder(s)
				IDungeonBuilder builder = new DungeonBuilderTopDown(levelBuilder);				
				// TODO select a dungeon config - check config if support or no support
				// TODO propogate support value to levelConfig(s)
				// NOTE for future, dungeon config should probably have an array of levelConfig so each level can have it's own config
				// ex. a dungeon where the levels increase in size the further you go down (volcano).
				// NOTE for future, dungeon config(s) should be loaded via JSON
				// TODO probably need a separate method that constructs the whole dungeon config with level configs in it
				// the dungeon config will drive part of the level config, ie # of rooms etc.
				// 7. determine a preset dungeon config base on size
//				DungeonConfig dungeonConfig = new DungeonConfig();
				DungeonConfig dungeonConfig = PRESET_DUNGEON_CONFIGS.getConfig(dungeonSize);

//				Dungeons2.log.debug(
//						String.format("Building D2 dungeons @ %s\n" + 
//								"\tUsing LevelConfig: %s\n" +
//								"\tUsing LevelBuilder: %s\n" +
//								"\tUsing DungeonConfig: %s", pos, levelConfig, levelBuilder, dungeonConfig));
				
				// 7. build the dungeon
				Dungeons2.log.debug("Starting BUILD process...");
//				Dungeon dungeon = builder.build(world, random, coords, dungeonConfig);
				Dungeon dungeon = builder.build(world, random, coords, dc);
				Dungeons2.log.debug("BUILD process complete.");
				/*
				 *  NOTE for now propagate the support property from dungeonConfig to levelConfig.
				 *  In future each level in a dungeon may have a different support setting
				 */
				levelConfig.setSupportOn(dungeonConfig.useSupport());

	   			// 8. update the dungeon with the theme
	   			dungeon.setTheme(theme);
	   			
				if (dungeon != null && dungeon != IDungeonBuilder.EMPTY_DUNGEON) {
					// 9. generate the dungeon
					try {
						Dungeons2.log.debug("Start GENERATE process...");
						isGenerated = generator.generate(world, random, dungeon, styleSheet, chestSheet, spawnSheet);
						Dungeons2.log.debug("GENERATE process complete.");
					} catch (FileNotFoundException e) {
						Dungeons2.log.error("Error generating dungeon @ " + coords.toShortString(), e);
					}
				}
				
				if (isGenerated) {
					// register the dungeon with the Dungeon Registry
					DungeonInfo info = new DungeonInfo(dungeon, pattern, dungeonSize, levelSize, direction);
					// update the coords to the actual entrance (changed due to usage of field)
					coords = info.getCoords();
					DungeonRegistry.getInstance().register(coords.toShortString(), info);

					// update the last dungeon position
					lastDungeonCoords = coords;
					Dungeons2.log.info("Dungeon generated @ " + coords.toShortString());
					
					// TODO if generated and config.dumps is on, generate a dungeon dump (text file of all properties or dungeon, levels, rooms, doors, etc)
					// TODO is generated and config.dumps.json is on, generate a dungeon json file. (same as above but in json format)
					if (ModConfig.enableDumps) {
						dump(dungeon);
					}
				}
				
				// set the generating flag
			    this.setGenerating(false);
     		}
     	}
     	// save world data
		DungeonsGenSavedData savedData = DungeonsGenSavedData.get(world);
    	if (savedData != null) {
    		savedData.markDirty();
    	}
		
	}

	/**
	 * Writes a human-readable version of the dungeon to disk.
	 * @param dungeon
	 */
	public void dump(Dungeon dungeon ) {
		DungeonPrettyPrinter printer  =new DungeonPrettyPrinter();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyymmdd");
		
		String filename = String.format("dungeon-%s-%s.txt", 
				formatter.format(new Date()), 
				dungeon.getEntrance().getBottomCenter().toShortString().replaceAll(" ", "-"));
		
				//"dungeon-" + formatter.format(new Date()) + "-"
//		+ dungeon.getEntrance().getBottomCenter().toShortString().replaceAll(" ", "-")
//		+ "-" + ".txt";		

		Path path = Paths.get(ModConfig.dungeonsFolder, "dumps").toAbsolutePath();
		try {
			Files.createDirectories(path);			
		} catch (IOException e) {
			Dungeons2.log.error("Couldn't create directories for dump files:", e);
			return;
		}
		String s = printer.print(dungeon, Paths.get(path.toString(), filename).toString());
//		Dungeons2.log.debug("\n" + s);
	}
	
	/**
	 * 
	 * @param world
	 * @param coords
	 * @return
	 */
	private ICoords getReduxCoords(World world, ICoords coords) {
		/*
		 * Get the closest player's distance from coords
		 */
        double closestDistSq = -1.0D;
        ICoords closestCoords = null;
		for (int i = 0; i < world.playerEntities.size(); ++i) {
			EntityPlayer player = world.playerEntities.get(i);
			ICoords playerCoords = new Coords(player.getPosition());
			double dist = coords.getDistanceSq(playerCoords);
			if (closestDistSq == -1.0D || dist < closestDistSq) {
				closestDistSq = dist;
				closestCoords = playerCoords;
			}
		}
		
		if (closestCoords != null) {
			Dungeons2.log.debug(
				String.format("The closest player is %s squared blocks away at pos %s", String.valueOf(closestDistSq), closestCoords.toShortString())
			);
		}
		
		// determine if closest player is within generate threshold (80 blocks / 5 chunks)
		if (closestDistSq > DEFAULT_GENERATION_PROXIMITY_SQAURED) {
			Dungeons2.log.debug("Closest player is outside of generation proximity. Moving to a closer position.");
			/*
			 * move the spawn coords to 80 blocks away.
			 * use scaling method instead of slope & pythagorean theorem
			 *  to avoid calculating squares and square roots.
			 * 
			 */
			
			// get dist ratio
			double ratio = DEFAULT_GENERATION_PROXIMITY_SQAURED / closestDistSq;
//			Dungeons2.log.debug("Distance ratio: " + ratio);
			
			// get x, z delta (or distance in blocks along the axis)
			ICoords delta = coords.delta(closestCoords);
//			Dungeons2.log.debug("Delta coords: " + delta.toShortString());
			
			// reduce the x, z distances by (1 - pecent)
			double redux = 1 - ratio;
			double xRedux = delta.getX() * redux;
			double zRedux = delta.getZ() * redux;
//			Dungeons2.log.debug(String.format("Redux: %s, xdux: %s, zdux: %s", String.valueOf(redux), String.valueOf(xRedux), String.valueOf(zRedux)));
										
			int xSpawn = coords.getX() - ((int)Math.floor(xRedux));
			int zSpawn = coords.getZ() - ((int)Math.floor(zRedux));
//			Dungeons2.log.debug("redux xSpawn:" + xSpawn);
//			Dungeons2.log.debug("redux zSpawn:" + zSpawn);
			int ySpawn = WorldInfo.getHeightValue(world, new Coords(xSpawn, 255, zSpawn));
//			Dungeons2.log.debug("redux ySpawn from WorldInfo:" + ySpawn);
			
			coords = new Coords (xSpawn, ySpawn, zSpawn);	
		}		
		return coords;
	}

	/**
	 * 
	 * @param world
	 * @param pos
	 * @param minDistance
	 * @return
	 */
	public boolean isRegisteredDungeonWithinDistance(World world, ICoords coords, int minDistance) {
		
		double minDistanceSq = minDistance * minDistance;
		
		// get a list of dungeons
		List<DungeonInfo> infos = DungeonRegistry.getInstance().getEntries();

		if (infos == null || infos.size() == 0) {
			Dungeons2.log.debug("Unable to locate the Dungeon Registry or the Registry doesn't contain any values");
			return false;
		}

		for (DungeonInfo info : infos) {
			// calculate the distance to the poi
			double distance = coords.getDistanceSq(info.getCoords());
//		    Dungeons2.log.debug("Dungeon dist^2: " + distance);
			if (distance < minDistanceSq) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * @return the chunksSinceLastDungeon
	 */
	public int getChunksSinceLastDungeon() {
		return chunksSinceLastDungeon;
	}

	/**
	 * @param chunksSinceLastDungeon the chunksSinceLastDungeon to set
	 */
	public void setChunksSinceLastDungeon(int chunksSinceLastDungeon) {
		this.chunksSinceLastDungeon = chunksSinceLastDungeon;
	}

	/**
	 * @return the lastDungeonCoords
	 */
	public ICoords getLastDungeonCoords() {
		return lastDungeonCoords;
	}

	/**
	 * @param lastDungeonCoords the lastDungeonCoords to set
	 */
	public void setLastDungeonCoords(ICoords lastDungeonBlockPos) {
		this.lastDungeonCoords = lastDungeonBlockPos;
	}

	public synchronized boolean isGenerating() {
		return isGenerating;
	}

	public synchronized void setGenerating(boolean isGenerating) {
		this.isGenerating = isGenerating;
	}

	private class RandomBuildPattern implements IRandomProbabilityItem {
		public BuildPattern pattern;
		public int prob = 0;
		
		public RandomBuildPattern(BuildPattern bp) {
			this.pattern = bp;
		}
		@Override
		public int getProbability() {
			return prob;
		}		
	}
	
	private class RandomBuildSize implements IRandomProbabilityItem {
		public BuildSize size;
		public int prob = 0;
		
		public RandomBuildSize(BuildSize bs) {
			this.size = bs;
		}
		@Override
		public int getProbability() {
			return prob;
		}		
	}
}
