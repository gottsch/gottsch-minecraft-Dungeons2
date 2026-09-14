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

import mod.gottsch.forge.dungeons2.core.entity.ai.SmashDebris;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * A chunk of the room, torn up and thrown &mdash; the Stone Colossus's answer to a player standing
 * where it cannot reach them.
 *
 * <p><strong>This is what makes "it stays in its room" fair.</strong> The colossus does not chase,
 * so without a thrown attack the fight has a free square in it: back out of reach, shoot, wait. The
 * wall burst opens the line of sight and this is what travels down it. The rule the pair enforces is
 * the one the plan states &mdash; retreating deep into a corridor is disengaging and is fine;
 * attacking from safety is not, so anywhere you can hit it from, it can hit back.
 *
 * <h2>It breaks what it lands on</h2>
 * <p>Under the same rules as a fist: {@code mobGriefing}, a hardness cap, nothing with a block
 * entity, and the thrower's own leash &mdash; all supplied by whoever threw it, because where a
 * rock may land is a property of the world and not of the rock. Hiding behind one block and leaning
 * out to shoot therefore stops being safe, which is the point; the block it breaks throws the same
 * {@link SmashShard} burst a smashed wall does, from {@link SmashDebris}, so debris behaves the same
 * whether a fist or a rock arrived.
 *
 * <h2>Drawn as a block, not a model</h2>
 * <p>The state it wears is synched and rendered through the block renderer, so it reads as the stone
 * it was torn from and needs no art. That is the opposite call from {@link SmashShard}, which tried
 * exactly this and was replaced with a purpose-built rig &mdash; and the difference is size. A chip
 * has a shape a cube cannot fake; a boulder <em>is</em> a block-sized lump of masonry, so the cube
 * is not a stand-in for the right shape, it is the right shape.
 *
 * @author Mark Gottschling on Sep 14, 2026
 */
public class Boulder extends ThrowableProjectile {

    /**
     * Heavier than a snowball's 0.03. A rock that floats across a room on a flat arc reads as a
     * fireball; the drop is what says it has weight, and it is also what makes distance a real cost
     * for the thrower rather than a free attack at any range.
     */
    private static final float GRAVITY = 0.05F;

    /** What it is made of, so the client can draw it. Sent as a block-state id. */
    private static final EntityDataAccessor<Integer> DATA_BLOCK_STATE =
            SynchedEntityData.defineId(Boulder.class, EntityDataSerializers.INT);

    /** Spin rate in degrees per tick. Constant, with only the axis varying &mdash; see the renderer. */
    public static final float SPIN_DEGREES_PER_TICK = 14.0F;

    private float damage = 8.0F;
    private float maxHardness = 6.0F;
    /**
     * Where this rock is allowed to break something. Not saved, and it does not need to be: the
     * entity is {@code noSave} and lives a couple of seconds. A null area breaks nothing, which is
     * the safe default for a boulder that arrived from somewhere unexpected.
     */
    private Predicate<BlockPos> breakableArea;
    private int shardsOnImpact = 10;
    private double shardBaseDamage = 2.0D;

    public Boulder(EntityType<? extends Boulder> entityType, Level level) {
        super(entityType, level);
    }

    public Boulder(EntityType<? extends Boulder> entityType, LivingEntity thrower, Level level) {
        super(entityType, thrower, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_BLOCK_STATE, Block.getId(Blocks.COBBLESTONE.defaultBlockState()));
    }

    @Override
    protected float getGravity() {
        return GRAVITY;
    }

    public void setBlockState(BlockState state) {
        this.entityData.set(DATA_BLOCK_STATE, Block.getId(state));
    }

    public BlockState getBlockState() {
        return Block.stateById(this.entityData.get(DATA_BLOCK_STATE));
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /** The two halves of "may this break the block it lands on": what, and where. */
    public void setBreaking(float maxHardness, Predicate<BlockPos> breakableArea) {
        this.maxHardness = maxHardness;
        this.breakableArea = breakableArea;
    }

    public void setShards(int shardsOnImpact, double shardBaseDamage) {
        this.shardsOnImpact = shardsOnImpact;
        this.shardBaseDamage = shardBaseDamage;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity hit = result.getEntity();
        Entity owner = getOwner();
        // Its own escort is not a target -- the Annihilation Ray's rule, and the ground slam's: a
        // boss that knocks its own encounter about disarms it.
        if (owner instanceof Mob thrower && hit != thrower.getTarget()
                && !(hit instanceof net.minecraft.world.entity.player.Player)) {
            return;
        }
        hit.hurt(damageSources().thrown(this, owner), this.damage);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) {
            return;
        }
        breakWhereItLanded(result.getBlockPos());
        this.level().playSound(null, blockPosition(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE,
                1.2F, 0.6F);
        discard();
    }

    private void breakWhereItLanded(BlockPos pos) {
        if (!(getOwner() instanceof Mob thrower)) {
            return;
        }
        if (this.breakableArea == null
                || !this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !this.breakableArea.test(pos)
                || !SmashDebris.isBreakable(this.level(), pos, this.maxHardness)) {
            return;
        }
        // destroyBlock, so the block drops and plays its own break effects -- the same as a wall
        // taken down by hand.
        this.level().destroyBlock(pos, true, thrower);
        // Aimed along the rock's own flight, so the debris carries on the way the rock was going
        // rather than back at the thing that threw it.
        Vec3 onward = position().add(getDeltaMovement().scale(4.0D));
        SmashDebris.throwShards(thrower, pos, this.shardsOnImpact, this.shardBaseDamage, onward);
    }
}
