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

import mod.gottsch.forge.dungeons2.core.entity.projectile.AnnihilationRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Fires a {@link AnnihilationRay} in the situations gmm's {@code CastSpellGoal} never will: at a
 * target behind a wall, toward a target the caster cannot get to, and out of a room it is sealed in.
 *
 * <h2>Why the spell pool is not enough</h2>
 * <p>The beam is also in the Beholder's and Death Tyrant's ordinary spell pools, drawn at random
 * like the rest. But {@code CastSpellGoal} only charges while the caster can <em>see</em> its
 * target &mdash; out of sight the charge bleeds away &mdash; and it only ever shoots, never cuts a
 * way through. This goal covers what the pool cannot.</p>
 *
 * <h2>Three routes</h2>
 * <ul>
 *   <li><strong>Tunnel</strong> &mdash; the Minotaur's route, in light. The target is farther than
 *       {@link BeholderkinPursueGoal#PROXIMITY}, and the caster's body, swept straight toward it,
 *       runs into something before it could get that close. A {@link AnnihilationRay.Mode#TUNNEL}
 *       beam, bored to the caster's size so it can follow. <strong>An open door does not stop
 *       this</strong> (Mark, 2026-09-10): a Beholder is 3.5 tall and a dungeon doorway is two, so a
 *       door is not a way through for it, and the sweep knows that because it is the caster's own
 *       body being swept.</li>
 *   <li><strong>Strike through a wall</strong> &mdash; the target is within reach but out of sight,
 *       and the first block between them is burnable. A {@link AnnihilationRay.Mode#STRIKE} line
 *       at it: an attack, not a way through.</li>
 *   <li><strong>Escape</strong> &mdash; no target, and sealed in with no open route anywhere
 *       &mdash; see {@link #isConfined()}. An {@link AnnihilationRay.Mode#ESCAPE} bore, exempt
 *       from the leash.</li>
 * </ul>
 *
 * <p>Each charges for {@link #CHARGE_TICKS} first, with a green telegraph distinct from
 * {@code CastSpellGoal}'s witch motes. It claims no flags: the caster keeps floating (or
 * {@link BeholderkinPursueGoal} keeps it pressing at the wall) while it charges.</p>
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
public class BeamBreachGoal extends Goal {

    /** About two seconds of being blocked before it commits (canUse runs every other tick). */
    private static final int BLOCKED_GRACE_TICKS = 20;
    /** Half of {@code CastSpellGoal}'s 80: the breach is a response, not an opener. */
    private static final int CHARGE_TICKS = 40;
    /** Gap after a STRIKE beam before the next charge, counted from when the beam ends. */
    private static final int STRIKE_COOLDOWN_TICKS = 100;
    /**
     * Gap after a boring beam. Short, for the reason {@code MinotaurGoalsEvent} gives for its swing
     * interval: a boss that cannot reach the player is STUCK, and every tick of cooldown is time spent
     * waiting on it. The charge is the pacing; this only stops two beams overlapping.
     */
    private static final int BORE_COOLDOWN_TICKS = 20;
    /** 24 blocks. Farther than that and shooting through walls at someone reads as a machine. */
    private static final double MAX_STRIKE_DISTANCE_SQR = 24.0D * 24.0D;
    /** How often the tunnel sweep is re-run. It is not free, and a wall does not move. */
    private static final int EVALUATE_TICKS = 5;

    /** Ticks sealed in before it starts burning its way out. */
    private static final int CONFINED_GRACE_TICKS = 100;
    /** The fill is too expensive to run every tick, and confinement does not change tick to tick. */
    private static final int CONFINEMENT_CHECK_TICKS = 40;
    /** Cells the fill may visit before declaring the surroundings open. */
    private static final int CONFINEMENT_SEARCH_CAP = 160;
    /** Reaching this far from the caster, in any direction, counts as being out. */
    private static final int CONFINEMENT_ESCAPE_RADIUS = 6;
    /** How far along each horizontal it looks for a wall to burn out through. */
    private static final double ESCAPE_PROBE_DISTANCE = 8.0D;

    private static final DustParticleOptions TELEGRAPH =
            new DustParticleOptions(new Vector3f(0.45F, 1.0F, 0.3F), 1.2F);

    private final Mob mob;
    private int blockedTicks;
    private int chargeTicks;
    private boolean fired;
    private long cooldownUntil;
    /** What this run will fire. Set when canUse commits. */
    private AnnihilationRay.Mode mode;
    /** The last evaluation's answer, reused between sweeps. */
    private AnnihilationRay.Mode pending;
    private int evaluateCooldown;
    /** The horizontal an ESCAPE run burns along. */
    private Vec3 escapeAim;
    private boolean confined;
    private int confinedTicks;
    private int confinementCheckCooldown;

    public BeamBreachGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (this.mob.level().getGameTime() < this.cooldownUntil || !griefingAllowed()
                || AnnihilationRay.hasActiveBeam(this.mob)) {
            this.blockedTicks = 0;
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target != null && target.isAlive()) {
            this.confinedTicks = 0;
            if (--this.evaluateCooldown <= 0) {
                this.evaluateCooldown = EVALUATE_TICKS;
                this.pending = evaluate(target);
            }
            if (this.pending == null) {
                this.blockedTicks = 0;
                return false;
            }
            if (++this.blockedTicks < BLOCKED_GRACE_TICKS) {
                return false;
            }
            this.mode = this.pending;
            return true;
        }
        this.blockedTicks = 0;
        this.pending = null;
        if (confinedLongEnough()) {
            this.escapeAim = findEscapeAim();
            if (this.escapeAim != null) {
                this.mode = AnnihilationRay.Mode.ESCAPE;
                return true;
            }
        }
        return false;
    }

    /**
     * Which beam this target calls for, if any. Tunnel first: if the caster cannot get within
     * {@link BeholderkinPursueGoal#PROXIMITY}, getting there matters more than a shot through the
     * wall. Only then, a strike at a target it cannot see.
     */
    private AnnihilationRay.Mode evaluate(LivingEntity target) {
        if (this.mob.distanceTo(target) > BeholderkinPursueGoal.PROXIMITY && tunnelNeeded(target)) {
            return AnnihilationRay.Mode.TUNNEL;
        }
        return targetBehindBurnableWall(target) ? AnnihilationRay.Mode.STRIKE : null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.fired || !griefingAllowed() || this.mode == null) {
            return false;
        }
        if (this.mode == AnnihilationRay.Mode.ESCAPE) {
            return true;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        // A tunnel keeps charging even if the target is in view through the doorway -- seeing it
        // was never the problem. A strike stands down the moment it can see: the spell pool has it.
        return this.mode == AnnihilationRay.Mode.TUNNEL || !this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public void start() {
        this.chargeTicks = 0;
        this.fired = false;
    }

    @Override
    public void stop() {
        this.blockedTicks = 0;
        this.chargeTicks = 0;
        this.confinedTicks = 0;
        this.escapeAim = null;
        this.mode = null;
        this.pending = null;
        this.evaluateCooldown = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        ++this.chargeTicks;
        if (this.chargeTicks % 5 == 0 && this.mob.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(TELEGRAPH, this.mob.getX(), this.mob.getY(0.5D), this.mob.getZ(),
                    4, this.mob.getBbWidth() * 0.4D, this.mob.getBbHeight() * 0.3D,
                    this.mob.getBbWidth() * 0.4D, 0.0D);
        }
        if (this.chargeTicks < CHARGE_TICKS) {
            return;
        }
        LivingEntity target = this.mob.getTarget();
        Vec3 aim = switch (this.mode) {
            case ESCAPE -> this.escapeAim;
            case TUNNEL -> target == null ? null : AnnihilationRay.pursuitAim(this.mob, target);
            case STRIKE -> target == null ? null : AnnihilationRay.aimAt(this.mob, target);
        };
        boolean lit = aim != null && AnnihilationRay.fire(this.mob, aim, this.mode);
        this.fired = true;
        if (lit) {
            this.cooldownUntil = this.mob.level().getGameTime() + AnnihilationRay.LIFETIME_TICKS
                    + (this.mode == AnnihilationRay.Mode.STRIKE ? STRIKE_COOLDOWN_TICKS : BORE_COOLDOWN_TICKS);
        }
    }

    /**
     * Whether the caster's body, swept straight at the target, runs into something before it gets
     * within {@link BeholderkinPursueGoal#PROXIMITY} &mdash; and whether one beam can clear it.
     *
     * <p>Only obstructions short of the proximity ring count. A wall beyond it is one the caster
     * never has to pass: it can get close enough without it, and the strike route handles shooting
     * through it.</p>
     */
    private boolean tunnelNeeded(LivingEntity target) {
        Vec3 centre = AnnihilationRay.centreOf(this.mob, 1.0F);
        double reach = Math.min(
                AnnihilationRay.pursuitPoint(this.mob, target).distanceTo(centre) - BeholderkinPursueGoal.PROXIMITY,
                AnnihilationRay.MAX_RANGE);
        if (reach <= 0.0D) {
            return false;
        }
        AnnihilationRay.Obstruction ahead = AnnihilationRay.obstructionAhead(
                this.mob, AnnihilationRay.pursuitAim(this.mob, target), reach);
        return ahead != null && AnnihilationRay.canBoreThrough(this.mob, ahead.cells(), false);
    }

    /**
     * Whether the target is out of sight, in range, and the first thing in the way is something the
     * beam can burn. The last clause matters: a wall of bedrock or a chest is not worth a charge.
     */
    private boolean targetBehindBurnableWall(LivingEntity target) {
        if (this.mob.distanceToSqr(target) > MAX_STRIKE_DISTANCE_SQR
                || this.mob.getSensing().hasLineOfSight(target)) {
            return false;
        }
        Vec3 aim = AnnihilationRay.aimAt(this.mob, target);
        Vec3 from = AnnihilationRay.originAlong(this.mob, aim, 1.0F);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        BlockHitResult hit = this.mob.level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
        return hit.getType() == HitResult.Type.BLOCK
                && AnnihilationRay.canAnnihilate(this.mob, hit.getBlockPos(), false);
    }

    /**
     * The horizontal to burn out along: the way the caster is facing first, so it reads as choosing
     * a wall, then the other three. Null if every side is something it cannot bore through.
     */
    private Vec3 findEscapeAim() {
        Direction facing = this.mob.getDirection();
        if (canEscapeAlong(facing)) {
            return Vec3.atLowerCornerOf(facing.getNormal());
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != facing && canEscapeAlong(direction)) {
                return Vec3.atLowerCornerOf(direction.getNormal());
            }
        }
        return null;
    }

    private boolean canEscapeAlong(Direction direction) {
        AnnihilationRay.Obstruction ahead = AnnihilationRay.obstructionAhead(
                this.mob, Vec3.atLowerCornerOf(direction.getNormal()), ESCAPE_PROBE_DISTANCE);
        return ahead != null && AnnihilationRay.canBoreThrough(this.mob, ahead.cells(), true);
    }

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
     * Whether the caster is sealed in: no route through open space to anywhere
     * {@link #CONFINEMENT_ESCAPE_RADIUS} away.
     *
     * <p>{@code SmashBlocksGoal#isConfined}'s bounded flood fill, with one difference: escape counts
     * <em>downward</em> too. The Minotaur's version only credits reaching up, because a walker cannot
     * climb out of a pit; a flyer can leave by any open side, so any direction is out.</p>
     *
     * <p>One-cell granularity, like the Minotaur's, so a doorway counts as a way out. That is fine
     * <em>here</em> because escape is now only the no-target case: a caster with a target that is
     * held in by a too-small door takes the tunnel route instead, which sweeps its real size.</p>
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
                    || Math.abs(pos.getY() - origin.getY()) >= CONFINEMENT_ESCAPE_RADIUS
                    || Math.abs(pos.getZ() - origin.getZ()) >= CONFINEMENT_ESCAPE_RADIUS) {
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
        return true;
    }

    private boolean griefingAllowed() {
        return this.mob.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
}
