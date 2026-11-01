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
package mod.gottsch.forge.dungeons2.core.config.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.IPillarPatternProvider;

import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Where a room's platforms go. The {@code platforms} slot's dispatch axis is authored as
 * {@code layout}, not {@code type}.
 *
 * <h2>Why this slot has two discriminators</h2>
 * <p>A platform entry says <em>what</em> it is ({@code type}, currently only {@code dais}) and
 * <em>where</em> the copies go ({@code layout}). That split is deliberate and predates this change
 * &mdash; it is what makes "a brazier in every corner" and "a brazier on a central platform" one
 * feature instead of two. Only {@code layout} was ever a {@code switch}, so only {@code layout}
 * becomes a registry; {@code type} keeps its own meaning and is validated separately.</p>
 *
 * <h2>{@code size} stays on the entry, {@code inset} moves here</h2>
 * <p>{@code inset} is pure placement &mdash; how far in from the wall the copies sit &mdash; so it
 * belongs to the layout. {@code size} is the <em>dais's own side length</em>, a property of the
 * thing being placed rather than of where it goes, so it stays on the entry and is handed to
 * {@link #provider(int)} instead. The repeating layouts derive their spacing from it; {@code
 * centre} and {@code corners} ignore it entirely, which is exactly the asymmetry that would be
 * hidden if it lived in here.</p>
 *
 * <h2>These reuse the pillar providers, and the registries still stay separate</h2>
 * <p>{@code grid}, {@code quartet} and {@code colonnade} build the very same provider classes as
 * the {@code pillars} slot. Sharing {@link
 * mod.gottsch.forge.dungeons2.core.config.pillar.PillarLayoutRegistry} was considered and dropped:
 * a pillar layout is authored with {@code spacing} (how far apart the columns are) and a platform
 * with {@code size} (how big each dais is), and one registry would have to force one vocabulary on
 * both, making the JSON lie in whichever slot lost. The cost is that a third-party layout must be
 * registered to each slot it wants to serve, which is honest: it has to decide what its config
 * words mean in each.</p>
 */
public interface PlatformLayoutPattern {

    /** The default: platforms sit one cell in from the wall. */
    int DEFAULT_INSET = 1;

    /**
     * The single knob every built-in layout takes. Declared once and reused so the five cannot
     * drift apart.
     */
    static <P extends PlatformLayoutPattern> MapCodec<P> INSET(IntFunction<P> factory,
                                                               ToIntFunction<P> inset) {
        return Codecs.closedMap(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                        DEFAULT_INSET).forGetter(inset::applyAsInt)
        ).apply(instance, factory::apply)));
    }

    /**
     * This layout's own codec, as registered. An implementation must return the <em>same</em>
     * instance it was registered with; that identity is how the id is recovered on encode.
     */
    MapCodec<? extends PlatformLayoutPattern> codec();

    /**
     * The provider deciding which cells carry a platform.
     *
     * @param size the dais's side length, from the entry. The repeating layouts space their copies
     *             by {@code size + 1} so two adjacent daises never touch; {@code centre} and
     *             {@code corners} place a fixed number and ignore it.
     */
    IPillarPatternProvider provider(int size);
}
