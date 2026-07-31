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

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorBorderPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.CrossFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RadialSpokesFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RandomSpeckleFloorPatternProvider;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One weighted option in a {@link FloorConfig}'s pattern list. {@code type} is a plain string
 * discriminator rather than an enum &mdash; deliberately, matching the reasoning already written
 * into {@code FloorBorderPattern}'s TODOs about not over-committing to an enum-per-decorator
 * shape before there's more than one real pattern to compare it against.
 *
 * <p>{@code "empty"} (or any unrecognized type) means no special pattern &mdash; the room's floor
 * generator falls back to the plain/alternating {@code BasicFloorGenerator}, same as an absent
 * config entry always degrades gracefully elsewhere in this codebase. {@code "border"} selects
 * {@link mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorBorderPatternProvider},
 * using {@code inset} and the three optional block-id fields below (only meaningful for that
 * type).</p>
 *
 * <p>{@code cornerBlock}/{@code edgeLeftBlock}/{@code edgeRightBlock} are resource-location
 * strings (e.g. {@code "minecraft:polished_andesite"}) &mdash; <strong>all three are required for
 * a {@code "border"} entry to actually render the ring.</strong> There is deliberately no
 * Java-side default block for any slot (see {@code FloorBorderPatternProvider}): if any of the
 * three is absent, malformed, or points at an unregistered id, {@code FloorPatternSelector}
 * degrades the <em>whole entry</em> to plain floor rather than guessing a block for the missing
 * slot. Set {@code edgeLeftBlock} and {@code edgeRightBlock} to the <em>same</em> id for a
 * single-block edge with no left/right texture variant (e.g. a plain stone type).</p>
 *
 * <p>{@code "checkerboard"} selects {@link
 * mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.CheckerboardFloorPatternProvider},
 * using {@code primaryBlock}/{@code secondaryBlock} &mdash; both required, same
 * degrade-the-whole-entry-to-plain rule as the border slots.</p>
 *
 * <p>{@code "speckle"} selects {@link
 * mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RandomSpeckleFloorPatternProvider},
 * using {@code primaryBlock}/{@code secondaryBlock} as base/accent (both required, same rule as
 * above) and {@code probability} (0-1, default {@value
 * mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RandomSpeckleFloorPatternProvider#DEFAULT_PROBABILITY})
 * as the per-cell chance of the accent block -- {@code probability} keeps its own default since
 * it's a pattern-shape knob, not a motif-scoped material.</p>
 *
 * <p>{@code "cross"} selects {@link
 * mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.CrossFloorPatternProvider} (an
 * accent plus through the room's centre) using {@code primaryBlock} and {@code thickness}
 * (default {@value mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.CrossFloorPatternProvider#DEFAULT_THICKNESS});
 * {@code "spokes"} selects {@link
 * mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RadialSpokesFloorPatternProvider}
 * (lines radiating from the centre) using {@code primaryBlock} and {@code spokes} (default
 * {@value mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.RadialSpokesFloorPatternProvider#DEFAULT_SPOKES}).
 * Both take one block, both are overlay-capable, and both degrade the entry to plain if that block
 * fails to resolve.</p>
 *
 * <p>{@code "composite"} layers several of the above into one pattern &mdash; e.g. a checkerboard
 * fill with a border ring on top &mdash; via its own {@code generators} list: an <em>ordered</em>
 * (not weighted) list of nested entries, applied in sequence. The first entry is the base full
 * fill; every entry after it overlays on top and only takes effect if its type is
 * overlay-capable ({@code "border"}, {@code "cross"} and {@code "spokes"} today) &mdash; anything
 * else there is silently skipped, same
 * graceful degradation an unrecognized top-level {@code type} already gets. {@code weight} on a
 * nested entry is ignored (defaults to {@code 1} if omitted); only the outer entry's own
 * {@code weight} matters for the roll.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public record FloorPatternEntry(String type, int weight, int inset,
                                 Optional<String> cornerBlock,
                                 Optional<String> edgeLeftBlock,
                                 Optional<String> edgeRightBlock,
                                 Optional<String> primaryBlock,
                                 Optional<String> secondaryBlock,
                                 double probability,
                                 int thickness,
                                 int spokes,
                                 List<FloorPatternEntry> generators) {

    /** Convenience for entries that don't need block substitution (e.g. {@code "empty"}). */
    public FloorPatternEntry(String type, int weight, int inset) {
        this(type, weight, inset, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY,
                CrossFloorPatternProvider.DEFAULT_THICKNESS, RadialSpokesFloorPatternProvider.DEFAULT_SPOKES,
                List.of());
    }

    /**
     * Holds {@link #CODEC} for {@code generators}' self-referential nested-list field below.
     * A plain {@code () -> CODEC} lambda inside {@code CODEC}'s own initializer is rejected by
     * javac as a forward self-reference even though it would be safe (the lambda only runs after
     * {@code CODEC} is assigned) &mdash; routing through this holder, populated by the static
     * initializer block right after {@code CODEC} itself, sidesteps that check entirely. This
     * project's {@code datafixerupper} version has no built-in {@code Codec.lazyInitialized}
     * (added in later Mojang versions) that would otherwise handle this.
     */
    private static Codec<FloorPatternEntry> codecHolder;

    public static final Codec<FloorPatternEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(FloorPatternEntry::type),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(FloorPatternEntry::weight),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("inset", FloorBorderPatternProvider.DEFAULT_INSET)
                    .forGetter(FloorPatternEntry::inset),
            Codec.STRING.optionalFieldOf("cornerBlock").forGetter(FloorPatternEntry::cornerBlock),
            Codec.STRING.optionalFieldOf("edgeLeftBlock").forGetter(FloorPatternEntry::edgeLeftBlock),
            Codec.STRING.optionalFieldOf("edgeRightBlock").forGetter(FloorPatternEntry::edgeRightBlock),
            Codec.STRING.optionalFieldOf("primaryBlock").forGetter(FloorPatternEntry::primaryBlock),
            Codec.STRING.optionalFieldOf("secondaryBlock").forGetter(FloorPatternEntry::secondaryBlock),
            Codec.doubleRange(0.0, 1.0)
                    .optionalFieldOf("probability", RandomSpeckleFloorPatternProvider.DEFAULT_PROBABILITY)
                    .forGetter(FloorPatternEntry::probability),
            Codec.intRange(0, Integer.MAX_VALUE)
                    .optionalFieldOf("thickness", CrossFloorPatternProvider.DEFAULT_THICKNESS)
                    .forGetter(FloorPatternEntry::thickness),
            Codec.intRange(0, Integer.MAX_VALUE)
                    .optionalFieldOf("spokes", RadialSpokesFloorPatternProvider.DEFAULT_SPOKES)
                    .forGetter(FloorPatternEntry::spokes),
            lazyInitialized(() -> codecHolder).listOf().optionalFieldOf("generators", List.of())
                    .forGetter(FloorPatternEntry::generators)
    ).apply(instance, FloorPatternEntry::new));

    static {
        codecHolder = CODEC;
    }

    private static <A> Codec<A> lazyInitialized(Supplier<Codec<A>> delegate) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return delegate.get().decode(ops, input);
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return delegate.get().encode(input, ops, prefix);
            }
        };
    }
}
