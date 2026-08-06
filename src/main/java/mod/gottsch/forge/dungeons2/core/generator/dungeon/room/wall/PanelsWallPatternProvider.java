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

import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.WallSurface;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Repeating rectangular fields on a wall &mdash; the panel between the pilasters.
 *
 * <h2>Why this is only the field, and not a framed panel</h2>
 * <p>A recessed panel reads as a field with a border round it, and it would be easy to build the
 * border here too. It would also be redundant: a panel's horizontal edges are two
 * {@code courses} and its vertical edges are {@code pilasters}, both of which already exist and both
 * of which the {@code wall} slot can list either side of this one. The plan has now recorded the
 * same lesson three times &mdash; sparse patterns compose for free, so <strong>check whether an
 * ordered list of the existing ones already draws it before adding geometry</strong>.</p>
 *
 * <p>What no existing type can draw is the rectangle itself. A course fills a whole row; a strip
 * fills a whole column; neither can stop short of the wall vertically. That gap is this class.</p>
 *
 * <h2>Geometry</h2>
 * <p>Fields are {@code width} cells wide, repeat at {@code spacing}, and span the wall's rows except
 * {@code inset} left plain at the top and bottom. The set is centred on the wall and never straddles
 * a corner column, both for the reasons {@code PilastersWallPatternProvider} documents: the four runs
 * are different lengths, so counting from one end puts each wall out of phase, and only one run can
 * reach a given corner at a given depth.</p>
 *
 * <p>{@code inset} is vertical here and horizontal on {@code end_pilasters}. They are different
 * types with no shared geometry, and in each the name means "in from the edge this pattern is
 * measured against" &mdash; a field is measured against the wall's top and bottom, an end strip
 * against its ends.</p>
 *
 * <h2>The material has to contrast, and in {@code classic} that is hard</h2>
 * <p>A field is texture, not relief: at {@code projection} 0 it is only visible if its block reads
 * differently from the wall. {@code classic} draws wall, floor and ceiling all from
 * {@code minecraft:stone_bricks} (Backlog #15), so a panel in anything close to that block is
 * invisible. Either pick a genuinely different block or give it a projection.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public class PanelsWallPatternProvider implements ISurfacePatternProvider, IProjectingPatternProvider {

    /** Cells wide when a datapack does not say -- wide enough to read as a field, not a stripe. */
    public static final int DEFAULT_WIDTH = 3;

    // NOTE: there is deliberately no panels-specific default for `inset`. It is one codec field
    // shared with end_pilasters, so it has one default (0), and an int cannot tell "absent" from
    // "wrote the default" -- the same reason RoomScheme's weights and gates are not inheritable.
    // At 0 a field spans the wall's full height, which is a legitimate look; a panel that should
    // stop short of the floor and ceiling has to say `"inset": 1` and the README says so.

    private final BlockState block;
    private final int width;
    private final int spacing;
    private final int inset;
    private final int projection;
    private final CourseOrient orient;

    public PanelsWallPatternProvider(BlockState block, int width, int spacing, int inset,
                                     int projection, CourseOrient orient) {
        this.block = Objects.requireNonNull(block, "block");
        this.width = Math.max(1, width);
        this.spacing = Math.max(1, spacing);
        this.inset = Math.max(0, inset);
        this.projection = projection;
        this.orient = Objects.requireNonNull(orient, "orient");
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        return projection == 0 ? planFields(uSize, vSize, facing) : SurfacePlan.of(uSize, vSize);
    }

    @Override
    public Map<Integer, SurfacePlan> projectedPlans(int uSize, int vSize, Direction facing,
                                                    RandomSource random) {
        return projection == 0 ? Map.of() : Map.of(projection, planFields(uSize, vSize, facing));
    }

    private SurfacePlan planFields(int uSize, int vSize, Direction facing) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        BlockState state = CoursesWallPatternProvider.oriented(block, orient, facing);
        int vLo = inset;
        int vHi = vSize - 1 - inset;
        if (vLo > vHi) {
            // A wall too short to carry a field with its margins draws nothing, rather than
            // collapsing the margins and running the panel into the floor. Same "drop it rather
            // than squash it" rule a course out of range follows.
            return plan;
        }
        for (int start : starts(uSize, width, spacing, projection, facing)) {
            for (int u = start; u < start + width && u < uSize; u++) {
                for (int v = vLo; v <= vHi; v++) {
                    plan.set(u, v, state);
                }
            }
        }
        return plan;
    }

    /**
     * The left edge of each field. As many as fit at {@code spacing}, centred in the run's usable
     * span, with the corner columns given up by whichever run owns them.
     *
     * <p>Package-visible and pure, for the same reason
     * {@code PilastersWallPatternProvider#columns} is: it is the arithmetic a reader cannot check by
     * eye and the part that makes all four walls agree.</p>
     */
    static List<Integer> starts(int uSize, int width, int spacing, int projection, Direction facing) {
        boolean spansCorners = CoursesWallPatternProvider.ownsCorners(facing, 0);
        int lo = Math.max(0, WallSurface.projectableFrom(spansCorners && projection > 0, projection));
        int hi = Math.min(uSize - 1,
                projection > 0
                        ? WallSurface.projectableTo(spansCorners, uSize, projection)
                        : uSize - 1);
        if (CoursesWallPatternProvider.ownsCorners(facing, projection)) {
            lo += 1;
            hi -= 1;
        }
        // A field occupies width cells, so the last place one can START is width-1 short of the end.
        int lastStart = hi - Math.max(1, width) + 1;
        if (lo > lastStart) {
            return List.of();
        }
        int stride = Math.max(1, spacing);
        int span = lastStart - lo;
        int count = span / stride + 1;
        int used = (count - 1) * stride;
        int first = lo + (span - used) / 2;
        List<Integer> starts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            starts.add(first + i * stride);
        }
        return starts;
    }
}
