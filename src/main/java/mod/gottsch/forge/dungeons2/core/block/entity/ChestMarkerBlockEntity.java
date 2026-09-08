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
    /**
     * Marks this as <strong>the dungeon's boss chest</strong>, so the processor's
     * {@code boss_loot_table} decides what it holds rather than {@link #LOOT_TABLE} or the pool's
     * ordinary weighted default.
     *
     * <h2>Why an opt-in flag and not a named table</h2>
     * <p>The reward has to follow the <em>dungeon's</em> size tier, not the room's geometry: a small
     * boss room drawn into a LARGE dungeon still sits at the bottom of a five-floor descent and must
     * pay out accordingly. Naming {@code dungeons2:chests/classic_boss_small} on the marker &mdash;
     * which is exactly what {@code small_boss_1} did until this existed &mdash; welds the payout to
     * the template, so the one boss room that shipped handed out small loot at every size.</p>
     *
     * <p>The tier therefore lives on the processor entry, which is per <em>pool</em>, and the pool
     * is chosen per size ({@code end_rooms/&lt;motif&gt;/&lt;size&gt;/normal}). The marker's job is
     * only to say <em>which</em> chest is the boss's. That keeps the template tier-neutral and
     * reusable at every size.</p>
     *
     * <h2>Why a boolean rather than a sentinel id in {@link #LOOT_TABLE}</h2>
     * <p>A sentinel such as {@code dungeons2:chests/boss} sits in a field typed as a real table id
     * and reads as one. If the substitution ever failed to run, it would fall through to "no such
     * loot table" &mdash; a chest that generates empty, indistinguishable from a looted one, which
     * is the exact failure {@link ChestMarkerProcessor} refuses to produce elsewhere. A boolean
     * cannot be mistaken for a table. It also matches {@link #TREASURE}, already an opt-in flag on
     * this same marker that redirects where the contents come from.</p>
     */
    public static final String BOSS = "boss";

    private String lootTable;
    private boolean treasure;
    private boolean boss;

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
        if (tag.contains(BOSS)) {
            this.boss = tag.getBoolean(BOSS);
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
        if (boss) {
            tag.putBoolean(BOSS, true);
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

    public boolean isBoss() {
        return boss;
    }

    public void setBoss(boolean boss) {
        this.boss = boss;
    }
}
