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
package mod.gottsch.neo.dungeons2.core.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/**
 * TODO port: compile stub for the NeoForge 1.21.1 port. Treasure2 has no NeoForge API build to
 * compile against yet, so the real facade (src/main/java/.../integration/TreasureIntegration.java)
 * is excluded from compilation and this stand-in reports Treasure2 as never loaded: every chest
 * marker falls back to this mod's own chest. Delete this file when the real one is restored.
 *
 * @author Mark Gottschling on 11/02/2026
 */
public final class TreasureIntegration {

    public static final String TREASURE2 = "treasure2";

    private TreasureIntegration() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static Optional<StructureTemplate.StructureBlockInfo> generateChest(
            LevelReader level, BlockPos pos, Direction facing, RandomSource random,
            ResourceLocation rarity, ResourceLocation supplementalLootTable) {
        return Optional.empty();
    }

    public static void finalizeChest(ServerLevelAccessor level, BlockPos pos) {
    }

    public static boolean isTreasureChest(BlockState state) {
        return false;
    }
}
