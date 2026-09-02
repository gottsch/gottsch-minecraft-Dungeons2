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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.partition.CornerPartitionShape;
import mod.gottsch.forge.dungeons2.core.config.partition.PartitionShapePattern;
import mod.gottsch.forge.dungeons2.core.config.partition.PartitionShapeRegistry;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The {@code partition} scheme slot: a <strong>wall inside the room</strong>. Backlog #74.
 *
 * <p>Iron bars across a corner make a prison cell; a low masonry run across a strip makes a pen or
 * an antechamber. This is the first slot that changes the shape of the space a player moves through,
 * rather than the surfaces around it.</p>
 *
 * <h2>Why it is not the {@code props} slot</h2>
 * <p>A prison cell is not a room with barrels in it; it is a room with a wall inside it. #73 places
 * <em>objects</em> in cells and cares only that each is standing on floor. This needs a line, an
 * axis, a way through, and a height &mdash; and every cell it claims has to reach the {@code taken}
 * set, or a pot spawns inside the bars and a column grows through them.</p>
 *
 * <h2>The room stops being convex, and it matters less than it looks like it should</h2>
 * <p>This is the first thing in the room pipeline to make a room non-convex, and the props, the
 * columns, the daises, the chests and the spawner all pick cells assuming one open volume. None of
 * them is <strong>wrong</strong> afterwards, and the reason is that <em>the shape always cuts a
 * gap</em>: an enclosure is permeable, so a chest inside the cell is a chest behind a door, not a
 * chest nobody can reach. That is content, and rather good content.</p>
 *
 * <p>The one case that genuinely is a fault is a room whose DOORWAY opens straight into the cage
 * &mdash; a corridor delivering the player inside the cell. {@code RoomPartitionGenerator} refuses
 * to build at all in that room, which is why {@code PartitionPlan} reports its enclosed cells.</p>
 *
 * <p>What the partition claims, everything after it avoids: it runs before the volume slots so a
 * column cannot grow through the bars, and its cells reach the {@code taken} set so a pot cannot
 * spawn inside them.</p>
 *
 * <h2>The way through is a gap, and optionally a door</h2>
 * <p>The shape always cuts one; see {@code IPartitionShapeProvider}. Left alone it is open air,
 * which is a doorway. {@code gap_block} hangs something in it, and is written on the two rows a
 * player walks through with vanilla's {@code half} property set &mdash; so a door works, and a
 * two-high grate or gate works, and anything else gets a two-high panel and ignores {@code half}.
 * A gap taller than two is deliberately not fillable: past head height the block is scenery, and
 * scenery on a partition is the partition's own material.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public record PartitionPatternEntry(PartitionShapePattern shape, String block,
                                    Optional<String> gapBlock, int height, SizeGate gate) {

    /**
     * Three. Head height plus one: tall enough that a player cannot jump it, which is the whole
     * point of a cell, and short enough to fit the shortest room the taper produces.
     */
    public static final int DEFAULT_HEIGHT = 3;

    /** An ungated partition of the default shape and height. */
    public PartitionPatternEntry(String block) {
        this(new CornerPartitionShape(), block, Optional.empty(), DEFAULT_HEIGHT,
                SizeGate.UNBOUNDED);
    }

    /** An ungated partition of a given shape. */
    public PartitionPatternEntry(PartitionShapePattern shape, String block) {
        this(shape, block, Optional.empty(), DEFAULT_HEIGHT, SizeGate.UNBOUNDED);
    }

    /**
     * The same record with its schema left OPEN, for {@link SlotOptions}: an option writes a
     * {@code weight} key alongside this record's own keys, so the closed check has to be re-imposed
     * one level up, over the union of both key sets, rather than here.
     */
    public static final MapCodec<PartitionPatternEntry> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
            // `type` + `config`, dispatched over the partition shape registry. An unregistered id
            // is a LOAD ERROR naming what is registered, not a room that quietly has no partition.
            PartitionShapeRegistry.MAP_CODEC.forGetter(PartitionPatternEntry::shape),
            // REQUIRED, unlike the pit's floor_block. A pit with no material continues the floor
            // around it, which is a sensible thing to mean; a partition with no material is not a
            // partition, so there is nothing for a default to fall back to.
            Codecs.BLOCK_ID_OR_ROLE.fieldOf("block").forGetter(PartitionPatternEntry::block),
            Codecs.strictOptionalFieldOf(Codecs.BLOCK_ID_OR_ROLE, "gap_block")
                    .forGetter(PartitionPatternEntry::gapBlock),
            // Capped at 8 rather than at the tallest room: the taper (#51) gives a room between 3
            // and 8 interior rows, so 8 is "as tall as the room allows" and anything above it is a
            // number that can never mean more than 8 does. The generator clamps to the room too --
            // a codec cannot see the room, the same wall ChestConfig#clampedMaxCount runs into.
            Codecs.strictOptionalFieldOf(Codec.intRange(1, 8), "height", DEFAULT_HEIGHT)
                    .forGetter(PartitionPatternEntry::height),
            SizeGate.MAP_CODEC.forGetter(PartitionPatternEntry::gate)
    ).apply(instance, PartitionPatternEntry::new));

    public static final Codec<PartitionPatternEntry> CODEC = Codecs.closed(MAP_CODEC);

    /**
     * This partition with its block, its gap block and its shape's blocks resolved. #65's second
     * half; neither shipped shape names a block, so in practice this is the entry's own two fields.
     */
    public PartitionPatternEntry withRoles(UnaryOperator<String> resolver) {
        PartitionShapePattern resolvedShape = shape.withRoles(resolver);
        String resolvedBlock = Codecs.resolveRole(block, resolver);
        Optional<String> resolvedGap = Codecs.resolveRole(gapBlock, resolver);
        if (resolvedShape == shape && resolvedBlock.equals(block) && resolvedGap.equals(gapBlock)) {
            return this;
        }
        return new PartitionPatternEntry(resolvedShape, resolvedBlock, resolvedGap, height, gate);
    }

    /**
     * How tall this partition may actually stand in a room of this height.
     *
     * <p>A room {@code height} blocks tall has its floor at row 0 and its ceiling at row
     * {@code height - 1}, so there are {@code height - 2} rows of air between them. A partition that
     * reached the ceiling would read as a structural wall rather than as a screen inside a room, and
     * a partition taller than the room would overwrite the ceiling itself.</p>
     */
    public int heightWithin(int roomHeight) {
        return Math.max(0, Math.min(height, roomHeight - 2));
    }
}
