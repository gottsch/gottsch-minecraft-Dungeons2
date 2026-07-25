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
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/**
 * Static lookup helpers for the {@link DungeonGenerationConfigRegistries#GENERATION_CONFIG}
 * datapack registry.
 *
 * @author Mark Gottschling on Jul 25, 2026
 */
public class DungeonGenerationConfigHelper {

    /** The single shipped entry: {@code data/dungeons2/dungeons2/generation_config/default.json}. */
    public static final ResourceLocation DEFAULT_ID = new ResourceLocation(Dungeons.MOD_ID, "default");

    private DungeonGenerationConfigHelper() {}

    /**
     * Looks up the {@code default} generation config, returning
     * {@link DungeonGenerationConfig#DEFAULT} when no entry (or no registry) is present so
     * callers never deal with null.
     */
    public static DungeonGenerationConfig get(RegistryAccess registryAccess) {
        return registryAccess.registry(DungeonGenerationConfigRegistries.GENERATION_CONFIG)
                .map(registry -> registry.get(DEFAULT_ID))
                .map(config -> config != null ? config : DungeonGenerationConfig.DEFAULT)
                .orElse(DungeonGenerationConfig.DEFAULT);
    }
}
