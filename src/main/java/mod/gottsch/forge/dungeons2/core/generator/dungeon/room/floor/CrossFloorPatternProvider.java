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
 * Lays an accent cross (a plus) through the room's centre: a band of {@code thickness} columns at
 * the width's midpoint and a band of {@code thickness} rows at the depth's midpoint, together
 * quartering the floor. Everything else is base floor.
 *
 * <p>Deliberately always <em>both</em> axes &mdash; a single-axis stripe is a different look and
 * there is no second real use for it yet, so it does not get a config knob until there is (the same
 * don't-over-commit reasoning {@code FloorPatternEntry} applies to its {@code type} discriminator).
 * </p>
 *
 * <p>The band is centred rather than anchored: for an even width and an odd thickness the band sits
 * one cell off-centre rather than splitting a cell, which is unavoidable on a discrete grid. A
 * thickness at or above the room's size makes the whole floor accent, which is degenerate but not
 * wrong, so it is left alone rather than clamped.</p>
 *
 * <p>Also an {@link IFloorOverlayGenerator}: {@link #overlay} emits only the cross cells, so it can
 * be layered over another generator's fill (e.g. {@link CheckerboardFloorPatternProvider}) in a
 * {@code "composite"} entry. Geometry lives in {@link #plan} as registry-free data for the same
 * unit-testability reason as {@code FloorBorderPatternProvider}.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public class CrossFloorPatternProvider implements IDungeonFloorGenerator, IFloorOverlayGenerator {
    /** A single-cell-wide cross. */
    public static final int DEFAULT_THICKNESS = 1;

    private final int thickness;
    private final Block accentBlock;
    private final BlockState baseState;

    public CrossFloorPatternProvider(int thickness, Block accentBlock) {
        this(thickness, accentBlock, Blocks.STONE_BRICKS.defaultBlockState());
    }

    /**
     * @param baseState what non-cross cells get from {@link #build} (the motif's own floor base,
     *                  supplied by {@code FloorPatternSelector}). Unused by {@link #overlay},
     *                  which leaves those cells to whatever it is layered over.
     */
    public CrossFloorPatternProvider(int thickness, Block accentBlock, BlockState baseState) {
        this.thickness = thickness;
        this.accentBlock = Objects.requireNonNull(accentBlock, "accentBlock");
        this.baseState = Objects.requireNonNull(baseState, "baseState");
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out);
    }

    /** Fills every cell: the cross in the accent block, everything else in the base block. */
    public void build(int width, int depth, int originX, int originZ, int floorY, List<BlockPlacement> out) {
        emit(width, depth, originX, originZ, floorY, out, true);
    }

    /** Emits only the cross cells, leaving the rest to whatever this is layered over. */
    @Override
    public void overlay(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        emit(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out, false);
    }

    private void emit(int width, int depth, int originX, int originZ, int floorY, List<BlockPlacement> out,
                      boolean includeBase) {
        boolean[][] grid = plan(width, depth, thickness);
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
     * Pure geometry: {@code true} where the cross runs. Package-visible for direct unit testing.
     * A non-positive thickness yields an empty grid (all base), the same graceful degradation an
     * empty pattern always has elsewhere in this codebase.
     */
    static boolean[][] plan(int width, int depth, int thickness) {
        boolean[][] grid = new boolean[width][depth];
        if (thickness <= 0) {
            return grid;
        }
        int xLo = (width - thickness) / 2;
        int xHi = xLo + thickness - 1;
        int zLo = (depth - thickness) / 2;
        int zHi = zLo + thickness - 1;

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                boolean inVerticalBand = x >= xLo && x <= xHi;
                boolean inHorizontalBand = z >= zLo && z <= zHi;
                grid[x][z] = inVerticalBand || inHorizontalBand;
            }
        }
        return grid;
    }
}
