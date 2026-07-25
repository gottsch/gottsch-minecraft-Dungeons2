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
package mod.gottsch.forge.dungeons2.core.config;

import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DataPackRegistryEvent;

/**
 * Registers Dungeons2's datapack-driven generation-tuning registry. Registered onto the mod
 * event bus from the {@link Dungeons} constructor.
 *
 * @author Mark Gottschling on Jul 25, 2026
 */
public class DungeonGenerationConfigRegistries {

    /**
     * Generation-tuning config registry. Entries live at
     * {@code data/dungeons2/dungeons2/generation_config/<name>.json}.
     */
    public static final ResourceKey<Registry<DungeonGenerationConfig>> GENERATION_CONFIG =
            ResourceKey.createRegistryKey(new ResourceLocation(Dungeons.MOD_ID, "generation_config"));

    @SubscribeEvent
    public static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(GENERATION_CONFIG, DungeonGenerationConfig.CODEC, DungeonGenerationConfig.CODEC);
    }
}
