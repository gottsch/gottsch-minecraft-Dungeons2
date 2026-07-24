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
package mod.gottsch.forge.dungeons2.core.decorator;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.decorator.data.SubstitutionDefinition;
import mod.gottsch.forge.dungeons2.core.decorator.data.SubstitutionRule;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Position-seeded "aging" pass for <em>procedurally</em> generated blocks: walks a
 * builder's {@link BlockPlacement} list and swaps a deterministic fraction of
 * eligible blocks for weathered variants (cracked / mossy / rubble), per a
 * per-motif {@link Rule} table.
 *
 * <p>This is the procedural-side analogue of a vanilla {@code StructureProcessor}.
 * Vanilla processors run only inside {@code StructureTemplate.placeInWorld}, so they
 * never see our procedural room / corridor / door pieces (those write blocks directly
 * via {@link mod.gottsch.forge.dungeons2.core.world.structure.DungeonPiece#placeAll}).
 * This class fills that gap without touching the template path.</p>
 *
 * <h2>Chunk-safety / determinism (the whole point)</h2>
 * <p>A procedural piece re-runs its builder and clips to the chunk box <em>once per
 * chunk it overlaps</em>. A block in the overlap of two chunks is therefore visited
 * twice, in two different generation passes &mdash; it MUST resolve to the same state
 * both times. So the swap decision is a pure function of the block's
 * <strong>absolute world position</strong> (and the source block id), via a SplitMix
 * positional hash. It never consumes a sequential / chunk-seeded {@code RandomSource},
 * which would desync across the seam.</p>
 *
 * <p>Because the dungeon's world anchor already derives from {@code chunkPos ^ worldSeed},
 * keying on absolute position also makes degradation vary naturally between world seeds
 * and between dungeons &mdash; no extra salt needed.</p>
 *
 * <p>Pure POJO &mdash; operates on {@link BlockPlacement} strings only, no Minecraft
 * imports, fully unit-testable.</p>
 *
 * @author Mark Gottschling on Jun 19, 2026
 */
public final class BlockSubstitutor {

    private BlockSubstitutor() {}

    /** A weathering rule for one source block: roll {@code chance}, then weighted-pick a variant. */
    public static final class Rule {
        final String from;
        final double chance;
        final List<String> variants;
        final int[] weights;
        final int totalWeight;

        Rule(String from, double chance, String[] variants, int[] weights) {
            this.from = from;
            this.chance = chance;
            this.variants = List.of(variants);
            this.weights = weights.clone();
            int sum = 0;
            for (int w : weights) {
                sum += w;
            }
            this.totalWeight = sum;
        }
    }

    /** Rules that apply to every motif (config entries with no {@code motif} field). */
    private static final String WILDCARD = "*";
    /** Probability used for a config entry that omits {@code chance}. */
    private static final double DEFAULT_CONFIG_CHANCE = 0.30;

    /** motif value (lower-case, or {@link #WILDCARD}) -> source block id -> rule. */
    private static volatile Map<String, Map<String, Rule>> tables = buildDefaultTables();

    /** The reserved {@code substitution/} file name whose rules apply to every motif. */
    private static final String GLOBAL_MOTIF_FILE = "global";

    /**
     * Datapack-JSON entry point: rebuilds the rule tables from per-motif
     * {@link SubstitutionDefinition}s (one per {@code data/<ns>/substitution/<motif>.json}).
     * The map key is the file name = motif; the reserved name {@value #GLOBAL_MOTIF_FILE}
     * becomes the {@link #WILDCARD} table that applies to every motif. Passing
     * {@code null}/empty resets to the built-in defaults (so a {@code /reload} that drops
     * every file still applies sane defaults).
     */
    public static void configureFromDefinitions(Map<String, SubstitutionDefinition> byMotif) {
        if (byMotif == null || byMotif.isEmpty()) {
            tables = buildDefaultTables();
            return;
        }
        Map<String, Map<String, Rule>> built = new HashMap<>();
        for (Map.Entry<String, SubstitutionDefinition> entry : byMotif.entrySet()) {
            String motif = entry.getKey().equalsIgnoreCase(GLOBAL_MOTIF_FILE)
                    ? WILDCARD : entry.getKey().trim().toLowerCase(Locale.ROOT);
            for (SubstitutionRule ruleDef : entry.getValue().rules()) {
                if (ruleDef.from() == null || ruleDef.to() == null || ruleDef.to().isEmpty()) {
                    continue;
                }
                double chance = ruleDef.chance().orElse(DEFAULT_CONFIG_CHANCE);
                String[] variants = ruleDef.to().stream()
                        .map(ResourceLocation::toString).toArray(String[]::new);
                int[] weights = resolveWeights(ruleDef.weights().orElse(null), variants.length);
                Rule rule = new Rule(normalize(ruleDef.from().toString()), chance, variants, weights);
                built.computeIfAbsent(motif, k -> new HashMap<>()).put(rule.from, rule);
            }
        }
        if (!built.isEmpty()) {
            tables = built;
        }
    }

    private static int[] resolveWeights(List<Integer> configured, int variantCount) {
        int[] weights = new int[variantCount];
        for (int i = 0; i < variantCount; i++) {
            Integer w = (configured != null && i < configured.size()) ? configured.get(i) : null;
            weights[i] = (w != null && w > 0) ? w : 1;
        }
        return weights;
    }

    /**
     * Degrades {@code placements} in place. No-op when the motif has no rule for a
     * placement's block. Placements carrying block-entity data (chests / spawners)
     * are always left alone.
     *
     * @param anchorX/anchorZ dungeon world anchor &mdash; added to each placement's
     *                        floor-local XZ to get the absolute world position the
     *                        hash is keyed on. ({@link BlockPlacement#getY()} is
     *                        already absolute.)
     */
    public static void substitute(List<BlockPlacement> placements, String motifValue,
                                  int anchorX, int anchorZ) {
        if (placements == null || placements.isEmpty()) {
            return;
        }
        Map<String, Map<String, Rule>> snapshot = tables;
        Map<String, Rule> motifTable = motifValue == null ? null
                : snapshot.get(motifValue.trim().toLowerCase(Locale.ROOT));
        Map<String, Rule> wildcardTable = snapshot.get(WILDCARD);
        if (motifTable == null && wildcardTable == null) {
            return;
        }
        for (BlockPlacement p : placements) {
            if (p.getBlockEntityNbt() != null) {
                continue;
            }
            Rule rule = lookup(motifTable, wildcardTable, normalize(p.getBlockId()));
            if (rule == null) {
                continue;
            }
            applyRule(rule, p, anchorX + p.getX(), p.getY(), anchorZ + p.getZ());
        }
    }

    /** Motif-specific rules win over wildcard rules for the same source block. */
    private static Rule lookup(Map<String, Rule> motifTable, Map<String, Rule> wildcardTable, String id) {
        if (motifTable != null) {
            Rule rule = motifTable.get(id);
            if (rule != null) {
                return rule;
            }
        }
        return wildcardTable == null ? null : wildcardTable.get(id);
    }

    private static void applyRule(Rule rule, BlockPlacement p, long wx, long wy, long wz) {
        long h = hash(wx, wy, wz);
        if (unit(h) >= rule.chance) {
            return;
        }
        // Decorrelate the variant pick from the chance roll (and per source block).
        long h2 = mix(h ^ ((long) rule.from.hashCode() * 0x9E3779B97F4A7C15L));
        String variant = pick(rule, h2);
        p.setBlockId(variant);
        // Variants are plain full cubes; drop any source properties (e.g. none on
        // stone bricks, but be defensive) so resolve() uses the variant's default state.
        p.getProperties().clear();
    }

    private static String pick(Rule rule, long h) {
        int r = (int) ((h >>> 1) % rule.totalWeight);
        for (int i = 0; i < rule.weights.length; i++) {
            r -= rule.weights[i];
            if (r < 0) {
                return rule.variants.get(i);
            }
        }
        return rule.variants.get(rule.variants.size() - 1);
    }

    private static String normalize(String blockId) {
        return blockId == null ? "" : blockId.trim().toLowerCase(Locale.ROOT);
    }

    // -------- positional hash (SplitMix64) --------

    private static long hash(long x, long y, long z) {
        long h = mix(0x243F6A8885A308D3L ^ (x * 0x9E3779B97F4A7C15L));
        h = mix(h ^ (y * 0xC2B2AE3D27D4EB4FL));
        h = mix(h ^ (z * 0x165667B19E3779F9L));
        return h;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Maps a hash to a double in [0, 1). */
    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    // -------- default rule tables --------

    private static Map<String, Map<String, Rule>> buildDefaultTables() {
        Map<String, Map<String, Rule>> tables = new HashMap<>();

        // CLASSIC: stone-brick dungeon. ~30% of stone bricks weather; floor/structural
        // stone occasionally crumbles to cobble.
        List<Rule> classic = new ArrayList<>();
        classic.add(new Rule("minecraft:stone_bricks", 0.30,
                new String[]{
                        "minecraft:cracked_stone_bricks",
                        "minecraft:mossy_stone_bricks",
                        "minecraft:cobblestone"},
                new int[]{50, 35, 15}));
        classic.add(new Rule("minecraft:stone", 0.12,
                new String[]{
                        "minecraft:cobblestone",
                        "minecraft:mossy_cobblestone"},
                new int[]{60, 40}));
        classic.add(new Rule("minecraft:cobblestone", 0.20,
                new String[]{"minecraft:mossy_cobblestone"},
                new int[]{1}));
        tables.put("classic", index(classic));

        return tables;
    }

    /** Indexes a rule list by its source block id for O(1) lookup. */
    private static Map<String, Rule> index(List<Rule> rules) {
        Map<String, Rule> byFrom = new HashMap<>();
        for (Rule rule : rules) {
            byFrom.put(rule.from, rule);
        }
        return byFrom;
    }
}
