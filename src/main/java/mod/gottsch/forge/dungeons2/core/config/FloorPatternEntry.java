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
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPattern;
import mod.gottsch.forge.dungeons2.core.config.floor.FloorPatternRegistry;
import mod.gottsch.forge.dungeons2.core.config.floor.PlainFloorPattern;

/**
 * A floor treatment as authored in a slot: the {@link FloorPattern} itself, plus the
 * {@link SizeGate} deciding which rooms it is allowed to draw in.
 *
 * <h2>What this used to be</h2>
 * <p>One flat record carrying <em>every</em> pattern's fields at once &mdash; {@code inset},
 * {@code corner_block}, {@code edge_left_block}, {@code edge_right_block}, {@code primary_block},
 * {@code secondary_block}, {@code probability}, {@code thickness}, {@code spokes},
 * {@code generators} &mdash; with a {@code type} string that {@code FloorPatternSelector} switched
 * over, privately knowing which fields each type read. Every block slot had to be
 * {@code Optional}, because every other type's slots were absent by design, so a {@code speckle}
 * entry that forgot its base block degraded silently to plain floor. Writing {@code spokes} on a
 * {@code border} entry did nothing and said nothing.</p>
 *
 * <p>Now {@code type} names a registered {@link FloorPattern} and that pattern's own codec declares
 * exactly its own fields under {@code config}. A missing required block is a load error, a field
 * that belongs to a different pattern is a load error, and the set of patterns is open to other
 * mods. See {@link FloorPatternRegistry}.</p>
 *
 * <h2>{@code weight} is gone</h2>
 * <p>It was vestigial. It dates from when {@code FloorConfig} held a <em>weighted list</em> of
 * patterns; since the scheme migration a floor slot has been a single {@link java.util.Optional},
 * and no Java has read {@code FloorPatternEntry#weight} since. Nothing shipped authored it. Under
 * the closed schema it is now a load error, which is the correct outcome for a key that has not
 * meant anything for months.</p>
 */
public record FloorPatternEntry(FloorPattern pattern, SizeGate gate) {

    /** An ungated treatment -- drawn whenever its scheme is rolled. */
    public FloorPatternEntry(FloorPattern pattern) {
        this(pattern, SizeGate.UNBOUNDED);
    }

    /** The undecorated floor, ungated. */
    public static final FloorPatternEntry PLAIN = new FloorPatternEntry(PlainFloorPattern.INSTANCE);

    /**
     * {@code type} and {@code config} come from {@link FloorPatternRegistry#MAP_CODEC}; the four
     * {@link SizeGate} keys are embedded flat beside them, as everywhere else. Closed, so the key
     * set is exactly those six -- see {@code Codecs#closed}, and note that the gate's keys reach
     * the closed check for free because {@code RecordCodecBuilder} composes them into this record's
     * own {@code keys()}.
     */
        /**
     * The same record with its schema left OPEN, for {@link SlotOptions}: an option writes a
     * {@code weight} key alongside this record's own keys, so the closed check has to be re-imposed
     * one level up, over the union of both key sets, rather than here.
     */
    public static final MapCodec<FloorPatternEntry> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    FloorPatternRegistry.MAP_CODEC.forGetter(FloorPatternEntry::pattern),
                    SizeGate.MAP_CODEC.forGetter(FloorPatternEntry::gate)
            ).apply(instance, FloorPatternEntry::new));

    public static final Codec<FloorPatternEntry> CODEC = Codecs.closed(MAP_CODEC);
}
