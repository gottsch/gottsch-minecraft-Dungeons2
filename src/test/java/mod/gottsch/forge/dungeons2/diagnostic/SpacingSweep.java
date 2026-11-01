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
package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Cell;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Sweeps {@code minRoomGap} and reports what each setting actually costs and buys.
 *
 * <p>Pure planning &mdash; no rendering, no Minecraft bootstrap, so it can afford hundreds of
 * dungeons per setting. Everything it reports is read off the maze grid and the room boxes.</p>
 *
 * <pre>
 *   ./gradlew spacingSweep
 *   ./gradlew spacingSweep -Pseeds=200 -Psize=LARGE -Pgaps=0,1,2,3,4
 * </pre>
 *
 * <h2>What the columns mean</h2>
 * <ul>
 *   <li><b>fail</b> &mdash; the planner returned nothing. The whole dungeon is lost, so this is the
 *       hard limit on how far the gap can be pushed.</li>
 *   <li><b>rooms</b> &mdash; mean rooms per floor. This is the price: space per room grows roughly
 *       with the square of the gap.</li>
 *   <li><b>shared</b> &mdash; share of rooms whose box overlaps at least one other room, i.e. rooms
 *       wearing a wall they do not own. Ordering cannot fix these; only spacing can.</li>
 *   <li><b>corridor width</b> &mdash; distribution of local corridor width. A corridor cell's width
 *       is the smaller of its contiguous horizontal and vertical runs, so a 3-wide passage counts 3
 *       and an open junction counts more. This is the thing the gap is meant to buy.</li>
 * </ul>
 */
public final class SpacingSweep {

    private SpacingSweep() {}

    public static void main(String[] args) {
        int seeds = intArg(args, "seeds", 120);
        DungeonSize size = DungeonSize.valueOf(strArg(args, "size", "MEDIUM").toUpperCase(Locale.ROOT));
        int corridorWidth = intArg(args, "corridor_width", 3);
        List<Integer> gaps = new ArrayList<>();
        for (String g : strArg(args, "gaps", "0,1,2,3,4").split(",")) {
            gaps.add(Integer.parseInt(g.trim()));
        }

        System.out.printf("%nminRoomGap sweep -- %d seeds per setting, %s, corridor_width=%d%n",
                seeds, size, corridorWidth);
        System.out.println("(a room's box overlapping another by one cell is a shared wall; "
                + "the maze runs on a 2-cell lattice so gaps are always even)");
        System.out.println();
        System.out.printf("%-5s %6s %8s %8s %8s %8s   %s%n",
                "gap", "fail", "rooms/fl", "shared", "roomArea", "meanGap", "corridor width 1/2/3/4+");
        System.out.println("-".repeat(96));

        for (int gap : gaps) {
            System.out.println(runOne(gap, seeds, size, corridorWidth).format(gap));
        }
        System.out.println();
    }

    private static Result runOne(int gap, int seeds, DungeonSize size, int corridorWidth) {
        Result r = new Result();
        for (int i = 0; i < seeds; i++) {
            long seed = 0xD2_0BADC0DEL + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                    .withSize(size)
                    .withCorridorWidth(corridorWidth)
                    .withMinRoomGap(gap)
                    .plan();
            if (planned.isEmpty()) {
                r.failures++;
                continue;
            }
            r.dungeons++;
            for (FloorLayout floor : planned.get().getFloors()) {
                measureFloor(floor, r);
            }
        }
        return r;
    }

    private static void measureFloor(FloorLayout floor, Result r) {
        List<RoomData> rooms = new ArrayList<>();
        for (RoomData room : floor.getRooms()) {
            if (room.getRole() == RoomRole.NORMAL) {
                rooms.add(room);
            }
        }
        r.floors++;
        r.rooms += rooms.size();

        Grid2D grid = floor.getGrid();
        if (grid != null) {
            r.gridCells += (long) grid.getWidth() * grid.getHeight();
            for (RoomData room : rooms) {
                r.roomBoxCells += (long) room.getWidth() * room.getDepth();
            }
            measureCorridorWidths(grid, r);
        }

        for (int i = 0; i < rooms.size(); i++) {
            int nearest = Integer.MAX_VALUE;
            for (int j = 0; j < rooms.size(); j++) {
                if (i == j) {
                    continue;
                }
                int gap = boxGap(rooms.get(i), rooms.get(j));
                nearest = Math.min(nearest, gap);
            }
            if (nearest == Integer.MAX_VALUE) {
                continue;
            }
            r.roomsMeasured++;
            r.nearestGapSum += nearest;
            r.nearestGapHist.merge(Math.min(nearest, 6), 1, Integer::sum);
            if (nearest == 0) {
                r.roomsSharingAWall++;
            }
        }
    }

