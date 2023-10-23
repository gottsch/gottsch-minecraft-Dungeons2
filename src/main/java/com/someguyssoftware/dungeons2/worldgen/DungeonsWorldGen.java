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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.block.DeferredDungeonGeneratorBlock;
import com.someguyssoftware.dungeons2.block.DungeonsBlocks;
import com.someguyssoftware.dungeons2.builder.DungeonBuilderTopDown;
import com.someguyssoftware.dungeons2.builder.IDungeonBuilder;
import com.someguyssoftware.dungeons2.builder.LevelBuilder;
import com.someguyssoftware.dungeons2.cache.DelayedFeatureSimpleDistanceCache;
import com.someguyssoftware.dungeons2.cache.FeatureCaches;
import com.someguyssoftware.dungeons2.cache.SimpleDistanceCache;
import com.someguyssoftware.dungeons2.config.BuildPattern;
import com.someguyssoftware.dungeons2.config.BuildSize;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.generator.DungeonGenerator;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonInfo;
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
import com.someguyssoftware.gottschcore.random.RandomHelper;
import com.someguyssoftware.gottschcore.random.RandomProbabilityCollection;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.entity.player.EntityPlayer;
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

	private int waitChunksCount = 0;
	
	// biome white/black lists
	private List<BiomeTypeHolder> biomeWhiteList;
	private List<BiomeTypeHolder> biomeBlackList;
	
	// the dungeon geneator
	private DungeonGenerator generator;
	
	// sheets
	private StyleSheet styleSheet;
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
	 * This executes for every chunk.
	 */
	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
		
		if (generator == null) return;
 
     	// get the dimension
     	int dimensionId = world.provider.getDimension();
     	     	
		// test the dimension
		if (!meetsDimensionCriteria(dimensionId)) { 
			return;
		}
		
		// get the dungeon registry
		DelayedFeatureSimpleDistanceCache<DungeonInfo> cache = FeatureCaches.CACHE;
		if (cache == null) {
			Dungeons2.log.debug("cache is null. this shouldn't be. should be initialized.");
			return;
		}
		
		if (!meetsWorldAgeCriteria(world, cache)) {
			// increment the local feature wait count
			this.waitChunksCount++;
			// update the cache's delay count
			cache.setDelayCount(waitChunksCount);
			return;
		}
		
        int xSpawn = chunkX * 16 + 7;
        int zSpawn = chunkZ * 16 + 7;
        ICoords coords = new Coords(xSpawn, 64, zSpawn);
