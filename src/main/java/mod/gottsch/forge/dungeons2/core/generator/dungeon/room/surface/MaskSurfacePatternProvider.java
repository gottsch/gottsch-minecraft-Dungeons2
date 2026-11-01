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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * One block wherever a {@link Mask} says so, inside a rectangle {@code inset} cells in from the
 * edge &mdash; the shape a floor pattern's {@code plan} grid already describes, put on a surface.
 *
 * <p>This is how the ceiling borrows the floor's checkerboard, cross, diagonal and spokes without a
 * second copy of their geometry: the mask is the floor provider's own {@code plan}. The mask is
 * computed on the INNER extent, so an inset cross or spoke star is still centred on the room.</p>
 *
 * <p>An inset that eats the surface marks nothing, as {@link BorderSurfacePatternProvider} does.</p>
 */
public class MaskSurfacePatternProvider implements ISurfacePatternProvider {

    /** A shape over a {@code uSize x vSize} extent; {@code true} cells take the block. */
    @FunctionalInterface
    public interface Mask {
        boolean[][] plan(int uSize, int vSize);
    }

    private final int inset;
    private final BlockState block;
    private final Mask mask;

    public MaskSurfacePatternProvider(int inset, BlockState block, Mask mask) {
        this.inset = inset;
        this.block = Objects.requireNonNull(block, "block");
        this.mask = Objects.requireNonNull(mask, "mask");
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        int innerU = uSize - 2 * inset;
        int innerV = vSize - 2 * inset;
        if (innerU <= 0 || innerV <= 0) {
            return plan;
        }
        boolean[][] grid = mask.plan(innerU, innerV);
        for (int u = 0; u < innerU; u++) {
            for (int v = 0; v < innerV; v++) {
                if (grid[u][v]) {
                    plan.set(inset + u, inset + v, block);
                }
            }
        }
        return plan;
    }
}
