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
package mod.gottsch.forge.dungeons2.core.entity.ai;

import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import mod.gottsch.forge.dungeons2.core.entity.projectile.SmashShard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a block coming apart looks like: the {@link SmashShard} burst, and the test for whether a
 * block may be broken at all.
 *
 * <p><strong>Shared on purpose.</strong> Two things in this mod break masonry &mdash; a mob taking a
 * wall down with its fists ({@code SmashBlocksGoal}) and a boulder landing on one
 * ({@code Boulder}) &mdash; and a player has no reason to expect the debris to behave differently
 * depending on which arrived. Every number and every correction below was paid for once, in game,
 * and a second copy is a second thing to get wrong later.</p>
 *
 * @author Mark Gottschling on Sep 14, 2026
 */
public final class SmashDebris {

    /**
     * How far the burst is turned from "straight out of the block" toward where it is aimed.
     *
     * <p>Was 0.45, i.e. the minority partner, so most of a burst went wherever that edge of the
     * block happened to face &mdash; which for a block in a wall is mostly MORE WALL. Mark,
     * 2026-09-09: "i only see the block shards in the immediate area where the block is broken - i
     * never see them fly at me." At 0.75 the room is the dominant direction and the edge supplies
     * character rather than aim.</p>
     */
    private static final double ROOMWARD_BIAS = 0.75D;
    /** Constant upward component, so shards arc and fall instead of skimming the floor. */
    private static final double LIFT = 0.35D;
    /**
     * Launch speed and spread, and the damage the caller passes, are gmm's bone-shard numbers
     * verbatim ({@code TaintedSkeleton.spawnShrapnel}: {@code shoot(dx, dy, dz, 0.9F, 12.0F)}).
     * Deliberate (Mark, 2026-09-08) &mdash; the two are the same kind of shrapnel and should hit the
     * same, not merely look alike.
     */
    private static final float SHARD_SPEED = 0.9F;
    private static final float SHARD_INACCURACY = 12.0F;
    /**
     * How far an edge origin is pulled back toward the middle of the broken block, as a fraction
     * of the way there.
     *
     * <p>Was an absolute 0.12 along the outward diagonal, which left the shard ~0.085 from EACH of
     * the two faces that edge belongs to. A shard moves {@link #SHARD_SPEED} of a block on its
     * first tick, so that is not clearance, it is a rounding error &mdash; and the neighbours of a
     * wall block are mostly wall. Pulling a third of the way to the centre puts the origin in air
     * the break just made, while still reading as coming off the edge.</p>
     */
    private static final double EDGE_PULL = 0.35D;
    /**
     * Render scale, where 1.0 is the bone shard's own size. A little variety so a burst is not three
     * identical objects; centred on 1.0 rather than shrunk, because the shard rig is already sized
     * to be a chip rather than a block.
     */
    private static final float MIN_SHARD_SCALE = 0.85F;
    private static final float MAX_SHARD_SCALE = 1.15F;
    /** How much of a heading has to point along an axis before that neighbour can stop it. */
    private static final double SOLID_PROBE_COMPONENT = 0.3D;

    private SmashDebris() {}

    /**
     * Throws a burst of {@link SmashShard}s off a block that has just been broken.
     *
     * <p>They are a real hazard, not a particle effect: a player standing in front of a wall while
     * something comes through it takes a hit for it. That is the point &mdash; it gives the player a
     * reason to back off from a wall being worked on, rather than standing on the far side of it and
     * hitting a mob that cannot reach back.</p>
     *
     * @param source who is credited with the hit; also supplies the level and the randomness.
     * @param aimAt  where the wall is coming apart TOWARDS, or null to throw it away from
     *               {@code source} &mdash; which is what a mob digging its way out of confinement,
     *               or a boulder landing at the end of its own flight, actually wants.
     */
    public static void throwShards(Mob source, BlockPos pos, int count, double baseDamage, Vec3 aimAt) {
        Level level = source.level();
        if (level.isClientSide || count <= 0) {
            return;
        }
        Vec3 centre = Vec3.atCenterOf(pos);
        Vec3 toward = aimAt != null ? aimAt.subtract(centre) : centre.subtract(source.position());
        toward = new Vec3(toward.x, 0.0D, toward.z);
        // an aim point directly above or below leaves no horizontal direction to throw along; fall
        // back to pure outward rather than normalizing a zero vector
        Vec3 roomward = toward.lengthSqr() < 1.0E-4D ? Vec3.ZERO : toward.normalize();

        for (int i = 0; i < count; i++) {
            Vec3 edge = randomEdgePoint(source.getRandom(), pos);
            Vec3 outward = edge.subtract(centre);
            outward = outward.lengthSqr() < 1.0E-4D ? roomward : outward.normalize();

            // Pull the origin back toward the middle of the (now empty) block. The point randomly
            // chosen is on an EDGE, which belongs to two faces at once, so an origin nudged along
            // the outward diagonal sits a hair from BOTH of them -- and a shard clears most of a
            // block on its first tick. Lerping toward the centre gives it air on every side while
            // still reading as coming off the edge.
            Vec3 origin = edge.add(centre.subtract(edge).scale(EDGE_PULL));

            Vec3 heading = outward.scale(1.0D - ROOMWARD_BIAS).add(roomward.scale(ROOMWARD_BIAS));
            if (heading.lengthSqr() < 1.0E-4D) {
                continue;
            }
            heading = heading.normalize().add(0.0D, LIFT, 0.0D).normalize();
            // A shard aimed into the rest of the wall travels a few centimetres and stops, which
            // looks like a bug rather than like debris. Send those into the room instead: the edge
            // origin is what the eye reads, the direction is free to be the sensible one.
            //
            // Tested AFTER the lift and across every axis it actually moves along. Testing the
            // single nearest Direction of the pre-lift heading -- which is what this did -- checked
            // one of the three neighbours a diagonal shard can clip, and never the one the lift had
            // just steered it into.
            if (roomward.lengthSqr() > 1.0E-4D && runsIntoSolid(level, pos, heading)) {
                heading = roomward.add(0.0D, LIFT, 0.0D).normalize();
            }

            SmashShard shard = new SmashShard(
                    DungeonsEntities.SMASH_SHARD_ENTITY.get(), source, level);
            shard.setPos(origin.x, origin.y, origin.z);
            shard.setBaseDamage(baseDamage);
            shard.setScale(MIN_SHARD_SCALE
                    + source.getRandom().nextFloat() * (MAX_SHARD_SCALE - MIN_SHARD_SCALE));
            shard.shoot(heading.x, heading.y, heading.z, SHARD_SPEED, SHARD_INACCURACY);
            level.addFreshEntity(shard);
        }
    }

