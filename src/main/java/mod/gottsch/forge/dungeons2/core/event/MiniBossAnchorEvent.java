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
import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Posts a mini-boss to guard where it was placed, so it neither wanders off nor despawns.
 *
 * <h2>The two halves, and why they are one call</h2>
 * <p>A boss placed by a {@code dungeons2:spawner_marker} is spawned through GottschCore's
 * {@code SpawnUtil.spawnAndAddMob} with {@code MobSpawnType.TRIGGERED}, which marks nothing
 * persistent and anchors nothing. So the boss of an authored boss room both <em>wandered</em>
 * &mdash; its goals are {@code WaterAvoidingRandomStrollGoal} plus a player target, nothing more
 * &mdash; and <em>despawned</em>. The despawn is the worse of the two and it is permanent: the
 * spawner {@code selfDestruct}s the instant it fires, so a room whose boss despawned can never be
 * re-armed, and nothing anywhere records that the encounter was ever there.</p>
 *
 * <p>{@code restrictTo} answers both at once, because the Monster Manual's anchor pattern makes the
 * restriction <em>be</em> the persistence flag &mdash; {@code GMMMonster.checkDespawn} returns early
 * when {@code hasRestriction()}. The anchor also suspends itself while the boss has a target, so it
 * still chases; it just does not spend the rest of its life drifting once the fight is over.</p>
 *
 * <h2>Why on join rather than at the spawner</h2>
 * <p>The spawner cannot reach the mob: {@code ProximityMobSetSpawnerBlockEntity.execute} draws,
 * spawns and discards the entity inside GottschCore, and Dungeons2 holds no reference to what came
 * out. Joining the level is the one moment every route &mdash; spawner, spawn egg, {@code /summon},
 * a Wight's thralls &mdash; passes through, and at that moment the mob is still standing where it
 * was placed, which is exactly the position to anchor it to.</p>
 *
 * <p><strong>Not {@code MobSpawnEvent.FinalizeSpawn}.</strong> That fires only on routes that
 * finalize, so a {@code /summon}ed boss would slip past &mdash; and {@code SpawnUtil}'s own history
 * with that event's return value is the kind of subtlety worth not depending on twice (see
 * {@code MobSetSpawnerBlock} for what it cost the first time).</p>
 *
 * <h2>Why the whole {@code MINI_BOSSES} list</h2>
 * <p>It is the existing single source of truth for "placed, never ambient" &mdash;
 * {@code MobSpawnExclusionTest} already reads it, and the same reasoning that keeps these three out
 * of the mob sets is the reasoning that says a placed one stays placed. Scoping this to the Bodak
 * alone would leave the other two to be discovered the same way, later.</p>
 *
 * @author Mark Gottschling on Sep 3, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MiniBossAnchorEvent {

    /**
     * How far a posted boss may drift, in blocks.
     *
     * <p>Sized to the room rather than to the cell: Dungeons2 rooms run 5&ndash;13 across, so this
     * covers the largest of them from a marker anywhere inside it, with enough margin that a boss
     * pushed around during a fight does not end up fighting its own anchor. It is not a leash &mdash;
     * a boss with a target ignores it entirely.</p>
     */
    private static final int GUARD_RADIUS = 16;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!isMiniBoss(mob) || mob.hasRestriction()) {
            // Already anchored: this event fires again every time the chunk reloads, and the
            // Monster Manual restores the saved post before this runs. Re-anchoring would move the
            // post to wherever the boss happens to be standing, which over enough reloads walks the
            // anchor across the map -- the exact bug the guard exists to prevent.
            return;
        }
        mob.restrictTo(mob.blockPosition(), GUARD_RADIUS);
    }

    /**
     * Matched by registry id rather than by class, so it stays true to the {@code MINI_BOSSES} list
     * rather than to the Monster Manual's class hierarchy &mdash; the Bodak and the Wight are both
     * in the zombie family, and the Skeleton Champion is not, so there is no shared type to test.
     */
    static boolean isMiniBoss(Mob mob) {
        ResourceLocation id = EntityType.getKey(mob.getType());
        return Dungeons.MOD_ID.equals(id.getNamespace())
                && DungeonsEntities.MINI_BOSSES.contains(id.getPath());
    }
}
