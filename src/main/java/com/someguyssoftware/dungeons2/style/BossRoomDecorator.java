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
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.generator.Location;
import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeonsengine.chest.ILootLoader;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;
import com.someguyssoftware.gottschcore.world.WorldInfo;
import com.someguyssoftware.treasure2.Treasure;
import com.someguyssoftware.treasure2.enums.Rarity;
import com.someguyssoftware.treasure2.enums.WorldGeneratorType;
import com.someguyssoftware.treasure2.generator.ChestGeneratorData;
import com.someguyssoftware.treasure2.generator.GeneratorResult;
import com.someguyssoftware.treasure2.generator.chest.IChestGenerator;
import com.someguyssoftware.treasure2.worldgen.SurfaceChestWorldGenerator;

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
import net.minecraftforge.fml.common.Loader;

/**
 * @author Mark Gottschling on Jan 11, 2017
 *
 */
public class BossRoomDecorator extends RoomDecorator {
	
	private static final int CARPET_PERCENT_CHANCE = 75;
	private ChestPopulator chestPopulator;
	private ILootLoader lootLoader;
	
	/**
	 * @param chestSheet
	 */
	public BossRoomDecorator(ChestSheet chestSheet) {
//		this.chestPopulator = new ChestPopulator(chestSheet);
	}

	/**
	 * 
	 * @param loader
	 */
	public BossRoomDecorator(ILootLoader loader) {
		setLootLoader(loader);
	}
	
	/**
	 * 
	 */
    // TODO this method needs to use the Template pattern. Needs to take in the Dungeon and return the dungeon
    // or has to return something that returns all important things added like, chests, spawners and any other specials. (like StructureGen in Treasure)
	@Override
	public void decorate(World world, Random random, Dungeon dungeon, IDungeonsBlockProvider provider, Room room, ILevelConfig config) {
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
		 * NOTE don't need to handle the chest coords as the chest if filled within the method
		 */
		addChest(world, random, dungeon, provider, room, floorZone, config);
	}
	
	// TODO add method to interface
	protected ICoords addChest(World world, Random random, Dungeon dungeon, IDungeonsBlockProvider provider, Room room,
			List<Entry<DesignElement, ICoords>> floorZone, ILevelConfig config) {

		// select a random position on the floor
		Entry<DesignElement, ICoords> floorEntry = floorZone.get(random.nextInt(floorZone.size()));
		DesignElement elem = floorEntry.getKey();
		ICoords chestCoords = floorEntry.getValue();
		// determine location in room
		Location location = provider.getLocation(chestCoords, room, room.getLayout());
		if (hasSupport(world, chestCoords, elem, location)) {
			EnumFacing facing = orientChest(location);		
			IBlockState chestState = Blocks.CHEST.getDefaultState().withProperty(BlockHorizontal.FACING,  facing);
			
			boolean isChestPlaced = false;
            // place a chest
            if (ModConfig.enableTreasure2Integration
            		&& Loader.isModLoaded(Treasure.MODID)
            		&& RandomHelper.checkProbability(random, ModConfig.treasure2ChestProbability)) {
            	Dungeons2.log.debug("boss room adding Treasure2 chest @ {}", chestCoords.toShortString());
                // determine rarity based on dungeon size, # of levels
            	int rooms = 0;
            	for (Level level : dungeon.getLevels()) {
            		rooms += level.getRooms().size();  
            	}
            	int levels = dungeon.getLevels().size();
            	
            	// run thru all level maxing the # of rooms.
            	Rarity rarity = Rarity.UNCOMMON;
            	if (levels > 8 || rooms > 260) {
            		rarity = Rarity.EPIC;
            	}
            	else if (levels > 5 || rooms > 180) {
            		rarity = Rarity.RARE;
            	}
            	else if (levels > 2 || rooms > 100) {
            		rarity = Rarity.SCARCE;
            	}

            	Dungeons2.log.debug("boss room using rarity -> {}", rarity);
            	
    			// get the chest world generator
    			SurfaceChestWorldGenerator chestGens = (SurfaceChestWorldGenerator) Treasure.WORLD_GENERATORS
    					.get(WorldGeneratorType.SURFACE_CHEST);
    			// get the rarity chest generator
    			IChestGenerator gen = chestGens.getChestGenMap().get(rarity).next();
    			GeneratorResult<ChestGeneratorData> chestResult = gen.generate(world, random, chestCoords, rarity, chestState);
    			if (chestResult.isSuccess()) {
    				isChestPlaced = true;
    			}
            }
 
            // default action
            if (!isChestPlaced) {
            	Dungeons2.log.debug("boss room, treasure2 chest was NOT generated, using default.");
    			world.setBlockState(chestCoords.toPos(), chestState, 3);
    			/*
    			 * NOTE this is duplicated from RoomDecorator - change into method
    			 */
				Dungeons2.log.debug("Adding boss chest @ " + chestCoords.toShortString());			
				getLootLoader().fill(world, random, chestCoords, config.getChestConfig());
            }

			// remove from list
			floorZone.remove(floorEntry);
		}
		else {
			Dungeons2.log.debug("Boss Chest has no floor support");
			chestCoords = null;
		}
		return chestCoords;
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
	 * @return the lootLoader
	 */
	public ILootLoader getLootLoader() {
		return lootLoader;
	}

	/**
	 * @param lootLoader the lootLoader to set
	 */
	public void setLootLoader(ILootLoader loader) {
		this.lootLoader = loader;
	}
}

