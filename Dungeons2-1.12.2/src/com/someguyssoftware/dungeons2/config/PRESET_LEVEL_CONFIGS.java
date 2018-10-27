/**
 * 
 */
package com.someguyssoftware.dungeons2.config;

import com.someguyssoftware.dungeonsengine.config.LevelConfig;
import com.someguyssoftware.gottschcore.Quantity;


/**
 * @author Mark Gottschling on Oct 5, 2016
 *
 */
public final class PRESET_LEVEL_CONFIGS {
	
	private static double DIRECTIONAL_LOW_OFFSET = 0.4;
	private static double DIRECTIONAL_HIGH_OFFSET = 1.6;
	
	
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
		config.setDecayMultiplier(ModConfig.decayMultiplier);
		
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
		
		// TODO remove - don't matter anymore
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
				// 25
				xdist = 30;
				zdist = 5;
				break;
			case MEDIUM:
				// 35
				xdist = 40;
				zdist = 5;
				break;
			case LARGE:
				//45
				xdist = 50;
				zdist = 5;
				break;
			case VAST:
				// 55
				xdist = 60;
				zdist = 5;
				break;
			}	
			break;
		case VERT:
			// select the x,z size
			switch(size) {
			case SMALL:
				xdist = 5;
				zdist = 30;
				break;
			case MEDIUM:
				xdist = 5;
				zdist = 40;
				break;
			case LARGE:
				xdist = 5;
				zdist = 50;
				break;
			case VAST:
				xdist = 5;
				zdist = 60;
				break;
			}	
			break;
		}
		
		// TODO not currently used
		switch(direction) {
		case CENTER:
			xDistQuantity = new Quantity(-xdist, xdist);
			zDistQuantity = new Quantity(-zdist, zdist);
			break;
		case NORTH:
			xDistQuantity = new Quantity(-xdist, xdist);
			zDistQuantity = new Quantity(-(DIRECTIONAL_LOW_OFFSET * zdist), DIRECTIONAL_HIGH_OFFSET * zdist);
			break;
		case SOUTH:
			xDistQuantity = new Quantity(-xdist, xdist);
			zDistQuantity = new Quantity(-(DIRECTIONAL_HIGH_OFFSET * zdist), DIRECTIONAL_LOW_OFFSET * zdist);
			break;
		case EAST:
			xDistQuantity = new Quantity(-(DIRECTIONAL_HIGH_OFFSET * xdist), DIRECTIONAL_LOW_OFFSET * xdist);
			zDistQuantity = new Quantity(-zdist, zdist);
			break;
		case WEST:
			xDistQuantity = new Quantity(-(DIRECTIONAL_LOW_OFFSET * xdist), DIRECTIONAL_HIGH_OFFSET * xdist);
			zDistQuantity = new Quantity(-zdist, zdist);
			break;
		}
		
		config.setNumberOfRooms(numRooms);
		config.setXDistance(xDistQuantity);
		config.setZDistance(zDistQuantity);
		
		return config;
	}
}
