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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;

import java.util.ArrayList;
import java.util.List;
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
            // Each registered pattern builds its own provider; the switch over `type`
            // and the four builders it called moved onto the types themselves.
            ISurfacePatternProvider provider = pattern.pattern().provider();
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
}
