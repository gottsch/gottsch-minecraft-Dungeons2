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
import mod.gottsch.forge.dungeons2.core.entity.ai.goal.SmashBlocksGoal;
import mod.gottsch.forge.gmm.core.entity.monster.Minotaur;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Dungeons2's overrides to a Minotaur's AI, applied as it joins the level: this mod's
 * {@link SmashBlocksGoal}, and the pursuit behaviour a <em>boss</em> needs.
 *
 * <h2>Why injected here rather than in the mob</h2>
 * <p>{@code Minotaur} lives in the Monster Manual, which is a library and owns no dungeon. Whether
 * a Minotaur may open a hole in a wall is a statement about <em>this</em> mod's content, so the goal
 * and the decision to attach it both belong here &mdash; the same split Dungeon Denizens uses to
 * inject Boulder-avoidance into gmm mobs that gmm itself knows nothing about.
 *
 * <p>Joining the level is the attachment point for the same reason
 * {@link MiniBossAnchorEvent} uses it: it is the one moment every spawn route passes through
 * &mdash; spawner, spawn egg, {@code /summon} &mdash; whereas {@code FinalizeSpawn} fires only on
 * the routes that finalize.
 *
 * <p>Matched on the gmm class rather than on a {@code dungeons2:} registry id deliberately: a
 * Minotaur summoned by another mod's content in a pack that also has Dungeons2 should behave like a
 * Minotaur, and there is exactly one Minotaur class to test against.
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MinotaurGoalsEvent {

    /**
     * Continuous ticks pressed against something before the first swing. A second of being genuinely
     * stuck, which is long enough that walking through a doorway never trips it.
     */
    private static final int BLOCKED_GRACE_TICKS = 20;

    /**
     * Ticks between swings.
     *
     * <p>Was 25, chosen so a wall came apart at a watchable pace. <strong>That was the wrong thing
     * to optimise for</strong> (Mark, 2026-09-09): in a maze the Minotaur is not putting on a show,
     * it is STUCK, and every swing interval is time the player spends waiting for a boss that
     * cannot reach them. At 25 a one-block wall cost 1.25s per block on top of the grace second,
     * and it gets wedged often enough that the cost is paid repeatedly.</p>
     *
     * <p>12 keeps the swing readable &mdash; still an animation per block, not an evaporating wall
     * &mdash; while roughly halving the time to dig out. Raise it again only if the smashing starts
     * reading as a jackhammer rather than as a bull hitting a wall.</p>
     */
    private static final int SWING_INTERVAL_TICKS = 12;

    /**
     * Hardness cap. Dungeon brick and its kin sit at 1.5-2, so 6 admits the structure comfortably
     * while excluding obsidian (50) and anything a player placed to hold. See
     * {@code SmashBlocksGoal#isSmashable}.
     */
    private static final float MAX_HARDNESS = 6.0F;

    /**
     * Priority 2 &mdash; <strong>above</strong> the charge (3) and the melee attack (4).
     *
     * <p>It was 5, below both, on the reasoning that "being able to reach the target always beats
     * digging toward it". That was right in principle and inert in practice: the goal claims MOVE
     * and LOOK, and a lower-priority goal cannot take a flag a higher one is holding, so while the
     * Minotaur was charging or approaching it could never commit to a wall. Mark, 2026-09-08:
     * "still not breaking the wall... if i go too far from the wall it attempts to charge
     * constantly."</p>
     *
     * <p>The principle is preserved by the goal's own entry test rather than by priority: it only
     * runs when a wall is actually between the mob and its target, and {@code ChargeAttackGoal}
     * requires line of sight, so the two are mutually exclusive by construction and this cannot
     * starve the charge of anything it could have used.</p>
     */
    private static final int GOAL_PRIORITY = 2;

    /** gmm's own priority and speed for the melee goal, kept identical across the swap. */
    private static final int MELEE_GOAL_PRIORITY = 4;
    private static final double MELEE_SPEED_MODIFIER = 1.0D;

    /**
     * How long a boss remembers a target it cannot see: 30 seconds, against vanilla's 3. Long
     * enough to break through a wall and follow, short enough that it eventually gives up on a
     * player who has genuinely gone.
     */
    private static final int BOSS_TARGET_MEMORY_TICKS = 600;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Minotaur minotaur)) {
            return;
        }
        minotaur.goalSelector.addGoal(GOAL_PRIORITY,
                new SmashBlocksGoal(minotaur, BLOCKED_GRACE_TICKS, SWING_INTERVAL_TICKS, MAX_HARDNESS));
        makeItPursue(minotaur);
    }

    /**
     * Makes the Minotaur keep coming when it cannot see you.
     *
     * <p>gmm registers {@code MeleeAttackGoal(this, 1.0D, false)} &mdash;
     * {@code followingTargetEvenIfNotSeen} is <strong>false</strong> &mdash; so a stock Minotaur
     * abandons pursuit the instant a wall breaks line of sight. That is right for gmm, which ships a
     * generic monster: giving up when it loses you is ordinary mob behaviour and gmm has no idea
     * what any consumer will use the mob for.</p>
     *
     * <p>It is wrong <em>here</em>, where the Minotaur is a boss (Mark, 2026-09-08). A boss you can
     * escape by stepping round a corner is not a boss, and it makes {@link SmashBlocksGoal} pointless
     * on top: the mob digs through a wall and then does not walk through the hole, because nothing is
     * driving it at a target it cannot see. So the override is D2's, not gmm's, exactly like the
     * decision that a Minotaur may break a dungeon wall at all.</p>
     *
     * <h2>Two changes, and both are needed</h2>
     * <p>Swapping the melee goal alone would not have worked. {@code TargetGoal} <em>drops</em> the
     * target {@code unseenMemoryTicks} after sight is lost &mdash; 60 ticks by default, three seconds
     * &mdash; so the mob would have kept walking toward a target it was about to forget. The target
     * memory is raised to {@link #BOSS_TARGET_MEMORY_TICKS} on the goal gmm already registered,
     * rather than by replacing it, because nothing else about its targeting needs changing.</p>
     */
    private static void makeItPursue(Minotaur minotaur) {
        // Remove through the selector rather than mutating its set directly, so a running goal is
        // stopped properly. Nothing is running yet at join, but that is a fact about today.
        List.copyOf(minotaur.goalSelector.getAvailableGoals()).stream()
                .map(WrappedGoal::getGoal)
                .filter(MeleeAttackGoal.class::isInstance)
                .forEach(minotaur.goalSelector::removeGoal);
        minotaur.goalSelector.addGoal(MELEE_GOAL_PRIORITY,
                new MeleeAttackGoal(minotaur, MELEE_SPEED_MODIFIER, true));

        minotaur.targetSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(TargetGoal.class::isInstance)
                .map(TargetGoal.class::cast)
                .forEach(goal -> goal.setUnseenMemoryTicks(BOSS_TARGET_MEMORY_TICKS));
    }
}
