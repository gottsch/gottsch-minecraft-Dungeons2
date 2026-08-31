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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * What one {@link RoomScheme} element slot may hold: either a single authored treatment (every
 * scheme written before Aug 2026) or a <strong>weighted list of authored alternatives</strong>, one
 * of which is rolled per room.
 *
 * <h2>Why the randomness lives in the COMBINATION, not in the values</h2>
 * <p>#65. A whole-room scheme is the unit of authoring, so the motif file grows linearly in the
 * number of rooms and every <em>combination</em> of treatments has to be written out by hand; ten
 * mud-band schemes were authored in two days. The obvious answer &mdash; a {@code random} scheme
 * type that rolls its own patterns &mdash; was rejected for two reasons worth not re-deriving:
 * <ol>
 *   <li>The pattern registries hold <em>types</em>, not configs. Rolling {@code dungeons2:diamond}
 *       yields a type with no block, no size and no spacing, and there is deliberately no default
 *       material for an inlay &mdash; so a random type would have to synthesize the very choice
 *       that <em>is</em> the theme.</li>
 *   <li>The registries are open to other mods. A scheme that rolled from the registry would place a
 *       third party's patterns in the dungeon the moment someone installed it, with no authored
 *       consent and no way to opt out.</li>
 * </ol>
 * <p>Rolling between <em>alternatives a human wrote</em> has neither problem: nothing is
 * synthesized, so the result is in-theme by construction, while the combinatorics work for the file
 * size instead of against it &mdash; the mud band's ten whole-room schemes are about fourteen
 * authored blocks' worth of content, which as slot options yields on the order of 144 rooms.</p>
 *
 * <h2>The shape, and why it is one key and not two</h2>
 * <pre>
 *   "wall": { "patterns": [ ... ] }                        // unchanged, still valid
 *
 *   "wall": [ { "weight": 3, "patterns": [ ...diamond...   ] },
 *             { "weight": 3, "patterns": [ ...pilasters... ] },
 *             { "weight": 4, "none": true } ]
 * </pre>
 * <p>An option is the slot object itself with a {@code weight} written alongside its own keys, or
 * {@code none: true} for the option of drawing nothing. Both shapes live on the <em>existing</em>
 * key rather than a parallel {@code wallOptions} one, which is not a style preference:
 * {@code RoomScheme.CODEC} is at fifteen group arguments against DFU's ceiling of sixteen (see
 * {@link SizeGate} and {@link FloorRange}, both folds forced by exactly that), so nine parallel
 * fields could not be added at all. Either-ing the existing key adds <strong>zero</strong> fields,
 * and it is also what makes migration optional &mdash; a scheme converts on its own, in place.</p>
 *
 * <h2>The trap: every list needs an explicit {@code none} weight</h2>
 * <p>Today a scheme naming no {@code ceiling} <em>has</em> no ceiling. With options, three ceiling
 * choices and no none-entry puts a ceiling in <strong>100%</strong> of the rooms that scheme
 * dresses &mdash; and that is invisible until someone walks the dungeon. It is not hypothetical:
 * band-level joists reached 55.9% incidence over 2874 rooms and became the band's <em>look</em>
 * rather than a room type. {@code BandCeilingIncidenceProbe} is the tool that keeps this honest;
 * run it against any converted scheme.</p>
 *
 * <h2>Rolling</h2>
 * <p>See {@link #resolve}. Two rules there earn their keep: options the room's size gates out are
 * dropped <em>before</em> the weights are totalled (the same reason {@code RoomSchemeSelector}
 * filters before weighting &mdash; probability must not pool into whichever option happens to be
 * last), and a slot with one authored option draws <strong>no</strong> random value, so every
 * scheme shipped before this existed generates exactly the dungeon it did before.</p>
 *
 * @param <T> the slot's treatment type &mdash; {@code WallPatternEntry}, {@code PotConfig}, ...
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
public record SlotOptions<T>(List<Option<T>> options) {

    /**
     * One alternative and its weight. An {@link Optional#empty()} value is the authored option of
     * drawing nothing &mdash; {@code none: true} &mdash; which is a different thing from the slot
     * being absent, and is the entry whose omission the class doc warns about.
     */
    public record Option<T>(int weight, Optional<T> value) {}

    private static final SlotOptions<?> EMPTY = new SlotOptions<>(List.of());

    /** The slot the author did not fill at all. */
    @SuppressWarnings("unchecked")
    public static <T> SlotOptions<T> empty() {
        return (SlotOptions<T>) EMPTY;
    }

    /** The pre-#65 shape: one treatment, drawn whenever its scheme is rolled and its gate fits. */
    public static <T> SlotOptions<T> of(T value) {
        return new SlotOptions<>(List.of(new Option<>(1, Optional.of(value))));
    }

    /** {@link #of(Object)} or {@link #empty()}, for the {@code Optional}-shaped constructors. */
    public static <T> SlotOptions<T> of(Optional<T> value) {
        return value.map(SlotOptions::of).orElseGet(SlotOptions::empty);
    }

    /** Whether the author filled this slot at all. */
    public boolean isEmpty() {
        return options.isEmpty();
    }

    /** Whether a choice still has to be made &mdash; see {@link #resolve}. */
    public boolean isUnresolved() {
        return options.size() > 1;
    }

    /** Every authored alternative's value, none-options excluded. For validation and probes. */
    public Stream<T> all() {
        return options.stream().map(Option::value).flatMap(Optional::stream);
    }

    /**
     * The one treatment this slot holds, once the choice has been made.
     *
     * <p><strong>Throws</strong> rather than returning empty when the slot still holds
     * alternatives. A slot that quietly drew nothing because nobody rolled it is precisely the
     * silent-nothing failure the strict codecs in this package exist to prevent, and it would look
     * in game exactly like an authoring mistake. {@code RoomSchemeSelector} resolves every scheme
     * it returns, so the only way to see this is to have built a scheme by hand and skipped the
     * roll.</p>
     */
    public Optional<T> value() {
        if (isUnresolved()) {
            throw new IllegalStateException("slot still holds " + options.size()
                    + " alternatives; RoomScheme#resolve has to run before a slot can be drawn");
        }
        return options.isEmpty() ? Optional.empty() : options.get(0).value();
    }

    /**
     * The {@link Optional} face of {@link #value}, so that the seventy-odd call sites written
     * against a slot that <em>was</em> an {@code Optional} go on reading as they did.
     *
     * <p>Every one of them routes through {@link #value}, which means an unresolved slot throws
     * here too rather than answering "nothing" &mdash; that is the point of keeping them thin.</p>
     */
    public boolean isPresent() {
        return value().isPresent();
    }

    /** See {@link #isPresent}. */
    public <R> Optional<R> map(java.util.function.Function<? super T, ? extends R> mapper) {
        return value().map(mapper);
    }

    /** See {@link #isPresent}. */
    public T orElseThrow() {
        return value().orElseThrow();
    }

    /** See {@link #isPresent}. */
    public Stream<T> stream() {
        return value().stream();
    }

    /** See {@link #isPresent}. */
    public void ifPresent(java.util.function.Consumer<? super T> action) {
        value().ifPresent(action);
    }

    /** This slot if the author filled it, else the inherited one. See {@code RoomScheme#inheritFrom}. */
    public SlotOptions<T> orElse(SlotOptions<T> inherited) {
        return isEmpty() ? inherited : this;
    }

    /**
     * This slot with its alternatives collapsed to the one this room gets.
     *
     * <p>{@code eligible} is the slot's own size gate, applied to the room being dressed. Options it
     * rejects are dropped <em>before</em> the weights are totalled, so an option that cannot draw
     * here does not merely lose &mdash; it never enters the denominator, and the surviving options
     * keep their relative proportions. A none-option is never gated out; drawing nothing fits any
     * room.</p>
     *
     * <p><strong>A random value is consumed if and only if the author wrote more than one
     * option</strong>, whatever the gates then do to them. That is what lets an existing motif be
     * converted one slot at a time: every scheme still holding a single treatment draws exactly the
     * values it drew before this class existed, so its dungeons are unchanged. The condition is the
     * <em>authored</em> count and not the surviving count on purpose &mdash; keying it on what
     * survives would make the number of values drawn depend on the room's size, which is the thing
     * {@code RoomSchemeSelector} takes care never to do.</p>
     */
    public SlotOptions<T> resolve(RandomSource random, Predicate<T> eligible) {
        if (!isUnresolved()) {
            // One option, or none. Nothing to choose and, deliberately, nothing drawn from `random`.
            return options.isEmpty() || options.get(0).value().filter(eligible.negate()).isEmpty()
                    ? this : empty();
        }
        List<Option<T>> fitting = new ArrayList<>();
        int totalWeight = 0;
        for (Option<T> option : options) {
            if (option.value().filter(eligible.negate()).isPresent()) {
                continue;
            }
            fitting.add(option);
            totalWeight += option.weight();
        }
        // The draw happens even when nothing survived, so that the stream position after a scheme
        // does not depend on the room it was rolled for.
        int roll = random.nextInt(Math.max(1, totalWeight));
        int cumulative = 0;
        for (Option<T> option : fitting) {
            cumulative += option.weight();
            if (roll < cumulative) {
                // A winning `none` collapses to the UNFILLED slot rather than to a one-entry list
                // holding nothing. The two would draw the same room, but only this way does
                // isEmpty() keep meaning the same thing as value().isEmpty() -- and a slot with two
                // ways to say "nothing here", one of which most callers do not test for, is the
                // kind of distinction that goes wrong months later.
                return of(option.value());
            }
        }
        return empty();
    }

    /**
     * The slot's field on a scheme: absent, one object, or a list of weighted options.
     *
     * <p>Written by hand rather than as {@code Codec.either} so that the error an author sees names
     * the shape they actually wrote. {@code either} reports both branches' failures concatenated,
     * so a misspelled key inside a single treatment comes back as a complaint about it not being a
     * list either &mdash; which buries the one line that matters under the one that does not.</p>
     *
     * <p>{@code element} is the treatment's <strong>open</strong> map codec. It has to be open
     * because an option writes {@code weight} alongside the treatment's own keys; the closed check
     * is re-imposed one level up, over the union of both key sets, so a typo inside an option is
     * still a load error.</p>
     */
    public static <T> MapCodec<SlotOptions<T>> field(MapCodec<T> element, String name) {
        Codec<T> single = Codecs.closed(element);
        Codec<List<Option<T>>> list = Codecs.closed(optionCodec(element)).listOf();
        Codec<SlotOptions<T>> codec = new Codec<>() {
            @Override
            public <U> DataResult<Pair<SlotOptions<T>, U>> decode(DynamicOps<U> ops, U input) {
                if (ops.getStream(input).result().isPresent()) {
                    return list.decode(ops, input)
                            .flatMap(pair -> validate(pair.getFirst())
                                    .map(options -> Pair.of(options, pair.getSecond())));
                }
                return single.decode(ops, input)
                        .map(pair -> Pair.of(SlotOptions.of(pair.getFirst()), pair.getSecond()));
            }

            @Override
            public <U> DataResult<U> encode(SlotOptions<T> input, DynamicOps<U> ops, U prefix) {
                // Round-trips to the shape it was written in: a lone unweighted option is the
                // single-treatment form, which is what every unconverted scheme dumps back as.
                if (input.options.size() == 1 && input.options.get(0).weight() == 1
                        && input.options.get(0).value().isPresent()) {
                    return single.encode(input.options.get(0).value().get(), ops, prefix);
                }
                return list.encode(input.options, ops, prefix);
            }
        };
        return new MapCodec<>() {
            @Override
            public <U> DataResult<SlotOptions<T>> decode(DynamicOps<U> ops, MapLike<U> input) {
                U value = input.get(name);
                return value == null
                        ? DataResult.success(SlotOptions.empty())
                        : codec.parse(ops, value).mapError(error -> name + ": " + error);
            }

            @Override
            public <U> RecordBuilder<U> encode(SlotOptions<T> input, DynamicOps<U> ops,
                                               RecordBuilder<U> prefix) {
                // An empty slot is an ABSENT key, not an empty list -- the distinction the whole
                // Optional-shaped slot API rests on.
                return input.isEmpty() ? prefix : prefix.add(name, codec.encodeStart(ops, input));
            }

            @Override
            public <U> Stream<U> keys(DynamicOps<U> ops) {
                return Stream.of(ops.createString(name));
            }
        };
    }

    /** A list that can never win a roll is an authoring mistake, not a slot that draws nothing. */
    private static <T> DataResult<SlotOptions<T>> validate(List<Option<T>> options) {
        if (options.isEmpty()) {
            return DataResult.error(() -> "an empty option list draws nothing at all; "
                    + "omit the slot instead, or write a single option with `none` set");
        }
        return DataResult.success(new SlotOptions<>(List.copyOf(options)));
    }

    private static final String WEIGHT = "weight";
    private static final String NONE = "none";

    /**
     * One {@code weight}-bearing option object. Hand-written because the two branches are not two
     * codecs over the same keys: {@code none: true} has to short-circuit the treatment's codec
     * entirely, and a treatment's own {@code patterns} key is required, so simply letting it decode
     * an option with nothing in it would fail.
     */
    private static <T> MapCodec<Option<T>> optionCodec(MapCodec<T> element) {
        return new MapCodec<>() {
            @Override
            public <U> DataResult<Option<T>> decode(DynamicOps<U> ops, MapLike<U> input) {
                U rawWeight = input.get(WEIGHT);
                DataResult<Integer> weight = rawWeight == null ? DataResult.success(1)
                        : Codec.intRange(1, Integer.MAX_VALUE).parse(ops, rawWeight)
                                .mapError(error -> WEIGHT + ": " + error);
                U rawNone = input.get(NONE);
                DataResult<Boolean> none = rawNone == null ? DataResult.success(false)
                        : Codec.BOOL.parse(ops, rawNone).mapError(error -> NONE + ": " + error);
                return weight.flatMap(w -> none.flatMap(isNone -> {
                    if (!isNone) {
                        return element.decode(ops, input)
                                .map(value -> new Option<>(w, Optional.of(value)));
                    }
                    // Both at once is a contradiction with a plausible reading either way -- the
                    // author either meant to delete the treatment or meant to delete the `none`,
                    // and guessing wrong is a room silently dressed the other way.
                    List<String> extra = new ArrayList<>();
                    input.entries().forEach(entry -> ops.getStringValue(entry.getFirst()).result()
                            .filter(key -> !WEIGHT.equals(key) && !NONE.equals(key))
                            .ifPresent(extra::add));
                    if (!extra.isEmpty()) {
                        return DataResult.error(() -> "a `none` option also declares "
                                + String.join(", ", extra) + "; an option is either `none` or a "
                                + "treatment, never both");
                    }
                    return DataResult.success(new Option<T>(w, Optional.empty()));
                }));
            }

            @Override
            public <U> RecordBuilder<U> encode(Option<T> input, DynamicOps<U> ops,
                                               RecordBuilder<U> prefix) {
                RecordBuilder<U> builder = prefix.add(WEIGHT, ops.createInt(input.weight()));
                return input.value().isPresent()
                        ? element.encode(input.value().get(), ops, builder)
                        : builder.add(NONE, ops.createBoolean(true));
            }

            @Override
            public <U> Stream<U> keys(DynamicOps<U> ops) {
                return Stream.concat(element.keys(ops),
                        Stream.of(ops.createString(WEIGHT), ops.createString(NONE)));
            }
        };
    }
}
