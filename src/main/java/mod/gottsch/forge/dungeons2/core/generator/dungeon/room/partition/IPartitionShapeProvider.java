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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition;

import net.minecraft.util.RandomSource;

/**
 * A partition provider: where the interior wall runs, and where the way through it is. Backlog #74.
 *
 * <p>The same split the pit and pillar slots make. <strong>The shape says WHERE; the entry says HOW
 * TALL and IN WHAT.</strong> Height and material are identical for every cell of a partition, so
 * putting them in the shape would force each new shape to re-declare them and to get them right; a
 * shape is a pure 2D question with nothing to get wrong about blocks.</p>
 *
 * <p>Coordinates are <strong>interior-local</strong> &mdash; see {@link PartitionPlan}.</p>
 *
 * <h2>A provider must leave a way through</h2>
 * <p>Not enforced here, and it cannot be: a shape returning no gap is a legal plan, and there is a
 * legitimate use for one (a sealed vault seen through bars). But it is the failure mode to think
 * about first when writing a shape, because a room whose only interesting content is behind an
 * unbroken wall is indistinguishable, from the corridor, from a room with nothing in it. Both
 * shipped shapes always cut one.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public interface IPartitionShapeProvider {

    /**
     * The plan for a room of this interior size. {@link PartitionPlan#EMPTY} is legal and means no
     * partition &mdash; a shape too big for the room says so this way rather than by throwing, the
     * same degrade-don't-abort convention every other slot follows.
     *
     * @param interiorWidth interior extent along X ({@code room width - 2})
     * @param interiorDepth interior extent along Z ({@code room depth - 2})
     * @param random        the room's own source, for a shape that picks a corner or an axis
     */
    PartitionPlan plan(int interiorWidth, int interiorDepth, RandomSource random);
}
