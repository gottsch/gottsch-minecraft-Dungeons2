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

import mod.gottsch.forge.dungeons2.core.block.entity.PotMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Backlog #56: the authoring marker a template places where a pot might stand.
 *
 * <h2>What it becomes</h2>
 * <p>Nothing, as a block. {@code PotMarkerProcessor} replaces the cell with air and spawns pot
 * <strong>entities</strong> in it &mdash; which is the one structural difference from
 * {@link ChestMarkerBlock}, whose processor swaps one block state for another. A pot is a
 * {@code PotEntity}, not a block, so there is no state to swap to and the work has to happen at
 * {@code finalizeProcessing} where a real {@code ServerLevelAccessor} exists.</p>
 *
 * <h2>No FACING</h2>
 * <p>The chest marker carries one so the author decides which way the chest opens. A pot has no
 * facing worth authoring &mdash; {@code RoomPropGenerator} gives every procedural pot a random
 * rotation, and this marker does the same &mdash; so the property would be a control that looked
 * meaningful and did nothing.</p>
 *
 * <h2>Visible and solid, deliberately</h2>
 * <p>Same reason as the chest marker: {@link BaseEntityBlock} defaults {@code getRenderShape} to
 * {@code INVISIBLE}, which is right for something that survives into the finished dungeon and wrong
 * for a marker its author has to see while building. Overridden back to {@code MODEL}, and left
 * solid so nothing replaces it before the processor runs.</p>
 *
 * @author Mark Gottschling on Aug 29, 2026
 */
public class PotMarkerBlock extends BaseEntityBlock {

    public PotMarkerBlock(Properties properties) {
        super(properties);
    }

    /** See the class note: {@code BaseEntityBlock} would otherwise hide the marker from its author. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PotMarkerBlockEntity(pos, state);
    }
}
