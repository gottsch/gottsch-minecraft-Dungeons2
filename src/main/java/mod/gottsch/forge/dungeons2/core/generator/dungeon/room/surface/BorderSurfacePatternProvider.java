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

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
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
 * <h2>Orientation, and why it cannot come from the surface's facing</h2>
 * <p>A wall pattern gets its direction from the run it is drawn on: every cell of one run faces the
 * same way, so {@code CoursesWallPatternProvider} orients a whole band from the single
 * {@code facing} it was handed. A ring has no runs. Its four sides face four different ways, and on
 * a ceiling the surface's own facing is DOWN &mdash; it cannot name a horizontal direction at all.
 * So the outward direction is derived <em>per cell</em>, from which side of the ring the cell sits
 * on, using the axis directions the surface passes in ({@link CeilingSurface#U_DIRECTION}).</p>
 *
 * <p>Unlike a wall course, <strong>no inversion is applied</strong>. {@code CourseOrient.TOWARD_WALL}
 * has to flip the surface's facing because that facing points <em>into the room</em>, away from the
 * wall the trim leans on. Here the direction is computed pointing outward to begin with, so
 * {@link SurfaceOrient#OUTWARD} is used as-is. Stated because the wall's inversion is the single
 * most error-prone thing in that class and copying it here would be silently wrong.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public class BorderSurfacePatternProvider implements ISurfacePatternProvider {

    /** Flush with the surface edge. */
    public static final int DEFAULT_INSET = 0;

    private final int inset;
    private final BlockState edge;
    private final BlockState corner;
    private final SurfaceOrient orient;
    private final Direction uDirection;
    private final Direction vDirection;

    /**
     * @param corner the four corner cells of the ring. Authored separately because a corner is the
     *               one place a directional or distinct trim block reads differently; pass the same
     *               state as {@code edge} for a uniform ring.
     */
    public BorderSurfacePatternProvider(int inset, BlockState edge, BlockState corner) {
        this(inset, edge, corner, SurfaceOrient.NONE,
                CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION);
    }

    /**
     * @param orient     how to turn a block that has a {@code facing} property.
     *                   {@link SurfaceOrient#NONE} leaves it alone, which is what a ring of full
     *                   cubes wants and what the three-argument constructor gives.
     * @param uDirection the world direction {@code u} advances in; {@code vDirection} likewise.
     *                   Supplied by the surface rather than assumed, since a provider sees only a
     *                   {@code (u, v)} extent. Unused when {@code orient} is NONE.
     */
    public BorderSurfacePatternProvider(int inset, BlockState edge, BlockState corner,
                                        SurfaceOrient orient,
                                        Direction uDirection, Direction vDirection) {
        this.inset = inset;
        this.edge = Objects.requireNonNull(edge, "edge");
        this.corner = Objects.requireNonNull(corner, "corner");
        this.orient = Objects.requireNonNull(orient, "orient");
        this.uDirection = Objects.requireNonNull(uDirection, "uDirection");
        this.vDirection = Objects.requireNonNull(vDirection, "vDirection");
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
                boolean onULo = u == uLo;
                boolean onUHi = u == uHi;
                boolean onVLo = v == vLo;
                boolean onVHi = v == vHi;
                boolean onU = onULo || onUHi;
                boolean onV = onVLo || onVHi;
                if (!onU && !onV) {
                    continue;
                }
                BlockState state = onU && onV ? corner : edge;
                plan.set(u, v, oriented(state, outwardFrom(onULo, onUHi, onVLo, onVHi)));
            }
        }
        return plan;
    }

    /**
     * The direction pointing off the ring, away from its interior, for a cell on the given sides.
     *
     * <p>A corner sits on two sides at once and so has two equally valid answers. It takes the one
     * with the <strong>lowest {@link Direction} ordinal</strong> &mdash; the same deterministic
     * tie-break the corridor arch's {@code haunchFacing} and the wall courses' {@code orient} use.
     * Which of the two wins does not change what the corner looks like: both leave the block's solid
     * mass on the outside of the ring, and vanilla's own corner derivation reaches a mitre from
     * either (see {@code DungeonPiece#settleJoinShapes}). Determinism is the point, not the choice
     * &mdash; a corner that varies run to run is the class of bug the planner's EnumMap fix was.</p>
     */
    private Direction outwardFrom(boolean onULo, boolean onUHi, boolean onVLo, boolean onVHi) {
        Direction fromU = onULo ? uDirection.getOpposite() : (onUHi ? uDirection : null);
        Direction fromV = onVLo ? vDirection.getOpposite() : (onVHi ? vDirection : null);
        if (fromU == null) {
            return fromV;
        }
        if (fromV == null) {
            return fromU;
        }
        return fromU.ordinal() <= fromV.ordinal() ? fromU : fromV;
    }

    /**
     * Applies {@link #orient} to one cell. Set through {@code withProperties} rather than
     * {@code setValue} so a ring authored from a block with no {@code facing} &mdash; which is every
     * ring shipped before this existed &mdash; keeps that block placed square instead of throwing.
     */
    private BlockState oriented(BlockState state, Direction outward) {
        Direction target = switch (orient) {
            case OUTWARD -> outward;
            case INWARD -> outward.getOpposite();
            case NONE -> null;
        };
        return target == null
                ? state
                : BlockStateCodec.withProperties(state, Map.of("facing", target.getSerializedName()));
    }
}
