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

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared codec helpers for this package's datapack configs.
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public final class Codecs {

    private Codecs() {}

    /**
     * A {@code fieldOf} that is optional but <strong>not</strong> forgiving: an absent field
     * yields {@code fallback}, while a field that is <em>present but fails to decode</em>
     * propagates the error.
     *
     * <p>This exists because DFU's own {@link Codec#optionalFieldOf(String, Object)} swallows
     * decode failures &mdash; it cannot tell "absent" from "malformed" and returns the default for
     * both. That is exactly the silent-fallthrough behaviour the {@code block_provider} system was
     * retired for: a datapack typo produced no error, no log line, and a block nobody authored. A
     * config that is worth validating at all is worth failing loudly on, so sections and lists here
     * use this instead.</p>
     *
     * <p>Note this only catches a malformed <em>value</em>. A misspelled field <em>name</em> is
     * indistinguishable from an absent one to a field codec, which is why {@link #closed} exists:
     * it closes the schema one level up, where the record's whole key set is known.</p>
     */
    /**
     * The no-fallback form: an absent field yields {@link Optional#empty()}, a present-but-malformed
     * one propagates the error. Same rationale as the fallback overload below &mdash; used where
     * "absent" is a meaningful state in its own right (a {@link RoomScheme} element slot left
     * undecorated) rather than a stand-in for some default value.
     */
    public static <A> MapCodec<Optional<A>> strictOptionalFieldOf(Codec<A> codec, String name) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null
                        ? DataResult.success(Optional.empty())
                        : codec.parse(ops, value).map(Optional::of);
            }

            @Override
            public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return input.isPresent() ? prefix.add(name, codec.encodeStart(ops, input.get())) : prefix;
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }
        };
    }

    public static <A> MapCodec<A> strictOptionalFieldOf(Codec<A> codec, String name, A fallback) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null ? DataResult.success(fallback) : codec.parse(ops, value);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return prefix.add(name, codec.encodeStart(ops, input));
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }
        };
    }

    /**
     * Closes a record's schema: a key the codec does not declare is a <strong>load error</strong>
     * rather than being dropped.
     *
     * <h2>Why</h2>
     * <p>DFU ignores keys it does not recognize, which is the last silent-authoring hole left in
     * this package. {@code "widht": 3} decodes cleanly and the panel comes out the default width;
     * {@code "projecton": 1} leaves the trim flush; a {@code width} written on a {@code courses}
     * pattern does nothing at all. In every case there is no error, no log line, and the pattern
     * still <em>draws</em> &mdash; which is why nothing in the build catches it and why it is the
     * hardest kind of mistake to see in game. It is the same failure class as
     * {@link #strictOptionalFieldOf} closes for a malformed value, and the same one that made
     * {@code WallPatternEntry}'s {@code patterns} key required.</p>
     *
     * <h2>How</h2>
     * <p>Every {@link MapCodec} already declares {@link MapCodec#keys(DynamicOps)}, and
     * {@link RecordCodecBuilder} composes a record's declared keys from its field codecs' &mdash;
     * <strong>including the flat-embedded {@link SizeGate#MAP_CODEC}</strong>, whose four gate keys
     * are therefore part of every enclosing record's key set for free. (That is load-bearing: it is
     * what stops every gated entry in the shipped schemes failing the moment this is switched on,
     * and {@code CodecsTest} pins it rather than leaving it to be rediscovered.) So all this has to
     * do is compare the input's keys against that set before delegating.</p>
     *
     * <p>The cost is that a datapack can no longer carry extra metadata of its own inside these
     * objects &mdash; the DFU idiom of forwards-compatible spare fields. Nothing here uses it, and
     * a schema that accepts anything cannot tell a spare field from a typo, which is the entire
     * problem being solved.</p>
     *
     * <p>Non-string keys are left alone rather than rejected: {@code DynamicOps} does not promise
     * string keys, and a key this cannot read is a key no field codec could have matched either, so
     * the delegate's own decode is the right place for it to fail.</p>
     */
    public static <A> Codec<A> closed(MapCodec<A> codec) {
        return closedMap(codec).codec();
    }

    /** {@link #closed} without the trailing {@link MapCodec#codec()}, for a record embedded flat. */
    public static <A> MapCodec<A> closedMap(MapCodec<A> codec) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                Set<String> declared = codec.keys(ops)
                        .map(key -> ops.getStringValue(key).result())
                        .flatMap(Optional::stream)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                List<String> unknown = new ArrayList<>();
                input.entries().forEach(entry -> ops.getStringValue(entry.getFirst()).result()
                        .filter(key -> !declared.contains(key))
                        .ifPresent(unknown::add));
                if (!unknown.isEmpty()) {
                    return DataResult.error(() -> describe(unknown, declared));
                }
                return codec.decode(ops, input);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return codec.encode(input, ops, prefix);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return codec.keys(ops);
            }
        };
    }

    /**
     * The error text. It names the nearest declared key when there is a plausible one, because the
     * whole point of this check is to catch a typo and "unknown key 'widht'" is a good deal less
     * useful than being told what was probably meant.
     */
    private static String describe(List<String> unknown, Set<String> declared) {
        StringBuilder message = new StringBuilder();
        for (String key : unknown) {
            if (message.length() > 0) {
                message.append("; ");
            }
            message.append("unknown field '").append(key).append("'");
            nearest(key, declared).ifPresent(near -> message.append(" -- did you mean '")
                    .append(near).append("'?"));
        }
        return message + " (known fields: " + String.join(", ", declared) + ")";
    }

    /**
     * The closest declared key within a small edit distance, or empty when nothing is close enough
     * to be worth guessing at. The threshold scales with length so that {@code "top"} does not get
     * offered as the intended spelling of some unrelated three-letter word.
     */
    private static Optional<String> nearest(String key, Set<String> declared) {
        int budget = Math.max(1, Math.min(3, key.length() / 3));
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : declared) {
            int distance = editDistance(key.toLowerCase(java.util.Locale.ROOT),
                    candidate.toLowerCase(java.util.Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return bestDistance <= budget ? Optional.ofNullable(best) : Optional.empty();
    }

    /** Plain Levenshtein, two rows. Only ever runs on the failure path. */
    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
