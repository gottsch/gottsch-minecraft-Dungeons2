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
package mod.gottsch.forge.dungeons2.core.entity.projectile;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The vector arithmetic {@link AnnihilationRay} and its renderer share, kept apart from the
 * entity so it can be tested without a level or a registry.
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
public final class BeamMath {

    private BeamMath() {}

    /**
     * Turns unit vector {@code from} toward unit vector {@code to} by at most {@code maxRadians},
     * along the great circle between them.
     *
     * <p>This is what makes the beam <em>dodgeable</em>. A beam that snapped to its target every tick
     * would be a hitscan weapon with a light show on it; capped, it sweeps, and a player who moves
     * across it faster than it can turn gets out of it. Same lesson as {@code ChargeAttackGoal}
     * locking its target's position &mdash; an attack that steers perfectly is not an attack you can
     * play against.</p>
     *
     * <p>Exactly opposite vectors have no unique shortest arc, so any perpendicular is used rather
     * than returning {@code from} unchanged: a target directly behind the caster should still be
     * swept toward, not ignored.</p>
     */
    public static Vec3 rotateToward(Vec3 from, Vec3 to, double maxRadians) {
        double angle = Math.acos(Mth.clamp(from.dot(to), -1.0D, 1.0D));
        if (angle <= maxRadians) {
            return to;
        }
        double sin = Math.sin(angle);
        if (sin < 1.0E-6D) {
            Vec3 axis = perpendicular(from);
            return from.scale(Math.cos(maxRadians)).add(axis.scale(Math.sin(maxRadians))).normalize();
        }
        double t = maxRadians / angle;
        double a = Math.sin((1.0D - t) * angle) / sin;
        double b = Math.sin(t * angle) / sin;
        return from.scale(a).add(to.scale(b)).normalize();
    }

    /** Some unit vector at right angles to {@code v}. Which one is unspecified, but it is stable. */
    public static Vec3 perpendicular(Vec3 v) {
        Vec3 helper = Math.abs(v.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        return v.cross(helper).normalize();
    }
}
