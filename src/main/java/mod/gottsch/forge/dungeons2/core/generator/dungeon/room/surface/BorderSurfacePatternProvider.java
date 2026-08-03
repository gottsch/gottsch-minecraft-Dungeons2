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
 * A rectangular ring inset from the surface's edge, with its own corner block.
 *
 * <p>On a ceiling this reads as a soffit or cornice band following the walls. Deliberately
 * surface-generic rather than ceiling-specific: the shape is the same one
 * {@code FloorBorderPatternProvider} draws, and writing it against {@link SurfacePlan} means the
 * floor could adopt it if that package is ever migrated, rather than a third copy appearing when
 * some other surface wants a ring.</p>
 *
 * <p>An inset large enough to collapse the ring produces nothing rather than a degenerate blob or
 * an exception &mdash; a small room simply gets an undecorated surface.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public class BorderSurfacePatternProvider implements ISurfacePatternProvider {

    /** Flush with the surface edge. */
    public static final int DEFAULT_INSET = 0;

    private final int inset;
    private final BlockState edge;
    private final BlockState corner;

    /**
     * @param corner the four corner cells of the ring. Authored separately because a corner is the
     *               one place a directional or distinct trim block reads differently; pass the same
     *               state as {@code edge} for a uniform ring.
     */
    public BorderSurfacePatternProvider(int inset, BlockState edge, BlockState corner) {
        this.inset = inset;
        this.edge = Objects.requireNonNull(edge, "edge");
        this.corner = Objects.requireNonNull(corner, "corner");
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        int uLo = inset;
        int uHi = uSize - 1 - inset;
        int vLo = inset;
        int vHi = vSize - 1 - inset;
        if (uLo > uHi || vLo > vHi) {
            return plan; // inset ate the surface: no ring
        }
        for (int u = uLo; u <= uHi; u++) {
            for (int v = vLo; v <= vHi; v++) {
                boolean onU = u == uLo || u == uHi;
                boolean onV = v == vLo || v == vHi;
                if (!onU && !onV) {
                    continue;
                }
                plan.set(u, v, onU && onV ? corner : edge);
            }
        }
        return plan;
    }
}
