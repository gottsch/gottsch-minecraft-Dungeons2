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
package mod.gottsch.forge.dungeons2.core.config.partition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.IPartitionShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.StripPartitionShapeProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

import java.util.Optional;

/**
 * One straight run wall to wall, dividing the room in two, with a way through it.
 *
 * <p>The other half of what #74 asked for: "across a corner <em>or a strip of the room</em>". Where
 * {@link CornerPartitionShape} makes a cell, this makes an <strong>antechamber</strong> &mdash; you
 * come in, and the rest of the room is on the far side of a grate.</p>
 *
 * <p>{@code offset} is the index of the run along the axis it divides, counted from the interior's
 * low edge; absent means the middle. It is <strong>clamped</strong> rather than rejected when it
 * would put the run against a wall, because the same scheme has to stay valid in a room the author
 * never measured &mdash; and keeping the partition is closer to what they asked for than dropping
 * it. A run hard against a wall is not a partition, it is a second skin on the wall.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public record StripPartitionShape(Axis axis, Optional<Integer> offset)
        implements PartitionShapePattern {

    public static final String NAME = "strip";

    /**
     * Which way the run lies.
     *
     * <p>{@link #ANY} is the default and is a roll, for {@code Corner.ANY}'s reason: a procedural
     * room has no reason to prefer one, and two schemes differing only in an axis is file growth for
     * nothing.</p>
     */
    public enum Axis implements StringRepresentable {
        /** Parallel to X: the run varies in x at a fixed z, dividing the room north from south. */
        X("x", true),
        /** Parallel to Z: dividing the room east from west. */
        Z("z", false),
        /** Rolled per room. Its own {@code alongX} is never read; see {@link #resolve}. */
        ANY("any", true);

        private static final Axis[] DEFINITE = {X, Z};

        private final String name;
        private final boolean alongX;

        Axis(String name, boolean alongX) {
            this.name = name;
            this.alongX = alongX;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** This axis, or one of the two drawn from {@code random} when it is {@link #ANY}. */
        public Axis resolve(RandomSource random) {
            return this == ANY ? DEFINITE[random.nextInt(DEFINITE.length)] : this;
        }

        public boolean alongX() {
            return alongX;
        }

        /** A failing codec rather than a lenient default; see {@code PropConfig.PropPlacement}. */
        public static final Codec<Axis> CODEC = StringRepresentable.fromEnum(Axis::values);
    }

    public StripPartitionShape() {
        this(Axis.ANY, Optional.empty());
    }

    public static final MapCodec<StripPartitionShape> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Axis.CODEC, "axis", Axis.ANY)
                            .forGetter(StripPartitionShape::axis),
                    // Optional rather than an int with a sentinel: 0 is a real index a reader might
                    // write, and "absent means the middle" cannot be spelled as a number at all
                    // without knowing the room -- which a codec never does.
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, 64), "offset")
                            .forGetter(StripPartitionShape::offset)
            ).apply(instance, StripPartitionShape::new)));

    @Override
    public MapCodec<? extends PartitionShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPartitionShapeProvider provider() {
        return new StripPartitionShapeProvider(axis, offset.orElse(null));
    }
}
