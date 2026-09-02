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
import mod.gottsch.forge.dungeons2.core.config.wall.ArcadeWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.WallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.WallPatternRegistry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry of {@code dungeons2:arcade} (#78). Pure {@code (u, v)}, no room and no world, which
 * is the point of the {@code ISurfacePatternProvider} boundary.
 *
 * <p>Two things here are worth more than the cell counting. The <strong>shoulders</strong> are what
 * turn a doorframe into an arch, and they are a stair-orientation question &mdash; the most
 * error-prone thing in this package, and invisible in a screenshot taken from the wrong side. And a
 * wall too short has to draw <strong>nothing</strong>, because a clipped arcade is a row of legs
 * with no heads and reads as a fence somebody meant.</p>
 */
class ArcadeWallPatternProviderTest {

    private static BlockState rib;
    private static BlockState stair;
    private static BlockState impost;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        rib = Blocks.STONE_BRICKS.defaultBlockState();
        stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState();
        impost = Blocks.POLISHED_ANDESITE.defaultBlockState();
    }

    private static ArcadeWallPatternProvider arcade(int width, int height, int spacing) {
        return new ArcadeWallPatternProvider(rib, stair, impost, width, height, spacing);
    }

    private static int marked(SurfacePlan plan) {
        int count = 0;
        for (int u = 0; u < plan.uSize(); u++) {
            for (int v = 0; v < plan.vSize(); v++) {
                if (plan.get(u, v) != null) {
                    count++;
                }
            }
        }
        return count;
    }

    // ---- the outline ----------------------------------------------------------------------------

    /**
     * One arch, cell by cell. A 5x5 arch is two 3-row legs, two shoulders and a 3-cell crown, and
     * the inside is left <strong>null</strong> &mdash; blind, so whatever this composes over shows
     * through it.
     */
    @Test
    void oneArchIsLegsShouldersAndACrownAroundAnUntouchedOpening() {
        SurfacePlan plan = arcade(5, 5, 1).plan(5, 5, Direction.SOUTH, null);

        for (int v = 0; v < 3; v++) {
            assertNotNull(plan.get(0, v), "left leg at v=" + v);
            assertNotNull(plan.get(4, v), "right leg at v=" + v);
        }
        assertNotNull(plan.get(0, 3), "left shoulder");
        assertNotNull(plan.get(4, 3), "right shoulder");
        for (int u = 1; u <= 3; u++) {
            assertSame(rib, plan.get(u, 4), "crown at u=" + u);
        }

        // Blind: the opening is untouched, and so are the two cells outside the crown.
        for (int u = 1; u <= 3; u++) {
            for (int v = 0; v <= 3; v++) {
                assertNull(plan.get(u, v), "the opening must be left alone, at (" + u + "," + v + ")");
            }
        }
        assertNull(plan.get(0, 4), "the crown is inset one cell from each leg");
        assertNull(plan.get(4, 4));

        assertEquals(3 + 3 + 1 + 1 + 3, marked(plan), "6 leg cells, 2 shoulders, 3 crown");
    }

    /** The impost is the top leg cell on each side, directly under the shoulder. */
    @Test
    void theImpostSitsOnTheLegDirectlyUnderTheShoulder() {
        SurfacePlan plan = arcade(5, 5, 1).plan(5, 5, Direction.SOUTH, null);
        assertSame(impost, plan.get(0, 2));
        assertSame(impost, plan.get(4, 2));
        assertSame(rib, plan.get(0, 1), "and the leg below it is still the rib");
    }

    /** No impost authored: the leg runs all the way up in the rib block. */
    @Test
    void withNoImpostTheLegIsUniform() {
        SurfacePlan plan = new ArcadeWallPatternProvider(rib, stair, null, 5, 5, 1)
                .plan(5, 5, Direction.SOUTH, null);
        assertSame(rib, plan.get(0, 2));
        assertSame(rib, plan.get(0, 1));
    }

    // ---- the shoulders --------------------------------------------------------------------------

    /**
     * The shoulders are upside-down stairs facing <strong>away from the arch's centre</strong>: a
     * stair's full-height half sits on its own facing side, so that puts the mass on the outer
     * corner and the cut looking down into the opening.
     *
     * <p>Facing is checked against the run's own {@code u} direction rather than a hardcoded
     * compass point, because that is the thing that would silently mirror the arch on two of the
     * four walls.</p>
     */
    @Test
    void theShouldersTurnTheCornerAwayFromTheOpening() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SurfacePlan plan = arcade(5, 5, 1).plan(5, 5, facing, null);
            Direction along = ArcadeWallPatternProvider.uDirection(facing);

            BlockState left = plan.get(0, 3);
            BlockState right = plan.get(4, 3);
            assertEquals(along.getOpposite(), left.getValue(StairBlock.FACING),
                    "left shoulder on the " + facing + " run");
            assertEquals(along, right.getValue(StairBlock.FACING),
                    "right shoulder on the " + facing + " run");
            assertEquals(Half.TOP, left.getValue(StairBlock.HALF), "shoulders are upside down");
            assertEquals(Half.TOP, right.getValue(StairBlock.HALF));
        }
    }

    /** {@code u} advances along +X on the Z-facing runs and +Z on the X-facing ones. */
    @Test
    void theUDirectionFollowsWallSurfacesOwnConvention() {
        assertEquals(Direction.EAST, ArcadeWallPatternProvider.uDirection(Direction.SOUTH));
        assertEquals(Direction.EAST, ArcadeWallPatternProvider.uDirection(Direction.NORTH));
        assertEquals(Direction.SOUTH, ArcadeWallPatternProvider.uDirection(Direction.EAST));
        assertEquals(Direction.SOUTH, ArcadeWallPatternProvider.uDirection(Direction.WEST));
    }

    /**
     * With no stair authored the arch squares off in the rib rather than leaving a gap. A partial
     * outline reads as damage, and saying that is the weathering pass's job.
     */
    @Test
    void withNoStairTheArchSquaresOffRatherThanBreaking() {
        SurfacePlan plan = new ArcadeWallPatternProvider(rib, null, null, 5, 5, 1)
                .plan(5, 5, Direction.SOUTH, null);
        assertSame(rib, plan.get(0, 3));
        assertSame(rib, plan.get(4, 3));
    }

    // ---- the run --------------------------------------------------------------------------------

    @Test
    void archesRepeatAtWidthPlusSpacingAndTheRunIsCentred() {
        // 5-wide arches with a 1-cell gap: pitch 6. A 20-wide wall fits 3 (5 + 6 + 6 = 17),
        // leaving 3 spare, split 1 before and 2 after.
        SurfacePlan plan = arcade(5, 5, 1).plan(20, 5, Direction.SOUTH, null);
        assertNull(plan.get(0, 0), "the leftover is split, not trailed off one end");
        assertNotNull(plan.get(1, 0), "first arch starts at u=1");
        assertNotNull(plan.get(7, 0), "second at u=7");
        assertNotNull(plan.get(13, 0), "third at u=13");
        assertNotNull(plan.get(17, 0), "and its right leg at u=17");
        assertNull(plan.get(18, 0));
        assertEquals(3 * 11, marked(plan), "three arches of eleven cells each");
    }

    /** Zero spacing pairs the legs into a two-cell pier, which is a real arcade and not clamped. */
    @Test
    void zeroSpacingPairsTheLegs() {
        SurfacePlan plan = arcade(5, 5, 0).plan(10, 5, Direction.SOUTH, null);
        assertEquals(2 * 11, marked(plan));
        assertNotNull(plan.get(4, 0), "the first arch's right leg");
        assertNotNull(plan.get(5, 0), "and the second arch's left leg, touching it");
    }

    // ---- what it refuses to draw ------------------------------------------------------------------

    /**
     * A wall too short draws NOTHING. This is the case that matters: the taper gives a wall 3 to 8
     * rows and most rooms are at the low end, so the default arch does not fit the commonest room.
     * Clipping would leave legs with no heads on every one of them.
     */
    @Test
    void aWallTooShortForAWholeArchDrawsNothing() {
        assertEquals(0, marked(arcade(5, 5, 1).plan(20, 4, Direction.SOUTH, null)));
        assertEquals(0, marked(arcade(5, 5, 1).plan(20, 3, Direction.SOUTH, null)));
        assertTrue(marked(arcade(5, 5, 1).plan(20, 5, Direction.SOUTH, null)) > 0,
                "and exactly enough rows is enough");
    }

    @Test
    void aWallTooNarrowForAWholeArchDrawsNothing() {
        assertEquals(0, marked(arcade(5, 5, 1).plan(4, 8, Direction.SOUTH, null)));
    }

    /** A taller wall leaves the arcade standing on the floor, not floating. */
    @Test
    void aTallerWallLeavesTheArchOnTheFloor() {
        SurfacePlan plan = arcade(5, 5, 1).plan(5, 8, Direction.SOUTH, null);
        assertNotNull(plan.get(0, 0), "the leg still starts at the floor");
        assertNull(plan.get(0, 5), "and the wall above the crown is left plain");
        assertNull(plan.get(2, 7));
    }

    // ---- the schema -------------------------------------------------------------------------------

    private static DataResult<WallPattern> parse(String json) {
        return WallPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void theTypeIsRegisteredAndDecodesFromItsAuthoredForm() {
        DataResult<WallPattern> result = parse("""
                {
                  "type": "dungeons2:arcade",
                  "config": {
                    "block": "minecraft:stone_bricks",
                    "stair_block": "minecraft:stone_brick_stairs",
                    "impost_block": "minecraft:polished_andesite",
                    "width": 7, "height": 6, "spacing": 2
                  }
                }""");
        WallPattern pattern = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        ArcadeWallPattern arcade = (ArcadeWallPattern) pattern;
        assertEquals(7, arcade.width());
        assertEquals(6, arcade.height());
        assertEquals(2, arcade.spacing());
        assertNotNull(arcade.provider());
    }

    /** The rib is required; the shoulders and the impost are not, and degrade INDEPENDENTLY. */
    @Test
    void onlyTheRibIsRequired() {
        assertTrue(parse("""
                {"type": "dungeons2:arcade", "config": {"stair_block": "minecraft:stone_brick_stairs"}}""")
                .result().isEmpty(), "an arcade with no block must not decode");
        assertNotNull(parse("""
                {"type": "dungeons2:arcade", "config": {"block": "minecraft:stone_bricks"}}""")
                .result().orElseThrow().provider(), "a bare rib is a squared arcade, not an error");
    }

    /** An unresolvable rib degrades the WHOLE pattern; an unresolvable shoulder degrades only itself. */
    @Test
    void theTwoOptionalBlocksDegradeIndependentlyOfTheRib() {
        assertNull(new ArcadeWallPattern("dungeons2:no_such_block").provider(),
                "no rib means no pattern at all");
        ArcadeWallPattern noStair = new ArcadeWallPattern("minecraft:stone_bricks",
                "dungeons2:no_such_block", "minecraft:polished_andesite");
        assertNotNull(noStair.provider(), "a missing shoulder squares the arch off, it does not "
                + "take the arcade down with it");
    }

    @Test
    void aStrayKeyIsALoadError() {
        assertTrue(parse("""
                {"type": "dungeons2:arcade",
                 "config": {"block": "minecraft:stone_bricks", "widht": 5}}""").result().isEmpty());
    }

    /**
     * A height above 8 is rejected rather than silently drawing nothing: the taper (#51) caps a
     * wall at 8 rows, so a bigger number could never mean more than 8 does.
     */
    @Test
    void aHeightTallerThanAnyWallIsALoadError() {
        assertTrue(parse("""
                {"type": "dungeons2:arcade",
                 "config": {"block": "minecraft:stone_bricks", "height": 9}}""").result().isEmpty());
    }

    /** Narrower than three cells is a pilaster, and there is already a type for that. */
    @Test
    void aWidthBelowThreeIsALoadError() {
        assertTrue(parse("""
                {"type": "dungeons2:arcade",
                 "config": {"block": "minecraft:stone_bricks", "width": 2}}""").result().isEmpty());
    }

    @Test
    void everyBlockFieldReadsAMaterialRole() {
        ArcadeWallPattern resolved = (ArcadeWallPattern)
                new ArcadeWallPattern("$rib", "$stair", "$impost")
                        // The resolver is handed the role NAME, already stripped of its $.
                        .withRoles(role -> "minecraft:" + role);
        assertEquals("minecraft:rib", resolved.block());
        assertEquals("minecraft:stair", resolved.stairBlock().orElseThrow());
        assertEquals("minecraft:impost", resolved.impostBlock().orElseThrow());
        assertEquals(ArcadeWallPatternProvider.DEFAULT_WIDTH, resolved.width(),
                "and keeps what it did not resolve");
    }

    @Test
    void anArcadeOfLiteralsIsNotEvenCopied() {
        ArcadeWallPattern pattern = new ArcadeWallPattern("minecraft:stone_bricks");
        assertSame(pattern, pattern.withRoles(role -> "minecraft:dirt"));
        assertFalse(pattern.stairBlock().isPresent());
    }
}
