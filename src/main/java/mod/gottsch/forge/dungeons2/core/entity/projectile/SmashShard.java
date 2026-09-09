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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A chip of masonry thrown off by {@link mod.gottsch.forge.dungeons2.core.entity.ai.goal.SmashBlocksGoal}
 * when something big enough takes a wall apart, and a hazard in its own right &mdash; a player
 * standing in the wrong place while a Minotaur comes through a wall takes a hit for it.
 *
 * <p><strong>This is gmm's {@code BoneShard} in stone.</strong> Same rig, same three variants, same
 * tumble, same physics, same damage, same despawn; the only difference is the texture it is drawn
 * with. That is deliberate (Mark, 2026-09-08), and it replaced a cleverer first version that synched
 * the smashed {@code BlockState} and drew that block's own model shrunk down &mdash; so a mud wall
 * threw mud and a brick wall threw brick, self-themeing, with no art to author. It read badly: a
 * shrunken cube tumbling through the air looks like a floating block, not a chip off one.
 * <strong>A purpose-built shard shape in slightly the wrong material beats the right material in the
 * wrong shape.</strong></p>
 *
 * <p>The one thing not shared with the bone shard is where a shard <em>starts</em> &mdash; see
 * {@code SmashBlocksGoal.randomEdgePoint}. Shards are thrown from the EDGES of the block being
 * broken rather than its centre, so a burst reads as a block coming apart rather than as an emitter
 * firing.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public class SmashShard extends AbstractArrow {

    /** Matches {@code BoneShardModel.LAYERS}: a shard is a random one of three shapes. */
    public static final int VARIANTS = 3;

    private static final EntityDataAccessor<Byte> DATA_VARIANT =
            SynchedEntityData.defineId(SmashShard.class, EntityDataSerializers.BYTE);
    private static final String VARIANT_TAG = "variant";

    /** Render scale; 1.0 is the default shard. Does not affect physics or damage. */
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(SmashShard.class, EntityDataSerializers.FLOAT);
    private static final String SCALE_TAG = "scale";

    /** Increments only while airborne, so the renderer freezes a shard that has landed. */
    private int spinTicks;

    public SmashShard(EntityType<? extends SmashShard> entityType, Level level) {
        super(entityType, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public SmashShard(EntityType<? extends SmashShard> entityType, LivingEntity shooter, Level level) {
        super(entityType, shooter, level);
        this.pickup = Pickup.DISALLOWED;
        if (!level.isClientSide) {
            this.setVariant((byte) this.random.nextInt(VARIANTS));
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_VARIANT, (byte) 0);
        this.entityData.define(DATA_SCALE, 1.0F);
    }

    public void setVariant(byte variant) {
        this.entityData.set(DATA_VARIANT, (byte) Math.floorMod(variant, VARIANTS));
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setScale(float scale) {
        this.entityData.set(DATA_SCALE, scale);
    }

    public float getScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public int getSpinTicks() {
        return this.spinTicks;
    }

    /** True once the shard has stuck in a block (arrow-style). */
    public boolean isStuck() {
        return this.inGround;
    }

    @Override
    public void tick() {
        super.tick();
        // keep tumbling only while actually flying; stops on contact / when velocity dies
        if (!this.inGround && this.getDeltaMovement().lengthSqr() > 1.0E-6D) {
            this.spinTicks++;
        }
    }

    /**
     * Never collected &mdash; the shard is {@link Pickup#DISALLOWED} &mdash; but
     * {@link AbstractArrow} requires a stack, and cobblestone is what a broken wall leaves behind.
     * The block it came from already dropped its own loot through {@code destroyBlock}, so a
     * collectable shard would be a second reward for one broken block.
     */
    @Override
    protected ItemStack getPickupItem() {
        return new ItemStack(Items.COBBLESTONE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte(VARIANT_TAG, (byte) this.getVariant());
        tag.putFloat(SCALE_TAG, this.getScale());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setVariant(tag.getByte(VARIANT_TAG));
        if (tag.contains(SCALE_TAG)) {
            this.setScale(tag.getFloat(SCALE_TAG));
        }
    }
}
