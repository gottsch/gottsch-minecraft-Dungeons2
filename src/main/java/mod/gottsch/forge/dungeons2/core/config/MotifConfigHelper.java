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
 * Static lookup helper for the {@link MotifConfigRegistries#MOTIF_CONFIG} datapack registry.
 *
 * <p>Motif-scoped, same convention as {@code rooms/<motif>/normal.json} and the weathering
 * processor lists ({@code PieceProcessors#weatheringList}). A motif with no entry (or no registry,
 * or a blank motif) degrades to {@link MotifConfig#DEFAULT} so callers never deal with null.</p>
 *
 * <p>Callers resolve once where {@code RegistryAccess} is available and inject the resolved value
 * into the generator &mdash; same shape as {@code DungeonStackPlanner#withCorridorWidth}. See
 * {@code DungeonRoomPiece#postProcess}.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public class MotifConfigHelper {

    private MotifConfigHelper() {}

    public static MotifConfig get(RegistryAccess registryAccess, String motifValue) {
        if (motifValue == null || motifValue.isBlank()) {
            return MotifConfig.DEFAULT;
        }
        ResourceLocation id = new ResourceLocation(Dungeons.MOD_ID, motifValue.trim().toLowerCase(Locale.ROOT));
        return registryAccess.registry(MotifConfigRegistries.MOTIF_CONFIG)
                .map(registry -> registry.get(id))
                .map(config -> config != null ? config : MotifConfig.DEFAULT)
                .orElse(MotifConfig.DEFAULT);
    }
}
