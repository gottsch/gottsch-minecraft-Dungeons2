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
package mod.gottsch.forge.dungeons2.core.block.entity;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks;
import mod.gottsch.forge.dungeons2.core.registry.DimensionalGeneratedRegistry;
import mod.gottsch.forge.dungeons2.core.registry.GeneratedRegistry;
import mod.gottsch.forge.dungeons2.core.registry.support.IGeneratedContext;
import mod.gottsch.forge.gottschcore.enums.IRarity;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import mod.gottsch.forge.gottschcore.world.WorldInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 *
 * @author Mark Gottschling on Oct 25, 2023
 *
 */
public class DeferredDungeonGeneratorBlockEntity extends BlockEntity {

    private IRarity rarity;

    public DeferredDungeonGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonsBlockEntities.DEFERRED_DUNGEON_GENERATOR_ENTITY_TYPE.get(), pos, state);
    }

    public void tickServer() {
            if (getLevel().isClientSide()) {
                return;
            }

            try {
                // TODO this currenlty is hardcoded for the the wither tree generator. this should use the wither tree feature select first

                ResourceLocation dimension = WorldInfo.getDimension(getLevel());
                ICoords spawnCoords = new Coords(getBlockPos());

                // get the dungeon registry
                GeneratedRegistry<IGeneratedContext> registry = DimensionalGeneratedRegistry.getGeneratedRegistry(dimension);
                if (registry == null) {
                    Dungeons.LOGGER.debug("Error, no registry for dimension -> {}", dimension);
                    return;
                }

                // get the generator config
                ChestFeaturesConfiguration config = Config.chestConfig;
                if (config == null) {
                    Dungeons.LOGGER.debug("ChestConfiguration is null. This shouldn't be.");
                    failAndPlacehold((ServerLevel)getLevel(), chestCache, rarity, spawnCoords, FEATURE_TYPE);
                    return;
                }

                ChestFeaturesConfiguration.Generator generatorConfig = config.getGenerator(FEATURE_TYPE.getName());
                if (generatorConfig == null) {
                    Dungeons.LOGGER.warn("unable to locate a config for feature type -> {}.", FEATURE_TYPE.getName());
                    failAndPlacehold((ServerLevel)getLevel(), chestCache, rarity, spawnCoords, FEATURE_TYPE);
                    return;
                }

                IFeatureGenerator featureGenerator = TreasureFeatureGenerators.WITHER_FEATURE_GENERATOR;
                Dungeons.LOGGER.debug("feature generator -> {}", featureGenerator.getClass().getSimpleName());

                Optional<ChestFeaturesConfiguration.ChestRarity> rarityConfig = generatorConfig.getRarity(rarity);
                if (!rarityConfig.isPresent()) {
                    Dungeons.LOGGER.warn("unable to locate rarity config for rarity - >{}", rarity);
                    failAndPlacehold((ServerLevel)getLevel(), chestCache, rarity, spawnCoords, FEATURE_TYPE);
                    return;
                }
                // call generate
                Optional<GeneratorResult<ChestGeneratorData>> result =
                        featureGenerator.generate(
                                new FeatureGenContext(
                                    (ServerLevel)getLevel(),
                                    ((ServerLevel)getLevel()).getChunkSource().getGenerator(),
                                    getLevel().getRandom(),
                                    FEATURE_TYPE),
                                spawnCoords, rarity, rarityConfig.get());

                if (result.isPresent()) {
                    cacheGeneratedChest((ServerLevel)getLevel(), rarity, FEATURE_TYPE, chestCache, result.get());
                    updateChestGeneratorRegistry(dimension, rarity, FEATURE_TYPE);
                } else {
                    failAndPlaceholdChest((ServerLevel)getLevel(), chestCache, rarity, spawnCoords, FEATURE_TYPE);
                    return;
                }

                // save world data
                TreasureSavedData savedData = TreasureSavedData.get(getLevel());
                if (savedData != null) {
                    savedData.setDirty();
                }

            } catch(Exception e) {
                Dungeons.LOGGER.error("unable to generate wither tree", e);
            } finally {
                if (getLevel().getBlockState(getBlockPos()).getBlock() == DungeonsBlocks.DEFERRED_DUNGEON_GENERATOR.get()) {
                    getLevel().setBlock(getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

    @Override
    public void saveAdditional(CompoundTag tag) {
//        tag.putString("rarity", getRarity().getName());
        super.saveAdditional(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        try {
//            Optional<IRarity> rarity = Optional.empty();
//            if (tag.contains("rarity")) {
//                rarity = TreasureApi.getRarity(tag.getString("rarity"));
//            }
//
//            setRarity(rarity.orElse(Rarity.NONE));
        } catch (Exception e) {
            Dungeons.LOGGER.error("error reading to tag:", e);
        }
    }

    public boolean failAndPlacehold(WorldGenLevel genLevel, GeneratedCache<GeneratedChestContext> cache, IRarity rarity, ICoords coords, IFeatureType featureType) {
        // add placeholder
        GeneratedChestContext generatedChestContext = new GeneratedChestContext(rarity, coords, GeneratedChestContext.GeneratedType.PLACEHOLDER).withFeatureType(featureType);
        cache.cache(rarity, coords, generatedChestContext);
        // need to save on fail
        TreasureSavedData savedData = TreasureSavedData.get(genLevel.getLevel());
        if (savedData != null) {
            savedData.setDirty();
        }
        return false;
    }

//    public IRarity getRarity() {
//        return rarity;
//    }
//
//    public void setRarity(IRarity rarity) {
//        this.rarity = rarity;
//    }
}
