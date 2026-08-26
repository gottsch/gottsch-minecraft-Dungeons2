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
package mod.gottsch.forge.dungeons2.core.config.floor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The open set of {@link FloorPattern} types, keyed by {@link ResourceLocation} so another mod can
 * add one and a datapack can name it as {@code "type": "yourmod:yourpattern"}.
 *
 * <h2>Why this is not a Forge registry</h2>
 * <p>A {@code RegistryBuilder} registry created in {@code NewRegistryEvent} is the idiomatic
 * answer, and it was the plan &mdash; but it only exists inside a running mod-loading cycle. The
 * pattern codecs are exercised by a large body of headless tests that boot nothing but
 * {@code SharedConstants} / {@code Bootstrap}, and under Forge the registries are frozen outside
 * that cycle (the same wall that stops this mod's blocks registering headlessly). A Forge registry
 * here would make every one of those tests unable to resolve a single pattern.</p>
 *
 * <p>So this is a plain static map with the same contract: string id in, codec out, unknown id is
 * an error. It gives a third-party mod exactly what the Forge registry would &mdash; a public
 * {@link #register} to call from its own setup &mdash; and costs only the registry-dump command and
 * the sync/freeze semantics, neither of which anything here uses.</p>
 *
 * <h2>An unknown id is a LOAD ERROR</h2>
 * <p>Not a fallback to plain floor (Gottsch, 2026-08-26). A datapack naming {@code yourmod:mosaic}
 * with that mod absent would otherwise turn every floor in the pack plain, with nothing logged
 * &mdash; the same silent degradation that let the inverted ceiling gates sit undetected for weeks.
 * The error names the id and lists what <em>is</em> registered, because "unknown pattern" with no
 * inventory is a bad message when the real cause is usually a typo or a load-order slip.</p>
 */
public final class FloorPatternRegistry {

    /** Insertion-ordered so the error message below lists ids in a stable, readable order. */
    private static final Map<ResourceLocation, MapCodec<? extends FloorPattern>> TYPES =
            new LinkedHashMap<>();

    private FloorPatternRegistry() {}

    /**
     * Registers a pattern type. Call from your mod's common setup, before any datapack load.
     *
     * @throws IllegalStateException if {@code id} is already taken &mdash; silently replacing
     *                               somebody else's pattern would change how their dungeons look
     *                               with no indication of why.
     */
    public static synchronized void register(ResourceLocation id, MapCodec<? extends FloorPattern> codec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        MapCodec<? extends FloorPattern> existing = TYPES.putIfAbsent(id, codec);
        if (existing != null && existing != codec) {
            throw new IllegalStateException("floor pattern type already registered: " + id);
        }
    }

    /** Convenience for this mod's own types. */
    static void register(String path, MapCodec<? extends FloorPattern> codec) {
        register(new ResourceLocation(Dungeons.MOD_ID, path), codec);
    }

    public static synchronized Optional<MapCodec<? extends FloorPattern>> get(ResourceLocation id) {
        return Optional.ofNullable(TYPES.get(id));
    }

    /** Every registered id, for error messages and for tests that assert the shipped set. */
    public static synchronized Set<ResourceLocation> ids() {
        return Set.copyOf(TYPES.keySet());
    }

    /** The id a pattern was registered under, for encoding. */
    static synchronized Optional<ResourceLocation> idOf(MapCodec<? extends FloorPattern> codec) {
        return TYPES.entrySet().stream()
                .filter(entry -> entry.getValue() == codec)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** The key naming the registered type. */
    public static final String TYPE_KEY = "type";

    /** The key holding that type's own fields. */
    public static final String CONFIG_KEY = "config";

    /**
     * {@code type} + {@code config}, dispatched by hand.
     *
     * <p>{@link Codec#dispatch} is the usual tool and is not usable here: it merges the subtype's
     * fields <em>alongside</em> {@code type} at one level, and a single flat level cannot be closed
     * (#31). The enclosing record cannot declare the subtype's keys, because which subtype it is
     * has not been read yet; and the subtype cannot declare {@code type} or the enclosing
     * {@code SizeGate}'s keys, because they are not its. Nesting under {@code config} gives each
     * level a key set it fully knows, so both can be closed with {@code Codecs#closedMap} and
     * neither has to be loosened.</p>
     *
     * <p>{@code config} is optional: a type with no fields ({@link PlainFloorPattern}) would
     * otherwise need an empty object written out to satisfy a required key.</p>
     */
    public static final MapCodec<FloorPattern> MAP_CODEC = new MapCodec<>() {
        @Override
        public <T> DataResult<FloorPattern> decode(DynamicOps<T> ops, MapLike<T> input) {
            T typeValue = input.get(TYPE_KEY);
            if (typeValue == null) {
                return DataResult.error(() -> "floor pattern has no '" + TYPE_KEY + "'");
            }
            return ResourceLocation.CODEC.parse(ops, typeValue)
                    .flatMap(FloorPatternRegistry::lookup)
                    .flatMap(codec -> decodeConfig(ops, input, codec));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> RecordBuilder<T> encode(FloorPattern input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            MapCodec<FloorPattern> codec = (MapCodec<FloorPattern>) input.codec();
            RecordBuilder<T> builder = prefix.add(TYPE_KEY,
                    idOf(input.codec())
                            .map(id -> ResourceLocation.CODEC.encodeStart(ops, id))
                            .orElseGet(() -> DataResult.error(() -> "floor pattern codec is not"
                                    + " registered, so it cannot be written out")));
            return builder.add(CONFIG_KEY, codec.codec().encodeStart(ops, input));
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(ops.createString(TYPE_KEY), ops.createString(CONFIG_KEY));
        }
    };

    /** Standalone form, for a pattern authored as a whole object. */
    public static final Codec<FloorPattern> CODEC = MAP_CODEC.codec();

    /**
     * Decodes the {@code config} object with the type's own codec, closing it as it goes so a
     * stray key inside is a load error too. An absent {@code config} decodes against an empty map,
     * which succeeds for a type whose fields are all optional and fails with that type's own
     * missing-field error otherwise -- which is the message the author needs.
     */
    private static <T> DataResult<FloorPattern> decodeConfig(
            DynamicOps<T> ops, MapLike<T> input, MapCodec<? extends FloorPattern> codec) {
        T configValue = input.get(CONFIG_KEY);
        MapCodec<? extends FloorPattern> closed = Codecs.closedMap(codec);
        T object = configValue == null ? ops.emptyMap() : configValue;
        return ops.getMap(object)
                .flatMap(map -> closed.decode(ops, map))
                .map(FloorPattern.class::cast);
    }

    private static DataResult<MapCodec<? extends FloorPattern>> lookup(ResourceLocation id) {
        Optional<MapCodec<? extends FloorPattern>> found = get(id);
        if (found.isPresent()) {
            return DataResult.success(found.get());
        }
        return DataResult.error(() -> "unknown floor pattern type '" + id + "'. Registered: "
                + ids().stream().map(ResourceLocation::toString).sorted()
                .reduce((a, b) -> a + ", " + b).orElse("(none)"));
    }

    /**
     * Registers this mod's own types. Idempotent, and called from both {@code Registration.init}
     * and the static initializer of the codec's users, because a datapack can be parsed by a test
     * that never runs mod setup.
     */
    public static synchronized void registerBuiltIns() {
        if (!TYPES.isEmpty()) {
            return;
        }
        register(PlainFloorPattern.NAME, PlainFloorPattern.CODEC);
        register(BorderFloorPattern.NAME, BorderFloorPattern.CODEC);
        register(CheckerboardFloorPattern.NAME, CheckerboardFloorPattern.CODEC);
        register(SpeckleFloorPattern.NAME, SpeckleFloorPattern.CODEC);
        register(CrossFloorPattern.NAME, CrossFloorPattern.CODEC);
        register(SpokesFloorPattern.NAME, SpokesFloorPattern.CODEC);
        register(CompositeFloorPattern.NAME, CompositeFloorPattern.CODEC);
    }

    static {
        registerBuiltIns();
    }
}
