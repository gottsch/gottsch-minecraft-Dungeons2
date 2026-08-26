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

import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.PlatformPatternEntry.PlatformEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maps a scheme's {@code platforms} slot to the daises to draw.
 *
 * <p><strong>The layout vocabulary MIRRORS the {@code pillars} slot without sharing its
 * registry.</strong> A layout provider answers only "which interior cells", which is the same
 * question whether the answer carries a column or a platform, so {@code centre}, {@code corners},
 * {@code grid}, {@code quartet} and {@code colonnade} exist in both and build the same provider
 * classes. They are registered <em>separately</em> though ({@code PlatformLayoutRegistry} against
 * {@code PillarLayoutRegistry}), because the two slots author the same footprint from different
 * words: a pillar layout takes {@code spacing}, a platform takes the dais's {@code size} and
 * derives spacing from it. One registry would have to force one vocabulary on both. So a
 * third-party layout does NOT work in both slots for free -- it registers to each it wants to
 * serve, having decided what its config words mean there.</p>
 *
 * <p>Splitting <em>where</em> from <em>what</em> is still what makes "a brazier on a central
 * platform" and "a brazier in every corner" one feature rather than two; that is the {@code type}
 * / {@code layout} split, and it is untouched.</p>
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
            // The dais block has to resolve; stair/centre/top all fall back to it or are optional,
            // so a typo in those degrades rather than dropping the platform.
            //
            // The `isDais` half of this check is gone: a non-dais `type` is now a LOAD ERROR in
            // PlatformPatternEntry.validate, and an unregistered `layout` cannot decode at all, so
            // neither can reach here. What is left is the one condition that is genuinely a
            // runtime question.
            if (BlockStateCodec.blockOrNull(pattern.block()) != null) {
                layouts.add(new PlatformLayout(pattern.layout().provider(pattern.size()), pattern));
            }
        }
        return layouts;
    }
}
