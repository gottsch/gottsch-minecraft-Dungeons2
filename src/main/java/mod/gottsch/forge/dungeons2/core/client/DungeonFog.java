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
package mod.gottsch.forge.dungeons2.core.client;

/**
 * The darkness a dungeon floor's fog closes in with (#129 step 3). Pure arithmetic and state, with
 * no client classes, so it is testable headless; {@link DungeonFogEvents} feeds it the camera's
 * biome each frame.
 *
 * <p>A biome can set fog COLOUR but not fog DISTANCE, which is why this is code. Each value eases
 * toward its target rather than snapping, so walking from one band into the next reads as the
 * dark settling or lifting rather than a cut.
 *
 * <p>Three values ease independently: how much the dark applies at all ({@code darkness}, 0 = vanilla
 * fog), how close the dark wall stands ({@code darkFar}, never past vanilla's), and the colour scale.
 *
 * <p><strong>No held-light push, deliberately.</strong> One was built (a torch moved the wall out
 * ~22 blocks) and removed the same day: in an unlit corridor what you see is block light, not fog,
 * so moving the wall changed nothing visible. Held light is left to a dynamic-lights mod (Mark,
 * 2026-10-26).
 *
 * @author Mark Gottschling on 10/26/2026
 */
public final class DungeonFog {

    /** How far a band's dark lets you see, and how dark its fog colour turns. Deeper is closer and darker. */
    public enum Tier {
        SHALLOW(40f, 0.6f),
        MIDDLE(28f, 0.4f),
        DEEP(16f, 0.2f);

        /** Blocks to the fog wall. */
        public final float far;
        /** What the fog colour is multiplied by. */
        public final float colourScale;

        Tier(float far, float colourScale) {
            this.far = far;
            this.colourScale = colourScale;
        }
    }

    /** Where the fog starts, as a fraction of the wall's distance: near enough to read as murk. */
    public static final float NEAR_FRACTION = 0.25f;
    /** Easing rate, per second: about 1.5 s to settle most of the way. */
    public static final float RATE = 2f;

    private float darkness;
    private float darkFar = Tier.MIDDLE.far;
    private float colourScale = 1f;

    /**
     * Eases toward this frame's targets.
     *
     * @param tier the camera's band, or null outside the dungeon (or where the dark must not apply)
     * @param seconds time since the last update
     */
    public void update(Tier tier, float seconds) {
        float k = 1f - (float) Math.exp(-RATE * Math.max(0f, seconds));
        darkness += ((tier == null ? 0f : 1f) - darkness) * k;
        // Outside the dungeon the wall and colour hold where they were, so leaving fades the dark
        // out from the last band's settings instead of first sliding to some neutral one.
        if (tier != null) {
            darkFar += (tier.far - darkFar) * k;
            colourScale += (tier.colourScale - colourScale) * k;
        }
        if (darkness < 0.001f) {
            darkness = 0f;
        }
    }

    /** Whether the dark applies at all; when not, vanilla's fog is left untouched. */
    public boolean active() {
        return darkness > 0f;
    }

    /** The fog end, given vanilla's. Never further than vanilla's. */
    public float far(float vanillaFar) {
        float wall = Math.min(vanillaFar, darkFar);
        return vanillaFar + (wall - vanillaFar) * darkness;
    }

    /** The fog start, given vanilla's and this frame's {@link #far}. */
    public float near(float vanillaNear, float far) {
        float target = Math.min(vanillaNear, far * NEAR_FRACTION);
        return vanillaNear + (target - vanillaNear) * darkness;
    }

    /** What to multiply the fog colour by. */
    public float colourScale() {
        return 1f + (colourScale - 1f) * darkness;
    }
}
