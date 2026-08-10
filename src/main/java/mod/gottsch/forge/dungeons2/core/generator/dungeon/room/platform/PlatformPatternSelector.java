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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform;

import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry.PlatformEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.CentrePillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.ColonnadePillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.CornersPillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.GridPillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.IPillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.QuartetPillarPatternProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps a scheme's {@code platforms} slot to the daises to draw.
 *
 * <p><strong>The layout vocabulary is shared with the {@code pillars} slot.</strong> A layout
 * provider answers only "which interior cells", which is the same question whether the answer
 * carries a column or a platform &mdash; so {@code centre}, {@code corners}, {@code grid},
 * {@code quartet} and {@code colonnade} all work here, and any layout added later works in both
 * slots for free. Splitting <em>where</em> from <em>what</em> is what makes "a brazier on a central
 * platform" and "a brazier in every corner" one feature rather than two.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public final class PlatformPatternSelector {

    private PlatformPatternSelector() {}

    /** Centre of the room -- the default, and what a lone dais almost always wants. */
    public static final String CENTRE = "centre";

    /** One in each corner of the interior. */
    public static final String CORNERS = "corners";

    public static List<PlatformLayout> layoutsFor(Optional<PlatformPatternEntry> entry,
                                                  int width, int depth, int height) {
        return entry.map(platforms -> platforms.forRoom(width, depth, height))
                .map(PlatformPatternSelector::toLayouts)
                .orElseGet(List::of);
    }

    /** Ungated form, for callers with no room in hand. */
    public static List<PlatformLayout> layoutsFor(Optional<PlatformPatternEntry> entry) {
        return entry.map(PlatformPatternSelector::toLayouts).orElseGet(List::of);
    }

    static List<PlatformLayout> toLayouts(PlatformPatternEntry entry) {
        List<PlatformLayout> layouts = new ArrayList<>(entry.patterns().size());
        for (PlatformEntry pattern : entry.patterns()) {
            IPillarPatternProvider provider = toProvider(pattern);
            if (provider != null) {
                layouts.add(new PlatformLayout(provider, pattern));
            }
        }
        return layouts;
    }

    private static IPillarPatternProvider toProvider(PlatformEntry pattern) {
        // The dais block has to resolve; stair/centre/top all fall back to it or are optional, so a
        // typo in those degrades rather than dropping the platform.
        if (!pattern.isDais() || BlockStateCodec.blockOrNull(pattern.block()) == null) {
            return null;
        }
        return switch (pattern.layout().trim().toLowerCase(Locale.ROOT)) {
            case CENTRE -> new CentrePillarPatternProvider(pattern.inset());
            case CORNERS -> new CornersPillarPatternProvider(pattern.inset());
            case PillarPatternEntry.GRID ->
                    new GridPillarPatternProvider(Math.max(2, pattern.size() + 1), pattern.inset());
            case PillarPatternEntry.QUARTET ->
                    new QuartetPillarPatternProvider(Math.max(2, pattern.size() + 1), pattern.inset());
            case PillarPatternEntry.COLONNADE ->
                    new ColonnadePillarPatternProvider(Math.max(2, pattern.size() + 1), pattern.inset());
            default -> null; // unrecognized layout: skipped
        };
    }
}
