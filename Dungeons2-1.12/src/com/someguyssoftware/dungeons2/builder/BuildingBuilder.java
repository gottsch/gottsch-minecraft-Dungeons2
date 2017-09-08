/**
 * 
 */
package com.someguyssoftware.dungeons2.builder;

import com.someguyssoftware.dungeons2.model.Building;
import com.someguyssoftware.dungeons2.model.FloorConfig;
import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.random.RandomHelper;

/**
 * @author Mark Gottschling on Sep 7, 2017
 *
 */
public class BuildingBuilder {
	
	public Building build() {	
		/*
		 * the number of potential rooms in the entrance
		 */
		int numOfPotentialRooms = RandomHelper.randomInt(1, 5);
		
		/*
		 * the number of potential floors in the entrance
		 */
		int numOfPotentialFloors = RandomHelper.randomInt(1, 5);
		/*
		 * array to hold the number of floors each room will have
		 */
		int[] roomFloorsHash = new int[numOfPotentialRooms];
		/*
		 * the number of actual floors
		 */
		int numOfFloors = 1;
		
		// initialize roomLevels
		for (int i = 0; i < numOfPotentialRooms; i++) {
			roomFloorsHash[i] = RandomHelper.randomInt(0, numOfPotentialFloors) + 1;
			if (roomFloorsHash[i] > numOfFloors) numOfFloors = roomFloorsHash[i];
		}
		
		FloorConfig floorConfig = new FloorConfig();
		floorConfig.setNumberOfRooms(new Quantity(1, 5)); // TODO get from PRESETs
		
		// for every floor
		int floorIndex = 0;
		
		return null;
	}

}
