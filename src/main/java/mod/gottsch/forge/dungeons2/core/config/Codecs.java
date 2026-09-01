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
     * The sigil that marks a <strong>material role</strong> rather than a literal block id &mdash;
     * {@code "block": "$shaft"}. #65's second half.
     *
     * <p>It goes on the <em>existing</em> key rather than a parallel {@code blockRole} one because
     * there are forty-nine block-valued fields across twenty-six records in this package; a parallel
     * key doubles that to ninety-eight, and the pair can disagree. That is only safe because
     * {@code $} is <strong>illegal in a {@link net.minecraft.resources.ResourceLocation}</strong>
     * (namespace and path are {@code [a-z0-9_.-]}), so a leading {@code $} cannot collide with any
     * id a pack could legally write and needs no escape. {@code #} was rejected for the opposite
     * reason: it is legal-looking and reads as a block tag.</p>
     */
    public static final String ROLE_PREFIX = "$";

    /**
     * A block-valued datapack field, <strong>in reject mode</strong>: a literal id only, and a
     * {@code $role} is a load error naming the field.
     *
     * <h2>Why the rejection has to land before any consumer does</h2>
     * <p>Material roles arrive one record at a time (#65's phases), so there is an interval in which
     * some fields resolve a role and some do not. A field that does not, left alone, hands
     * {@code "$shaft"} straight to {@code BlockStateCodec#blockOrNull}, which returns {@code null}
     * for anything that is not a resolvable id &mdash; and every caller reads {@code null} as
     * "draw nothing" or substitutes a structural fallback. So the room generates, nothing is
     * logged, and a wall the author dressed comes out plain. That is the exact silent-nothing class
     * {@link #closed} and {@link #strictOptionalFieldOf} exist to close, and it is worse here
     * because it appears only in the half-converted state, where nobody is looking for it.</p>
     *
     * <p>Installing this on <strong>every</strong> block field first means the half-converted state
     * cannot happen: until a record's fields are flipped to accept roles, writing one there fails
     * the pack. The allowlist maintains itself &mdash; there is no separate list of converted
     * records to keep in step, which is the kind of list that goes stale.</p>
     *
     * <p>Deliberately <em>not</em> validating that the id is well-formed or that the block exists.
     * Well-formedness is a real hole and closing it is a one-line change here, but it would reject
     * data that loads today, which is a separate decision from this one. Existence cannot be checked
     * at all: Forge locks the block registry, so a headless load has no registry to ask.</p>
     */
    public static final Codec<String> BLOCK_ID =
            Codec.STRING.flatXmap(Codecs::rejectRole, Codecs::rejectRole);

    private static DataResult<String> rejectRole(String id) {
        if (!id.strip().startsWith(ROLE_PREFIX)) {
            return DataResult.success(id);
        }
        return DataResult.error(() -> "'" + id.strip() + "' is a material role, and this field does"
                + " not read one yet -- the palette exists but nothing resolves against it. Write a"
                + " literal block id here. (Left as-is it would silently draw nothing.)");
    }

    /**
     * A motif's or band's palette: role name to literal block id.
     *
     * <p>Flat on purpose, even though a role name may contain a dot ({@code joist.beam}). The dot is
     * a <strong>naming convention for a coordinated set</strong> &mdash; a beam and the bracket that
     * carries it move together, so they are named together &mdash; and nothing here inspects it. A
     * nested JSON object would read a little better and would cost a deep merge, which is precisely
     * what a band must not need: a stratum overlays <em>one</em> role without restating its
     * siblings.</p>
     *
     * <p>Values are literal ids, never roles: a role pointing at a role is indirection nobody asked
     * for and a cycle nothing checks. {@link #BLOCK_ID} enforces that here as much as anywhere.</p>
     */
    public static final Codec<java.util.Map<String, String>> PALETTE =
            Codec.unboundedMap(Codec.STRING.flatXmap(Codecs::roleName, Codecs::roleName), BLOCK_ID);

    private static final java.util.regex.Pattern ROLE_NAME =
            java.util.regex.Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9_.]*");

    private static DataResult<String> roleName(String name) {
        // The sigil belongs at the USE site and not the declaration, so that a palette reads as a
        // list of names and a pattern reads as naming one. Writing it on both is the likelier slip,
        // and it would otherwise define a role called "$shaft" that "$shaft" never matches.
        if (name.startsWith(ROLE_PREFIX)) {
            return DataResult.error(() -> "palette role '" + name + "': declare it without the '"
                    + ROLE_PREFIX + "' (write \"" + name.substring(1) + "\"); the sigil is how a"
                    + " pattern REFERS to a role, not part of its name");
        }
        if (!ROLE_NAME.matcher(name).matches()) {
            return DataResult.error(() -> "palette role '" + name + "' is not a usable name;"
                    + " use letters, digits, underscore, and '.' to group a coordinated set"
                    + " (joist.beam, joist.bracket)");
        }
        return DataResult.success(name);
    }

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
     *
     * <h2>Both forms PREFIX the error with the field name</h2>
     * <p>Added 2026-08-30. Without it the propagated error is whatever the inner codec said and
     * nothing more &mdash; an {@code intRange} rejection reads "Value 0 outside of range
     * [1..2147483647]" with no hint as to <em>which key</em>, in a record that may have nine of
     * them and in a motif file that may declare a dozen records. The value is quoted; the field is
     * the half the author actually needs to go and fix. Vanilla's own {@code fieldOf} has the same
     * gap, so this is not a regression being restored, and it applies to every strict optional
     * field in the mod at once.</p>
     */
    public static <A> MapCodec<Optional<A>> strictOptionalFieldOf(Codec<A> codec, String name) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null
                        ? DataResult.success(Optional.empty())
                        : codec.parse(ops, value).map(Optional::of)
                                .mapError(error -> name + ": " + error);
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
                return value == null ? DataResult.success(fallback)
                        : codec.parse(ops, value).mapError(error -> name + ": " + error);
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
     * Declares extra keys that carry no data, so a {@link #closed} schema will accept a note written
     * into the file itself.
     *
     * <h2>Why a config needs somewhere to explain itself</h2>
     * <p>JSON has no comments, and closing the schema (which is the right default &mdash; see
     * {@link #closed}) means a pack cannot smuggle one in under a spare key either. For most of
     * these records that is fine: the README is the documentation. It is <em>not</em> fine for a
     * knob whose consequences reach outside the file, where the person about to change a number
     * needs to read the warning at the moment they are changing it, not to have read a document
     * some time earlier.</p>
     *
     * <p>The declared keys are added to {@link MapCodec#keys} so the closed check accepts them, and
     * are otherwise ignored on decode and never written on encode. That is deliberate: a note is
     * not state, and round-tripping it would make it part of the record's identity.</p>
     */
    public static <A> MapCodec<A> documented(MapCodec<A> codec, String... commentKeys) {
        List<String> extra = List.of(commentKeys);
        return new MapCodec<>() {
            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                return codec.decode(ops, input);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return codec.encode(input, ops, prefix);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.concat(codec.keys(ops), extra.stream().map(ops::createString));
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
