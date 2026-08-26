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
package mod.gottsch.forge.dungeons2.core.config.pillar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.GridPillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.IPillarPatternProvider;

import java.util.function.BiFunction;

/**
 * Where a pillar layout puts its columns &mdash; the <em>footprint</em>, and nothing about what
 * they are built from.
 *
 * <h2>Why the split falls here and not where the floor's did</h2>
 * <p>A floor pattern owns its own blocks, so {@code FloorPattern} absorbed everything and
 * {@code FloorPatternEntry} became {@code (pattern, gate)}. A pillar cannot: a layout is a bare
 * footprint, and the blocks travel alongside it to draw time on the authored entry, so that the
 * per-row defaulting ({@code baseBlock} falling back to {@code block}, and so on) stays in the one
 * place that defines it &mdash; see {@code PillarLayout}. So {@link PillarPatternEntry.PillarEntry}
 * keeps every <em>material</em> field and this interface owns only the <em>geometry</em>.</p>
 *
 * <h2>The built-in three all take the same two knobs</h2>
 * <p>{@code grid}, {@code colonnade} and {@code quartet} are each {@code (spacing, inset)}, which
 * is the honest reason the flat-record complaint that drove the floor registry does not apply here:
 * there were no per-type fields to untangle. What the registry buys pillars is the other half
 * &mdash; a third-party layout can register {@code yourmod:spiral} and declare a radius of its own,
 * which the closed {@code type} switch could never allow. {@link #RHYTHM} is shared by the three so
 * that agreement stays one declaration rather than three copies.</p>
 */
public interface PillarLayoutPattern {

    /**
     * The two knobs every built-in layout takes: {@code spacing} between columns and {@code inset}
     * from the wall. Declared once and reused, so the three cannot drift apart.
     */
    static <P extends PillarLayoutPattern> MapCodec<P> RHYTHM(BiFunction<Integer, Integer, P> factory,
                                                              java.util.function.ToIntFunction<P> spacing,
                                                              java.util.function.ToIntFunction<P> inset) {
        return Codecs.closedMap(RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codecs.strictOptionalFieldOf(Codec.intRange(2, Integer.MAX_VALUE), "spacing",
                        GridPillarPatternProvider.DEFAULT_SPACING).forGetter(spacing::applyAsInt),
                Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                        GridPillarPatternProvider.DEFAULT_INSET).forGetter(inset::applyAsInt)
        ).apply(instance, factory::apply)));
    }

    /**
     * This layout's own codec, as registered. An implementation must return the <em>same</em>
     * instance it was registered with; that identity is how the id is recovered on encode.
     */
    MapCodec<? extends PillarLayoutPattern> codec();

    /** The provider that decides which cells carry a column. */
    IPillarPatternProvider provider();
}
