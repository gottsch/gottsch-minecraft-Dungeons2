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

/**
 * Draws a pattern into a surface's own {@code (u, v)} space.
 *
 * <p>Implementations see no room, no world and no registry &mdash; only an extent, a facing, and
 * the block states they were constructed with. That is what keeps them unit testable without a
 * running Forge instance, the same reason the floor providers keep a registry-free {@code plan}.
 * </p>
 *
 * <p>A {@link RandomSource} is <em>not</em> a hole in that: it is the same channel
 * {@code IDungeonFloorGenerator#build} already takes, and a pattern that mixes two blocks (a
 * course's {@code block}/{@code alternateBlock} pair, exactly as {@code BasicFloorGenerator} mixes
 * {@code base}/{@code alternateBase}) cannot be a pure function of {@code (u, v)} without every
 * room in the dungeon coming out identically speckled. Most patterns ignore it; the
 * {@link #plan(int, int, Direction) three-argument form} exists so those &mdash; and tests of them
 * &mdash; never have to invent one.</p>
 *
 * <p>Because {@link SurfacePlan} is sparse, there is no build/overlay distinction to implement:
 * leave a cell null and the caller renders the surface's base block there. Two providers compose by
 * {@link SurfacePlan#overlay}, later non-null winning &mdash; which is why a "composite" pattern
 * type is unlikely ever to be needed here. When a second wall pattern type lands, the natural shape
 * is an ordered list of entries applied in sequence, not a nested wrapper.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public interface ISurfacePatternProvider {

    /**
     * @param uSize length along the surface
     * @param vSize height up the surface. For a wall this is {@code roomHeight - 2}, i.e. only
     *              <strong>3 to 8</strong> rows &mdash; a pattern that needs more must be gated out
     *              by its scheme's {@code minHeight} rather than degrade inside here.
     * @param facing which way the surface's decorated face points, for patterns that place
     *               directional blocks. Ignored by patterns built from full cubes.
     * @param random the room's random. Ignored by patterns whose output is a pure function of
     *               {@code (u, v)}, which is most of them.
     */
    SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random);

    /**
     * The deterministic form, for callers with no random to hand &mdash; unit tests, and any
     * pattern known to be pure geometry. The fixed seed makes a randomized pattern repeatable here
     * rather than absent, so this stays a convenience and never a second code path.
     */
    default SurfacePlan plan(int uSize, int vSize, Direction facing) {
        return plan(uSize, vSize, facing, RandomSource.create(0L));
    }
}
