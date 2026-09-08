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
    /**
     * 0..1, the chance this marker produces a spawner <em>at all</em>. Absent means the pool decides,
     * and the pool's own default is 1.0 &mdash; so a template authored before this key existed still
     * produces its spawner every time.
     *
     * <p>Spelled to match {@code PotMarkerBlockEntity.PROBABILITY}, which already means exactly this
     * on the sibling marker. The two get placed from the same palette in the same authoring session;
     * one of them calling it something else would be a tax paid in every {@code /data merge} typed
     * from memory.</p>
     *
     * <p>What it does <strong>not</strong> do is make a spawner that fires and releases nothing. A
     * roll that comes up empty leaves the cell as air, so a room authored around a spawner has to
     * still read correctly with the spawner absent.</p>
     */
    public static final String PROBABILITY = "probability";
    /** {@code proximity} or {@code vanilla}, matching the processor's own {@code type} field. */
    public static final String TYPE = "type";
    /**
     * Marks this as <strong>the dungeon's boss spawner</strong>, so the processor's
     * {@code boss_mob_set} decides what it releases rather than {@link #MOB_SET_NAME} or the pool's
     * ordinary default.
     *
     * <p>The mirror of {@code ChestMarkerBlockEntity.BOSS}, and there for the same reason: the
     * boss's difficulty has to follow the <em>dungeon's</em> size tier rather than the geometry of
     * whichever end room was drawn. See that field for the full argument, including why this is a
     * flag rather than a sentinel written into {@link #MOB_SET_NAME}.</p>
     *
     * <p><strong>Absence still means what it always meant</strong> &mdash; "no opinion, take the
     * pool's {@code mob_set}". That is the whole point of making the boss say so explicitly: the
     * alternative considered (treat an unnamed set as the boss's) would have turned every marker
     * that merely <em>forgot</em> to name a set into a boss, silently, and a boss room holds four
     * ordinary spawners for every one of these.</p>
     */
    public static final String BOSS = "boss";
    /**
     * Marks this as one of the boss's <strong>melee escort</strong>, taking the tier's
     * {@code escort_mob_set}.
     *
     * <p>The boss room's other spawners had the same fault the boss did, one step quieter: they
     * named {@code classic_undead} and {@code classic_ranged} outright, so the room a LARGE dungeon
     * ends in was garrisoned by the same grave zombies as a SMALL one. The boss scaled and its
     * guard did not.</p>
     *
     * <p>Three flat flags rather than one {@code role} string, and rather than folding the escort
     * into {@link #BOSS}: they are independent, they read the same way {@code boss} and
     * {@code treasure} already do, and {@code boss} was authored into a shipped template before
     * these existed. If a fourth role ever appears this should become a single {@code role} string
     * with a map on the processor -- three is the point at which that trade is still not worth the
     * migration.</p>
     */
    public static final String ESCORT = "escort";
    /**
     * Marks this as one of the boss's <strong>ranged escort</strong>, taking the tier's
     * {@code ranged_escort_mob_set}.
     *
     * <p>Separate from {@link #ESCORT} because the POSITION is authored, not incidental:
     * {@code small_boss_1} puts three of these on ledges at Y 6 and Y 8 and one melee group on the
     * floor. One escort set for both would perch grave zombies on the archer galleries, which is a
     * visible authoring decision quietly undone by a schema convenience.</p>
     */
    public static final String RANGED_ESCORT = "ranged_escort";

    private String mobSetName;
    private Double proximity;
    private Integer minMobs;
    private Integer maxMobs;
    private Float probability;
    private String type;
    private boolean boss;
    private boolean escort;
    private boolean rangedEscort;

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
        // Clamped rather than rejected, unlike the processor's datapack field: this value arrives on
        // a worldgen thread, where throwing is not an option, and `probability:2` is unambiguous
        // about what the author wanted.
        if (tag.contains(PROBABILITY, Tag.TAG_ANY_NUMERIC)) {
            this.probability = clampProbability(tag.getFloat(PROBABILITY));
        }
        if (tag.contains(TYPE)) {
            this.type = tag.getString(TYPE);
        }
        if (tag.contains(BOSS)) {
            this.boss = tag.getBoolean(BOSS);
        }
        if (tag.contains(ESCORT)) {
            this.escort = tag.getBoolean(ESCORT);
        }
        if (tag.contains(RANGED_ESCORT)) {
            this.rangedEscort = tag.getBoolean(RANGED_ESCORT);
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
        if (probability != null) {
            tag.putFloat(PROBABILITY, probability);
        }
        if (type != null && !type.isEmpty()) {
            tag.putString(TYPE, type);
        }
        if (boss) {
            tag.putBoolean(BOSS, true);
        }
        if (escort) {
            tag.putBoolean(ESCORT, true);
        }
        if (rangedEscort) {
            tag.putBoolean(RANGED_ESCORT, true);
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

    public Float getProbability() {
        return probability;
    }

    public void setProbability(Float probability) {
        this.probability = probability == null ? null : clampProbability(probability);
    }

    /** 0..1. Shared by {@link #load} and {@link #setProbability} so the two cannot disagree. */
    private static float clampProbability(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
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

    public boolean isBoss() {
        return boss;
    }

    public void setBoss(boolean boss) {
        this.boss = boss;
    }

    public boolean isEscort() {
        return escort;
    }

    public void setEscort(boolean escort) {
        this.escort = escort;
    }

    public boolean isRangedEscort() {
        return rangedEscort;
    }

    public void setRangedEscort(boolean rangedEscort) {
        this.rangedEscort = rangedEscort;
    }
}
