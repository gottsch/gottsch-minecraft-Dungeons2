/**
 * 
 */
package com.someguyssoftware.dungeons2.config;

import java.io.File;

// TODO add IModConfiguration to ModUtils library and create an Abstract class which implements initialize()
/**
 * 
 * @author Mark Gottschling on Aug 12, 2015
 *
 */
public class DungeonsConfiguration {
	private static final String DUNGEONS_CONFIG_DIR = "dungeons2";
	
	public static String configPath;
	
	/*
	 * 
	 */
	public static void initialize(File configDir) {
		// build the path to the minecraft config directory
		configPath = (new StringBuilder()).append(configDir).append("/").append(DUNGEONS_CONFIG_DIR).append("/").toString();
		
		// create the file to the general treasure config file
		File generalConfigFile = new File((new StringBuilder()).append(configPath).append("general.cfg").toString());
		// initialize the General config
		GeneralConfig.initialize(generalConfigFile);
	}
}
