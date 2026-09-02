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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.PropConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places a room scheme's {@code props} slot: the furniture &mdash; barrels, crates, cages, an anvil.
 * Backlog #73.
 *
 * <h2>Why it is not called {@code RoomPropGenerator}</h2>
 * <p>Because that name is taken, by the generator that places <em>pots</em>. "Prop" was the wider
 * word when pots were the only thing standing on a floor; the {@code props} slot has since claimed
 * the narrower, more useful sense. Renaming the older class would be the tidier end state and is a
 * separate change from this one, so the two live side by side and this javadoc says which is which:
 * <strong>{@code RoomPropGenerator} is pots (entities), {@code RoomFurnitureGenerator} is props
 * (blocks).</strong></p>
 *
 * <h2>What it shares with the pots slot, and what it does not</h2>
 * <p>The count roll, the {@code taken} set and the one-cell-at-a-time draw are all
 * {@link CellDraw}'s, unchanged. What is new is that the candidate set depends on the config: a pot
 * always stands on the inner ring, where a prop stands where its
 * {@link PropConfig.PropPlacement} says. See {@link #candidates}.</p>
 *
 * <p>Props are emitted as <strong>blocks</strong> and the cells they took are returned, so
 * everything placed afterwards can avoid them. That matters most for pots, which are entities with
 * gravity: a pot spawned in the cell a barrel occupies falls and shatters the moment the chunk
 * ticks &mdash; the same trap the chest slot is ordered around.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public final class RoomFurnitureGenerator {

    /** Vanilla's horizontal facing property; see {@link PropConfig.PropVariant} on {@code oriented}. */
    static final String FACING = "facing";

    /** The four horizontal facings, in a fixed order so a random draw over them is reproducible. */
    private static final String[] HORIZONTAL = {"north", "east", "south", "west"};

    private RoomFurnitureGenerator() {}

    /**
     * Emits this room's props, returning the cells they took.
     *
     * @param occupied cells already claimed by architecture, spawners or chests; props avoid them
     *                 and add their own to what they return
     */
    public static Set<Coords2D> placeProps(RoomData room, int floorY, PropConfig config,
                                           Set<Coords2D> occupied, RandomSource random,
                                           List<BlockPlacement> out) {
        List<PropConfig.PropVariant> variants = config.variants();
        int totalWeight = variants.stream().mapToInt(PropConfig.PropVariant::weight).sum();
        if (variants.isEmpty() || totalWeight <= 0) {
            return Set.of();
        }

        List<Coords2D> candidates = candidates(room, config.placement(), occupied);
        if (candidates.isEmpty()) {
            return Set.of();
        }

        CellDraw draw = CellDraw.of(candidates, config.minCount(), config.clampedMaxCount(), random);
        Set<Coords2D> used = new LinkedHashSet<>();
        while (draw.hasNext()) {
            Coords2D cell = draw.next();
            PropConfig.PropVariant variant = pickVariant(variants, totalWeight, random);

            Map<String, String> properties = new LinkedHashMap<>();
            if (variant.oriented()) {
                properties.put(FACING, facingFor(room, cell, random));
            }
            // floorY + 1: resting on the floor surface, the same row the pots, chests and spawners
            // use.
            out.add(new BlockPlacement(cell.getX(), floorY + 1, cell.getY(), variant.block(),
                    properties));
            used.add(cell);
        }
        return used;
    }

    /**
     * The cells this placement rule offers, in floor-local coords, minus the doorway approaches and
     * minus {@code occupied}.
     *
     * <p>Returned as an ordered list because {@link CellDraw} draws from it by index: two runs of
     * the same piece over two overlapping chunks have to produce the same plan, and a set with no
     * iteration order would quietly break that.</p>
     *
     * <p>An empty result means "place nothing here", and every rule is allowed to return one. A
     * {@code corner} slot whose four corners are all under columns places no props rather than
     * spilling into the middle of the floor &mdash; the spill would produce exactly the arrangement
     * the author chose {@code corner} to avoid, and it would do so silently.</p>
     */
    static List<Coords2D> candidates(RoomData room, PropConfig.PropPlacement placement,
                                     Set<Coords2D> occupied) {
        return switch (placement) {
            // The pots slot's own set: the interior ring, wall-adjacent, doorway approaches already
            // removed. Shared rather than re-derived -- two copies of "which cell is against a
            // wall" would drift the first time either changed.
            case AGAINST_WALL -> RoomPropGenerator.eligibleCells(room, occupied);
            case CORNER -> filter(corners(room), room, occupied);
            case FREE -> filter(interior(room), room, occupied);
            case FLANKING_DOOR -> filter(doorFlanks(room), room, occupied);
        };
    }

    /** Every interior cell, row by row. The whole floor the hollow step cleared. */
    private static List<Coords2D> interior(RoomData room) {
        List<Coords2D> cells = new ArrayList<>();
        for (int x = 1; x < room.getWidth() - 1; x++) {
            for (int z = 1; z < room.getDepth() - 1; z++) {
                cells.add(new Coords2D(room.getOriginX() + x, room.getOriginZ() + z));
            }
        }
        return cells;
    }

    /**
     * The four interior corners, deduplicated.
     *
     * <p>The dedupe is not defensive tidiness: a room three cells across on either axis has an
     * interior one cell wide, so two of its "corners" are the same cell and one of them would
     * otherwise be drawn twice &mdash; two props written into one position, the second silently
     * replacing the first.</p>
     */
    private static List<Coords2D> corners(RoomData room) {
        int minX = room.getOriginX() + 1;
        int maxX = room.getOriginX() + room.getWidth() - 2;
        int minZ = room.getOriginZ() + 1;
        int maxZ = room.getOriginZ() + room.getDepth() - 2;
        if (maxX < minX || maxZ < minZ) {
            return List.of();
        }
        Set<Coords2D> distinct = new LinkedHashSet<>(List.of(
                new Coords2D(minX, minZ), new Coords2D(maxX, minZ),
                new Coords2D(minX, maxZ), new Coords2D(maxX, maxZ)));
        return new ArrayList<>(distinct);
    }

    /**
     * The two cells either side of each doorway's approach.
     *
     * <p>A doorway sits on the perimeter ring, so exactly one of its orthogonal neighbours is
     * interior: that is the approach, the cell a player walks through, and it is excluded from every
     * placement rule. The flanks are the approach's own neighbours along the wall &mdash; which is
     * the axis <em>perpendicular</em> to the step from door to approach, and is why the direction is
     * derived per door rather than assumed.</p>
     */
    private static List<Coords2D> doorFlanks(RoomData room) {
        Set<Coords2D> flanks = new LinkedHashSet<>();
        for (Coords2D door : room.getDoorways()) {
            for (Coords2D approach : orthogonalNeighbours(door)) {
                if (!isInterior(room, approach)) {
                    continue;
                }
                // The step door -> approach. Its perpendicular runs along the wall.
                int stepX = approach.getX() - door.getX();
                int stepZ = approach.getY() - door.getY();
                flanks.add(new Coords2D(approach.getX() + stepZ, approach.getY() + stepX));
                flanks.add(new Coords2D(approach.getX() - stepZ, approach.getY() - stepX));
            }
        }
        return new ArrayList<>(flanks);
    }

    private static List<Coords2D> orthogonalNeighbours(Coords2D cell) {
        return List.of(
                new Coords2D(cell.getX() + 1, cell.getY()),
                new Coords2D(cell.getX() - 1, cell.getY()),
                new Coords2D(cell.getX(), cell.getY() + 1),
                new Coords2D(cell.getX(), cell.getY() - 1));
    }

    /**
     * Interior, not a doorway approach, not already claimed. Applied to every rule's raw set,
     * including {@code free}: the rules differ in which cells they <em>offer</em>, never in whether
     * a prop may stand in a doorway or inside a column.
     */
    private static List<Coords2D> filter(List<Coords2D> cells, RoomData room,
                                         Set<Coords2D> occupied) {
        Set<Coords2D> blocked = RoomInterior.cellsInsideDoorways(room);
        List<Coords2D> kept = new ArrayList<>();
        for (Coords2D cell : cells) {
            if (isInterior(room, cell) && !blocked.contains(cell) && !occupied.contains(cell)) {
                kept.add(cell);
            }
        }
        return kept;
    }

    /**
     * Whether a cell is inside the room's wall ring. The room box includes its walls, so the
     * interior runs from {@code origin + 1} to {@code origin + size - 2} on each axis.
     */
    private static boolean isInterior(RoomData room, Coords2D cell) {
        return cell.getX() > room.getOriginX()
                && cell.getX() < room.getOriginX() + room.getWidth() - 1
                && cell.getY() > room.getOriginZ()
                && cell.getY() < room.getOriginZ() + room.getDepth() - 1;
    }

    /**
     * The facing to write on an oriented prop: away from the wall it backs onto, and a random
     * horizontal for a cell that touches no wall.
     *
     * <p>The random branch is what {@code free} placement needs. Falling back to a constant instead
     * (which is what {@code RoomChestGenerator#facingAwayFromWall} does, correctly, because a chest
     * is only ever placed on the ring) would line every prop in an open floor up facing north, which
     * reads as a grid rather than as clutter.</p>
     */
    static String facingFor(RoomData room, Coords2D cell, RandomSource random) {
        boolean touchesWall = cell.getX() == room.getOriginX() + 1
                || cell.getX() == room.getOriginX() + room.getWidth() - 2
                || cell.getY() == room.getOriginZ() + 1
                || cell.getY() == room.getOriginZ() + room.getDepth() - 2;
        return touchesWall
                ? RoomChestGenerator.facingAwayFromWall(room, cell)
                : HORIZONTAL[random.nextInt(HORIZONTAL.length)];
    }

    /** Weighted draw over the declared variants. Mirrors {@code RoomChestGenerator#pickVariant}. */
    static PropConfig.PropVariant pickVariant(List<PropConfig.PropVariant> variants, int totalWeight,
                                              RandomSource random) {
        int roll = random.nextInt(totalWeight);
        for (PropConfig.PropVariant variant : variants) {
            roll -= variant.weight();
            if (roll < 0) {
                return variant;
            }
        }
        return variants.get(variants.size() - 1);
    }
}
