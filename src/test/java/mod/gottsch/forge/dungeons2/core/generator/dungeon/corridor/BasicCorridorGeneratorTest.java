/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.config.CorridorConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicCorridorGeneratorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A 7x7 grid with a 3-cell straight corridor in the middle (the rest is rock/wall). */
    private Grid2D simpleGrid() {
        Grid2D grid = new Grid2D(7, 7);
        // Grid is initialized with WALL borders and ROCK interior. Carve a corridor.
        for (int x = 2; x <= 4; x++) {
            grid.get(x, 3).setType(CellType.CORRIDOR);
            grid.get(x, 3).setRegionId(10);
        }
        return grid;
    }

    private CorridorData simpleCorridor() {
        CorridorData c = new CorridorData(10);
        c.getCells().add(new Coords2D(2, 3));
        c.getCells().add(new Coords2D(3, 3));
        c.getCells().add(new Coords2D(4, 3));
        return c;
    }

    @Test
    void corridorEmitsFloorAndAirColumnPerCell() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60,
                DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // Per corridor cell: 1 floor + 3 air = 4 placements.
        // Per cell also: up to 8 wall neighbors. For 3-cell line, neighbor walls
        // are deduped. We just verify the corridor cells got their 4 each.
        long corridorCellPlacements = out.stream()
                .filter(bp -> bp.getY() >= 60 && bp.getY() <= 63
                        && bp.getX() >= 2 && bp.getX() <= 4 && bp.getZ() == 3)
                .count();
        assertEquals(3 * 4, corridorCellPlacements,
                "Each corridor cell should have 1 floor + 3 air = 4 placements");
    }

    @Test
    void corridorWallsAreEmittedAroundCorridor() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60,
                DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // For corridor at (2..4, 3), neighbor cells include (1..5, 2), (1..5, 3), (1..5, 4)
        // minus the corridor itself. All neighbors are WALL/ROCK in this grid.
        // Each wall cell emits 5 vertical blocks (y=60..64).
        boolean sawWallColumn = false;
        for (BlockPlacement bp : out) {
            if (bp.getX() == 1 && bp.getZ() == 3 && bp.getY() == 62) {
                sawWallColumn = true;
                break;
            }
        }
        assertTrue(sawWallColumn,
                "Expected a wall column to the west of corridor at (1,*,3)");
    }

    /**
     * The corridor half of the lichen-on-doors fix: a bordering DOOR cell must
     * not carry a full cube at the two door-half levels, or the corridor's
     * decoration pass anchors lichen there facing the door cell and the lichen
     * ends up rendered onto the door {@code DungeonDoorPiece} carves in later.
     */
    @Test
    void aBorderingDoorCellIsPiercedAtTheTwoDoorHalfLevels() {
        Grid2D grid = simpleGrid();
        grid.get(1, 3).setType(CellType.DOOR); // west end of the corridor run

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertColumnIsPierced(out, 1, 3, 60);
    }

    /** A CONNECTOR is still a plain wall — no door piece follows it, so no hole. */
    @Test
    void aBorderingConnectorCellStaysSolid() {
        Grid2D grid = simpleGrid();
        grid.get(1, 3).setType(CellType.CONNECTOR);

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        for (BlockPlacement bp : out) {
            if (bp.getX() == 1 && bp.getZ() == 3) {
                assertEquals(false, "minecraft:air".equals(bp.getBlockId()),
                        "connector column must stay solid: " + bp);
            }
        }
    }

    /**
     * The grid-free overload (what the deserialized piece uses) must agree with
     * the grid-based one, reading door cells off {@code CorridorData}.
     */
    @Test
    void theGridFreeOverloadPiercesDoorCellsToo() {
        CorridorData corridor = simpleCorridor();
        corridor.getDoorCells().add(new Coords2D(1, 3));

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(corridor, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertColumnIsPierced(out, 1, 3, 60);
    }

    /** Air at floorY+1 / floorY+2, solid everywhere else, exactly {@code height} blocks. */
    private static void assertColumnIsPierced(List<BlockPlacement> out, int x, int z, int floorY) {
        assertColumnIsPierced(out, x, z, floorY, CorridorData.DEFAULT_WALL_HEIGHT);
    }

    private static void assertColumnIsPierced(List<BlockPlacement> out, int x, int z, int floorY, int height) {
        int seen = 0;
        for (BlockPlacement bp : out) {
            if (bp.getX() != x || bp.getZ() != z) continue;
            seen++;
            int offset = bp.getY() - floorY;
            if (offset == 1 || offset == 2) {
                assertEquals("minecraft:air", bp.getBlockId(),
                        "door-half level " + offset + " must be air: " + bp);
            } else {
                assertTrue(!"minecraft:air".equals(bp.getBlockId()),
                        "level " + offset + " must stay solid: " + bp);
            }
        }
        assertEquals(height, seen, "a doorway column is a full " + height + "-block column");
    }

    /**
     * Regression test for a bug this outlived two refactors. Pre-merge, {@code palette()}
     * resolved the corridor floor from the WALL_PATTERN BlockSet using a CorridorFloorPattern key
     * -- and since BlockSet was keyed by enum instance rather than name, that BlockSet (populated
     * only with WallPattern constants) never had the key, so the corridor floor always silently
     * fell through to a hardcoded stone_bricks regardless of what the datapack authored. The merged
     * MotifConfig makes that class of bug unexpressible: the corridor's blocks are named record
     * fields. Asserts the authored floor pair actually reaches the world.
     */
    @Test
    void corridorFloorComesFromTheMotifsCorridorSection() {
        MotifConfig motifConfig = new MotifConfig(
                MotifConfig.DEFAULT.wall(), MotifConfig.DEFAULT.ceiling(), MotifConfig.DEFAULT.door(),
                new CorridorConfig("minecraft:granite", "minecraft:diorite", "minecraft:andesite",
                        CorridorConfig.DEFAULT_HEIGHT),
                MotifConfig.DEFAULT.floor(), MotifConfig.DEFAULT.schemes());

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator().withMotifConfig(motifConfig)
                .build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        boolean sawAuthoredFloor = out.stream().anyMatch(bp -> bp.getY() == 60
                && ("minecraft:granite".equals(bp.getBlockId()) || "minecraft:diorite".equals(bp.getBlockId())));
        assertTrue(sawAuthoredFloor, "corridor floor should come from CorridorConfig, not a hardcoded default");

        boolean sawAuthoredCeiling = out.stream()
                .anyMatch(bp -> bp.getY() == 64 && "minecraft:andesite".equals(bp.getBlockId()));
        assertTrue(sawAuthoredCeiling, "corridor ceiling should come from CorridorConfig too");
    }

    // -------- variable corridor height --------

    /**
     * A corridor's air gap grows with its height and the ceiling moves with it: at {@code h} the
     * cell is floor at {@code floorY}, {@code h-2} air rows, ceiling at {@code floorY+h-1}.
     */
    @Test
    void aTallerCorridorGetsMoreAirAndAHigherCeiling() {
        CorridorData corridor = simpleCorridor();
        corridor.setWallHeight(7);

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(corridor, simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        long airInMiddleCell = out.stream()
                .filter(bp -> bp.getX() == 3 && bp.getZ() == 3 && "minecraft:air".equals(bp.getBlockId()))
                .count();
        assertEquals(5, airInMiddleCell, "a 7-high corridor has 5 air rows");

        boolean ceilingAtTop = out.stream()
                .anyMatch(bp -> bp.getX() == 3 && bp.getZ() == 3 && bp.getY() == 66
                        && !"minecraft:air".equals(bp.getBlockId()));
        assertTrue(ceilingAtTop, "the ceiling should sit at floorY+height-1 = 66");

        long westWallColumn = out.stream().filter(bp -> bp.getX() == 1 && bp.getZ() == 3).count();
        assertEquals(7, westWallColumn, "wall columns should be as tall as the corridor");
    }

    /**
     * The one thing height must not touch. {@code BasicDoorGenerator} owns
     * {@code floorY..floorY+3} and both wall generators pierce a door cell at {@code +1}/{@code +2};
     * a taller corridor may only add rows <em>above</em> that. If this ever drifts, doors are
     * either walled shut or hung in a hole.
     */
    @Test
    void aTallerCorridorStillPiercesExactlyTheTwoDoorHalfRows() {
        Grid2D grid = simpleGrid();
        grid.get(1, 3).setType(CellType.DOOR);

        CorridorData corridor = simpleCorridor();
        corridor.setWallHeight(8);

        List<BlockPlacement> out = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(corridor, grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertColumnIsPierced(out, 1, 3, 60, 8);
    }

    /** The grid-free overload reads the same height off the data, so both paths still agree. */
    @Test
    void bothOverloadsAgreeAtANonDefaultHeight() {
        CorridorData gridBased = simpleCorridor();
        gridBased.setWallHeight(6);
        List<BlockPlacement> a = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(gridBased, simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), a);

        CorridorData gridFree = simpleCorridor();
        gridFree.setWallHeight(6);
        for (BlockPlacement bp : a) {
            if (bp.getZ() != 3 || bp.getX() < 2 || bp.getX() > 4) {
                Coords2D cell = new Coords2D(bp.getX(), bp.getZ());
                if (!gridFree.getWallCells().contains(cell)) {
                    gridFree.getWallCells().add(cell);
                }
            }
        }
        List<BlockPlacement> b = new ArrayList<>();
        new BasicCorridorGenerator()
                .build(gridFree, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), b);

        assertEquals(a.size(), b.size(), "the two overloads should emit the same number of blocks");
    }

    @Test
    void corridorBuilderIsDeterministic() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> a = new ArrayList<>();
        List<BlockPlacement> b = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), a);
        gen.build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), b);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).toString(), b.get(i).toString());
        }
    }
}
