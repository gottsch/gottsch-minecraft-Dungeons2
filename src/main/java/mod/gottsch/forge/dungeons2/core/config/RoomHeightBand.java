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
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * One entry in the room-height taper: the range of heights a room of a given footprint may have.
 * Backlog #51.
 *
 * <h2>What it replaced, and why the direction mattered</h2>
 * <p>{@code DungeonStackPlanner#pickRoomHeight} used to be
 * {@code min(5 + rand(6), max(width, depth))} &mdash; a cap that <em>rose</em> with the footprint,
 * so the only rooms that could be tall were the big ones. That is backwards. A big tall room is a
 * box; a small tall room is a shaft or a nave, and reads well. Measured over 76,285 procedural
 * rooms the old rule bit almost entirely on the 7x7 case (26.2% of rooms, capped to 7), and left
 * every room with a long side of 10+ free to roll the full range &mdash; including the 19x19 ones.
 * The taper inverts that: the ceiling <strong>falls</strong> as the footprint grows.</p>
 *
 * <h2>The roll is clamped, not re-rolled</h2>
 * <p>{@code pickRoomHeight} still draws exactly {@code 5 + random.nextInt(6)} and then clamps the
 * result into the matched band. Same reasoning as #50's world-bottom clamp: one identical draw
 * consumes an identical amount of the planner's stream, so the maze, the footprints and the
 * corridors of every existing seed stay byte-identical and only the heights move. Re-rolling
 * inside the band would have relayouted every dungeon in every existing world.</p>
 *
 * <p>A consequence worth knowing when reading the height histogram: heights <em>pile up</em> at a
 * band's edges, because every roll above {@code maxHeight} lands on {@code maxHeight}. That is not
 * a bug and it is not new &mdash; the old {@code min(..., max(width, depth))} piled at the long
 * side in exactly the same way.</p>
 *
 * <h2>Matching</h2>
 * <p>Bands are matched <strong>in order</strong> against the room's long side
 * ({@code max(width, depth)}), first match wins. {@link #maxLongSide} absent means "everything
 * larger", so the list's last entry must be open-ended and no earlier entry may be &mdash; see
 * {@link #validate}. That is what makes the table total: there is no footprint the planner can
 * hand it that falls off the end.</p>
 *
 * @author Mark Gottschling on Aug 20, 2026
 */
public record RoomHeightBand(Optional<Integer> maxLongSide, int minHeight, int maxHeight) {

    /**
     * Bounds on the authored numbers. A room's height counts the floor block and the ceiling block,
     * so 3 is the shortest thing with an interior at all; the upper bound is the planner's
     * {@code floorHeight}, which no room may exceed without eating into the floor above &mdash; see
     * {@link #validateAgainstBudget}.
     */
    private static final int MIN_AUTHORABLE_HEIGHT = 3;
    private static final int MAX_AUTHORABLE_HEIGHT = 64;

    public static final Codec<RoomHeightBand> CODEC = Codecs.closed(
            RecordCodecBuilder.<RoomHeightBand>mapCodec(instance -> instance.group(
                    // Absent = open-ended. Deliberately not defaulted to Integer.MAX_VALUE: the
                    // list validation needs to tell "the author wrote a huge number" from "the
                    // author meant everything else", because only the second may repeat-terminate.
                    Codecs.strictOptionalFieldOf(Codec.intRange(1, 256), "maxLongSide")
                            .forGetter(RoomHeightBand::maxLongSide),
                    Codec.intRange(MIN_AUTHORABLE_HEIGHT, MAX_AUTHORABLE_HEIGHT).fieldOf("minHeight")
                            .forGetter(RoomHeightBand::minHeight),
                    Codec.intRange(MIN_AUTHORABLE_HEIGHT, MAX_AUTHORABLE_HEIGHT).fieldOf("maxHeight")
                            .forGetter(RoomHeightBand::maxHeight)
            ).apply(instance, RoomHeightBand::new)))
            // flatXmap rather than a throwing constructor: a DFU error result still carries a
            // PARTIAL value and RecordCodecBuilder calls the constructor to build it, so a throw
            // here escapes the reload as an exception instead of naming the file.
            .flatXmap(RoomHeightBand::checkRange, RoomHeightBand::checkRange);

    /** The whole table, validated for order and totality. See {@link #validate}. */
    public static final Codec<List<RoomHeightBand>> LIST_CODEC =
            CODEC.listOf().flatXmap(RoomHeightBand::validate, RoomHeightBand::validate);

    private static DataResult<RoomHeightBand> checkRange(RoomHeightBand band) {
        if (band.minHeight > band.maxHeight) {
            return DataResult.error(() -> "roomHeightBands: minHeight " + band.minHeight
                    + " is greater than maxHeight " + band.maxHeight);
        }
        return DataResult.success(band);
    }

    /**
     * A table is well-formed when it is non-empty, its {@code maxLongSide} values strictly
     * increase, and <strong>exactly one</strong> band &mdash; the last &mdash; is open-ended.
     *
     * <p>The totality rule is the one that earns its keep. An open-ended band in the middle makes
     * every band after it dead code that still loads cleanly, and a table with no open-ended band
     * at all leaves the largest rooms unmatched, which would have to fall back to something the
     * author never wrote. Both are silent in a way a wrong number is not.</p>
     */
    public static DataResult<List<RoomHeightBand>> validate(List<RoomHeightBand> bands) {
        if (bands.isEmpty()) {
            return DataResult.error(() -> "roomHeightBands: must declare at least one band");
        }
        int previous = 0;
        for (int i = 0; i < bands.size(); i++) {
            RoomHeightBand band = bands.get(i);
            boolean last = i == bands.size() - 1;
            if (band.maxLongSide.isEmpty()) {
                if (!last) {
                    final int at = i;
                    return DataResult.error(() -> "roomHeightBands: band " + at
                            + " omits maxLongSide, which matches every room and makes the "
                            + (bands.size() - at - 1) + " band(s) after it unreachable; only the "
                            + "last band may be open-ended");
                }
            } else {
                if (last) {
                    return DataResult.error(() -> "roomHeightBands: the last band must omit "
                            + "maxLongSide so every footprint matches something; it declares "
                            + band.maxLongSide.get());
                }
                int value = band.maxLongSide.get();
                if (value <= previous) {
                    final int at = i;
                    final int prior = previous;
                    return DataResult.error(() -> "roomHeightBands: band " + at + "'s maxLongSide "
                            + value + " does not exceed the previous band's " + prior
                            + "; bands are matched in order and must strictly increase");
                }
                previous = value;
            }
        }
        return DataResult.success(List.copyOf(bands));
    }

    /**
     * Trims a table to fit a floor of {@code floorHeight} blocks, returning it unchanged when it
     * already does.
     *
     * <h2>Clamp rather than reject</h2>
     * <p>The floor height is not visible from a datapack load &mdash; it is a different field, and
     * the codec has no way to reach it &mdash; so this is checked at the call site instead. The
     * first version of it <em>rejected</em> an oversized table and fell back to the shipped one,
     * which is wrong in the case that matters: lower {@code floorHeight} below 10 and the shipped
     * table does not fit either, so falling back to it hands the planner the very table just
     * refused. Clamping has no such hole &mdash; every band ends up inside the budget whatever the
     * budget is.</p>
     *
     * <p>{@code minHeight} is clamped too, so a band whose whole range sits above the budget
     * collapses to the budget rather than inverting.</p>
     */
    public static List<RoomHeightBand> clampToBudget(List<RoomHeightBand> bands, int floorHeight) {
        if (validateAgainstBudget(bands, floorHeight)) {
            return bands;
        }
        return bands.stream()
                .map(band -> new RoomHeightBand(band.maxLongSide,
                        Math.min(band.minHeight, floorHeight),
                        Math.min(band.maxHeight, floorHeight)))
                .toList();
    }

    /**
     * True if every band in the table fits inside a floor of {@code floorHeight} blocks. Not part
     * of the codec: the planner's floor height is not visible from a datapack load, and a band
     * taller than the budget would push a room's ceiling through the floor above it.
     *
     * @see #clampToBudget
     */
    public static boolean validateAgainstBudget(List<RoomHeightBand> bands, int floorHeight) {
        return bands.stream().allMatch(band -> band.maxHeight <= floorHeight);
    }

    /**
     * The band governing a room whose long side is {@code longSide}. First match wins; the last
     * band is open-ended, so this always returns something for a validated table.
     */
    public static RoomHeightBand forLongSide(List<RoomHeightBand> bands, int longSide) {
        for (RoomHeightBand band : bands) {
            if (band.maxLongSide.isEmpty() || longSide <= band.maxLongSide.get()) {
                return band;
            }
        }
        // Unreachable for a validated table; a caller that assembled one by hand gets the widest
        // band rather than an exception mid-plan.
        return bands.get(bands.size() - 1);
    }

    /** Clamps a rolled height into this band. See the class note on why it clamps. */
    public int clamp(int rolledHeight) {
        return Math.min(Math.max(rolledHeight, minHeight), maxHeight);
    }
}
