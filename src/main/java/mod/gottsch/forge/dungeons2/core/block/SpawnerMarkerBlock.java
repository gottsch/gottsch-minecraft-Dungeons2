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
package mod.gottsch.forge.dungeons2.core.block;

import mod.gottsch.forge.dungeons2.core.block.entity.SpawnerMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The authoring marker a template places where a spawner should go. Backlog #10, given a block
 * entity on 2026-09-03.
 *
 * <h2>Why it is a marker and not the spawner itself</h2>
 * <p>Two independent reasons, and the second is the one that bites.</p>
 *
 * <p>First, it must survive jigsaw placement, which a DATA structure block does not &mdash;
 * {@code SinglePoolElement.getSettings} installs {@code BlockIgnoreProcessor.STRUCTURE_BLOCK} before
 * the pool's own processors, and that <em>removes</em> the block rather than replacing it. That is
 * the reason this class has always existed; see {@code SpawnerMarkerProcessor}.</p>
 *
 * <p>Second, <strong>a live {@code mob_set_spawner} cannot be authored by hand at all.</strong>
 * GottschCore's {@code ProximityMobSetSpawnerBlockEntity.tickServer} fires on the first tick a
 * player is inside {@code proximity} and then calls {@code selfDestruct()}, which sets the cell to
 * air and drops the block entity &mdash; unconditionally, even when the mob set resolves to nothing.
 * So a spawner placed with {@code /setblock} while you stand next to it is gone before you can look
 * at it, and one placed further away dies as you walk over to save the structure. The marker is
 * inert, so it survives to be saved into the {@code .nbt}, and only becomes a spawner at placement
 * in a real dungeon.</p>
 *
 * <h2>Visible and solid, deliberately</h2>
 * <p>{@link BaseEntityBlock} defaults {@code getRenderShape} to {@code INVISIBLE}, which is right
 * for the spawner it becomes and wrong for a marker: an author has to see what they put where.
 * Overridden back to {@code MODEL}, and left solid so nothing replaces it before the processor
 * runs. No {@code FACING} &mdash; a spawner has no orientation, so the property would look
 * meaningful and do nothing, which is the same call {@code PotMarkerBlock} made.</p>
 *
 * @author Mark Gottschling on Sep 3, 2026
 */
public class SpawnerMarkerBlock extends BaseEntityBlock {

    public SpawnerMarkerBlock(Properties properties) {
        super(properties);
    }

    /** See the class note: {@code BaseEntityBlock} would otherwise hide the marker from its author. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerMarkerBlockEntity(pos, state);
    }
}
