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
                          CorridorConfig corridor, FloorConfig floor, List<RoomScheme> schemes) {

    /** Used when a motif has no files: stone_bricks everywhere, oak door, always-plain floor. */
    public static final MotifConfig DEFAULT = new MotifConfig(
            WallConfig.DEFAULT, CeilingConfig.DEFAULT, DoorConfig.DEFAULT,
            CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(RoomScheme.PLAIN));
}
