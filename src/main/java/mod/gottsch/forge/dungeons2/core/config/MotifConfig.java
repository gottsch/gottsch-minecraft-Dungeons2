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

import java.util.LinkedHashMap;
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
                          List<ChestLootBand> chestLootByFloorIndex,
                          Map<String, TemplateLimit> templateLimits,
                          List<Stratum> strataByFloorIndex,
                          Map<String, String> palette) {

    /** The shape before {@code palette}: a motif whose patterns all name literal block ids. */
    public MotifConfig(WallConfig wall, CeilingConfig ceiling, DoorConfig door,
                       CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes,
                       List<MobSetBand> mobSetsByFloorIndex,
                       List<ChestLootBand> chestLootByFloorIndex,
                       Map<String, TemplateLimit> templateLimits,
                       List<Stratum> strataByFloorIndex) {
        this(wall, ceiling, door, corridor, floor, schemes, mobSetsByFloorIndex,
                chestLootByFloorIndex, templateLimits, strataByFloorIndex, Map.of());
    }

    /** The shape before {@code strata_by_floor_index}: a motif that looks the same all the way down. */
    public MotifConfig(WallConfig wall, CeilingConfig ceiling, DoorConfig door,
                       CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes,
                       List<MobSetBand> mobSetsByFloorIndex,
                       List<ChestLootBand> chestLootByFloorIndex,
                       Map<String, TemplateLimit> templateLimits) {
        this(wall, ceiling, door, corridor, floor, schemes, mobSetsByFloorIndex,
                chestLootByFloorIndex, templateLimits, List.of());
    }

    /** The shape before {@code chest_loot_by_floor_index}: a motif whose chests must name their own tables. */
    public MotifConfig(WallConfig wall, CeilingConfig ceiling, DoorConfig door,
                       CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes,
                       List<MobSetBand> mobSetsByFloorIndex,
                       Map<String, TemplateLimit> templateLimits) {
        this(wall, ceiling, door, corridor, floor, schemes, mobSetsByFloorIndex, List.of(),
                templateLimits);
    }

    /**
     * The chest loot band covering {@code floorIndex}. Pair with
     * {@code ChestConfig#resolvedAgainst}; a scheme naming its own tables keeps them.
     *
     * @param floorIndex 0 at the entrance, counting downward
     */
    public Optional<ChestLootBand> chestBandFor(int floorIndex) {
        return ChestLootBand.forFloor(chestLootByFloorIndex, floorIndex);
    }

    /** The shape before {@code mob_sets_by_floor_index}: a motif whose schemes must name their own sets. */
    public MotifConfig(WallConfig wall, CeilingConfig ceiling, DoorConfig door,
                       CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes) {
        this(wall, ceiling, door, corridor, floor, schemes, List.of(), List.of(), Map.of());
    }

    /** The shape before {@code template_limits}: a motif that caps no authored template. */
    public MotifConfig(WallConfig wall, CeilingConfig ceiling, DoorConfig door,
                       CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes,
                       List<MobSetBand> mobSetsByFloorIndex) {
        this(wall, ceiling, door, corridor, floor, schemes, mobSetsByFloorIndex, List.of(), Map.of());
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

    /**
     * The name of the stratum covering {@code floorIndex}, when it has one. Backlog #45 step 3.
     *
     * <p>This is the segment that opts a depth into its own template pools:
     * {@code rooms/<motif>/<stratum>/normal}, resolved ahead of the motif's own by
     * {@code DungeonStructure.chooseStartPool}. Empty means "this depth draws from the motif's
     * rooms", which is every motif shipped today.
     *
     * <p>Read from the <em>unprojected</em> config: {@link #forFloor} clears the table, so a
     * projection has no stratum to name. The assembler asks the motif, not the projection.
     *
     * @param floorIndex 0 at the entrance, counting downward
     */
    public Optional<String> stratumNameFor(int floorIndex) {
        return Stratum.forFloor(strataByFloorIndex, floorIndex).flatMap(Stratum::name);
    }

    /**
     * This motif as it is built on {@code floorIndex}: the element sections swapped for the
     * covering stratum's, everything else untouched. Backlog #45.
     *
     * <p><strong>A motif with no strata returns itself</strong> &mdash; not a copy, the same
     * instance &mdash; which is what makes this safe to call unconditionally from every piece. That
     * is also the guarantee that every dungeon in every existing world renders byte-identically:
     * nothing ships a stratum, so nothing changes until an author writes one.
     *
     * <p>Sections the band does not declare fall through to this config's own; see {@link Stratum}.
     * {@code schemes} is the exception and does not replace at all &mdash; it merges by name, via
     * {@link #mergeSchemes}.
     * The projection is a pure function of (motif, floorIndex) and both are in hand where a piece
     * renders, so it happens at <strong>build</strong> time. Resolving at plan time would buy
     * nothing and would add a field to serialise.
     *
     * <p>The result carries an <strong>empty</strong> strata table, so a projection cannot be
     * projected again. That is deliberate rather than tidy: re-projecting a floor-0 config onto
     * floor 3 would resolve floor 3's undeclared sections against floor 0's <em>banded</em>
     * sections instead of the motif's base, silently. Making the second call a no-op removes the
     * question.
     *
     * @param floorIndex 0 at the entrance, counting downward
     */
    public MotifConfig forFloor(int floorIndex) {
        if (strataByFloorIndex.isEmpty()) {
            // Still resolve roles. A motif may declare a palette and no bands at all -- the roles
            // then do nothing depth-dependent, but they are still how its schemes name materials,
            // and returning `this` here would hand `$shaft` to the generator, which draws NOTHING.
            // The early return predates the palette and quietly excluded exactly that motif.
            return palette.isEmpty() ? this : withPalette(this, palette);
        }
        return Stratum.forFloor(strataByFloorIndex, floorIndex)
                .map(stratum -> new MotifConfig(
                        stratum.wall().orElse(wall),
                        stratum.ceiling().orElse(ceiling),
                        stratum.door().orElse(door),
                        stratum.corridor().orElse(corridor),
                        stratum.floor().orElse(floor),
                        withRoles(mergeSchemes(stratum), overlay(palette, stratum.palette())),
                        mobSetsByFloorIndex, chestLootByFloorIndex, templateLimits,
                        List.of(),
                        // OVERLAY, not replace -- the one section that behaves like this, and the
                        // reason is the same one that makes `schemes` merge. Every other section is
                        // a coherent whole a band either restates or inherits; a palette is a set
                        // of INDEPENDENT roles, and a band typically repaints two or three of them
                        // (classic to mud is four lines). Whole-replace would make a band restate
                        // the entire vocabulary to change one entry, which is exactly the drift an
                        // overlay exists to prevent.
                        overlay(palette, stratum.palette())))
                // No band covers this floor -- UNREACHABLE for any pack that loads, since
                // Stratum.validate already rejects a band table that does not cover floor 0 and
                // bands run downward from their own floor. Kept resolving roles anyway rather than
                // returning `this`, because the alternative if it ever were reached is a scheme
                // handing `$shaft` to the generator, which draws nothing.
                .orElseGet(() -> palette.isEmpty() ? this : withPalette(this, palette));
    }

    /** {@link #forFloor}'s no-band paths: the motif's own palette, applied to its own schemes. */
    private static MotifConfig withPalette(MotifConfig motif, Map<String, String> palette) {
        List<RoomScheme> resolved = withRoles(motif.schemes(), palette);
        return resolved == motif.schemes() ? motif
                : new MotifConfig(motif.wall(), motif.ceiling(), motif.door(), motif.corridor(),
                        motif.floor(), resolved, motif.mobSetsByFloorIndex(),
                        motif.chestLootByFloorIndex(), motif.templateLimits(),
                        motif.strataByFloorIndex(), palette);
    }

    /**
     * The band's schemes with their material roles resolved against the palette in scope.
     *
     * <p><strong>Here, and not at load, because the answer is depth-dependent</strong> &mdash; that
     * is the entire point of roles. One authored scheme paints itself in spruce on the mud band and
     * in dressed stone below it, so the substitution cannot happen until the floor is known, and
     * the floor is known here.</p>
     *
     * <p>This method is on the per-piece path, which rules out the cheap implementation: a scheme
     * cannot be re-encoded to JSON, substituted and re-decoded, because that would run once per
     * room piece per chunk. It is an explicit walk over the records instead, and every step of it
     * returns the instance it was given when nothing changed, so an unconverted motif pays one
     * reference comparison per slot and allocates nothing.</p>
     *
     * <p>A role the palette does not declare is left as-is, which draws nothing. That is not a
     * design choice so much as a place the design must not be reached: {@code MotifConfigFragment}
     * validates every role in every band at load, over this same walk, so a pack that gets here
     * with an unresolvable role has already been reported as broken.</p>
     */
    private static List<RoomScheme> withRoles(List<RoomScheme> schemes, Map<String, String> palette) {
        if (palette.isEmpty()) {
            return schemes;
        }
        return schemes.stream()
                .map(scheme -> scheme.withRoles(role -> palette.getOrDefault(role, "$" + role)))
                .toList();
    }

    /**
     * The band's roles written over the motif's, key by key. Returns the motif's own map unchanged
     * when the band declares none, so the common case allocates nothing.
     */
    private static Map<String, String> overlay(Map<String, String> motif, Map<String, String> band) {
        if (band.isEmpty()) {
            return motif;
        }
        Map<String, String> merged = new LinkedHashMap<>(motif);
        merged.putAll(band);
        return Map.copyOf(merged);
    }

    /**
     * The literal block id a role names, at this depth. Empty for a role the palette in scope does
     * not declare, which callers must treat as a load error rather than as a block that draws
     * nothing -- see {@code Codecs#BLOCK_ID}.
     *
     * <p>Ask this of a config that has already been through {@link #forFloor}: the answer is
     * depth-dependent, and that is the whole point.</p>
     *
     * @param role the name WITHOUT the {@code $} sigil
     */
    public Optional<String> role(String role) {
        return Optional.ofNullable(palette.get(role));
    }

    /**
     * The scheme list rolled at this band's depth: the motif's, with the band's entries merged in
     * <strong>by name</strong>.
     *
     * <p>The one section that is not whole-replace. {@link Stratum} carries the reasoning; the
     * mechanics are the ones {@code MotifConfigFragment.resolve} uses to fold a motif's files
     * together, down to the {@link LinkedHashMap} &mdash; overriding an existing name keeps its
     * position, so a band cannot quietly reorder the rest, and a new name lands at the end.
     *
     * <p>The band's entries are already inherited by the time they get here; {@code resolve} did
     * that, because {@code extends} is resolvable only across a whole folded motif. A band built by
     * hand in a test skips that step and merges whatever it was handed, which is the honest
     * behaviour &mdash; there is nothing to inherit from.
     */
    private List<RoomScheme> mergeSchemes(Stratum stratum) {
        if (stratum.schemes().isEmpty() || stratum.schemes().get().isEmpty()) {
            return schemes;
        }
        Map<String, RoomScheme> merged = new LinkedHashMap<>();
        for (RoomScheme scheme : schemes) {
            merged.put(scheme.name(), scheme);
        }
        for (RoomScheme scheme : stratum.schemes().get()) {
            merged.put(scheme.name(), scheme);
        }
        return List.copyOf(merged.values());
    }

}
