/**
 * 
 */
package com.someguyssoftware.dungeons2.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * 
 * @author Mark Gottschling on Jul 27, 2016
 *
 */
@Deprecated
public class GeneralConfig {

	// Configuration file
	public static Configuration config;
	
	// logging
	public static String loggerLevel;
	public static String loggerFolder;
	public static String loggerSize;
	



	public static String dungeonsFolder;
	
	/**
	 * 
	 */
	public GeneralConfig() {
		
	}
	
	/**
	 * 
	 * @param file
	 */
	public static void initialize(File file) {
        config = new Configuration(file);
        config.load();
        
       // logging
        config.setCategoryComment("01-logging", "Logging properties.");        
        loggerLevel = config.getString("loggerLevel", "01-mod", "info", "The logging level. Set to 'off' to disable logging. [trace|debug|info|warn|error|off]");
        loggerFolder = config.getString("loggerFolder", "01-mod", "mods/dungeons2/logs/", "The directory where the logs should be stored. This is relative to the Minecraft install path.");
        loggerSize = config.getString("loggerSize", "01-mod", "1000K", "The size a log file can be before rolling over to a new file.");
        
        // resources
        config.setCategoryComment("02-resources", "Resource properties.");   
        dungeonsFolder = config.getString("dungeonsFolder", "02-resources", "mods/dungeons2/", "Where default Dungeons2 folder is located.");
  
        // mob spawn rates
        config.setCategoryComment("05-mobs", "Dungeon mob properties.");
//        minZombieSpawnRate = config.getInt("minZombieSpawnRate", "05-mobs", 2, 1, 25, "");
//        maxZombieSpawnRate = config.getInt("minZombieSpawnRate", "05-mobs", 5, 1, 25, "");
//        minSkeletonSpawnRate = config.getInt("minSkeletonSpawnRate", "05-mobs", 1, 1, 25, "");
//        maxSkeletonSpawnRate = config.getInt("minSkeletonSpawnRate", "05-mobs", 4, 1, 25, "");
//        minSpiderSpawnRate = config.getInt("minSpiderSpawnRate", "05-mobs", 2, 1, 25, "");
//        maxSpiderSpawnRate = config.getInt("minSpiderSpawnRate", "05-mobs", 4, 1, 25, "");
//        minCaveSpiderSpawnRate = config.getInt("minCaveSpiderSpawnRate", "05-mobs", 1, 1, 25, "");
//        maxCaveSpiderSpawnRate = config.getInt("minCaveSpiderSpawnRate", "05-mobs", 3, 1, 25, "");
//        minWitchSpawnRate = config.getInt("minWitchSpawnRate", "05-mobs", 1, 1, 25, "");
//        maxWitchSpawnRate = config.getInt("minWitchSpawnRate", "05-mobs", 2, 1, 25, "");
//        minCreeperSpawnRate = config.getInt("minCreeperSpawnRate", "05-mobs", 1, 1, 25, "");
//        maxCreeperSpawnRate = config.getInt("minCreeperSpawnRate", "05-mobs", 3, 1, 25, "");
        
        // ids
        config.setCategoryComment("99-ids", "ID properties.");


        // the the default values
       if(config.hasChanged()) {
    	   config.save();
       }
	}
}
