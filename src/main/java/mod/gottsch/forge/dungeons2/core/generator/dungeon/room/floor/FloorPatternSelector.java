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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps the {@link FloorPatternEntry} in a room scheme's floor slot to a concrete
 * {@link IDungeonFloorGenerator}. Kept separate from the config records themselves (which stay pure
 * data, same split {@code DungeonGenerationConfig} keeps from the planner) since only this package
 * needs to know what a {@code type} string actually builds.
 *
 * <p><strong>This does not roll.</strong> It used to own a weighted pick over
 * {@code FloorConfig.patterns}; that roll moved up to {@code RoomSchemeSelector} so that a room's
 * floor is chosen together with its walls and ceiling rather than independently of them (see
 * {@code RoomScheme}). What is left here is the part that was always floor-specific: turning one
 * chosen entry into the generator that renders it.</p>
 *
 * <p>Every plain-floor outcome &mdash; the {@code "empty"} type, an unrecognized type, an absent
 * floor slot, or a pattern whose blocks failed to resolve &mdash; returns a
 * {@link BasicFloorGenerator} carrying the same {@link FloorConfig}, so a room that renders plain
 * still uses the motif's own base blocks rather than reverting to the hardcoded stone_bricks
 * fallback.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026 (roll extracted to RoomSchemeSelector Jul 31, 2026)
 */
public final class FloorPatternSelector {

    private FloorPatternSelector() {}

    /**
     * The generator for a scheme's floor slot: the entry's own generator when present, plain floor
     * when the slot is absent (an undecorated floor is what "no floor treatment in this scheme"
     * means).
     */
    public static IDungeonFloorGenerator generatorFor(Optional<FloorPatternEntry> entry, FloorConfig config) {
        return entry.map(e -> toGenerator(e, config)).orElseGet(() -> plain(config));
    }

    /** The plain floor, carrying the motif's own base blocks. */
    public static IDungeonFloorGenerator plain(FloorConfig config) {
        return new BasicFloorGenerator().withFloorConfig(config);
    }

    /**
     * Maps a {@code type} to its generator. There is deliberately no Java-side default block for
     * any pattern's material slots (see {@code FloorBorderPatternProvider}/{@code
     * CheckerboardFloorPatternProvider}/{@code RandomSpeckleFloorPatternProvider}) &mdash; the
     * motif config is the single source of truth for which blocks a pattern renders, so if a
     * required slot fails to resolve (absent, malformed, or an unregistered id), the whole entry
     * degrades to plain floor rather than silently substituting a guessed block.
     */
    public static IDungeonFloorGenerator toGenerator(FloorPatternEntry entry, FloorConfig config) {
        return switch (entry.type().trim().toLowerCase(Locale.ROOT)) {
            case "border" -> {
                Block corner = resolveBlock(entry.cornerBlock());
                Block edgeLeft = resolveBlock(entry.edgeLeftBlock());
                Block edgeRight = resolveBlock(entry.edgeRightBlock());
                yield (corner == null || edgeLeft == null || edgeRight == null)
                        ? plain(config)
                        : new FloorBorderPatternProvider(entry.inset(), corner, edgeLeft, edgeRight,
                                config.baseState());
            }
            case "checkerboard" -> {
                Block primary = resolveBlock(entry.primaryBlock());
                Block secondary = resolveBlock(entry.secondaryBlock());
                yield (primary == null || secondary == null)
                        ? plain(config)
                        : new CheckerboardFloorPatternProvider(primary, secondary);
            }
            case "speckle" -> {
                Block base = resolveBlock(entry.primaryBlock());
                Block accent = resolveBlock(entry.secondaryBlock());
                yield (base == null || accent == null)
                        ? plain(config)
                        : new RandomSpeckleFloorPatternProvider(entry.probability(), base, accent);
            }
            case "cross" -> {
                Block accent = resolveBlock(entry.primaryBlock());
                yield accent == null ? plain(config)
                        : new CrossFloorPatternProvider(entry.thickness(), accent, config.baseState());
            }
            case "spokes" -> {
                Block accent = resolveBlock(entry.primaryBlock());
                yield accent == null ? plain(config)
                        : new RadialSpokesFloorPatternProvider(entry.spokes(), accent, config.baseState());
            }
            case "composite" -> toComposite(entry.generators(), config);
            default -> plain(config); // "empty" or unrecognized
        };
    }

    /**
     * The first nested entry becomes the base full fill; every entry after it is only kept if
     * its generator is overlay-capable ({@link IFloorOverlayGenerator}: {@code "border"},
     * {@code "cross"} and {@code "spokes"}) &mdash; anything else in an overlay slot is silently skipped, same graceful
     * degradation an unrecognized top-level {@code type} already gets. An empty {@code
     * generators} list degrades to plain floor, same as an empty top-level pattern list.
     */
    private static IDungeonFloorGenerator toComposite(List<FloorPatternEntry> generators, FloorConfig config) {
        if (generators.isEmpty()) {
            return plain(config);
        }
        IDungeonFloorGenerator base = toGenerator(generators.get(0), config);
        List<IFloorOverlayGenerator> overlays = new ArrayList<>();
        for (int i = 1; i < generators.size(); i++) {
            IDungeonFloorGenerator generator = toGenerator(generators.get(i), config);
            if (generator instanceof IFloorOverlayGenerator overlay) {
                overlays.add(overlay);
            }
        }
        return new CompositeFloorPatternProvider(base, overlays);
    }

    /**
     * Resolves an optional block-id string to a {@link Block}, or {@code null} when absent,
     * malformed, or not a registered block id. {@code null} here means "this slot didn't resolve"
     * -- the caller degrades the whole entry to plain floor, since there is no per-slot default to
     * fall back to (see {@link #toGenerator}).
     */
    private static Block resolveBlock(Optional<String> id) {
        return id.map(BlockStateCodec::blockOrNull).orElse(null);
    }
}
