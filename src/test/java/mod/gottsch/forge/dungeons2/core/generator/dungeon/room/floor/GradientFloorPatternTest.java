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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPatternRegistry;
import mod.gottsch.forge.dungeons2.core.config.floor.GradientFloorPattern;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor's two-material gradient &mdash; the wall gradient's counterpart, ramping from the walls
 * to the middle of the room instead of from the foot of a wall to its top.
 *
 * <h2>Why the arithmetic is tested directly</h2>
 * <p>Same reason {@code GradientWallPatternTest} gives: the ramp is short. A room reaches only 3 to
 * 6 cells from its wall to its centre, so an off-by-one is a third of the gradient, and the output
 * is deliberately speckled &mdash; in game it would look like scatter rather than like a bug.
 * {@code probabilityAt}, {@code distance} and {@code maxDistance} are pure and package-visible for
 * exactly this.</p>
 */
class GradientFloorPatternTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Methods, not static finals: a static field referencing Blocks.* initialises when the class
    // loads, which is BEFORE @BeforeAll runs Bootstrap.
    private static Block edgeBlock() {
        return Blocks.MUD_BRICKS;
    }

    private static Block centreBlock() {
        return Blocks.COBBLESTONE;
    }

    private static final double EPSILON = 1.0e-9;

    private static GradientFloorPatternProvider provider(double edge, double centre, int hold) {
        return new GradientFloorPatternProvider(edgeBlock(), centreBlock(), edge, centre, hold);
    }

    // ---------- the distance field ----------

    @Test
    void distanceIsMeasuredFromTheNearestEdgeOfTheFootprint() {
        // 9x9. The outer ring is 0 and the middle cell is 4 -- and a cell near a long wall takes its
        // distance from THAT wall, not from the room's centre, which is what makes the field read as
        // "swept clean where people walk" rather than as a circle painted in the middle.
        assertEquals(0, GradientFloorPatternProvider.distance(0, 4, 9, 9));
        assertEquals(0, GradientFloorPatternProvider.distance(4, 8, 9, 9));
        assertEquals(1, GradientFloorPatternProvider.distance(1, 6, 9, 9));
        assertEquals(4, GradientFloorPatternProvider.distance(4, 4, 9, 9));
    }

    @Test
    void theRampIsScaledToTheNARROWERAxis() {
        // A 5x15 hall reaches distance 2, not 7. Scaling against the long axis would leave the ramp
        // less than a third finished at the only centreline the room actually has, so a gradient
        // authored to reach cobblestone in the middle would never get there.
        assertEquals(2, GradientFloorPatternProvider.maxDistance(5, 15));
        assertEquals(2, GradientFloorPatternProvider.maxDistance(15, 5));
        assertEquals(4, GradientFloorPatternProvider.maxDistance(9, 9));
    }

    // ---------- the ramp ----------

    @Test
    void theWallRingIsTheEdgeProbabilityAndTheCentreIsTheCentreProbability() {
        GradientFloorPatternProvider gradient = provider(1.0D, 0.0D, 0);

        // Both ENDPOINTS land exactly, the same reason the wall's ramp divides by vSize-1: stopping
        // one step short would sprinkle the edge material into the middle of every room.
        assertEquals(1.0D, gradient.probabilityAt(0, 4), EPSILON);
        assertEquals(0.0D, gradient.probabilityAt(4, 4), EPSILON);
    }

    @Test
    void theRampFallsMonotonically() {
        GradientFloorPatternProvider gradient = provider(0.9D, 0.05D, 2);
        double previous = Double.MAX_VALUE;
        for (int d = 0; d <= 6; d++) {
            double p = gradient.probabilityAt(d, 6);
            assertTrue(p <= previous + EPSILON,
                    "distance " + d + " is more likely to be the edge material than the cell outside it");
            previous = p;
        }
    }

    @Test
    void theHoldKeepsTheCellsAtTheWallAtFullBias() {
        GradientFloorPatternProvider gradient = provider(0.9D, 0.05D, 2);

        // Rings 0 and 1 are the hold; the ramp starts at ring 2. Ring 0 is under the wall, so a hold
        // of 1 buys nothing a player can see and 2 is the smallest hold that shows as a band.
        assertEquals(0.9D, gradient.probabilityAt(0, 5), EPSILON);
        assertEquals(0.9D, gradient.probabilityAt(1, 5), EPSILON);
        assertEquals(0.9D, gradient.probabilityAt(2, 5), EPSILON);
        assertTrue(gradient.probabilityAt(3, 5) < 0.9D, "the ramp did not start after the hold");
        assertEquals(0.05D, gradient.probabilityAt(5, 5), EPSILON);
    }

    @Test
    void aHoldDeeperThanTheRoomKeepsEveryCellAtFullBias() {
        GradientFloorPatternProvider gradient = provider(0.9D, 0.0D, 5);

        // A room 3 cells to its centre with hold_cells 5 was authored as "all edge material".
        // Inverting it -- or dividing by a negative span -- would put the CENTRE material against
        // the walls, the one outcome the author certainly did not ask for.
        for (int d = 0; d <= 3; d++) {
            assertEquals(0.9D, gradient.probabilityAt(d, 3), EPSILON, "distance " + d);
        }
    }

    @Test
    void aRoomWithNoMiddleAtAllStillDraws() {
        // maxDistance 0 (a 2-wide strip) is span 0 even with no hold. It must not divide by zero and
        // must not invert: every cell is edge material, which is the only reading available.
        GradientFloorPatternProvider gradient = provider(1.0D, 0.0D, 0);
        assertEquals(0, GradientFloorPatternProvider.maxDistance(2, 9));
        assertEquals(1.0D, gradient.probabilityAt(0, 0), EPSILON);
    }

    // ---------- what it draws ----------

    @Test
    void everyCellIsFilledBecauseThisIsAMaterialNotATreatment() {
        List<BlockPlacement> out = new ArrayList<>();
        provider(0.5D, 0.5D, 0).build(9, 7, 0, 0, 64, RandomSource.create(0xD2_21L), out);

        // A FILL, like the wall version: it covers the room's whole footprint, so a scheme can name
        // it first and draw a border or a cross over it.
        assertEquals(9 * 7, out.size());
    }

    @Test
    void theWallsAreMostlyTheEdgeMaterialAndTheMiddleIsMostlyTheOther() {
        // The behaviour Mark asked for, measured rather than asserted about the ramp. A big room so
        // each ring is a real sample: 41x41 gives 160 cells on the outer ring.
        List<BlockPlacement> out = new ArrayList<>();
        provider(0.9D, 0.05D, 2).build(41, 41, 0, 0, 64, RandomSource.create(0xD2_22L), out);

        double atWall = edgeShareAtDistance(out, 41, 41, 0);
        double atCentre = edgeShareAtDistance(out, 41, 41, 20);
        double halfway = edgeShareAtDistance(out, 41, 41, 11);

        assertTrue(atWall > 0.8D, "the cells at the wall are not mostly mud: " + atWall);
        assertTrue(atCentre < 0.3D, "the middle of the room is not mostly cobble: " + atCentre);
        assertTrue(halfway > 0.2D && halfway < 0.8D, "halfway across is not a mix: " + halfway);
    }

    /** The share of cells at exactly {@code distance} from the edge that came out edge material. */
    private static double edgeShareAtDistance(List<BlockPlacement> out, int width, int depth,
                                              int distance) {
        int matched = 0;
        int total = 0;
        String edgeId = ForgeRegistries.BLOCKS.getKey(edgeBlock()).toString();
        for (BlockPlacement placement : out) {
            if (GradientFloorPatternProvider.distance(placement.getX(), placement.getZ(),
                    width, depth) != distance) {
                continue;
            }
            total++;
            if (edgeId.equals(placement.getBlockId())) {
                matched++;
            }
        }
        assertTrue(total > 0, "no cells at distance " + distance);
        return (double) matched / total;
    }

    // ---------- the schema ----------

    @Test
    void theTypeIsRegisteredAndDecodesFromItsAuthoredForm() {
        DataResult<FloorPattern> result = FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "dungeons2:gradient",
                          "config": {
                            "edge_block": "minecraft:mud_bricks",
                            "centre_block": "minecraft:cobblestone",
                            "edge_probability": 0.9,
                            "centre_probability": 0.05,
                            "hold_cells": 2
                          }
                        }"""));

        FloorPattern pattern = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        assertTrue(pattern instanceof GradientFloorPattern);
        assertNotNull(pattern.generator(FloorConfig.DEFAULT));
    }

    @Test
    void bothMaterialsAreRequired() {
        // A gradient with one material is a plain floor and should be authored as one, so neither
        // field gets a default -- the closed schema turns the omission into a load error rather than
        // a floor that quietly draws in one colour.
        DataResult<FloorPattern> result = FloorPatternRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "dungeons2:gradient",
                          "config": { "edge_block": "minecraft:mud_bricks" }
                        }"""));
        assertTrue(result.result().isEmpty(), "a gradient with no centre_block must not decode");
    }

    @Test
    void anUnresolvableBlockDegradesTheWholePatternToPlain() {
        GradientFloorPattern pattern = new GradientFloorPattern("minecraft:mud_bricks",
                "dungeons2:no_such_block", 1.0D, 0.0D, 0);

        // Not "fill with the survivor": a floor drawn entirely in one of the two materials looks
        // authored and would never be reported.
        assertTrue(pattern.generator(FloorConfig.DEFAULT) instanceof BasicFloorGenerator);
    }
}
