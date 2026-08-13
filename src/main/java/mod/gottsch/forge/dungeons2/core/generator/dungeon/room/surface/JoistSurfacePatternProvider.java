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
 * Parallel beams crossing the surface in <strong>one</strong> direction &mdash; joists, or rafters
 * &mdash; and, as a second instance, the brackets that carry them.
 *
 * <p>{@link GridSurfacePatternProvider} draws a lattice on both axes, which reads as formal masonry:
 * a coffered ceiling is a grid of sunken panels. A run of parallel beams reads as something else
 * entirely &mdash; the underside of the floor above you. There is no way to express one with the
 * other, which is why this is a second provider rather than an axis flag on the grid. (The
 * {@code ambulatory} result in the pillar package is the cautionary tale for adding a layout that
 * turns out to draw what an existing one already did; that is not the case here, since the grid
 * marks both axes in every room and this marks one in every room.)</p>
 *
 * <h2>Beams span the SHORT axis, which is the opposite of a colonnade</h2>
 * <p>{@code ColonnadePillarPatternProvider} runs <em>along</em> the room's longer axis, because a
 * colonnade is a length you walk down. A joist is a span: it is the shorter distance a beam has to
 * bridge, and beams repeat along the room's length. So the run direction here is the room's shorter
 * axis and the {@code spacing} rhythm steps along its longer one. Do not reuse the colonnade's
 * elongation test &mdash; it answers the opposite question.</p>
 *
 * <p>The axis comes from the extents and <strong>never from the RNG</strong>; a square surface
 * always runs along {@code u}. A room is rendered once per overlapping chunk and every run must
 * agree, or the ceiling tears at the seam. Unlike the colonnade this <em>never declines</em> a
 * square room: a square room with beams across it is an ordinary ceiling, where a square room with
 * two rows of columns down it is a grid that lost its middle row.</p>
 *
 * <h2>A bracket goes UNDER its beam, which is why it is a second instance</h2>
 * <p>The first cut had the bracket <em>replace</em> the beam at the two cells where the run met a
 * wall. Mark, on the first screenshots: <em>"the corbels/support should be under the beams, not
 * in-line with them."</em> He is right, and it is what a bracket is for &mdash; a corbel carries a
 * load from below, so one sitting in the beam's own row is not supporting it, it is interrupting
 * it. The beam now runs unbroken wall to wall and the brackets hang one row lower.</p>
 *
 * <p>That makes them a <strong>separate layer at a different depth</strong>, not different cells in
 * one plan, so {@link Part} splits this class into two instances the selector stacks. They cannot
 * drift apart: both derive the run axis and the beam lines from the same extents and {@code spacing},
 * so a bracket is always under a beam by construction rather than by agreement.</p>
 *
 * <h2>Orientation is derived, and degrades</h2>
 * <p>A beam block that carries {@code axis} &mdash; a log, a pillar block &mdash; is laid along the
 * run, so its value depends on which way the beams went and cannot be authored. A beam block with
 * no {@code axis} (any plain cube, and most stone) is placed unchanged: the derivation goes through
 * {@code BlockStateCodec.withProperties}, which drops a property the block does not have rather
 * than throwing. That is what lets the same entry carry a stone beam and a timber one.</p>
 *
 * <p>The <strong>bracket</strong> is optional and is any block &mdash; {@code dungeonblocks}'
 * corbels are the obvious choice, stairs are an equally good one, and no bracket at all is the
 * default. It takes its {@code facing} from {@link SurfaceOrient} applied to <em>that end's</em>
 * outward direction, so one authored value comes out correctly turned at both ends.</p>
 *
 * <p>A spacing of 1 or less would make every line a beam, which is a solid ceiling rather than a run
 * of joists; it yields an empty plan instead, exactly as the grid does.</p>
 *
 * @author Mark Gottschling on Aug 11, 2026
 */
public class JoistSurfacePatternProvider implements ISurfacePatternProvider {

    /** A beam every third cell. Shared with the grid's, since it is the same authored field. */
    public static final int DEFAULT_SPACING = GridSurfacePatternProvider.DEFAULT_SPACING;

    /** Which half of the treatment an instance draws. See the class notes. */
    public enum Part {
        /** The beams themselves: every cell of every run, wall to wall. */
        BEAMS,
        /** Only the two cells at each run's ends, for the layer one row below the beams. */
        BRACKETS
    }

