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
import com.someguyssoftware.dungeons2.generator.ISupportedBlock;
import com.someguyssoftware.dungeons2.generator.SupportedBlock;
import com.someguyssoftware.dungeons2.generator.SupportedBlockProcessor;
import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Aug 28, 2016
 *
 */
public class SupportedRoomGenerationStrategy extends AbstractRoomGenerationStrategy {

	/**
	 * 
	 * @param provider
	 */
	public SupportedRoomGenerationStrategy(IDungeonsBlockProvider provider) {
		super(provider);
	}
	
	/* 
	 * This class uses a 2-pass bottom-up supportive method for generation.
	 * The layout is known and set by the calling method as it selects the layout for the room.  The room contains the layout. 
	 * The style is NOT known because each element of the room needs to be processed. Therefor StyleSheet is passed to the method.
	 * (non-Javadoc)
	 * @see com.someguyssoftware.dungeons2.generator.IRoomGenerator#generate(net.minecraft.world.World, com.someguyssoftware.dungeons2.model.Room, com.someguyssoftware.dungeons2.style.Layout, com.someguyssoftware.dungeons2.style.Style)
	 */
	@Override
	public void generate(World world, Random random, Room room,
			Theme theme, StyleSheet styleSheet, LevelConfig config) {
		
		IBlockState blockState = null;
		SupportedBlockProcessor supportProcessor = new SupportedBlockProcessor(getBlockProvider(), room);
		ISupportedBlock supportedBlock = null;
		Map<ICoords, Arrangement> postProcessMap = new HashMap<>();
		Multimap<DesignElement, ICoords> blueprint = room.getFloorMap();
		
		// first pass
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
			
					// TEMP
//					if (arrangement.getElement() == DesignElement.FACADE_SUPPORT) {
//					Dungeons2.log.debug(String.format("Element: %s, BlockState: %s @ %s", arrangement.getElement().getName(),
//							blockState.toString(), worldCoords.toShortString()));
//					}

					// update support calculations for air
					if (blockState == null || blockState == Blocks.AIR || blockState == IDungeonsBlockProvider.NULL_BLOCK) {
						// create a supported block instance
						supportedBlock = new SupportedBlock(blockState, 100); // 100 = the block as been processed and is in the world
						// update the world with the blockState
						if (blockState != null && blockState != IDungeonsBlockProvider.NULL_BLOCK) {
							world.setBlockState(worldCoords.toPos(), blockState, 3);
							// add the design element to the blueprint (if floor level)
//							if (worldCoords.getY() == room.getMinY() + 1) blueprint.put(arrangement.getElement(), worldCoords);
							// add the design element to the blueprint (if floor level or surface_air)
							if (worldCoords.getY() == room.getMinY() + 1 ||
									arrangement.getElement().getFamily() == DesignElement.SURFACE_AIR) {
								blueprint.put(arrangement.getElement(), worldCoords);
							}
						}
					}
					else {
						// apply the pass 1 support 
						// perform support rules and set the supportedBlock array
						int amount = supportProcessor.applySupportRulesPass1(world, indexCoords, worldCoords, arrangement.getElement());
//						Dungeons2.log.debug("Pass 1 Support amount:" + amount);
						if (amount >= 100) {
							supportedBlock = new SupportedBlock(blockState, 100);			
							world.setBlockState(worldCoords.toPos(), blockState, 3);
//							if (worldCoords.getY() == room.getMinY() + 1) blueprint.put(arrangement.getElement(), worldCoords);
							// add the design element to the blueprint (if floor level or surface_air)
							if (worldCoords.getY() == room.getMinY() + 1 ||
									arrangement.getElement().getFamily() == DesignElement.SURFACE_AIR) {
								blueprint.put(arrangement.getElement(), worldCoords);
							}
						}
						else {
							supportedBlock = new SupportedBlock(blockState, amount);
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
								world.setBlockState(worldCoords.toPos(), blockState, 3);
						}
						// else calculate the support
						else {
							// create a supported block with 0 support
							supportedBlock = new SupportedBlock(blockState, 0);

							// perform support rules to determine amount of support
							int amount = supportProcessor.applySupportRulesPass2(world, indexCoords, worldCoords, arrangement.getElement());
//							Dungeons2.log.debug("Pass 2 Support amount:" + amount);
							// update supportBlock's amount of support
							supportedBlock.setAmount(supportedBlock.getAmount() + amount);
//							Dungeons2.log.debug("Total Support amount:" + supportedBlock.getAmount());
							// if amount is now greated than threshold, update the world
							if (supportedBlock.getAmount() >= 100) {
								world.setBlockState(worldCoords.toPos(), blockState, 3);
								if (worldCoords.getY() == room.getMinY() + 1) blueprint.put(arrangement.getElement(), worldCoords);
							}
							else {
								// not supported, set to air
								world.setBlockState(worldCoords.toPos(), Blocks.AIR.getDefaultState(), 3);
							}
						}
					}
				}
			}
		}	
		
		// generate the post processing blocks
		postProcess(world, random, postProcessMap, room.getLayout(), theme, styleSheet, config);	
	}

//	/**
//	 * @return the blockProvider
//	 */
//	@Override
//	public IDungeonsBlockProvider getBlockProvider() {
//		return blockProvider;
//	}
//
//	/**
//	 * @param blockProvider the blockProvider to set
//	 */
//	@Override
//	public void setBlockProvider(IDungeonsBlockProvider blockProvider) {
//		this.blockProvider = blockProvider;
//	}

}
