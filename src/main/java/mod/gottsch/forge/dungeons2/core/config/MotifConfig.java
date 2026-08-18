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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a single motif renders with: the base architectural blocks for each element, plus the
 * weighted list of {@link RoomScheme}s a room is dressed from. Looked up via
 * {@link MotifConfigHelper#get}.
 *
 * <p>This is the <em>resolved</em> value and has no codec of its own. A motif is authored as a
 * folder of files under {@code data/dungeons2/dungeons2/motif_config/<motif>/}, each decoded as a
 * {@link MotifConfigFragment} and folded together by {@link MotifConfigFragment#resolve}; see that
 * class for why the two types are separate.</p>
 *
 * <h2>Why this replaced two systems</h2>
 * <p>Until Jul 2026 the base blocks lived in {@code data/dungeons2/block_provider/<motif>.json},
 * loaded by a {@code SimpleJsonResourceReloadListener} into a {@code BlockProvider} of
 * {@code BlockSet}s keyed by {@code IPatternEnum} instances, while the floor's decorative patterns
 * lived in a separate codec-backed datapack registry. The two systems answered the same question
 * ("which block does this motif use here?") through completely different machinery, and the
 * {@code BlockProvider} half routed every lookup through a string &rarr; enum &rarr; map-key
 * indirection ({@code PatternRegistry}) that produced two silent, hard-to-spot bugs: a pattern-key
 * constant aliased to the wrong string, and a {@code BlockSet} queried with an enum constant it was
 * never populated with. Both failed the same way &mdash; a miss returned empty, the caller fell
 * through to a hardcoded default, and the datapack's authored block was silently ignored.</p>
 *
 * <p>A codec over named record fields cannot express either bug: a field is present and typed or
 * the file fails to load, loudly. That is the whole reason for the merge.</p>
 *
 * <h2>Materials vs. decoration</h2>
 * <p>The element sections ({@code wall}, {@code ceiling}, {@code door}, {@code corridor},
 * {@code floor}) say what the motif is <em>made of</em> &mdash; one block per slot, no choices.
 * {@code schemes} says how a room is <em>dressed</em>, and is the only thing here that is rolled.
 * The roll is per room and covers every element at once, so a room's floor, walls and ceiling
 * always come from one authored combination; see {@link RoomScheme} for why that is worth the
 * authoring redundancy it costs.</p>
 *
 * <h2>Fallbacks</h2>
 * <p>Every section is optional, but every block field <em>within</em> a section is
 * <strong>required</strong> &mdash; there are no per-slot defaults, so a half-authored section
 * fails to load loudly rather than silently rendering someone else's block. That needs
 * {@link Codecs#strictOptionalFieldOf} rather than DFU's own {@code optionalFieldOf}, which
 * swallows decode errors and would reintroduce the very failure mode this merge removed.</p>
 *
 * <p>{@link #DEFAULT} covers only the coarse case of a motif with no files at all (or no
 * registry), and reproduces the pre-merge hardcoded fallbacks exactly: stone_bricks everywhere, an
 * oak door, and an always-plain floor. The same per-section values fill any section a motif's files
 * never author. An individual block <em>id</em> that doesn't resolve at render time (a typo, or a
 * block from an uninstalled mod) falls back per-slot &mdash; see {@code BlockStateCodec#block}.</p>
 *
 * <p>No two-tier fallback to a shared/classic config, matching the rooms/transitions motif-naming
 * convention documented in {@code structures/README.md}.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record MotifConfig(WallConfig wall, CeilingConfig ceiling, DoorConfig door,
                          CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes,
                          List<MobSetBand> mobSetsByFloorIndex,
                          Map<String, TemplateLimit> templateLimits) {

    /** The shape before {@code mobSetsByFloorIndex}: a motif whose schemes must name their own sets. */
    public MotifConfig(WallConfig wall, CeilingConfig ceiling, DoorConfig door,
                       CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes) {
        this(wall, ceiling, door, corridor, floor, schemes, List.of(), Map.of());
    }

    /** The shape before {@code templateLimits}: a motif that caps no authored template. */
    public MotifConfig(WallConfig wall, CeilingConfig ceiling, DoorConfig door,
                       CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes,
                       List<MobSetBand> mobSetsByFloorIndex) {
        this(wall, ceiling, door, corridor, floor, schemes, mobSetsByFloorIndex, Map.of());
    }

    /** Used when a motif has no files: stone_bricks everywhere, oak door, always-plain floor. */
    public static final MotifConfig DEFAULT = new MotifConfig(
            WallConfig.DEFAULT, CeilingConfig.DEFAULT, DoorConfig.DEFAULT,
            CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(RoomScheme.PLAIN));

    /**
     * The mob sets a spawner on this floor draws from, when its scheme does not name its own.
     *
     * <p>Empty means the table has nothing to say &mdash; either the motif declares no table, or (a
     * case the load-time check makes unreachable for a non-empty table) no band covers this floor.
     * A scheme whose {@code spawners} slot also names no sets then places nothing, which is the
     * degrade-don't-abort convention every other missing-content path here follows.</p>
     *
     * @param floorIndex 0 at the entrance, counting downward
     */
    public List<SpawnerConfig.MobSetEntry> mobSetsFor(int floorIndex) {
        return bandFor(floorIndex)
                .map(MobSetBand::mobSets)
                .orElseGet(List::of);
    }

    /**
     * The whole band covering {@code floorIndex}, which carries the per-depth mob counts as well as
     * the sets. Prefer this over {@link #mobSetsFor} where a {@link SpawnerConfig} is being
     * resolved &mdash; the sets-only view silently drops the counts.
     *
     * @param floorIndex 0 at the entrance, counting downward
     */
    public Optional<MobSetBand> bandFor(int floorIndex) {
        return MobSetBand.forFloor(mobSetsByFloorIndex, floorIndex);
    }

    /**
     * The cap on an authored template, or empty when it is unlimited &mdash; which is the default
     * and the overwhelmingly common case.
     *
     * <p>Keyed by the template's <strong>full id</strong>, the same string a template pool's
     * {@code "location"} field carries and the same one {@code PoolElementIds} reads back off a
     * placed element. Keying on the id rather than on some D2-side alias is what makes this
     * category-agnostic: nothing about it is specific to rooms, so a transition or an entrance
     * variant can be capped later with no new mechanism.</p>
     *
     * <p>An id naming a template no pool references caps nothing and says nothing &mdash; a typo
     * cannot be caught at load, because pools resolve from datapacks at a different time, so it is
     * swept at build time instead ({@code ShippedTemplateLimitsTest}).</p>
     */
    public Optional<TemplateLimit> limitFor(String templateId) {
        return Optional.ofNullable(templateLimits.get(templateId));
    }
}
