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

import java.util.Set;

/**
 * A surface pattern that wants to know where this run's DOORWAYS are (#72).
 *
 * <h2>What was actually missing</h2>
 * <p>Less than it looked. {@code BasicWallGenerator} has had the room's doorways all along &mdash;
 * it hands them to {@link WallSurface#emit} so the two door rows come out as air, and to
 * {@code emitProjected} so a cornice does not hang across an opening. What never reached the
 * <em>pattern</em> was the same set: {@link ISurfacePatternProvider#plan} takes a size, a facing and
 * a random, deliberately, because that is what lets one pattern type serve walls and ceilings
 * alike.</p>
 *
 * <p>So this is the wall's counterpart of {@link IProjectingPatternProvider}: an OPTIONAL extension
 * a provider may implement, tested for by the generator, leaving every existing pattern and the
 * generic contract untouched.</p>
 *
 * <h2>Columns, not cells</h2>
 * <p>A doorway is handed over as the set of {@code u} positions on this run that are doorway
 * columns, not as world coordinates. Two reasons: a provider works in {@code (u, v)} and converting
 * back would hand it the coordinate space this interface exists to keep it out of; and a doorway
 * occupies a whole column of the run as far as a pattern is concerned &mdash; the two rows the door
 * itself fills are {@link WallSurface#DOOR_HALF_LOW_V} and {@link WallSurface#DOOR_HALF_HIGH_V}, and
 * a pattern that writes there is overwritten by air anyway.</p>
 *
 * <p>A 2-wide door is TWO adjacent columns, because the maze stores it as two doorway cells. A
 * pattern that frames an opening therefore cannot assume one column per door &mdash; see
 * {@code DoorJambsWallPatternProvider}, which brackets each run of adjacent columns rather than each
 * column.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public interface IDoorAwarePatternProvider extends ISurfacePatternProvider {

    /**
     * As {@link ISurfacePatternProvider#plan}, plus the run's doorway columns.
     *
     * @param doorColumns {@code u} positions on this run that are doorway cells; empty for a run
     *                    with no opening in it, which is most of them
     */
    SurfacePlan plan(int uSize, int vSize, Direction facing, Set<Integer> doorColumns,
                     RandomSource random);

    /**
     * The door-blind form, which a door-aware pattern should never be asked for in the wall
     * pipeline but must still answer &mdash; a ceiling has no doorways, and
     * {@code CeilingPatternSelector} would call this one.
     */
    @Override
    default SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        return plan(uSize, vSize, facing, Set.of(), random);
    }
}