    private final Part part;
    private final int spacing;
    private final BlockState block;
    private final SurfaceOrient orient;
    private final Direction uDirection;
    private final Direction vDirection;

    /** The beams. Nothing about them is orientable, so this needs no axes beyond the run's own. */
    public static JoistSurfacePatternProvider beams(int spacing, BlockState beam) {
        return new JoistSurfacePatternProvider(Part.BEAMS, spacing, beam, SurfaceOrient.NONE,
                CeilingSurface.U_DIRECTION, CeilingSurface.V_DIRECTION);
    }

    /**
     * The brackets under the beams, as a layer of its own.
     *
     * @param orient     which way a bracket that has a {@code facing} property is turned.
     *                   {@link SurfaceOrient#OUTWARD} points it at the wall the run ends on;
     *                   {@code INWARD} points it into the room, which is what a
     *                   {@code dungeonblocks} corbel's model asks for.
     * @param uDirection the world direction {@code u} advances in; {@code vDirection} likewise.
     *                   Supplied by the surface, as {@link BorderSurfacePatternProvider} takes them
     *                   and for the same reason: a provider sees only a {@code (u, v)} extent, and
     *                   a facing is a world direction.
     */
    public static JoistSurfacePatternProvider brackets(int spacing, BlockState bracket,
                                                       SurfaceOrient orient,
                                                       Direction uDirection, Direction vDirection) {
        return new JoistSurfacePatternProvider(Part.BRACKETS, spacing, bracket, orient,
                uDirection, vDirection);
    }

    private JoistSurfacePatternProvider(Part part, int spacing, BlockState block,
                                        SurfaceOrient orient,
                                        Direction uDirection, Direction vDirection) {
        this.part = Objects.requireNonNull(part, "part");
        this.spacing = spacing;
        this.block = Objects.requireNonNull(block, "block");
        this.orient = Objects.requireNonNull(orient, "orient");
        this.uDirection = Objects.requireNonNull(uDirection, "uDirection");
        this.vDirection = Objects.requireNonNull(vDirection, "vDirection");
    }

    public Part part() {
        return part;
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        if (spacing <= 1 || uSize <= 0 || vSize <= 0) {
            return plan;
        }
        // Beams bridge the shorter extent; the rhythm steps along the longer one. A square surface
        // runs along u -- deterministic, never rolled. Both parts derive this identically, which is
        // what puts a bracket under a beam rather than merely near one.
        boolean alongU = uSize <= vSize;
        int runLength = alongU ? uSize : vSize;
        int strideExtent = alongU ? vSize : uSize;
        Direction runDirection = alongU ? uDirection : vDirection;

        BlockState laid = alongAxis(block, runDirection);

        for (int stride = 0; stride < strideExtent; stride++) {
            if (!GridSurfacePatternProvider.onCentredRhythm(stride, strideExtent, spacing)) {
                continue;
            }
            if (part == Part.BEAMS) {
                for (int along = 0; along < runLength; along++) {
                    set(plan, alongU, along, stride, laid);
                }
            } else {
                // The wall this end rests on lies back along the run at 0, and on ahead at the far
                // end -- so one authored orient turns both brackets the same way relative to their
                // own wall. A one-cell run has a single end, and gets a single bracket.
                set(plan, alongU, 0, stride, oriented(laid, runDirection.getOpposite()));
                if (runLength > 1) {
                    set(plan, alongU, runLength - 1, stride, oriented(laid, runDirection));
                }
            }
        }
        return plan;
    }

    private static void set(SurfacePlan plan, boolean alongU, int along, int stride, BlockState state) {
        if (alongU) {
            plan.set(along, stride, state);
        } else {
            plan.set(stride, along, state);
        }
    }

    /**
     * Lays a block along the run. Set through {@code withProperties} so a beam of plain cubes --
     * which is every stone beam -- is returned untouched instead of throwing.
     *
     * <p>This <strong>overrides</strong> an authored {@code axis}, unlike every other property in
     * the entry: an authored one is right in at most half the rooms, since the run direction is a
     * property of the room's proportions rather than of the pattern.</p>
     */
    private static BlockState alongAxis(BlockState state, Direction run) {
        return BlockStateCodec.withProperties(state,
                Map.of("axis", run.getAxis().getSerializedName()));
    }

    /** Applies {@link #orient} to one bracket. Same lenient path as {@link #alongAxis}. */
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
