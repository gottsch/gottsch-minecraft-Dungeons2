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
import mod.gottsch.forge.dungeons2.core.entity.ai.goal.BeamBreachGoal;
import mod.gottsch.forge.dungeons2.core.entity.ai.goal.BeholderkinPursueGoal;
import mod.gottsch.forge.dungeons2.core.entity.projectile.AnnihilationRay;
import mod.gottsch.forge.gmm.core.entity.monster.beholderkin.Beholder;
import mod.gottsch.forge.gmm.core.entity.monster.beholderkin.DeathTyrant;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives the Beholder and the Death Tyrant this mod's {@link BeamBreachGoal}, as they join the level
 * &mdash; the flyers' counterpart to {@link MinotaurGoalsEvent}, and injected here for the same
 * reason: gmm owns the mobs, the decision that they may burn through a dungeon wall is this mod's.
 *
 * <p>The beam's place in their ordinary spell pools is wired in {@code CommonSetup}; this adds only
 * what a pool cannot do, which is fire without line of sight.</p>
 *
 * <p>Both are matched by class, like the Minotaur, so one summoned by another mod's content
 * behaves the same. Not the Spectator or other small Beholder-kin: they are summons and escorts,
 * not bosses.</p>
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BeholderkinBeamEvent {

    /**
     * Beside gmm's {@code CastSpellGoal} (6). The goal claims no flags, so the number orders it and
     * decides nothing.
     */
    private static final int GOAL_PRIORITY = 6;

    /**
     * Above gmm's random float (5), which also claims MOVE: while there is somewhere to pursue to,
     * the float must not pull the mob elsewhere. Below the bite (4) is irrelevant -- the bite claims
     * no flags -- so 3 is simply the first free slot above the float.
     */
    private static final int PURSUE_PRIORITY = 3;

    /**
     * How long it remembers a target it cannot see: 30 seconds, as for the Minotaur. gmm's
     * {@code NearestAttackableTargetGoal} requires sight and drops the target after vanilla's 3,
     * which is shorter than {@code BeamBreachGoal}'s grace plus its charge &mdash; so without this it
     * would forget the player behind the wall before it could ever shoot through it.
     */
    private static final int BOSS_TARGET_MEMORY_TICKS = 600;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof Beholder || event.getEntity() instanceof DeathTyrant)) {
            return;
        }
        Mob mob = (Mob) event.getEntity();
        AnnihilationRay.recordHome(mob);
        mob.goalSelector.addGoal(GOAL_PRIORITY, new BeamBreachGoal(mob));
        mob.goalSelector.addGoal(PURSUE_PRIORITY, new BeholderkinPursueGoal(mob));
        mob.targetSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(TargetGoal.class::isInstance)
                .map(TargetGoal.class::cast)
                .forEach(goal -> goal.setUnseenMemoryTicks(BOSS_TARGET_MEMORY_TICKS));
    }
}
