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
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * The provider for a scheme's wall slot in a room of these dimensions, or {@code null} when the
     * slot is absent, unusable, or every one of its courses gates out.
     *
     * <p>Course gates are applied here, by {@code WallPatternEntry#forRoom}, rather than inside the
     * provider: the provider is handed an extent and a facing, so it can infer the room's height
     * but not its footprint, and a course may be gated on either.</p>
     */
    public static ISurfacePatternProvider providerFor(Optional<WallPatternEntry> entry,
                                                      int width, int depth, int height) {
        return entry.map(wall -> wall.forRoom(width, depth, height))
                .map(WallPatternSelector::toProvider)
                .orElse(null);
    }

    /** Ungated form, for callers with no room in hand (tests, and any fully unconditional entry). */
    public static ISurfacePatternProvider providerFor(Optional<WallPatternEntry> entry) {
        return entry.map(WallPatternSelector::toProvider).orElse(null);
    }


    /**
     * Builds one provider per pattern and composes them in list order.
     *
     * <p>A pattern that will not resolve is <strong>dropped, and the rest still draw</strong>. That
     * is the one place this differs from the pre-list behaviour, and deliberately: the
     * degrade-the-whole-entry rule below is about a <em>single</em> treatment losing part of itself,
     * which reads as a bug. Two patterns in a list are two authored decisions, and taking the
     * pilasters away because a course names a typo'd block would hide which of them is broken.</p>
     *
     * <p>Every pattern dropping leaves nothing to draw, which is returned as null &mdash; the plain
     * wall, allocating nothing, exactly as an absent slot does.</p>
     */
    static ISurfacePatternProvider toProvider(WallPatternEntry entry) {
        List<ISurfacePatternProvider> providers = new ArrayList<>(entry.patterns().size());
        for (WallPatternEntry.PatternEntry pattern : entry.patterns()) {
            ISurfacePatternProvider provider = toPattern(pattern);
            if (provider != null) {
                providers.add(provider);
            }
        }
        if (providers.isEmpty()) {
            return null;
        }
        // One pattern is the overwhelmingly common case; handing it back unwrapped keeps the plans
        // it produces identical to the pre-list ones rather than routed through an overlay.
        return providers.size() == 1 ? providers.get(0) : new CompositeWallPatternProvider(providers);
    }

    /**
     * Maps a {@code type} to its provider. There is deliberately no Java-side default block, and a
     * course whose block fails to resolve degrades <strong>the whole pattern</strong> to plain wall
     * rather than rendering the rest of the bands without it &mdash; the same
     * degrade-the-whole-entry rule the floor patterns follow, and for the same reason: a half-drawn
     * pattern reads as a bug, where a plain wall reads as a plain wall.
     */
    private static ISurfacePatternProvider toPattern(WallPatternEntry.PatternEntry pattern) {
        return switch (pattern.type().trim().toLowerCase(Locale.ROOT)) {
            case WallPatternEntry.COURSES -> toCourses(pattern);
            case WallPatternEntry.PILASTERS ->
                    toPilasters(pattern, PilastersWallPatternProvider.Layout.EVEN);
            case WallPatternEntry.END_PILASTERS ->
                    toPilasters(pattern, PilastersWallPatternProvider.Layout.ENDS);
            case WallPatternEntry.PANELS -> toPanels(pattern);
            default -> null; // unrecognized type: plain wall
        };
    }

    /**
     * A rectangular field. Only {@code block} is used -- a panel's frame is drawn by listing
     * {@code courses} and {@code pilasters} around it, not by this type. See
     * {@link PanelsWallPatternProvider}.
     */
    private static ISurfacePatternProvider toPanels(WallPatternEntry.PatternEntry pattern) {
        BlockState field = pattern.block().map(id -> state(id, pattern.properties())).orElse(null);
        return field == null ? null : new PanelsWallPatternProvider(field, pattern.width(),
                pattern.spacing(), pattern.inset(), pattern.projection(), pattern.orient());
    }

    /**
     * A pilaster strip. {@code block} is required by the codec, so an empty Optional here can only
     * mean the id did not resolve, which drops the pattern like any other unresolvable block.
     */
    private static ISurfacePatternProvider toPilasters(WallPatternEntry.PatternEntry pattern,
                                                       PilastersWallPatternProvider.Layout layout) {
        // The three rows take their properties separately, unlike a course's three block slots.
        // A pilaster needs it: a plinth and a capital are typically the SAME block at opposite
        // values of a vertical property (dungeonblocks' pillar blocks use `base`, where `up` is the
        // unrotated model and `down` is it flipped), so one shared map cannot express a column.
        BlockState shaft = pattern.block().map(id -> state(id, pattern.properties())).orElse(null);
        BlockState base = pattern.baseBlockOrBase()
                .map(id -> state(id, pattern.basePropertiesOrBase())).orElse(null);
        BlockState cap = pattern.capBlockOrBase()
                .map(id -> state(id, pattern.capPropertiesOrBase())).orElse(null);
        if (shaft == null || base == null || cap == null) {
            return null;
        }
        return new PilastersWallPatternProvider(shaft, base, cap, pattern.spacing(),
                pattern.projection(), pattern.orient(), layout, pattern.inset());
    }

    private static ISurfacePatternProvider toCourses(WallPatternEntry.PatternEntry entry) {
        if (entry.courses().isEmpty()) {
            return null;
        }
        List<CoursesWallPatternProvider.Course> courses = new ArrayList<>(entry.courses().size());
        for (WallPatternEntry.CourseEntry course : entry.courses()) {
            // Author-supplied properties (half=top for an upside-down cornice, and anything else
            // the block needs) are baked in here; `orient` is applied later, per wall run, since it
            // is the one property that differs between the four walls.
            BlockState block = state(course.block(), course.properties());
            // An unresolvable alternate/corner degrades the whole entry too, not just its own
            // cells: a band that silently loses its quoins is a subtler wrong than a plain wall.
            BlockState alternate = state(course.alternateBlockOrBase(), course.properties());
            BlockState corner = state(course.cornerBlockOrBase(), course.properties());
            if (block == null || alternate == null || corner == null) {
                return null;
            }
            courses.add(new CoursesWallPatternProvider.Course(block, alternate, corner,
                    course.anchor(), course.offset(), course.projection(), course.orient(),
                    course.alternate()));
        }
        return new CoursesWallPatternProvider(courses);
    }

    /** The default state of {@code id} with {@code properties} applied, or null if it won't resolve. */
    private static BlockState state(String id, Map<String, String> properties) {
        Block block = BlockStateCodec.blockOrNull(id);
        return block == null ? null : BlockStateCodec.withProperties(block.defaultBlockState(), properties);
    }
}
