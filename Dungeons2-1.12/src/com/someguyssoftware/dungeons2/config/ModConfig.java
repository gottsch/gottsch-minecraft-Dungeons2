package com.someguyssoftware.dungeons2.config;

import java.io.File;

import com.someguyssoftware.gottschcore.config.AbstractConfig;
import com.someguyssoftware.gottschcore.mod.IMod;

import net.minecraftforge.common.config.Configuration;

/**
 * 
 * @author Mark Gottschling on Jul 29, 2017
 *
 */
public class ModConfig extends AbstractConfig {
	// resources
	@Deprecated
	public static String styleSheetFile;
	@Deprecated
	public static String chestSheetFile;
	@Deprecated
	public static String spawnSheetFile;
	
	// enablements
	public static Boolean enableDungeons;
	public static Boolean enableSpawners;
	public static Boolean enableChests;
	
	// props
	public static int minChunksPerDungeon;
	public static int minDistancePerDungeon;
	public static int dungeonGenProbability;
	
	// biome type white/black lists
	public static String[] generalDungeonBiomeWhiteList;
	public static String[] generalDungeonBiomeBlackList;
	
	// use support algorithm for structures (true = more decayed/broken look, false = entire building is intact)
	public static boolean supportOn ;
	
	// level builder settings
	public static int decayMultiplier;
	
	// ids
	public static String tabId;
	public static String oneTimeSpawnerBlockId;
	public static String oneTimeSpawnerTileEntityId;

	// basic facade
	public static String basicStoneFacadeId;
	public static String basicCobblestoneFacadeId;
	public static String basicMossyCobblestoneFacadeId;
	public static String basicStonebrickFacadeId;
	public static String basicMossyStonebrickFacadeId;
	public static String basicCrackedStonebrickFacadeId;
	public static String basicChiseledStonebrickFacadeId;
	public static String basicObsidianbrickFacadeId;
		
	// sills
	public static String sillStoneBlockId;
	public static String sillCobblestoneBlockId;
	public static String sillStonebrickBlockId;
	public static String sillMossyCobblestoneBlockId;
	public static String sillMossyStonebrickBlockId;
	public static String sillCrackedStonebrickBlockId;
	public static String sillObsidianbrickBlockId;

	// double sills
	public static String doubleSillStoneBlockId;
	public static String doubleSillCobblestoneBlockId;
	public static String doubleSillMossyCobblestoneBlockId;
	public static String doubleSillStonebrickBlockId;
	public static String doubleSillMossyStonebrickBlockId;
	public static String doubleSillCrackedStonebrickBlockId;
	public static String doubleSillObsidianbrickBlockId;
	
	// half pillar bases
	public static String halfPillarBaseStoneBlockId;
	public static String halfPillarBaseCobblestoneBlockId;
	public static String halfPillarBaseMossyCobblestoneBlockId;
	public static String halfPillarBaseStonebrickBlockId;
	public static String halfPillarBaseMossyStonebrickBlockId;
	public static String halfPillarBaseCrackedStonebrickBlockId;
	public static String halfPillarBaseObsidianbrickBlockId;
	
	// half-pillar
	public static String halfPillarStoneBlockId;
	public static String halfPillarCobblestoneBlockId;
	public static String halfPillarMossyCobblestoneBlockId;
	public static String halfPillarStonebrickBlockId;
	public static String halfPillarMossyStonebrickBlockId;
	public static String halfPillarCrackedStonebrickBlockId;
	public static String halfPillarObsidianbrickBlockId;
	
	
	// cornices
	public static String corniceStoneBlockId;
	public static String corniceCobblestoneBlockId;
	public static String corniceMossyCobblestoneBlockId;
	public static String corniceStonebrickBlockId;
	public static String corniceMossyStonebrickBlockId;
	public static String corniceCrackedStonebrickBlockId;
	public static String corniceObsidianbrickBlockId;
	
