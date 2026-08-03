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
 * A square block of accent at the centre of the surface &mdash; a ceiling boss or medallion.
 *
 * <p>The focal point a coffered or bordered ceiling otherwise lacks, and the natural anchor for a
 * future hanging feature (a chandelier, a chain). Laid over a lattice in a {@code patterns} list it
 * replaces the middle panel; on its own it is a single distinguished block in an otherwise plain
 * ceiling.</p>
 *
 * <p>Centring follows the same discrete-grid rule as the rest of this package: an odd {@code size}
 * on an odd extent lands exactly centred, and any even case sits one cell off rather than splitting
 * a cell. A {@code size} at or beyond the surface fills it entirely &mdash; degenerate but not
 * wrong, so it is left alone rather than clamped, matching {@code CrossFloorPatternProvider}'s
 * reasoning about oversized bands.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public class CentreSurfacePatternProvider implements ISurfacePatternProvider {

    /** A single block. */
    public static final int DEFAULT_SIZE = 1;

    private final int size;
    private final BlockState block;

    public CentreSurfacePatternProvider(int size, BlockState block) {
        this.size = size;
        this.block = Objects.requireNonNull(block, "block");
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        if (size <= 0) {
            return plan;
        }
        int uLo = (uSize - size) / 2;
        int vLo = (vSize - size) / 2;
        for (int u = uLo; u < uLo + size; u++) {
            for (int v = vLo; v < vLo + size; v++) {
                // Out-of-range writes are swallowed by set(), so an oversized boss clips.
                plan.set(u, v, block);
            }
        }
        return plan;
    }
}
