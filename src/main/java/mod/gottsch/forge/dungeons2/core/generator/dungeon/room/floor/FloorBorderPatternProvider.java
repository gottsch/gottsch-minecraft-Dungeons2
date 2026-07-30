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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeonblocks.core.block.ModBlocks;
import mod.gottsch.forge.gottschcore.block.IFacingBlock;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Reproduces the hand-authored {@code floor_border_pattern_1.nbt} reference (a plain floor with a
 * decorative picture-frame ring of {@code dungeonblocks:left_large_stone_brick}/
 * {@code right_large_stone_brick} inset from the edge) for a floor of <strong>any</strong>
 * width/depth, rather than a single fixed-size template.
 *
 * <p>Reverse-engineered from the reference NBT (9x9 floor, ring inset 2 from each edge): the ring
 * is the perimeter of the rectangle {@code [inset, size-1-inset]} on each axis. Each of the 4
 * corners is a {@code RIGHT} block facing the cardinal direction reached by walking the ring
 * clockwise from north (NW&rarr;north, NE&rarr;east, SE&rarr;south, SW&rarr;west). Each straight
 * run between two corners alternates {@code LEFT}/{@code RIGHT} starting with {@code LEFT},
 * facing outward along that edge's own cardinal direction. Everywhere else (outside the ring,
 * and inside it) is plain floor.</p>
 *
 * <p>Degenerate sizes degrade gracefully: if the floor is too small to fit a ring at the
 * requested inset (fewer than 2 cells on either axis between the two insets), the whole floor is
 * plain &mdash; same as an empty pattern always has elsewhere in this codebase.</p>
 *
 * <p>The ring's geometry is computed by {@link #plan} as plain data ({@link RingCell}, no
 * {@code BlockState}/registry involved) specifically so it's unit-testable without a running
 * Forge instance &mdash; {@code dungeonblocks:*} blocks only resolve once Forge has actually
 * loaded that mod (see {@code DecorationOnRealRoomTest}'s note on the same limitation), which a
 * bare {@code Bootstrap.bootStrap()} JUnit environment never does.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public class FloorBorderPatternProvider implements IDungeonFloorGenerator {
    /** Matches the reference NBT: the ring's outer edge sits 2 cells in from the floor edge. */
    public static final int DEFAULT_INSET = 2;

    private static final BlockState PLAIN = Blocks.STONE_BRICKS.defaultBlockState();

    private final int inset;

    public FloorBorderPatternProvider() {
        this(DEFAULT_INSET);
    }

    public FloorBorderPatternProvider(int inset) {
        this.inset = inset;
    }

    /** One ring cell's block/orientation, independent of any Minecraft registry. */
    record RingCell(boolean left, Direction facing) {
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out);
    }

    /**
     * Builds the pattern for a floor of the given size at the given origin, independent of
     * {@link RoomData} (e.g. for use outside the room pipeline).
     */
    public void build(int width, int depth, int originX, int originZ, int floorY, List<BlockPlacement> out) {
        RingCell[][] grid = plan(width, depth, inset);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                BlockState state = grid[x][z] == null ? PLAIN : toBlockState(grid[x][z]);
                out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, state));
            }
        }
    }

    /**
     * Pure geometry: which cells belong to the decorative ring, and their block/orientation.
     * {@code null} means plain floor. Package-visible for direct unit testing.
     */
    static RingCell[][] plan(int width, int depth, int inset) {
        RingCell[][] grid = new RingCell[width][depth];

        int xMin = inset;
        int xMax = width - 1 - inset;
        int zMin = inset;
        int zMax = depth - 1 - inset;
        if (xMax - xMin < 1 || zMax - zMin < 1) {
            // Too small to fit a ring at this inset -- plain floor only.
            return grid;
        }

        // Corners: always RIGHT, facing the direction reached walking the ring clockwise from north.
        grid[xMin][zMin] = new RingCell(false, Direction.NORTH);
        grid[xMax][zMin] = new RingCell(false, Direction.EAST);
        grid[xMax][zMax] = new RingCell(false, Direction.SOUTH);
        grid[xMin][zMax] = new RingCell(false, Direction.WEST);

        // North/south straight runs (excluding corners), alternating LEFT/RIGHT starting with LEFT.
        for (int x = xMin + 1, i = 0; x < xMax; x++, i++) {
            grid[x][zMin] = new RingCell(i % 2 == 0, Direction.NORTH);
            grid[x][zMax] = new RingCell(i % 2 == 0, Direction.SOUTH);
        }
        // West/east straight runs (excluding corners), alternating LEFT/RIGHT starting with LEFT.
        for (int z = zMin + 1, i = 0; z < zMax; z++, i++) {
            grid[xMin][z] = new RingCell(i % 2 == 0, Direction.WEST);
            grid[xMax][z] = new RingCell(i % 2 == 0, Direction.EAST);
        }
        return grid;
    }

    private static BlockState toBlockState(RingCell cell) {
        var block = cell.left() ? ModBlocks.LEFT_LARGE_STONE_BRICK : ModBlocks.RIGHT_LARGE_STONE_BRICK;
        return block.get().defaultBlockState().setValue(IFacingBlock.FACING, cell.facing());
    }
}
