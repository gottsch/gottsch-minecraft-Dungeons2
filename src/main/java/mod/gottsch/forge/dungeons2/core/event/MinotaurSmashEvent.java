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
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives every Minotaur that joins the level this mod's {@link SmashBlocksGoal}.
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
public class MinotaurSmashEvent {

    /**
     * Continuous ticks pressed against something before the first swing. A second of being genuinely
     * stuck, which is long enough that walking through a doorway never trips it.
     */
    private static final int BLOCKED_GRACE_TICKS = 20;

    /** Ticks between swings. Slow enough to watch a wall come apart rather than evaporate. */
    private static final int SWING_INTERVAL_TICKS = 25;

    /**
     * Hardness cap. Dungeon brick and its kin sit at 1.5-2, so 6 admits the structure comfortably
     * while excluding obsidian (50) and anything a player placed to hold. See
     * {@code SmashBlocksGoal#isSmashable}.
     */
    private static final float MAX_HARDNESS = 6.0F;

    /**
     * Priority 5: below the charge (3) and the melee attack (4) the mob registers itself, so being
     * able to reach the target always beats digging toward it. The goal claims no flags, so it runs
     * concurrently with whichever of those is active rather than displacing it.
     */
    private static final int GOAL_PRIORITY = 5;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Minotaur minotaur)) {
            return;
        }
        minotaur.goalSelector.addGoal(GOAL_PRIORITY,
                new SmashBlocksGoal(minotaur, BLOCKED_GRACE_TICKS, SWING_INTERVAL_TICKS, MAX_HARDNESS));
    }
}
