/**
 * 
 */
package com.someguyssoftware.gottschcore.config;

import java.io.File;

import com.someguyssoftware.gottschcore.mod.IMod;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * @author Mark Gottschling on Apr 30, 2017
 *
 */
public class GottschCoreConfig extends AbstractConfig {
	
	/**
	 * 
	 * @param file
	 * @param modDir
	 * @param filename
	 */
	public GottschCoreConfig(IMod mod, File file, String modDir, String filename) {
		super(mod, file, modDir, filename);
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.config.IConfig#load(java.io.File)
	 */
	@Override
	public Configuration load(File file) {
		// load the config file
		Configuration config = super.load(file);
		
//		Configuration config = new Configuration(file);
//		// load the file
//        config.load();

		// add mod specific settings

        // the the default values
       if(config.hasChanged()) {
    	   config.save();
       }
       
		return config;		
	}
}
