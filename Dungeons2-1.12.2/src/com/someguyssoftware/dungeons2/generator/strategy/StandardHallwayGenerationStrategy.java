/**
 * 
 */
package com.someguyssoftware.dungeons2.generator.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.generator.AbstractRoomGenerationStrategy;
import com.someguyssoftware.dungeons2.generator.Arrangement;
import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Builds a structure using the base rule set ie. all blocks are generated regardless of location, adjacent blocks etc.
 * @author Mark Gottschling on Aug 27, 2016
 *
 */
public class StandardHallwayGenerationStrategy extends AbstractRoomGenerationStrategy {
	/*
	 * a list of all the rooms in the level 
	 */
	private List<Room> rooms;
	
	/*
	 * a list of generated hallways
	 */
	private List<Hallway> hallways;
		
//	/**
//	 * 
//	 * @param blockProvider
//	 */
//	public StandardHallwayGenerationStrategy(IDungeonsBlockProvider blockProvider) {
//		setBlockProvider(blockProvider);
//	}
	
	/**
	 * 
	 * @param blockProvider
	 * @param rooms
	 * @param hallways
	 */
	public StandardHallwayGenerationStrategy(IDungeonsBlockProvider blockProvider, List<Room> rooms, List<Hallway> hallways) {
		super(blockProvider);
		//		setBlockProvider(blockProvider);
		setRooms(rooms);
		setHallways(hallways);
	}
	
