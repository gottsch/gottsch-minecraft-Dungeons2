/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.chest.ChestCategory;
import com.someguyssoftware.dungeons2.chest.ChestContainer;
import com.someguyssoftware.dungeons2.chest.ChestPopulator;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.generator.Location;
import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeonsengine.chest.IPopulator;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.dungeonsengine.config.LootTableMethod;
import com.someguyssoftware.gottschcore.enums.Rarity;
import com.someguyssoftware.gottschcore.loot.LootTable;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.block.BlockCarpet;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Jan 11, 2017
 *
 */
public class BossRoomDecorator extends RoomDecorator {
	
	private static final int CARPET_PERCENT_CHANCE = 75;
	private ChestPopulator chestPopulator;
	private IPopulator enginesChestPopulator;
	
	/**
	 * @param chestSheet
	 */
	public BossRoomDecorator(ChestSheet chestSheet) {
		this.chestPopulator = new ChestPopulator(chestSheet);
	}

	/**
	 * 
	 * @param populator
	 */
	public BossRoomDecorator(IPopulator populator) {
		setEnginesChestPopulator(populator);
	}
	
	/**
	 * 
	 */
	@Override
	public void decorate(World world, Random random, IDungeonsBlockProvider provider, Room room, ILevelConfig config) {
		Dungeons2.log.debug("In Boos Room Decorator.");
		List<Entry<DesignElement, ICoords>> surfaceAirZone = room.getFloorMap().entries().stream().filter(x -> x.getKey().getFamily() == DesignElement.SURFACE_AIR)
				.collect(Collectors.toList());			
		if (surfaceAirZone == null || surfaceAirZone.size() == 0) return;

		List<Entry<DesignElement, ICoords>> wallZone = null;
		List<Entry<DesignElement, ICoords>> floorZone = null;
		
		// get the floor only (from the air zone)
		floorZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.FLOOR_AIR).collect(Collectors.toList());
		
		// decorate with carpet		
		EnumDyeColor dye = EnumDyeColor.values()[random.nextInt(EnumDyeColor.values().length)];
 
