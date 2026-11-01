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

import java.util.Map;

/**
 * Implemented by an {@link ISurfacePatternProvider} that also places blocks <em>in front of</em> its
 * surface &mdash; trim that stands out into the room rather than sitting flush in the wall.
 *
 * <p>An optional second capability rather than part of {@link ISurfacePatternProvider}, the same
 * shape {@code IFloorOverlayGenerator} takes for the floor: most patterns are flat, and a flat
 * pattern should not have to implement a method it has nothing to say about.</p>
 *
 * <p>Projected layers are <strong>sparse</strong> in a way the surface plan is not. A wall plan's
 * null cell means "use the wall block", because every cell of a wall is something. A projected
 * layer's null cell means "leave this alone" &mdash; that layer is the room's open air, and filling
 * it would wall the room in. {@link WallSurface#emitProjected} enforces that.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public interface IProjectingPatternProvider {

    /**
     * Plans for the layers standing out from the surface, keyed by depth in cells (1 = the layer
     * immediately in front). An empty map means nothing projects.
     *
     * <p>{@code random} is the room's, same contract as
     * {@link ISurfacePatternProvider#plan(int, int, Direction, RandomSource)}.</p>
     */
    Map<Integer, SurfacePlan> projectedPlans(int uSize, int vSize, Direction facing, RandomSource random);

    /** The deterministic form. See {@link ISurfacePatternProvider#plan(int, int, Direction)}. */
    default Map<Integer, SurfacePlan> projectedPlans(int uSize, int vSize, Direction facing) {
        return projectedPlans(uSize, vSize, facing, RandomSource.create(0L));
    }
}
