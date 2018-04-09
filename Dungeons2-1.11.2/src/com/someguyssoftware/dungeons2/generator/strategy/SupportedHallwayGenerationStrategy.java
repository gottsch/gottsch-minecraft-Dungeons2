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
import com.someguyssoftware.dungeons2.generator.ISupportedBlock;
import com.someguyssoftware.dungeons2.generator.SupportedBlock;
import com.someguyssoftware.dungeons2.generator.SupportedBlockProcessor;
import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.Hallway;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.mod.Coords;
import com.someguyssoftware.mod.ICoords;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Sep 9, 2016
 *
 */
public class SupportedHallwayGenerationStrategy extends AbstractRoomGenerationStrategy {
	/*
	 * a list of all the rooms in the level 
	 */
	private List<Room> rooms;
	
	/*
	 * a list of generated hallways
	 */
	private List<Room> hallways;
	
	/**
	 * 
	 * @param blockProvider
	 * @param rooms
	 * @param hallways
	 */
	public SupportedHallwayGenerationStrategy(IDungeonsBlockProvider blockProvider, List<Room> rooms, List<Room> hallways) {
		super(blockProvider);
		//		setBlockProvider(blockProvider);
		setRooms(rooms);
		setHallways(hallways);
	}
	
	/**
	 * 
	 */
	@Override
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet, LevelConfig config) {
		Hallway hallway = (Hallway)room;
		IBlockState blockState = null;
		Map<ICoords, Arrangement> postProcessMap = new HashMap<>();
		Multimap<DesignElement, ICoords> blueprint = room.getFloorMap();

		SupportedBlockProcessor supportProcessor = new SupportedBlockProcessor(getBlockProvider(), room);
		ISupportedBlock supportedBlock = null;
		
		// collect a list of rooms that the hallway intersects against
		List<Room> intersectRooms = new ArrayList<>();
		for (Room otherRoom : getRooms()) {
			if (hallway.getBoundingBox().intersectsWith(otherRoom.getBoundingBox())) {
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

					// update support calculations for air
					if (blockState == null || arrangement.getElement() == DesignElement.AIR || blockState == Blocks.AIR.getDefaultState() || blockState == IDungeonsBlockProvider.NULL_BLOCK) {
						// create a supported block instance
						supportedBlock = new SupportedBlock(blockState, 100); // 100 = the block as been processed and is in the world
						// update the world with the blockState
						if (blockState == Blocks.AIR.getDefaultState()) {
//							Dungeons2.log.debug("Updating hallway with AIR @ " + worldCoords.toShortString());
							// update the world
							world.setBlockState(worldCoords.toBlockPos(), blockState, 3);
							// add the design element to the blueprint (if floor level)
							if (worldCoords.getY() == room.getMinY() + 1) blueprint.put(arrangement.getElement(), worldCoords);
						}
					}
					else {
						// NOTE we already know at this point that the design element is not AIR				
						boolean buildBlock = isBlockBuildable(worldCoords, hallway, intersectRooms);
//Dungeons2.log.debug(String.format("Supported buildBlock %s: %b", blockState.getBlock().getRegistryName(), buildBlock));
						// update the world with the blockState
						if (blockState != null && buildBlock && blockState != IDungeonsBlockProvider.NULL_BLOCK) {
							// apply the pass 1 support 
							// perform support rules and set the supportedBlock array
							int amount = supportProcessor.applySupportRulesPass1(world, indexCoords, worldCoords, arrangement.getElement());
//							Dungeons2.log.debug("Pass 1 Support amount:" + amount);
							if (amount >= 100) {
								supportedBlock = new SupportedBlock(blockState, 100);			
								world.setBlockState(worldCoords.toBlockPos(), blockState, 3);
							}
							else {
								supportedBlock = new SupportedBlock(blockState, amount);
							}							
						}
						else {
							// create a supported block instance of null block
							supportedBlock = new SupportedBlock(IDungeonsBlockProvider.NULL_BLOCK, 100); // 100 = the block as been processed and is in the world
//							Dungeons2.log.debug("Adding NULL BLOCK @" + worldCoords.toShortString());
						}			
					}
					// update the supported block matrix
					supportProcessor.getSupportMatrix()[y][z][x] = supportedBlock;
				}
			}			
			
			// second pass
			for (int z = room.getDepth() -1; z >= 0; z--) {
				for (int x = room.getWidth() -1; x >=0; x--) {								
					// check matrix if this entry is less than 100, ie still need checks to determine if to place
					supportedBlock = supportProcessor.getSupportMatrix()[y][z][x];
					if (supportedBlock == null || supportedBlock.getAmount() < 100 ) {

						// create index coords
						ICoords indexCoords = new Coords(x, y, z);
						// get the world coords
						ICoords worldCoords = room.getCoords().add(indexCoords);				

						// get the element arrangement
						Arrangement arrangement = getBlockProvider().getArrangement(worldCoords, room, room.getLayout());

						if (arrangement.getElement() != DesignElement.AIR) {							
							// get the block state
							blockState = getBlockProvider().getBlockState(random, worldCoords, room, arrangement, theme, styleSheet, config);
						}
						else {
							blockState = Blocks.AIR.getDefaultState();
						}
						
						// if the block is air, update the world
						if (blockState == Blocks.AIR) {
								world.setBlockState(worldCoords.toBlockPos(), blockState, 3);
						}
						// else calculate the support
						else {
							// create a supported block with 0 support
							supportedBlock = new SupportedBlock(blockState, 0);
							// determine if the block should be built
							boolean buildBlock = isBlockBuildable(worldCoords, hallway, intersectRooms);
						
							if (blockState != null && buildBlock && blockState != IDungeonsBlockProvider.NULL_BLOCK) {
								// perform support rules to determine amount of support
								int amount = supportProcessor.applySupportRulesPass2(world, indexCoords, worldCoords, arrangement.getElement());
//								Dungeons2.log.debug("Pass 2 Support amount:" + amount);
								// update supportBlock's amount of support
								supportedBlock.setAmount(supportedBlock.getAmount() + amount);
								
								// if amount is now greated than threshold, update the world
								if (supportedBlock.getAmount() >= 100) {
									world.setBlockState(worldCoords.toBlockPos(), blockState, 3);
									if (worldCoords.getY() == room.getMinY() + 1) blueprint.put(arrangement.getElement(), worldCoords);
								}
							}
						}
					}					
				}
			}
			
			// generate the post processing blocks
			postProcess(world, random, postProcessMap, room.getLayout(), theme, styleSheet, config);	
			
			// TODO should we override postProcess for Supported that is a Supported version?
		}
	}

	/**
	 * 
	 * @param worldCoords
	 * @param hallway
	 * @param intersectRooms
	 * @return
	 */
	public boolean isBlockBuildable(ICoords worldCoords, Hallway hallway, List<Room> intersectRooms) {
		// NOTE we already know at this point that the design element is not AIR				
		AxisAlignedBB box = new AxisAlignedBB(worldCoords.toBlockPos());
		boolean buildBlock = true;						

		// get the bounding boxes of the rooms the doors are connected to
		// NOTE may have to change to list in the future if more than 2 doors per hall
		AxisAlignedBB bb1 = hallway.getDoors().size() > 0 &&
				hallway.getDoors().get(0) != null ? hallway.getDoors().get(0).getRoom().getBoundingBox() : null;
		AxisAlignedBB bb2 = hallway.getDoors().size() > 1 && 
				hallway.getDoors().get(1) != null ? hallway.getDoors().get(1).getRoom().getBoundingBox() : null;
		
		// first check the wayline rooms
		if ((bb1 != null && box.intersectsWith(bb1)) || (bb2 != null && box.intersectsWith(bb2))) {
			buildBlock = false;
		}

		// second, check against any rooms in the level that the hallway intersects with
		if (buildBlock) {
			for (Room r : intersectRooms) {
				AxisAlignedBB bb = r.getBoundingBox();
				if (box.intersectsWith(bb)) {
					buildBlock = false;
					break;								
				}
			}
		}
		
		// lastly, check against all other hallways
		if (buildBlock) {
			for (Room r : getHallways()) {
				AxisAlignedBB bb = r.getBoundingBox();
				if (box.intersectsWith(bb)) {
//					Dungeons2.log.debug(String.format("Supported Hallway @ %s intersects with hallway @ %s", box, bb));
					buildBlock = false;
					break;
				}
			}
		}
		
		return buildBlock;
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

	/**
	 * @return the hallways
	 */
	public List<Room> getHallways() {
		return hallways;
	}

	/**
	 * @param hallways the hallways to set
	 */
	public void setHallways(List<Room> hallways) {
		this.hallways = hallways;
	}
}
