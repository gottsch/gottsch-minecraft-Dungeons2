/**
 * 
 */
package com.someguyssoftware.dungeons2.config;

import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.mod.Quantity;

/**
 * @author Mark Gottschling on Oct 5, 2016
 *
 */
public final class PRESET_LEVEL_CONFIGS {
//	
//
//	// ORIGINAL PRESETS: SQUARE, HORZ/VERT RECTANGLES
//	public static final LevelConfig SMALL_CENTERED_SQUARE = new LevelConfig();
//	public static final LevelConfig SMALL_CENTERED_HORZ = new LevelConfig();
//	public static final LevelConfig SMALL_CENTERED_VERT = new LevelConfig();	
//	
//	// NOTE not final but also private so only way to access is via getConfig()
//	private static LevelConfig SMALL_WEST_SQUARE ;
//	private static LevelConfig SMALL_EAST_SQUARE;
//	private static LevelConfig SMALL_NORTH_SQUARE;
//	private static LevelConfig SMALL_SOUTH_SQUARE;
	
//	static {		
//		init();		
//	}
	
	/**
	 * 
	 */
	private static void init() {
//		final int surfaceBuffer = 10;
//		final Quantity smallNumRooms = new Quantity(10.0, 20.0);
//		final Quantity smallCenteredSquareDist = new Quantity(-25, 25);
//		
//		SMALL_CENTERED_SQUARE.setNumberOfRooms(smallNumRooms);
//		SMALL_CENTERED_SQUARE.setXDistance(smallCenteredSquareDist);
//		SMALL_CENTERED_SQUARE.setZDistance(smallCenteredSquareDist);
//		SMALL_CENTERED_SQUARE.setSurfaceBuffer(surfaceBuffer);		
//		
//		SMALL_CENTERED_HORZ.setNumberOfRooms(smallNumRooms);
//		SMALL_CENTERED_HORZ.setXDistance(new Quantity(-35, 35));
//		SMALL_CENTERED_HORZ.setZDistance(new Quantity(-15, 15));
//		SMALL_CENTERED_HORZ.setSurfaceBuffer(surfaceBuffer);
//		
//		SMALL_CENTERED_VERT.setNumberOfRooms(smallNumRooms);
//		SMALL_CENTERED_VERT.setXDistance(new Quantity(-15, 15));
//		SMALL_CENTERED_VERT.setZDistance(new Quantity(-35, 35));
//		SMALL_CENTERED_VERT.setSurfaceBuffer(surfaceBuffer);
//		
//		SMALL_NORTH_SQUARE = new LevelConfig(SMALL_CENTERED_SQUARE);
//		SMALL_NORTH_SQUARE.setZDistance(new Quantity(0, 50));
//		
//		SMALL_SOUTH_SQUARE = new LevelConfig(SMALL_CENTERED_SQUARE);
//		SMALL_SOUTH_SQUARE.setZDistance(new Quantity(-50, 0));
//		
//		SMALL_EAST_SQUARE = new LevelConfig(SMALL_CENTERED_SQUARE);
//		SMALL_EAST_SQUARE.setXDistance(new Quantity(-50, 0));
//		
//		SMALL_WEST_SQUARE = new LevelConfig(SMALL_CENTERED_SQUARE);
//		SMALL_WEST_SQUARE.setXDistance(new Quantity(0, 50));
		

	}
	
	/**
	 * 
	 * @param pattern
	 * @param size
	 * @return
	 */
	public static LevelConfig getConfig(BuildPattern pattern, BuildSize size, BuildDirection direction) {
		int xdist = 0;
		int zdist = 0;
		Quantity xDistQuantity = null;
		Quantity zDistQuantity = null;
		Quantity numRooms = null;
		
		LevelConfig config = new LevelConfig();
		
		// apply mod config settings
		config.setDecayMultiplier(GeneralConfig.decayMultiplier);
		
		// select the room size
		switch(size) {
		case SMALL:
			numRooms = new Quantity(10.0, 20.0);
			break;
		case MEDIUM:
			numRooms = new Quantity(15.0, 30.0);
			break;
		case LARGE:
			numRooms = new Quantity(20.0, 40.0);
			break;
		case VAST:
			numRooms = new Quantity(25.0, 50.0);
			break;
		}	
		
		// select the pattern
		switch(pattern) {
		case SQUARE:
			// select the x,z size
			switch(size) {
			case SMALL:
				xdist = zdist = 25;
				break;
			case MEDIUM:
				xdist = zdist = 35;
				break;
			case LARGE:
				xdist = zdist = 45;
				break;
			case VAST:
				xdist = zdist = 55;
				break;
			}			

			break;
		case HORZ:
			// select the x,z size
			switch(size) {
			case SMALL:
				xdist = 35;
				zdist = 15;
				break;
			case MEDIUM:
				xdist = 50;
				zdist = 20;
				break;
			case LARGE:
				xdist = 65;
				zdist = 25;
				break;
			case VAST:
				xdist = 80;
				zdist = 30;
				break;
			}	
			break;
		case VERT:
			// select the x,z size
			switch(size) {
			case SMALL:
				xdist = 15;
				zdist = 35;
				break;
			case MEDIUM:
				xdist = 20;
				zdist = 50;
				break;
			case LARGE:
				xdist = 25;
				zdist = 65;
				break;
			case VAST:
				xdist = 30;
				zdist = 80;
				break;
			}	
			break;
		}
		
		switch(direction) {
		case CENTER:
			xDistQuantity = new Quantity(-xdist, xdist);
			zDistQuantity = new Quantity(-zdist, zdist);
			break;
		case NORTH:
			xDistQuantity = new Quantity(-xdist, xdist);
			zDistQuantity = new Quantity(0, 2*zdist);
			break;
		case SOUTH:
			xDistQuantity = new Quantity(-xdist, xdist);
			zDistQuantity = new Quantity(-(2*zdist), 0);
			break;
		case EAST:
			xDistQuantity = new Quantity(-(2*xdist), 0);
			zDistQuantity = new Quantity(-zdist, zdist);
			break;
		case WEST:
			xDistQuantity = new Quantity(0, 2*xdist);
			zDistQuantity = new Quantity(-zdist, zdist);
			break;
		}
		
		config.setNumberOfRooms(numRooms);
		config.setXDistance(xDistQuantity);
		config.setZDistance(zDistQuantity);
		
		return config;
	}
}
