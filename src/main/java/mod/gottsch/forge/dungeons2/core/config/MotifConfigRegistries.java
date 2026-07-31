/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
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
 * Registers Dungeons2's datapack-driven motif registry. Registered onto the mod event bus from the
 * {@link Dungeons} constructor.
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public class MotifConfigRegistries {

    /**
     * Motif config registry. Entries live at
     * {@code data/dungeons2/dungeons2/motif_config/<motif>.json}.
     *
     * <p>The doubled {@code dungeons2/dungeons2} is not a mistake: a datapack registry's folder is
     * {@code data/<pack namespace>/<registry key namespace>/<registry key path>/}, and both
     * namespaces here are this mod's own id. Forge does not permit a mod to register a custom
     * registry under any namespace but its own, so every mod-defined datapack registry looks like
     * this. Compare {@link DungeonGenerationConfigRegistries}, which has the same shape.</p>
     */
    public static final ResourceKey<Registry<MotifConfig>> MOTIF_CONFIG =
            ResourceKey.createRegistryKey(new ResourceLocation(Dungeons.MOD_ID, "motif_config"));

    @SubscribeEvent
    public static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(MOTIF_CONFIG, MotifConfig.CODEC, MotifConfig.CODEC);
    }
}
