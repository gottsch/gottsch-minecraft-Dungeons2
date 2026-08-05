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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.CorridorStyleWeight;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5.3: the planner rolls one corridor style per <em>floor</em> and stamps it onto every corridor on
 * that floor, and the roll survives to the piece and back through NBT.
 *
 * <p>Two properties carry the feature. <strong>Uniformity within a floor</strong> is what makes a
 * style a style rather than per-corridor noise &mdash; an 8-high arched run and a 5-high flat one on
 * either side of the same door is the failure this rules out. And the <strong>bounding box</strong>
 * has to keep agreeing with the generator now that the height differs floor to floor: a box that
 * disagrees does not throw, it silently decapitates every corridor on that floor, which is precisely
 * the class of defect §5 of the Aug 03 handoff says no headless check would otherwise see.</p>
 *
 * @author Mark Gottschling on Aug 04, 2026
 */
class CorridorStyleTest {

    private static final int ANCHOR_X = 128;
    private static final int ANCHOR_Z = 256;
    private static final int SURFACE_Y = 64;
    private static final String MOTIF = "classic";

    /** Deliberately spread across the legal range so a floor's height is visible in its corridors. */
    private static final List<CorridorStyleWeight> STYLES = List.of(
            new CorridorStyleWeight("vaulted", 3, 7),
            new CorridorStyleWeight("grand", 1, 8),
            new CorridorStyleWeight("cramped", 2, 5));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DungeonLayout plan(long seed, int floors, List<CorridorStyleWeight> styles) {
        ICoords anchor = new Coords(ANCHOR_X, 0, ANCHOR_Z);
        DungeonStackPlanner planner = new DungeonStackPlanner(seed, anchor, SURFACE_Y, MOTIF, new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withFloorCount(floors);
        if (styles != null) {
            planner.withCorridorStyles(styles);
        }
        return planner.plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
    }

    /**
     * A planner nobody gave styles to behaves exactly as it did before &mdash; including the style
     * name, which stays the baseline so nothing extra reaches the NBT of an existing motif.
     */
    @Test
    void aPlannerWithNoStylesKeepsTheInjectedHeightAndTheBaselineName() {
        DungeonLayout layout = new DungeonStackPlanner(0xD2_5A_2026L, new Coords(ANCHOR_X, 0, ANCHOR_Z),
                SURFACE_Y, MOTIF, new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withFloorCount(3)
                .withCorridorHeight(7)
                .plan().orElseThrow();

        List<CorridorData> corridors = corridors(layout);
        assertFalse(corridors.isEmpty(), "fixed seed produced no corridors to check");
        for (CorridorData corridor : corridors) {
            assertEquals(7, corridor.getWallHeight());
            assertEquals(CorridorData.BASELINE_STYLE, corridor.getStyleName());
        }
    }

    /** The whole point: one style per floor, not per corridor. */
    @Test
    void everyCorridorOnAFloorSharesOneStyle() {
        for (long seed = 0; seed < 24; seed++) {
            DungeonLayout layout = plan(seed, 3, STYLES);
            for (FloorLayout floor : layout.getFloors()) {
                if (floor.getCorridors().isEmpty()) {
                    continue;
                }
                CorridorData first = floor.getCorridors().get(0);
                for (CorridorData corridor : floor.getCorridors()) {
                    assertEquals(first.getStyleName(), corridor.getStyleName(),
                            "seed " + seed + " floor " + floor.getFloorIndex() + ": corridors disagree on style");
                    assertEquals(first.getWallHeight(), corridor.getWallHeight(),
                            "seed " + seed + " floor " + floor.getFloorIndex() + ": corridors disagree on height");
                }
            }
        }
    }

    /** The stamped height must be the rolled style's height, not some other entry's. */
    @Test
    void theStampedHeightMatchesTheStampedStyle() {
        for (long seed = 0; seed < 24; seed++) {
            for (CorridorData corridor : corridors(plan(seed, 3, STYLES))) {
                CorridorStyleWeight style = STYLES.stream()
                        .filter(s -> s.name().equals(corridor.getStyleName()))
                        .findFirst().orElseThrow(() ->
                                new AssertionError("corridor stamped with unknown style " + corridor.getStyleName()));
                assertEquals(style.height(), corridor.getWallHeight(),
                        "seed " + seed + ": style " + style.name() + " stamped the wrong height");
            }
        }
    }

    /**
     * Floors roll independently, or "per-floor variation" is just a dungeon-wide constant with extra
     * steps. Asserted across seeds rather than within one, because any single dungeon is entitled to
     * roll the same style three times.
     */
    @Test
    void differentFloorsCanRollDifferentStyles() {
        boolean sawAFloorDisagreeWithAnother = false;
        for (long seed = 0; seed < 24 && !sawAFloorDisagreeWithAnother; seed++) {
            Set<String> perFloor = new HashSet<>();
            for (FloorLayout floor : plan(seed, 3, STYLES).getFloors()) {
                if (!floor.getCorridors().isEmpty()) {
                    perFloor.add(floor.getCorridors().get(0).getStyleName());
                }
            }
            sawAFloorDisagreeWithAnother = perFloor.size() > 1;
        }
        assertTrue(sawAFloorDisagreeWithAnother,
                "24 three-floor dungeons rolled one style throughout every time -- the roll is not per floor");
    }

    /** Every authored style must be reachable, or its weight is a lie. */
    @Test
    void everyAuthoredStyleGetsRolledEventually() {
        Set<String> seen = new HashSet<>();
        for (long seed = 0; seed < 64; seed++) {
            for (CorridorData corridor : corridors(plan(seed, 3, STYLES))) {
                seen.add(corridor.getStyleName());
            }
        }
        assertEquals(STYLES.stream().map(CorridorStyleWeight::name).collect(java.util.stream.Collectors.toSet()),
                seen, "some authored style is never rolled");
    }

    /**
     * Same seed, same styles, same result. Worth asserting outright: the planner has already shipped
     * one nondeterminism bug of exactly this shape (a direction picked by iterating a HashMap whose
     * keys had identity hashes), and no in-JVM test could catch that one either &mdash; but a roll
     * that drifts <em>within</em> a JVM is squarely in reach.
     */
    @Test
    void theRollIsDeterministicForAGivenSeed() {
        for (long seed = 0; seed < 8; seed++) {
            assertEquals(styleNamesPerFloor(plan(seed, 3, STYLES)), styleNamesPerFloor(plan(seed, 3, STYLES)),
                    "seed " + seed + " rolled different styles on two runs");
        }
    }

    /**
     * The silent-failure guard from {@link CorridorHeightTest}, re-run with the height varying floor
     * to floor. A block outside its piece's box is dropped by vanilla with no error at all.
     */
    @Test
    void everyEmittedBlockFitsInsideItsPiecesBoxAtEveryRolledHeight() {
        for (long seed = 0; seed < 8; seed++) {
            DungeonLayout layout = plan(seed, 3, STYLES);
            for (FloorLayout floor : layout.getFloors()) {
                int floorY = floor.getFloorY();
                for (CorridorData corridor : floor.getCorridors()) {
                    DungeonCorridorPiece piece =
                            new DungeonCorridorPiece(corridor, MOTIF, floorY, ANCHOR_X, ANCHOR_Z);
                    BoundingBox box = piece.getBoundingBox();
                    assertEquals(floorY + corridor.getWallHeight() - 1, box.maxY(),
                            "box ceiling disagrees with the rolled style on floor " + floor.getFloorIndex());

                    for (BlockPlacement bp : piece.renderPlacements()) {
                        assertTrue(box.isInside(new BlockPos(ANCHOR_X + bp.getX(), bp.getY(), ANCHOR_Z + bp.getZ())),
                                "seed " + seed + " floor " + floor.getFloorIndex() + " style "
                                        + corridor.getStyleName() + ": block at Y=" + bp.getY()
                                        + " falls outside " + box + " and would be clipped");
                    }
                }
            }
        }
    }

    @Test
    void theStyleNameSurvivesTheNbtRoundTrip() {
        CorridorData corridor = new CorridorData(1);
        corridor.setStyleName("grand");
        corridor.setWallHeight(8);

        CorridorData back = PieceNbt.readCorridor(PieceNbt.writeCorridor(corridor));
        assertEquals("grand", back.getStyleName());
        assertEquals(8, back.getWallHeight());
    }

    /**
     * A corridor from a motif with no styles writes no tag at all, and a save from before styles
     * existed has none either. Both have to read back as the baseline rather than as a style named
     * {@code null}.
     */
    @Test
    void aCorridorWithNoStyleWritesNoTagAndReadsBackAsTheBaseline() {
        CompoundTag tag = PieceNbt.writeCorridor(new CorridorData(1));

        assertFalse(tag.contains("CorridorStyle"), "a baseline corridor must not write a style tag");
        assertEquals(CorridorData.BASELINE_STYLE, PieceNbt.readCorridor(tag).getStyleName());
    }

    private static List<CorridorData> corridors(DungeonLayout layout) {
        return layout.getFloors().stream().map(FloorLayout::getCorridors).flatMap(List::stream).toList();
    }

    private static List<String> styleNamesPerFloor(DungeonLayout layout) {
        return layout.getFloors().stream()
                .map(f -> f.getCorridors().isEmpty() ? "(none)" : f.getCorridors().get(0).getStyleName())
                .toList();
    }
}
