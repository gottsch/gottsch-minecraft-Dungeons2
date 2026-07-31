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
 * Radiates {@code spokes} evenly-spaced accent lines outward from the room's centre to its edges,
 * like a compass rose or a cartwheel. Everything else is base floor.
 *
 * <p>Spokes are rasterised by walking outward from the centre in half-cell steps along each angle
 * and marking whichever cell each step lands in &mdash; a half step (rather than a whole one)
 * guarantees the line is unbroken, since a whole step can skip a cell diagonally. Cells are marked
 * idempotently, so spokes overlapping near the centre is harmless.</p>
 *
 * <p>The count is not restricted to divisors of 4: 8 spokes gives the familiar
 * cardinals-plus-diagonals rose, but 6 or 5 work and simply produce angled lines that stair-step on
 * the grid. Angles start due east and go clockwise, which matters only if you are matching a
 * hand-authored reference. A non-positive count yields no spokes at all.</p>
 *
 * <p>Note the spokes converge at the centre, so the middle few cells are always accent regardless
 * of count &mdash; on a small floor a high count degenerates into a mostly-accent blob. That is a
 * property of the shape rather than a bug, so it is not clamped; author the count against the room
 * sizes the motif actually generates.</p>
 *
 * <p>Also an {@link IFloorOverlayGenerator}, on the same terms as {@link CrossFloorPatternProvider}.
 * Geometry lives in {@link #plan} as registry-free data so it is unit-testable without Forge.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public class RadialSpokesFloorPatternProvider implements IDungeonFloorGenerator, IFloorOverlayGenerator {
    /** Cardinals plus diagonals. */
    public static final int DEFAULT_SPOKES = 8;

    private final int spokes;
    private final Block accentBlock;
    private final BlockState baseState;

    public RadialSpokesFloorPatternProvider(int spokes, Block accentBlock) {
        this(spokes, accentBlock, Blocks.STONE_BRICKS.defaultBlockState());
    }

    /** @param baseState see {@code CrossFloorPatternProvider}'s constructor. */
    public RadialSpokesFloorPatternProvider(int spokes, Block accentBlock, BlockState baseState) {
        this.spokes = spokes;
        this.accentBlock = Objects.requireNonNull(accentBlock, "accentBlock");
        this.baseState = Objects.requireNonNull(baseState, "baseState");
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        build(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out);
    }

    /** Fills every cell: the spokes in the accent block, everything else in the base block. */
    public void build(int width, int depth, int originX, int originZ, int floorY, List<BlockPlacement> out) {
        emit(width, depth, originX, originZ, floorY, out, true);
    }

    /** Emits only the spoke cells, leaving the rest to whatever this is layered over. */
    @Override
    public void overlay(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        emit(room.getWidth(), room.getDepth(), room.getOriginX(), room.getOriginZ(), floorY, out, false);
    }

    private void emit(int width, int depth, int originX, int originZ, int floorY, List<BlockPlacement> out,
                      boolean includeBase) {
        boolean[][] grid = plan(width, depth, spokes);
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
     * Pure geometry: {@code true} where a spoke runs. Package-visible for direct unit testing.
     * A non-positive count yields an empty grid (all base).
     */
    static boolean[][] plan(int width, int depth, int spokes) {
        boolean[][] grid = new boolean[width][depth];
        if (spokes <= 0 || width <= 0 || depth <= 0) {
            return grid;
        }
        double centerX = (width - 1) / 2.0;
        double centerZ = (depth - 1) / 2.0;
        // Far enough to always clear the corner from the centre.
        double maxRadius = Math.hypot(width, depth);

        for (int spoke = 0; spoke < spokes; spoke++) {
            double angle = 2.0 * Math.PI * spoke / spokes;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            // Half-cell steps: a whole step can jump diagonally past a cell and break the line.
            for (double radius = 0.0; radius <= maxRadius; radius += 0.5) {
                int x = (int) Math.round(centerX + radius * dx);
                int z = (int) Math.round(centerZ + radius * dz);
                if (x < 0 || z < 0 || x >= width || z >= depth) {
                    continue;
                }
                grid[x][z] = true;
            }
        }
        return grid;
    }
}
