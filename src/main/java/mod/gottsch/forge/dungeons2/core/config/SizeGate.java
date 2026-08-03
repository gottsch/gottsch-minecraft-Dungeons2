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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A room-size range: {@code minHeight}/{@code minSize}/{@code maxHeight}/{@code maxSize}, the four
 * fields {@link RoomScheme} has always had, factored out so an <em>element slot</em> can carry them
 * too.
 *
 * <h2>The same four fields, at two levels, meaning different things</h2>
 * <ul>
 *   <li>On a <strong>scheme</strong>, the range decides whether that scheme <em>enters the roll</em>.
 *       Excluding it changes every other scheme's probability, because the weights are re-totalled
 *       over the survivors.</li>
 *   <li>On an <strong>element slot</strong>, the range decides whether that slot is <em>drawn</em>
 *       once the scheme has already won. It changes no probabilities at all: the scheme still fires
 *       at its full weight, it just renders one element fewer.</li>
 * </ul>
 *
 * <p>That second level is what collapses a pair of near-identical schemes into one. A bordered floor
 * that wants a crown moulding only where there is headroom for it used to need two schemes &mdash;
 * {@code andesite_border} and {@code crowned_andesite_border} &mdash; identical but for the wall slot
 * and a {@code minHeight}. With a gate on the wall slot it is one scheme whose crown drops out in
 * short rooms.</p>
 *
 * <p>It also removes an inconsistency that arrangement had. With two competing schemes, a tall room
 * would sometimes roll the <em>un</em>crowned one, so a fraction of tall bordered rooms came out
 * without trim for no reason anyone authored. Gated, a tall room gets the crown whenever the scheme
 * fires.</p>
 *
 * <h2>Semantics</h2>
 * <p>Bounds are <strong>inclusive</strong>. Heights are the room's full height (floor block through
 * ceiling block); sizes are the <em>smaller</em> of width and depth, so a long thin room is judged
 * by its narrow axis. Minimums default to 0; maximums absent mean unbounded &mdash; see
 * {@link RoomScheme} for why that is an {@link Optional} rather than a sentinel.</p>
 *
 * @author Mark Gottschling on Aug 2, 2026
 */
public record SizeGate(int minHeight, int minSize,
                       Optional<Integer> maxHeight, Optional<Integer> maxSize) {

    /** Fits every room. What an element slot with no gate fields authored decodes to. */
    public static final SizeGate UNBOUNDED = new SizeGate(0, 0, Optional.empty(), Optional.empty());

    /**
     * A {@link MapCodec} rather than a {@code Codec} so the four fields stay <strong>flat</strong>
     * in the JSON object that embeds it:
     *
     * <pre>"wall": { "minHeight": 6, "type": "courses", "courses": [ ... ] }</pre>
     *
     * <p>Nesting them under a {@code "requires": { ... }} key would separate a constraint from the
     * thing it constrains for no gain. Flat also means the element-level fields are spelled exactly
     * like the scheme-level ones, so there is one concept to learn rather than two.</p>
     */
    public static final MapCodec<SizeGate> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("minHeight", 0).forGetter(SizeGate::minHeight),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("minSize", 0).forGetter(SizeGate::minSize),
            Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "maxHeight")
                    .forGetter(SizeGate::maxHeight),
            Codecs.strictOptionalFieldOf(Codec.intRange(1, Integer.MAX_VALUE), "maxSize")
                    .forGetter(SizeGate::maxSize)
    ).apply(instance, SizeGate::new));

    /** Whether a room of these dimensions is inside this range. */
    public boolean fits(int width, int depth, int height) {
        int size = Math.min(width, depth);
        return height >= minHeight
                && size >= minSize
                && maxHeight.map(max -> height <= max).orElse(true)
                && maxSize.map(max -> size <= max).orElse(true);
    }

    /** Whether this gate constrains anything at all. */
    public boolean isUnbounded() {
        return minHeight == 0 && minSize == 0 && maxHeight.isEmpty() && maxSize.isEmpty();
    }

    /**
     * Whether two gates can ever be satisfied by the same room. Used by the shipped-content checks:
     * a rule like "pots and a floor-level projecting course must not coexist" only actually fires
     * when the two slots' ranges overlap, and gates make it possible for them not to.
     */
    public boolean overlaps(SizeGate other) {
        int lowHeight = Math.max(minHeight, other.minHeight);
        int highHeight = Math.min(maxHeight.orElse(Integer.MAX_VALUE),
                other.maxHeight.orElse(Integer.MAX_VALUE));
        int lowSize = Math.max(minSize, other.minSize);
        int highSize = Math.min(maxSize.orElse(Integer.MAX_VALUE),
                other.maxSize.orElse(Integer.MAX_VALUE));
        return lowHeight <= highHeight && lowSize <= highSize;
    }

    /**
     * Rejects an inverted range, naming where it was found. A codec cannot express "at least the
     * value of that other field", so this is called by the enclosing {@link RoomScheme}'s own
     * validation rather than from inside {@link #MAP_CODEC}.
     *
     * <p>An error rather than a clamp: a range that fits no room makes its slot (or its whole
     * scheme) draw nothing, anywhere, which at generation time is indistinguishable from content
     * that merely never came up.</p>
     */
    public DataResult<SizeGate> validate(String where) {
        if (maxHeight.isPresent() && maxHeight.get() < minHeight) {
            return DataResult.error(() -> where + ": maxHeight " + maxHeight.get()
                    + " is below minHeight " + minHeight + ", so it fits no room at all");
        }
        if (maxSize.isPresent() && maxSize.get() < minSize) {
            return DataResult.error(() -> where + ": maxSize " + maxSize.get()
                    + " is below minSize " + minSize + ", so it fits no room at all");
        }
        return DataResult.success(this);
    }
}
