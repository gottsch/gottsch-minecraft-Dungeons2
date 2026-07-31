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
import mod.gottsch.forge.gottschcore.block.IFacingBlock;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * Reproduces the hand-authored {@code floor_border_pattern_1.nbt} reference (a plain floor with a
 * decorative picture-frame ring inset from the edge) for a floor of <strong>any</strong>
 * width/depth, rather than a single fixed-size template. The three blocks used (corner, left
 * edge, right edge) are required per instance &mdash; see {@link #FloorBorderPatternProvider(
 * int, Block, Block, Block)} &mdash; there is deliberately no Java-side default block for any of
 * the three slots. {@code floor_pattern_config} (via {@code FloorPatternEntry}'s {@code
 * cornerBlock}/{@code edgeLeftBlock}/{@code edgeRightBlock} fields) is the single source of truth
 * for which blocks a {@code "border"} entry renders; {@code FloorPatternSelector} degrades the
 * whole entry to plain floor (see {@code BasicFloorGenerator}) rather than constructing this class
 * with a guessed block when any slot fails to resolve.
 *
 * <p>Reverse-engineered from the reference NBT (9x9 floor, ring inset 2 from each edge): the ring
 * is the perimeter of the rectangle {@code [inset, size-1-inset]} on each axis. Each of the 4
 * corners is the corner block, facing the cardinal direction reached by walking the ring
 * clockwise from north (NW&rarr;north, NE&rarr;east, SE&rarr;south, SW&rarr;west). Each straight
 * run between two corners alternates the left/right edge blocks starting with left, facing
 * outward along that edge's own cardinal direction &mdash; if both are the same block (e.g. a
 * single plain block like {@code minecraft:polished_andesite}), the alternation is simply
 * invisible, which is exactly what you want for a pattern with no left/right texture variant.
 * Everywhere else (outside the ring, and inside it) is plain floor.</p>
 *
 * <p><strong>Orientation is applied generically, not just to {@code dungeonblocks} pieces.</strong>
 * A substituted block only gets a facing set if its blockstate actually has one ({@link
 * IFacingBlock#FACING} or vanilla's {@link HorizontalDirectionalBlock#FACING}, checked in that
 * order) &mdash; a plain cube like polished andesite has neither, so it's placed as-is with no
 * orientation attempt.</p>
 *
 * <p>Degenerate sizes degrade gracefully: if the floor is too small to fit a ring at the
 * requested inset (fewer than 2 cells on either axis between the two insets), the whole floor is
 * plain &mdash; same as an empty pattern always has elsewhere in this codebase.</p>
 *
 * <p>Also implements {@link IFloorOverlayGenerator}: {@link #overlay} emits only the ring cells
 * (no plain-floor fill), so this can be layered on top of another generator's own fill (e.g.
 * {@link CheckerboardFloorPatternProvider}) inside a {@code "composite"} {@code
 * floor_pattern_config} entry, via {@link CompositeFloorPatternProvider}.</p>
 *
 * <p>The ring's geometry is computed by {@link #plan} as plain data ({@link RingCell}, no
 * {@code BlockState}/registry involved) specifically so it's unit-testable without a running
 * Forge instance &mdash; {@code dungeonblocks:*} blocks only resolve once Forge has actually
 * loaded that mod (see {@code DecorationOnRealRoomTest}'s note on the same limitation), which a
 * bare {@code Bootstrap.bootStrap()} JUnit environment never does. For the same reason, {@code
 * plan} never touches the registry; only {@link #build}/{@link #overlay} do, once the three
 * blocks are already resolved {@link Block} instances.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public class FloorBorderPatternProvider implements IDungeonFloorGenerator, IFloorOverlayGenerator {
    /** Matches the reference NBT: the ring's outer edge sits 2 cells in from the floor edge. */
    public static final int DEFAULT_INSET = 2;

    private final int inset;
    private final Block cornerBlock;
    private final Block edgeLeftBlock;
    private final Block edgeRightBlock;
    private final BlockState baseState;

    /**
     * @param cornerBlock    block for the 4 ring corners
     * @param edgeLeftBlock  block for the "left" half of each straight run's alternation
     * @param edgeRightBlock block for the "right" half of each straight run's alternation. Pass
     *                       the same block as {@code edgeLeftBlock} for a single-block edge with
     *                       no visible alternation.
     */
    public FloorBorderPatternProvider(int inset, Block cornerBlock, Block edgeLeftBlock, Block edgeRightBlock) {
        this(inset, cornerBlock, edgeLeftBlock, edgeRightBlock, Blocks.STONE_BRICKS.defaultBlockState());
    }

    /**
     * @param baseState what non-ring cells get from {@link #build} (the motif's own floor base,
     *                  supplied by {@code FloorPatternSelector}). Unused by {@link #overlay}.
     */
    public FloorBorderPatternProvider(int inset, Block cornerBlock, Block edgeLeftBlock, Block edgeRightBlock,
                                      BlockState baseState) {
        this.baseState = Objects.requireNonNull(baseState, "baseState");
        this.inset = inset;
        this.cornerBlock = Objects.requireNonNull(cornerBlock, "cornerBlock");
        this.edgeLeftBlock = Objects.requireNonNull(edgeLeftBlock, "edgeLeftBlock");
        this.edgeRightBlock = Objects.requireNonNull(edgeRightBlock, "edgeRightBlock");
    }

    /** One ring cell's role/orientation, independent of any Minecraft registry. */
    record RingCell(boolean corner, boolean left, Direction facing) {
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out);
    }

    /**
     * Builds the pattern for a floor of the given size at the given origin, independent of
     * {@link RoomData} (e.g. for use outside the room pipeline). Fills every cell &mdash; ring
     * cells with the ring's blocks, everywhere else plain floor.
     */
    public void build(int width, int depth, int originX, int originZ, int floorY, List<BlockPlacement> out) {
        emit(width, depth, originX, originZ, floorY, out, true);
    }

    /**
     * As a {@link IFloorOverlayGenerator}: emits <strong>only</strong> the ring cells, leaving
     * everything else untouched so this can be layered over another generator's own full fill
     * (e.g. {@link CheckerboardFloorPatternProvider}) inside a {@code "composite"} entry.
     */
    @Override
    public void overlay(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        emit(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out, false);
    }

    private void emit(int width, int depth, int originX, int originZ, int floorY, List<BlockPlacement> out,
                       boolean includeBase) {
        RingCell[][] grid = plan(width, depth, inset);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (grid[x][z] == null) {
                    if (includeBase) {
                        out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, baseState));
                    }
                    continue;
                }
                out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, toBlockState(grid[x][z])));
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

        // Corners, facing the direction reached walking the ring clockwise from north.
        grid[xMin][zMin] = new RingCell(true, false, Direction.NORTH);
        grid[xMax][zMin] = new RingCell(true, false, Direction.EAST);
        grid[xMax][zMax] = new RingCell(true, false, Direction.SOUTH);
        grid[xMin][zMax] = new RingCell(true, false, Direction.WEST);

        // North/south straight runs (excluding corners), alternating LEFT/RIGHT starting with LEFT.
        for (int x = xMin + 1, i = 0; x < xMax; x++, i++) {
            grid[x][zMin] = new RingCell(false, i % 2 == 0, Direction.NORTH);
            grid[x][zMax] = new RingCell(false, i % 2 == 0, Direction.SOUTH);
        }
        // West/east straight runs (excluding corners), alternating LEFT/RIGHT starting with LEFT.
        for (int z = zMin + 1, i = 0; z < zMax; z++, i++) {
            grid[xMin][z] = new RingCell(false, i % 2 == 0, Direction.WEST);
            grid[xMax][z] = new RingCell(false, i % 2 == 0, Direction.EAST);
        }
        return grid;
    }

    private BlockState toBlockState(RingCell cell) {
        Block block;
        if (cell.corner()) {
            block = cornerBlock;
        } else if (cell.left()) {
            block = edgeLeftBlock;
        } else {
            block = edgeRightBlock;
        }
        return orient(block, cell.facing());
    }

    /** Sets a facing property only if the block actually has one; otherwise placed as-is. */
    private static BlockState orient(Block block, Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(IFacingBlock.FACING)) {
            return state.setValue(IFacingBlock.FACING, facing);
        }
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return state.setValue(HorizontalDirectionalBlock.FACING, facing);
        }
        return state;
    }
}
