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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import com.mojang.serialization.Codec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * Which way a barrel vault runs, decided ONCE for all of its steps.
 *
 * <p>A barrel vault rises from the two walls it runs between and leaves its two ends flat against
 * the end walls: each step is drawn in across one axis only. Every step is a separate
 * {@link FieldSurfacePatternProvider}, planned one after another with the room's random, so the axis
 * cannot be decided per step &mdash; a square room drawing its own coin per step would lay one step
 * north-south and the next east-west. The vault's steps share one of these instead, and it answers
 * the same for every step of the same plan.</p>
 *
 * <p>"The same plan" is recognised by the random instance and the surface size: a later plan of the
 * same room with a freshly seeded random (another chunk's pass over the room) decides again, and
 * draws the same value, so the vault is the same in every chunk.</p>
 *
 * @author Mark Gottschling on 10/29/2026
 */
public final class BarrelAxis {

    /** How the axis is chosen. */
    public enum Mode implements StringRepresentable {
        /** Along the room's longer side; a square room picks one with the room's random. */
        LONG("long"),
        /** Runs east-west, rising from the north and south walls. */
        X("x"),
        /** Runs north-south, rising from the east and west walls. */
        Z("z");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
    }

    private final Mode mode;

    // the last decision, and the plan it was made for
    private RandomSource decidedFor;
    private int decidedU = -1;
    private int decidedV = -1;
    private boolean alongU;

    public BarrelAxis(Mode mode) {
        this.mode = mode;
    }

    /**
     * True when the vault runs along u (east-west, x), so its steps are drawn in across v only; false
     * when it runs along v. Draws from {@code random} only for a square room in {@link Mode#LONG}, and
     * then once per plan.
     */
    public boolean runsAlongU(int uSize, int vSize, RandomSource random) {
        if (random != decidedFor || uSize != decidedU || vSize != decidedV) {
            alongU = switch (mode) {
                case X -> true;
                case Z -> false;
                case LONG -> uSize != vSize ? uSize > vSize : random.nextBoolean();
            };
            decidedFor = random;
            decidedU = uSize;
            decidedV = vSize;
        }
        return alongU;
    }
}
