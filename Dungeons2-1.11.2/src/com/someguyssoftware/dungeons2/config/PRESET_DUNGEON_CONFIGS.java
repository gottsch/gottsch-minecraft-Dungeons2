/**
 * 
 */
package com.someguyssoftware.dungeons2.config;

import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.mod.Quantity;

/**
 * @author Mark Gottschling on Oct 5, 2016
 *
 */
public final class PRESET_DUNGEON_CONFIGS {
//	public static final DungeonConfig SMALL_SUPPORTED = new DungeonConfig();
//	public static final DungeonConfig MEDIUM_SUPPORTED = new DungeonConfig();
//	public static final DungeonConfig LARGE_SUPPORTED = new DungeonConfig();
//	public static final DungeonConfig VAST_SUPPORTED = new DungeonConfig();
	
//	static {
//		SMALL_SUPPORTED.setNumberOfLevels(new Quantity(2, 5));
//		SMALL_SUPPORTED.setSurfaceBuffer(10);
//		SMALL_SUPPORTED.setUseSupport(true);
//		SMALL_SUPPORTED.setYBottom(5);
//		
//		MEDIUM_SUPPORTED.setNumberOfLevels(new Quantity(3, 6));
//		MEDIUM_SUPPORTED.setSurfaceBuffer(8); // TODO is this used here or in Level Config ?? It's being used from LevelConfig but should be from here.
//		MEDIUM_SUPPORTED.setUseSupport(true);
//		MEDIUM_SUPPORTED.setYBottom(4);
//	}
	
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
		if (!GeneralConfig.supportOn) config.setUseSupport(false);
		
		return config;
	}
}
