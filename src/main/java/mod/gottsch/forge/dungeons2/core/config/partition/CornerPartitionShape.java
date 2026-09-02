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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.CornerPartitionShapeProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.IPartitionShapeProvider;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * A rectangular cell fenced off in one corner of the room, by an L of two axis-aligned runs.
 *
 * <p>{@code width} and {@code depth} are the <strong>fenced cell's</strong> extents, not the run's:
 * a 3&times;3 cell is nine cells of floor behind seven cells of bars. The room needs those, plus the
 * run itself, plus at least one cell on the far side &mdash; a partition with nothing on the other
 * side of it is just a smaller room &mdash; so a cell too big for the room draws nothing rather than
 * being squeezed.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public record CornerPartitionShape(int width, int depth, Corner corner)
        implements PartitionShapePattern {

    public static final String NAME = "corner";

    /** Three: the smallest cell a player can stand in and turn round in without being in a doorway. */
    public static final int DEFAULT_SIZE = 3;

    /**
     * Which corner the cell sits in.
     *
     * <p>{@link #ANY} is the default and is a <strong>roll</strong>, not a fifth corner: a
     * procedural room has no reason to prefer one, and four schemes that differ only in a compass
     * point is exactly the file growth #65 exists to stop. It is spelled out rather than left
     * implicit because "absent means random" and "absent means north-west" are both defensible and
     * a reader cannot tell from a codec which one it chose.</p>
     */
    public enum Corner implements StringRepresentable {
        NORTH_WEST("north_west", true, true),
        NORTH_EAST("north_east", false, true),
        SOUTH_WEST("south_west", true, false),
        SOUTH_EAST("south_east", false, false),
        /** Rolled per room. Its own {@code west}/{@code north} are never read; see {@link #resolve}. */
        ANY("any", true, true);

        private static final Corner[] DEFINITE =
                {NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST};

        private final String name;
        private final boolean west;
        private final boolean north;

        Corner(String name, boolean west, boolean north) {
            this.name = name;
            this.west = west;
            this.north = north;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /**
         * This corner, or one of the four drawn from {@code random} when it is {@link #ANY}.
         *
         * <p>Drawn from a definite array rather than {@code values()[random.nextInt(4)]}: the two
         * are the same today only because {@code ANY} happens to be declared last, and a reordering
         * of the enum would silently start rolling {@code ANY} itself.</p>
         */
        public Corner resolve(RandomSource random) {
            return this == ANY ? DEFINITE[random.nextInt(DEFINITE.length)] : this;
        }

        public boolean west() {
            return west;
        }

        public boolean north() {
            return north;
        }

        /** A failing codec rather than a lenient default; see {@code PropConfig.PropPlacement}. */
        public static final Codec<Corner> CODEC = StringRepresentable.fromEnum(Corner::values);
    }

    public CornerPartitionShape() {
        this(DEFAULT_SIZE, DEFAULT_SIZE, Corner.ANY);
    }

    public static final MapCodec<CornerPartitionShape> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 32), "width", DEFAULT_SIZE)
                            .forGetter(CornerPartitionShape::width),
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 32), "depth", DEFAULT_SIZE)
                            .forGetter(CornerPartitionShape::depth),
                    Codecs.strictOptionalFieldOf(Corner.CODEC, "corner", Corner.ANY)
                            .forGetter(CornerPartitionShape::corner)
            ).apply(instance, CornerPartitionShape::new)));

    @Override
    public MapCodec<? extends PartitionShapePattern> codec() {
        return CODEC;
    }

    @Override
    public IPartitionShapeProvider provider() {
        return new CornerPartitionShapeProvider(width, depth, corner);
    }
}
