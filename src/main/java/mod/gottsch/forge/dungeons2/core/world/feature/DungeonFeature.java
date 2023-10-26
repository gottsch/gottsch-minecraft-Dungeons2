/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.world.feature;

import com.mojang.serialization.Codec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.persistence.DungeonsSavedData;
import mod.gottsch.forge.dungeons2.core.registry.DimensionalGeneratedRegistry;
import mod.gottsch.forge.dungeons2.core.registry.GeneratedRegistry;
import mod.gottsch.forge.dungeons2.core.registry.support.GeneratedDungeonContext;
import mod.gottsch.forge.dungeons2.core.registry.support.IGeneratedContext;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import mod.gottsch.forge.gottschcore.world.WorldInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
public class DungeonFeature extends Feature<NoneFeatureConfiguration> implements IDungeonFeature {
	private static int CHUNK_SIZE = 16;
	
	private int waitChunksCount = 0;
	
	/**
	 * 
	 * @param codec
	 */
	public DungeonFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		ServerLevel level = context.level().getLevel();
		ResourceLocation dimension = WorldInfo.getDimension(level);
		
		// test the dimension
		if (!meetsDimensionCriteria(dimension)) { 
			Dungeons.LOGGER.debug("Did not meet diemensional criteria");
			return false;
		}
		
		// get the dungeon registry
		GeneratedRegistry<IGeneratedContext> registry = DimensionalGeneratedRegistry.getGeneratedRegistry(dimension);
		if (registry == null) {
			Dungeons.LOGGER.debug("Error, no registry for dimension -> {}", dimension);
			return false;
		}
		
		// check if a dungeon is already in this chunk
		// ensures that 2 dungeons do not overlap using the max. size of a dungeon
		// TODO maybe should have the config loaded and the size decided on
		if (isChunkWithinDungeonBoundary(registry, new Coords(context.origin()))) {
			Dungeons.LOGGER.debug("chunk is within current dungeon field -> {}. Should do gen processes at this point.", context.origin());
			return false;
		}


		// the get first surface y (could be leaves, trunk, etc)
		ICoords coords = new Coords(context.origin().offset(WorldInfo.CHUNK_RADIUS - 1, 0, WorldInfo.CHUNK_RADIUS - 1));
		ICoords spawnCoords = WorldInfo.getDryLandSurfaceCoords(level, context.chunkGenerator(), coords);
		if (spawnCoords == Coords.EMPTY) {
			Dungeons.LOGGER.debug("No spawn coords  at -> {}", context.origin());
			return false;
		}

		if (!meetsAllCriteria(level, context.random(), spawnCoords, registry)) {
			Dungeons.LOGGER.debug("didn't meet criteria -> {}", spawnCoords);
			failAndPlacehold((ServerLevel)level, registry, spawnCoords);
			return false;
		}

		// TODO if not, generate dungeon layout
//		IDungeonLayout layout = DungeonGenerator.build(level, random, spawnCoords);

		// generate deferred dungeon block
		level.setBlock(spawnCoords.toPos(), DungeonsBlocks.DEFERRED_DUNGEON_GENERATOR.get().defaultBlockState(), 3);

		// register dungeon using the start and end coords of the dungeon field
		GeneratedDungeonContext generatedContext = new GeneratedDungeonContext(spawnCoords.add(-48, 0, -48), spawnCoords.add(48, 0, 48));
		
		// add a placeholder
		registry.register(generatedContext.getMinCoords(), generatedContext.getMaxCoords(), generatedContext);

		DungeonsSavedData savedData = DungeonsSavedData.get(level);
		if (savedData != null) {
			savedData.setDirty();
		}

