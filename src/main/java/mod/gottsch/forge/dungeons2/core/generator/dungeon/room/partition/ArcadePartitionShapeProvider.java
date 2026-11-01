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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition;

import mod.gottsch.forge.dungeons2.core.config.partition.ArcadePartitionShape;
import mod.gottsch.forge.dungeons2.core.config.partition.StripPartitionShape.Axis;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.ElevatedPartitionPlan.Column;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.ElevatedPartitionPlan.Stone;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A wall across the room built as an arcade: piers, and between each pair an opening that corbels
 * in a course at a time to a keystone. Above the keystone course it is solid wall to the ceiling,
 * however high that is &mdash; the arches are a fixed height and the wall above them is what
 * changes from room to room.
 *
 * <h2>Whole bays, symmetric, whatever the span</h2>
 * <p>As many openings as fit with a pier between each and one at each end. What is left over goes
 * to the two end piers equally, so the run meets the room's walls with a pier, wider than the rest
 * when the span was not a whole number of bays. An odd cell left over cannot be split, and goes to
 * the middle of the run instead: the middle pier when there is an even number of openings, the
 * middle opening when there is an odd number, which then has two keystones side by side. Either
 * way the run reads the same from both ends.</p>
 *
 * <h2>Where the run goes</h2>
 * <p>Across the middle of the room, as a {@code strip} does, unless that would put a pier against a
 * door in an end wall &mdash; then the nearest line either side that does not. No draw is spent on
 * that search; the only draw is the axis, and only for {@link Axis#ANY}.</p>
 *
 * @author Mark Gottschling on 10/30/2026
 */
public class ArcadePartitionShapeProvider implements IElevatedPartitionProvider {

    static final String FACING = "facing";
    static final String HALF = "half";

    private final ArcadePartitionShape shape;

    public ArcadePartitionShapeProvider(ArcadePartitionShape shape) {
        this.shape = shape;
    }

    @Override
    public ElevatedPartitionPlan elevate(int interiorWidth, int interiorDepth, Set<Coords2D> blocked,
                                         RandomSource random) {
        // Rolled here for CornerPartitionShapeProvider's reason: the room's own source, so the
        // draw is a pure function of the piece's seed and every chunk's postProcess agrees.
        boolean first = shape.axis().resolve(random).alongX();
        List<Boolean> axes = shape.axis() == Axis.ANY ? List.of(first, !first) : List.of(first);
        for (boolean alongX : axes) {
            ElevatedPartitionPlan plan = elevate(alongX, interiorWidth, interiorDepth, blocked);
            if (!plan.isEmpty()) {
                return plan;
            }
        }
        return ElevatedPartitionPlan.EMPTY;
    }

    private ElevatedPartitionPlan elevate(boolean alongX, int interiorWidth, int interiorDepth,
                                          Set<Coords2D> blocked) {
        int across = alongX ? interiorDepth : interiorWidth;
        int span = alongX ? interiorWidth : interiorDepth;
        // A cell on each side of the run, as a strip needs.
        if (across < 3) {
            return ElevatedPartitionPlan.EMPTY;
        }
        List<int[]> openings = openings(span);
        if (openings.isEmpty()) {
            return ElevatedPartitionPlan.EMPTY;
        }

        // Middle first, then outward a cell at a time, lower side first: deterministic, and never
        // hard against a side wall.
        int middle = across / 2;
        for (int d = 0; d < across; d++) {
            for (int line : d == 0 ? new int[]{middle} : new int[]{middle - d, middle + d}) {
                if (line < 1 || line > across - 2) {
                    continue;
                }
                List<Column> columns = columns(alongX, line, span, openings);
                if (columns.stream().noneMatch(column -> blocked.contains(column.cell()))) {
                    return new ElevatedPartitionPlan(columns, shape.archRows());
                }
            }
        }
        return ElevatedPartitionPlan.EMPTY;
    }

    /**
     * The openings along a run of this span, each as its inclusive {@code [start, end]}; empty when
     * not even one bay fits.
     */
    public List<int[]> openings(int span) {
        int pier = shape.pierWidth();
        int width = shape.openingWidth();
        int count = (span - pier) / (width + pier);
        if (count < 1) {
            return List.of();
        }
        int spare = span - count * width - (count + 1) * pier;

        // Element widths along the run, pier first: pier, opening, pier, ..., opening, pier.
        int[] widths = new int[2 * count + 1];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = i % 2 == 0 ? pier : width;
        }
        widths[0] += spare / 2;
        widths[widths.length - 1] += spare / 2;
        // The middle element is a pier for an even count, an opening for an odd one.
        widths[count] += spare % 2;

        List<int[]> openings = new ArrayList<>(count);
        int u = 0;
        for (int i = 0; i < widths.length; i++) {
            if (i % 2 == 1) {
                openings.add(new int[]{u, u + widths[i] - 1});
            }
            u += widths[i];
        }
        return openings;
    }

    private List<Column> columns(boolean alongX, int line, int span, List<int[]> openings) {
        int height = shape.openingHeight();
        int steps = shape.corbelSteps();
        Stone base = shape.baseBlock()
                .map(block -> new Stone(Optional.of(block), Map.of()))
                .orElse(Stone.WALL);
        // A run along X goes west to east as u rises; along Z, north to south.
        String towardLow = alongX ? "west" : "north";
        String towardHigh = alongX ? "east" : "south";

        Map<Integer, int[]> openingAt = new HashMap<>();
        for (int[] opening : openings) {
            for (int u = opening[0]; u <= opening[1]; u++) {
                openingAt.put(u, opening);
            }
        }

        List<Column> columns = new ArrayList<>(span);
        for (int u = 0; u < span; u++) {
            Coords2D cell = alongX ? new Coords2D(u, line) : new Coords2D(line, u);
            int[] opening = openingAt.get(u);
            if (opening == null) {
                // A pier: its foot, then wall all the way up.
                columns.add(new Column(cell, Map.of(0, base), 1));
                continue;
            }
            int start = opening[0];
            int end = opening[1];
            int inFromLow = u - start;
            int inFromHigh = end - u;
            int in = Math.min(inFromLow, inFromHigh);
            Map<Integer, Stone> stones = new LinkedHashMap<>();
            int fillFrom;
            if (in < steps) {
                // This cell's corbel course: the one that oversails the course below by one cell.
                // It springs from the side it is nearer, so it faces back toward that pier.
                String facing = inFromLow <= inFromHigh ? towardLow : towardHigh;
                stones.put(height + in, corbel(facing));
                fillFrom = height + in + 1;
            } else {
                int row = height + steps;
                int centreLow = (start + end) / 2;
                int centreHigh = (end - start) % 2 == 0 ? centreLow : centreLow + 1;
                if (u == centreLow || u == centreHigh) {
                    stones.put(row, keystone());
                    fillFrom = row + 1;
                } else {
                    fillFrom = row;
                }
            }
            columns.add(new Column(cell, stones, fillFrom));
        }
        return columns;
    }

    private Stone corbel(String facing) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(FACING, facing);
        properties.put(HALF, "top");
        return new Stone(shape.corbelBlock(), shape.corbelBlock().isPresent() ? properties : Map.of());
    }

    private Stone keystone() {
        return new Stone(shape.keystoneBlock(), Map.of());
    }
}
