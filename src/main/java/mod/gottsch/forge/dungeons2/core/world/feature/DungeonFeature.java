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

import java.util.Random;

import com.mojang.serialization.Codec;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.registry.DimensionalGeneratedRegistry;
import mod.gottsch.forge.dungeons2.core.registry.GeneratedRegistry;
import mod.gottsch.forge.dungeons2.core.registry.support.DungeonGeneratedContext;
import mod.gottsch.forge.dungeons2.core.registry.support.IGeneratedContext;
import mod.gottsch.forge.gottschcore.random.RandomHelper;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import mod.gottsch.forge.gottschcore.world.WorldInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

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
		// TODO this will skip all other criteria tests, locate the set of rooms to generate
		// and proceed to generate for this chunk.
		if (isChunkWithinDungeonBoundary(registry, new Coords(context.origin()))) {
			Dungeons.LOGGER.debug("chunk is within current dungeon field -> {}. Should do gen processes at this point.", context.origin());
		}
		else {
			// DO everything else
		}
		
		// TODO will also have to check if any rooms are in a generated chunk that are not complete
		// and process them at this time. this will have to happen for every new chunk gen where the chunk
		// is within a current dungeon
		
		// the get first surface y (could be leaves, trunk, etc)
		// NOTE don't use offset on chunkPos ie. don't use center of chunk, use the origin
//		offset(WorldInfo.CHUNK_RADIUS - 1, 0, WorldInfo.CHUNK_RADIUS - 1)
		ICoords spawnCoords = WorldInfo.getDryLandSurfaceCoords(level, context.chunkGenerator(), new Coords(context.origin()));
		if (spawnCoords == Coords.EMPTY) {
			Dungeons.LOGGER.debug("No spawn coords  at -> {}", context.origin());
			return false;
		}

		if (!meetsAllCriteria(level, context.random(), spawnCoords, registry)) {
			Dungeons.LOGGER.debug("Didn't meet criteria -> {}", spawnCoords);
			return false;
		}

		// TODO if not, generate dungeon layout
//		IDungeonLayout layout = DungeonGenerator.build(level, random, spawnCoords);
		
		// TODO get all chunk coords that are within the dungeon field area.
		
		// TODO get all chunks for list that exist ie already generated.
		
		// TODO if % of exists > threshold, then ok placement for dungeon

		// TODO register dungeon using the start and end coords of the dungeon field
		DungeonGeneratedContext gc = new DungeonGeneratedContext(spawnCoords, spawnCoords.add(96, 0, 96));
		
		// TEMP expand coords to simulate a large dungeon (96x96)
		registry.register(gc.getMinCoords(), gc.getMaxCoords(), gc);
		
		// TODO generate dungeon chunk in world
		
		// TODO also have to register / store the dungeon layout somewhere - same registry?
		// NOTE is the dimensional generated registry ONLY for completed dungeons? dont' necessarily
		// want to store all the incomplete details needed about a dungeon here as it
		Dungeons.LOGGER.debug("This is a good chunk to gen -> {}", spawnCoords.toShortString());
		return true;
	}

	protected boolean isChunkWithinDungeonBoundary(GeneratedRegistry<IGeneratedContext> registry, Coords coords) {
		return registry.withinArea(coords, coords.add(CHUNK_SIZE, 0, CHUNK_SIZE));
	}

	protected boolean meetsAllCriteria(ServerLevel level, Random random, ICoords spawnCoords, GeneratedRegistry<IGeneratedContext> registry) {
		// test the world age
		if (!meetsWorldAgeCriteria(registry)) {
			return false;
		}
		// test if the override (global) biome is allowed
		if (!meetsBiomeCriteria(level, spawnCoords, Config.SERVER.dungeons.biomesWhitelist.get(), Config.SERVER.dungeons.biomesBlacklist.get())) {
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
	
	protected boolean meetsProbabilityCriteria(Random random) {
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
			ICoords coords,int minDistance) {

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
}
