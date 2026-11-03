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
package mod.gottsch.neo.dungeons2.core.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import org.jetbrains.annotations.Nullable;

/**
 * Spawn placement types this mod adds to vanilla's four.
 *
 * <h2>{@link #ON_GROUND_OR_LAVA}</h2>
 * <p>For the fire-immune skeletons. Mark authored {@code classic_small_fire_bones} spawners over
 * the lava channel in the {@code bridge_3} rooms, and they never spawned anything: the spawner
 * probe logged {@code placement=ON_GROUND okCells=0/125}. {@code ON_GROUND} needs a sturdy block
 * under the cell, lava is not one, and within the two cells {@code SpawnUtil} reaches there was no
 * floor. The one deepslate spawner that did have floor in reach spawned normally.</p>
 *
 * <p>So: wherever {@code ON_GROUND} would allow it, OR in an empty cell (and empty cell above, the
 * same two tests {@code ON_GROUND} makes) standing on lava. The mob drops into the lava it is immune
 * to and wades out. Not {@code IN_LAVA}, which would put it INSIDE the lava, where
 * {@code Mob#checkSpawnObstruction} refuses any box holding liquid; and not for anything that
 * burns.</p>
 *
 * <p>On 1.21 a placement type is a plain {@link SpawnPlacementType} implementation (vanilla's are
 * the constants in {@link SpawnPlacementTypes}), so nothing has to be created or registered before
 * {@code CommonSetup}'s placement registration uses it.</p>
 *
 * @author Mark Gottschling on Oct 24, 2026
 */
public final class DungeonSpawnPlacements {

    private DungeonSpawnPlacements() {}

    public static final SpawnPlacementType ON_GROUND_OR_LAVA = DungeonSpawnPlacements::onGroundOrLava;

    static boolean onGroundOrLava(LevelReader level, BlockPos pos, @Nullable EntityType<?> type) {
        return SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, pos, type)
                || overLava(level, pos, type);
    }

    /** An empty two-cell column standing on lava. */
    static boolean overLava(LevelReader level, BlockPos pos, @Nullable EntityType<?> type) {
        BlockPos above = pos.above();
        return level.getFluidState(pos.below()).is(FluidTags.LAVA)
                && NaturalSpawner.isValidEmptySpawnBlock(level, pos, level.getBlockState(pos),
                        level.getFluidState(pos), type)
                && NaturalSpawner.isValidEmptySpawnBlock(level, above, level.getBlockState(above),
                        level.getFluidState(above), type);
    }
}
