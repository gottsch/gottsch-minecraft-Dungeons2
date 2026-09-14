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
package mod.gottsch.forge.dungeons2.core.entity.ai.goal;

import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import mod.gottsch.forge.dungeons2.core.entity.ai.SmashDebris;
import mod.gottsch.forge.dungeons2.core.entity.projectile.SmashShard;
import mod.gottsch.forge.dungeons2.core.world.BossRoomRegistry;
import mod.gottsch.forge.gmm.core.entity.monster.IBurstingMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Lets a big enough mob break its way through a wall that stands between it and its target.
 *
 * <p>This lives in Dungeons2 rather than in the Monster Manual on purpose: what is and is not
 * acceptable to smash is a property of the <em>dungeon</em>, not of the mob. gmm owns the Minotaur
 * and its charge; the decision that a Minotaur may open a hole in a dungeon wall is this mod's.
 *
 * <h2>What it breaks</h2>
 * <p>Structure included &mdash; walls and doors are in scope, so a Minotaur can and will open a new
 * route through a room rather than give up. Three things are still off limits, and none of them is
 * a style preference:</p>
 * <ul>
 *   <li><strong>Anything with a block entity.</strong> Chests, spawners and the authoring markers
 *       all carry one, and smashing a chest destroys generated loot and a spawner destroys an
 *       encounter. This is the guard that keeps "it can break walls" from meaning "it can delete
 *       content".</li>
 *   <li><strong>Anything harder than {@link #maxHardness}.</strong> Dungeon brick sits near 1.5 and
 *       obsidian at 50, so the cap admits the structure and excludes the things a player deliberately
 *       placed to be unbreakable. Blocks with a negative destroy speed (bedrock) are excluded by the
 *       same test.</li>
 *   <li><strong>Fluids and air.</strong> Nothing to swing at.</li>
 * </ul>
 *
 * <h2>What a swing throws off</h2>
 * <p>Every swing throws a burst of {@link SmashShard}s &mdash; chips wearing the block that was
 * just broken, which <strong>hurt a player they hit</strong>. They are not decoration: they are the
 * reason not to stand on the far side of a wall trading hits with something that cannot reach back,
 * and they turn "the mob is slowly mining toward me" into a moment with a cost. Set
 * {@code shardsPerSwing} to 0 for a mob that should break walls quietly.</p>
 *
 * <h2>Where it may swing</h2>
 * <p>Within {@link #LEASH_RADIUS} of where the mob spawned, by default &mdash; see
 * {@link #anchoredAtSpawn}. <strong>The one exemption is a mob digging its way out of
 * confinement</strong>, which may break anything anywhere: the leash was meant to stop a mob
 * rampaging after a player, not to seal one in a box for ever. Wall-breaking
 * is acceptable where the walls are this mod's own; the same mob loose in the overworld is a
 * griefing engine pointed at whatever the player built, and getting it there takes no bug at all,
 * only a player who runs and a mob that follows. The area is a constructor parameter so the policy
 * stays the caller's, but the convenience constructor supplies the leash rather than leaving it
 * off, because forgetting it is the expensive mistake.</p>
 *
 * <h2>When it swings &mdash; two routes</h2>
 * <p><strong>Digging TOWARDS someone.</strong> The mob has a live target, that target is out of
 * melee reach, and <strong>it has stopped getting closer</strong> for {@link #blockedGraceTicks}
 * continuously. Closing the distance is the only evidence that walking is working; while the mob is
 * still gaining ground it has no reason to dig.
 *
 * <p>Two earlier triggers were wrong, and both failed silently in play:
 * <ul>
 *   <li>{@code horizontalCollision} assumed the mob would keep shoving into the wall under its
 *       movement goal. <strong>Vanilla pathfinding does not path into a wall</strong> &mdash; given
 *       an unreachable target it makes no path at all, so the mob stood perfectly still, never
 *       collided, and the goal never fired.</li>
 *   <li>"the navigation cannot reach the target" fixed that, and then stopped working the moment
 *       pursuit was fixed: given a door the navigation <em>can</em> reach, so the mob walked the
 *       long way round. Correct for an ordinary monster; wrong for a boss.</li>
 * </ul>
 *
 * <p>Both mistakes are the same mistake &mdash; asking whether the mob <em>could</em> walk there
 * rather than whether walking is actually getting it there.
 *
 * <p>It also survives losing the target, which it will &mdash; a mob drops its target seconds after
 * losing sight, and a wall is what breaks sight. The goal latches the last known position and keeps
 * working for {@link #TARGET_LOST_PERSIST_TICKS}, or it would stop at the moment it became useful.
 *
 * <p><strong>NOTE the mob's own goals still decide whether it CHASES.</strong> gmm's Minotaur uses
 * {@code MeleeAttackGoal(this, 1.0D, false)} &mdash; {@code followingTargetEvenIfNotSeen} is false
 * &mdash; so it gives up pursuit the instant it loses sight. This goal digs regardless, but the mob
 * walking through the hole afterwards is gmm's business, not this class's.
 *
 * <p><strong>Digging OUT.</strong> Added 2026-09-08, after Mark reported that a Minotaur "makes no
 * attempt at breaking blocks to escape confinement" &mdash; and it could not, because all three
 * conditions above fail for a walled-in mob. It has no target (sealed in, it has no line of sight,
 * so the targeting goals never give it one), and with no target nothing drives it into a wall, so
 * {@code horizontalCollision} stays false and the aim had nothing to aim at. A mob sealed in with
 * no route out (see {@link #isConfined()}) therefore swings without needing any of them, at the
 * wall it is facing.
 *
 * <p>The goal claims {@link Goal.Flag#MOVE} and {@link Goal.Flag#LOOK} and is registered
 * <strong>above</strong> the charge and melee goals, so while it is digging it owns the mob. It
 * originally claimed no flags, on the theory that the mob would keep shoving into the wall under
 * its own movement goal; what actually happened is that the melee goal walked it around to the door
 * and the charge goal threw it about, so it was never standing at the wall long enough to swing.
 *
 * <p>This cannot starve either of them, because the entry test and {@code ChargeAttackGoal}'s are
 * exact opposites: the charge needs line of sight, this needs a wall in the way.
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public class SmashBlocksGoal extends Goal {

    /** Shards per swing when the caller does not say. Enough to read as a burst, few enough to dodge. */
    private static final int DEFAULT_SHARDS_PER_SWING = 6;

    /**
     * Blocks a single swing takes out. Three, so a Minotaur opens its 2x3 doorway in two swings
     * rather than six -- it should look like a wall coming down, not like mining.
     */
    private static final int DEFAULT_BLOCKS_PER_SWING = 3;
    /** gmm's bone-shard damage, verbatim. See {@code SmashDebris.SHARD_SPEED}. */
    private static final double DEFAULT_SHARD_BASE_DAMAGE = 2.0D;

    /**
     * How long the mob keeps working at a wall after its target is dropped.
     *
     * <p>Not an edge case &mdash; the <em>normal</em> case. A mob loses its target a few seconds
     * after losing sight of it, and losing sight is exactly what a wall causes. Without this the
     * goal would stop the moment it became useful and the mob would abandon a half-broken wall.</p>
     */
    private static final int TARGET_LOST_PERSIST_TICKS = 200;

    /**
     * Beyond this the mob does not go looking for a wall to break. A boss that starts mining
     * because it noticed someone forty blocks away reads as a machine, not a hunter.
     */
    private static final double MAX_WALL_SEARCH_DISTANCE_SQR = 576.0D;

    /**
     * Below this distance the mob is close enough that a wall in its line of sight is more likely
     * to be scenery than an obstruction, so route one stands down and it attacks instead.
     *
     * <p><strong>This is not a reach test</strong>, and it was read as one until 2026-09-09. A
     * Minotaur is 2.2 blocks tall; a single block at head height stops it dead while the player
     * stands well inside three blocks. Route two -- no navigable route AND something smashable
     * ahead -- is exempt from it for that reason. See {@link #canUse()}.</p>
     */
    private static final double MIN_BLOCKED_DISTANCE_SQR = 9.0D;

    /**
     * Ticks a mob must be sealed in before it starts digging out. Long enough that a mob briefly
     * boxed in by a closing door or a piece of terrain settles rather than immediately mining.
     */
    /**
     * How far from where it spawned a mob may break blocks. Generous enough to take apart the room
     * it is in and the corridor outside it, far short of following someone home.
     */
    private static final int LEASH_RADIUS = 48;

    /**
     * Shrinks the mob's box a hair before reading which columns it covers. A box whose edge lands
     * exactly on a block boundary would otherwise claim the next column along and widen every
     * opening by one for no reason.
     */
    private static final double EDGE_BIAS = 1.0E-7D;

    /** No burst in progress. */
    private static final int NO_BURST = -1;

    /** No blow in flight. */
    private static final int NO_STRIKE = -1;

    /**
     * Ticks between a plain swing starting and the blocks going.
     *
     * <p>They used to go on the SAME tick as {@code mob.swing()}, which is the tick the arm starts
     * MOVING: vanilla's swing runs six ticks from rest to the bottom of the arc, so the wall came
     * apart with the fist still up beside the mob's head and the swing then played through empty
     * air (Mark, 2026-09-14). Four ticks puts the break near the bottom of the arc.</p>
     *
     * <p>A mob with a real wind-up does not use this at all &mdash; it publishes its own wind-up
     * length and the blocks go on the tick the fist arrives. See {@link IBurstingMob}.</p>
     */
    private static final int SWING_STRIKE_DELAY_TICKS = 4;

    private static final int CONFINED_GRACE_TICKS = 60;
    /** How often the flood fill re-runs. Confinement does not change tick to tick. */
    private static final int CONFINEMENT_CHECK_TICKS = 40;
    /** Cells the fill may visit before declaring the surroundings open. */
    private static final int CONFINEMENT_SEARCH_CAP = 160;
    /** Reaching this far from the mob counts as being out. */
    private static final int CONFINEMENT_ESCAPE_RADIUS = 6;

    private final Mob mob;
    /** Ticks of continuous blockage before the first swing. */
    private final int blockedGraceTicks;
    /** Ticks between swings, so a wall comes apart at a readable pace rather than instantly. */
    private final int swingIntervalTicks;
    private final float maxHardness;
    private final int shardsPerSwing;
    private final int blocksPerSwing;
    private final double shardBaseDamage;
    private final Predicate<BlockPos> smashableArea;

    private int blockedTicks;
    private int swingCooldown;
    /**
     * The mob's wall-burst animation, or null for one that just swings.
     *
     * <p>Resolved once at construction rather than tested per tick: whether a mob has a punch to
     * throw is a fact about its class.</p>
     */
    private final IBurstingMob burster;
    /**
     * Ticks into the current burst, or {@link #NO_BURST}. <strong>Server-side and the goal's own
     * count</strong> -- see {@link IBurstingMob}: the flag is what crosses the wire, the clock does
     * not, because the two sides' tick counters are not the same clock.
     */
    private int burstTicks = NO_BURST;
    /** Ticks left before a swing already in the air lands, or {@link #NO_STRIKE}. */
    private int strikeDelay = NO_STRIKE;
    private BlockPos smashing;
    /** Cached answer from {@link #isConfined()}; see {@link #confinedLongEnough()}. */
    private boolean confined;
    private int confinedTicks;
    private int confinementCheckCooldown;
    /**
     * Where the target was when it was last seen. The mob keeps digging at this after the target
     * is dropped, which it will be: see {@link #TARGET_LOST_PERSIST_TICKS}.
     */
    private Vec3 digToward;
    private int persistTicks;

    public SmashBlocksGoal(Mob mob, int blockedGraceTicks, int swingIntervalTicks, float maxHardness) {
        this(mob, blockedGraceTicks, swingIntervalTicks, maxHardness,
                DEFAULT_SHARDS_PER_SWING, DEFAULT_SHARD_BASE_DAMAGE, anchoredAtSpawn(mob));
    }

    public SmashBlocksGoal(Mob mob, int blockedGraceTicks, int swingIntervalTicks, float maxHardness,
                           int shardsPerSwing, double shardBaseDamage,
                           Predicate<BlockPos> smashableArea) {
        this(mob, blockedGraceTicks, swingIntervalTicks, maxHardness,
                shardsPerSwing, shardBaseDamage, smashableArea, DEFAULT_BLOCKS_PER_SWING);
    }

    /**
     * @param shardsPerSwing how many {@link SmashShard}s a swing throws; 0 disables them entirely,
     *                       which is the way to get the pre-2026-09-07 behaviour back for a mob
     *                       that should break walls quietly.
     * @param shardBaseDamage the shards' {@code baseDamage} &mdash; see
     *                        {@link #DEFAULT_SHARD_BASE_DAMAGE} for how that becomes real damage.
     * @param blocksPerSwing blocks a single swing takes out. A parameter rather than
     *                       {@link #DEFAULT_BLOCKS_PER_SWING} because it is the difference between
     *                       a Minotaur working a doorway open over a couple of swings and a Stone
     *                       Colossus taking a whole 3 x 7 face out in one telegraphed blow. Set it
     *                       at or above the size of the face the mob wants and the wall goes in one
     *                       hit; leave it small and the same mob reads as mining.
     */
    public SmashBlocksGoal(Mob mob, int blockedGraceTicks, int swingIntervalTicks, float maxHardness,
                           int shardsPerSwing, double shardBaseDamage,
                           Predicate<BlockPos> smashableArea, int blocksPerSwing) {
        this.mob = mob;
        this.blockedGraceTicks = blockedGraceTicks;
        this.swingIntervalTicks = swingIntervalTicks;
        this.maxHardness = maxHardness;
        this.shardsPerSwing = shardsPerSwing;
        this.blocksPerSwing = Math.max(1, blocksPerSwing);
        this.burster = mob instanceof IBurstingMob burstingMob ? burstingMob : null;
        this.shardBaseDamage = shardBaseDamage;
        this.smashableArea = smashableArea;
        // MOVE and LOOK, and registered ABOVE the charge and melee goals -- see the class javadoc.
        // Digging is not something the mob does while also being walked somewhere else.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * The default leash: within {@link #LEASH_RADIUS} of wherever the mob was standing when the
     * goal was attached &mdash; which, for anything the dungeon spawns, is inside the dungeon.
     *
     * <p><strong>This replaced a structure lookup on 2026-09-08, and the reason is worth keeping.</strong>
     * The leash used to ask the level whether the block was inside a {@code dungeons2:dungeon}
     * bounding box. That query depends on the chunk holding a <em>reference</em> back to the
     * structure, and vanilla only writes those references within eight chunks of a structure's
     * start &mdash; while a Dungeons2 dungeon sprawls well past 128 blocks. So from its own boss
     * room the dungeon was invisible to the query, every wall was refused, and the goal was
     * inert. It failed closed, silently, in exactly the place it was supposed to work.</p>
     *
     * <p>Distance from spawn needs no world lookup at all, cannot fail closed, and expresses the
     * same intent: a mob may wreck the place it belongs to, and may not follow a player home and
     * wreck theirs. It composes with {@code MiniBossAnchorEvent}, which already restricts a
     * mini-boss to 16 blocks of where it was placed.</p>
     */
    private static Predicate<BlockPos> anchoredAtSpawn(Mob mob) {
        return anchoredAtSpawn(mob, LEASH_RADIUS);
    }

    private static Predicate<BlockPos> anchoredAtSpawn(Mob mob, int radius) {
        BlockPos home = mob.blockPosition();
        return pos -> pos.distSqr(home) <= (double) radius * radius;
    }

    /**
     * The leash for a boss that is supposed to <strong>stay in its room and break the doorway</strong>:
     * the room box {@link BossRoomRegistry} recorded when the dungeon generated, inflated by
     * {@code inflate}.
     *
     * <p>A radius will not do here, and the reason is the goal's second route. "No navigable route
     * AND something smashable ahead" is true of a player who has walked off down a corridor, so a
     * radius-leashed Stone Colossus would bore along after them &mdash; which is option B, the design
     * that was rejected (see the Stone Colossus plan, &sect;5). Inflating the recorded box by a
     * block or two is exactly enough to take out the doorway and the wall it is cut into, and not
     * enough to reach the corridor beyond. <strong>The leash is what makes "stays in its room" a
     * property of the mob rather than a hope about player behaviour.</strong></p>
     *
     * <p>Resolved once, at construction, from where the mob is standing &mdash; the room is recorded
     * at generation, long before anything spawns in it, and a boss that has been knocked out of its
     * own box should still be leashed to it. Where no room is recorded (a spawn egg in the
     * overworld, an older world, a mob placed outside any boss room) it falls back to a small radius
     * around the spawn: small, not {@link #LEASH_RADIUS}, because the fallback should behave like the
     * arena it stands in for rather than like a Minotaur loose in a maze.</p>
     */
    public static Predicate<BlockPos> boundToBossRoom(Mob mob, int inflate, int fallbackRadius) {
        BlockPos home = mob.blockPosition();
        BoundingBox room = mob.level() instanceof ServerLevel serverLevel
                ? BossRoomRegistry.get(serverLevel).roomAt(home).orElse(null)
                : null;
        if (room == null) {
            return anchoredAtSpawn(mob, fallbackRadius);
        }
        BoundingBox leash = room.inflatedBy(inflate);
        return leash::isInside;
    }

    @Override
    public boolean canUse() {
        if (!griefingAllowed()) {
            // reset, so turning the rule back on does not immediately fire a swing that has been
            // "charging" the whole time it was off
            this.blockedTicks = 0;
            return false;
        }
        // ROUTE TWO first, because it does not need a target and a confined mob usually has none.
        if (confinedLongEnough()) {
            return findEscapeTarget() != null;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.blockedTicks = 0;
            return false;
        }
        // TWO WAYS IN.
        //   1. A wall between us, asked directly by looking.
        //   2. A clear LINE but no clear ROUTE -- which is what the mob is left with the moment it
        //      knocks the first block out of a wall, since the hole is at eye level. Without this
        //      the goal could see through its own peephole, decline to start, and leave the mob
        //      staring through a gap it cannot fit into.
        BlockPos wall = wallBetween(target);
        boolean noRoute = cannotReach(target) && findSmashTarget(directionTo(target)) != null;
        if (wall == null && !noRoute) {
            this.blockedTicks = 0;
            return false;
        }
        // Not while already in reach: a mob that is landing hits should be landing hits, not mining
        // the floor between them.
        //
        // ROUTE TWO IS EXEMPT, and that exemption is the whole point (Mark, 2026-09-09: a Minotaur
        // two blocks away, held by a single overhanging block). "Within three blocks" was standing
        // in for "in reach", and for a mob 2.2 blocks tall those are different questions: one block
        // jutting out at head height stops it moving forward while the player stands close enough
        // to trip this guard and too far to be hit. The result was a deadlock -- too close to dig,
        // too blocked to close, too far to swing -- and the mob simply stood there.
        //
        // Route two already answers the real question, twice over: the navigation cannot reach the
        // target AND there is a smashable block in the way. A mob genuinely toe to toe with its
        // target has air in front of it, so findSmashTarget returns null and this guard still
        // holds. The proximity test therefore only vetoes route ONE, where "there is a wall in the
        // line of sight" really can be true of a mob that should be attacking instead.
        if (!noRoute && this.mob.distanceToSqr(target) < MIN_BLOCKED_DISTANCE_SQR) {
            this.blockedTicks = 0;
            return false;
        }
        return ++this.blockedTicks >= this.blockedGraceTicks;
    }

    @Override
    public boolean canContinueToUse() {
        if (!griefingAllowed()) {
            return false;
        }
        if (this.confined) {
            return findEscapeTarget() != null;
        }
        LivingEntity target = this.mob.getTarget();
        if (target != null && target.isAlive()) {
            this.digToward = target.position();
            this.persistTicks = TARGET_LOST_PERSIST_TICKS;
            // NOT wallBetween(): once the mob has knocked out one block at eye level it can SEE the
            // target through its own peephole, the ray comes back clear, and the goal stopped with
            // a hole too small to walk through -- while the mob still could not reach anything.
            // What matters is whether there is still something in the way, at the heights the mob
            // has to fit through.
            return this.mob.distanceToSqr(target) >= MIN_BLOCKED_DISTANCE_SQR
                    && findSmashTarget(directionTo(target)) != null;
        }
        // The target is GONE, and that is the normal case rather than an edge one -- see the
        // class javadoc on losing sight. Keep digging at where it was for a few seconds.
        return this.digToward != null
                && --this.persistTicks > 0
                && findSmashTarget(directionToward(this.digToward)) != null;
    }

    @Override
    public void start() {
        this.swingCooldown = 0;
        this.persistTicks = TARGET_LOST_PERSIST_TICKS;
        LivingEntity target = this.mob.getTarget();
        this.digToward = target == null ? null : target.position();
    }

    @Override
    public void stop() {
        this.blockedTicks = 0;
        this.swingCooldown = 0;
        this.smashing = null;
        this.confinedTicks = 0;
        this.digToward = null;
        this.persistTicks = 0;
        this.strikeDelay = NO_STRIKE;
        // A goal that ends mid-punch would otherwise leave the flag set and the arm cocked for
        // ever: the pose is driven entirely by the synched boolean, and nothing else clears it.
        cancelBurst();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        // A burst already in flight owns the mob until it lands: the fist is committed, and
        // re-aiming at a different block halfway through a punch would slide the mob sideways
        // under a pose that is already swinging.
        if (this.burstTicks != NO_BURST) {
            tickBurst(target);
            return;
        }
        if (this.confined) {
            // A confined mob digs its way out whether or not it has anyone to dig towards.
            this.smashing = findEscapeTarget();
        } else if (target != null) {
            this.smashing = findSmashTarget(directionTo(target));
        } else {
            this.smashing = this.digToward == null
                    ? null
                    : findSmashTarget(directionToward(this.digToward));
        }
        if (this.smashing == null) {
            return;
        }
        // Push AT the wall, every tick this goal is active.
        //
        // Without this the goal is inert in the one case it exists for. It claims no flags, so the
        // melee goal keeps its own navigation, and that navigation walks the mob AROUND the wall to
        // the door -- so the mob is never standing at the wall, and `findSmashTarget` (which probes
        // two blocks ahead) finds nothing to hit. Running at a lower priority than the melee goal
        // means this executes after it within the tick, so this wins the tie.
        Vec3 face = Vec3.atCenterOf(this.smashing);
        this.mob.getNavigation().stop();
        this.mob.getLookControl().setLookAt(face.x, face.y, face.z);
        this.mob.getMoveControl().setWantedPosition(face.x, this.mob.getY(), face.z, 1.0D);

        // A swing already in the air lands whatever else is going on -- including on a tick the
        // cooldown would have returned early from.
        if (this.strikeDelay != NO_STRIKE && --this.strikeDelay < 0) {
            this.strikeDelay = NO_STRIKE;
            breakFace(target);
        }

        if (--this.swingCooldown > 0) {
            return;
        }

        // A mob with a punch winds up first; the blocks go on the tick the fist arrives. Everything
        // else swings and breaks in the same tick, which is what a Minotaur shouldering a wall
        // should look like.
        if (this.burster != null) {
            beginBurst();
            return;
        }

        this.swingCooldown = this.swingIntervalTicks;
        this.mob.swing(InteractionHand.MAIN_HAND);
        // NOT in this tick: the arm has only just started moving. See SWING_STRIKE_DELAY_TICKS.
        this.strikeDelay = SWING_STRIKE_DELAY_TICKS;
    }

    /**
     * Starts a wall burst: raise the flag, pick the fist, and let {@link #tickBurst} count.
     *
     * <p>The fist alternates rather than being random. A boss that throws the same arm every time
     * reads as one animation replayed; alternating reads as working at the wall, and it costs a
     * single bit that is already being synched.</p>
     */
    private void beginBurst() {
        this.burster.setBurstLeftFisted(!this.burster.isBurstLeftFisted());
        this.burster.setBursting(true);
        this.burstTicks = 0;
    }

    /**
     * Counts a burst out: rooted through the wind-up, blocks on the impact tick, still committed
     * through the recovery.
     *
     * <p>The mob is held still for the whole blow. It is already standing at the wall -- it got
     * here by pushing at it -- and letting the move control keep shoving while the punch plays
     * walks it into its own hole.</p>
     */
    private void tickBurst(LivingEntity target) {
        Vec3 face = Vec3.atCenterOf(this.smashing);
        this.mob.getNavigation().stop();
        this.mob.getLookControl().setLookAt(face.x, face.y, face.z);

        int windup = Math.max(1, this.burster.getBurstWindupTicks());
        int recovery = Math.max(0, this.burster.getBurstRecoveryTicks());
        this.burstTicks++;
        if (this.burstTicks == windup) {
            breakFace(target);
        } else if (this.burstTicks >= windup + recovery) {
            this.burster.setBursting(false);
            this.burstTicks = NO_BURST;
            // The interval is measured from the END of the blow, not its start, so lengthening the
            // wind-up does not silently shorten the gap between punches.
            this.swingCooldown = this.swingIntervalTicks;
        }
    }

    private void cancelBurst() {
        if (this.burster != null) {
            this.burster.setBursting(false);
        }
        this.burstTicks = NO_BURST;
    }

    /**
     * Takes the blocks out: {@link #blocksPerSwing} of them, or as much of the passage as is left.
     */
    private void breakFace(LivingEntity target) {
        // The cached block was chosen when the blow began -- which, for a burst, was a wind-up ago.
        // A player can mine it out from the other side in that time, and a shard burst wearing the
        // texture of air is not what that should look like.
        if (this.smashing != null && this.mob.level().getBlockState(this.smashing).isAir()) {
            this.smashing = nextTarget(target);
        }
        if (this.smashing == null) {
            return;
        }
        // A SWING TAKES SEVERAL BLOCKS, not one. One block a swing meant a Minotaur needed six
        // swings to open a doorway it could walk through (2 wide x 3 tall), which read as mining
        // rather than as something shouldering a wall down. Each block is re-found rather than
        // batched up front: destroying one changes what the next-best target is.
        int shardsEach = Math.max(1, this.shardsPerSwing / this.blocksPerSwing);
        for (int i = 0; i < this.blocksPerSwing; i++) {
            BlockPos pos = i == 0 ? this.smashing : nextTarget(target);
            if (pos == null) {
                break;
            }
            // destroyBlock plays the break sound and particles and honours loot, so a smashed wall
            // leaves its bricks on the floor rather than vanishing
            this.mob.level().destroyBlock(pos, true, this.mob);
            throwShards(pos, target, shardsEach);
        }
    }

    /**
     * Throws the debris burst off a block this swing has just broken.
     *
     * <p>The burst itself lives in {@link SmashDebris}, shared with the boulder: a player has no
     * reason to expect a wall to come apart differently depending on whether a fist or a thrown
     * rock arrived.</p>
     */
    private void throwShards(BlockPos pos, LivingEntity target, int count) {
        // Aimed at the target where there is one. Escaping confinement there is nobody to aim at,
        // and null makes the burst follow the mob's own heading out -- which is the way it is
        // digging.
        SmashDebris.throwShards(this.mob, pos, count, this.shardBaseDamage,
                target == null ? null : target.position());
    }


    /**
     * The block standing between the mob's eyes and its target's, or null if it can see out.
     *
     * <p>This is the trigger, and it is the question the goal should have been asking from the
     * start. The three attempts before it all asked something indirect &mdash; am I colliding, can
     * the navigation reach, am I getting closer &mdash; and each was defeated by a different
     * ordinary situation. "Is there a wall between us" is answered by looking, in one ray cast, and
     * is not confounded by pathing, by doors, or by the mob being shoved around by its own charge.</p>
     *
     * <p>It also settles the fight with {@code ChargeAttackGoal} for free, and that is worth stating
     * because it is the reason this goal can take the MOVE flag safely: the charge requires line of
     * sight and this requires the absence of it. <strong>The two can never want the mob at the same
     * time.</strong> Clear line, the mob charges; wall in the way, the mob digs.</p>
     *
     * <p><strong>This starts the goal and does not continue it.</strong> Continuing on it was a bug:
     * the first block out of a wall is at eye level, so the mob could immediately see through its
     * own peephole, the ray came back clear, and it walked away from a hole it could not fit through.
     * {@code canContinueToUse} asks {@link #findSmashTarget} instead &mdash; is there still something
     * in the way at the heights I have to fit through.</p>
     *
     * <p>Only the FIRST block hit is considered. If that one cannot be broken &mdash; obsidian, a
     * chest, outside the leash &mdash; the mob does not dig, rather than hunting along the ray for
     * something it is allowed to hit. Digging a hole that stops at an unbreakable second layer
     * would be worse than not starting.</p>
     */
    private BlockPos wallBetween(LivingEntity target) {
        if (this.mob.distanceToSqr(target) > MAX_WALL_SEARCH_DISTANCE_SQR) {
            return null;
        }
        Vec3 from = this.mob.getEyePosition();
        Vec3 to = target.getEyePosition();
        BlockHitResult hit = this.mob.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return isSmashable(pos, false) ? pos : null;
    }

    /**
     * Whether the navigation says the mob cannot get to {@code target}.
     *
     * <p>Only ever used as the SECOND entry route, never as the only one: on its own it fails the
     * moment a dungeon has a door, because a route exists and the mob politely takes it. Paired with
     * "and there is something diggable in front of me" it catches the case the ray cannot &mdash; a
     * hole already opened at eye level that the mob still cannot walk through.</p>
     */
    private boolean cannotReach(LivingEntity target) {
        Path path = this.mob.getNavigation().getPath();
        return path == null || !path.canReach();
    }

    /** Horizontal unit vector from the mob toward a remembered point. */
    private Vec3 directionToward(Vec3 point) {
        Vec3 to = new Vec3(point.x - this.mob.getX(), 0.0D, point.z - this.mob.getZ());
        return to.lengthSqr() < 1.0E-4D ? null : to.normalize();
    }

    /** The next block of the opening, whichever route this goal is running under. */
    private BlockPos nextTarget(LivingEntity target) {
        if (this.confined) {
            return findEscapeTarget();
        }
        if (target != null) {
            return findSmashTarget(directionTo(target));
        }
        return this.digToward == null ? null : findSmashTarget(directionToward(this.digToward));
    }

    /** Horizontal unit vector from the mob toward {@code target}. */
    private Vec3 directionTo(LivingEntity target) {
        Vec3 to = new Vec3(target.getX() - this.mob.getX(), 0.0D, target.getZ() - this.mob.getZ());
        return to.lengthSqr() < 1.0E-4D ? null : to.normalize();
    }

    /**
     * Whether the mob has been walled in long enough to start digging out.
     *
     * <p>Re-tested only every {@link #CONFINEMENT_CHECK_TICKS}; the answer is cached between
     * checks. The flood fill below is far too expensive to run every tick, and confinement is not a
     * state that changes between one tick and the next.</p>
     */
    private boolean confinedLongEnough() {
        if (--this.confinementCheckCooldown <= 0) {
            this.confinementCheckCooldown = CONFINEMENT_CHECK_TICKS;
            this.confined = isConfined();
        }
        if (!this.confined) {
            this.confinedTicks = 0;
            return false;
        }
        return ++this.confinedTicks >= CONFINED_GRACE_TICKS;
    }

    /**
     * Whether the mob is sealed in: no route through open space to anywhere
     * {@link #CONFINEMENT_ESCAPE_RADIUS} away.
     *
     * <p>A flood fill rather than "am I touching walls on all four sides", because confinement is
     * not a property of the block you are standing in &mdash; a Minotaur in the middle of a sealed
     * 5&times;5 room touches nothing and is no less trapped. The fill is bounded twice, by
     * {@link #CONFINEMENT_SEARCH_CAP} cells and by the escape radius, so the cost is fixed however
     * open the surroundings are; reaching either bound means <em>not</em> confined and stops the
     * search immediately.</p>
     *
     * <p>Cells are tested for an empty collision shape, one cell at a time. That ignores the mob's
     * actual 1&times;2 footprint, so a gap it cannot really fit through still counts as an escape
     * route. The error is deliberately in that direction: the failure mode is a Minotaur that
     * declines to break a wall, not one that breaks a wall it had no need to.</p>
     */
    private boolean isConfined() {
        Level level = this.mob.level();
        BlockPos origin = this.mob.blockPosition();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            if (seen.size() >= CONFINEMENT_SEARCH_CAP) {
                return false;
            }
            BlockPos pos = queue.poll();
            if (Math.abs(pos.getX() - origin.getX()) >= CONFINEMENT_ESCAPE_RADIUS
                    || Math.abs(pos.getZ() - origin.getZ()) >= CONFINEMENT_ESCAPE_RADIUS
                    || pos.getY() - origin.getY() >= CONFINEMENT_ESCAPE_RADIUS) {
                return false;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.contains(next)
                        && level.getBlockState(next).getCollisionShape(level, next).isEmpty()) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        // the fill closed without reaching open space
        return true;
    }

    /**
     * The block to swing at when digging out rather than digging towards someone.
     *
     * <p>Tries the way the mob is looking first, then the other three horizontals, then up. Facing
     * first so the mob appears to choose a wall and commit to it rather than chewing whichever
     * neighbour the loop happened to visit; the fallbacks matter because the facing wall may be
     * bedrock, a chest, or outside the leash while another is none of those.</p>
     */
    private BlockPos findEscapeTarget() {
        Direction facing = this.mob.getDirection();
        BlockPos found = findSmashTarget(Vec3.atLowerCornerOf(facing.getNormal()), true);
        if (found != null) {
            return found;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction == facing) {
                continue;
            }
            found = findSmashTarget(Vec3.atLowerCornerOf(direction.getNormal()), true);
            if (found != null) {
                return found;
            }
        }
        BlockPos above = this.mob.blockPosition().above(2);
        return isSmashable(above, true) ? above : null;
    }

    private boolean griefingAllowed() {
        return this.mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /**
     * The first smashable block between the mob and its target, searched chest height first, then
     * head, then knee &mdash; the order a thing this size would actually swing at, and it keeps the
     * mob from digging at its own feet when the real obstruction is in front of its face.
     */
    private BlockPos findSmashTarget(Vec3 direction) {
        return findSmashTarget(direction, false);
    }

    private BlockPos findSmashTarget(Vec3 direction, boolean escaping) {
        if (direction == null || direction.lengthSqr() < 1.0E-4D) {
            return null;
        }
        // The opening is derived from the mob's OWN FOOTPRINT rather than being a single column.
        // A Minotaur is a full block wide, so it straddles two columns whenever it is not perfectly
        // centred -- and a one-column hole is one it cannot walk through however tall it is. Moving
        // its bounding box forward and clearing every column that box covers gives exactly the hole
        // this mob needs, and gives a different (correct) hole for any other mob the goal is
        // attached to, with no width constant to keep in step with the entity's registration.
        AABB body = this.mob.getBoundingBox();
        int feetY = Mth.floor(body.minY);
        int topRow = passageRows(body) - 1;
        for (int step = 1; step <= 2; step++) {
            AABB ahead = body.move(direction.x * step, 0.0D, direction.z * step);
            for (int dy = 0; dy <= topRow; dy++) {
                for (int x = Mth.floor(ahead.minX); x <= Mth.floor(ahead.maxX - EDGE_BIAS); x++) {
                    for (int z = Mth.floor(ahead.minZ); z <= Mth.floor(ahead.maxZ - EDGE_BIAS); z++) {
                        BlockPos pos = new BlockPos(x, feetY + dy, z);
                        if (isSmashable(pos, escaping)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * How many rows above its feet the mob has to clear, read off its own bounding box.
     *
     * <p>Rows are cleared floor first, then body, then head. It used to be chest-head-knee, "the
     * order a thing this size would swing at", and that was the wrong idea entirely: the goal is not
     * to hit a wall convincingly, it is to make a <strong>hole the mob can walk through</strong>.
     * Carving from the floor up means the passage becomes usable at the moment the last row goes.
     *
     * <p><strong>The count used to be the constant three</strong>, on the reasoning that it was the
     * same three blocks for anything this goal is sensibly attached to. That held only while the
     * Minotaur was the only consumer. A 7-block Stone Colossus would have carved a 3-tall hole it
     * cannot enter &mdash; and then gone quiet with the wall still standing, because
     * {@link #findSmashTarget} would find nothing left in those three rows. Taking it off the box,
     * exactly as the columns already are, gives the Minotaur the same three rows it always had and
     * the Colossus the seven it needs, with no constant to keep in step with an entity's
     * registration.</p>
     */
    private static int passageRows(AABB body) {
        // The highest row the body occupies, not the row above it: a box from y to y+2.6 needs
        // y, y+1 and y+2 clear and nothing more. EDGE_BIAS keeps a box that ends exactly on a
        // boundary from claiming the row above, the same reason the columns use it.
        return Math.max(1, Mth.ceil(body.maxY - EDGE_BIAS) - Mth.floor(body.minY));
    }

    private boolean isSmashable(BlockPos pos, boolean escaping) {
        Level level = this.mob.level();
        // The leash, tested FIRST: it is the cheapest way to say no for a mob that has followed a
        // player out of the dungeon, and it short-circuits the state lookups below.
        //
        // ESCAPING IS EXEMPT, deliberately. The leash exists to stop a mob that followed a player
        // out of a dungeon from chewing through whatever the player built; it was never meant to
        // seal a mob in a box for ever, and outside a dungeon it would do exactly that -- which is
        // the behaviour Mark reported on 2026-09-08. The exemption stays narrow because the trigger
        // is: it fires only for a mob with NO route out at all, which a free-roaming Minotaur in
        // the overworld never is. Trapping one is the only way to reach it, and a player who seals
        // a Minotaur into a room has asked the question this answers.
        if (!escaping && !this.smashableArea.test(pos)) {
            return false;
        }
        // WHAT may be broken is shared with the boulder; WHERE is this goal's, above.
        return SmashDebris.isBreakable(level, pos, this.maxHardness);
    }
}
