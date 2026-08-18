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
package mod.gottsch.forge.dungeons2.core.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The per-cell data a {@code dungeons2:chest_marker} carries. Backlog #48 step 3.
 *
 * <p>Nothing here has any runtime behaviour: the block entity exists so a <em>template</em> can
 * store these fields per marker and {@code ChestMarkerProcessor} can read them back as
 * {@code current.nbt()} during placement. By the time the world is running the marker is gone,
 * replaced by the chest it described.</p>
 *
 * <h2>The two fields, and why they are per marker</h2>
 * <ul>
 *   <li>{@link #LOOT_TABLE} &mdash; which table this chest draws. Absent means the processor's own
 *       default, so a template full of ordinary chests states nothing and one special chest states
 *       one line.</li>
 *   <li>{@link #TREASURE} &mdash; the Treasure2 opt-in (#48 step 4). Read here so the marker's
 *       shape is settled in one place, and deliberately inert until that step wires it: a template
 *       authored today can already say {@code treasure: true} and will simply get an ordinary chest
 *       until the branch exists.</li>
 * </ul>
 *
 * @author Mark Gottschling on Aug 18, 2026
 */
public class ChestMarkerBlockEntity extends BlockEntity {

    /** Which loot table this chest draws; absent leaves it to the processor. */
    public static final String LOOT_TABLE = "lootTable";
    /** Whether this chest should be a Treasure2 chest when Treasure2 is installed. */
    public static final String TREASURE = "treasure";

    private String lootTable;
    private boolean treasure;

    public ChestMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonsBlockEntities.CHEST_MARKER.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(LOOT_TABLE)) {
            this.lootTable = tag.getString(LOOT_TABLE);
        }
        if (tag.contains(TREASURE)) {
            this.treasure = tag.getBoolean(TREASURE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Written back so a marker placed and configured in a dev world survives being saved into a
        // structure .nbt -- which is the only way these fields are ever authored.
        if (lootTable != null && !lootTable.isEmpty()) {
            tag.putString(LOOT_TABLE, lootTable);
        }
        if (treasure) {
            tag.putBoolean(TREASURE, true);
        }
    }

    public String getLootTable() {
        return lootTable;
    }

    public void setLootTable(String lootTable) {
        this.lootTable = lootTable;
    }

    public boolean isTreasure() {
        return treasure;
    }

    public void setTreasure(boolean treasure) {
        this.treasure = treasure;
    }
}
