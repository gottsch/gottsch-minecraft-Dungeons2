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
package mod.gottsch.forge.dungeons2.core.config;

import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static lookup helper for the {@link MotifConfigRegistries#MOTIF_CONFIG} datapack registry, and
 * the place a motif's folder of files becomes one {@link MotifConfig}.
 *
 * <p>Motif-scoped, same convention as {@code rooms/<motif>/normal.json} and the weathering
 * processor lists ({@code PieceProcessors#weatheringList}). A motif with no files (or no registry,
 * or a blank motif) degrades to {@link MotifConfig#DEFAULT} so callers never deal with null.</p>
 *
 * <p>Callers resolve once where {@code RegistryAccess} is available and inject the resolved value
 * into the generator &mdash; same shape as {@code DungeonStackPlanner#withCorridorWidth}. See
 * {@code DungeonRoomPiece#postProcess}.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public class MotifConfigHelper {

    private MotifConfigHelper() {}

    public static MotifConfig get(RegistryAccess registryAccess, String motifValue) {
        if (motifValue == null || motifValue.isBlank()) {
            return MotifConfig.DEFAULT;
        }
        String motif = motifValue.trim().toLowerCase(Locale.ROOT);
        return registryAccess.registry(MotifConfigRegistries.MOTIF_CONFIG)
                .map(registry -> resolve(registry, motif))
                .orElse(MotifConfig.DEFAULT);
    }

    /**
     * Every fragment belonging to {@code motif}, folded in id order.
     *
     * <h2>Matched on path, not on the full id</h2>
     * <p>An entry's namespace is the namespace of the <em>pack</em> that shipped the file, so
     * matching {@code dungeons2:classic/...} exactly would mean only this mod could ever contribute
     * to classic. Matching the path lets a datapack drop
     * {@code data/<its ns>/dungeons2/motif_config/classic/more_schemes.json} in and have it land in
     * classic, which is the whole point of splitting a motif across files.</p>
     *
     * <p>Sorted by the full id string so the fold is a function of what is installed and not of
     * registry iteration order. Two useful consequences fall out of plain lexicographic order: the
     * flat {@code <motif>.json} sorts before everything in {@code <motif>/} (it is a prefix of
     * them), so it reads as the base layer; and a foreign namespace sorts on its own name, so
     * whether an addon wins against {@code dungeons2:} is at least stable and inspectable rather
     * than arbitrary.</p>
     */
    static MotifConfig resolve(Registry<MotifConfigFragment> registry, String motif) {
        String folder = motif + "/";
        List<MotifConfigFragment> fragments = registry.entrySet().stream()
                .filter(entry -> {
                    String path = entry.getKey().location().getPath();
                    return path.equals(motif) || path.startsWith(folder);
                })
                .sorted(Comparator.comparing(entry -> entry.getKey().location().toString()))
                .map(Map.Entry::getValue)
                .toList();

        return fragments.isEmpty()
                ? MotifConfig.DEFAULT
                : MotifConfigFragment.resolve(fragments, problem -> report(motif, problem));
    }

    /**
     * Schemes already reported, so a broken pack says its piece once instead of every chunk.
     *
     * <h2>Why a static set rather than logging at the call site</h2>
     * <p>{@link #resolve} runs <strong>once per piece per chunk</strong> during worldgen, and it is
     * the only place a cross-file scheme fault can be detected at all (see
     * {@code MotifConfigFragment#inherit}). Logged plainly, one typo in one datapack would put
     * thousands of identical lines in the log of every world that generates a dungeon &mdash; which
     * is a good way to make an error message worth ignoring.</p>
     *
     * <p>Bounded by the pack's own content: one entry per motif and scheme name, added only when
     * something is already wrong. Not cleared on reload, deliberately &mdash; re-reporting a fault
     * the author has not fixed yet buys nothing, and a fixed one stops being reported because it
     * stops being detected.</p>
     */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static void report(String motif, String problem) {
        if (REPORTED.add(motif + ": " + problem)) {
            LOGGER.error("motif '{}': {}", motif, problem);
        }
    }

    // Dungeons.MOD_ID is a compile-time String constant, so this inlines and does not load the @Mod
    // class -- which matters, since this helper is exercised under a bare Bootstrap in tests.
    private static final Logger LOGGER = LogManager.getLogger(Dungeons.MOD_ID);
}