		// TODO also have to register / store the dungeon layout somewhere - same registry?
		return true;
	}

	/**
	 *
	 * @param registry
	 * @param coords
	 * @return
	 */
	protected boolean isChunkWithinDungeonBoundary(GeneratedRegistry<IGeneratedContext> registry, Coords coords) {
//		return registry.withinArea(coords, coords.add(CHUNK_SIZE, 0, CHUNK_SIZE));
		return isRegisteredDungeonWithinDistance(null, registry, coords,96/2);
	}

	/**
	 *
	 * @param level
	 * @param random
	 * @param spawnCoords
	 * @param registry
	 * @return
	 */
	protected boolean meetsAllCriteria(ServerLevel level, RandomSource random, ICoords spawnCoords, GeneratedRegistry<IGeneratedContext> registry) {
		// test the world age
		if (!meetsWorldAgeCriteria(registry)) {
			return false;
		}
		// test if the override (global) biome is allowed
		if (!meetsBiomeCriteria(level, spawnCoords, (List<String>) Config.SERVER.dungeons.biomesWhitelist.get(), (List<String>) Config.SERVER.dungeons.biomesBlacklist.get())) {
			return false;
		}
		
		// check against all registered chests
		if (!meetsProximityCriteria(level, registry, spawnCoords, Config.SERVER.dungeons.minBlockDistance.get())) {
			Dungeons.LOGGER.debug("Didn't meet proximity criteria -> {}", spawnCoords);
			return false;
		}
		
		// check if meets the probability criteria
		if (!meetsProbabilityCriteria(random)) {
			return false;
		}
		return true;
	}

	/**
	 * 
	 * @param registry
	 * @return
	 */
	protected boolean meetsWorldAgeCriteria(GeneratedRegistry<IGeneratedContext> registry) {
		// wait count check		
		if (registry.getValues().isEmpty() && waitChunksCount < Config.SERVER.dungeons.waitChunks.get()) {
			Dungeons.LOGGER.debug("World is too young");
			this.waitChunksCount++;
			return false;
		}
		return true;
	}

	/**
	 *
	 * @param random
	 * @return
	 */
	protected boolean meetsProbabilityCriteria(RandomSource random) {
		if (!RandomHelper.checkProbability(random, Config.SERVER.dungeons.probability.get())) {
			Dungeons.LOGGER.debug("chest gen does not meet generate probability.");
			return false;
		}
		return true;
	}
	
	/**
	 * 
	 */
	@Override
	public boolean isRegisteredDungeonWithinDistance(Level world, GeneratedRegistry<IGeneratedContext> registry, 
			ICoords coords, int minDistance) {

		if (registry == null || registry.getValues().isEmpty()) {
			Dungeons.LOGGER.debug("unable to locate the GeneratedRegistry or the registry doesn't contain any values");
			return false;
		}
		
		// generate a box with coords as center and minDistance as radius
		ICoords startBox = new Coords(coords.getX() - minDistance, 0, coords.getZ() - minDistance);
		ICoords endBox = new Coords(coords.getX() + minDistance, 0, coords.getZ() + minDistance);

		// find if box overlaps anything in the registry
		if (registry.withinArea(startBox, endBox)) {
			return true;
		}
		return false;
	}

	/**
	 *
	 * @param level
	 * @param registry
	 * @param coords
	 * @return
	 */
	public boolean failAndPlacehold(ServerLevel level, GeneratedRegistry<IGeneratedContext> registry, ICoords coords) {
		// add placeholder

		// TODO add inflate to Box

		// TODO move to own method
		int minDistance = 48;
		ICoords startBox = new Coords(coords.getX() - minDistance, 0, coords.getZ() - minDistance);
		ICoords endBox = new Coords(coords.getX() + minDistance, 0, coords.getZ() + minDistance);

		GeneratedDungeonContext context = new GeneratedDungeonContext(startBox, endBox);
		registry.register(context.getMinCoords(), context.getMaxCoords(), context);
		// need to save on fail
		DungeonsSavedData savedData = DungeonsSavedData.get(level);
		if (savedData != null) {
			savedData.setDirty();
		}
		return true;
	}
}
