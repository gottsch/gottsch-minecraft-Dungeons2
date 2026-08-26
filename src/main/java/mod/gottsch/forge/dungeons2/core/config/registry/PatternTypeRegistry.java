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
package mod.gottsch.forge.dungeons2.core.config.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An open, {@link ResourceLocation}-keyed set of authored pattern types, and the {@code type} +
 * {@code config} codec that dispatches over it. One instance per element slot &mdash; floor
 * patterns, pillar layouts, and (in time) wall, ceiling and platform.
 *
 * <p>Extracted from {@code FloorPatternRegistry} on the second use rather than the third: the
 * decisions below were all made once for floors and none of them are floor-specific, so copying
 * them per slot would be five chances to get the closed-schema nesting subtly different.</p>
 *
 * <h2>An unknown id is a LOAD ERROR</h2>
 * <p>Not a fallback to the plain treatment (Gottsch, 2026-08-26). A datapack naming
 * {@code yourmod:mosaic} with that mod absent would otherwise silently flatten every room in the
 * pack, with nothing logged. The error names the id and lists what <em>is</em> registered, because
 * "unknown type" with no inventory is a bad message when the cause is usually a typo or a
 * load-order slip.</p>
 *
 * <h2>Why {@code config} is nested</h2>
 * <p>{@link Codec#dispatch} merges the subtype's fields flat beside {@code type}, and a flat level
 * cannot be closed (#31): the enclosing record cannot declare the subtype's keys, because which
 * subtype it is has not been read yet; and the subtype cannot declare {@code type} or the
 * enclosing {@code SizeGate}'s keys, because they are not its. Nesting gives each level a key set
 * it fully knows, so both are closed and neither is loosened.</p>
 *
 * <p>{@code config} is optional, so a type with no fields of its own needs no empty object.</p>
 *
 * <h2>Why this is not a Forge registry</h2>
 * <p>A {@code RegistryBuilder} registry in {@code NewRegistryEvent} is the idiomatic answer and
 * only exists inside a mod-loading cycle. These codecs are exercised by a large headless test body
 * that boots nothing but {@code SharedConstants}/{@code Bootstrap}, where Forge's registries are
 * frozen &mdash; the same wall that stops this mod's blocks registering headlessly. A Forge
 * registry would leave every one of those tests unable to resolve a single type. What is given up
 * is the registry-dump command and sync/freeze semantics, neither of which anything here uses.</p>
 *
 * @param <T> the authored pattern interface for one slot
 */
public final class PatternTypeRegistry<T> {

    /**
     * The key naming the registered type, for every slot but one. Platform authors its dispatch
     * axis as {@code layout} instead, because it carries a SECOND discriminator called
     * {@code type} ("what the platform is") alongside "where it goes" -- so the key is a
     * constructor argument rather than a constant.
     */
    public static final String TYPE_KEY = "type";

    /** The key holding that type's own fields. */
    public static final String CONFIG_KEY = "config";

    private final String what;
    private final String typeKey;
    private final Function<T, MapCodec<? extends T>> codecOf;
    /** Insertion-ordered, so the "registered:" list in an error reads stably. */
    private final Map<ResourceLocation, MapCodec<? extends T>> types = new LinkedHashMap<>();

    /**
     * @param what    what a type of this kind is called, for error messages, e.g. "floor pattern"
     * @param codecOf recovers a value's own codec, so it can be written back out. An implementation
     *                must return the <em>same</em> instance it was registered with; that identity
     *                is how the id is recovered on encode.
     */
    public PatternTypeRegistry(String what, Function<T, MapCodec<? extends T>> codecOf) {
        this(what, TYPE_KEY, codecOf);
    }

    /**
     * @param typeKey the key naming the type, for a slot that cannot spell it {@code type} -- see
     *                {@link #TYPE_KEY}
     */
    public PatternTypeRegistry(String what, String typeKey,
                               Function<T, MapCodec<? extends T>> codecOf) {
        this.what = Objects.requireNonNull(what, "what");
        this.typeKey = Objects.requireNonNull(typeKey, "typeKey");
        this.codecOf = Objects.requireNonNull(codecOf, "codecOf");
    }

    /**
     * Registers a type. Call from your mod's common setup, before any datapack load.
     *
     * @throws IllegalStateException if {@code id} is taken &mdash; silently replacing somebody
     *                               else's pattern would change how their dungeons look with no
     *                               indication of why.
     */
    public synchronized void register(ResourceLocation id, MapCodec<? extends T> codec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        MapCodec<? extends T> existing = types.putIfAbsent(id, codec);
        if (existing != null && existing != codec) {
            throw new IllegalStateException(what + " type already registered: " + id);
        }
    }

    public synchronized Optional<MapCodec<? extends T>> get(ResourceLocation id) {
        return Optional.ofNullable(types.get(id));
    }

    /** Every registered id, for error messages and for tests asserting the shipped set. */
    public synchronized Set<ResourceLocation> ids() {
        return Set.copyOf(types.keySet());
    }

    public synchronized boolean isEmpty() {
        return types.isEmpty();
    }

    private synchronized Optional<ResourceLocation> idOf(MapCodec<? extends T> codec) {
        return types.entrySet().stream()
                .filter(entry -> entry.getValue() == codec)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** {@code type} + {@code config}, for embedding flat beside a {@code SizeGate}. */
    public MapCodec<T> mapCodec() {
        return new MapCodec<>() {
            @Override
            public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
                O typeValue = input.get(typeKey);
                if (typeValue == null) {
                    return DataResult.error(() -> what + " has no '" + typeKey + "'");
                }
                return ResourceLocation.CODEC.parse(ops, typeValue)
                        .flatMap(PatternTypeRegistry.this::lookup)
                        .flatMap(codec -> decodeConfig(ops, input, codec));
            }

            @SuppressWarnings("unchecked")
            @Override
            public <O> RecordBuilder<O> encode(T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
                MapCodec<? extends T> own = codecOf.apply(input);
                RecordBuilder<O> builder = prefix.add(typeKey, idOf(own)
                        .map(id -> ResourceLocation.CODEC.encodeStart(ops, id))
                        .orElseGet(() -> DataResult.error(() ->
                                what + " codec is not registered, so it cannot be written out")));
                return builder.add(CONFIG_KEY, ((MapCodec<T>) own).codec().encodeStart(ops, input));
            }

            @Override
            public <O> Stream<O> keys(DynamicOps<O> ops) {
                return Stream.of(ops.createString(typeKey), ops.createString(CONFIG_KEY));
            }
        };
    }

    /** Standalone form, for a type authored as a whole object (e.g. nested in a composite). */
    public Codec<T> codec() {
        return mapCodec().codec();
    }

    /**
     * Decodes {@code config} with the type's own codec, closing it so a stray key inside is an
     * error too. An absent {@code config} decodes against an empty map: that succeeds for a type
     * whose fields are all optional, and otherwise fails with that type's own missing-field
     * message, which is what the author needs to read.
     */
    private <O> DataResult<T> decodeConfig(
            DynamicOps<O> ops, MapLike<O> input, MapCodec<? extends T> codec) {
        O object = Optional.ofNullable(input.get(CONFIG_KEY)).orElseGet(ops::emptyMap);
        return ops.getMap(object)
                .flatMap(map -> Codecs.closedMap(codec).decode(ops, map))
                .map(value -> (T) value);
    }

    private DataResult<MapCodec<? extends T>> lookup(ResourceLocation id) {
        Optional<MapCodec<? extends T>> found = get(id);
        if (found.isPresent()) {
            return DataResult.success(found.get());
        }
        return DataResult.error(() -> "unknown " + what + " type '" + id + "'. Registered: "
                + ids().stream().map(ResourceLocation::toString).sorted()
                .reduce((a, b) -> a + ", " + b).orElse("(none)"));
    }
}
