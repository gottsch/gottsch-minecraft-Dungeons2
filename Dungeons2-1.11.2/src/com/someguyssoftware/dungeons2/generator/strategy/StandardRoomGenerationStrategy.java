/**
 * 
 */
package com.someguyssoftware.dungeons2.generator.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.generator.AbstractRoomGenerationStrategy;
import com.someguyssoftware.dungeons2.generator.Arrangement;
import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.mod.Coords;
import com.someguyssoftware.mod.ICoords;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;

/**
 * Builds a structure using the base rule set ie. all blocks are generated regardless of location, adjacent blocks etc.
 * @author Mark Gottschling on Aug 27, 2016
 *
 */
public class StandardRoomGenerationStrategy extends AbstractRoomGenerationStrategy {
	
	/**
	 * 
	 * @param blockProvider
	 */
	public StandardRoomGenerationStrategy(IDungeonsBlockProvider blockProvider) {
//		setBlockProvider(blockProvider);
		super(blockProvider);
	}
	
	/**
	 * 
	 */
	@Override
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet, LevelConfig config) {
		IBlockState blockState = null;
		Multimap<DesignElement, ICoords> blueprint = room.getFloorMap();
		Map<ICoords, Arrangement> postProcessMap = new HashMap<>();
		
		// generate the room
		for (int y = 0; y < room.getHeight(); y++) {
			// first pass
			for (int z = 0; z < room.getDepth(); z++) {
				for (int x = 0; x < room.getWidth(); x++) {

					// create index coords
					ICoords indexCoords = new Coords(x, y, z);
					// get the world coords
					ICoords worldCoords = room.getCoords().add(indexCoords);
					
					// get the design arrangement of the block @ xyz
					Arrangement arrangement = getBlockProvider().getArrangement(worldCoords, room, room.getLayout());
					
					// add the design element to the blueprint (if floor level or surface_air)
					if (worldCoords.getY() == room.getMinY() + 1 ||
							arrangement.getElement().getFamily() == DesignElement.SURFACE_AIR) {
						blueprint.put(arrangement.getElement(), worldCoords);
					}
					
					// if element is of a type that requires post-processing, save for processing after the rest of the room is generated
					if (isPostProcessed(arrangement, worldCoords, postProcessMap)) continue;
					
					/*
					 *  TODO need to add a type of DesignElement that helps age the room.  Something to define if it is something that
					 *  can be placed on the floor (ymin+1) or something that can hang from ceiling (ymax-1).
					 *  This would be used for webs etc.
					 */
					
					// get the block state
					blockState = getBlockProvider().getBlockState(random, worldCoords, room, arrangement, theme, styleSheet, config);

					// update the world with the blockState
					if (blockState != null && blockState != IDungeonsBlockProvider.NULL_BLOCK) {
						world.setBlockState(worldCoords.toBlockPos(), blockState, 3);
					}
				}				
			}
		}
		
		// generate the post processing blocks
		postProcess(world, random, postProcessMap, room.getLayout(), theme, styleSheet, config);	
	}
}
