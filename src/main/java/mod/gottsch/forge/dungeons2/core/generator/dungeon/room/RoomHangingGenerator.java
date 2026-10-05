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

import mod.gottsch.forge.dungeons2.core.config.HangingConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places a room scheme's {@code hangings} slot: fixtures hung from the ceiling on a run of chain.
 *
 * <h2>It reads the room it is hanging in</h2>
 * <p>Every other slot knows where the ceiling is from the room's height. This one cannot: a rising
 * vault (#68) has lifted it over some cells, a coffer rib or a cornice has dropped it over others,
 * a column or a partition fills the whole column, and a dais has raised the floor. So it runs after
 * every other block-emitting step and reads their output &mdash; the placement list, last write
 * wins, exactly as the piece will render it &mdash; column by column. What it needs from a column
 * is one unbroken run of air and a block closing it off at the top that a lantern could hang from.
 * Columns, bars and piers need no special case: a column with no air run is simply refused.</p>
 *
 * <h2>It claims no floor cells</h2>
 * <p>Every fixture is hung with {@link #CLEARANCE} rows of air beneath it, so a player walks under
 * it and a pot may stand below it. That is why this returns nothing and why the pots, placed after
 * it, do not need to know about it.</p>
 *
 * @author Mark Gottschling on Sep 28, 2026
 */
public final class RoomHangingGenerator {

    /** Rows of air left under the lowest block of a fixture: a player's height. */
    static final int CLEARANCE = 2;

    static final String FACING = "facing";
    /** Vanilla's lantern property; dropped at resolve by every block that lacks it. */
    static final String HANGING = "hanging";
    static final String PART = "part";
    static final String AIR = "minecraft:air";

    /**
     * Fixtures taller than one block, and how tall. The gibbet is a stack of {@code part}
     * bottom/middle/top that its own item places as a unit; a worldgen write has to spell each part
     * out. Keyed on the id because nothing on a block id says how tall its model is.
     */
    static final Map<String, Integer> TALL = Map.of("dungeonblocks:gibbet", 3);
    private static final String[] PARTS = {"bottom", "middle", "top"};

    /** What vanilla's {@code Block#canSupportCenter} tests against: a lantern's hook. */
    private static final VoxelShape CENTRE_SUPPORT = Block.box(7.0D, 0.0D, 7.0D, 9.0D, 10.0D, 9.0D);

    private RoomHangingGenerator() {}

    /**
     * Emits this room's hangings.
     *
     * @param out the room's placement list so far, which is also what the columns are read from;
     *            call this after every other block-emitting step
     */
    public static void placeHangings(RoomData room, int floorY, HangingConfig config,
                                     RandomSource random, List<BlockPlacement> out) {
        List<HangingConfig.HangingVariant> variants = config.variants();
        int totalWeight = variants.stream().mapToInt(HangingConfig.HangingVariant::weight).sum();
        if (variants.isEmpty() || totalWeight <= 0) {
            return;
        }
        List<Coords2D> candidates = candidates(room, config.placement());
        if (candidates.isEmpty()) {
            return;
        }
        Map<Coords2D, Map<Integer, BlockPlacement>> columns = columns(candidates, floorY, out);

        CellDraw draw = CellDraw.of(candidates, config.minCount(), config.clampedMaxCount(), random);
        Set<Coords2D> keepClear = new HashSet<>();
        List<BlockPlacement> hung = new ArrayList<>();
        int placed = 0;
        // nextUndrawn, the tombs' loop: whether a cell can take a hanging depends on its column and
        // on the hangings already placed, so a refused cell is replaced rather than lost.
        while (placed < draw.count() && draw.hasUndrawn()) {
            Coords2D cell = draw.nextUndrawn();
            if (keepClear.contains(cell)) {
                continue;
            }
            AirRun run = airRun(columns.get(cell), floorY);
            if (run == null) {
                continue;
            }
            HangingConfig.HangingVariant variant = pickVariant(variants, totalWeight, random);
            int height = TALL.getOrDefault(variant.block(), 1);
            int spareRows = run.length() - height - CLEARANCE;
            if (spareRows < 0) {
                continue;
            }
            // A single-value range draws nothing, as a count range's does (CellDraw).
            int drop = config.clampedMaxDrop() > config.minDrop()
                    ? config.minDrop() + random.nextInt(config.clampedMaxDrop() - config.minDrop() + 1)
                    : config.minDrop();
            drop = Math.min(drop, spareRows);
            String facing = RoomFurnitureGenerator.facingFor(room, cell, random);

            int y = run.top();
            for (int link = 0; link < drop; link++, y--) {
                hung.add(new BlockPlacement(cell.getX(), y, cell.getY(), config.chain()));
            }
            for (int part = height - 1; part >= 0; part--, y--) {
                Map<String, String> properties = new LinkedHashMap<>();
                properties.put(FACING, facing);
                if (height > 1) {
                    properties.put(PART, PARTS[part]);
                } else {
                    properties.put(HANGING, "true");
                }
                hung.add(new BlockPlacement(cell.getX(), y, cell.getY(), variant.block(), properties));
            }
            keepClear.addAll(withNeighbours(cell));
            placed++;
        }
        // Appended after the scan, not during it: the columns were read from `out`, and a later
        // hanging must not read an earlier one's chain as its ceiling.
        out.addAll(hung);
    }

    /** The cells a placement offers, before any column is read. */
    static List<Coords2D> candidates(RoomData room, HangingConfig.HangingPlacement placement) {
        return switch (placement) {
            case AGAINST_WALL -> RoomPropGenerator.eligibleCells(room);
            case FREE -> interior(room);
            case CENTRE -> centre(room);
        };
    }

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
     * The middle cell of the interior, or the cells sharing the middle on an axis whose interior
     * extent is even &mdash; so one, two or four. An even hall has no single middle cell, and
     * picking one of the two would put every chandelier in the mod a half-block off to the same side.
     */
    private static List<Coords2D> centre(RoomData room) {
        int innerW = room.getWidth() - 2;
        int innerD = room.getDepth() - 2;
        if (innerW < 1 || innerD < 1) {
            return List.of();
        }
        List<Coords2D> cells = new ArrayList<>();
        for (int x : middles(innerW)) {
            for (int z : middles(innerD)) {
                cells.add(new Coords2D(room.getOriginX() + 1 + x, room.getOriginZ() + 1 + z));
            }
        }
        return cells;
    }

    private static int[] middles(int extent) {
        return extent % 2 == 1 ? new int[]{extent / 2} : new int[]{extent / 2 - 1, extent / 2};
    }

    /** The final block at each Y above the floor, for each candidate column, later writes winning. */
    private static Map<Coords2D, Map<Integer, BlockPlacement>> columns(List<Coords2D> cells, int floorY,
                                                                      List<BlockPlacement> out) {
        Map<Coords2D, Map<Integer, BlockPlacement>> columns = new HashMap<>();
        for (Coords2D cell : cells) {
            columns.put(cell, new HashMap<>());
        }
        for (BlockPlacement p : out) {
            if (p.getY() <= floorY) {
                continue;
            }
            Map<Integer, BlockPlacement> column = columns.get(new Coords2D(p.getX(), p.getZ()));
            if (column != null) {
                column.put(p.getY(), p);
            }
        }
        return columns;
    }

    /** An inclusive run of air rows, and its top. */
    record AirRun(int bottom, int top) {
        int length() {
            return top - bottom + 1;
        }
    }

    /**
     * The column's air run and whether it can be hung from, or null.
     *
     * <p>Whatever stands on the floor first (a dais, a barrel) is stepped over, and the run begins
     * above it. A row nothing wrote is not air this generator can vouch for &mdash; it is the rock
     * the room was cut from, or another piece's &mdash; so meeting one before the run closes, or
     * before it opens, refuses the cell. So does a closing block whose underside will not hold a
     * lantern's hook: the fixture would pop off at its first block update.</p>
     */
    static AirRun airRun(Map<Integer, BlockPlacement> column, int floorY) {
        if (column == null) {
            return null;
        }
        int y = floorY + 1;
        while (column.containsKey(y) && !isAir(column.get(y))) {
            y++;
        }
        int bottom = y;
        while (column.containsKey(y) && isAir(column.get(y))) {
            y++;
        }
        BlockPlacement ceiling = column.get(y);
        if (y == bottom || ceiling == null || !holdsAHook(ceiling)) {
            return null;
        }
        return new AirRun(bottom, y - 1);
    }

    private static boolean isAir(BlockPlacement p) {
        return AIR.equals(p.getBlockId());
    }

    /** Vanilla's {@code Block#canSupportCenter} downward, without a level: the block's own shape. */
    static boolean holdsAHook(BlockPlacement ceiling) {
        BlockState state = BlockStateCodec.resolve(ceiling);
        if (state.isAir()) {
            return false;
        }
        VoxelShape face = state.getBlockSupportShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                .getFaceShape(Direction.DOWN);
        return !Shapes.joinIsNotEmpty(face, CENTRE_SUPPORT, BooleanOp.ONLY_SECOND);
    }

    private static List<Coords2D> withNeighbours(Coords2D cell) {
        return List.of(cell,
                new Coords2D(cell.getX() + 1, cell.getY()), new Coords2D(cell.getX() - 1, cell.getY()),
                new Coords2D(cell.getX(), cell.getY() + 1), new Coords2D(cell.getX(), cell.getY() - 1));
    }

    /** Weighted draw over the declared variants. Mirrors {@code RoomChestGenerator#pickVariant}. */
    static HangingConfig.HangingVariant pickVariant(List<HangingConfig.HangingVariant> variants,
                                                    int totalWeight, RandomSource random) {
        int roll = random.nextInt(totalWeight);
        for (HangingConfig.HangingVariant variant : variants) {
            roll -= variant.weight();
            if (roll < 0) {
                return variant;
            }
        }
        return variants.get(variants.size() - 1);
    }
}
