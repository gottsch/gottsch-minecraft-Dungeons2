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
package mod.gottsch.forge.dungeons2.core.config.pit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.IPitShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.InsetPitShapeProvider;

/**
 * The interior sunk but for a walkway {@code inset} cells wide around it &mdash; a sunken court
 * with a ledge, terraced inward.
 *
 * <p>The inset starts at <strong>1</strong>, not 0: an inset of 0 sinks the cells in front of the
 * doorways, and a doorway opening onto a step down is a threshold nobody authored. Nothing else
 * about the pit slot knows where the doors are, so the walkway is what keeps them ordinary.</p>
 */
public record InsetPitShape(int inset, int depth) implements PitShapePattern {

    public static final String NAME = "inset";

    public static final int DEFAULT_INSET = 1;
    public static final int DEFAULT_DEPTH = 2;

    public InsetPitShape() {
        this(DEFAULT_INSET, DEFAULT_DEPTH);
    }

    public static final MapCodec<InsetPitShape> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "inset",
                            DEFAULT_INSET).forGetter(InsetPitShape::inset),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 24), "depth", DEFAULT_DEPTH)
                            .forGetter(InsetPitShape::depth)
            ).apply(instance, InsetPitShape::new)));

    @Override
    public MapCodec<? extends PitShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPitShapeProvider provider() {
        return new InsetPitShapeProvider(inset, depth);
    }
}
