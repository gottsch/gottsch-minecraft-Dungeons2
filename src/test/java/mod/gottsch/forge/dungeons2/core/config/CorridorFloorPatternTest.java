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
package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.floor.CellLocalFloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.SpeckleFloorPattern;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corridors take a <strong>cell-local</strong> floor pattern, and only a cell-local one.
 *
 * <p>Raised in game by Mark, 2026-09-09: "the corridor speckle pattern doesn't seem to work.. i
 * haven't seen 1 square_stone_brick". It never could &mdash; the {@code pattern} he had authored
 * was on the motif's {@code floor} section, which is the ROOM floor, and {@code CorridorConfig} had
 * no pattern field at all. The stated reason was that a border ring or checkerboard needs a
 * room-sized rectangle while a corridor is a 1-3 cell run, which is true of most patterns and
 * simply not true of {@code speckle}.</p>
 *
 * <p>So the capability is narrow on purpose, and both halves of that need saying out loud: a
 * cell-local pattern paves a corridor, and a rectangle-shaped one is a <strong>load error</strong>.
 * The second is the one worth a test &mdash; the alternative implementation, skipping a pattern a
 * corridor cannot use, would leave an author looking at a plain passage with nothing to read.</p>
 *
 * @author Mark Gottschling on Sep 9, 2026
 */
class CorridorFloorPatternTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DataResult<CorridorConfig> decode(String pattern) {
        String json = "{\"floor\": \"minecraft:cobblestone\","
                + " \"alternate_floor\": \"minecraft:stone_bricks\","
                + " \"ceiling\": \"minecraft:stone_bricks\", \"height\": 7"
                + (pattern == null ? "" : ", \"pattern\": " + pattern) + "}";
        return CorridorConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void aSpeckleCanPaveACorridor() {
        DataResult<CorridorConfig> result = decode("""
                {"type": "dungeons2:speckle", "config": {
                   "primary_block": "minecraft:cobblestone",
                   "secondary_block": "minecraft:andesite",
                   "probability": 0.5}}""");
        CorridorConfig config = result.result().orElseThrow(
                () -> new AssertionError("speckle was rejected: " + result.error().orElseThrow()));

        CellLocalFloorPattern.CellFloor cells = config.cellFloor();
        assertNotNull(cells, "the corridor decoded a speckle but hands back no cell painter");

        RandomSource random = RandomSource.create(7L);
        Set<BlockState> drawn = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            drawn.add(cells.at(i, 0, random));
        }
        assertTrue(drawn.contains(Blocks.COBBLESTONE.defaultBlockState()), "no primary block drawn");
        assertTrue(drawn.contains(Blocks.ANDESITE.defaultBlockState()),
                "the accent never appeared at probability 0.5 over 400 cells");
    }

    /**
     * The mode Mark asked for: no {@code primary_block}, so the accent lands ON the corridor's own
     * floor pair instead of repaving over it. A declined cell comes back null and the pair's roll
     * stands.
     */
    @Test
    void aSpeckleWithNoPrimaryBlockAccentsTheFloorPairInsteadOfReplacingIt() {
        CorridorConfig config = decode("""
                {"type": "dungeons2:speckle", "config": {
                   "secondary_block": "minecraft:andesite",
                   "probability": 0.5}}""").result().orElseThrow();

        CellLocalFloorPattern.CellFloor cells = config.cellFloor();
        assertNotNull(cells, "an accent-only speckle hands back no painter");

        RandomSource random = RandomSource.create(7L);
        int accented = 0;
        int declined = 0;
        for (int i = 0; i < 400; i++) {
            BlockState state = cells.at(i, 0, random);
            if (state == null) {
                declined++;
            } else {
                assertEquals(Blocks.ANDESITE.defaultBlockState(), state,
                        "an accent-only speckle drew something other than its accent");
                accented++;
            }
        }
        assertTrue(accented > 0, "the accent never appeared");
        assertTrue(declined > 0, "every cell was claimed, so the floor pair is still being erased");
    }

    /**
     * The stream-length rule from {@link CellLocalFloorPattern}. A corridor piece builds its whole
     * placement list in one pass and is clipped to chunks afterwards, so nothing may make the draw
     * count depend on the cell -- a pattern that short-circuited at probability 0 would shorten the
     * stream and move every later cell in the run.
     */
    @Test
    void aSpeckleConsumesExactlyOneValuePerCellEvenAtProbabilityZero() {
        CorridorConfig config = decode("""
                {"type": "dungeons2:speckle", "config": {
                   "primary_block": "minecraft:cobblestone",
                   "secondary_block": "minecraft:andesite",
                   "probability": 0.0}}""").result().orElseThrow();

        RandomSource painted = RandomSource.create(11L);
        for (int i = 0; i < 50; i++) {
            config.cellFloor().at(i, 0, painted);
        }
        RandomSource counted = RandomSource.create(11L);
        for (int i = 0; i < 50; i++) {
            counted.nextFloat();
        }
        assertEquals(counted.nextLong(), painted.nextLong(),
                "the two streams have diverged, so the painter did not consume exactly one value "
                        + "per cell");
    }

    @Test
    void aCheckerboardKeepsItsPhaseAcrossTheOrigin() {
        CorridorConfig config = decode("""
                {"type": "dungeons2:checkerboard", "config": {
                   "primary_block": "minecraft:cobblestone",
                   "secondary_block": "minecraft:andesite"}}""").result().orElseThrow();

        CellLocalFloorPattern.CellFloor cells = config.cellFloor();
        RandomSource unused = RandomSource.create(1L);
        // -1 and +1 are both odd sums with z=0, so a `%` implementation flips the phase between
        // them and leaves a seam down the line x=0. floorMod does not.
        assertEquals(cells.at(-1, 0, unused), cells.at(1, 0, unused),
                "the checkerboard changes phase at the world origin");
        assertEquals(cells.at(-2, 0, unused), cells.at(2, 0, unused),
                "the checkerboard changes phase at the world origin");
    }

    @Test
    void aRectangleShapedPatternIsALoadError() {
        DataResult<CorridorConfig> result = decode("""
                {"type": "dungeons2:border", "config": {
                   "inset": 1,
                   "corner_block": "minecraft:andesite",
                   "edge_left_block": "minecraft:stone_bricks",
                   "edge_right_block": "minecraft:stone_bricks"}}""");

        assertTrue(result.error().isPresent(),
                "a border ring was accepted on a corridor; it would draw a quarter of itself "
                        + "across a passage");
        assertTrue(result.error().orElseThrow().message().contains("cell-local"),
                "the error should say what the rule is, got: "
                        + result.error().orElseThrow().message());
    }

    @Test
    void aCorridorWithNoPatternHandsBackNoPainter() {
        assertNull(decode(null).result().orElseThrow().cellFloor(),
                "a corridor authoring no pattern must fall through to its floor/alternate_floor "
                        + "pair, byte for byte as before");
    }

    /** What classic actually ships, since that is the thing Mark went looking for. */
    @Test
    void classicAccentsItsCorridorsWithSquareStoneBrick() {
        CorridorConfig corridor = MotifConfigs.load("classic").forFloor(1).corridor();
        assertTrue(corridor.pattern().isPresent(),
                "classic's corridors author no floor pattern, so no square_stone_brick can appear "
                        + "in one -- the floor/alternate_floor pair is only two blocks wide");
        // Accent, not repave: the cobblestone/stone-brick mix is the corridor's identity and the
        // square brick is meant to show THROUGH it. A primary_block here would erase the pair.
        assertTrue(corridor.pattern().get() instanceof SpeckleFloorPattern speckle
                        && speckle.primaryBlock().isEmpty(),
                "classic's corridor speckle names a primary_block, which repaves the passage "
                        + "instead of accenting it: " + corridor.pattern().get());
    }
}
