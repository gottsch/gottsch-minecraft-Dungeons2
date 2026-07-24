/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
 * Role of a {@link RoomData} within its floor.
 *
 * <ul>
 *     <li>{@link #NORMAL} &mdash; a regular maze room with no template attachment.</li>
 *     <li>{@link #START} &mdash; the upstairs anchor. On floor 0 this slot is occupied by
 *         the {@link EntranceData} template; on lower floors it's occupied by the
 *         upstairs {@link TransitionData} template.</li>
 *     <li>{@link #END} &mdash; the downstairs anchor. Occupied by this floor's
 *         downstairs {@link TransitionData} template (absent on the bottom floor).</li>
 * </ul>
 *
 * <p>The piece emitter in the Forge shell skips rooms marked {@code START} or
 * {@code END} when rendering normal room pieces &mdash; the template piece covers
 * those slots so they're not double-built.</p>
 *
 * <p>Named {@code RoomRole} rather than {@code RoomType} to avoid collision with
 * {@code Room2D.RoomType} (ROOM/JOINER/...) which describes a different concept
 * inside the maze planner.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public enum RoomRole {
    NORMAL,
    START,
    END
}
