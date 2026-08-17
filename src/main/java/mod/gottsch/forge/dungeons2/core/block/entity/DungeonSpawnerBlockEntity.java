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

import mod.gottsch.forge.gottschcore.block.entity.ProximityMobSetSpawnerBlockEntity;
import mod.gottsch.forge.gottschcore.size.IntegerRange;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * GottschCore's proximity mob-set spawner plus the one thing Dungeons2 knows that a generic spawner
 * cannot: <strong>which floor of the dungeon it is on</strong>.
 *
 * <h2>Why a subclass and not just another tag field</h2>
 * <p>{@code BlockEntityData} can carry any key, and {@code DungeonPiece.applyBlockEntity} will load
 * it &mdash; but a key the block entity has no field for is <strong>silently dropped at the next
 * save</strong>, because {@code saveAdditional} writes fields, not the tag it was loaded from. The
 * floor index would survive generation, survive until the chunk unloaded, and then be gone. A
 * spawner that fires on the player's first visit would look correct in every test and lose its depth
 * on a revisit &mdash; the exact shape of invisible failure this whole feature keeps producing.</p>
 *
 * <p>Subclassing here rather than adding the field to GottschCore keeps the change inside this repo.
 * Backlog #10's open half is a GottschCore base class designed against <em>two</em> consumers; a
 * field added now, for one consumer, would prejudge that design.</p>
 *
 * <h2>What floorIndex is for</h2>
 * <p>Nothing reads it yet. It is stored from generation so that when the Stronger Mobs Below
 * integration lands, dungeons generated before it still carry the depth their mobs should scale by.
 * Note that SMB's own axis is <strong>world Y</strong> ({@code EchelonConfigsHolder.Config
 * .getDifficulty(Integer y)} is an interval tree over Y), which is a different thing: a dungeon
 * under a mountain has its floor 3 higher than a ravine dungeon's floor 0. This field is the
 * dungeon-relative ordinal, 0 at the entrance.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
public class DungeonSpawnerBlockEntity extends ProximityMobSetSpawnerBlockEntity {

    public static final String FLOOR_INDEX = "floorIndex";

    /** Unset. Distinguishable from floor 0, which is a real and common answer. */
    public static final int UNKNOWN_FLOOR = -1;

    private int floorIndex = UNKNOWN_FLOOR;

    public DungeonSpawnerBlockEntity(Supplier<BlockEntityType<?>> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(FLOOR_INDEX)) {
            this.floorIndex = tag.getInt(FLOOR_INDEX);
        }
    }

    /**
     * <p><strong>The null guard is not defensive coding.</strong> The parent's
     * {@code saveAdditional} reads {@code getMobSizeRange().getMin()} unguarded and rethrows, so it
     * throws {@code NullPointerException} for any spawner whose range has not been set &mdash; which
     * is <em>every</em> freshly created one, since the range only arrives when a tag is loaded. That
     * is not a hypothetical: {@code DungeonPiece.applyBlockEntity} saves the entity before applying
     * data to it, and before this guard that save threw and the spawner ended up with none of its
     * configuration. Seeding the parent's own documented default (1..1, from its
     * {@code defaultMobSpawnerSettings}) makes the entity saveable at any point in its life, which
     * is what a block entity is supposed to be.</p>
     */
    @Override
    protected void saveAdditional(CompoundTag tag) {
        if (getMobSizeRange() == null) {
            setMobSizeRange(new IntegerRange(1, 1));
        }
        super.saveAdditional(tag);
        tag.putInt(FLOOR_INDEX, floorIndex);
    }

    /** Which floor of the dungeon this spawner is on, 0 at the entrance; {@link #UNKNOWN_FLOOR} if unset. */
    public int getFloorIndex() {
        return floorIndex;
    }

    public void setFloorIndex(int floorIndex) {
        this.floorIndex = floorIndex;
    }
}
