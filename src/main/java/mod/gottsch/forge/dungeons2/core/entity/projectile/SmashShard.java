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
package mod.gottsch.forge.dungeons2.core.entity.projectile;

import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A chip of masonry thrown off by {@link mod.gottsch.forge.dungeons2.core.entity.ai.goal.SmashBlocksGoal}
 * when something big enough takes a wall apart, and a hazard in its own right &mdash; a player
 * standing in the wrong place while a Minotaur comes through a wall takes a hit for it.
 *
 * <p>Modelled on gmm's {@code BoneShard}: an {@link AbstractArrow}, so flight, gravity and
 * damage-on-contact all come from vanilla rather than being reimplemented. It differs from a bone
 * shard in three ways, and each is deliberate.</p>
 *
 * <h2>It wears the block it came from</h2>
 * <p>The smashed {@link BlockState} is synched to the client and the renderer draws <em>that
 * block's own model</em> at a fraction of its size. So a smashed stone-brick wall throws stone-brick
 * chips and a mud wall throws mud, with no per-material art and nothing to keep in sync when the
 * motif palettes change. That is the whole reason this is a separate entity from the bone shard
 * rather than a re-skin: a bone shard is one fixed thing, a masonry chip is whatever it was cut
 * from.</p>
 *
 * <h2>It is debris, not ammunition</h2>
 * <p>{@link Pickup#DISALLOWED}, and {@link #getPickupItem()} is empty. The block it came from has
 * already dropped its own loot through {@code destroyBlock}, so a collectable shard would be a
 * second, duplicate reward for the same broken block.</p>
 *
 * <h2>It does not stick</h2>
 * <p>An arrow embeds itself and waits to be collected. A chip of wall that parks itself in the
 * floor for a minute reads as litter, so {@link #onHitBlock} discards on contact and
 * {@link #MAX_LIFE_TICKS} clears anything still in the air. Shards are a moment, not a state.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public class SmashShard extends AbstractArrow {

    /**
     * The smashed block, as a {@link Block#getId(BlockState)} id.
     *
     * <p>Synched because the render is a CLIENT-side read and the client is never told which block
     * the goal destroyed &mdash; by the time the shard exists that block is already air, so there
     * is nothing at the position to look up.</p>
     */
    private static final EntityDataAccessor<Integer> DATA_BLOCK_STATE =
            SynchedEntityData.defineId(SmashShard.class, EntityDataSerializers.INT);
    private static final String BLOCK_STATE_TAG = "blockState";

    /** Render size, as a fraction of a full block. Physics and damage do not read it. */
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(SmashShard.class, EntityDataSerializers.FLOAT);
    private static final String SCALE_TAG = "scale";

    /**
     * A shard in flight this long has missed everything and is falling down a stairwell. Clearing
     * it matters more than it looks: a swing throws several, a wall is many swings, and nothing
     * else removes one that never touches a surface.
     */
    private static final int MAX_LIFE_TICKS = 100;

    private int lifeTicks;

    public SmashShard(EntityType<? extends SmashShard> entityType, Level level) {
        super(entityType, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public SmashShard(Level level, LivingEntity shooter, BlockState smashed, double x, double y, double z) {
        super(DungeonsEntities.SMASH_SHARD_ENTITY.get(), level);
        this.setOwner(shooter);
        this.setPos(x, y, z);
        this.pickup = Pickup.DISALLOWED;
        if (!level.isClientSide) {
            this.setSmashedState(smashed);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_BLOCK_STATE, Block.getId(Blocks.STONE.defaultBlockState()));
        this.entityData.define(DATA_SCALE, 0.25F);
    }

    public void setSmashedState(BlockState state) {
        this.entityData.set(DATA_BLOCK_STATE, Block.getId(state));
    }

    /**
     * The block this shard was cut from. Falls back to stone rather than to air: a shard that
     * renders nothing at all is worse than one wearing the wrong material, and an unknown state id
     * means a block that no longer exists (a removed mod), not a bug worth crashing on.
     */
    public BlockState getSmashedState() {
        BlockState state = Block.stateById(this.entityData.get(DATA_BLOCK_STATE));
        return state.isAir() ? Blocks.STONE.defaultBlockState() : state;
    }

    public void setScale(float scale) {
        this.entityData.set(DATA_SCALE, scale);
    }

    public float getScale() {
        return this.entityData.get(DATA_SCALE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && ++this.lifeTicks > MAX_LIFE_TICKS) {
            this.discard();
        }
    }

    /**
     * Break apart on contact instead of embedding. {@code super} is still called so the impact
     * sound and the arrow's own bookkeeping happen; the discard just denies it the stuck state.
     */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            this.discard();
        }
    }

    /** Debris, not ammunition &mdash; see the class javadoc. */
    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(BLOCK_STATE_TAG, this.entityData.get(DATA_BLOCK_STATE));
        tag.putFloat(SCALE_TAG, this.getScale());
        tag.putInt("lifeTicks", this.lifeTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(BLOCK_STATE_TAG)) {
            this.entityData.set(DATA_BLOCK_STATE, tag.getInt(BLOCK_STATE_TAG));
        }
        if (tag.contains(SCALE_TAG)) {
            this.setScale(tag.getFloat(SCALE_TAG));
        }
        this.lifeTicks = tag.getInt("lifeTicks");
    }
}
