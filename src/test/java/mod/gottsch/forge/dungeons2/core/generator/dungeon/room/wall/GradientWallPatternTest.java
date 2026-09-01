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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.wall.GradientWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.WallPatternRegistry;
import mod.gottsch.forge.dungeons2.core.config.wall.WallPattern;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mud stratum's two-material wall gradient.
 *
 * <h2>Why the arithmetic is tested directly</h2>
 * <p>A wall is only <strong>3 to 8 rows</strong> tall. An off-by-one in the ramp is a third of the
 * gradient on a short wall, and the output is deliberately speckled &mdash; so in game it would look
 * like scatter rather than like a bug, and nobody would ever report it. {@code probabilityAt} is
 * pure and package-visible for exactly this.</p>
 */
class GradientWallPatternTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Methods, not static finals: a static field referencing Blocks.* is initialised when the
    // class loads, which is BEFORE @BeforeAll runs Bootstrap -- so the whole class dies with
    // "Not bootstrapped" and reports as initializationError rather than as a test failure.
    private static BlockState bottomState() {
        return Blocks.MUD_BRICKS.defaultBlockState();
    }

    private static BlockState topState() {
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    private static final double EPSILON = 1.0e-9;

    private static GradientWallPatternProvider provider(double bottom, double top, int hold) {
        return new GradientWallPatternProvider(bottomState(), topState(), bottom, top, hold);
    }

    // ---------- the ramp ----------

    @Test
    void theBottomRowIsTheBottomProbabilityAndTheTopRowIsTheTop() {
        GradientWallPatternProvider gradient = provider(1.0D, 0.0D, 0);

        // Both ENDPOINTS land exactly, which is the whole reason the ramp divides by vSize-1 rather
        // than vSize: dividing by vSize leaves the top row one step short of its authored value, so
        // a "0.0 at the top" gradient still sprinkles the bottom material into the highest row.
        assertEquals(1.0D, gradient.probabilityAt(0, 6), EPSILON);
        assertEquals(0.0D, gradient.probabilityAt(5, 6), EPSILON);
    }

    @Test
    void theRampFallsMonotonically() {
        GradientWallPatternProvider gradient = provider(0.9D, 0.05D, 2);
        double previous = Double.MAX_VALUE;
        for (int v = 0; v < 8; v++) {
            double p = gradient.probabilityAt(v, 8);
            assertTrue(p <= previous + EPSILON,
                    "row " + v + " is more likely to be the bottom material than the row below it");
            previous = p;
        }
    }

    @Test
    void theHoldKeepsTheFootAtFullBias() {
        GradientWallPatternProvider gradient = provider(0.9D, 0.05D, 2);

        // Rows 0 and 1 are the hold; the ramp starts at row 2. Without the hold a bare ramp starts
        // falling at row 1 and "mostly mud at the bottom" is only ever true of one row.
        assertEquals(0.9D, gradient.probabilityAt(0, 6), EPSILON);
        assertEquals(0.9D, gradient.probabilityAt(1, 6), EPSILON);
        assertEquals(0.9D, gradient.probabilityAt(2, 6), EPSILON);
        assertTrue(gradient.probabilityAt(3, 6) < 0.9D, "the ramp did not start after the hold");
        assertEquals(0.05D, gradient.probabilityAt(5, 6), EPSILON);
    }

    @Test
    void aHoldTallerThanTheWallKeepsEveryRowAtFullBias() {
        GradientWallPatternProvider gradient = provider(0.9D, 0.0D, 5);

        // A 3-row room with holdRows 5 was authored as "all bottom material". Inverting it -- or
        // dividing by a negative span -- would put the TOP material at the foot of the wall, which
        // is the one outcome the author certainly did not ask for.
        for (int v = 0; v < 3; v++) {
            assertEquals(0.9D, gradient.probabilityAt(v, 3), EPSILON, "row " + v);
        }
    }

    // ---------- the plan ----------

    @Test
    void everyCellIsFilledBecauseThisIsAMaterialNotATreatment() {
        SurfacePlan plan = provider(0.5D, 0.5D, 0).plan(7, 6, Direction.NORTH,
                RandomSource.create(0xD2_11L));

        // Unlike every other wall pattern this one is a FILL. A sparse cell here would show the
        // surface's base block through the gradient, which is the wall it is meant to replace.
        assertEquals(7 * 6, plan.markedCells());
        for (int u = 0; u < 7; u++) {
            for (int v = 0; v < 6; v++) {
                assertNotNull(plan.get(u, v), "unfilled cell at " + u + "," + v);
            }
        }
    }

    @Test
    void theBottomOfTheWallIsMostlyTheBottomMaterialAndTheTopIsMostlyTheOther() {
        // The behaviour Mark asked for, measured rather than asserted about the ramp: a wide wall
        // so one row is a real sample.
        SurfacePlan plan = provider(0.9D, 0.05D, 2).plan(400, 6, Direction.NORTH,
                RandomSource.create(0xD2_12L));

        assertTrue(countOf(plan, bottomState(), 0) > 300, "the foot of the wall is not mostly mud");
        assertTrue(countOf(plan, bottomState(), 5) < 60, "the top of the wall is not mostly cobble");
        assertTrue(countOf(plan, bottomState(), 3) > 40 && countOf(plan, bottomState(), 3) < 360,
                "the middle of the wall is not a mix");
    }

    private static int countOf(SurfacePlan plan, BlockState state, int v) {
        int count = 0;
        for (int u = 0; u < plan.uSize(); u++) {
            if (state.equals(plan.get(u, v))) {
                count++;
            }
        }
        return count;
    }

    // ---------- the schema ----------

    @Test
    void theTypeIsRegisteredAndDecodesFromItsAuthoredForm() {
        DataResult<WallPattern> result = WallPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "dungeons2:gradient",
                          "config": {
                            "bottom_block": "minecraft:mud_bricks",
                            "top_block": "minecraft:cobblestone",
                            "bottom_probability": 0.9,
                            "top_probability": 0.05,
                            "hold_rows": 2
                          }
                        }"""));

        WallPattern pattern = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        assertTrue(pattern instanceof GradientWallPattern);
        assertNotNull(pattern.provider());
    }

    @Test
    void bothMaterialsAreRequired() {
        // A gradient with one material is a plain wall and should be authored as one, so neither
        // field gets a default -- the closed schema turns the omission into a load error rather
        // than a wall that quietly draws in one colour.
        DataResult<WallPattern> result = WallPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "dungeons2:gradient",
                          "config": { "bottom_block": "minecraft:mud_bricks" }
                        }"""));
        assertTrue(result.result().isEmpty(), "a gradient with no top_block must not decode");
    }

    @Test
    void anUnresolvableMaterialDegradesTheWholePattern() {
        GradientWallPattern pattern = new GradientWallPattern("minecraft:mud_bricks",
                "minecraft:not_a_real_block", 0.9D, 0.05D, 2, java.util.Map.of(), java.util.Map.of());

        // Filling with the survivor would look AUTHORED and never be reported; a plain wall reads
        // as a plain wall. Same rule every other pattern here follows.
        assertNull(pattern.provider());
    }
}
