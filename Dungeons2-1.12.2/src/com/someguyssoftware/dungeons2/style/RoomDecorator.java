/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.block.DecorationBlock;
import com.someguyssoftware.dungeons2.block.DungeonsBlocks;
import com.someguyssoftware.dungeons2.chest.ChestContainer;
import com.someguyssoftware.dungeons2.chest.ChestPopulator;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.generator.Location;
import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.rotate.RotatorHelper;
import com.someguyssoftware.dungeons2.spawner.SpawnGroup;
import com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import com.someguyssoftware.dungeons2.spawner.SpawnerPopulator;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;
import com.someguyssoftware.gottschcore.random.RandomProbabilityCollection;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockDirt.DirtType;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Sep 7, 2016
 *
 */
public class RoomDecorator implements IRoomDecorator {
	final PropertyEnum<BlockTallGrass.EnumType> GRASSTYPE = PropertyEnum.<BlockTallGrass.EnumType>create("type", BlockTallGrass.EnumType.class);

	private ChestPopulator chestPopulator;
	private SpawnerPopulator spawnerPopulator;

	/**
	 * 
	 */
	public RoomDecorator() {}
	
	/**
	 * 
	 * @param chestSheet
	 */
	public RoomDecorator(ChestSheet chestSheet, SpawnSheet spawnSheet) {
		this.chestPopulator = new ChestPopulator(chestSheet);
		this.spawnerPopulator = new SpawnerPopulator(spawnSheet);
		//		this.chestSheet = chestSheet;
	}

	@Override
	public void decorate(World world, Random random, IDungeonsBlockProvider provider, Room room, ILevelConfig config) {
//		Dungeons2.log.debug("floorMap in decorate -> {}", room.getFloorMap());
		List<Entry<DesignElement, ICoords>> surfaceAirZone = room.getFloorMap().entries().stream().filter(x -> x.getKey().getFamily() == DesignElement.SURFACE_AIR)
				.collect(Collectors.toList());			
		
//		Dungeons2.log.debug("SurfaceAirZone.size() -> {}", surfaceAirZone.size());
		
		if (surfaceAirZone == null || surfaceAirZone.size() == 0) return;

		List<Entry<DesignElement, ICoords>> wallZone = null;
		List<Entry<DesignElement, ICoords>> floorZone = null;

		if (config.isDecorations() || ModConfig.enableChests) {
			// get the floor only (from the air zone)
			floorZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.FLOOR_AIR).collect(Collectors.toList());
		}
		
		// decorate enabled
		if (config.isDecorations()) {
			// TODO these methods could be reduced to more generic methods
			
			/*
			 * webs
			 */
			addBlock(world, random, provider, room, surfaceAirZone, 
					new IBlockState[] {Blocks.WEB.getDefaultState()}, config.getWebFrequency(), config.getNumberOfWebs(), config);

			/*
			 * all-over decorations: moss, lichen, lichen2, mold
			 */
//			addAnywhereDecoration(world, random, provider, room, surfaceAirZone, config);

			// get the walls only (from the air zone)
			wallZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.WALL_AIR).collect(Collectors.toList());

			/*
			 * vines
			 */
			addVines(world, random, provider, room, wallZone, config);

			/*
			 * wall blood
			 */
//			addBlood(world, random, provider, room, wallZone, config);

			/*
			 * grass
			 */
			addGrass(world, random, provider, room, floorZone, config);

			// TODO change addGrass to addDirtSupportBlock() which will pass into grasses, mushrooms
			/*
			 * floor blood
			 */
//			addBlood(world, random, provider, room, floorZone, config);
			
			/*
			 * puddles
			 */
//			addPuddles(world, random, provider, room, floorZone, config);
			
			/*
			 * add water (above the ceiling block so that there are drips coming down... maybe should be above puddles?
			 */
			
			// TODO add roots

