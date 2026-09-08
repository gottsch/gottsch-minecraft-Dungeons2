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

import mod.gottsch.forge.dungeons2.core.entity.projectile.SmashShard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
 * <p>Inside a generated dungeon only, by default &mdash; see {@link DungeonBounds}. Wall-breaking
 * is acceptable where the walls are this mod's own; the same mob loose in the overworld is a
 * griefing engine pointed at whatever the player built, and getting it there takes no bug at all,
 * only a player who runs and a mob that follows. The area is a constructor parameter so the policy
 * stays the caller's, but the convenience constructor supplies the leash rather than leaving it
 * off, because forgetting it is the expensive mistake.</p>
 *
 * <h2>When it swings</h2>
 * <p>Only when the mob is <em>actually blocked</em>: it needs a live target, it needs to be pressed
 * against something ({@code horizontalCollision}), and it needs to have been in that state for
 * {@link #blockedGraceTicks} continuously. The grace period is what separates "the wall is in my
 * way" from "I brushed a doorframe on the way past", and without it the mob chews up a room while
 * merely walking through it.
 *
 * <p>The goal claims no {@link Goal.Flag}s deliberately, so it runs <em>alongside</em> the melee and
 * charge goals rather than instead of them. That is what makes it read correctly: the mob keeps
 * shoving into the wall under its own movement goal while this one takes the wall apart in front of
 * it, instead of stopping to mine.
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public class SmashBlocksGoal extends Goal {

    /** Shards per swing when the caller does not say. Enough to read as a burst, few enough to dodge. */
    private static final int DEFAULT_SHARDS_PER_SWING = 5;
    /**
     * Default {@code baseDamage}. An {@link net.minecraft.world.entity.projectile.AbstractArrow}
     * deals {@code ceil(speed * baseDamage)}, so at the speeds below this lands as 1&ndash;2 hearts'
     * worth per shard &mdash; a reason to stand back, not a second melee attack.
     */
    private static final double DEFAULT_SHARD_BASE_DAMAGE = 4.0D;

    /** How far the burst is turned from "straight out of the block" toward the mob's target. */
    private static final double ROOMWARD_BIAS = 0.45D;
    /** Constant upward component, so shards arc and fall instead of skimming the floor. */
    private static final double LIFT = 0.35D;
    private static final float MIN_SHARD_SPEED = 0.45F;
    private static final float MAX_SHARD_SPEED = 0.85F;
    private static final float SHARD_INACCURACY = 6.0F;
    /** How far inside the broken block an edge origin is pulled. See {@code throwShards}. */
    private static final double EDGE_INSET = 0.12D;
    private static final float MIN_SHARD_SCALE = 0.15F;
    private static final float MAX_SHARD_SCALE = 0.30F;

    private final Mob mob;
    /** Ticks of continuous blockage before the first swing. */
    private final int blockedGraceTicks;
    /** Ticks between swings, so a wall comes apart at a readable pace rather than instantly. */
    private final int swingIntervalTicks;
    private final float maxHardness;
    private final int shardsPerSwing;
    private final double shardBaseDamage;
    private final Predicate<BlockPos> smashableArea;

    private int blockedTicks;
    private int swingCooldown;
    private BlockPos smashing;

    public SmashBlocksGoal(Mob mob, int blockedGraceTicks, int swingIntervalTicks, float maxHardness) {
        // The default leash is the dungeon itself, chosen as the DEFAULT rather than offered as an
        // option because the unleashed behaviour is the dangerous one: a caller that wants a mob
        // able to smash anywhere has to say so and will think about it while typing, and a caller
        // that forgets gets the safe answer.
        this(mob, blockedGraceTicks, swingIntervalTicks, maxHardness,
                DEFAULT_SHARDS_PER_SWING, DEFAULT_SHARD_BASE_DAMAGE,
                pos -> DungeonBounds.contains(mob.level(), pos));
    }

    /**
     * @param shardsPerSwing how many {@link SmashShard}s a swing throws; 0 disables them entirely,
     *                       which is the way to get the pre-2026-09-07 behaviour back for a mob
     *                       that should break walls quietly.
     * @param shardBaseDamage the shards' {@code baseDamage} &mdash; see
     *                        {@link #DEFAULT_SHARD_BASE_DAMAGE} for how that becomes real damage.
     */
    public SmashBlocksGoal(Mob mob, int blockedGraceTicks, int swingIntervalTicks, float maxHardness,
                           int shardsPerSwing, double shardBaseDamage,
                           Predicate<BlockPos> smashableArea) {
        this.mob = mob;
        this.blockedGraceTicks = blockedGraceTicks;
        this.swingIntervalTicks = swingIntervalTicks;
        this.maxHardness = maxHardness;
        this.shardsPerSwing = shardsPerSwing;
        this.shardBaseDamage = shardBaseDamage;
        this.smashableArea = smashableArea;
    }

    @Override
    public boolean canUse() {
        if (!griefingAllowed()) {
            // reset, so turning the rule back on does not immediately fire a swing that has been
            // "charging" the whole time it was off
            this.blockedTicks = 0;
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.blockedTicks = 0;
            return false;
        }
        if (!this.mob.horizontalCollision) {
            this.blockedTicks = 0;
            return false;
        }
        if (++this.blockedTicks < this.blockedGraceTicks) {
            return false;
        }
        return findSmashTarget(target) != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return griefingAllowed()
                && target != null
                && target.isAlive()
                && this.mob.horizontalCollision
                && findSmashTarget(target) != null;
    }

    @Override
    public void start() {
        this.swingCooldown = 0;
    }

    @Override
    public void stop() {
        this.blockedTicks = 0;
        this.swingCooldown = 0;
        this.smashing = null;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.smashing = findSmashTarget(target);
        if (this.smashing == null) {
            return;
        }
        if (--this.swingCooldown > 0) {
            return;
        }
        this.swingCooldown = this.swingIntervalTicks;

        this.mob.swing(InteractionHand.MAIN_HAND);
        // read the state BEFORE the break -- the shards wear it, and a moment later the position
        // is air
        BlockState smashed = this.mob.level().getBlockState(this.smashing);
        // destroyBlock plays the break sound and particles and honours loot, so a smashed wall
        // leaves its bricks on the floor rather than vanishing
        this.mob.level().destroyBlock(this.smashing, true, this.mob);
        throwShards(this.smashing, smashed, target);
    }

    /**
     * Throws a burst of {@link SmashShard}s off the block that was just broken.
     *
     * <p>They are a real hazard, not a particle effect: a player standing in front of a wall while
     * something comes through it takes a hit for it. That is the point &mdash; it gives the player
     * a reason to back off from a wall the mob is working on, rather than standing on the far side
     * of it and hitting a mob that cannot reach back.</p>
     *
     * <p>Shards are aimed outward from the block <em>blended with</em> the direction of the target,
     * so a wall comes apart into the room the mob is trying to reach rather than spraying evenly in
     * all directions. Vertical lift is small and always positive: chips arc and fall, they do not
     * fountain.</p>
     */
    private void throwShards(BlockPos pos, BlockState smashed, LivingEntity target) {
        Level level = this.mob.level();
        if (level.isClientSide || this.shardsPerSwing <= 0) {
            return;
        }
        Vec3 centre = Vec3.atCenterOf(pos);
        Vec3 toTarget = target.position().subtract(centre);
        toTarget = new Vec3(toTarget.x, 0.0D, toTarget.z);
        // a target directly above or below leaves no horizontal direction to throw along; fall back
        // to pure outward rather than normalizing a zero vector
        Vec3 roomward = toTarget.lengthSqr() < 1.0E-4D ? Vec3.ZERO : toTarget.normalize();

        for (int i = 0; i < this.shardsPerSwing; i++) {
            Vec3 edge = randomEdgePoint(pos);
            Vec3 outward = edge.subtract(centre);
            outward = outward.lengthSqr() < 1.0E-4D ? roomward : outward.normalize();

            // Pull the origin just inside the (now empty) block. On the boundary exactly, a shard
            // starts flush against whatever the wall's neighbour is and the very first collision
            // test kills it -- and most of a wall block's edges are shared with MORE WALL, so most
            // of the burst would wink out on the frame it spawned. Inside by a fraction it still
            // reads as coming off the edge and it has somewhere to fly.
            Vec3 origin = edge.subtract(outward.scale(EDGE_INSET));

            Vec3 heading = outward.scale(1.0D - ROOMWARD_BIAS).add(roomward.scale(ROOMWARD_BIAS));
            if (heading.lengthSqr() < 1.0E-4D) {
                continue;
            }
            heading = heading.normalize();
            // A shard aimed into the rest of the wall travels a few centimetres and stops, which
            // looks like a bug rather than like debris. Send those into the room instead: the edge
            // origin is what the eye reads, the direction is free to be the sensible one.
            if (roomward.lengthSqr() > 1.0E-4D && isSolidNeighbour(pos, heading)) {
                heading = roomward;
            }
            heading = heading.add(0.0D, LIFT, 0.0D);

            SmashShard shard = new SmashShard(level, this.mob, smashed, origin.x, origin.y, origin.z);
            shard.setBaseDamage(this.shardBaseDamage);
            shard.setKnockback(0);
            shard.setSilent(true);
            shard.setScale(MIN_SHARD_SCALE
                    + this.mob.getRandom().nextFloat() * (MAX_SHARD_SCALE - MIN_SHARD_SCALE));
            float speed = MIN_SHARD_SPEED
                    + this.mob.getRandom().nextFloat() * (MAX_SHARD_SPEED - MIN_SHARD_SPEED);
            shard.shoot(heading.x, heading.y, heading.z, speed, SHARD_INACCURACY);
            level.addFreshEntity(shard);
        }
    }

    /**
     * A random point on one of the block's twelve <strong>edges</strong>, never its middle.
     *
     * <p>Deliberate, and it is what makes a burst read as a block coming apart rather than as a
     * mob firing something. Spawned from the centre, every shard in a swing starts at the same
     * point and they leave in a visible starburst from a single spot &mdash; which looks like an
     * emitter. Started on the edges, they appear along the block's outline and the eye reads the
     * whole block as shattering.</p>
     *
     * <p>Two of the three axes are pinned to a face, leaving one free: that is an edge rather than
     * a face, and it puts the origins at the corners and margins where a real block would fracture
     * first. It also keeps every origin clear of the block's interior, which matters now that the
     * position is already air but its neighbours may not be.</p>
     */
    /**
     * Whether the block one step along {@code heading} would stop a shard immediately. Cheap enough
     * to run per shard, and it is one lookup against a chunk that is certainly loaded &mdash; the
     * mob is standing next to it.
     */
    private boolean isSolidNeighbour(BlockPos pos, Vec3 heading) {
        BlockPos neighbour = pos.relative(Direction.getNearest(heading.x, heading.y, heading.z));
        Level level = this.mob.level();
        return level.getBlockState(neighbour).isSolidRender(level, neighbour);
    }

    private Vec3 randomEdgePoint(BlockPos pos) {
        RandomSource random = this.mob.getRandom();
        int freeAxis = random.nextInt(3);
        double[] offset = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            offset[axis] = axis == freeAxis
                    ? random.nextDouble()          // anywhere along the edge
                    : (random.nextBoolean() ? 0.0D : 1.0D);  // pinned to a face
        }
        return new Vec3(pos.getX() + offset[0], pos.getY() + offset[1], pos.getZ() + offset[2]);
    }

    private boolean griefingAllowed() {
        return this.mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /**
     * The first smashable block between the mob and its target, searched chest height first, then
     * head, then knee &mdash; the order a thing this size would actually swing at, and it keeps the
     * mob from digging at its own feet when the real obstruction is in front of its face.
     */
    private BlockPos findSmashTarget(LivingEntity target) {
        Vec3 toTarget = new Vec3(target.getX() - this.mob.getX(), 0.0D, target.getZ() - this.mob.getZ());
        if (toTarget.lengthSqr() < 1.0E-4D) {
            return null;
        }
        Vec3 direction = toTarget.normalize();
        for (int step = 1; step <= 2; step++) {
            for (int dy : new int[] {1, 2, 0}) {
                BlockPos pos = BlockPos.containing(
                        this.mob.getX() + direction.x * step,
                        this.mob.getY() + dy,
                        this.mob.getZ() + direction.z * step);
                if (isSmashable(pos)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private boolean isSmashable(BlockPos pos) {
        Level level = this.mob.level();
        // The leash, tested FIRST: it is the cheapest way to say no for a mob that has followed a
        // player out of the dungeon, and it short-circuits the state lookups below.
        if (!this.smashableArea.test(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        // chests, spawners and the dungeons2 markers all carry one -- see the class javadoc
        if (state.hasBlockEntity()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F && hardness <= this.maxHardness;
    }
}
