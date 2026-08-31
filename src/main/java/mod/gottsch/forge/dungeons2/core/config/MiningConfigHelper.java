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
 * Static lookup helper for the {@link DungeonGenerationConfigRegistries#MINING_CONFIG} datapack
 * registry (#7).
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
public class MiningConfigHelper {

    /** The single shipped entry: {@code data/dungeons2/dungeons2/mining_config/default.json}. */
    public static final ResourceLocation DEFAULT_ID = new ResourceLocation(Dungeons.MOD_ID, "default");

    private MiningConfigHelper() {}

    /**
     * Looks up the {@code default} mining config, falling back to {@link MiningConfig#DEFAULT} when
     * no entry (or no registry) is present so callers never deal with null.
     *
     * <p>That fallback carries an <strong>empty</strong> ore table, which yields no chest at all.
     * Deliberate, and the opposite of {@code DungeonGenerationConfigHelper}'s: a missing geometry
     * config has to produce a working dungeon, whereas a missing payout table has no honest default
     * &mdash; a hard-coded ore list in Java would be a second table competing with the datapack's,
     * and the failure it hides (a datapack that removed the file) is better seen as "no Mining
     * Chest" than as "a Mining Chest nobody authored".</p>
     */
    public static MiningConfig get(RegistryAccess registryAccess) {
        return registryAccess.registry(DungeonGenerationConfigRegistries.MINING_CONFIG)
                .map(registry -> registry.get(DEFAULT_ID))
                .map(config -> config != null ? config : MiningConfig.DEFAULT)
                .orElse(MiningConfig.DEFAULT);
    }
}