//        Dungeons2.log.debug("starting Coords:" + coords);
        
		if (!meetsBiomeCriteria(world, coords)) {
			return;
		}		
		
		// check against all cached dungeons
		if (!meetsProximityCriteria(world, coords, ModConfig.minDistancePerDungeon, cache)) {			
			return;
		}
		
		// check if meets the probability criteria
		if (!meetsProbabilityCriteria(random)) {
			Dungeons2.log.info("failed probability -> {}", coords);
			failAndPlacehold(world, cache, coords);
			return;
		}

		// get land coords
		ICoords spawnCoords = WorldInfo.getDryLandSurfaceCoords(world, coords);
		if (spawnCoords == WorldInfo.EMPTY_COORDS) {
			Dungeons2.log.info("failed dry land -> {}", coords);
			failAndPlacehold(world, cache, coords);
			return;
		}		

		// place deferred block
		world.setBlockState(spawnCoords.toPos(), DungeonsBlocks.DEFERRED_DUNGEON_GENERATOR.getDefaultState(), 3);
		
		// cache placeholder
		DungeonInfo info = new DungeonInfo();
		info.setCoords(spawnCoords);
		cache.cache(spawnCoords, info);
		Dungeons2.log.info("deferred placerholder -> {}", info);

     	// save world data
		DungeonsGenSavedData savedData = DungeonsGenSavedData.get(world);
    	if (savedData != null) {
    		savedData.markDirty();
    	}
		
	}

	/**
	 * 
	 * @param world
	 * @param cache
	 * @param coords
	 * @return
	 */
	private boolean failAndPlacehold(World world, DelayedFeatureSimpleDistanceCache<DungeonInfo> cache, ICoords coords) {
		// add placeholder
		DungeonInfo info = new DungeonInfo();
		info.setCoords(coords);
		cache.cache(coords, info);
		Dungeons2.log.info("failed and place holder -> {}", info);
		// need to save on fail
		DungeonsGenSavedData savedData = DungeonsGenSavedData.get(world);
    	if (savedData != null) {
    		savedData.markDirty();
    	}
		return false;
	}

	/**
	 * 
	 * @param random
	 * @return
	 */
	private boolean meetsProbabilityCriteria(Random random) {
		if (!RandomHelper.checkProbability(random, ModConfig.dungeonGenProbability)) {
			Dungeons2.log.debug("does not meet generate probability.");
			return false;
		}
		return true;
	}

	/**
	 * Determines if the coords is outside/farther away from the minDistance
	 * @param world
	 * @param coords
	 * @param minDistance
	 * @param cache
	 * @return
	 */
	private boolean meetsProximityCriteria(World world, ICoords coords, int minDistance,
			DelayedFeatureSimpleDistanceCache<DungeonInfo> cache) {
		if (cache == null || cache.getValues().isEmpty()) {
			Dungeons2.log.debug("unable to locate the cache or the cache doesn't contain any values");
			return true;
		}

		// generate a box with coords as center and minDistance as radius
		ICoords startBox = new Coords(coords.getX() - minDistance, 0, coords.getZ() - minDistance);
		ICoords endBox = new Coords(coords.getX() + minDistance, 0, coords.getZ() + minDistance);

		// find if box overlaps anything in the registry
		if (cache.withinArea(startBox, endBox)) {
			return false;
		}
		return true;
	}

	/**
	 * 
	 * @param world
	 * @param coords
	 * @return
	 */
	private boolean meetsBiomeCriteria(World world, ICoords coords) {
		Biome biome = world.getBiome(coords.toPos());
		if (WorldInfo.isClientSide(world)/*world.isRemote*/) {
			Dungeons2.log.debug("biome -> {}", biome.getBiomeName());
		}
	    if (!BiomeHelper.isBiomeAllowed(biome, biomeWhiteList, biomeBlackList)) {
	    	if (WorldInfo.isClientSide(world)/*world.isRemote*/) {
	    		Dungeons2.log.debug("{} is not a valid biome.", biome.getBiomeName());
	    	}
	    	return false;
	    }
	    return true;
	}

	/**
	 * 
	 * @param world
	 * @param cache
	 * @return
	 */
	private boolean meetsWorldAgeCriteria(World world, DelayedFeatureSimpleDistanceCache<DungeonInfo> cache) {
		// wait count check		
		// TODO since wells are very rare, a well may not generated before the world is save and player exits
		// in this case the waitChunksCount would be reset when the world restarts. this value needs to be saved.
		if (cache.getValues().isEmpty() && waitChunksCount < ModConfig.waitChunks) {
			Dungeons2.log.debug("world is too young");
			return false;
		}
		return true;
	}

	private boolean meetsDimensionCriteria(int dimensionId) {
		return dimensionId == 0 ? true : false;
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
	
	// TODO this might be needed in the DeferredDungeonGeneratorTileEntity
	/**
	 * 
	 * @param world
	 * @param coords
	 * @return
	 */
//	private ICoords getReduxCoords(World world, ICoords coords) {
//		/*
//		 * Get the closest player's distance from coords
//		 */
//        double closestDistSq = -1.0D;
//        ICoords closestCoords = null;
//		for (int i = 0; i < world.playerEntities.size(); ++i) {
//			EntityPlayer player = world.playerEntities.get(i);
//			ICoords playerCoords = new Coords(player.getPosition());
//			double dist = coords.getDistanceSq(playerCoords);
//			if (closestDistSq == -1.0D || dist < closestDistSq) {
//				closestDistSq = dist;
//				closestCoords = playerCoords;
//			}
//		}
//		
//		if (closestCoords != null) {
//			Dungeons2.log.debug(
//				String.format("The closest player is %s squared blocks away at pos %s", String.valueOf(closestDistSq), closestCoords.toShortString())
//			);
//		}
//		
//		// determine if closest player is within generate threshold (80 blocks / 5 chunks)
//		if (closestDistSq > DEFAULT_GENERATION_PROXIMITY_SQAURED) {
//			Dungeons2.log.debug("Closest player is outside of generation proximity. Moving to a closer position.");
//			/*
//			 * move the spawn coords to 80 blocks away.
//			 * use scaling method instead of slope & pythagorean theorem
//			 *  to avoid calculating squares and square roots.
//			 * 
//			 */
//			
//			// get dist ratio
//			double ratio = DEFAULT_GENERATION_PROXIMITY_SQAURED / closestDistSq;
////			Dungeons2.log.debug("Distance ratio: " + ratio);
//			
//			// get x, z delta (or distance in blocks along the axis)
//			ICoords delta = coords.delta(closestCoords);
////			Dungeons2.log.debug("Delta coords: " + delta.toShortString());
//			
//			// reduce the x, z distances by (1 - pecent)
//			double redux = 1 - ratio;
//			double xRedux = delta.getX() * redux;
//			double zRedux = delta.getZ() * redux;
////			Dungeons2.log.debug(String.format("Redux: %s, xdux: %s, zdux: %s", String.valueOf(redux), String.valueOf(xRedux), String.valueOf(zRedux)));
//										
//			int xSpawn = coords.getX() - ((int)Math.floor(xRedux));
//			int zSpawn = coords.getZ() - ((int)Math.floor(zRedux));
////			Dungeons2.log.debug("redux xSpawn:" + xSpawn);
////			Dungeons2.log.debug("redux zSpawn:" + zSpawn);
//			int ySpawn = WorldInfo.getHeightValue(world, new Coords(xSpawn, 255, zSpawn));
////			Dungeons2.log.debug("redux ySpawn from WorldInfo:" + ySpawn);
//			
//			coords = new Coords (xSpawn, ySpawn, zSpawn);	
//		}		
//		return coords;
//	}

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
//	public int getChunksSinceLastDungeon() {
//		return chunksSinceLastDungeon;
//	}

	/**
	 * @param chunksSinceLastDungeon the chunksSinceLastDungeon to set
	 */
//	public void setChunksSinceLastDungeon(int chunksSinceLastDungeon) {
//		this.chunksSinceLastDungeon = chunksSinceLastDungeon;
//	}

	/**
	 * @return the lastDungeonCoords
	 */
//	public ICoords getLastDungeonCoords() {
//		return lastDungeonCoords;
//	}

	/**
	 * @param lastDungeonCoords the lastDungeonCoords to set
//	 */
//	public void setLastDungeonCoords(ICoords lastDungeonBlockPos) {
//		this.lastDungeonCoords = lastDungeonBlockPos;
//	}

//	public synchronized boolean isGenerating() {
//		return isGenerating;
//	}
//
//	public synchronized void setGenerating(boolean isGenerating) {
//		this.isGenerating = isGenerating;
//	}

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

	public StyleSheet getStyleSheet() {
		return styleSheet;
	}

	public SpawnSheet getSpawnSheet() {
		return spawnSheet;
	}

	public DungeonGenerator getGenerator() {
		return generator;
	}
}
