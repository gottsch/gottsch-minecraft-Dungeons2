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

import mod.gottsch.forge.dungeons2.core.client.DungeonFog.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dark fog's arithmetic (#129 step 3): it eases rather than snaps, deeper is closer, and it
 * never reaches past vanilla's fog.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class DungeonFogTest {

    private static final float VANILLA_FAR = 192f;
    private static final float VANILLA_NEAR = 172.8f;

    private static DungeonFog settled(Tier tier) {
        DungeonFog fog = new DungeonFog();
        for (int i = 0; i < 200; i++) {
            fog.update(tier, 0.05f);
        }
        return fog;
    }

    @Test
    void outsideTheDungeonVanillaIsUntouched() {
        DungeonFog fog = settled(null);
        assertFalse(fog.active());
        assertEquals(VANILLA_FAR, fog.far(VANILLA_FAR), 1e-3);
        assertEquals(1f, fog.colourScale(), 1e-3);
    }

    @Test
    void deeperBandsCloseTheFogInFurther() {
        float shallow = settled(Tier.SHALLOW).far(VANILLA_FAR);
        float middle = settled(Tier.MIDDLE).far(VANILLA_FAR);
        float deep = settled(Tier.DEEP).far(VANILLA_FAR);
        assertEquals(Tier.SHALLOW.far, shallow, 0.01);
        assertTrue(shallow > middle && middle > deep, shallow + " > " + middle + " > " + deep);
        assertTrue(settled(Tier.DEEP).colourScale() < settled(Tier.SHALLOW).colourScale());
    }

    /** At a short render distance the dark never sets the fog further than vanilla had it. */
    @Test
    void neverPastVanilla() {
        DungeonFog fog = settled(Tier.SHALLOW);
        assertEquals(32f, fog.far(32f), 1e-3);
        assertTrue(fog.near(28f, fog.far(32f)) <= 28f);
    }

    @Test
    void itEasesInRatherThanSnapping() {
        DungeonFog fog = new DungeonFog();
        fog.update(Tier.DEEP, 0.1f);
        float first = fog.far(VANILLA_FAR);
        assertTrue(first < VANILLA_FAR && first > 100f, "one tenth of a second in: " + first);
        fog.update(Tier.DEEP, 0.1f);
        assertTrue(fog.far(VANILLA_FAR) < first);
    }

    @Test
    void theFogStartsNearTheWall() {
        DungeonFog fog = settled(Tier.MIDDLE);
        float far = fog.far(VANILLA_FAR);
        assertEquals(far * DungeonFog.NEAR_FRACTION, fog.near(VANILLA_NEAR, far), 0.01);
    }

    /**
     * A haze holds the biome's own fog colour whatever vanilla handed in (vanilla darkens it toward
     * night), and closes in nearer than the shallowest dark.
     */
    @Test
    void aHazeHoldsItsOwnColour() {
        DungeonFog fog = new DungeonFog();
        for (int i = 0; i < 200; i++) {
            fog.update(Tier.HAZE, 0.05f, 0xA24A1E);
        }
        float[] night = fog.colour(0.02f, 0.02f, 0.03f);
        assertEquals(0xA2 / 255f, night[0], 0.01);
        assertEquals(0x4A / 255f, night[1], 0.01);
        assertEquals(0x1E / 255f, night[2], 0.01);
        assertTrue(fog.far(VANILLA_FAR) < Tier.SHALLOW.far);
    }

    /** A dark tier only scales vanilla's colour; it never pulls toward a glow. */
    @Test
    void aDarkTierOnlyScales() {
        DungeonFog fog = settled(Tier.DEEP);
        float[] colour = fog.colour(0.5f, 0.5f, 0.5f);
        assertEquals(0.5f * Tier.DEEP.colourScale, colour[0], 0.01);
    }
}
