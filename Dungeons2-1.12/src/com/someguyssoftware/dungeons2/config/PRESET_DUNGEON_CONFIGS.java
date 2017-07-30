/**
 * 
 */
package com.someguyssoftware.dungeons2.config;

import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Oct 5, 2016
 *
 */
public final class PRESET_DUNGEON_CONFIGS {
	
	/**
	 * 
	 * @param size
	 * @return
	 */
	public static DungeonConfig getConfig(BuildSize size) {
		DungeonConfig config = new DungeonConfig();
		
		switch(size) {
		case SMALL:
			config.setNumberOfLevels(new Quantity(2, 5));
			break;
		case MEDIUM:
			config.setNumberOfLevels(new Quantity(4, 7));
			break;
		case LARGE:
			config.setNumberOfLevels(new Quantity(6, 9));
			break;
		case VAST:
			config.setNumberOfLevels(new Quantity(8, 11));
			config.setSurfaceBuffer(7);
			break;
		}
		
		// NOTE useSupported is ON by default
		// check mod config
		if (!ModConfig.supportOn) config.setUseSupport(false);
		
		return config;
	}
}