			// TODO add debris

		}

		/*
		 * chest
		 */
		// TODO this should be part of ILevelConfig
		if (ModConfig.enableChests) {
		ICoords chestCoords = addChest(world, random, provider, room, floorZone, config);
			if (chestCoords != null) {
				if (Dungeons2.log.isDebugEnabled()) {
					Dungeons2.log.debug("Added chest block @ " + chestCoords.toShortString());
				}
				// get the chest inventory
				IInventory inventory = this.chestPopulator.getChestTileEntity(world, chestCoords);
//				Dungeons2.log.debug("Checking for chest tile entity...");
				if (inventory == null) {
					Dungeons2.log.debug("Manually adding chest tile entity.");
					world.setTileEntity(chestCoords.toPos(), new TileEntityChest());
					inventory = (TileEntityChest) world.getTileEntity(chestCoords.toPos());
				}
				if (inventory != null) {
					// read the chest categories from the level config
					String chestCategory = config.getChestCategories().get(random.nextInt(config.getChestCategories().size()));
					Dungeons2.log.debug("Chest category:" + chestCategory);
					// get chests by category and choose one
					List<ChestContainer> containers = (List<ChestContainer>) chestPopulator.getMap().get(chestCategory);
//					Dungeons2.log.debug("Containers found:" + containers.size());
					if (containers != null && !containers.isEmpty()) {
						// add each container to the random prob collection
						RandomProbabilityCollection<ChestContainer> chestProbCol = new RandomProbabilityCollection<>(containers);
						// select a container
						ChestContainer chest = (ChestContainer) chestProbCol.next();
	//					ChestContainer chest = containers.get(random.nextInt(containers.size()));
						// populate the chest with items from the selected chest sheet container
						chestPopulator.populate(random, inventory, chest);
					}
				}
				else {
					Dungeons2.log.debug("Chest tile entity not found... removing chest block.");
					world.setBlockState(chestCoords.toPos(), Blocks.AIR.getDefaultState());
				}
			}
		}

		/*
		 * determine if the rroom should get a spawner and what kind (boss, one-time, vanilla) etc
		 */
		if (ModConfig.enableSpawners) {
			ICoords spawnerCoords = addSpawner(world, random, provider, room, floorZone, config);
			if (spawnerCoords != null) {
				Dungeons2.log.debug("Adding spawner @ " + spawnerCoords.toShortString());
				// get the spawner tile entity
				TileEntityMobSpawner spawner = (TileEntityMobSpawner) world.getTileEntity(spawnerCoords.toPos());
				if (spawner != null) {
					List<SpawnGroup> groups = new ArrayList<>(spawnerPopulator.getSpawnSheet().getGroups().values());
					RandomProbabilityCollection<SpawnGroup> spawnerProbCol = new RandomProbabilityCollection<>(groups);
					SpawnGroup spawnGroup = (SpawnGroup) spawnerProbCol.next();
					spawnerPopulator.populate(random, spawner, spawnGroup);
				}				
			}
		}
	}

	// TODO load the chest sheet into categories ???
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeons2.style.IRoomDecorator#decorate(net.minecraft.world.World, java.util.Random, com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider, com.someguyssoftware.dungeons2.model.Room, com.someguyssoftware.dungeons2.model.LevelConfig)
	 */
	@Deprecated
	@Override
	public void decorate(World world, Random random, IDungeonsBlockProvider provider, Room room, LevelConfig config) {
		List<Entry<DesignElement, ICoords>> surfaceAirZone = room.getFloorMap().entries().stream().filter(x -> x.getKey().getFamily() == DesignElement.SURFACE_AIR)
				.collect(Collectors.toList());			
		if (surfaceAirZone == null || surfaceAirZone.size() == 0) return;

		List<Entry<DesignElement, ICoords>> wallZone = null;
		List<Entry<DesignElement, ICoords>> floorZone = null;

		// decorate enabled
		if (config.isDecorationsOn()) {
			// TODO these methods could be reduced to more generic methods
			
			/*
			 * webs
			 */
//			addWebs(world, random, provider, room, surfaceAirZone, config);
			addBlock(world, random, provider, room, surfaceAirZone, 
					new IBlockState[] {Blocks.WEB.getDefaultState()}, config.getWebFrequency(), config.getNumberOfWebs(), config);

			/*
			 * all-over decorations: moss, lichen, lichen2, mold
			 */
//			addAnywhereDecoration(world, random, provider, room, surfaceAirZone, config);

			// get the walls only (from the air zone)
			wallZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.WALL_AIR).collect(Collectors.toList());

			/*
			 * vines
			 */
			addVines(world, random, provider, room, wallZone, config);

			/*
			 * wall blood
			 */
//			addBlood(world, random, provider, room, wallZone, config);
			
			// get the floor only (from the air zone)
			floorZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.FLOOR_AIR).collect(Collectors.toList());

			/*
			 * grass
			 */
			addGrass(world, random, provider, room, floorZone, config);

			// TODO change addGrass to addDirtSupportBlock() which will pass into grasses, mushrooms
			/*
			 * floor blood
			 */