    /**
     * Separation between two room boxes in cells. {@code 0} means the boxes overlap or touch, which
     * for this maze means they share a wall column; {@code n} means {@code n - 1} free cells lie
     * between them.
     */
    private static int boxGap(RoomData a, RoomData b) {
        int dx = Math.max(Math.max(a.getOriginX() - (b.getOriginX() + b.getWidth() - 1),
                b.getOriginX() - (a.getOriginX() + a.getWidth() - 1)), 0);
        int dz = Math.max(Math.max(a.getOriginZ() - (b.getOriginZ() + b.getDepth() - 1),
                b.getOriginZ() - (a.getOriginZ() + a.getDepth() - 1)), 0);
        return Math.max(dx, dz);
    }

    /** Local width of every corridor cell: the smaller of its horizontal and vertical runs. */
    private static void measureCorridorWidths(Grid2D grid, Result r) {
        for (int z = 0; z < grid.getHeight(); z++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!isCorridor(grid, x, z)) {
                    continue;
                }
                int spanX = 1;
                for (int i = x - 1; isCorridor(grid, i, z); i--) {
                    spanX++;
                }
                for (int i = x + 1; isCorridor(grid, i, z); i++) {
                    spanX++;
                }
                int spanZ = 1;
                for (int i = z - 1; isCorridor(grid, x, i); i--) {
                    spanZ++;
                }
                for (int i = z + 1; isCorridor(grid, x, i); i++) {
                    spanZ++;
                }
                r.corridorWidth.merge(Math.min(4, Math.min(spanX, spanZ)), 1L, Long::sum);
                r.corridorCells++;
            }
        }
    }

    private static boolean isCorridor(Grid2D grid, int x, int z) {
        if (x < 0 || z < 0 || x >= grid.getWidth() || z >= grid.getHeight()) {
            return false;
        }
        Cell cell = grid.get(x, z);
        return cell != null && cell.getType() == CellType.CORRIDOR;
    }

    private static final class Result {
        int failures;
        int dungeons;
        int floors;
        int rooms;
        int roomsMeasured;
        int roomsSharingAWall;
        long nearestGapSum;
        long gridCells;
        long roomBoxCells;
        long corridorCells;
        final TreeMap<Integer, Integer> nearestGapHist = new TreeMap<>();
        final TreeMap<Integer, Long> corridorWidth = new TreeMap<>();

        String format(int gap) {
            int attempts = dungeons + failures;
            double failPct = attempts == 0 ? 0 : 100.0 * failures / attempts;
            double roomsPerFloor = floors == 0 ? 0 : (double) rooms / floors;
            double sharedPct = roomsMeasured == 0 ? 0 : 100.0 * roomsSharingAWall / roomsMeasured;
            double areaPct = gridCells == 0 ? 0 : 100.0 * roomBoxCells / gridCells;
            double meanGap = roomsMeasured == 0 ? 0 : (double) nearestGapSum / roomsMeasured;

            StringBuilder widths = new StringBuilder();
            for (int w = 1; w <= 4; w++) {
                double pct = corridorCells == 0 ? 0
                        : 100.0 * corridorWidth.getOrDefault(w, 0L) / corridorCells;
                widths.append(String.format("%5.1f%%", pct));
                if (w < 4) {
                    widths.append(" /");
                }
            }
            return String.format("%-5d %5.1f%% %8.1f %7.1f%% %7.1f%% %8.2f   %s",
                    gap, failPct, roomsPerFloor, sharedPct, areaPct, meanGap, widths);
        }
    }

    private static String strArg(String[] args, String key, String def) {
        for (String a : args) {
            if (a.startsWith("--" + key + "=")) {
                return a.substring(key.length() + 3);
            }
        }
        return def;
    }

    private static int intArg(String[] args, String key, int def) {
        return Integer.parseInt(strArg(args, key, String.valueOf(def)));
    }
}