		// cover floor with carpet
		IBlockState carpet = Blocks.CARPET.getDefaultState().withProperty(BlockCarpet.COLOR, dye);
		for (Entry<DesignElement, ICoords> entry : floorZone) {
			if (random.nextInt(100) < CARPET_PERCENT_CHANCE) {
				DesignElement elem = entry.getKey();
				ICoords coords = entry.getValue();
				// check if the adjoining block exists
				if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
					// update the world
					world.setBlockState(coords.toPos(), carpet, 3);	
				}
			}
		}		

		// get the walls only (from the air zone)
		wallZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.WALL_AIR).collect(Collectors.toList());
				
		// add paintings
		for (int i = 0; i < 4; i++) {
			Entry<DesignElement, ICoords> entry = wallZone.get(random.nextInt(wallZone.size()));
			ICoords coords = entry.getValue();
			Location location = provider.getLocation(coords, room, room.getLayout());
			EnumFacing facing = location.getFacing();
			if (location != null) {
				EntityHanging entityhanging = new EntityPainting(world, coords.toPos(), facing);
		        if (entityhanging != null && entityhanging.onValidSurface()) {
		            if (WorldInfo.isServerSide(world)/*!world.isRemote*/) {
		                entityhanging.playPlaceSound();
		                world.spawnEntity(entityhanging);
		            }
		        }
			}
			wallZone.remove(entry);
		}

		// TODO add pedestal/alter
		
		/*
		 * add chest
		 */
		// select a random position on the floor
		Entry<DesignElement, ICoords> floorEntry = floorZone.get(random.nextInt(floorZone.size()));
		DesignElement elem = floorEntry.getKey();
		ICoords chestCoords = floorEntry.getValue();
		// determine location in room
		Location location = provider.getLocation(chestCoords, room, room.getLayout());
		if (hasSupport(world, chestCoords, elem, location)) {	
			EnumFacing facing = orientChest(location);			
			// place a chest
			world.setBlockState(chestCoords.toPos(), Blocks.CHEST.getDefaultState().withProperty(BlockHorizontal.FACING,  facing), 3);
			// remove from list
			floorZone.remove(floorEntry);
		}
		else {
			Dungeons2.log.debug("Boss Chest has no floor support");
			chestCoords = null;
		}
		
		/*
		 * NOT this is duplicated from RoomDecorator - change into method
		 */
		if (chestCoords != null) {
			Dungeons2.log.debug("Adding boss chest @ " + chestCoords.toShortString());
			
			getEnginesChestPopulator().populate(world, random, chestCoords, config.getChestConfig());
			
			// get the chest inventory
//			IInventory inventory = this.chestPopulator.getChestTileEntity(world, chestCoords);
//			if (inventory != null) {				
//				// TODO use loot tables here. Get the EPIC/BOSS loot tables from map and populate the chest
//				// TODO check the chest config if CUSTOM or BUILTIN to determine how the chest should be filled.
//				if (config.getChestConfig() != null) {
//					if (config.getChestConfig().getLootTableMethod() == LootTableMethod.CUSTOM) {
////						List<Rarity> rarities = config.getChestConfig().getRarity();
//						enginesChestPopulator.populate(world, random, chestCoords, config.getChestConfig());
//						
//						// TODO all this could be one method fillChest(rand, inventory, rarity)
//						List<LootTable> lootTables = Dungeons2.LOOT_TABLES.getLootTableByRarity(Rarity.SCARCE);
//						if (lootTables != null) {
//							Dungeons2.log.debug("found loot tables -> {}", lootTables.size());
//							int index = RandomHelper.randomInt(random, 0, lootTables.size()-1);
//							LootTable table = lootTables.get(index);
//							table.fillInventory(inventory, random, Dungeons2.LOOT_TABLES.getContext());
//						}
//						else
//							Dungeons2.log.debug("did not find any loot tables by rarity -> {}", Rarity.SCARCE);
//					}
//				}
				
				// select a epic/boss chest
//				String chestCategory = ChestCategory.EPIC.name().toLowerCase();
//				Dungeons2.log.debug("Chest category:" + chestCategory);
//				// get chests by category and choose one
//				List<ChestContainer> containers = (List<ChestContainer>) chestPopulator.getMap().get(chestCategory);
//				Dungeons2.log.debug("Containers found:" + containers.size());
//				if (containers != null && !containers.isEmpty()) {
//					// TODO use RandomProbabilityCollection
//					ChestContainer chest = containers.get(random.nextInt(containers.size()));
//					// populate the chest with items from the selected chest sheet container
//					chestPopulator.populate(random, inventory, chest);
//					// TODO update room floor map with chest
//				}
//			}
//			else {
//				Dungeons2.log.debug("Chest does not have iinventory.");
//			}
		}
		
	}
	
	/**
	 * 
	 */
	@Deprecated
	@Override
	public void decorate(World world, Random random, IDungeonsBlockProvider provider, Room room, LevelConfig config) {
		Dungeons2.log.debug("In Boos Room Decorator.");
		List<Entry<DesignElement, ICoords>> surfaceAirZone = room.getFloorMap().entries().stream().filter(x -> x.getKey().getFamily() == DesignElement.SURFACE_AIR)
				.collect(Collectors.toList());			
		if (surfaceAirZone == null || surfaceAirZone.size() == 0) return;

		List<Entry<DesignElement, ICoords>> wallZone = null;
		List<Entry<DesignElement, ICoords>> floorZone = null;
		
		// get the floor only (from the air zone)
		floorZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.FLOOR_AIR).collect(Collectors.toList());
		
		// decorate with carpet		
		EnumDyeColor dye = EnumDyeColor.values()[random.nextInt(EnumDyeColor.values().length)];
 
		// cover floor with carpet
		IBlockState carpet = Blocks.CARPET.getDefaultState().withProperty(BlockCarpet.COLOR, dye);
		for (Entry<DesignElement, ICoords> entry : floorZone) {
			if (random.nextInt(100) < CARPET_PERCENT_CHANCE) {
				DesignElement elem = entry.getKey();
				ICoords coords = entry.getValue();
				// check if the adjoining block exists
				if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
					// update the world
					world.setBlockState(coords.toPos(), carpet, 3);	
				}
			}
		}		

		// get the walls only (from the air zone)
		wallZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.WALL_AIR).collect(Collectors.toList());
				
		// add paintings
		for (int i = 0; i < 4; i++) {
			Entry<DesignElement, ICoords> entry = wallZone.get(random.nextInt(wallZone.size()));
			ICoords coords = entry.getValue();
			Location location = provider.getLocation(coords, room, room.getLayout());
			EnumFacing facing = location.getFacing();
			if (location != null) {
				EntityHanging entityhanging = new EntityPainting(world, coords.toPos(), facing);
		        if (entityhanging != null && entityhanging.onValidSurface()) {
		            if (WorldInfo.isServerSide(world)/*!world.isRemote*/) {
		                entityhanging.playPlaceSound();
		                world.spawnEntity(entityhanging);
		            }
		        }
			}
			wallZone.remove(entry);
		}

		// TODO add pedestal/alter
		
		/*
		 * add chest
		 */
		// select a random position on the floor
		Entry<DesignElement, ICoords> floorEntry = floorZone.get(random.nextInt(floorZone.size()));
		DesignElement elem = floorEntry.getKey();
		ICoords chestCoords = floorEntry.getValue();
		// determine location in room
		Location location = provider.getLocation(chestCoords, room, room.getLayout());
		if (hasSupport(world, chestCoords, elem, location)) {	
			EnumFacing facing = orientChest(location);			
			// place a chest
			world.setBlockState(chestCoords.toPos(), Blocks.CHEST.getDefaultState().withProperty(BlockHorizontal.FACING,  facing), 3);
			// remove from list
			floorZone.remove(floorEntry);
		}
		else {
			Dungeons2.log.debug("Boss Chest has no floor support");
			chestCoords = null;
		}
		
		/*
		 * NOT this is duplicated from RoomDecorator - change into method
		 */
		if (chestCoords != null) {
			Dungeons2.log.debug("Adding boss chest @ " + chestCoords.toShortString());
			// get the chest inventory
			IInventory inventory = this.chestPopulator.getChestTileEntity(world, chestCoords);
			if (inventory != null) {
				// TODO categories should have a weight as well.. ie common chests should occur more than rare etc.
				// select a epic/boss chest
				String chestCategory = ChestCategory.EPIC.name().toLowerCase();
				Dungeons2.log.debug("Chest category:" + chestCategory);
				// get chests by category and choose one
				List<ChestContainer> containers = (List<ChestContainer>) chestPopulator.getMap().get(chestCategory);
				Dungeons2.log.debug("Containers found:" + containers.size());
				if (containers != null && !containers.isEmpty()) {
					// TODO use RandomProbabilityCollection
					ChestContainer chest = containers.get(random.nextInt(containers.size()));
					// populate the chest with items from the selected chest sheet container
					chestPopulator.populate(random, inventory, chest);
					// TODO update room floor map with chest
				}
			}
			else {
				Dungeons2.log.debug("Chest does not have iinventory.");
			}
		}
		
	}

	/**
	 * @return the enginesChestPopulator
	 */
	public IPopulator getEnginesChestPopulator() {
		return enginesChestPopulator;
	}

	/**
	 * @param enginesChestPopulator the enginesChestPopulator to set
	 */
	public void setEnginesChestPopulator(IPopulator enginesChestPopulator) {
		this.enginesChestPopulator = enginesChestPopulator;
	}
}

