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

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The beam's turn cap is what lets a player step out of it; these pin that it really is a cap.
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
class BeamMathTest {

    private static final double EPS = 1.0E-9D;
    private static final Vec3 NORTH = new Vec3(0.0D, 0.0D, -1.0D);
    private static final Vec3 EAST = new Vec3(1.0D, 0.0D, 0.0D);

    @Test
    void withinTheCapItArrives() {
        Vec3 target = new Vec3(Math.sin(0.01D), 0.0D, -Math.cos(0.01D));
        assertSame(target, BeamMath.rotateToward(NORTH, target, 0.05D));
    }

    @Test
    void beyondTheCapItTurnsExactlyTheCapTowardTheTarget() {
        double cap = Math.toRadians(4.0D);
        Vec3 turned = BeamMath.rotateToward(NORTH, EAST, cap);
        assertEquals(1.0D, turned.length(), EPS);
        assertEquals(cap, Math.acos(turned.dot(NORTH)), 1.0E-7D, "turned by the cap from where it was");
        assertEquals(Math.PI / 2 - cap, Math.acos(turned.dot(EAST)), 1.0E-7D, "and toward the target");
        assertEquals(0.0D, turned.y, EPS, "stays in the plane of the two vectors");
    }

    @Test
    void aTargetDirectlyBehindIsStillTurnedToward() {
        double cap = Math.toRadians(4.0D);
        Vec3 turned = BeamMath.rotateToward(NORTH, NORTH.scale(-1.0D), cap);
        assertEquals(1.0D, turned.length(), EPS);
        assertEquals(cap, Math.acos(turned.dot(NORTH)), 1.0E-7D);
    }

    @Test
    void perpendicularIsPerpendicularAndUnitIncludingStraightUp() {
        for (Vec3 v : new Vec3[] {NORTH, EAST, new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.3D, -0.9D, 0.2D).normalize()}) {
            Vec3 p = BeamMath.perpendicular(v);
            assertEquals(0.0D, p.dot(v), 1.0E-9D, "perpendicular to " + v);
            assertEquals(1.0D, p.length(), 1.0E-9D, "unit for " + v);
        }
    }
}