	// crown molding
	public static String crownMoldingStoneBlockId;
	public static String crownMoldingCobblestoneBlockId;
	public static String crownMoldingMossyCobblestoneBlockId;
	public static String crownMoldingStonebrickBlockId;
	public static String crownMoldingMossyStonebrickBlockId;
	public static String crownMoldingCrackedStonebrickBlockId;
	public static String crownMoldingObsidianbrickBlockId;
	
	// "T" pillar facades
	public static String teePillarStoneBlockId;
	public static String teePillarCobblestoneBlockId;
	public static String teePillarMossyCobblestoneBlockId;
	public static String teePillarStonebrickBlockId;
	public static String teePillarMossyStonebrickBlockId;
	public static String teePillarCrackedStonebrickBlockId;
	public static String teePillarObsidianbrickBlockId;
	
	// thin "T" pillar facades
	public static String teeThinPillarStoneBlockId;
	public static String teeThinPillarCobblestoneBlockId;
	public static String teeThinPillarMossyCobblestoneBlockId;
	public static String teeThinPillarStonebrickBlockId;
	public static String teeThinPillarMossyStonebrickBlockId;
	public static String teeThinPillarCrackedStonebrickBlockId;
	public static String teeThinPillarObsidianbrickBlockId;
	
	// coffer middle
	public static String cofferMiddleStoneBlockId;
	public static String cofferMiddleCobblestoneBlockId;
	public static String cofferMiddleMossyCobblestoneBlockId;
	public static String cofferMiddleStonebrickBlockId;
	public static String cofferMiddleMossyStonebrickBlockId;
	public static String cofferMiddleCrackedStonebrickBlockId;
	public static String cofferMiddleObsidianbrickBlockId;
	
	// coffer straight
	public static String cofferStoneBlockId;
	public static String cofferCobblestoneBlockId;
	public static String cofferMossyCobblestoneBlockId;
	public static String cofferStonebrickBlockId;
	public static String cofferMossyStonebrickBlockId;
	public static String cofferCrackedStonebrickBlockId;
	public static String cofferObsidianbrickBlockId;
	
	// seven eights
	public static String sevenEightsPillarStoneBlockId;
	public static String sevenEightsPillarCobblestoneBlockId;
	public static String sevenEightsPillarMossyCobblestoneBlockId;
	public static String sevenEightsPillarStonebrickBlockId;
	public static String sevenEightsPillarMossyStonebrickBlockId;
	public static String sevenEightsPillarCrackedStonebrickBlockId;
	public static String sevenEightsPillarObsidianbrickBlockId;
	
	// fluted pillars
	public static String flutePillarStoneBlockId;
	public static String flutePillarCobblestoneBlockId;
	public static String flutePillarMossyCobblestoneBlockId;
	public static String flutePillarStonebrickBlockId;
	public static String flutePillarMossyStonebrickBlockId;
	public static String flutePillarCrackedStonebrickBlockId;
	public static String flutePillarObsidianbrickBlockId;
	
	// thin fluted pillar facades
	public static String fluteThinPillarStoneBlockId;
	public static String fluteThinPillarCobblestoneBlockId;
	public static String fluteThinPillarMossyCobblestoneBlockId;
	public static String fluteThinPillarStonebrickBlockId;
	public static String fluteThinPillarMossyStonebrickBlockId;
	public static String fluteThinPillarCrackedStonebrickBlockId;
	public static String fluteThinPillarObsidianbrickBlockId;
	
	// wall sconce
	public static String wallSconceStoneBlockId;
	public static String wallSconceCobblestoneBlockId;
	public static String wallSconceMossyCobblestoneBlockId;
	public static String wallSconceStonebrickBlockId;
	public static String wallSconceMossyStonebrickBlockId;
	public static String wallSconceCrackedStonebrickBlockId;
	public static String wallSconceObsidianbrickBlockId;
	
	// grate
	public static String grateBlockId;
	
