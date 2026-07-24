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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.maze;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.ILevel2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.IRoom2D;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1.5 deliverable: verify {@link MazeLevelGenerator2D#dilateCorridors}
 * widens corridors as expected while preserving room walls.
 *
 * <p>Tests work directly on the maze planner's grid output (no Minecraft
 * bootstrap needed) since corridor dilation is loader-agnostic.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
class MazeLevelGenerator2DDilationTest {

    private static final int W = 45;
    private static final int H = 45;
    private static final long SEED = 0xD2CAFE_BABE_C0DEL;

    private MazeLevelGenerator2D buildWithWidth(int corridorCells) {
        return new MazeLevelGenerator2D.Builder()
                .with($ -> {
                    $.width = W;
                    $.height = H;
                    $.numberOfRooms = 8;
                })
                .corridorWidth(corridorCells)
                .seed(SEED)
                .build();
    }

    private long countCellsOfType(ILevel2D level, CellType type) {
        Grid2D grid = level.getGrid();
        long count = 0;
        for (int x = 0; x < grid.getWidth(); x++) {
            for (int z = 0; z < grid.getHeight(); z++) {
                if (grid.get(x, z).getType() == type) count++;
            }
        }
        return count;
    }

    @Test
    void widerCorridorsProduceMoreCorridorCells() {
        Optional<ILevel2D> oneWide = buildWithWidth(1).generate();
        Optional<ILevel2D> twoWide = buildWithWidth(2).generate();
        Optional<ILevel2D> threeWide = buildWithWidth(3).generate();

        assertTrue(oneWide.isPresent(), "1-wide maze should generate");
        assertTrue(twoWide.isPresent(), "2-wide maze should generate");
        assertTrue(threeWide.isPresent(), "3-wide maze should generate");

        long oneCount = countCellsOfType(oneWide.get(), CellType.CORRIDOR);
        long twoCount = countCellsOfType(twoWide.get(), CellType.CORRIDOR);
        long threeCount = countCellsOfType(threeWide.get(), CellType.CORRIDOR);

        assertTrue(twoCount > oneCount,
                "2-wide should produce more CORRIDOR cells than 1-wide; got "
                        + oneCount + " vs " + twoCount);
        assertTrue(threeCount > twoCount,
                "3-wide should produce more CORRIDOR cells than 2-wide; got "
                        + twoCount + " vs " + threeCount);
    }

    @Test
    void oneWideEqualsNoDilation() {
        // corridorWidth(1) means 0 dilation passes; output should match an
        // explicitly un-dilated build.
        Optional<ILevel2D> withWidth1 = buildWithWidth(1).generate();
        Optional<ILevel2D> withDefault = new MazeLevelGenerator2D.Builder()
                .with($ -> { $.width = W; $.height = H; $.numberOfRooms = 8; })
                .seed(SEED)
                .build()
                .generate();
        assertTrue(withWidth1.isPresent() && withDefault.isPresent());

        // Same RNG path (no dilation, no extra RNG calls); same CORRIDOR count.
        assertEquals(countCellsOfType(withDefault.get(), CellType.CORRIDOR),
                countCellsOfType(withWidth1.get(), CellType.CORRIDOR),
                "corridorWidth(1) should match the un-configured default");
    }

    @Test
    void roomInteriorsAreNeverErodedByDilation() {
        Optional<ILevel2D> result = buildWithWidth(3).generate();
        assertTrue(result.isPresent());
        ILevel2D level = result.get();
        Grid2D grid = level.getGrid();

        // For each room, every interior cell (the non-edge ones) should still
        // be CellType.ROOM. Dilation must never turn a ROOM cell into CORRIDOR.
        for (IRoom2D room : level.getRooms()) {
            int originX = room.getOrigin().getX();
            int originZ = room.getOrigin().getY();
            int w = room.getWidth();
            int d = room.getHeight();
            for (int x = 1; x < w - 1; x++) {
                for (int z = 1; z < d - 1; z++) {
                    CellType t = grid.get(originX + x, originZ + z).getType();
                    assertEquals(CellType.ROOM, t,
                            "Room " + room.getId() + " interior cell at ("
                                    + (originX + x) + "," + (originZ + z) + ") was eroded to " + t);
                }
            }
        }
    }

