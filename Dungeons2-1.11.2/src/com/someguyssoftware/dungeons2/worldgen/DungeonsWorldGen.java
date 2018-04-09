/**
 * 
 */
package com.someguyssoftware.dungeons2.worldgen;

import java.io.FileNotFoundException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import com.someguyssoftware.dungeons2.config.GeneralConfig;
import com.someguyssoftware.dungeons2.config.PRESET_DUNGEON_CONFIGS;
import com.someguyssoftware.dungeons2.config.PRESET_LEVEL_CONFIGS;
import com.someguyssoftware.dungeons2.generator.DungeonGenerator;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;
import com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import com.someguyssoftware.dungeons2.spawner.SpawnSheetLoader;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.StyleSheetLoader;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.mod.Coords;
import com.someguyssoftware.mod.worldgen.BiomeTypeHolder;
import com.someguyssoftware.mod.worldgen.util.WorldGenUtil;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * 
 * @author Mark Gottschling on Oct 12, 2016
 *
 */
public class DungeonsWorldGen implements IWorldGenerator {
	// the number of blocks of half a chunk (radius) (a chunk is 16x16)
	public static final int CHUNK_RADIUS = 16;	 // <-- should be 8, not 16
	/*
	 *  values that control the frequency of dungeon generation
	 *  persisted to game save data
	 */	
	private int chunksSinceLastDungeon = 0;
	private BlockPos lastDungeonBlockPos = null;
	
	// biome white/black lists
	private List<BiomeTypeHolder> biomeWhiteList;
	private List<BiomeTypeHolder> biomeBlackList;
	
	// the dungeon geneator
	private DungeonGenerator generator;
	
	// sheets
	private StyleSheet styleSheet;
	private ChestSheet chestSheet;
	private SpawnSheet spawnSheet;
	
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
		WorldGenUtil.loadBiomeApprovalList(GeneralConfig.generalDungeonBiomeWhiteList, biomeWhiteList);
		WorldGenUtil.loadBiomeApprovalList(GeneralConfig.generalDungeonBiomeBlackList, biomeBlackList);
		

		try {		
			// add the directories if they don't exist
			Path folder = Paths.get(GeneralConfig.dungeonsFolder, StyleSheetLoader.BUILT_IN_STYLE_SHEET_SUB_FOLDER);
			try {
				Files.createDirectory(folder);
			}
			catch(FileAlreadyExistsException e) {;}
			
			folder = Paths.get(GeneralConfig.dungeonsFolder, ChestSheetLoader.BUILT_IN_CHEST_SHEET_SUB_FOLDER);
			try {
				Files.createDirectory(folder);
			}
			catch(FileAlreadyExistsException e) {;}
			
			folder = Paths.get(GeneralConfig.dungeonsFolder, SpawnSheetLoader.BUILT_IN_SPAWN_SHEET_SUB_FOLDER);
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
	 * 
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
		
	    // increment last dungeon chunk count
	    chunksSinceLastDungeon++;
	    
		// get the x,z world coords, centered in the current chunk
        int xPos = chunkX * 16 + 8;
        int zPos = chunkZ * 16 + 8;
        
        int xSpawn = xPos + random.nextInt(CHUNK_RADIUS);
        int zSpawn = zPos + random.nextInt(CHUNK_RADIUS);
        // the get first surface y (could be leaves, trunk, water, etc)
        int ySpawn = world.getChunkFromChunkCoords(chunkX, chunkZ).getHeightValue(8, 8);
        
        BlockPos pos = new BlockPos(xSpawn, ySpawn, zSpawn);

     	boolean isGenerated = false;
     	if (chunksSinceLastDungeon > GeneralConfig.minChunksPerDungeon) {
     		// check if  the min distance between dungeons is met
     		if (lastDungeonBlockPos == null || lastDungeonBlockPos.distanceSq(pos) > (GeneralConfig.minDistancePerDungeon * GeneralConfig.minDistancePerDungeon)) {
	     		// clear count
				chunksSinceLastDungeon = 0;

				// 1. test if dungeon meets the probability criteria
				if (random.nextInt(100) > GeneralConfig.dungeonGenProbability) {
					Dungeons2.log.debug("Dungeon fail generate probability.");
					return;
				}		

				// 2. test if correct biome
				Biome biome = world.getBiome(pos);
				Dungeons2.log.debug("Current biome:" + biome.getBiomeName());

			    if (!WorldGenUtil.isBiomeAllowed(biome, biomeWhiteList, biomeBlackList)) {
			    	Dungeons2.log.debug(String.format("[%s] is not a valid biome.", biome.getBiomeName()));
			    	return;
			    }
			    
			    // 3. get the sheets - NOTE see constructor

				// 4. select random theme, pattern, size and direction
//				Dungeons2.log.debug("StyleSheet:" + styleSheet);
				Dungeons2.log.debug("Themes.size: " + styleSheet.getThemes().size());
	   			Theme theme = styleSheet.getThemes().get(styleSheet.getThemes().keySet().toArray()[random.nextInt(styleSheet.getThemes().size())]);
    			BuildPattern pattern = BuildPattern.values()[random.nextInt(BuildPattern.values().length)];
				BuildSize levelSize = BuildSize.values()[random.nextInt(BuildSize.values().length)];
				BuildSize dungeonSize = BuildSize.values()[random.nextInt(BuildSize.values().length)];
				BuildDirection direction = BuildDirection.values()[random.nextInt(BuildDirection.values().length)];
				
				// NOTE temp
				pattern = BuildPattern.SQUARE;
				levelSize = BuildSize.SMALL;
				direction = BuildDirection.CENTER;
				
				// 5. determine a preset level config based on pattern and size
				LevelConfig levelConfig = PRESET_LEVEL_CONFIGS.getConfig(pattern, levelSize, direction);
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
				Dungeon dungeon = builder.build(world, random, new Coords(pos), dungeonConfig);
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
						isGenerated = generator.generate(world, random, dungeon, styleSheet, chestSheet, spawnSheet);
					} catch (FileNotFoundException e) {
						Dungeons2.log.error("Error generating dungeon @ " + new Coords(pos).toShortString(), e);
					}
				}
				
				if (isGenerated) {
					// update the last dungeon position
					lastDungeonBlockPos = pos;
					Dungeons2.log.info("Dungeon generated @ " + new Coords(pos).toShortString());
				}
     		}
     	}
     	// save world data
		DungeonsGenSavedData savedData = DungeonsGenSavedData.get(world);
    	if (savedData != null) {
    		savedData.markDirty();
    	}
		
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
	 * @return the lastDungeonBlockPos
	 */
	public BlockPos getLastDungeonBlockPos() {
		return lastDungeonBlockPos;
	}

	/**
	 * @param lastDungeonBlockPos the lastDungeonBlockPos to set
	 */
	public void setLastDungeonBlockPos(BlockPos lastDungeonBlockPos) {
		this.lastDungeonBlockPos = lastDungeonBlockPos;
	}

}
