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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Rolls how many cells a room slot gets, and hands them out one at a time, distinct.
 *
 * <p>Backlog #49. {@link RoomPropGenerator}, {@link RoomSpawnerGenerator} and
 * {@link RoomChestGenerator} each carried this same body: roll a count from an inclusive range,
 * clamp it to the number of eligible cells so a cramped room gets fewer of a thing rather than two
 * in one cell, then draw that many distinct cells. Three copies of a subtle loop in three files
 * nobody reads together is where drift stops being theoretical &mdash; an off-by-one in the live
 * range would have to be found and fixed three times.</p>
 *
 * <h2>Why it hands out cells rather than returning a list</h2>
 * <p>A "give me N cells" helper cannot express what {@link RoomSpawnerGenerator} does: when the mob
 * set will not resolve it <strong>consumes a draw but claims nothing</strong>, leaving the cell free
 * for a pot. The caller therefore has to see each cell before deciding, so this owns only the count
 * and the sequence and leaves every emit and every claim decision where it was.</p>
 *
 * <h2>The draw, and why it is written this way</h2>
 * <p>Swap-to-the-end: pick an index in the live range, swap the chosen cell out to the end of that
 * range, shrink the range by one. That is a partial Fisher-Yates, and it is what makes the draw
 * <em>without replacement</em> in one pass with no rejection loop &mdash; which matters because a
 * rejection loop would consume a seed-dependent number of draws and so make the stream depend on
 * what it happened to hit.</p>
 *
 * <h2>Determinism</h2>
 * <p>The randomness this consumes is exactly what the three copies consumed, in the same order: one
 * roll for the count (and none at all when the range is a single value), then one per cell handed
 * out. That is load-bearing rather than tidy &mdash; every generator downstream of a room slot draws
 * from the same {@link RandomSource}, so an extra or missing call here would relayout the contents
 * of every room in every existing world. {@code CellDrawTest} pins it against the literal old loop.</p>
 *
 * <p><strong>Construct only after the caller's own emptiness checks.</strong> All three callers
 * return early on an empty candidate list <em>before</em> rolling, so the count roll is not consumed
 * for a room that can hold nothing. Constructing this unconditionally would consume it and shift
 * everything after.</p>
 *
 * @author Mark Gottschling on Aug 22, 2026
 */
public final class CellDraw {

    /** The caller's candidates, copied, and permuted in place as cells are handed out. */
    private final List<Coords2D> candidates;
    private final RandomSource random;
    private final int count;
    private int drawn;

    private CellDraw(List<Coords2D> candidates, RandomSource random, int count) {
        this.candidates = candidates;
        this.random = random;
        this.count = count;
    }

    /**
     * Rolls the count and prepares the sequence. Consumes one {@code nextInt} when
     * {@code maxCount > minCount} and none when the range is a single value &mdash; the shape the
     * three copies had, kept because it is the shape existing worlds were generated with.
     *
     * <p>The candidate list is copied, so a caller may hand over a shared or cached list without
     * this permuting it underneath them.</p>
     *
     * @param candidates the cells this slot may use; may be empty, though callers are expected to
     *                   have returned before that (see the class note)
     * @param minCount   inclusive lower bound of the count roll
     * @param maxCount   inclusive upper bound; the caller's already-clamped maximum
     */
    public static CellDraw of(List<Coords2D> candidates, int minCount, int maxCount,
                              RandomSource random) {
        List<Coords2D> working = new ArrayList<>(candidates);
        int rolled = minCount + (maxCount > minCount ? random.nextInt(maxCount - minCount + 1) : 0);
        return new CellDraw(working, random, Math.min(rolled, working.size()));
    }

    /** How many cells this draw will hand out: the roll, clamped to the candidates available. */
    public int count() {
        return count;
    }

    public boolean hasNext() {
        return drawn < count;
    }

    /**
     * The next cell, distinct from every cell already handed out.
     *
     * @throws NoSuchElementException if called past {@link #count()}
     */
    public Coords2D next() {
        if (!hasNext()) {
            throw new NoSuchElementException("cell draw exhausted after " + count + " cells");
        }
        int live = candidates.size() - drawn;
        int pick = random.nextInt(live);
        Coords2D cell = candidates.get(pick);
        candidates.set(pick, candidates.get(live - 1));
        candidates.set(live - 1, cell);
        drawn++;
        return cell;
    }
}
