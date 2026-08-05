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
package mod.gottsch.forge.dungeons2.core.data;

/**
 * One entry in the per-floor corridor style roll, as the planner sees it: just enough of a
 * {@code CorridorStyle} to pick one and stamp the result.
 *
 * <p>This exists because {@code DungeonStackPlanner} has <strong>zero</strong> {@code net.minecraft}
 * imports and handing it the real {@code CorridorStyle} would end that. The planner does not need
 * the blocks &mdash; it needs a name to record and a height to size bounding boxes with, and the
 * generator re-resolves everything else from the datapack at render time. Same
 * "resolve where RegistryAccess is available, inject the value" shape as
 * {@code DungeonStackPlanner#withCorridorWidth}, one step wider.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on Aug 04, 2026
 */
public record CorridorStyleWeight(String name, int weight, int height) {
}
