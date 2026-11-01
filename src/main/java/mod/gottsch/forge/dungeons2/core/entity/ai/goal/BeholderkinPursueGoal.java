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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Makes a Beholder-kin boss close on its target until it is within {@link #PROXIMITY}.
 *
 * <h2>Why a flyer needed this at all</h2>
 * <p>gmm's Beholder-kin do not chase. Their only movement is {@code BeholderkinRandomFloatAroundGoal},
 * which drifts them to random points near where they already are, and they fight from wherever that
 * leaves them. For a gmm monster that is right. For a Dungeons2 <em>boss</em> it meant a player could
 * step out of the room and the boss would never follow (Mark, 2026-09-10: "just like the minotaur, if
 * it can't reach the player, then it should cut a hole") &mdash; and cutting a hole is pointless for
 * something that will not then go through it.</p>
 *
 * <h2>It stops short of walls, on purpose</h2>
 * <p>gmm's {@code BeholderkinMoveControl} will not move at all if its body cannot travel the
 * <em>whole</em> straight line to the wanted point: one wall anywhere along it and it parks. So this
 * never asks for the target itself. It asks for the farthest point its body can reach along the way
 * &mdash; {@link AnnihilationRay#obstructionAhead} &mdash; which brings it up against the wall,
 * where {@link BeamBreachGoal} sees the obstruction and bores through it. The two goals share the
 * same geometry, so the wall this one stops at is exactly the one that one cuts.</p>
 *
 * <p>Straight at the target, never round by a door: a boss that takes the long way round is the
 * failure {@code SmashBlocksGoal} documents for the Minotaur, and Beholder-kin have no pathfinding
 * to take the long way with anyway.</p>
 *
 * <p>Claims {@link Goal.Flag#MOVE}, and is registered above the random float, so while it has
 * somewhere to go the float does not pull it elsewhere. Inside {@link #PROXIMITY} it lets go and the
 * mob hovers and casts as gmm intended.</p>
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
public class BeholderkinPursueGoal extends Goal {

    /**
     * How close it tries to get. Eight blocks: inside it the Beholder is a caster in range, not a
     * biter &mdash; it is meant to hang back and gaze, and this only stops it being left behind.
     * Also the line {@link BeamBreachGoal} uses between "shoot through the wall" (inside) and "cut a
     * way through to follow" (outside).
     */
    public static final double PROXIMITY = 8.0D;

    /** How often the wanted point is re-measured. The sweep is not free, and the target moves slowly. */
    private static final int REPLAN_TICKS = 10;
    /** Closer to a wall than this and there is nowhere left to go: the breach goal's turn. */
    private static final double MIN_TRAVEL = 0.5D;
    /** gmm's own speed for the float goal. */
    private static final double SPEED = 1.0D;

    private final Mob mob;
    private int replanCooldown;

    public BeholderkinPursueGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceToSqr(target) > PROXIMITY * PROXIMITY;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.replanCooldown = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--this.replanCooldown > 0) {
            return;
        }
        this.replanCooldown = REPLAN_TICKS;
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        Vec3 centre = AnnihilationRay.centreOf(this.mob, 1.0F);
        Vec3 toward = AnnihilationRay.pursuitPoint(this.mob, target).subtract(centre);
        // Only as far as brings it within PROXIMITY; flying the rest would put it in biting range.
        double distance = toward.length() - PROXIMITY * 0.75D;
        if (distance < MIN_TRAVEL) {
            return;
        }
        Vec3 aim = toward.normalize();
        AnnihilationRay.Obstruction ahead = AnnihilationRay.obstructionAhead(this.mob, aim, distance);
        // Stop one sweep step short of whatever is in the way -- the last place the body fits.
        double travel = ahead == null ? distance : ahead.distance() - 0.5D;
        if (travel < MIN_TRAVEL) {
            return;
        }
        Vec3 wanted = centre.add(aim.scale(travel));
        // MoveControl wants the entity's POSITION -- its feet -- not its centre.
        this.mob.getMoveControl().setWantedPosition(
                wanted.x, wanted.y - this.mob.getBbHeight() * 0.5D, wanted.z, SPEED);
    }
}