//			addBlood(world, random, provider, room, floorZone, config);
			
			/*
			 * puddles
			 */
			addPuddles(world, random, provider, room, floorZone, config);
			
			/*
			 * add water (above the ceiling block so that there are drips coming down... maybe should be above puddles?
			 */
			
			// TODO add roots

			// TODO add debris

		}

		/*
		 * chest
		 */
		if (ModConfig.enableChests) {
		ICoords chestCoords = addChest(world, random, provider, room, floorZone, config);
			if (chestCoords != null) {
				if (Dungeons2.log.isDebugEnabled()) {
					Dungeons2.log.debug("Added chest block @ " + chestCoords.toShortString());
				}
				// get the chest inventory
				IInventory inventory = this.chestPopulator.getChestTileEntity(world, chestCoords);
//				Dungeons2.log.debug("Checking for chest tile entity...");
				if (inventory == null) {
					Dungeons2.log.debug("Manually adding chest tile entity.");
					world.setTileEntity(chestCoords.toPos(), new TileEntityChest());
					inventory = (TileEntityChest) world.getTileEntity(chestCoords.toPos());
				}
				if (inventory != null) {
					String chestCategory = config.getChestCategories().get(random.nextInt(config.getChestCategories().size()));
//					Dungeons2.log.debug("Chest category:" + chestCategory);
					// get chests by category and choose one
					List<ChestContainer> containers = (List<ChestContainer>) chestPopulator.getMap().get(chestCategory);
//					Dungeons2.log.debug("Containers found:" + containers.size());
					if (containers != null && !containers.isEmpty()) {
						// add each container to the random prob collection
						RandomProbabilityCollection<ChestContainer> chestProbCol = new RandomProbabilityCollection<>(containers);
						// select a container
						ChestContainer chest = (ChestContainer) chestProbCol.next();
	//					ChestContainer chest = containers.get(random.nextInt(containers.size()));
						// populate the chest with items from the selected chest sheet container
						chestPopulator.populate(random, inventory, chest);
					}
				}
				else {
					Dungeons2.log.debug("Chest tile entity not found... removing chest block.");
					world.setBlockState(chestCoords.toPos(), Blocks.AIR.getDefaultState());
				}
			}
		}

		/*
		 * determine if the rroom should get a spawner and what kind (boss, one-time, vanilla) etc
		 */
		if (ModConfig.enableSpawners) {
			ICoords spawnerCoords = addSpawner(world, random, provider, room, floorZone, config);
			if (spawnerCoords != null) {
				Dungeons2.log.debug("Adding spawner @ " + spawnerCoords.toShortString());
				// get the spawner tile entity
				TileEntityMobSpawner spawner = (TileEntityMobSpawner) world.getTileEntity(spawnerCoords.toPos());
				if (spawner != null) {
					List<SpawnGroup> groups = new ArrayList<>(spawnerPopulator.getSpawnSheet().getGroups().values());
					RandomProbabilityCollection<SpawnGroup> spawnerProbCol = new RandomProbabilityCollection<>(groups);
					SpawnGroup spawnGroup = (SpawnGroup) spawnerProbCol.next();
					spawnerPopulator.populate(random, spawner, spawnGroup);
				}				
			}
		}

	}


	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param airZone
	 * @param config
	 */
	@SuppressWarnings("incomplete-switch")
	protected void addAnywhereDecoration(World world, Random random, IDungeonsBlockProvider provider, Room room,
			List<Entry<DesignElement, ICoords>> airZone, LevelConfig config) {

		// for the number of webs to attempt to generate
		double freq = RandomHelper.randomDouble(random, config.getAnywhereDecorationFrequency().getMin(), config.getAnywhereDecorationFrequency().getMax());
		for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, config.getNumberOfAnywhereDecorations().getMinInt(), config.getNumberOfAnywhereDecorations().getMaxInt()), config); i++) {
			double n = random.nextDouble() * 100;
			int b = random.nextInt(5);
			Block block = null;
//			switch (b) {
//			case 0:
//				block = Dungeons2.moss;
//				break;
//			case 1:
//				block = Dungeons2.moss2;
//				break;
//			case 2:
//				block = Dungeons2.lichen;
//				break;
//			case 3:
//				block = Dungeons2.lichen2;
//				break;
//			case 4:
//				block = Dungeons2.mold1;
//				break;
//			default:
//				block = Dungeons2.moss;
//			}

			if (n < freq && airZone.size() > 0) {
				// select ANY surface air spot
				int airZoneIndex = random.nextInt(airZone.size());
				Entry<DesignElement, ICoords> entry = airZone.get(airZoneIndex);
				DesignElement elem = airZone.get(airZoneIndex).getKey();
				ICoords coords = entry.getValue();
				Location location = provider.getLocation(coords, room, room.getLayout());
				IBlockState blockState = null;
				// check if the adjoining block exists
				if (hasSupport(world, coords, elem, location)) {
					// orient vines
					switch(elem) {
					case FLOOR_AIR:
						blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.UP);
						break;
					case WALL_AIR:
						switch(location) {
						case NORTH_SIDE:
							blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.SOUTH);
							break;
						case EAST_SIDE:
							blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.WEST);
							break;
						case SOUTH_SIDE:
							blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.NORTH);
							break;
						case WEST_SIDE:
							blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.EAST);
							break;
						}
						break;
					case CEILING_AIR:
						blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.DOWN);
						break;
					}
					// update the world
					if (blockState != null) {
						world.setBlockState(coords.toPos(), blockState, 3);	
						// remove location from airZone
						airZone.remove(entry);
					}
				}
			}
		}			
	}

	@SuppressWarnings("incomplete-switch")
	protected void addBlood(World world, Random random, IDungeonsBlockProvider provider, Room room,
			List<Entry<DesignElement, ICoords>> zone, LevelConfig config) {

		// for the number of blood items to attempt to generate
		double freq = RandomHelper.randomDouble(random, config.getBloodFrequency().getMin(), config.getBloodFrequency().getMax());
		for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, config.getNumberOfBlood().getMinInt(), config.getNumberOfBlood().getMaxInt()), config); i++) {
			double n = random.nextDouble() * 100;
//			Block block = Dungeons2.blood;
			Block block = null;

			if (n < freq && zone.size() > 0) {
				// select ANY surface air spot
				int airZoneIndex = random.nextInt(zone.size());
				Entry<DesignElement, ICoords> entry = zone.get(airZoneIndex);
				DesignElement elem = zone.get(airZoneIndex).getKey();
				ICoords coords = entry.getValue();
				Location location = provider.getLocation(coords, room, room.getLayout());
				IBlockState blockState = null;
				// check if the adjoining block exists
				if (hasSupport(world, coords, elem, location)) {
					// orient vines
					switch(elem) {
					case FLOOR_AIR:
						blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.UP);
						break;
					case WALL_AIR:
						switch(location) {
						case NORTH_SIDE:
							blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.SOUTH);
							break;
						case EAST_SIDE:
							blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.WEST);
							break;
						case SOUTH_SIDE:
							blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.NORTH);
							break;
						case WEST_SIDE:
							blockState = block.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.EAST);
							break;
						}
						break;
					}
					// update the world
					if (blockState != null) {
						world.setBlockState(coords.toPos(), blockState, 3);	
						// remove location from airZone
						zone.remove(entry);
					}
				}
			}
		}			
	}
	/**
	 * 
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param zone
	 * @param config
	 */
	protected void addWebs(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> zone, LevelConfig config) {

		// for the number of webs to attempt to generate
		double freq = RandomHelper.randomDouble(random, config.getWebFrequency().getMin(), config.getWebFrequency().getMax());
		for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, config.getNumberOfWebs().getMinInt(), config.getNumberOfWebs().getMaxInt()), config); i++) {
			double n = random.nextDouble() * 100;
			if (n < freq && zone.size() > 0) {
				// select ANY surface air spot
				int zoneIndex = random.nextInt(zone.size());
				Entry<DesignElement, ICoords> entry = zone.get(zoneIndex);
				DesignElement elem = zone.get(zoneIndex).getKey();
				ICoords webCoords = entry.getValue();
				// check if the adjoining block exists
				if (hasSupport(world, webCoords, elem, provider.getLocation(webCoords, room, room.getLayout()))) {
					// update the world
					world.setBlockState(webCoords.toPos(), Blocks.WEB.getDefaultState(), 3);	
					// remove location from airZone
					zone.remove(entry);
				}
			}
		}		
	}

	protected void addVines(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> zone, ILevelConfig config) {

		double freq = RandomHelper.randomDouble(random, config.getVineFrequency().getMin(), config.getVineFrequency().getMax());
		//Dungeons2.log.debug("Vine Freq:" + freq);
		for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, config.getNumberOfVines().getMinInt(), config.getNumberOfVines().getMaxInt()), config); i++) {
			double n = random.nextDouble() * 100;
			//Dungeons2.log.debug("Vine n:" + n);
			if (n < freq && zone.size() > 0) {
				int wallZoneIndex = random.nextInt(zone.size());
				DesignElement elem = zone.get(wallZoneIndex).getKey();
				ICoords vineCoords = zone.get(wallZoneIndex).getValue();
				if (hasSupport(world, vineCoords, elem, provider.getLocation(vineCoords, room, room.getLayout()))) {
					// orient vines
					Location location = provider.getLocation(vineCoords, room, room.getLayout());
					Direction d = provider.getDirection(vineCoords, room, DesignElement.WALL_AIR, location);
					// rotate vines
					IBlockState blockState = RotatorHelper.rotateBlock(Blocks.VINE.getDefaultState(), d);				
					// update the world
					world.setBlockState(vineCoords.toPos(), blockState, 3);
					// remove location from wallZone
					zone.remove(wallZoneIndex);		
				}			
			}
		}
	}
	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param zone
	 * @param config
	 */
	@Deprecated
	protected void addVines(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> zone, LevelConfig config) {

		double freq = RandomHelper.randomDouble(random, config.getVineFrequency().getMin(), config.getVineFrequency().getMax());
		//Dungeons2.log.debug("Vine Freq:" + freq);
		for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, config.getNumberOfVines().getMinInt(), config.getNumberOfVines().getMaxInt()), config); i++) {
			double n = random.nextDouble() * 100;
			//Dungeons2.log.debug("Vine n:" + n);
			if (n < freq && zone.size() > 0) {
				int wallZoneIndex = random.nextInt(zone.size());
				DesignElement elem = zone.get(wallZoneIndex).getKey();
				ICoords vineCoords = zone.get(wallZoneIndex).getValue();
				if (hasSupport(world, vineCoords, elem, provider.getLocation(vineCoords, room, room.getLayout()))) {
					// orient vines
					Location location = provider.getLocation(vineCoords, room, room.getLayout());
					Direction d = provider.getDirection(vineCoords, room, DesignElement.WALL_AIR, location);
					// rotate vines
					IBlockState blockState = RotatorHelper.rotateBlock(Blocks.VINE.getDefaultState(), d);				
					// update the world
					world.setBlockState(vineCoords.toPos(), blockState, 3);
					// remove location from wallZone
					zone.remove(wallZoneIndex);		
				}			
			}
		}
	}

	protected void addGrass(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> floorZone, ILevelConfig config) {

		double freq = RandomHelper.randomDouble(random, config.getWebFrequency().getMin(), config.getWebFrequency().getMax());
		//Dungeons2.log.debug("Grass Freq:" + freq);
		for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, config.getNumberOfWebs().getMinInt(), config.getNumberOfWebs().getMaxInt()), config); i++) {
			double n = random.nextDouble() * 100;
			//Dungeons2.log.debug("Grass n:" + n);			
			if (n < freq && floorZone.size() > 0) {
				
				// select a grass/mushroom
				int b = random.nextInt(5);
				IBlockState plantBlockState = null;
				IBlockState groundBlockState = null;
				switch (b) {
				case 0:
					plantBlockState = Blocks.TALLGRASS.getDefaultState().withProperty(GRASSTYPE, BlockTallGrass.EnumType.GRASS);
					break;
				case 1:
					plantBlockState = Blocks.TALLGRASS.getDefaultState().withProperty(GRASSTYPE, BlockTallGrass.EnumType.DEAD_BUSH);
					break;
				case 2:
					plantBlockState = Blocks.TALLGRASS.getDefaultState().withProperty(GRASSTYPE, BlockTallGrass.EnumType.FERN);
					break;
				case 3:
					plantBlockState = Blocks.BROWN_MUSHROOM.getDefaultState();
					break;
				case 4:
					plantBlockState = Blocks.RED_MUSHROOM.getDefaultState();
					break;
				default:
					plantBlockState = Blocks.TALLGRASS.getDefaultState().withProperty(GRASSTYPE, BlockTallGrass.EnumType.GRASS);
				}
				if (b < 3) groundBlockState = Blocks.DIRT.getDefaultState();
				else groundBlockState = Blocks.DIRT.getDefaultState().withProperty(BlockDirt.VARIANT, DirtType.PODZOL);
				
				// select ANY surface air spot
				int floorZoneIndex = random.nextInt(floorZone.size());
				DesignElement elem = floorZone.get(floorZoneIndex).getKey();
				ICoords grassCoords = floorZone.get(floorZoneIndex).getValue();
				//Dungeons2.log.debug("Grass Coords:" + grassCoords.toShortString());				
				if (hasSupport(world, grassCoords, elem, provider.getLocation(grassCoords, room, room.getLayout()))) {
					// update the block below with dirt
					world.setBlockState(grassCoords.toPos().add(0, -1, 0), groundBlockState, 3);
					// update the world
					world.setBlockState(grassCoords.toPos(), plantBlockState, 3);	
					// remove location from airZone
					floorZone.remove(elem);
				}
			}
		}	
	}
	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param floorZone
	 * @param config
	 */
	@Deprecated
	protected void addGrass(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> floorZone, LevelConfig config) {

		double freq = RandomHelper.randomDouble(random, config.getWebFrequency().getMin(), config.getWebFrequency().getMax());
		//Dungeons2.log.debug("Grass Freq:" + freq);
		for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, config.getNumberOfWebs().getMinInt(), config.getNumberOfWebs().getMaxInt()), config); i++) {
			double n = random.nextDouble() * 100;
			//Dungeons2.log.debug("Grass n:" + n);			
			if (n < freq && floorZone.size() > 0) {
				
				// select a grass/mushroom
				int b = random.nextInt(5);
				IBlockState plantBlockState = null;
				IBlockState groundBlockState = null;
				switch (b) {
				case 0:
					plantBlockState = Blocks.TALLGRASS.getDefaultState().withProperty(GRASSTYPE, BlockTallGrass.EnumType.GRASS);
					break;
				case 1:
					plantBlockState = Blocks.TALLGRASS.getDefaultState().withProperty(GRASSTYPE, BlockTallGrass.EnumType.DEAD_BUSH);
					break;
				case 2:
					plantBlockState = Blocks.TALLGRASS.getDefaultState().withProperty(GRASSTYPE, BlockTallGrass.EnumType.FERN);
					break;
				case 3:
					plantBlockState = Blocks.BROWN_MUSHROOM.getDefaultState();
					break;
				case 4:
					plantBlockState = Blocks.RED_MUSHROOM.getDefaultState();
					break;
				default:
					plantBlockState = Blocks.TALLGRASS.getDefaultState().withProperty(GRASSTYPE, BlockTallGrass.EnumType.GRASS);
				}
				if (b < 3) groundBlockState = Blocks.DIRT.getDefaultState();
				else groundBlockState = Blocks.DIRT.getDefaultState().withProperty(BlockDirt.VARIANT, DirtType.PODZOL);
				
				// select ANY surface air spot
				int floorZoneIndex = random.nextInt(floorZone.size());
				DesignElement elem = floorZone.get(floorZoneIndex).getKey();
				ICoords grassCoords = floorZone.get(floorZoneIndex).getValue();
				//Dungeons2.log.debug("Grass Coords:" + grassCoords.toShortString());				
				if (hasSupport(world, grassCoords, elem, provider.getLocation(grassCoords, room, room.getLayout()))) {
					// update the block below with dirt
					world.setBlockState(grassCoords.toPos().add(0, -1, 0), groundBlockState, 3);
					// update the world
					world.setBlockState(grassCoords.toPos(), plantBlockState, 3);	
					// remove location from airZone
					floorZone.remove(elem);
				}
			}
		}	
	}
	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param zone
	 * @param config
	 */
	protected void addPuddles(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> zone, LevelConfig config) {

		double freq = RandomHelper.randomDouble(random, config.getPuddleFrequency().getMin(), config.getPuddleFrequency().getMax());
		//Dungeons2.log.debug("Grass Freq:" + freq);
		for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random, config.getNumberOfPuddles().getMinInt(), config.getNumberOfPuddles().getMaxInt()), config); i++) {
			double n = random.nextDouble() * 100;
			if (n < freq && zone.size() > 0) {
				// select ANY floor air spot
				int floorZoneIndex = random.nextInt(zone.size());
				DesignElement elem = zone.get(floorZoneIndex).getKey();
				ICoords coords = zone.get(floorZoneIndex).getValue();
				//Dungeons2.log.debug("Grass Coords:" + grassCoords.toShortString());				
				if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
					// update the world
					world.setBlockState(coords.toPos(), DungeonsBlocks.PUDDLE.getDefaultState().withProperty(DecorationBlock.BASE, EnumFacing.UP), 3);
					// remove location from airZone
					zone.remove(elem);
				}
			}
		}	
	}

	protected ICoords addChest(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> floorZone, ILevelConfig config) {

		ICoords chestCoords = null;
		// determine if room should get a chest
		double freq = RandomHelper.randomDouble(random, config.getChestFrequency().getMin(), config.getChestFrequency().getMax());
//		Dungeons2.log.debug("Chest floorZone.size -> {}", floorZone.size());
//		Dungeons2.log.debug("Chest freq -> {}", freq);
		if (RandomHelper.checkProbability(random, freq) && floorZone.size() > 0) {
			int floorIndex = random.nextInt(floorZone.size());
			DesignElement elem = floorZone.get(floorIndex).getKey();
			chestCoords = floorZone.get(floorIndex).getValue();
			// determine location in room
			Location location = provider.getLocation(chestCoords, room, room.getLayout());
			if (hasSupport(world, chestCoords, elem, location)) {	
				EnumFacing facing = orientChest(location);			
				// place a chest
				world.setBlockState(chestCoords.toPos(), Blocks.CHEST.getDefaultState().withProperty(BlockHorizontal.FACING,  facing), 3);
				// remove from list
				floorZone.remove(floorIndex);
			}
			else {
				chestCoords = null;
			}
		}
		// return coords
		return chestCoords;
	}
	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param floorZone
	 * @param config
	 */
	@Deprecated
	protected ICoords addChest(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> floorZone, LevelConfig config) {

		ICoords chestCoords = null;
		// determine if room should get a chest
		double freq = RandomHelper.randomDouble(random, config.getChestFrequency().getMin(), config.getChestFrequency().getMax());
		if (random.nextDouble() * 100 < freq && floorZone.size() > 0) {
			int floorIndex = random.nextInt(floorZone.size());
			DesignElement elem = floorZone.get(floorIndex).getKey();
			chestCoords = floorZone.get(floorIndex).getValue();
			// determine location in room
			Location location = provider.getLocation(chestCoords, room, room.getLayout());
			if (hasSupport(world, chestCoords, elem, location)) {	
				EnumFacing facing = orientChest(location);			
				// place a chest
				world.setBlockState(chestCoords.toPos(), Blocks.CHEST.getDefaultState().withProperty(BlockHorizontal.FACING,  facing), 3);
				// remove from list
				floorZone.remove(floorIndex);
			}
			else {
				chestCoords = null;
			}
		}
		// return coords
		return chestCoords;
	}

	protected ICoords addSpawner(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> floorZone, ILevelConfig config) {
		ICoords spawnerCoords = null;

		// determine if room should get a chest
		double freq = RandomHelper.randomDouble(random, config.getSpawnerFrequency().getMin(), config.getSpawnerFrequency().getMax());
		if (random.nextDouble() * 100 < freq && floorZone.size() > 0) {
			int floorIndex = random.nextInt(floorZone.size());
			DesignElement elem = floorZone.get(floorIndex).getKey();
			spawnerCoords = floorZone.get(floorIndex).getValue();
			// determine location in room
			Location location = provider.getLocation(spawnerCoords, room, room.getLayout());
			if (hasSupport(world, spawnerCoords, elem, location)) {				
				// TODO how will boss room be handled... check it here or have an extended BossRoomDecorator ?
				// TODO select the spawner type from SpawnerProvider
				world.setBlockState(spawnerCoords.toPos(), Blocks.MOB_SPAWNER.getDefaultState());				
//				TileEntityMobSpawner spawner = (TileEntityMobSpawner) world.getTileEntity(spawnerCoords.toPos());
				// TODO select mob type from SpawnerProvider
//				spawner.getSpawnerBaseLogic().setEntityName("Zombie");

				// remove from list
				floorZone.remove(floorIndex);
			}
		}
		return spawnerCoords;
	}
	
	/**
	 * 
	 * @param world
	 * @param random
	 * @param provider
	 * @param room
	 * @param floorZone
	 * @param config
	 * @return
	 */
	@Deprecated
	protected ICoords addSpawner(World world, Random random, IDungeonsBlockProvider provider,
			Room room, List<Entry<DesignElement, ICoords>> floorZone, LevelConfig config) {
		ICoords spawnerCoords = null;

		// determine if room should get a chest
		double freq = RandomHelper.randomDouble(random, config.getSpawnerFrequency().getMin(), config.getSpawnerFrequency().getMax());
		if (random.nextDouble() * 100 < freq && floorZone.size() > 0) {
			int floorIndex = random.nextInt(floorZone.size());
			DesignElement elem = floorZone.get(floorIndex).getKey();
			spawnerCoords = floorZone.get(floorIndex).getValue();
			// determine location in room
			Location location = provider.getLocation(spawnerCoords, room, room.getLayout());
			if (hasSupport(world, spawnerCoords, elem, location)) {				
				// TODO how will boss room be handled... check it here or have an extended BossRoomDecorator ?
				// TODO select the spawner type from SpawnerProvider
				world.setBlockState(spawnerCoords.toPos(), Blocks.MOB_SPAWNER.getDefaultState());				
//				TileEntityMobSpawner spawner = (TileEntityMobSpawner) world.getTileEntity(spawnerCoords.toPos());
				// TODO select mob type from SpawnerProvider
//				spawner.getSpawnerBaseLogic().setEntityName("Zombie");

				// remove from list
				floorZone.remove(floorIndex);
			}
		}
		return spawnerCoords;
	}
}
