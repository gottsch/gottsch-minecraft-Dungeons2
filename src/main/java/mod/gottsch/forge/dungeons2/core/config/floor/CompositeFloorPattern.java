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
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.CompositeFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IFloorOverlayGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An ordered (not weighted) list of patterns, applied in sequence: the first is the base full fill,
 * every one after it is layered on top.
 *
 * <p>Only an overlay-capable pattern can be a layer &mdash; {@code border}, {@code cross} and
 * {@code spokes}, i.e. the ones whose provider implements {@link IFloorOverlayGenerator}. Anything
 * else in an overlay slot is silently skipped. That is unchanged behaviour, and it is the one place
 * this package still degrades silently rather than erroring: whether a pattern can overlay is a
 * property of its <em>provider</em>, which the codec cannot see at decode time. A registered
 * third-party pattern is in exactly the same position.</p>
 *
 * <p>An empty {@code generators} list degrades to plain floor.</p>
 */
public record CompositeFloorPattern(List<FloorPattern> generators) implements FloorPattern {

    public static final String NAME = "composite";

    /**
     * Self-referential, so the element codec has to be deferred: a plain {@code () ->
     * FloorPatternRegistry.CODEC} would be fine at runtime but javac rejects the forward
     * self-reference, and this project's {@code datafixerupper} predates
     * {@code Codec.lazyInitialized}. Same workaround the old flat record used for the same reason.
     */
    public static final MapCodec<CompositeFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    lazy(() -> FloorPatternRegistry.CODEC).listOf()
                            .fieldOf("generators").forGetter(CompositeFloorPattern::generators)
            ).apply(instance, CompositeFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        if (generators.isEmpty()) {
            return PlainFloorPattern.INSTANCE.generator(config);
        }
        IDungeonFloorGenerator base = generators.get(0).generator(config);
        List<IFloorOverlayGenerator> overlays = new ArrayList<>();
        for (int i = 1; i < generators.size(); i++) {
            if (generators.get(i).generator(config) instanceof IFloorOverlayGenerator overlay) {
                overlays.add(overlay);
            }
        }
        return new CompositeFloorPatternProvider(base, overlays);
    }

    private static <A> Codec<A> lazy(Supplier<Codec<A>> delegate) {
        return new Codec<>() {
            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                return delegate.get().decode(ops, input);
            }

            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return delegate.get().encode(input, ops, prefix);
            }
        };
    }
}
