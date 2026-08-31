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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.CentrePillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.GridPillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.IPillarPatternProvider;

/**
 * <strong>Exactly one</strong> column, at the room's centre. A single pier.
 *
 * <h2>Why this is not a grid or a quartet tuned small</h2>
 * <p>Both of those <em>can</em> land on one column, and that is the problem: it is incidental. A
 * grid produces one when the interior is too small to carry two at its {@code spacing}, and a
 * quartet's square collapses when the room cannot hold it at {@code inset} &mdash; so "a single
 * pier" is a thing that happens to some rooms rather than a thing an author can ask for. Every
 * other layout answers "how are the columns arranged"; this one answers "there is one column", and
 * the count is the guarantee. It does not become two in a bigger room.</p>
 *
 * <h2>It takes {@code inset} and NOT {@code spacing}</h2>
 * <p>The other three share {@link PillarLayoutPattern#RHYTHM} because they are each
 * {@code (spacing, inset)}. A lone column has nothing to be spaced from, so a {@code spacing} here
 * would be a field that reads as meaningful and does nothing &mdash; the exact class of silent
 * no-op the closed schema (#31) exists to reject. Authoring one is a load error naming the key.</p>
 *
 * <p>{@code inset} keeps its meaning, but acts <strong>only as a veto</strong>: a room whose
 * interior cannot keep that much clearance draws no column at all rather than putting a pier where
 * a player walks along the wall. It cannot move the column, because the centre is the centre.</p>
 *
 * <h2>Where the centre falls in an even-sided room</h2>
 * <p>{@code (interior - 1) / 2} on each axis, so an even interior takes the lower of the two middle
 * cells. Rooms are odd-sided by convention and the question does not usually arise; an authored
 * even-sided room is legal, and this is what it gets. See {@code CentrePillarPatternProvider}.</p>
 *
 * @author Mark Gottschling on Aug 30, 2026
 */
public record CentrePillarLayout(int inset) implements PillarLayoutPattern {

    public static final String NAME = "centre";

    /** The US spelling, registered over the same codec. Follows {@code CentreCeilingPattern}. */
    public static final String ALIAS = "center";

    /** A pier at the shared default clearance. */
    public CentrePillarLayout() {
        this(GridPillarPatternProvider.DEFAULT_INSET);
    }

    public static final MapCodec<CentrePillarLayout> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "inset",
                                    GridPillarPatternProvider.DEFAULT_INSET)
                            .forGetter(CentrePillarLayout::inset)
            ).apply(instance, CentrePillarLayout::new)));

    @Override
    public MapCodec<? extends PillarLayoutPattern> codec() {
        return CODEC;
    }

    @Override
    public IPillarPatternProvider provider() {
        return new CentrePillarPatternProvider(inset);
    }
}
