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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.config.CorridorConfig;
import mod.gottsch.forge.dungeons2.core.config.CorridorStyle;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAlternate;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5.4: horizontal courses on a corridor's wall columns.
 *
 * <p>The corridor's version of the room's {@code CoursesWallPatternProvider}, and it reuses that
 * provider's {@code CourseEntry} verbatim &mdash; so what is worth testing here is the handful of
 * things that are genuinely different: rows anchored against the <em>corridor</em> height (which now
 * varies per floor), {@code strict} alternation with no run coordinate to count along, and the
 * doorway rows that must stay air whatever a course says.</p>
 *
 * @author Mark Gottschling on Aug 04, 2026
 */
class CorridorCoursesTest {

    private static final int FLOOR_Y = 60;
    private static final int HEIGHT = 7;
    // Resolved lazily, not as static finals: the block registry is not populated until the
    // @BeforeAll bootstrap runs, and a static field initializer beats it to it.
    private static BlockState wall() {
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    private static BlockState plinth() {
        return Blocks.POLISHED_ANDESITE.defaultBlockState();
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A 7x7 grid with a 3-cell straight corridor through the middle. */
    private static Grid2D grid() {
        Grid2D grid = new Grid2D(7, 7);
        for (int x = 2; x <= 4; x++) {
            grid.get(x, 3).setType(CellType.CORRIDOR);
            grid.get(x, 3).setRegionId(10);
        }
        return grid;
    }

    private static CorridorData corridor() {
        CorridorData corridor = new CorridorData(10);
        for (int x = 2; x <= 4; x++) {
            corridor.getCells().add(new Coords2D(x, 3));
        }
        corridor.setWallHeight(HEIGHT);
        return corridor;
    }

    /** A flat motif at {@link #HEIGHT}, carrying exactly the courses given. */
    private static MotifConfig motif(CourseEntry... courses) {
        CorridorConfig corridor = new CorridorConfig(
                "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks",
                HEIGHT, CorridorConfig.Profile.FLAT, Optional.empty(), Optional.empty(),
                List.of(), List.of(courses));
        return new MotifConfig(MotifConfig.DEFAULT.wall(), MotifConfig.DEFAULT.ceiling(),
                MotifConfig.DEFAULT.door(), corridor, MotifConfig.DEFAULT.floor(),
                MotifConfig.DEFAULT.schemes());
    }

    private static List<BlockPlacement> build(MotifConfig motifConfig) {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(motifConfig)
                .build(corridor(), grid(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out);
        return out;
    }

    /** The wall cell directly north of the middle corridor cell, at one row of the column. */
    private static BlockState wallAt(List<BlockPlacement> out, int x, int z, int yOffset) {
        for (BlockPlacement placement : out) {
            if (placement.getX() == x && placement.getZ() == z
                    && placement.getY() == FLOOR_Y + yOffset) {
                return BlockStateCodec.resolve(placement);
            }
        }
        throw new AssertionError("nothing emitted at (" + x + "," + (FLOOR_Y + yOffset) + "," + z + ")");
    }

    @Test
    void withNoCoursesEveryWallRowIsThePlainWallBlock() {
        List<BlockPlacement> out = build(motif());
        for (int yOffset = 0; yOffset < HEIGHT; yOffset++) {
            assertEquals(wall(), wallAt(out, 3, 2, yOffset), "row " + yOffset);
        }
    }

    @Test
    void aBottomAnchoredCourseLandsOnTheFloorRow() {
        List<BlockPlacement> out = build(motif(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 0)));

        assertEquals(plinth(), wallAt(out, 3, 2, 0));
        assertEquals(wall(), wallAt(out, 3, 2, 1), "the course must claim exactly one row");
    }

    /**
     * The reason {@code top} exists at all, and it matters more here than in a room: corridor height
     * is rolled per floor now, so a crown measured from the floor would sit at a different distance
     * from the ceiling on every floor of the same dungeon.
     */
    @Test
    void aTopAnchoredCourseIsMeasuredFromTheCeiling() {
        List<BlockPlacement> out = build(motif(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.TOP, 2)));

        assertEquals(plinth(), wallAt(out, 3, 2, HEIGHT - 3));
        assertEquals(wall(), wallAt(out, 3, 2, HEIGHT - 2));
    }

    /** Anchoring off the end of the column drops the course; it must not clamp to the last row. */
    @Test
    void aCourseAnchoredOutsideTheColumnIsDroppedNotClamped() {
        List<BlockPlacement> out = build(motif(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.TOP, HEIGHT + 3)));

        for (int yOffset = 0; yOffset < HEIGHT; yOffset++) {
            assertEquals(wall(), wallAt(out, 3, 2, yOffset),
                    "row " + yOffset + " took a course that resolved outside the wall");
        }
    }

