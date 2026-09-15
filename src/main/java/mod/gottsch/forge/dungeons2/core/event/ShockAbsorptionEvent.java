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
import mod.gottsch.forge.dungeons2.core.item.BootsOfShockAbsorption;
import mod.gottsch.forge.dungeons2.core.setup.CommonSetup;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Backlog #97: what the {@link BootsOfShockAbsorption} actually do.
 *
 * <p>The colossus knows nothing about the boots and the boots know nothing about the colossus. The
 * only thing the two ends share is the damage type {@code dungeons2:ground_slam}, which
 * {@code CommonSetup.wireGroundSlam} hands to gmm's {@code GroundSlamGoal} &mdash; so anything else
 * that ever slams the floor under that type is answered by these boots for free, and a colossus
 * summoned by another mod is answered too.
 *
 * <h2>Four effects, and why they need three events</h2>
 * <p>A slam does four things to whoever is standing in it, and they arrive through three different
 * channels:
 * <ul>
 *   <li><strong>damage</strong> &mdash; {@link LivingHurtEvent}, and <strong>cut, not cancelled</strong>.
 *       Boots that made the signature attack of a boss free would remove the fight rather than
 *       answer it; the player should still want to get out of the circle.</li>
 *   <li><strong>knockback</strong> &mdash; {@link LivingKnockBackEvent}, cut hard. Being thrown is
 *       what turns one slam into a second one, because it costs the distance the player spent the
 *       wind-up earning.</li>
 *   <li><strong>the slow</strong> &mdash; {@link MobEffectEvent.Applicable}, cancelled outright. It
 *       is the part that kills: rooted inside the radius with a 40-tick cooldown running is a free
 *       second hit. This is the one thing the boots undo completely.</li>
 *   <li><strong>the fall</strong> &mdash; the rim collapse ({@code SlamRimCollapse}) drops the floor
 *       out, so vanilla fall damage is part of the attack whether or not the slam itself connected.
 *       Reduced always, not only after a slam: boots that absorb shock absorb shock.</li>
 * </ul>
 *
 * <h2>Why the knockback and the slow are matched by TICK</h2>
 * <p>Neither event carries a damage source, so neither can be asked whether it came from a slam.
 * gmm's {@code GroundSlamGoal} applies all three to a victim within a single {@code hurt()} →
 * {@code knockback()} → {@code addEffect()} sequence on one tick, so the hurt handler stamps the
 * victim's tick and the other two ask whether that stamp is the tick they are on. The alternative
 * &mdash; cancelling every slow and every knockback while boots are worn &mdash; would make the
 * boots a general-purpose immunity item, which is not what they are.
 *
 * <p>The map is weak-keyed and touched only from the server thread, where all three of these events
 * are fired.</p>
 *
 * @author Mark Gottschling on Sep 14, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ShockAbsorptionEvent {

    /**
     * What is left of a slam's damage. Enough that standing in one still hurts and a second one
     * still threatens; a first guess, like every other number on this mob.
     */
    private static final float SLAM_DAMAGE_MULTIPLIER = 0.4F;

    /** What is left of the throw. Nearly nothing -- see the class javadoc. */
    private static final double SLAM_KNOCKBACK_MULTIPLIER = 0.15D;

    /** What is left of a fall. Not zero: these are boots, not a parachute. */
    private static final float FALL_DAMAGE_MULTIPLIER = 0.35F;

    /** The tick each wearer was last hit by a slam, so the two source-less events can recognise one. */
    private static final Map<LivingEntity, Integer> LAST_SLAM_TICK = new WeakHashMap<>();

    /**
     * HIGH so the cut happens before anything that reads the final number. The event is not
     * cancelled either way -- a slam that is absorbed still hits.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!isWearingBoots(victim)) {
            return;
        }
        if (event.getSource().is(CommonSetup.GROUND_SLAM_DAMAGE_TYPE)) {
            LAST_SLAM_TICK.put(victim, victim.tickCount);
            event.setAmount(event.getAmount() * SLAM_DAMAGE_MULTIPLIER);
            wear(victim);
        } else if (event.getSource().is(DamageTypes.FALL)) {
            event.setAmount(event.getAmount() * FALL_DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (wasSlammedThisTick(event.getEntity())) {
            event.setStrength((float) (event.getStrength() * SLAM_KNOCKBACK_MULTIPLIER));
        }
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance().getEffect() == MobEffects.MOVEMENT_SLOWDOWN
                && wasSlammedThisTick(event.getEntity())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /**
     * True only on the same tick the wearer took slam damage. The boots must be on now as well as
     * then: taking them off between the hurt and the effect is not a case worth carrying state for,
     * but reading the stamp of someone no longer wearing them would be wrong.
     */
    private static boolean wasSlammedThisTick(LivingEntity entity) {
        if (!isWearingBoots(entity)) {
            return false;
        }
        Integer tick = LAST_SLAM_TICK.get(entity);
        return tick != null && tick == entity.tickCount;
    }

    private static boolean isWearingBoots(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof BootsOfShockAbsorption;
    }

    /** Absorbing a slam costs the boots something. Falls do not -- vanilla already charges for those. */
    private static void wear(LivingEntity entity) {
        ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
        boots.hurtAndBreak(1, entity, e -> e.broadcastBreakEvent(EquipmentSlot.FEET));
    }

    private ShockAbsorptionEvent() {}
}
