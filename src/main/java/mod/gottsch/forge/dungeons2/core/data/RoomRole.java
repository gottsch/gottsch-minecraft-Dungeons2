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
 *         downstairs {@link TransitionData} template.</li>
 *     <li>{@link #TERMINAL} &mdash; the bottom floor's final room, where the dungeon
 *         stops. Nothing covers it, so it is built procedurally.</li>
 * </ul>
 *
 * <p>The piece emitter in the Forge shell skips rooms marked {@code START} or
 * {@code END} when rendering normal room pieces &mdash; the template piece covers
 * those slots so they're not double-built. {@code NORMAL} and {@code TERMINAL} are
 * rendered.</p>
 *
 * <h2>Why {@code TERMINAL} is not just {@code END}</h2>
 * <p>It was, and it left a hole in every dungeon. {@code END} means "a transition
 * occupies this slot", and the bottom floor has no downstairs transition &mdash; this
 * enum's own javadoc used to say so in parentheses and nothing acted on it. The maze
 * still reserved the footprint and still routed a door into it, so the result was a
 * door opening into unbuilt terrain: invisible where that terrain was solid stone,
 * and a hole into a cave at bottom-floor depth, which is where it was found.</p>
 *
 * <p>So the distinction is real rather than cosmetic: {@code END} is a slot somebody
 * else fills, {@code TERMINAL} is a room this mod builds. Encoding that as a role
 * makes the emitter's rule state itself instead of a floor-index check sitting in the
 * emitter, and makes "which slots are covered by a template" an exhaustive question a
 * test can ask &mdash; see {@code SlotCoverageTest}.</p>
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
    END,
    TERMINAL;

    /**
     * Whether a room in this role is built by this mod's own procedural builders, as opposed to
     * being covered by an assembled template piece.
     *
     * <p>Lives here rather than in the emitter so the emitter and
     * {@code DungeonLayoutRenderer} cannot drift apart &mdash; they are required to stay in step,
     * and they were previously two copies of the same {@code != NORMAL} test.</p>
     */
    public boolean isProcedurallyBuilt() {
        return this == NORMAL || this == TERMINAL;
    }
}
