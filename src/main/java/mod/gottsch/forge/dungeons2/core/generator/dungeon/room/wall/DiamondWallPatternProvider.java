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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * A run of diamonds (lozenges) along the wall &mdash; the first purely GEOMETRIC wall pattern.
 *
 * <h2>The shape is Manhattan distance, and that is the whole algorithm</h2>
 * <p>A diamond of radius {@code size} centred at {@code (uc, vc)} is the set of cells where
 * {@code |u - uc| + |v - vc|} equals {@code size} (the outline) or is at most {@code size} (filled).
 * Nothing here is approximated or rasterised: on a square grid a Manhattan ball <em>is</em> a
 * diamond, so the outline is exactly one cell thick on all four sides with no special-casing of the
 * tips, and it is symmetric under both reflections for free.</p>
 *
 * <h2>Height is the binding constraint, not width</h2>
 * <p>A wall's {@code vSize} is {@code roomHeight - 2} &mdash; <strong>3 to 8 rows</strong>, and most
 * often at the low end. A diamond spans {@code 2 * size + 1} rows, so {@code size} 1 needs 3,
 * {@code size} 2 needs 5, and {@code size} 3 needs 7 and is only ever drawable in the tallest rooms.
 * That is why this draws NOTHING rather than clipping when the wall is too short: a clipped diamond
 * is a triangle, and a wall of triangles reads as a different pattern that nobody authored. A
 * scheme wanting a guarantee gates itself with {@code minHeight}.</p>
 *
 * <h2>Spacing below the diamond's own width is a feature</h2>
 * <p>At {@code spacing >= 2 * size + 1} the diamonds stand apart as separate lozenges. Below that
 * they overlap and their edges cross into a continuous lattice &mdash; a trellis, which is a real
 * and quite different look rather than a degenerate one. It is not clamped away; the only floor is
 * 1, because a spacing of 0 would place every diamond at the same centre.</p>
 *
 * <h2>The run is centred along the wall</h2>
 * <p>As many whole diamonds as fit, then the whole run is centred, so the leftover is split evenly
 * between the two ends instead of trailing off one of them. Same treatment the colonnade gives its
 * columns, and for the same reason: a wall pattern that starts flush at one corner and stops short
 * of the other reads as a mistake even when the spacing is right.</p>
 *
 * <h2>Flush only</h2>
 * <p>This does not implement {@code IProjectingPatternProvider}. A projecting lozenge would want its
 * own edge treatment at the four diagonal faces &mdash; which is a stair-orientation problem, not a
 * geometry one &mdash; and there is nothing to say what a diagonal run of projecting stairs should
 * face. Flush is the honest version of this pattern.</p>
 *
 * @author Mark Gottschling on Aug 30, 2026
 */
public class DiamondWallPatternProvider implements ISurfacePatternProvider {

    /** Two: a 5x5 lozenge, the smallest that reads as a diamond rather than as a plus sign. */
    public static final int DEFAULT_SIZE = 2;

    /**
     * Six: at the default size that is a one-cell gap between lozenges. Wide enough that they read
     * as a row of separate marks, tight enough that a wall carries several.
     */
    public static final int DEFAULT_SPACING = 6;

    private final BlockState block;
    private final int size;
    private final int spacing;
    private final boolean filled;

    public DiamondWallPatternProvider(BlockState block, int size, int spacing, boolean filled) {
        this.block = Objects.requireNonNull(block, "block");
        this.size = Math.max(1, size);
        this.spacing = Math.max(1, spacing);
        this.filled = filled;
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        int span = 2 * size + 1;
        // Whole diamonds only -- see the class note on why a clipped one is not drawn small.
        if (uSize < span || vSize < span) {
            return plan;
        }

        int count = 1 + (uSize - span) / spacing;
        int used = span + (count - 1) * spacing;
        // + size because uStart names the first CENTRE, not the first cell it touches.
        int uStart = (uSize - used) / 2 + size;
        // The lower middle row in an even-height wall, matching every other centred thing in the
        // mod -- CentrePillarPatternProvider and the cross floor pattern both take (n - 1) / 2.
        int vCentre = (vSize - 1) / 2;

        for (int i = 0; i < count; i++) {
            mark(plan, uStart + i * spacing, vCentre);
        }
        return plan;
    }

    /** One diamond. Bounds are checked per cell because overlapping diamonds may reach past them. */
    private void mark(SurfacePlan plan, int uCentre, int vCentre) {
        for (int du = -size; du <= size; du++) {
            int remaining = size - Math.abs(du);
            for (int dv = -remaining; dv <= remaining; dv++) {
                if (!filled && Math.abs(du) + Math.abs(dv) != size) {
                    continue;
                }
                int u = uCentre + du;
                int v = vCentre + dv;
                if (u >= 0 && u < plan.uSize() && v >= 0 && v < plan.vSize()) {
                    plan.set(u, v, block);
                }
            }
        }
    }
}
