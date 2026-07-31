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

import java.util.Locale;

/**
 * Static lookup helpers for the {@link FloorPatternConfigRegistries#FLOOR_PATTERN_CONFIG}
 * datapack registry.
 *
 * <p>Motif-scoped, same convention as {@code rooms/<motif>/normal.json}/
 * {@code transitions/<motif>/shaft_bottom.json} and the weathering processor lists
 * ({@code PieceProcessors#weatheringList}) &mdash; entries live at
 * {@code data/dungeons2/dungeons2/floor_pattern_config/<motif>.json}, one shipped entry today,
 * {@code classic}. A motif with no such entry (or no registry at all) degrades gracefully to
 * {@link FloorPatternConfig#DEFAULT} (always plain), same as an absent template pool always has
 * elsewhere in this codebase &mdash; no two-tier fallback to a shared/classic config, matching
 * the rooms/transitions motif-naming note in {@code structures/README.md}.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public class FloorPatternConfigHelper {

    private FloorPatternConfigHelper() {}

    /**
     * Looks up the floor-pattern config for {@code motifValue}, returning
     * {@link FloorPatternConfig#DEFAULT} when no entry (or no registry, or a blank motif) is
     * present so callers never deal with null.
     */
    public static FloorPatternConfig get(RegistryAccess registryAccess, String motifValue) {
        if (motifValue == null || motifValue.isBlank()) {
            return FloorPatternConfig.DEFAULT;
        }
        ResourceLocation id = new ResourceLocation(Dungeons.MOD_ID, motifValue.trim().toLowerCase(Locale.ROOT));
        return registryAccess.registry(FloorPatternConfigRegistries.FLOOR_PATTERN_CONFIG)
                .map(registry -> registry.get(id))
                .map(config -> config != null ? config : FloorPatternConfig.DEFAULT)
                .orElse(FloorPatternConfig.DEFAULT);
    }
}
