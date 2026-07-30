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
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/**
 * Static lookup helpers for the {@link FloorPatternConfigRegistries#FLOOR_PATTERN_CONFIG}
 * datapack registry.
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public class FloorPatternConfigHelper {

    /** The single shipped entry: {@code data/dungeons2/dungeons2/floor_pattern_config/default.json}. */
    public static final ResourceLocation DEFAULT_ID = new ResourceLocation(Dungeons.MOD_ID, "default");

    private FloorPatternConfigHelper() {}

    /**
     * Looks up the {@code default} floor-pattern config, returning
     * {@link FloorPatternConfig#DEFAULT} when no entry (or no registry) is present so callers
     * never deal with null.
     */
    public static FloorPatternConfig get(RegistryAccess registryAccess) {
        return registryAccess.registry(FloorPatternConfigRegistries.FLOOR_PATTERN_CONFIG)
                .map(registry -> registry.get(DEFAULT_ID))
                .map(config -> config != null ? config : FloorPatternConfig.DEFAULT)
                .orElse(FloorPatternConfig.DEFAULT);
    }
}
