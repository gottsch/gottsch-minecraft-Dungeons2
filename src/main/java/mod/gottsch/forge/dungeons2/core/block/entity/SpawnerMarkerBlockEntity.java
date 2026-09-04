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
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The per-cell data a {@code dungeons2:spawner_marker} carries.
 *
 * <h2>This block entity is a correction, and the record should say what was wrong</h2>
 * <p>{@code SpawnerMarkerProcessor} said for a fortnight that a DATA marker could carry free text
 * and "a block cannot", and concluded that the mob set had to be a codec field on the processor
 * &mdash; one set per motif, a second set costing a second registered block. {@code ChestMarkerBlock}
 * had already disproved the premise: a structure template stores block-entity NBT <em>per cell</em>
 * and hands it to a processor as {@code current.nbt()}. The limitation was never the block, it was
 * the missing block entity.</p>
 *
 * <p>What forced the issue was a boss room: one authored template wanting its own mob set at its own
 * trigger distance, which the pool-wide codec fields cannot express no matter what they are set to.
 * The chest and pot markers had both already gone this way for exactly the same reason.</p>
 *
 * <h2>Every field is an override, and absent means "the pool's"</h2>
 * <p>None of these is required. A marker that states nothing behaves exactly as it did before this
 * class existed &mdash; it takes the {@code dungeons2:spawner} processor entry's values &mdash;
 * which is what lets the shipped templates stay untouched. State one key and only that key changes.
 * Absent-not-defaulted is load-bearing here: a marker cannot be distinguished from one that meant
 * the pool default if this class fills in numbers of its own.</p>
 *
 * <p>Nothing here has any runtime behaviour. By the time the world is running the marker is gone,
 * replaced by the spawner it described.</p>
 *
 * @author Mark Gottschling on Sep 3, 2026
 */
public class SpawnerMarkerBlockEntity extends BlockEntity {

    /**
     * The mob set this spawner draws, e.g. {@code dungeons2:small_dungeon_boss}.
     *
     * <p>Spelled the same as the tag {@code DungeonSpawnerBlockEntity} reads, on purpose: the
     * marker's key and the finished spawner's key being one string is what stops the two drifting,
     * and it is what makes the authored value readable in the same {@code /data get block} the
     * finished spawner answers.</p>
     */
    public static final String MOB_SET_NAME = "mobSetName";
    /** Trigger distance in blocks. See {@code SpawnerConfig} for why there is no default. */
    public static final String PROXIMITY = "proximity";
    /** Fewest mobs released when it fires. */
    public static final String MIN_MOBS = "minMobs";
    /** Most mobs released when it fires. */
    public static final String MAX_MOBS = "maxMobs";
    /** {@code proximity} or {@code vanilla}, matching the processor's own {@code type} field. */
    public static final String TYPE = "type";

    private String mobSetName;
    private Double proximity;
    private Integer minMobs;
    private Integer maxMobs;
    private String type;

    public SpawnerMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonsBlockEntities.SPAWNER_MARKER.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(MOB_SET_NAME)) {
            this.mobSetName = tag.getString(MOB_SET_NAME);
        }
        // TAG_ANY_NUMERIC rather than a bare contains(): an author typing `proximity:20` in a
        // /data merge writes an int, and getDouble on an IntTag reads 0 -- the same class of bug
        // BlockEntityData's javadoc records, where a proximity stored as a string read back as 0.
        // Accepting any numeric tag and converting here means the marker cannot be authored wrong.
        if (tag.contains(PROXIMITY, Tag.TAG_ANY_NUMERIC)) {
            this.proximity = tag.getDouble(PROXIMITY);
        }
        if (tag.contains(MIN_MOBS, Tag.TAG_ANY_NUMERIC)) {
            this.minMobs = tag.getInt(MIN_MOBS);
        }
        if (tag.contains(MAX_MOBS, Tag.TAG_ANY_NUMERIC)) {
            this.maxMobs = tag.getInt(MAX_MOBS);
        }
        if (tag.contains(TYPE)) {
            this.type = tag.getString(TYPE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Only what was actually stated. Writing back a default would turn "the pool decides" into
        // "this marker decided, and happened to agree", which survives into the saved .nbt and can
        // never be undone by changing the processor entry.
        if (mobSetName != null && !mobSetName.isEmpty()) {
            tag.putString(MOB_SET_NAME, mobSetName);
        }
        if (proximity != null) {
            tag.putDouble(PROXIMITY, proximity);
        }
        if (minMobs != null) {
            tag.putInt(MIN_MOBS, minMobs);
        }
        if (maxMobs != null) {
            tag.putInt(MAX_MOBS, maxMobs);
        }
        if (type != null && !type.isEmpty()) {
            tag.putString(TYPE, type);
        }
    }

    public String getMobSetName() {
        return mobSetName;
    }

    public void setMobSetName(String mobSetName) {
        this.mobSetName = mobSetName;
    }

    public Double getProximity() {
        return proximity;
    }

    public void setProximity(Double proximity) {
        this.proximity = proximity;
    }

    public Integer getMinMobs() {
        return minMobs;
    }

    public void setMinMobs(Integer minMobs) {
        this.minMobs = minMobs;
    }

    public Integer getMaxMobs() {
        return maxMobs;
    }

    public void setMaxMobs(Integer maxMobs) {
        this.maxMobs = maxMobs;
    }

    /**
     * Named {@code SpawnerType}, not {@code Type}: {@code BlockEntity#getType} is final-ish in
     * meaning (it returns the registered {@link net.minecraft.world.level.block.entity.BlockEntityType})
     * and cannot be overridden with a String. The NBT key stays {@code type}, matching the
     * processor's own field.
     */
    public String getSpawnerType() {
        return type;
    }

    public void setSpawnerType(String type) {
        this.type = type;
    }
}
