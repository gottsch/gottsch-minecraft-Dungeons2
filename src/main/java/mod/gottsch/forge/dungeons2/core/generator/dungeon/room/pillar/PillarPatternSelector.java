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

import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.PillarPatternEntry.PillarEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps the {@link PillarPatternEntry} in a room scheme's {@code pillars} slot to the concrete
 * layouts to draw. Like its floor, wall and ceiling counterparts, <strong>this does not
 * roll</strong> &mdash; the choice was made once for the whole room by {@code RoomSchemeSelector}.
 *
 * <p>An entry whose {@code type} is unrecognized, or whose {@code block} will not resolve, is
 * <strong>dropped while the rest of the list still draws</strong> &mdash; the ceiling's rule rather
 * than the wall {@code courses} rule. Two layouts in a list are two authored decisions, and taking
 * the colonnade away because the quartet names a typo'd block would hide which of them is broken.</p>
 *
 * @author Mark Gottschling on Aug 6, 2026
 */
public final class PillarPatternSelector {

    private PillarPatternSelector() {}

    /**
     * The layouts for a scheme's pillars slot in a room of these dimensions, or an empty list when
     * there is nothing to draw. Patterns failing their own size gate are dropped first.
     */
    public static List<PillarLayout> layoutsFor(Optional<PillarPatternEntry> entry,
                                                int width, int depth, int height) {
        return entry.map(pillars -> pillars.forRoom(width, depth, height))
                .map(PillarPatternSelector::toLayouts)
                .orElseGet(List::of);
    }

    /** Ungated form, for callers with no room in hand (tests, and any unconditional entry). */
    public static List<PillarLayout> layoutsFor(Optional<PillarPatternEntry> entry) {
        return entry.map(PillarPatternSelector::toLayouts).orElseGet(List::of);
    }

    static List<PillarLayout> toLayouts(PillarPatternEntry entry) {
        List<PillarLayout> layouts = new ArrayList<>(entry.patterns().size());
        for (PillarEntry pattern : entry.patterns()) {
            IPillarPatternProvider provider = toProvider(pattern);
            if (provider != null) {
                layouts.add(new PillarLayout(provider, pattern));
            }
        }
        return layouts;
    }

    private static IPillarPatternProvider toProvider(PillarEntry pattern) {
        // The shaft has to resolve for the column to mean anything. Base and cap fall back to it, so
        // a typo in either degrades to a uniform column rather than dropping the layout -- the same
        // "absent means another authored value" rule the wall strips follow.
        if (BlockStateCodec.blockOrNull(pattern.block()) == null) {
            return null;
        }
        return switch (pattern.type().trim().toLowerCase(Locale.ROOT)) {
            case PillarPatternEntry.GRID ->
                    new GridPillarPatternProvider(pattern.spacing(), pattern.inset());
            case PillarPatternEntry.COLONNADE ->
                    new ColonnadePillarPatternProvider(pattern.spacing(), pattern.inset());
            case PillarPatternEntry.QUARTET ->
                    new QuartetPillarPatternProvider(pattern.spacing(), pattern.inset());
            default -> null; // unrecognized type: skipped
        };
    }
}
