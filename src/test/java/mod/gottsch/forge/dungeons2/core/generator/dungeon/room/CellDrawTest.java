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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The extracted cell draw, backlog #49.
 *
 * <p>The load-bearing test is {@link #matchesTheLoopItReplaced}. The three generators this was
 * pulled out of all draw from the same {@link RandomSource} the rest of a room's contents draw from,
 * so consuming one call more or fewer &mdash; or in a different order &mdash; would relayout every
 * room in every existing world. The old loop is therefore reproduced <em>literally</em> here and the
 * two are run against the same seed.</p>
 *
 * <p>Note what that test is careful about: the two sides must be genuinely different code paths.
 * {@link #oldLoop} is a transcription of the deleted body, not a call into {@code CellDraw}, which is
 * what makes the comparison mean anything.</p>
 */
class CellDrawTest {

    private static List<Coords2D> cells(int n) {
        List<Coords2D> cells = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cells.add(new Coords2D(i, i * 2));
        }
        return cells;
    }

    /**
     * The body that lived in RoomPropGenerator / RoomSpawnerGenerator / RoomChestGenerator, copied
     * verbatim from before the extraction. Do not refactor this into a call to {@code CellDraw} —
     * it exists precisely to be the other side of the comparison.
     */
    private static List<Coords2D> oldLoop(List<Coords2D> candidates, int min, int max,
                                          RandomSource random) {
        int count = min + (max > min ? random.nextInt(max - min + 1) : 0);
        count = Math.min(count, candidates.size());

        List<Coords2D> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int pick = random.nextInt(candidates.size() - i);
            Coords2D cell = candidates.get(pick);
            candidates.set(pick, candidates.get(candidates.size() - 1 - i));
            candidates.set(candidates.size() - 1 - i, cell);
            drawn.add(cell);
        }
        return drawn;
    }

    private static List<Coords2D> newDraw(List<Coords2D> candidates, int min, int max,
                                          RandomSource random) {
        CellDraw draw = CellDraw.of(candidates, min, max, random);
        List<Coords2D> drawn = new ArrayList<>();
        while (draw.hasNext()) {
            drawn.add(draw.next());
        }
        return drawn;
    }

    /**
     * Same cells, same order, and the random source left in the same state — swept over seeds and
     * over ranges, including the single-value range that must consume NO count roll and the
     * over-subscribed range that must clamp.
     */
    @Test
    void matchesTheLoopItReplaced() {
        int[][] ranges = {{0, 0}, {1, 1}, {0, 3}, {2, 5}, {1, 40}, {12, 12}};
        for (long seed = 0; seed < 200; seed++) {
            for (int[] range : ranges) {
                RandomSource a = RandomSource.create(seed);
                RandomSource b = RandomSource.create(seed);

                List<Coords2D> expected = oldLoop(cells(9), range[0], range[1], a);
                List<Coords2D> actual = newDraw(cells(9), range[0], range[1], b);

                assertEquals(expected, actual,
                        "seed " + seed + " range " + range[0] + ".." + range[1]);
                // The stream state after the draw matters as much as the cells: everything the
                // generator does next (variants, loot seeds, yaw) draws from the same source.
                assertEquals(a.nextLong(), b.nextLong(),
                        "stream diverged after seed " + seed + " range " + range[0] + ".." + range[1]);
            }
        }
    }

    /** Cells are distinct — the whole point of drawing without replacement. */
    @Test
    void handsOutDistinctCells() {
        for (long seed = 0; seed < 300; seed++) {
            CellDraw draw = CellDraw.of(cells(12), 12, 12, RandomSource.create(seed));
            Set<Coords2D> seen = new HashSet<>();
            while (draw.hasNext()) {
                assertTrue(seen.add(draw.next()), "duplicate cell at seed " + seed);
            }
            assertEquals(12, seen.size());
        }
    }

    /** A cramped room gets fewer of a thing, not two in one cell. */
    @Test
    void clampsTheCountToTheCandidatesAvailable() {
        CellDraw draw = CellDraw.of(cells(3), 10, 10, RandomSource.create(1L));
        assertEquals(3, draw.count());
        assertEquals(3, newDraw(cells(3), 10, 10, RandomSource.create(1L)).size());
    }

    /**
     * An empty candidate list yields nothing. The callers return before this point, but a draw over
     * nothing must not throw if one ever does reach here.
     */
    @Test
    void drawsNothingFromNoCandidates() {
        CellDraw draw = CellDraw.of(List.of(), 1, 3, RandomSource.create(1L));
        assertEquals(0, draw.count());
        assertFalse(draw.hasNext());
    }

    /** The caller's list is not permuted underneath them. */
    @Test
    void doesNotMutateTheCallersList() {
        List<Coords2D> candidates = cells(8);
        List<Coords2D> before = List.copyOf(candidates);
        newDraw(candidates, 8, 8, RandomSource.create(7L));
        assertEquals(before, candidates);
    }

    @Test
    void throwsPastTheCount() {
        CellDraw draw = CellDraw.of(cells(5), 1, 1, RandomSource.create(3L));
        draw.next();
        assertThrows(NoSuchElementException.class, draw::next);
    }
}
