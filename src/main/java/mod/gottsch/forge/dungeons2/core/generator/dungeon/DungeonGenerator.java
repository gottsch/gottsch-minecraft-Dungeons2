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
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import mod.gottsch.forge.dungeons2.core.generator.GeneratorData;
import mod.gottsch.forge.dungeons2.core.generator.GeneratorResult;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * <strong>Legacy / stubbed.</strong> The original 1.12.2-style orchestrator
 * that ran the maze planner and then called the {@code Basic*Generator}
 * builders directly against {@code ServerLevel}.
 *
 * <p>Replaced by:</p>
 * <ul>
 *     <li>{@code DungeonStackPlanner} (Phase 1) for layout planning</li>
 *     <li>The {@code Basic*Generator#build(...)} family (Phase 2) for
 *         producing {@code BlockPlacement} data</li>
 *     <li>{@code DungeonStructure} + {@code StructurePiece} subclasses
 *         (Phase 3+) for vanilla chunk-safe rendering</li>
 * </ul>
 *
 * <p>Kept as a compile-time stub so the rest of the module still builds.
 * Phase 6 will delete this class along with {@code DungeonFeature} and the
 * deferred-block-entity workaround.</p>
 *
 * @author Mark Gottschling on Oct Nov 14, 2023 (Phase 2 stubbed May 25, 2026)
 */
public class DungeonGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DungeonGenerator.class);

    /**
     * <strong>Legacy entry point &mdash; no longer functional.</strong>
     * Always returns {@link Optional#empty()}. New worldgen goes through
     * the Structure / StructurePiece system; see the Phase 6 milestone.
     */
    public Optional<GeneratorResult<GeneratorData>> generate(ServerLevel level, RandomSource random, ICoords spawnCoords) {
        LOGGER.debug("DungeonGenerator.generate() is a no-op stub; use DungeonStructure (Phase 3+) instead");
        return Optional.empty();
    }
}
