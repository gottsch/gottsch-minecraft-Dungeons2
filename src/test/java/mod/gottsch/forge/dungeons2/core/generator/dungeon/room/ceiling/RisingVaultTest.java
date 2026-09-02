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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfacePatternEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.config.ceiling.FieldCeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.VaultedCeilingPattern;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonRoomPiece;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RISING vault (#68): a ceiling treatment that reaches up into the floor's own unspent budget
 * instead of hanging down into the room's headroom.
 *
 * <h2>What actually needs proving</h2>
 * <p>The arithmetic was nearly free &mdash; {@code CeilingSurface#emitProjected} already writes at
 * {@code ceilingY - depth}, so a negative depth landed above the plane before any of this was
 * written. The three things that were NOT free, and are what these tests are about:</p>
 * <ol>
 *   <li>the column under a raised block has to be EXCAVATED, including the plane cell the ceiling
 *       has just been paved with, or the room gains nothing and a slab of ceiling material is buried
 *       in the rock above it;</li>
 *   <li>the rise has to be clamped to what this floor actually has left over this room, or a vault
 *       opens into the stone buffer and then into the floor above;</li>
 *   <li>stepping has to come out of the emission ORDER, since two steps write the same cell.</li>
 * </ol>
 */
class RisingVaultTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String VAULT_BLOCK = "minecraft:polished_andesite";
    private static final String AIR = "minecraft:air";
    private static final String PLAIN_CEILING = "minecraft:stone_bricks";

    /** 9x9 interior 7x7, 6 high: ceiling plane at floorY + 5. */
    private static RoomData room() {
        return new RoomData(1, 10, 20, 9, 9, 6, RoomRole.NORMAL);
    }

    private static final int FLOOR_Y = 60;

    private static CeilingPatternEntry entry(SurfacePatternEntry... patterns) {
        return new CeilingPatternEntry(List.of(patterns));
    }

    /** A field of {@code inset} raised {@code rise} rows above the plane. */
    private static SurfacePatternEntry rising(int inset, int rise) {
        return new SurfacePatternEntry(
                new FieldCeilingPattern(VAULT_BLOCK, inset, Map.of()), 0, rise, SizeGate.UNBOUNDED);
    }

    private static List<BlockPlacement> render(int riseBudget, SurfacePatternEntry... patterns) {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCeilingGenerator()
                .withRiseBudget(riseBudget)
                .withCeilingPattern(CeilingPatternSelector.providerFor(Optional.of(entry(patterns))))
                .build(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        return out;
    }

    /**
     * What the world ends up looking like: the LAST placement in a cell wins, which is the renderer's
     * own rule and the rule the whole stepped vault depends on.
     */
    private static Map<String, String> settle(List<BlockPlacement> out) {
        Map<String, String> world = new HashMap<>();
        for (BlockPlacement bp : out) {
            world.put(bp.getX() + "," + bp.getY() + "," + bp.getZ(), bp.getBlockId());
        }
        return world;
    }

    private static String at(Map<String, String> world, int x, int y, int z) {
        return world.get(x + "," + y + "," + z);
    }

    // ---------- the column is opened ----------

    @Test
    void aRaisedFieldRoofsHighAndOpensEverythingBeneathIt() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        Map<String, String> world = settle(render(4, rising(1, 3)));

        // The middle of the 7x7 interior. The surface's origin is the room's + 1 (the wall ring is
        // not ceiling), so interior u,v = 3,3 is world x,z = 14,24 -- worth spelling out, because an
        // off-by-one here silently tests the cell next door, which a vault also covers.
        assertEquals(AIR, at(world, 14, ceilingY, 24),
                "the plane cell was left paved, so the room gained no headroom at all");
        assertEquals(AIR, at(world, 14, ceilingY + 1, 24));
        assertEquals(AIR, at(world, 14, ceilingY + 2, 24));
        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY + 3, 24), "the vault has no roof");
    }

    @Test
    void theCellsTheFieldDoesNotCoverKeepTheirCeiling() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        // inset 1 leaves the outermost ring of the INTERIOR (u,v = 0) on the plane -- the springing
        // the vault rises from, and the reason a rising vault does not need the walls to be taller.
        Map<String, String> world = settle(render(4, rising(1, 3)));

        assertEquals(PLAIN_CEILING, at(world, 11, ceilingY, 21), "the lip of the vault was excavated");
        assertEquals(null, at(world, 11, ceilingY + 1, 21),
                "nothing should be written above a cell the vault does not cover");
    }

    // ---------- stepping ----------

    @Test
    void stepsAuthoredAscendingLeaveOnlyTheHighestRoofOverACell() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        // The shape of a corbelled dome: three fields, each smaller and higher than the last.
        Map<String, String> world = settle(render(4, rising(1, 1), rising(2, 2), rising(3, 3)));

        // Centre cell (interior 3,3): covered by all three, so all three wrote to it. The highest
        // step's own excavation reopens what the two below it roofed, which is the whole mechanism.
        assertEquals(AIR, at(world, 14, ceilingY + 1, 24), "step 1 is still roofing the centre");
        assertEquals(AIR, at(world, 14, ceilingY + 2, 24), "step 2 is still roofing the centre");
        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY + 3, 24));

        // A cell only the first step covers stays at its own height: interior 1,1 -> world 12,22.
        assertEquals(AIR, at(world, 12, ceilingY, 22));
        assertEquals(VAULT_BLOCK, at(world, 12, ceilingY + 1, 22));
        assertEquals(null, at(world, 12, ceilingY + 2, 22), "the low step is not being over-dug");
    }

    // ---------- the budget clamp ----------

    @Test
    void aRiseIsClampedToWhatTheFloorHasLeftOverTheRoom() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        // Authored 4, but this floor has 2 rows spare. The vault must stop at 2 -- a rise past the
        // budget eats the stone buffer and then opens into the floor above.
        Map<String, String> world = settle(render(2, rising(1, 4)));

        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY + 2, 24));
        assertEquals(null, at(world, 14, ceilingY + 3, 24), "the clamp did not hold");
        assertEquals(null, at(world, 14, ceilingY + 4, 24), "the clamp did not hold");
    }

    @Test
    void withNoSpareBudgetTheVaultIsDrawnFlat() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        Map<String, String> world = settle(render(0, rising(1, 3)));

        // Clamped to a flush layer rather than dropped: the room reads as a plain ceiling in the
        // vault's material, which is the same picture as any other ceiling and never a hole.
        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY, 24));
        for (BlockPlacement bp : render(0, rising(1, 3))) {
            assertTrue(bp.getY() <= ceilingY, "nothing may be written above the plane: " + bp);
        }
    }

    @Test
    void aRoomWhoseSchemeDoesNotRiseIsUntouchedByAllOfThis() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        // The regression that matters most: every shipped scheme authors rise 0, and every one of
        // them must render exactly where it did before #68 even with a budget available.
        List<BlockPlacement> out = render(6,
                new SurfacePatternEntry(new FieldCeilingPattern(VAULT_BLOCK, 1, Map.of())));

        assertFalse(out.isEmpty());
        for (BlockPlacement bp : out) {
            assertEquals(ceilingY, bp.getY(), "a flush pattern moved: " + bp);
        }
    }

    // ---------- the vaulted TYPE ----------

    /** The whole dome as one entry: what an author actually writes. */
    private static SurfacePatternEntry vaulted(int steps, int stepHeight, int stepInset) {
        return new SurfacePatternEntry(new VaultedCeilingPattern(VAULT_BLOCK, steps, stepHeight,
                stepInset, Optional.empty(), Optional.empty(), SurfaceOrient.INWARD, Map.of()),
                0, 0, SizeGate.UNBOUNDED);
    }

    @Test
    void theVaultedTypeExpandsToExactlyTheHandStackedFields() {
        // The claim that justifies the type existing at all: it is sugar, not a second mechanism.
        // If these two ever diverge, one of them is drawing a dome the other cannot.
        Map<String, String> byType = settle(render(4, vaulted(3, 1, 1)));
        Map<String, String> byHand = settle(render(4, rising(1, 1), rising(2, 2), rising(3, 3)));

        assertEquals(byHand, byType);
    }

    @Test
    void everyStepOfTheVaultIsOneRowHigherAndOneCellFurtherIn() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        Map<String, String> world = settle(render(4, vaulted(3, 1, 1)));

        // Walking out from the middle of the 7x7 interior: 3 rows up at the centre, 2 one ring out,
        // then 1, then the plane itself. That profile IS the dome.
        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY + 3, 24), "the crown");
        assertEquals(VAULT_BLOCK, at(world, 13, ceilingY + 2, 23), "the second step");
        assertEquals(VAULT_BLOCK, at(world, 12, ceilingY + 1, 22), "the first step");
        assertEquals(PLAIN_CEILING, at(world, 11, ceilingY, 21), "the lip stays on the plane");
    }

    @Test
    void stepHeightRaisesEachCourseByMoreThanOneRow() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        // Two steps of two rows: the same 4 rows of gain as a four-step vault, in half the courses
        // and with a visibly coarser profile.
        Map<String, String> world = settle(render(6, vaulted(2, 2, 1)));

        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY + 4, 24), "the crown is not 2x2 rows up");
        assertEquals(VAULT_BLOCK, at(world, 12, ceilingY + 2, 22), "the first step is not 2 rows up");
        assertEquals(AIR, at(world, 12, ceilingY + 1, 22), "the step did not excavate its full height");
    }

    @Test
    void stepInsetWidensEachCourseAndAStepWithNoFieldLeftSimplyStops() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        // step_inset 2 on a 7x7 interior: the first step starts two cells in, and the second would
        // start four -- past the middle, so it has no field left. It draws nothing rather than a
        // one-cell spike, which is what lets one authored vault serve rooms of different sizes.
        Map<String, String> world = settle(render(6, vaulted(2, 1, 2)));

        assertEquals(PLAIN_CEILING, at(world, 12, ceilingY, 22),
                "step_inset 2 should have left the first interior ring on the plane");
        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY + 1, 24), "the first step is missing");
        assertEquals(null, at(world, 14, ceilingY + 2, 24),
                "the second step had no ceiling left to raise and should have drawn nothing");
    }

    @Test
    void aSpringingCourseIsDrawnInThePlaneAtTheLip() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        SurfacePatternEntry withSpringing = new SurfacePatternEntry(
                new VaultedCeilingPattern(VAULT_BLOCK, 2, 1, 1,
                        Optional.of("minecraft:stone_brick_stairs"), Optional.empty(),
                        SurfaceOrient.INWARD, Map.of()),
                0, 0, SizeGate.UNBOUNDED);
        Map<String, String> world = settle(render(4, withSpringing));

        // The lip is the one ring the steps do not cover, and it is what makes the first step read
        // as springing off the wall rather than as a shelf floating over it.
        assertEquals("minecraft:stone_brick_stairs", at(world, 11, ceilingY, 21));
        assertEquals(VAULT_BLOCK, at(world, 12, ceilingY + 1, 22));
    }

    @Test
    void theCrownMayBeItsOwnMaterial() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        SurfacePatternEntry withCrown = new SurfacePatternEntry(
                new VaultedCeilingPattern(VAULT_BLOCK, 2, 1, 1, Optional.empty(),
                        Optional.of("minecraft:chiseled_stone_bricks"), SurfaceOrient.INWARD,
                        Map.of()),
                0, 0, SizeGate.UNBOUNDED);
        Map<String, String> world = settle(render(4, withCrown));

        assertEquals("minecraft:chiseled_stone_bricks", at(world, 14, ceilingY + 2, 24),
                "the top step should be the crown material");
        assertEquals(VAULT_BLOCK, at(world, 12, ceilingY + 1, 22),
                "the lower steps stay the vault material");
    }

    @Test
    void aVaultedEntryDecodesFromItsAuthoredForm() {
        DataResult<CeilingPatternEntry> result = parse("""
                {
                  "patterns": [
                    { "type": "dungeons2:vaulted", "config": {
                        "block": "minecraft:stone_bricks",
                        "springing_block": "minecraft:stone_brick_stairs",
                        "crown_block": "minecraft:chiseled_stone_bricks",
                        "steps": 3
                    } }
                  ]
                }""");

        CeilingPatternEntry entry = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        VaultedCeilingPattern vault = (VaultedCeilingPattern) entry.patterns().get(0).pattern();
        assertEquals(3, vault.steps());
        assertEquals(1, vault.stepHeight());
    }

    @Test
    void aVaultThatCouldNeverDrawInFullIsALoadError() {
        // 4 steps of 2 rows is 8, past MAX_RISE. A load error rather than a clamp: the render-time
        // clamp exists to fit a vault to a ROOM, and one no room could ever hold is a mistake in the
        // file that clamping would hide.
        DataResult<CeilingPatternEntry> result = parse("""
                {
                  "patterns": [
                    { "type": "dungeons2:vaulted", "config": {
                        "block": "minecraft:stone_bricks", "steps": 4, "step_height": 2 } }
                  ]
                }""");

        assertTrue(result.result().isEmpty(), "a vault rising 8 must not decode");
        assertTrue(result.error().orElseThrow().message().contains("step"),
                "the error must name the field to change: " + result.error().orElseThrow().message());
    }

    @Test
    void anOrientWithNoSpringingCourseToTurnIsALoadError() {
        DataResult<CeilingPatternEntry> result = parse("""
                {
                  "patterns": [
                    { "type": "dungeons2:vaulted", "config": {
                        "block": "minecraft:stone_bricks", "orient": "outward" } }
                  ]
                }""");

        assertTrue(result.result().isEmpty(), "an orient with nothing to turn must not decode");
    }

    @Test
    void aVaultIsClampedToTheRoomLikeAnyOtherRise() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        // Three steps authored, one row of budget: the dome flattens instead of punching through.
        Map<String, String> world = settle(render(1, vaulted(3, 1, 1)));

        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY + 1, 24));
        assertEquals(null, at(world, 14, ceilingY + 2, 24), "the clamp did not hold");
    }

    // ---------- the piece's box ----------

    @Test
    void thePieceBoxCoversTheFloorsWholeBudgetAboveTheWalkingPlaneAndBelowIt() {
        // Exactly the reasoning DungeonRoomPiece#computeBox gives for the sink, applied upward: the
        // scheme is rolled at render time, so the box cannot be sized to the vault that this room
        // happens to draw -- it is sized to the budget every room on this floor shares.
        DungeonRoomPiece piece = new DungeonRoomPiece(room(), "classic", FLOOR_Y, 0, 0, 0, 5, 15);

        assertEquals(FLOOR_Y - 5, piece.getBoundingBox().minY(), "the pit half of the budget");
        assertEquals(FLOOR_Y + 15 - 1, piece.getBoundingBox().maxY(), "the vault half of the budget");
    }

    @Test
    void aRoomAsTallAsItsFloorStillFitsItsOwnBox() {
        // height 6 against a budget of 3 cannot happen with the shipped numbers, but max() is what
        // makes the box a superset rather than a replacement -- a clamp here would cut the ceiling
        // out of the piece that draws it.
        DungeonRoomPiece piece = new DungeonRoomPiece(room(), "classic", FLOOR_Y, 0, 0, 0, 0, 3);

        assertEquals(FLOOR_Y + room().getHeight() - 1, piece.getBoundingBox().maxY());
    }

    // ---------- the schema ----------

    private static DataResult<CeilingPatternEntry> parse(String json) {
        return CeilingPatternEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void aRisingVaultDecodesFromItsAuthoredForm() {
        DataResult<CeilingPatternEntry> result = parse("""
                {
                  "patterns": [
                    { "type": "dungeons2:field", "config": { "block": "minecraft:polished_andesite",
                      "inset": 1 }, "rise": 1 },
                    { "type": "dungeons2:field", "config": { "block": "minecraft:polished_andesite",
                      "inset": 2 }, "rise": 2 }
                  ]
                }""");

        CeilingPatternEntry entry = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        assertEquals(2, entry.patterns().size());
        assertEquals(1, entry.patterns().get(0).rise());
        // The signed axis everything downstream uses: a rise is a negative depth.
        assertEquals(-2, entry.patterns().get(1).depth());
    }

    @Test
    void aLayerMayNotBothHangAndRise() {
        DataResult<CeilingPatternEntry> result = parse("""
                {
                  "patterns": [
                    { "type": "dungeons2:field", "config": { "block": "minecraft:polished_andesite" },
                      "projection": 2, "rise": 3 }
                  ]
                }""");

        assertTrue(result.result().isEmpty(), "projection and rise together must not decode");
        assertTrue(result.error().orElseThrow().message().contains("rise"),
                "the error must name the field the author has to remove: "
                        + result.error().orElseThrow().message());
    }

    @Test
    void aRiseBeyondTheSchemaBoundIsALoadError() {
        DataResult<CeilingPatternEntry> result = parse("""
                {
                  "patterns": [
                    { "type": "dungeons2:field", "config": { "block": "minecraft:polished_andesite" },
                      "rise": 99 }
                  ]
                }""");

        assertTrue(result.result().isEmpty(), "a rise past MAX_RISE must not decode");
    }

    @Test
    void theFieldTypeFillsFromItsInsetInwardRatherThanRinging() {
        int ceilingY = FLOOR_Y + room().getHeight() - 1;
        Map<String, String> world = settle(render(3, rising(2, 2)));

        // 7x7 interior, inset 2: the field is the middle 3x3, interior u,v 2..4 -- world x,z 13..15
        // and 23..25. A ring would leave the centre cell on the plane, which is the bug this type
        // exists to avoid; it is also what the two corners below prove, since a ring covers those.
        assertEquals(VAULT_BLOCK, at(world, 14, ceilingY + 2, 24), "the middle of the field is missing");
        assertEquals(VAULT_BLOCK, at(world, 13, ceilingY + 2, 23), "the near corner of the field is missing");
        assertEquals(VAULT_BLOCK, at(world, 15, ceilingY + 2, 25), "the far corner of the field is missing");
        assertEquals(null, at(world, 12, ceilingY + 2, 22), "the field leaked outside its inset");
    }
}
