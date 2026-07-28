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
import mod.gottsch.forge.dungeons2.core.world.feature.ConfiguredFeatures;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.AgingProcessor;
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

	/** Multi-stage block aging that preserves state properties. See {@link AgingProcessor}. */
	public static final RegistryObject<StructureProcessorType<AgingProcessor>> AGING_PROCESSOR =
			STRUCTURE_PROCESSORS.register(AgingProcessor.NAME, () -> () -> AgingProcessor.CODEC);

	/**
	 *
	 */
	public static void init() {
		IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
		BLOCKS.register(eventBus);
		ITEMS.register(eventBus);
		BLOCK_ENTITIES.register(eventBus);
		ENTITIES.register(eventBus);
		PARTICLES.register(eventBus);
		STRUCTURE_TYPES.register(eventBus);
		STRUCTURE_PROCESSORS.register(eventBus);

		ConfiguredFeatures.register(eventBus);
	}
}