    /** Ordering is execution order everywhere else in this config; two courses on one row agree. */
    @Test
    void whenTwoCoursesResolveToOneRowTheLaterWins() {
        List<BlockPlacement> out = build(motif(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 0),
                new CourseEntry("minecraft:andesite", CourseAnchor.BOTTOM, 0)));

        assertEquals(Blocks.ANDESITE.defaultBlockState(), wallAt(out, 3, 2, 0));
    }

    /**
     * A corridor winds, so there is no run coordinate to alternate along; parity is on
     * {@code (x + z)}, which alternates on both axes and survives a 90° turn.
     */
    @Test
    void strictAlternationFollowsXPlusZParity() {
        List<BlockPlacement> out = build(motif(new CourseEntry(
                "minecraft:polished_andesite", Optional.of("minecraft:andesite"), Optional.empty(),
                CourseAnchor.BOTTOM, 0, 0, WallPatternEntry.CourseOrient.NONE,
                java.util.Map.of(), CourseAlternate.STRICT,
                mod.gottsch.forge.dungeons2.core.config.SizeGate.UNBOUNDED)));

        for (int x = 2; x <= 4; x++) {
            BlockState expected = Math.floorMod(x + 2, 2) == 0 ? plinth() : Blocks.ANDESITE.defaultBlockState();
            assertEquals(expected, wallAt(out, x, 2, 0), "parity broke at x=" + x);
        }
    }

    /**
     * The one row a course may never claim. A band across the two door-half rows would brick up the
     * doorway, and unlike a room's projecting trim there is no cell to step aside into.
     */
    @Test
    void aCourseNeverFillsTheDoorHalfRows() {
        Grid2D grid = grid();
        grid.get(3, 2).setType(CellType.DOOR);

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(motif(
                        new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 1),
                        new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 2),
                        new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 3)))
                .build(corridor(), grid, FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        BlockState air = Blocks.AIR.defaultBlockState();
        assertEquals(air, wallAt(out, 3, 2, 1), "door half row 1 was filled by a course");
        assertEquals(air, wallAt(out, 3, 2, 2), "door half row 2 was filled by a course");
        // The lintel row above them is fair game, and is what keeps a band running across a doorway
        // rather than stopping dead at every opening.
        assertEquals(plinth(), wallAt(out, 3, 2, 3));
    }

    /** A style's courses are what a corridor stamped with that style gets. */
    @Test
    void aCorridorBuildsTheCoursesOfItsOwnStyle() {
        CorridorConfig config = new CorridorConfig(
                "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks",
                HEIGHT, CorridorConfig.Profile.FLAT, Optional.empty(), Optional.empty(),
                List.of(new CorridorStyle("trimmed", 1, HEIGHT, CorridorConfig.Profile.FLAT,
                        Optional.empty(), Optional.empty(),
                        List.of(new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 0)))),
                List.of());
        MotifConfig motifConfig = new MotifConfig(MotifConfig.DEFAULT.wall(), MotifConfig.DEFAULT.ceiling(),
                MotifConfig.DEFAULT.door(), config, MotifConfig.DEFAULT.floor(),
                MotifConfig.DEFAULT.schemes());

        CorridorData corridor = corridor();
        corridor.setStyleName("trimmed");
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(motifConfig)
                .build(corridor, grid(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertEquals(plinth(), wallAt(out, 3, 2, 0));
    }

    /** Courses are a wall treatment; the passage's own cells are not theirs to touch. */
    @Test
    void coursesDoNotReachIntoTheCorridorCellsThemselves() {
        List<BlockPlacement> out = build(motif(
                new CourseEntry("minecraft:polished_andesite", CourseAnchor.BOTTOM, 1)));

        for (int x = 2; x <= 4; x++) {
            assertFalse(plinth().equals(wallAt(out, x, 3, 1)),
                    "a course reached into the corridor cell at x=" + x + ", which is the passage");
        }
        assertTrue(true);
    }
}