	@Override
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet, ILevelConfig config) {
		Hallway hallway = (Hallway)room;
		IBlockState blockState = null;
		Map<ICoords, Arrangement> postProcessMap = new HashMap<>();
		Multimap<DesignElement, ICoords> blueprint = room.getFloorMap();
		
		// collect a list of rooms that the hallway intersects against
		List<Room> intersectRooms = new ArrayList<>();
		for (Room otherRoom : getRooms()) {
			if (hallway.getBoundingBox().intersects(otherRoom.getBoundingBox())) {
//				Dungeons2.log.debug("Hallway intersects with Room: " + room);
				intersectRooms.add(otherRoom);
			}
		}
		
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
					
					// if element is of a type that requires post-processing, save for processing after the rest of the room is generated
					if (isPostProcessed(arrangement, worldCoords, postProcessMap)) continue;
					
					// get the block state
					blockState = getBlockProvider().getBlockState(random, worldCoords, room, arrangement, theme, styleSheet, config);
					if (blockState == IDungeonsBlockProvider.NULL_BLOCK) continue;
					
					AxisAlignedBB box = new AxisAlignedBB(worldCoords.toPos());
					boolean buildBlock = true;
					if (arrangement.getElement() != DesignElement.AIR) {
						// get the bounding boxes of the rooms the doors are connected to
						// NOTE may have to change to list in the future if more than 2 doors per hall
						AxisAlignedBB bb1 = hallway.getDoors().size() > 0 &&
								hallway.getDoors().get(0) != null ? hallway.getDoors().get(0).getRoom().getBoundingBox() : null;
						AxisAlignedBB bb2 = hallway.getDoors().size() > 1 && 
								hallway.getDoors().get(1) != null ? hallway.getDoors().get(1).getRoom().getBoundingBox() : null;
						
						// first check the wayline rooms
						if ((bb1 != null && box.intersects(bb1)) || (bb2 != null && box.intersects(bb2))) {
							buildBlock = false;
						}						
						// second, check against any rooms in the level that the hallway intersects with
						if (buildBlock) {
							for (Room r : intersectRooms) {
								AxisAlignedBB bb = r.getBoundingBox();
								if (box.intersects(bb)) {
//									Dungeons2.log.debug(String.format("Hallway @ %s intersects with room @ %s", box, bb));
									buildBlock = false;
									break;								
								}
							}
						}						
						// lastly, check against all other hallways
						if (buildBlock) {
							for (Room r : getHallways()) {
								AxisAlignedBB bb = r.getBoundingBox();
								if (box.intersects(bb)) {
//									Dungeons2.log.debug(String.format("Hallway @ %s intersects with hallway @ %s", box, bb));
									buildBlock = false;
									break;
								}
							}
						}						
					}
					// update the world with the blockState
					if (blockState != null && buildBlock && blockState != IDungeonsBlockProvider.NULL_BLOCK) {
						world.setBlockState(worldCoords.toPos(), blockState, 3);
					}
				}				
			}
		}		
		// generate the post processing blocks
		postProcess(world, random, postProcessMap, room.getLayout(), theme, styleSheet, config);	
	}
	
	/**
	 * 
	 */
	@Override
	@Deprecated
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet, LevelConfig config) {
		Hallway hallway = (Hallway)room;
		IBlockState blockState = null;
		Map<ICoords, Arrangement> postProcessMap = new HashMap<>();
		Multimap<DesignElement, ICoords> blueprint = room.getFloorMap();
		
		// collect a list of rooms that the hallway intersects against
		List<Room> intersectRooms = new ArrayList<>();
		for (Room otherRoom : getRooms()) {
			if (hallway.getBoundingBox().intersects(otherRoom.getBoundingBox())) {
//				Dungeons2.log.debug("Hallway intersects with Room: " + room);
				intersectRooms.add(otherRoom);
			}
		}
		
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
					
					// if element is of a type that requires post-processing, save for processing after the rest of the room is generated
					if (isPostProcessed(arrangement, worldCoords, postProcessMap)) continue;
					
					// get the block state
					blockState = getBlockProvider().getBlockState(random, worldCoords, room, arrangement, theme, styleSheet, config);
					if (blockState == IDungeonsBlockProvider.NULL_BLOCK) continue;
					
					AxisAlignedBB box = new AxisAlignedBB(worldCoords.toPos());
					boolean buildBlock = true;
					if (arrangement.getElement() != DesignElement.AIR) {
						// get the bounding boxes of the rooms the doors are connected to
						// NOTE may have to change to list in the future if more than 2 doors per hall
						AxisAlignedBB bb1 = hallway.getDoors().size() > 0 &&
								hallway.getDoors().get(0) != null ? hallway.getDoors().get(0).getRoom().getBoundingBox() : null;
						AxisAlignedBB bb2 = hallway.getDoors().size() > 1 && 
								hallway.getDoors().get(1) != null ? hallway.getDoors().get(1).getRoom().getBoundingBox() : null;
						
						// first check the wayline rooms
						if ((bb1 != null && box.intersects(bb1)) || (bb2 != null && box.intersects(bb2))) {
							buildBlock = false;
						}
						
						// second, check against any rooms in the level that the hallway intersects with
						if (buildBlock) {
							for (Room r : intersectRooms) {
								AxisAlignedBB bb = r.getBoundingBox();
								if (box.intersects(bb)) {
//									Dungeons2.log.debug(String.format("Hallway @ %s intersects with room @ %s", box, bb));
									buildBlock = false;
									break;								
								}
							}
						}
						
						// lastly, check against all other hallways
						if (buildBlock) {
							for (Room r : getHallways()) {
								AxisAlignedBB bb = r.getBoundingBox();
								if (box.intersects(bb)) {
//									Dungeons2.log.debug(String.format("Hallway @ %s intersects with hallway @ %s", box, bb));
									buildBlock = false;
									break;
								}
							}
						}
						
					}

					// update the world with the blockState
					if (blockState != null && buildBlock && blockState != IDungeonsBlockProvider.NULL_BLOCK) {
						world.setBlockState(worldCoords.toPos(), blockState, 3);
					}
				}				
			}
		}
		
		// generate the post processing blocks
		postProcess(world, random, postProcessMap, room.getLayout(), theme, styleSheet, config);	

	}

	/**
	 * @return the hallways
	 */
	public List<Hallway> getHallways() {
		return hallways;
	}

	/**
	 * @param hallways the hallways to set
	 */
	public void setHallways(List<Hallway> hallways) {
		this.hallways = hallways;
	}

	/**
	 * @return the rooms
	 */
	public List<Room> getRooms() {
		return rooms;
	}

	/**
	 * @param rooms the rooms to set
	 */
	public void setRooms(List<Room> rooms) {
		this.rooms = rooms;
	}
}
