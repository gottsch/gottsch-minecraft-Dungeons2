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

import mod.gottsch.forge.dungeons2.core.config.WallConfig;
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
        return providerFor(entry, WallConfig.DEFAULT, width, depth, height);
    }

    /**
     * As above, resolved against the motif-or-stratum default underneath it. The two tiers
     * <strong>COMPOSE</strong>: the {@link WallConfig}'s own {@code pattern} draws first and the
     * scheme's own {@code wall} entry draws on top of it, as though the band's patterns were the
     * opening entries of the scheme's own list. Neither tier, plain wall ({@code null}).
     *
     * <h2>Why walls compose where the floor and ceiling replace</h2>
     * <p>Both of the others are first-match-wins, and that asymmetry is deliberate rather than an
     * oversight (Gottsch, 2026-08-27). <strong>A wall is a stack of horizontal bands at different
     * anchors</strong> &mdash; plinth, field, cornice &mdash; so two tiers naturally occupy
     * different rows and read as one wall. A floor or a ceiling is a single surface, so two
     * treatments fight over the same cells and one of them simply loses.</p>
     *
     * <p>The evidence was a shipped band that drew <strong>nowhere</strong>. A band pattern is
     * only reached when the rolled scheme names no slot of its own, and ten of classic's eleven
     * schemes name {@code wall} (the eleventh inherits one) &mdash; so a mud band authored with a
     * stone plinth produced it in 0% of rooms, while the same band's ceiling, whose schemes mostly
     * say nothing, drew in 55.9%. The wall tier was dead weight in exactly the case a band exists
     * for: something true of the whole depth.</p>
     *
     * <p>The alternative was for every scheme touching a wall to restate the band's plinth as the
     * first entry of its own list. That is the {@code N+1} authoring duplication a band exists to
     * remove, and forgetting it fails <em>silently</em> &mdash; the room simply loses its plinth.
     * Composition means a scheme's {@code wall} slot says what is DIFFERENT about that room, which
     * is what a scheme slot means everywhere else.</p>
     *
     * <h2>Order, and who wins a shared cell</h2>
     * <p>Band entries first, then the scheme's, and {@code CompositeWallPatternProvider} overlays
     * in list order &mdash; so where the two do land on the same cell, <strong>the scheme
     * wins</strong>. A room that asked for a crown at the top row gets its crown even if the band
     * authored one there too.</p>
     *
     * <p><strong>Each tier is gated on its own.</strong> The band's entry is size-gated here the
     * way {@code RoomScheme#wallFor} gates the scheme's before it ever arrives, and then each
     * tier's per-pattern gates are applied by {@code forRoom}. Before composition the band's
     * entry-level gate was never consulted at all &mdash; {@code forRoom} filters the patterns
     * inside an entry, not the entry &mdash; so a band pattern with a {@code minHeight} on the
     * entry drew in rooms below it. Nothing shipped authored one, which is why it went unseen.</p>
     */
    public static ISurfacePatternProvider providerFor(Optional<WallPatternEntry> entry,
                                                      WallConfig config,
                                                      int width, int depth, int height) {
        List<WallPatternEntry.PatternEntry> composed = new ArrayList<>();
        config.pattern()
                .filter(band -> band.gate().fits(width, depth, height))
                .map(band -> band.forRoom(width, depth, height))
                .ifPresent(band -> composed.addAll(band.patterns()));
        entry.map(own -> own.forRoom(width, depth, height))
                .ifPresent(own -> composed.addAll(own.patterns()));
        if (composed.isEmpty()) {
            return null;
        }
        // Handed back through the ordinary list path, so one surviving pattern is still returned
        // unwrapped and draws byte-identically to how it drew before this method composed anything.
        return toProvider(new WallPatternEntry(composed));
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
