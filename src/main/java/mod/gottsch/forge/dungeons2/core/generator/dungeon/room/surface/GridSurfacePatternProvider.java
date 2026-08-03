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
 * A lattice of ribs dividing the surface into panels &mdash; a coffered ceiling.
 *
 * <p>Ribs run in both axes at {@code spacing} intervals, leaving the untouched cells between them
 * as recessed panels of the base block. Unlike a checkerboard this marks a minority of cells, which
 * is what makes it read as structure rather than as a texture.</p>
 *
 * <p><strong>The lattice is centred, not anchored to a corner.</strong> A rib always lands on the
 * middle cell and the rest step outward from it, so the panels are symmetric about the room's axes;
 * anchoring at {@code u = 0} would leave a ragged partial panel at the far edge whose width depends
 * on the room's size. On an even extent the centre falls between two cells and the lattice sits one
 * cell off-centre &mdash; unavoidable on a discrete grid, and the same compromise
 * {@code CrossFloorPatternProvider} documents for its bands.</p>
 *
 * <p>A spacing of 1 or less would make every cell a rib, which is a solid fill rather than a
 * lattice; it yields an empty plan instead, the same degrade-to-nothing an empty pattern gets.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public class GridSurfacePatternProvider implements ISurfacePatternProvider {

    /** Panels two cells across. */
    public static final int DEFAULT_SPACING = 3;

    private final int spacing;
    private final BlockState rib;

    public GridSurfacePatternProvider(int spacing, BlockState rib) {
        this.spacing = spacing;
        this.rib = Objects.requireNonNull(rib, "rib");
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        if (spacing <= 1 || uSize <= 0 || vSize <= 0) {
            return plan;
        }
        int uCentre = (uSize - 1) / 2;
        int vCentre = (vSize - 1) / 2;
        for (int u = 0; u < uSize; u++) {
            boolean uRib = Math.abs(u - uCentre) % spacing == 0;
            for (int v = 0; v < vSize; v++) {
                boolean vRib = Math.abs(v - vCentre) % spacing == 0;
                if (uRib || vRib) {
                    plan.set(u, v, rib);
                }
            }
        }
        return plan;
    }
}
