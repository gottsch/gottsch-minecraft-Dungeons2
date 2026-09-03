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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;

/**
 * A run of chevrons &mdash; repeating V's pointing up the room's depth axis. Backlog #82.
 *
 * <h2>The geometry, which is one line</h2>
 * <p>Every {@code spacing} rows the V restarts at the floor's centre column and its two arms walk
 * outward at {@code slope} cells per row: at row {@code z}, with {@code r = z % spacing}, the arms
 * sit at {@code centre &plusmn; r * slope}. {@code filled} true marks everything between them
 * instead, which turns the outline into a run of solid triangles.</p>
 *
 * <p>An arm that walks off the edge of the floor is simply not drawn &mdash; it is not clipped back
 * to the wall, which would draw a vertical line the author never asked for. So a {@code spacing} or
 * {@code slope} too large for the room loses the outer part of each V rather than deforming it,
 * the same "draw less, never draw something else" rule {@code diamond} follows on walls.</p>
 *
 * <h2>It is anchored to the room, not to the world</h2>
 * <p>Like every other pattern here it plans in floor-local {@code (x, z)} and adds the room's
 * origin only when emitting, so every room starts its first V at its own near edge and at its own
 * centre column. That is what makes it read as deliberate: anchoring to world coordinates instead
 * would run one continuous chevron field through the walls between adjacent rooms.</p>
 *
 * <p>Also an {@link IFloorOverlayGenerator}: {@link #overlay} emits only the chevron cells, so it
 * layers over a fill inside a {@code "composite"}. Geometry lives in {@link #plan} as registry-free
 * data for the same unit-testability reason as {@code FloorBorderPatternProvider}.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public class ChevronFloorPatternProvider implements IDungeonFloorGenerator, IFloorOverlayGenerator {

    /** Rows between one V's apex and the next, so the default V is three rows deep. */
    public static final int DEFAULT_SPACING = 4;

    /** Cells outward per row: a 45 degree arm, the only slope that reads as a chevron at a glance. */
    public static final int DEFAULT_SLOPE = 1;

    private final int spacing;
    private final int slope;
    private final boolean filled;
    private final Block accentBlock;
    private final BlockState baseState;

    public ChevronFloorPatternProvider(int spacing, int slope, boolean filled, Block accentBlock) {
        this(spacing, slope, filled, accentBlock, Blocks.STONE_BRICKS.defaultBlockState());
    }

    /**
     * @param baseState what non-chevron cells get from {@link #build} (the motif's own floor base,
     *                  supplied by {@code FloorPatternSelector}). Unused by {@link #overlay},
     *                  which leaves those cells to whatever it is layered over.
     */
    public ChevronFloorPatternProvider(int spacing, int slope, boolean filled, Block accentBlock,
                                       BlockState baseState) {
        this.spacing = spacing;
        this.slope = slope;
        this.filled = filled;
        this.accentBlock = Objects.requireNonNull(accentBlock, "accentBlock");
        this.baseState = Objects.requireNonNull(baseState, "baseState");
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                      List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out);
    }

    /** Fills every cell: the chevrons in the accent block, everything else in the base block. */
    public void build(int width, int depth, int originX, int originZ, int floorY,
                      List<BlockPlacement> out) {
        emit(width, depth, originX, originZ, floorY, out, true);
    }

    /** Emits only the chevron cells, leaving the rest to whatever this is layered over. */
    @Override
    public void overlay(RoomData room, int floorY, IDungeonMotif motif, RandomSource random,
                        List<BlockPlacement> out) {
        emit(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out,
                false);
    }

    private void emit(int width, int depth, int originX, int originZ, int floorY,
                      List<BlockPlacement> out, boolean includeBase) {
        boolean[][] grid = plan(width, depth, spacing, slope, filled);
        BlockState accent = accentBlock.defaultBlockState();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                if (grid[x][z]) {
                    out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, accent));
                } else if (includeBase) {
                    out.add(BlockStateCodec.placement(originX + x, floorY, originZ + z, baseState));
                }
            }
        }
    }

    /**
     * Pure geometry: {@code true} where the chevrons run. Package-visible for direct unit testing,
     * and registry-free so it runs without a Forge instance.
     *
     * <p>A non-positive {@code spacing} or a negative {@code slope} yields an empty grid (all base),
     * the same graceful degradation every other pattern here has. A {@code slope} of 0 is not
     * degenerate and is not treated as one: it draws the centre line, which is a legitimate, if
     * plain, thing to ask for.</p>
     */
    static boolean[][] plan(int width, int depth, int spacing, int slope, boolean filled) {
        boolean[][] grid = new boolean[width][depth];
        if (spacing <= 0 || slope < 0 || width <= 0) {
            return grid;
        }
        int centre = (width - 1) / 2;
        for (int z = 0; z < depth; z++) {
            int offset = Math.floorMod(z, spacing) * slope;
            if (filled) {
                int lo = Math.max(0, centre - offset);
                int hi = Math.min(width - 1, centre + offset);
                for (int x = lo; x <= hi; x++) {
                    grid[x][z] = true;
                }
            } else {
                mark(grid, centre - offset, z, width);
                mark(grid, centre + offset, z, width);
            }
        }
        return grid;
    }

    /** Marks one arm cell, dropping it when the arm has walked off the floor. */
    private static void mark(boolean[][] grid, int x, int z, int width) {
        if (x >= 0 && x < width) {
            grid[x][z] = true;
        }
    }
}
