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
package mod.gottsch.forge.dungeons2.core.event;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.item.MirrorShield;
import mod.gottsch.forge.gmm.core.entity.projectile.DisarmSpell;
import mod.gottsch.forge.gmm.core.entity.projectile.DisintegrateSpell;
import mod.gottsch.forge.gmm.core.entity.projectile.GMMHurtingProjectile;
import mod.gottsch.forge.gmm.core.entity.projectile.HarmSpell;
import mod.gottsch.forge.gmm.core.entity.projectile.ParalysisSpell;
import mod.gottsch.forge.gmm.core.entity.projectile.WitheringGazeSpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Backlog #97: what the Mirror Shield does, and how a player learns that ordinary shields do not.
 *
 * <h2>Reflecting a bolt</h2>
 * <p>The eye-spells are GMM projectiles. Their damage is applied in {@code onHitEntity}, which only
 * runs if {@code ProjectileImpactEvent} leaves the impact alone &mdash; so the reflect is decided
 * here, before any damage exists, and GMM needs no change. The bolt is skipped, turned toward its
 * caster, and re-owned by the player: its next hit is on the Beholder, credited to the player, and
 * the bolt cannot turn round and hit the player again (a projectile ignores its owner until it has
 * left them). Both sides skip the impact so the client does not play a hit that did not happen;
 * only the server steers.</p>
 *
 * <h2>The "pierced" cue</h2>
 * <p>The companion tag ({@code #minecraft:bypasses_shield}) makes most eye-magic ignore ORDINARY
 * shields, which is invisible unless something says so. A player blocking in the right direction
 * who is hit anyway by a GMM or D2 spell gets a glassy crack and sparks instead of the clang, and,
 * the first time only, one action-bar line.</p>
 *
 * @author Mark Gottschling on Sep 24, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MirrorShieldEvent {

    /** Persisted across death, so the lesson is taught once per player, not once per life. */
    private static final String TAUGHT_KEY = Dungeons.MOD_ID + ".shield_pierced_taught";

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)
                || !(event.getProjectile() instanceof GMMHurtingProjectile bolt)
                || !isEyeMagic(bolt)
                || !MirrorShield.isReflecting(target, bolt.position())) {
            return;
        }
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        if (target.level().isClientSide) {
            return;
        }
        reflect(bolt, target);
        wear(target);
        target.level().playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 1.0F, 1.4F + target.getRandom().nextFloat() * 0.2F);
    }

    /**
     * The eye-spells. Fire Spout, Firewall and Spike Growth are left out on purpose: they are the
     * blockable ones, and an ordinary shield already answers them.
     */
    static boolean isEyeMagic(Entity projectile) {
        return projectile instanceof ParalysisSpell || projectile instanceof HarmSpell
                || projectile instanceof DisintegrateSpell || projectile instanceof DisarmSpell
                || projectile instanceof WitheringGazeSpell;
    }

    /** Aims the bolt at its caster's middle, at its current speed, and hands it to the reflector. */
    private static void reflect(GMMHurtingProjectile bolt, LivingEntity reflector) {
        Entity caster = bolt.getOwner();
        Vec3 direction = caster != null && caster.isAlive()
                ? caster.position().add(0.0D, caster.getBbHeight() * 0.5D, 0.0D)
                        .subtract(bolt.position()).normalize()
                : bolt.getDeltaMovement().scale(-1.0D).normalize();
        double speed = Math.max(bolt.getDeltaMovement().length(), 0.5D);
        bolt.setDeltaMovement(direction.scale(speed));
        bolt.xPower = direction.x * 0.1D;
        bolt.yPower = direction.y * 0.1D;
        bolt.zPower = direction.z * 0.1D;
        bolt.setOwner(reflector);
        bolt.hasImpulse = true;
    }

    /** A reflect costs what a block of the same hit would: the bolt never dealt damage to charge. */
    public static void wear(LivingEntity entity) {
        ItemStack shield = entity.getUseItem();
        InteractionHand hand = entity.getUsedItemHand();
        shield.hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(hand));
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide
                || !player.isBlocking() || player.getUseItem().getItem() instanceof MirrorShield) {
            return;
        }
        DamageSource source = event.getSource();
        if (!source.is(DamageTypeTags.BYPASSES_SHIELD) || source.getSourcePosition() == null
                || !isSpell(source)) {
            return;
        }
        Vec3 toSource = source.getSourcePosition().vectorTo(player.position()).normalize();
        if (new Vec3(toSource.x, 0.0D, toSource.z).dot(player.getViewVector(1.0F)) >= 0.0D) {
            return; // hit from behind: an ordinary shield would not have blocked it either
        }
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS,
                0.8F, 1.6F);
        Vec3 look = player.getViewVector(1.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + look.x * 0.6D,
                player.getEyeY() - 0.4D, player.getZ() + look.z * 0.6D, 8, 0.2D, 0.2D, 0.2D, 0.1D);

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.getBoolean(TAUGHT_KEY)) {
            persisted.putBoolean(TAUGHT_KEY, true);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            player.displayClientMessage(Component.translatable("message.dungeons2.shield_pierced"), true);
        }
    }

    /** GMM's spells and D2's own ray; a vanilla or third-party bypass is none of this mod's business. */
    private static boolean isSpell(DamageSource source) {
        return source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace())
                .map(ns -> ns.equals("gmm") || ns.equals(Dungeons.MOD_ID) || ns.equals("minecraft")
                        && source.getDirectEntity() instanceof GMMHurtingProjectile)
                .orElse(false);
    }

    private MirrorShieldEvent() {}
}
