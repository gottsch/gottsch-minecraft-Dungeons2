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
package mod.gottsch.forge.dungeons2.core.setup;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.api.DungeonsApi;
import mod.gottsch.forge.dungeons2.core.config.Config;
import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.world.structure.StructurePieces;
import mod.gottsch.forge.gmm.core.entity.monster.Rat;
import mod.gottsch.forge.gmm.core.entity.monster.plant.Shrieker;
import mod.gottsch.forge.gmm.core.entity.monster.plant.VioletFungus;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.Arrays;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonSetup {
	/**
	 * 
	 * @param event
	 */
	public static void common(final FMLCommonSetupEvent event) {
		// add mod specific logging
		Config.instance.addRollingFileAppender(Dungeons.MOD_ID);

		// Backlog #10 diagnostic. Registration is the one thing about the mob-set spawner that
		// cannot be observed from inside the game: the block is invisible, so "registered and
		// working" and "block entity type missing" look identical, and a DeferredRegister holder
		// that never class-loads registers NOTHING with no error at all (see DungeonsBlockEntities,
		// and #40/#41 where the same trap ate the rats). This runs after registration is complete,
		// so it is the authoritative answer.
		//
		//   grep "D2-REGISTRY" run/logs/dungeons2.log
		logRegistryPresence();
//		Dungeons2Networking.register();

		// Register structure piece types. The STRUCTURE_PIECE registry is frozen
		// after bootstrap, so this must run on the synchronized work queue (Forge
		// unfreezes the vanilla registries there).
		event.enqueueWork(StructurePieces::register);

		Dungeons.LOGGER.info("common setup complete");
		Dungeons.LOGGER.debug("initializing dimensional generated registries");

		// register all motifs (doesn't have to be restricted to the enum's values)
		Arrays.stream(DungeonMotif.values()).sequential().forEach(DungeonsApi::registerMotif);


		// Block palettes are datapack-driven: one MotifConfig per motif, loaded by
		// Forge's datapack-registry machinery (see MotifConfigRegistries). Nothing to
		// register here -- the string->enum PatternRegistry indirection this used to
		// need went away with the block_provider system it served.
	}

	/**
	 * Attributes for the dungeon's mobs (backlog #40 / #41).
	 *
	 * <p>The rat uses GMM's own {@code createAttributes}; the giant rat has its own, because it is
	 * the same class registered at a different size (see {@link DungeonsEntities}).</p>
	 */
	@SubscribeEvent
	public static void onAttributeCreate(EntityAttributeCreationEvent event) {
		event.put(DungeonsEntities.RAT_ENTITY.get(), Rat.createAttributes().build());
		event.put(DungeonsEntities.GIANT_RAT_ENTITY.get(),
				DungeonsEntities.createGiantRatAttributes().build());
		event.put(DungeonsEntities.SHRIEKER_ENTITY.get(), Shrieker.createAttributes().build());
		event.put(DungeonsEntities.VIOLET_FUNGUS_ENTITY.get(), VioletFungus.createAttributes().build());
	}

	/**
	 * Where a rat is <em>allowed</em> to stand if something spawns one &mdash; not what causes one to
	 * spawn.
	 *
	 * <p>These two are separate mechanisms and it is easy to conflate them: this event only says
	 * "on the ground, at a position the heightmap agrees is solid, subject to the usual mob rules".
	 * What actually makes rats appear in a dungeon is the structure's {@code spawn_overrides}
	 * (backlog #42), which is still empty &mdash; so as of this commit the rats exist, render and can
	 * be spawn-egged, but nothing spawns them naturally.</p>
	 *
	 * <p>{@code Mob::checkMobSpawnRules} rather than the monster variant, matching the other GMM
	 * consumers: the monster rules add a light-level test, and a structure-scoped spawn should be
	 * governed by the structure, not by whether the player happens to have lit the room.</p>
	 */
	@SubscribeEvent
	public static void onRegisterSpawnPlacements(SpawnPlacementRegisterEvent event) {
		event.register(DungeonsEntities.RAT_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.GIANT_RAT_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		// Registered for the same reason the rats' are -- this says where one is ALLOWED to stand,
		// not what spawns one. Nothing spawns these naturally: the weathering pass places them
		// directly (FungusGrowth), which bypasses spawn placement entirely. The registration still
		// earns its keep for a spawn egg and for /summon.
		event.register(DungeonsEntities.SHRIEKER_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
		event.register(DungeonsEntities.VIOLET_FUNGUS_ENTITY.get(), SpawnPlacements.Type.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
				SpawnPlacementRegisterEvent.Operation.OR);
	}

	/**
	 * Reports whether #10's block, its block entity type and its marker are actually in the game's
	 * registries. Logged at INFO deliberately -- a missing block entity type is a hard fault, not a
	 * detail, and this is the line that says so without needing debug logging turned on.
	 */
	private static void logRegistryPresence() {
		java.util.List<String> report = new java.util.ArrayList<>();
		for (String name : java.util.List.of("mob_set_spawner", "spawner_marker")) {
			net.minecraft.resources.ResourceLocation id =
					new net.minecraft.resources.ResourceLocation(Dungeons.MOD_ID, name);
			report.add(name + ": block="
					+ net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id));
		}
		net.minecraft.resources.ResourceLocation spawner =
				new net.minecraft.resources.ResourceLocation(Dungeons.MOD_ID, "mob_set_spawner");
		report.add("mob_set_spawner: blockEntityType="
				+ net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(spawner));
		Dungeons.LOGGER.info("[D2-REGISTRY] {}", String.join("  |  ", report));
	}
}
