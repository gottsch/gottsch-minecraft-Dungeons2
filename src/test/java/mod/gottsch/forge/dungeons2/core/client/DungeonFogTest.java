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
 * The dark fog's arithmetic (the mood biomes): it eases rather than snaps, deeper is closer, and it
 * never reaches past vanilla's fog.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class DungeonFogTest {

    private static final float VANILLA_FAR = 192f;
    private static final float VANILLA_NEAR = 172.8f;

    private static final int BIOME_RGB = 0xA24A1E;

    private static DungeonFog settled(Tier tier) {
        DungeonFog fog = new DungeonFog();
        for (int i = 0; i < 200; i++) {
            fog.update(tier, 0.05f, BIOME_RGB);
        }
        return fog;
    }

    @Test
    void outsideTheDungeonVanillaIsUntouched() {
        DungeonFog fog = settled(null);
        assertFalse(fog.active());
        assertEquals(VANILLA_FAR, fog.far(VANILLA_FAR), 1e-3);
        assertEquals(0.3f, fog.colour(0.3f, 0.4f, 0.5f)[0], 1e-3);
    }

    @Test
    void deeperBandsCloseTheFogInFurther() {
        float shallow = settled(Tier.SHALLOW).far(VANILLA_FAR);
        float middle = settled(Tier.MIDDLE).far(VANILLA_FAR);
        float deep = settled(Tier.DEEP).far(VANILLA_FAR);
        assertEquals(Tier.SHALLOW.far, shallow, 0.01);
        assertTrue(shallow > middle && middle > deep, shallow + " > " + middle + " > " + deep);
        assertTrue(settled(Tier.DEEP).colour(0f, 0f, 0f)[0] < settled(Tier.SHALLOW).colour(0f, 0f, 0f)[0]);
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
        fog.update(Tier.DEEP, 0.1f, BIOME_RGB);
        float first = fog.far(VANILLA_FAR);
        assertTrue(first < VANILLA_FAR && first > 100f, "one tenth of a second in: " + first);
        fog.update(Tier.DEEP, 0.1f, BIOME_RGB);
        assertTrue(fog.far(VANILLA_FAR) < first);
    }

    @Test
    void theFogStartsNearTheWall() {
        DungeonFog fog = settled(Tier.MIDDLE);
        float far = fog.far(VANILLA_FAR);
        assertEquals(far * DungeonFog.NEAR_FRACTION, fog.near(VANILLA_NEAR, far), 0.01);
    }

    /**
     * The time of day never reaches the fog: vanilla's colour darkens toward night, and a settled
     * band's colour is the same whatever vanilla hands in.
     */
    @Test
    void dayAndNightGiveTheSameColour() {
        for (Tier tier : Tier.values()) {
            DungeonFog fog = settled(tier);
            float[] noon = fog.colour(0.75f, 0.85f, 1f);
            float[] night = fog.colour(0.02f, 0.02f, 0.03f);
            assertEquals(0xA2 / 255f * tier.colourScale, noon[0], 0.01);
            assertEquals(0x4A / 255f * tier.colourScale, noon[1], 0.01);
            assertEquals(0x1E / 255f * tier.colourScale, noon[2], 0.01);
            assertEquals(noon[0], night[0], 0.01);
            assertEquals(noon[1], night[1], 0.01);
            assertEquals(noon[2], night[2], 0.01);
        }
    }

    /** A haze takes the biome's colour at full strength, and closes in nearer than the shallowest dark. */
    @Test
    void aHazeIsItsBiomesFullColour() {
        DungeonFog fog = settled(Tier.HAZE);
        assertEquals(0xA2 / 255f, fog.colour(0f, 0f, 0f)[0], 0.01);
        assertTrue(fog.far(VANILLA_FAR) < Tier.SHALLOW.far);
    }

    /** Leaving the dungeon fades back to vanilla's colour rather than cutting to it. */
    @Test
    void leavingFadesBackToVanilla() {
        DungeonFog fog = settled(Tier.MIDDLE);
        fog.update(null, 0.1f, 0);
        float own = 0xA2 / 255f * Tier.MIDDLE.colourScale;
        float red = fog.colour(1f, 1f, 1f)[0];
        assertTrue(red > own && red < 1f, "one tenth of a second out: " + red);
    }
}
