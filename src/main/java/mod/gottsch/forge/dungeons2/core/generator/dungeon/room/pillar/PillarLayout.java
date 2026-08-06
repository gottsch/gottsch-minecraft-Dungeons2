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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar;

import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry.PillarEntry;

/**
 * One layout paired with the materials it draws in.
 *
 * <p>The surface patterns bake their blocks into the provider, because a {@code SurfacePlan} carries
 * {@code BlockState} per cell. A pillar layout is a bare footprint (see
 * {@link IPillarPatternProvider}), so the blocks have to travel alongside it &mdash; and keeping
 * them on the authored {@link PillarEntry} rather than copying them into the provider means the
 * per-row defaulting ({@code baseBlock} falling back to {@code block}, and so on) stays in the one
 * place that defines it.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public record PillarLayout(IPillarPatternProvider provider, PillarEntry entry) {}
