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

import com.mojang.serialization.Codec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.DecorationSweepProcessor;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.SupportSweepProcessor;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.SpawnerMarkerProcessor;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.SurfaceAgingProcessor;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.AgingProcessor;
import mod.gottsch.forge.gottschcore.world.gen.structure.templatesystem.DecorationProcessor;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
public class Registration {
	/*
	 * deferred registries
	 */
	public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Dungeons.MOD_ID);
	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Dungeons.MOD_ID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Dungeons.MOD_ID);

	public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Dungeons.MOD_ID);
	public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Dungeons.MOD_ID);

	/*
	 * structure types (vanilla registry, keyed by ResourceKey)
	 */
	public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
			DeferredRegister.create(Registries.STRUCTURE_TYPE, Dungeons.MOD_ID);

	/** The dungeon structure type. Its codec is referenced by the Phase 5 structure JSON. */
	public static final RegistryObject<StructureType<DungeonStructure>> DUNGEON =
			STRUCTURE_TYPES.register("dungeon", () -> () -> DungeonStructure.CODEC);

	/*
	 * structure processor types (vanilla registry) -- referenced by
	 * worldgen/processor_list JSONs via their "processor_type" field.
	 */
	public static final DeferredRegister<StructureProcessorType<?>> STRUCTURE_PROCESSORS =
			DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, Dungeons.MOD_ID);

	/*
	 * Both processors live in GottschCore, which deliberately registers no
	 * StructureProcessorType of its own -- most mods depending on it never touch structure
	 * processors. Each consuming mod registers its own type under its own namespace and
	 * binds it into the codec via a Supplier, which keeps the codec<->type circle lazy: the
	 * codec only asks for the type when an instance is serialized, long after registration.
	 *
	 * The registered names are the "processor_type" values authored in our
	 * worldgen/processor_list JSONs, so they stay dungeons2:aging / dungeons2:decoration --
	 * the whole point of the per-mod registration model. Held as constants so the JSON's
	 * value and the registered name can be asserted equal by a test (see
	 * WeatheringProcessorListTest) rather than being two independent string literals.
	 */

	/** Registry name of {@link AgingProcessor} under this mod's namespace. */
	public static final String AGING_PROCESSOR_NAME = "aging";

	/** Registry name of {@link DecorationProcessor} under this mod's namespace. */
	public static final String DECORATION_PROCESSOR_NAME = "decoration";

	/**
	 * Multi-stage block aging that preserves state properties. See {@link AgingProcessor}.
	 *
	 * <p>The codec is built inside the registration supplier so it is created exactly once,
	 * when the type is registered, rather than per (de)serialization. Its own type supplier
	 * is qualified ({@code Registration.AGING_PROCESSOR}) because a static field cannot refer
	 * to itself by simple name from within its own initializer.</p>
	 */
	public static final RegistryObject<StructureProcessorType<AgingProcessor>> AGING_PROCESSOR =
			STRUCTURE_PROCESSORS.register(AGING_PROCESSOR_NAME, () -> {
				Codec<AgingProcessor> codec = AgingProcessor.codec(() -> Registration.AGING_PROCESSOR.get());
				return () -> codec;
			});

	/** Registry name of {@link SpawnerMarkerProcessor} under this mod's namespace. */
	public static final String SPAWNER_PROCESSOR_NAME = "spawner";

	/**
	 * Backlog #10: turns an authored {@code d2:spawner} DATA marker into the mob-set spawner.
	 * Unlike the two above, this class is Dungeons2's own -- it is specific to this mod's marker
	 * convention and its own block, so there is nothing to promote.
	 */
	public static final RegistryObject<StructureProcessorType<SpawnerMarkerProcessor>> SPAWNER_PROCESSOR =
			STRUCTURE_PROCESSORS.register(SPAWNER_PROCESSOR_NAME, () -> {
				Codec<SpawnerMarkerProcessor> codec =
						SpawnerMarkerProcessor.codec(() -> Registration.SPAWNER_PROCESSOR.get());
				return () -> codec;
			});

	/** Registry name of {@link mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.ChestMarkerProcessor}. */
	public static final String CHEST_PROCESSOR_NAME = "chest";

	/** Backlog #48 step 3: turns an authored {@code dungeons2:chest_marker} into a chest with loot. */
	public static final RegistryObject<StructureProcessorType<mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.ChestMarkerProcessor>> CHEST_PROCESSOR =
			STRUCTURE_PROCESSORS.register(CHEST_PROCESSOR_NAME, () -> {
				Codec<mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.ChestMarkerProcessor> codec =
						mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.ChestMarkerProcessor
								.codec(() -> Registration.CHEST_PROCESSOR.get());
				return () -> codec;
			});

	/** Registry name of {@link mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.PotMarkerProcessor}. */
	public static final String POT_PROCESSOR_NAME = "pot";

	/** Backlog #56: turns an authored {@code dungeons2:pot_marker} into pot entities. */
	public static final RegistryObject<StructureProcessorType<mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.PotMarkerProcessor>> POT_PROCESSOR =
			STRUCTURE_PROCESSORS.register(POT_PROCESSOR_NAME, () -> {
				Codec<mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.PotMarkerProcessor> codec =
						mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.PotMarkerProcessor
								.codec(() -> Registration.POT_PROCESSOR.get());
				return () -> codec;
			});

	/** Neighbour-aware decoration (cobwebs, clustering wall growth). See {@link DecorationProcessor}. */
	public static final RegistryObject<StructureProcessorType<DecorationProcessor>> DECORATION_PROCESSOR =
			STRUCTURE_PROCESSORS.register(DECORATION_PROCESSOR_NAME, () -> {
				Codec<DecorationProcessor> codec =
						DecorationProcessor.codec(() -> Registration.DECORATION_PROCESSOR.get());
				return () -> codec;
			});

	/** Registry name of {@link SurfaceAgingProcessor} under this mod's namespace. */
	public static final String SURFACE_AGING_PROCESSOR_NAME = "surface_aging";

	/**
	 * Surface-scoped block aging -- the mud stratum's own, so a cobble floor can wear on a
	 * different schedule from the mud-brick walls above it. See {@link SurfaceAgingProcessor},
	 * and note it must OWN a list's aging rather than sit beside a dungeons2:aging entry.
	 */
	public static final RegistryObject<StructureProcessorType<SurfaceAgingProcessor>> SURFACE_AGING_PROCESSOR =
			STRUCTURE_PROCESSORS.register(SURFACE_AGING_PROCESSOR_NAME, () -> {
				Codec<SurfaceAgingProcessor> codec =
						SurfaceAgingProcessor.codec(() -> Registration.SURFACE_AGING_PROCESSOR.get());
				return () -> codec;
			});

	/** Registry name of {@link DecorationSweepProcessor} under this mod's namespace. */
	public static final String DECORATION_SWEEP_PROCESSOR_NAME = "decoration_sweep";

	/**
	 * Clears decoration a later piece's blocks would strand at a shared wall. Dungeons2's own,
	 * like the two marker processors: the defect it repairs is a consequence of this mod's
	 * shared-wall + render-order rules (#18), not of anything in the template system.
	 * It must sit AFTER {@code dungeons2:decoration} in a processor list, or the growth it is
	 * meant to inspect has not been decided yet. See {@link DecorationSweepProcessor}.
	 */
	public static final RegistryObject<StructureProcessorType<DecorationSweepProcessor>> DECORATION_SWEEP_PROCESSOR =
			STRUCTURE_PROCESSORS.register(DECORATION_SWEEP_PROCESSOR_NAME, () -> {
				Codec<DecorationSweepProcessor> codec =
						DecorationSweepProcessor.codec(() -> Registration.DECORATION_SWEEP_PROCESSOR.get());
				return () -> codec;
			});

	/** Registry name of {@link SupportSweepProcessor} under this mod's namespace. */
	public static final String SUPPORT_SWEEP_PROCESSOR_NAME = "support_sweep";

	/**
	 * Drops any part of a piece severe weathering left with no path to the ground. Dungeons2's own
	 * for the same reason the other sweeps are: a template system places what it is told, and
	 * "what is left standing after the decay" is a question only something that sees the FINISHED
	 * piece can answer.
	 * <p>
	 * It must sit LAST in a processor list -- after every aging and decoration entry -- since it
	 * judges what those passes actually left behind. See {@link SupportSweepProcessor}, and note
	 * that support there means connectivity to the ground, not a block directly below: the naive
	 * rule deletes lintels, arches and every overhanging course.
	 */
	public static final RegistryObject<StructureProcessorType<SupportSweepProcessor>> SUPPORT_SWEEP_PROCESSOR =
			STRUCTURE_PROCESSORS.register(SUPPORT_SWEEP_PROCESSOR_NAME, () -> {
				Codec<SupportSweepProcessor> codec =
						SupportSweepProcessor.codec(() -> Registration.SUPPORT_SWEEP_PROCESSOR.get());
				return () -> codec;
			});

	/**
	 *
	 */
	public static void init() {
		// Touch the registry-holder classes so their static RegistryObject fields actually reach the
		// DeferredRegisters below. A DeferredRegister collects an entry when the field initialises,
		// which happens only when its holding class is first loaded -- so a holder nothing references
		// registers NOTHING, silently and with no error. Do this before register(eventBus).
		mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities.register();
		mod.gottsch.forge.dungeons2.core.item.DungeonsItems.register();
		// Added 2026-08-14 with #10's mob-set spawner. Both of these holders had been registering
		// nothing since they were written -- see DungeonsBlockEntities' javadoc, and note that
		// backlog #43's "do not wire these up" advice was about the dead deferred-generator entry,
		// which is now sharing a holder with a live block.
		mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks.register();
		mod.gottsch.forge.dungeons2.core.block.entity.DungeonsBlockEntities.register();
		// Floor pattern types. Its own static initializer would get there anyway -- the first use
		// of FloorPatternEntry.CODEC touches the class -- but "registered only if somebody happens
		// to load the class first" is exactly the failure mode the DeferredRegister note above is
		// about, and it is idempotent, so it is stated rather than relied upon. Registering here
		// also puts this mod's own types in before any third-party setup can claim an id.
		mod.gottsch.forge.dungeons2.core.config.floor.FloorPatternRegistry.registerBuiltIns();
		mod.gottsch.forge.dungeons2.core.config.pillar.PillarLayoutRegistry.registerBuiltIns();
		mod.gottsch.forge.dungeons2.core.config.platform.PlatformLayoutRegistry.registerBuiltIns();
		mod.gottsch.forge.dungeons2.core.config.wall.WallPatternRegistry.registerBuiltIns();
		mod.gottsch.forge.dungeons2.core.config.ceiling.CeilingPatternRegistry.registerBuiltIns();
		mod.gottsch.forge.dungeons2.core.config.pit.PitShapeRegistry.registerBuiltIns();

		IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(eventBus);
		ITEMS.register(eventBus);
		BLOCK_ENTITIES.register(eventBus);
		ENTITIES.register(eventBus);
		PARTICLES.register(eventBus);
		STRUCTURE_TYPES.register(eventBus);
		STRUCTURE_PROCESSORS.register(eventBus);

	}
}