	// fancy cube block
	public static String fancyCubeStoneBlockId;
	public static String fancyCubeCobblestoneBlockId;
	public static String fancyCubeMossyCobblestoneBlockId;
	public static String fancyCubeStonebrickBlockId;
	public static String fancyCubeMossyStonebrickBlockId;
	public static String fancyCubeCrackedStonebrickBlockId;
	public static String fancyCubeObsidianbrickBlockId;

	public static String mossBlockId;
	public static String moss2BlockId;
	public static String lichenBlockId;
	public static String lichen2BlockId;
	public static String puddleBlockId;
	public static String bloodBlockId;
	public static String mold1BlockId;

	/**
	 * 
	 * @param mod
	 * @param configDir
	 * @param modDir
	 * @param filename
	 */
	public ModConfig(IMod mod, File configDir, String modDir, String filename) {
		super(mod, configDir, modDir, filename);
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.config.IConfig#load(java.io.File)
	 */
	@Override
	public Configuration load(File file) {
		// load the config file
		Configuration config = super.load(file);

        // resources
        config.setCategoryComment("02-resources", "Resource properties.");   
//        dungeonsFolder = config.getString("dungeonsFolder", "02-resources", "mods/dungeons2/", "Where default Dungeons2 folder is located.");
        styleSheetFile = config.getString("styleSheetFile", "02-resources", "mods/dungeons2/styleSheet.json", "@Deprecated\nWhere the style sheet file is located.");
        chestSheetFile = config.getString("chestSheetFile", "02-resources", "mods/dungeons2/chestSheet.json", "@Deprecate\nWhere the chest sheet file is located.");
        spawnSheetFile = config.getString("spawnSheetFile", "02-resources", "mods/dungeons2/spawnSheet.json", "@Deprecated\nWhere the spawn sheet file is located.");

        // enable dungeons settings
        config.setCategoryComment("03-dungeons2", "General Dungeons2! mod properties.");        
        enableDungeons = config.getBoolean("enableDungeons", "03-dungeons2", true, "Enable/disable the generation of dungeons.");
        
        // dungeon generation setttings
        config.setCategoryComment("04-dungeon-gen", "Dungeon generation properties.");   
        enableSpawners = config.getBoolean("enableVanillaSpawners", "04-dungeon-gen", true, "Enable/disable the generation of any type of spawners in dungeons.");
        enableChests = config.getBoolean("enableChests", "04-dungeon-gen", true, "Enable/disable the generation of chests in dungeons.");
        
        // props
        minChunksPerDungeon = config.getInt("minChunksPerDungeon", "04-dungeon-gen", 150, 50, 32000, ""); // 300
        minDistancePerDungeon = config.getInt("minDistancePerDungeon", "04-dungeon-gen", 600, 0, 32000, "Minimum block distance between 2 consecutive dungeon spawns.");
        dungeonGenProbability = config.getInt("dungeonGenProbability", "04-dungeon-gen", 45, 0, 100, "");
        
        // white/black lists
        generalDungeonBiomeWhiteList = config.getStringList("generalDungeonBiomeWhiteList", "04-dungeon-gen", new String[]{}, "Allowable Biome Types for general Dungeons. Must match the Type identifer(s).");
        generalDungeonBiomeBlackList = config.getStringList("generalDungeonBiomeBlackList", "04-dungeon-gen", new String[]{"ocean"}, "Disallowable Biome Types for general Dungeons. Must match the Type identifer(s).");
        
        // structures
        supportOn = config.getBoolean("supportOn", "04-dungeon-gen", true, "Enable/disable the support algorithm.");

        // level builder settings
        decayMultiplier = config.getInt("decayMultiplier", "04-dungeon-gen", 5, 1, 10, "Number of times a block is checked to see if it meets the decay criteria.\nHigher number means more likely to be in a higher decayed state.");
     
        // ids
        config.setCategoryComment("99-ids", "ID properties.");
        tabId = config.getString("tabsId", "99-ids", "dungeons_tab", "");
        oneTimeSpawnerBlockId = config.getString("oneTimeSpawnerBlockId", "99-ids", "one_time_spawner_block", "");        
        oneTimeSpawnerTileEntityId = config.getString("oneTimeSpawnerTileEntityId", "99-ids", "one_time_spawner_tile_entity", "");
        
        // basic facades
        basicStoneFacadeId = config.getString("basicStoneFacadeId", "99-ids", "basic_facade_stone", "");
        basicCobblestoneFacadeId = config.getString("basicCobblestoneFacadeId", "99-ids", "basic_facade_cobblestone", "");
        basicMossyCobblestoneFacadeId = config.getString("basicMossyCobblestoneFacadeId", "99-ids", "basic_facade_cobblestone_mossy", "");
        basicStonebrickFacadeId = config.getString("basicStonebrickFacadeId", "99-ids", "basic_facade_stonebrick", "");
        basicMossyStonebrickFacadeId = config.getString("basicMossyStonebrickFacadeId", "99-ids", "basic_facade_stonebrick_mossy", "");
        basicCrackedStonebrickFacadeId = config.getString("basicCrackedStonebrickFacadeId", "99-ids", "basic_facade_stonebrick_cracked", "");
        basicChiseledStonebrickFacadeId = config.getString("basicChiseledStonebrickFacadeId", "99-ids", "basic_facade_stonebrick_chiseled", "");
        basicObsidianbrickFacadeId = config.getString("basicObsidianbrickFacadeId", "99-ids", "basic_facade_obsidianbrick", "");
                
        // "T" pillar facades
        teePillarStoneBlockId = config.getString("teePillarStoneBlockId", "99-ids", "tee_pillar_stone", "");
        teePillarCobblestoneBlockId = config.getString("teePillarCobblestoneBlockId", "99-ids", "tee_pillar_cobblestone", "");
        teePillarMossyCobblestoneBlockId = config.getString("teePillarMossyCobblestoneBlockId", "99-ids", "tee_pillar_cobblestone_mossy", "");
        teePillarStonebrickBlockId = config.getString("teePillarStonebrickBlockId", "99-ids", "tee_pillar_stonebrick", "");
        teePillarMossyStonebrickBlockId = config.getString("teePillarMossyStonebrickBlockId", "99-ids", "tee_pillar_stonebrick_mossy", "");
        teePillarCrackedStonebrickBlockId = config.getString("teePillarCrackedStonebrickBlockId", "99-ids", "tee_pillar_stonebrick_cracked", "");
        teePillarObsidianbrickBlockId = config.getString("teePillarObsidianbrickBlockId", "99-ids", "tee_pillar_obsidianbrick", "");
        
        // thin "T" pillar facades
        teeThinPillarStoneBlockId = config.getString("teeThinPillarStoneBlockId", "99-ids", "tee_thin_pillar_stone", "");
        teeThinPillarCobblestoneBlockId = config.getString("teeThinPillarCobblestoneBlockId", "99-ids", "tee_thin_pillar_cobblestone", "");
        teeThinPillarMossyCobblestoneBlockId = config.getString("teeThinPillarMossyCobblestoneBlockId", "99-ids", "tee_thin_pillar_cobblestone_mossy", "");
        teeThinPillarStonebrickBlockId = config.getString("teeThinPillarStonebrickBlockId", "99-ids", "tee_thin_pillar_stonebrick", "");
        teeThinPillarMossyStonebrickBlockId = config.getString("teeThinPillarMossyStonebrickBlockId", "99-ids", "tee_thin_pillar_stonebrick_mossy", "");
        teeThinPillarCrackedStonebrickBlockId = config.getString("teeThinPillarCrackedStonebrickBlockId", "99-ids", "tee_thin_pillar_stonebrick_cracked", "");
        teeThinPillarObsidianbrickBlockId = config.getString("teeThinPillarObsidianbrickBlockId", "99-ids", "tee_thin_pillar_obsidianbrick", "");
        
        // fluted pillars
        flutePillarStoneBlockId = config.getString("flutePillarStoneBlockId", "99-ids", "flute_pillar_stone", "");
        flutePillarCobblestoneBlockId = config.getString("flutePillarCobblestoneBlockId", "99-ids", "flute_pillar_cobblestone", "");
        flutePillarMossyCobblestoneBlockId = config.getString("flutePillarMossyCobblestoneBlockId", "99-ids", "flute_pillar_cobblestone_mossy", "");
        flutePillarStonebrickBlockId = config.getString("flutePillarStonebrickBlockId", "99-ids", "flute_pillar_stonebrick", "");
        flutePillarMossyStonebrickBlockId = config.getString("flutePillarMossyStonebrickBlockId", "99-ids", "flute_pillar_stonebrick_mossy", "");
        flutePillarCrackedStonebrickBlockId = config.getString("flutePillarCrackedStonebrickBlockId", "99-ids", "flute_pillar_stonebrick_cracked", "");
        flutePillarObsidianbrickBlockId = config.getString("flutePillarObsidianbrickBlockId", "99-ids", "flute_pillar_obsidianbrick", "");
        
        // thin fluted pillars
        fluteThinPillarStoneBlockId = config.getString("fluteThinPillarStoneBlockId", "99-ids", "flute_thin_pillar_stone", "");
        fluteThinPillarCobblestoneBlockId = config.getString("fluteThinPillarCobblestoneBlockId", "99-ids", "flute_thin_pillar_cobblestone", "");
        fluteThinPillarMossyCobblestoneBlockId = config.getString("fluteThinPillarMossyCobblestoneBlockId", "99-ids", "flute_thin_pillar_cobblestone_mossy", "");
        fluteThinPillarStonebrickBlockId = config.getString("fluteThinPillarStonebrickBlockId", "99-ids", "flute_thin_pillar_stonebrick", "");
        fluteThinPillarMossyStonebrickBlockId = config.getString("fluteThinPillarMossyStonebrickBlockId", "99-ids", "flute_thin_pillar_stonebrick_mossy", "");
        fluteThinPillarCrackedStonebrickBlockId = config.getString("fluteThinPillarCrackedStonebrickBlockId", "99-ids", "flute_thin_pillar_stonebrick_cracked", "");
        fluteThinPillarObsidianbrickBlockId = config.getString("fluteThinPillarObsidianbrickBlockId", "99-ids", "flute_thin_pillar_obsidianbrick", "");
        
        // cornices
        corniceStoneBlockId = config.getString("corniceStoneBlockId", "99-ids", "cornice_stone", "");
        corniceCobblestoneBlockId = config.getString("corniceCobblestoneBlockId", "99-ids", "cornice_cobblestone", "");
        corniceMossyCobblestoneBlockId = config.getString("corniceMossyCobblestoneBlockId", "99-ids", "cornice_cobblestone_mossy", "");
        corniceStonebrickBlockId = config.getString("corniceStonebrickBlockId", "99-ids", "cornice_stonebrick", "");
        corniceMossyStonebrickBlockId = config.getString("corniceMossyStonebrickBlockId", "99-ids", "cornice_stonebrick_mossy", "");
        corniceCrackedStonebrickBlockId = config.getString("corniceCrackedStonebrickBlockId", "99-ids", "cornice_stonebrick_cracked", "");
        corniceObsidianbrickBlockId = config.getString("corniceObsidianbrickBlockId", "99-ids", "cornice_obsidianbrick", "");

        // seven-eights pillars
        sevenEightsPillarStoneBlockId = config.getString("sevenEightsPillarStoneBlockId", "99-ids", "seven_eights_pillar_stone", "");
        sevenEightsPillarCobblestoneBlockId = config.getString("sevenEightsPillarCobblestoneBlockId", "99-ids", "seven_eights_pillar_cobblestone", "");
        sevenEightsPillarMossyCobblestoneBlockId = config.getString("sevenEightsPillarMossyCobblestoneBlockId", "99-ids", "seven_eights_pillar_cobblestone_mossy", "");
        sevenEightsPillarStonebrickBlockId = config.getString("sevenEightsPillarStonebrickBlockId", "99-ids", "seven_eights_pillar_stonebrick", "");
        sevenEightsPillarMossyStonebrickBlockId = config.getString("sevenEightsPillarMossyStonebrickBlockId", "99-ids", "seven_eights_pillar_stonebrick_mossy", "");
        sevenEightsPillarCrackedStonebrickBlockId = config.getString("sevenEightsPillarCrackedStonebrickBlockId", "99-ids", "seven_eights_pillar_stonebrick_cracked", "");
        sevenEightsPillarObsidianbrickBlockId = config.getString("sevenEightsPillarObsidianbrickBlockId", "99-ids", "seven_eights_pillar_obsidianbrick", "");
        
        // sills
        sillStoneBlockId = config.getString("sillStoneBlockId", "99-ids", "sill_stone", "");
        sillCobblestoneBlockId = config.getString("sillCobblestoneBlockId", "99-ids", "sill_cobblestone", "");
        sillMossyCobblestoneBlockId = config.getString("sillMossyCobblestoneBlockId", "99-ids", "sill_cobblestone_mossy", "");
        sillStonebrickBlockId = config.getString("sillStonebrickBlockId", "99-ids", "sill_stonebrick", "");
        sillMossyStonebrickBlockId = config.getString("sillMossyStonebrickBlockId", "99-ids", "sill_stonebrick_mossy", "");
        sillCrackedStonebrickBlockId = config.getString("sillCrackedStonebrickBlockId", "99-ids", "sill_stonebrick_cracked", "");
        sillObsidianbrickBlockId = config.getString("sillObsidianbrickBlockId", "99-ids", "sill_obsidianbrick", "");
        
        // double sills
        doubleSillStoneBlockId = config.getString("doubleSillStoneBlockId", "99-ids", "double_sill_stone", "");
        doubleSillCobblestoneBlockId = config.getString("doubleSillCobblestoneBlockId", "99-ids", "double_sill_cobblestone", "");
        doubleSillMossyCobblestoneBlockId = config.getString("doubleSillMossyCobblestoneBlockId", "99-ids", "double_sill_cobblestone_mossy", "");
        doubleSillStonebrickBlockId = config.getString("doubleSillStonebrickBlockId", "99-ids", "double_sill_stonebrick", "");
        doubleSillMossyStonebrickBlockId = config.getString("doubleSillMossyStonebrickBlockId", "99-ids", "double_sill_stonebrick_mossy", "");
        doubleSillCrackedStonebrickBlockId = config.getString("doubleSillCrackedStonebrickBlockId", "99-ids", "double_sill_stonebrick_cracked", "");
        doubleSillObsidianbrickBlockId = config.getString("doubleObsidianStonebrickBlockId", "99-ids", "double_sill_obsidianbrick", "");

        // half pillar bases
        halfPillarBaseStoneBlockId = config.getString("halfPillarBaseStoneBlockId", "99-ids", "half_pillar_base_stone", "");
        halfPillarBaseCobblestoneBlockId = config.getString("halfPillarBaseCobblestoneBlockId", "99-ids", "half_pillar_base_cobblestone", "");
        halfPillarBaseMossyCobblestoneBlockId = config.getString("halfPillarBaseMossyCobblestoneBlockId", "99-ids", "half_pillar_base_cobblestone_mossy", "");
        halfPillarBaseStonebrickBlockId = config.getString("halfPillarBaseStonebrickBlockId", "99-ids", "half_pillar_base_stonebrick", "");
        halfPillarBaseMossyStonebrickBlockId = config.getString("halfPillarBaseMossyStonebrickBlockId", "99-ids", "half_pillar_base_stonebrick_mossy", "");
        halfPillarBaseCrackedStonebrickBlockId = config.getString("halfPillarBaseCrackedStonebrickBlockId", "99-ids", "half_pillar_base_stonebrick_cracked", "");
        halfPillarBaseObsidianbrickBlockId = config.getString("halfPillarBaseObsidianbrickBlockId", "99-ids", "half_pillar_base_obsidianbrick", "");
       
        // half pillars
        halfPillarStoneBlockId = config.getString("halfPillarStoneId", "99-ids", "half_pillar_stone", "");
        halfPillarCobblestoneBlockId = config.getString("halfPillarCobblestoneBlockId", "99-ids", "half_pillar_cobblestone", "");
        halfPillarMossyCobblestoneBlockId = config.getString("halfPillarMossyCobblestoneBlockId", "99-ids", "half_pillar_cobblestone_mossy", "");
        halfPillarStonebrickBlockId = config.getString("halfPillarStonebrickBlockId", "99-ids", "half_pillar_stonebrick", "");
        halfPillarMossyStonebrickBlockId = config.getString("halfPillarMossyStonebrickBlockId", "99-ids", "half_pillar_stonebrick_mossy", "");
        halfPillarCrackedStonebrickBlockId = config.getString("halfPillarCrackedStonebrickBlockId", "99-ids", "half_pillar_stonebrick_cracked", "");
        halfPillarObsidianbrickBlockId = config.getString("halfPillarObsidianbrickBlockId", "99-ids", "half_pillar_obsidianbrick", "");

        // coffer middle
        cofferMiddleStoneBlockId = config.getString("cofferMiddleStoneBlockId", "99-ids", "coffer_middle_stone", "");
        cofferMiddleCobblestoneBlockId = config.getString("cofferMiddleCobblestoneBlockId", "99-ids", "coffer_middle_cobblestone", "");
        cofferMiddleMossyCobblestoneBlockId = config.getString("cofferMiddleMossyCobblestoneBlockId", "99-ids", "coffer_middle_cobblestone_mossy", "");
        cofferMiddleStonebrickBlockId = config.getString("cofferMiddleStonebrickBlockId", "99-ids", "coffer_middle_stonebrick", "");
        cofferMiddleMossyStonebrickBlockId = config.getString("cofferMiddleMossyStonebrickBlockId", "99-ids", "coffer_middle_stonebrick_mossy", "");
        cofferMiddleCrackedStonebrickBlockId = config.getString("cofferMiddleCrackedStonebrickBlockId", "99-ids", "coffer_middle_stonebrick_cracked", "");
        cofferMiddleObsidianbrickBlockId = config.getString("cofferMiddleObsidianbrickBlockId", "99-ids", "coffer_middle_obsidianbrick", "");
        
        // coffer 
        cofferStoneBlockId = config.getString("cofferStoneBlockId", "99-ids", "coffer_stone", "");
        cofferCobblestoneBlockId = config.getString("cofferCobblestoneBlockId", "99-ids", "coffer_cobblestone", "");
        cofferMossyCobblestoneBlockId = config.getString("cofferMossyCobblestoneBlockId", "99-ids", "coffer_cobblestone_mossy", "");
        cofferStonebrickBlockId = config.getString("cofferStonebrickBlockId", "99-ids", "coffer_stonebrick", "");
        cofferMossyStonebrickBlockId = config.getString("cofferMossyStonebrickBlockId", "99-ids", "coffer_stonebrick_mossy", "");
        cofferCrackedStonebrickBlockId = config.getString("cofferCrackedStonebrickBlockId", "99-ids", "coffer_stonebrick_cracked", "");
        cofferObsidianbrickBlockId = config.getString("cofferObsidianbrickBlockId", "99-ids", "coffer_obsidianbrick", "");
        
        // crown molding
        crownMoldingStoneBlockId = config.getString("crownMoldingStoneBlockId", "99-ids", "crown_molding_stone", "");
        crownMoldingCobblestoneBlockId = config.getString("crownMoldingCobblestoneBlockId", "99-ids", "crown_molding_cobblestone", "");
        crownMoldingMossyCobblestoneBlockId = config.getString("crownMoldingMossyCobblestoneBlockId", "99-ids", "crown_molding_cobblestone_mossy", "");
        crownMoldingStonebrickBlockId = config.getString("crownMoldingStonebrickBlockId", "99-ids", "crown_molding_stonebrick", "");
        crownMoldingMossyStonebrickBlockId = config.getString("crownMoldingMossyStonebrickBlockId", "99-ids", "crown_molding_stonebrick_mossy", "");
        crownMoldingCrackedStonebrickBlockId = config.getString("crownMoldingCrackedStonebrickBlockId", "99-ids", "crown_molding_stonebrick_cracked", "");
        crownMoldingObsidianbrickBlockId = config.getString("crownMoldingObsidianbrickBlockId", "99-ids", "crown_molding_obsidianbrick", "");
        
        // wall sconce
        wallSconceStoneBlockId = config.getString("wallSconceStoneBlockId", "99-ids", "wall_sconce_stone", "");
        wallSconceCobblestoneBlockId = config.getString("wallSconceCobblestoneBlockId", "99-ids", "wall_sconce_cobblestone", "");
        wallSconceMossyCobblestoneBlockId = config.getString("wallSconceMossyCobblestoneBlockId", "99-ids", "wall_sconce_cobblestone_mossy", "");
        wallSconceStonebrickBlockId = config.getString("wallSconceStonebrickBlockId", "99-ids", "wall_sconce_stonebrick", "");
        wallSconceMossyStonebrickBlockId = config.getString("wallSconceMossyStonebrickBlockId", "99-ids", "wall_sconce_stonebrick_mossy", "");
        wallSconceCrackedStonebrickBlockId = config.getString("wallSconceCrackedStonebrickBlockId", "99-ids", "wall_sconce_stonebrick_cracked", "");
        wallSconceObsidianbrickBlockId = config.getString("wallSconceObsidianbrickBlockId", "99-ids", "wall_sconce_obsidianbrick", "");

        // grate
        grateBlockId = config.getString("grateBlockId", "99-ids", "grate", "");

        // fancy cube
        fancyCubeStoneBlockId = config.getString("fancyCubeStoneBlockId", "99-ids", "fancy_cube_stone", "");
        fancyCubeCobblestoneBlockId = config.getString("fancyCubeCobblestoneBlockId", "99-ids", "fancy_cube_cobblestone", "");
        fancyCubeMossyCobblestoneBlockId = config.getString("fancyCubeMossyCobblestoneBlockId", "99-ids", "fancy_cube_cobblestone_mossy", "");
        fancyCubeStonebrickBlockId = config.getString("fancyCubeStonebrickBlockId", "99-ids", "fancy_cube_stonebrick", "");
        fancyCubeMossyStonebrickBlockId = config.getString("fancyCubeMossyStonebrickBlockId", "99-ids", "fancy_cube_stonebrick_mossy", "");
        fancyCubeCrackedStonebrickBlockId = config.getString("fancyCubeCrackedStonebrickBlockId", "99-ids", "fancy_cube_stonebrick_cracked", "");
        fancyCubeObsidianbrickBlockId = config.getString("fancyCubeObsidianbrickBlockId", "99-ids", "fancy_cube_obsidianbrick", "");
        
        // decorations
        mossBlockId = config.getString("mossBlockId", "99-ids", "moss", "");
        moss2BlockId = config.getString("moss2BlockId", "99-ids", "moss2", "");
        lichenBlockId = config.getString("lichenBlockId", "99-ids", "lichen", "");
        lichen2BlockId = config.getString("lichen2BlockId", "99-ids", "lichen2", "");
        puddleBlockId = config.getString("puddleBlockId", "99-ids", "PUDDLE", "");
        bloodBlockId = config.getString("bloodBlockId", "99-ids", "blood", "");
        mold1BlockId = config.getString("mold1BlockId", "99-ids", "mold1", "");
        ///////////////////////////////////////
        
        // the the default values
       if(config.hasChanged()) {
    	   config.save();
       }
       
		return config;		
	}
}
