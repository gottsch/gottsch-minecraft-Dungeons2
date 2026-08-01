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

import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps the {@link WallPatternEntry} in a room scheme's wall slot to a concrete
 * {@link ISurfacePatternProvider}. The wall counterpart of {@code FloorPatternSelector}, and like
 * it, <strong>this does not roll</strong> &mdash; the choice was already made once for the whole
 * room by {@code RoomSchemeSelector}.
 *
 * <p>Returns {@code null} for "no treatment", which {@code BasicWallGenerator} renders as the plain
 * motif wall. Null rather than a do-nothing provider so the common case allocates nothing per room.
 * </p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public final class WallPatternSelector {

    private WallPatternSelector() {}

    /** The provider for a scheme's wall slot, or {@code null} when the slot is absent. */
    public static ISurfacePatternProvider providerFor(Optional<WallPatternEntry> entry) {
        return entry.map(WallPatternSelector::toProvider).orElse(null);
    }

    /**
     * Maps a {@code type} to its provider. There is deliberately no Java-side default block, and a
     * course whose block fails to resolve degrades <strong>the whole entry</strong> to plain wall
     * rather than rendering the rest of the bands without it &mdash; the same
     * degrade-the-whole-entry rule the floor patterns follow, and for the same reason: a half-drawn
     * pattern reads as a bug, where a plain wall reads as a plain wall.
     */
    static ISurfacePatternProvider toProvider(WallPatternEntry entry) {
        return switch (entry.type().trim().toLowerCase(Locale.ROOT)) {
            case "courses" -> toCourses(entry);
            default -> null; // unrecognized type: plain wall
        };
    }

    private static ISurfacePatternProvider toCourses(WallPatternEntry entry) {
        if (entry.courses().isEmpty()) {
            return null;
        }
        List<CoursesWallPatternProvider.Course> courses = new ArrayList<>(entry.courses().size());
        for (WallPatternEntry.CourseEntry course : entry.courses()) {
            Block block = BlockStateCodec.blockOrNull(course.block());
            if (block == null) {
                return null;
            }
            courses.add(new CoursesWallPatternProvider.Course(
                    block.defaultBlockState(), course.anchor(), course.offset()));
        }
        return new CoursesWallPatternProvider(courses);
    }
}