    @Test
    void dilationNeverErodesAnyRoomCell() {
        // The invariant dilation must guarantee: no cell inside a room's
        // footprint (interior, border, or corner) is ever turned into
        // CORRIDOR. (A border cell may legitimately be ROOM/WALL/DOOR; with
        // the START room skipping intersection checks, rooms can overlap and
        // a "border" cell may be another room's interior. That's a pre-existing
        // maze quirk, not a dilation bug.)
        Optional<ILevel2D> result = buildWithWidth(3).generate();
        assertTrue(result.isPresent());
        ILevel2D level = result.get();
        Grid2D grid = level.getGrid();

        for (IRoom2D room : level.getRooms()) {
            int originX = room.getOrigin().getX();
            int originZ = room.getOrigin().getY();
            int w = room.getWidth();
            int d = room.getHeight();
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    CellType t = grid.get(originX + x, originZ + z).getType();
                    assertNotEquals(CellType.CORRIDOR, t,
                            "Room " + room.getId() + " footprint cell at ("
                                    + (originX + x) + "," + (originZ + z)
                                    + ") was eroded to CORRIDOR by dilation");
                }
            }
        }
    }

    @Test
    void dilationIsDeterministic() {
        // Two independent runs with the same seed and width produce
        // bit-identical CORRIDOR cell sets.
        ILevel2D a = buildWithWidth(2).generate().orElseThrow();
        ILevel2D b = buildWithWidth(2).generate().orElseThrow();

        Grid2D ga = a.getGrid();
        Grid2D gb = b.getGrid();
        assertEquals(ga.getWidth(), gb.getWidth());
        assertEquals(ga.getHeight(), gb.getHeight());
        for (int x = 0; x < ga.getWidth(); x++) {
            for (int z = 0; z < ga.getHeight(); z++) {
                assertEquals(ga.get(x, z).getType(), gb.get(x, z).getType(),
                        "Type mismatch at (" + x + "," + z + ")");
            }
        }
    }

    @Test
    void everyCorridorCellHasAtLeastOneWallNeighbor() {
        // Sanity: after dilation + rebuildCorridorWalls, every CORRIDOR cell
        // should still have at least one WALL (or ROOM-wall or DOOR) neighbor
        // unless it's completely surrounded by other CORRIDOR cells (interior
        // of a fat corridor, which is fine). This catches the bug where
        // rebuildCorridorWalls fails to re-wall the new outer boundary.
        Optional<ILevel2D> result = buildWithWidth(3).generate();
        assertTrue(result.isPresent());
        Grid2D grid = result.get().getGrid();

        int orphanCount = 0;
        int[][] cards = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int x = 1; x < grid.getWidth() - 1; x++) {
            for (int z = 1; z < grid.getHeight() - 1; z++) {
                if (grid.get(x, z).getType() != CellType.CORRIDOR) continue;
                boolean hasBoundary = false;
                boolean hasNonCorridor = false;
                for (int[] off : cards) {
                    CellType t = grid.get(x + off[0], z + off[1]).getType();
                    if (t == CellType.WALL || t == CellType.DOOR || t == CellType.ROOM) {
                        hasBoundary = true;
                    }
                    if (t != CellType.CORRIDOR) {
                        hasNonCorridor = true;
                    }
                }
                // OK case 1: corridor surrounded by walls (a regular wall-bound corridor cell)
                // OK case 2: corridor entirely surrounded by other corridors (interior of fat corridor)
                if (hasNonCorridor && !hasBoundary) {
                    orphanCount++;
                }
            }
        }
        assertEquals(0, orphanCount,
                "No CORRIDOR cell should border non-corridor non-boundary cells "
                        + "(rebuildCorridorWalls should have promoted them to WALL)");
    }
}