    /**
     * Whether a block is the kind of thing this mod's brutes may break, ignoring WHERE it is &mdash;
     * the leash is the caller's, because it differs per mob.
     *
     * <p>Three exclusions, and none of them is a style preference. <strong>Anything with a block
     * entity</strong>: chests, spawners and the authoring markers all carry one, and smashing a
     * chest destroys generated loot while a spawner destroys an encounter &mdash; this is the guard
     * that keeps "it can break walls" from meaning "it can delete content". <strong>Anything harder
     * than {@code maxHardness}</strong>, which also excludes bedrock, since its destroy speed is
     * negative. And <strong>air and fluids</strong>, which are nothing to hit.</p>
     */
    public static boolean isBreakable(Level level, BlockPos pos, float maxHardness) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.0F && hardness <= maxHardness;
    }

    /**
     * A random point on one of the block's twelve <strong>edges</strong>, never its middle.
     *
     * <p>Deliberate, and it is what makes a burst read as a block coming apart rather than as a mob
     * firing something. Spawned from the centre, every shard starts at the same point and they leave
     * in a visible starburst from a single spot &mdash; which looks like an emitter. Started on the
     * edges, they appear along the block's outline and the eye reads the whole block as
     * shattering.</p>
     *
     * <p>Two of the three axes are pinned to a face, leaving one free: that is an edge rather than a
     * face, and it puts the origins at the corners and margins where a real block would fracture
     * first. It also keeps every origin clear of the block's interior, which matters because the
     * position is already air but its neighbours may not be.</p>
     */
    private static Vec3 randomEdgePoint(RandomSource random, BlockPos pos) {
        int freeAxis = random.nextInt(3);
        double[] offset = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            offset[axis] = axis == freeAxis
                    ? random.nextDouble()                    // anywhere along the edge
                    : (random.nextBoolean() ? 0.0D : 1.0D);  // pinned to a face
        }
        return new Vec3(pos.getX() + offset[0], pos.getY() + offset[1], pos.getZ() + offset[2]);
    }

    /**
     * Whether a shard leaving on this heading immediately runs into something solid.
     *
     * <p>Checks <strong>every axis the heading meaningfully moves along</strong>, not just the
     * nearest single {@link Direction}. A shard launched off an edge travels diagonally, so it can
     * be stopped by any of two or three neighbours; asking only about the dominant axis passed a
     * heading that clipped one of the others on its first tick, which is how a burst ended up lying
     * at the foot of the wall it came out of.</p>
     */
    private static boolean runsIntoSolid(Level level, BlockPos pos, Vec3 heading) {
        for (Direction direction : Direction.values()) {
            double component = heading.x * direction.getStepX()
                    + heading.y * direction.getStepY()
                    + heading.z * direction.getStepZ();
            // Only the axes it is actually going somewhere along. A component this small moves the
            // shard a few hundredths of a block in the tick that matters and cannot be what stops
            // it, so counting it would send half the burst roomward for no reason.
            if (component <= SOLID_PROBE_COMPONENT) {
                continue;
            }
            BlockPos neighbour = pos.relative(direction);
            if (level.getBlockState(neighbour).isSolidRender(level, neighbour)) {
                return true;
            }
        }
        return false;
    }
}
