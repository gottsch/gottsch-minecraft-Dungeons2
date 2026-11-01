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
import mod.gottsch.forge.dungeons2.core.entity.ai.goal.SmashBlocksGoal;
import mod.gottsch.forge.dungeons2.core.entity.projectile.Boulder;
import mod.gottsch.forge.gmm.core.entity.ai.goal.ThrowBoulderGoal;
import mod.gottsch.forge.gmm.core.entity.monster.StoneColossus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dungeons2's additions to a Stone Colossus's AI: <strong>the wall burst and the boulder</strong>.
 *
 * <p>The counterpart to {@link MinotaurGoalsEvent} and {@link BeholderkinBeamEvent}, injected here
 * for the same reason &mdash; {@code StoneColossus} lives in the Monster Manual, which is a library
 * and owns no dungeon, so the decision that it may take a dungeon wall apart is this mod's to make
 * and this mod's to bound.
 *
 * <h2>Why a boss that cannot leave its room needs to break walls</h2>
 * <p>The Colossus is option A of its plan: it stays in its arena, and the player is free to walk
 * away. What it may not allow is being attacked <em>from safety</em>, and the doorway is exactly
 * that &mdash; a doorway is two blocks clear, the Colossus's eyes are around six up, and
 * {@code hasLineOfSight} is eye to eye, so a player standing in one can see its legs while it
 * cannot see them at all. Every ranged answer it has is blind from there. The wall burst is what
 * turns the doorway from a free sniping post into a bad place to stand, and the goal's own route
 * one (a wall between us, asked by looking) means the blind spot is also the trigger.
 *
 * <h2>And the breach alone was not enough</h2>
 * <p>Mark, 2026-09-14, on the burst working: "you just move out of reach and the colossus is stuck."
 * Taking the doorway away moves the sniping post rather than removing it, because a boss that does
 * not chase has no answer to anyone standing anywhere it cannot walk. <strong>The boulder is that
 * answer</strong>, and the two attacks are one design: the burst opens the line of sight, the throw
 * travels down it. Together they say the thing option A always meant &mdash; walking away is
 * allowed, shooting from where it cannot reply is not.
 *
 * @author Mark Gottschling on Sep 14, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StoneColossusGoalsEvent {

    /**
     * Continuous ticks blocked before the first blow. A second, the same as the Minotaur's: long
     * enough that walking through a doorway it fits through never trips it.
     */
    private static final int BLOCKED_GRACE_TICKS = 20;

    /**
     * Ticks between bursts. Not between blocks &mdash; see {@link #BLOCKS_PER_SWING}, which is
     * sized so that a burst is the whole face. This is the pace at which a wall comes down in
     * slabs: long enough to read as a wind-up and a blow rather than a jackhammer, short enough
     * that the player in the doorway has to move rather than watch.
     */
    private static final int SWING_INTERVAL_TICKS = 40;

    /**
     * Blocks a single burst takes out, and the number that makes this the <em>wall burst</em>
     * rather than mining.
     *
     * <p>The opening the Colossus needs is its own box: 2-3 columns wide by <strong>7 rows</strong>
     * tall, and possibly two blocks deep where the wall is thick &mdash; call it the low forties at
     * the worst. Anything at or above that takes the face out in one blow; the goal re-finds each
     * block as it goes and simply stops when the passage is clear, so an over-generous number costs
     * nothing. The Minotaur's 3 against a 3 x 7 face would have been seven blows a slice, which is
     * the Minotaur lesson (tune for time-to-unstick, not for spectacle) getting the answer exactly
     * backwards for a mob this size.</p>
     */
    private static final int BLOCKS_PER_SWING = 48;

    /**
     * Hardness cap, matched to {@code MinotaurGoalsEvent} and {@code SlamRimCollapse}: dungeon brick
     * and its kin sit at 1.5-2, so 6 admits the structure and excludes obsidian and anything a
     * player placed to hold.
     */
    private static final float MAX_HARDNESS = 6.0F;

    /**
     * A face rather than a block at a time, so the burst throws a proportionate amount of debris.
     * The shards are divided among the blocks a blow takes, so this is the size of the cloud, not
     * a per-block count.
     */
    private static final int SHARDS_PER_SWING = 18;
    /** Heavier chips than the Minotaur's 2.0: these are wall, thrown by something made of stone. */
    private static final double SHARD_BASE_DAMAGE = 3.0D;

    /**
     * How far past the recorded boss room the Colossus may reach.
     *
     * <p>Two, which is the doorway and the wall it is cut into and nothing beyond. The room box is
     * recorded at generation and includes its wall ring, so one would leave a thick wall's far
     * course standing; three starts to be corridor.</p>
     */
    private static final int LEASH_INFLATE = 2;

    /**
     * Leash where no boss room is recorded &mdash; a spawn egg in the overworld, a world generated
     * before the registry existed. Small on purpose: it stands in for an arena, so it should behave
     * like one rather than like a Minotaur loose in a maze.
     */
    private static final int FALLBACK_LEASH_RADIUS = 12;

    /**
     * Priority 1 &mdash; above the ground slam (2), the stalk (3) and the melee (4), below only
     * dormancy (0).
     *
     * <p>It has to be above the slam rather than beside it. The slam's trigger range is 5 blocks
     * and it does not care what is between, so a player sniping from a doorway is well inside it:
     * at equal priority whichever goal happened to start first would hold MOVE, and the Colossus
     * would spend its cooldowns pounding the floor on its own side of a wall that never comes down.
     * This cannot starve the slam, because it runs only while something smashable is in the way
     * &mdash; and the moment the wall is gone, so is the goal's entry test.</p>
     */
    private static final int GOAL_PRIORITY = 1;

    /**
     * How long it remembers a target it cannot see, as {@code MinotaurGoalsEvent} sets for the same
     * reason: vanilla's three seconds is less than the time it takes to open a wall, so without this
     * the Colossus would breach and then stand in the hole with nothing to walk toward.
     */
    private static final int BOSS_TARGET_MEMORY_TICKS = 600;

    /**
     * The boulder's range band.
     *
     * <p>The floor is the answer to Mark's report of 2026-09-14: the wall burst takes the doorway
     * away as a sniping post, and the player simply steps back out of reach and shoots from there.
     * <strong>A boss that cannot chase needs something that can</strong>, and 8 blocks is far enough
     * out that the throw never competes with the melee it would look silly beside. The ceiling is
     * how far a lobbed rock is worth throwing; past it the player has genuinely disengaged, which
     * the design allows.</p>
     */
    private static final double BOULDER_MIN_RANGE = 8.0D;
    private static final double BOULDER_MAX_RANGE = 28.0D;
    /**
     * Between throws. Long: the boulder is a heavy answer to a specific mistake, not a rate of fire,
     * and the 30-tick heave in front of it means a shorter cooldown would have the colossus winding
     * up almost continuously.
     */
    private static final int BOULDER_COOLDOWN_TICKS = 120;
    /** Blocks under the arc, so distance costs the thrower accuracy as well as time. */
    private static final float BOULDER_VELOCITY = 1.1F;
    private static final float BOULDER_INACCURACY = 4.0F;
    private static final float BOULDER_DAMAGE = 9.0F;
    private static final int BOULDER_SHARDS = 10;
    private static final double BOULDER_SHARD_DAMAGE = 2.0D;

    /**
     * Priority 2 &mdash; below the wall burst and above the stalk, beside the ground slam.
     *
     * <p>Beside the slam is safe because the two can never both want the mob: the slam triggers
     * within 5 blocks and the throw needs 8. Below the burst is deliberate &mdash; a wall in the way
     * means there is nothing to throw AT, since the goal needs line of sight, so the breach has to
     * come first.</p>
     */
    private static final int BOULDER_GOAL_PRIORITY = 2;

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof StoneColossus colossus)) {
            return;
        }
        colossus.goalSelector.addGoal(GOAL_PRIORITY, new SmashBlocksGoal(colossus,
                BLOCKED_GRACE_TICKS, SWING_INTERVAL_TICKS, MAX_HARDNESS,
                SHARDS_PER_SWING, SHARD_BASE_DAMAGE,
                // NOT a radius. See SmashBlocksGoal#boundToBossRoom: a radius leash and the goal's
                // second route (no way through AND something breakable ahead) together are option B
                // -- a Colossus boring down the corridor after a player who left.
                SmashBlocksGoal.boundToBossRoom(colossus, LEASH_INFLATE, FALLBACK_LEASH_RADIUS),
                BLOCKS_PER_SWING));

        colossus.goalSelector.addGoal(BOULDER_GOAL_PRIORITY, new ThrowBoulderGoal(colossus,
                StoneColossusGoalsEvent::hurlBoulder,
                BOULDER_MIN_RANGE, BOULDER_MAX_RANGE, BOULDER_COOLDOWN_TICKS));

        colossus.targetSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .filter(TargetGoal.class::isInstance)
                .map(TargetGoal.class::cast)
                .forEach(goal -> goal.setUnseenMemoryTicks(BOSS_TARGET_MEMORY_TICKS));
    }

    /**
     * Tears a lump out of the room and lobs it.
     *
     * <p>Ballistic, using vanilla's own lead for a thrown projectile: the aim is raised by a fifth
     * of the horizontal distance, which is what every vanilla ranged attack does and what makes a
     * rock arc into a target rather than drop short of it.</p>
     *
     * <p>The rock <em>wears the floor it was torn from</em>, so it reads as part of the room in
     * every motif and every strata band with nothing authored. Where the block under the colossus is
     * air &mdash; it is standing on a slab, or over a pit &mdash; the default in {@link Boulder}
     * stands in.</p>
     */
    private static void hurlBoulder(Mob thrower, LivingEntity target, double x, double y, double z) {
        Level level = thrower.level();
        Boulder boulder = new Boulder(DungeonsEntities.BOULDER_ENTITY.get(), thrower, level);
        boulder.setPos(x, y, z);
        boulder.setDamage(BOULDER_DAMAGE);
        boulder.setShards(BOULDER_SHARDS, BOULDER_SHARD_DAMAGE);
        // The same policy the fists have, by construction rather than by being kept in step: what
        // may be broken, and where.
        boulder.setBreaking(MAX_HARDNESS,
                SmashBlocksGoal.boundToBossRoom(thrower, LEASH_INFLATE, FALLBACK_LEASH_RADIUS));

        BlockPos underfoot = thrower.blockPosition().below();
        BlockState floor = level.getBlockState(underfoot);
        if (!floor.isAir()) {
            boulder.setBlockState(floor);
        }

        double dx = target.getX() - x;
        double dy = target.getY(0.3333D) - boulder.getY();
        double dz = target.getZ() - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        boulder.shoot(dx, dy + horizontal * 0.2D, dz, BOULDER_VELOCITY, BOULDER_INACCURACY);
        level.addFreshEntity(boulder);
    }
}
