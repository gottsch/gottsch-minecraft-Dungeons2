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
     * indistinguishable from an absent one in any scheme without a closed schema, so that still
     * silently takes the default.</p>
     */
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
}
