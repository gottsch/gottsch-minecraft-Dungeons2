/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.maze;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.data.TransitionData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A jigsaw-assembled transition's real footprint is only known AFTER it assembles,
 * so the planner assembles it twice: once to measure, then again — same seed, so the
 * same chain — anchored onto a slot reserved at the measured size. These tests pin
 * the two failure modes that motivated it, both of which are silent in game.
 *
 * <p><strong>A transition that isn't adopted renders as an empty reserved shaft</strong>
 * with nothing in it. Measured 2026-07-29: a self-contained single-piece transition
 * was adopted only 86% of the time when the planner validated against a guessed
 * position, so roughly one in seven came out as an empty hole.</p>
 *
 * <p><strong>A chained transition was worse than not adopted.</strong> Measured
 * 2026-07-30 over 200 seeds: adopted 0% of the time, and every seed that did adopt
 * one aborted the entire dungeon, because a chain's union rect lands on an odd
 * origin and {@code MazeLevelGenerator2D.isRoomValid} rejects an odd-origin room.
 * Both are 100%/0 with the measure-then-reserve loop.</p>
 *
 * @author Mark Gottschling on Jul 29, 2026
 */
class TransitionAssemblyPlacementTest {

    /**
     * A single-piece transition: 9x9, footprint anchored exactly at the assembly
     * point, doors mid-wall. This is the shape {@code ladder1} / {@code stairs_1}
     * have, i.e. everything that actually ships in `shaft_bottom` today.
     */
    @Test
    void aSelfContainedTransitionIsAlmostAlwaysAdopted() {
        int adopted = 0;
        int synthetic = 0;

        for (long seed = 0; seed < 120; seed++) {
            Optional<DungeonLayout> opt = new DungeonStackPlanner(
                    seed, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    .withFloorCount(3)
                    .withTransitionAssembler(SINGLE_PIECE)
                    .plan();
            if (opt.isEmpty()) {
                continue;
            }
            for (TransitionData t : opt.get().getTransitions()) {
                if (t.getTemplateId() != null && t.getTemplateId().contains("assembled")) {
                    adopted++;
                } else {
                    synthetic++;
                }
            }
        }

        assertTrue(adopted > 0, "no transition was adopted at all -- the assembler never validated");
        // With one attempt this measured 86%; with retries it is 100%. Assert a
        // floor well above the single-attempt rate so losing the retry fails here.
        double rate = (double) adopted / (adopted + synthetic);
        assertTrue(rate >= 0.95,
                String.format("only %.0f%% of assembled transitions were adopted (%d synthetic) -- "
                        + "a synthetic transition renders as an empty reserved shaft, so the planner "
                        + "must retry at another position instead of falling back on the first miss",
                        rate * 100, synthetic));
    }

    /**
     * The real three-piece {@code stairs_2} chain: a 7x12 union footprint offset up
     * to 11 blocks NEGATIVE from the point it was assembled at. This is the shape
     * that could not be placed at all before the planner measured before reserving
     * &mdash; adopted 0% of the time across 200 seeds, and worse than that, every
     * seed that DID adopt one aborted the whole dungeon, because the union's origin
     * is not even-aligned and {@code MazeLevelGenerator2D.isRoomValid} rejects an
     * odd-origin room outright.
     */
    @Test
    void aChainedTransitionIsAdoptedAndPlansSuccessfully() {
        Tally chained = tally(STAIRS_2_CHAIN);

        assertEquals(0, chained.planFailed,
                "a chained transition must never abort planning -- it is reserved at the size and "
                        + "even-aligned origin it was measured at, so the maze can always take it");
        assertTrue(chained.adopted > 0, "the chained transition was never adopted at all");
        double rate = (double) chained.adopted / (chained.adopted + chained.synthetic);
        assertTrue(rate >= 0.95, String.format(
                "only %.0f%% of chained transitions were adopted (%d synthetic) -- measured 0%% before "
                        + "the planner measured the real footprint before reserving a slot for it",
                rate * 100, chained.synthetic));
    }

    /**
     * The same probe/place round trip must not regress the self-contained case, and
     * in particular the measuring probe must not be double-placed: the probe is
     * assembled at a throwaway position, so if it were committed alongside the real
     * placement the dungeon would contain two copies of the transition.
     */
    @Test
    void theMeasuringProbeIsNeverCommitted() {
        List<int[]> committed = new java.util.ArrayList<>();
        new DungeonStackPlanner(7L, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withFloorCount(3)
                .withTransitionAssembler((wx, wy, wz, seed, commit) -> {
                    if (commit) {
                        committed.add(new int[] {wx, wy, wz});
                    }
                    return STAIRS_2_CHAIN.assemble(wx, wy, wz, seed, commit);
                })
                .plan().orElseThrow();

        assertEquals(2, committed.size(),
                "a 3-floor dungeon has 2 inter-floor links, so exactly 2 assemblies may be committed");
    }

    private record Tally(int adopted, int synthetic, int plannedOk, int planFailed) {
    }

    private Tally tally(DungeonStackPlanner.TransitionAssembler assembler) {
        int adopted = 0;
        int synthetic = 0;
        int plannedOk = 0;
        int planFailed = 0;

        for (long seed = 0; seed < 200; seed++) {
            Optional<DungeonLayout> opt = new DungeonStackPlanner(
                    seed, new Coords(128, 0, 256), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    .withFloorCount(3)
                    .withTransitionAssembler(assembler)
                    .plan();
            if (opt.isEmpty()) {
                planFailed++;
                continue;
            }
            plannedOk++;
            for (TransitionData t : opt.get().getTransitions()) {
                if (t.getTemplateId() != null && t.getTemplateId().contains("assembled")) {
                    adopted++;
                } else {
                    synthetic++;
                }
            }
        }
        return new Tally(adopted, synthetic, plannedOk, planFailed);
    }

    /**
     * A single-piece transition: 9x9, footprint anchored exactly at the assembly
     * point, doors mid-wall. The shape {@code ladder1} / {@code stairs_1} have.
     */
    private static final DungeonStackPlanner.TransitionAssembler SINGLE_PIECE =
            (wx, wy, wz, seed, commit) -> Optional.of(new DungeonStackPlanner.AssembledTransition(
                    new Rectangle2D(wx, wz, 9, 9),
                    List.of(new Coords2D(wx + 4, wz)),
                    List.of(new Coords2D(wx + 4, wz + 8)),
                    List.of(), List.of()));

    /** {minX, minZ, width, depth} of the stairs_2 union, relative to the assembly point. */
    private static final int[][] STAIRS_2_UNION = {
            {-3, 0, 7, 12},    // NONE
            {-11, -3, 12, 7},  // CW 90    (x,z) -> (-z, x)
            {-3, -11, 7, 12},  // 180      (x,z) -> (-x,-z)
            {0, -3, 12, 7},    // CCW 90   (x,z) -> ( z,-x)
    };

    /**
     * The four {@code dungeons2:connector} cells relative to the assembly point,
     * per rotation: bottom pair first, then top pair. Same transforms as above
     * applied to the unrotated cells {@code (1,0) (2,0)} and {@code (-2,0) (-1,0)}.
     */
    private static final int[][][] STAIRS_2_MARKERS = {
            {{1, 0}, {2, 0}, {-2, 0}, {-1, 0}},       // NONE
            {{0, 1}, {0, 2}, {0, -2}, {0, -1}},       // CW 90
            {{-1, 0}, {-2, 0}, {2, 0}, {1, 0}},       // 180
            {{0, -1}, {0, -2}, {0, 2}, {0, 1}},       // CCW 90
    };

    /**
     * Models the real {@code stairs_2} chain, measured off the three {@code .nbt}
     * files (2026-07-30) and rotated the four ways vanilla may rotate it.
     *
     * <p>Unrotated, assembled at the origin, the three pieces land at
     * {@code bottom x 0..3 z 0..7}, {@code mid x -3..3 z 3..11},
     * {@code top x -3..0 z 0..11} &mdash; union {@code x -3..3, z 0..11}, i.e. 7x12
     * with its min corner 3 blocks WEST of the assembly point. Both
     * {@code dungeons2:connector} pairs sit on the union's {@code z = 0} edge:
     * the bottom piece's at local x 1,2 (union-relative 4,5) and the top piece's at
     * local x 1,2 of a piece whose own origin is x = -3 (union-relative 1,2).</p>
     *
     * <p>It honours the assembler contract: shape is a pure function of
     * {@code assemblySeed} (which of the four rotations), and position only
     * translates the result &mdash; exactly what {@code rigid} jigsaw placement
     * does.</p>
     */
    private static final DungeonStackPlanner.TransitionAssembler STAIRS_2_CHAIN =
            (wx, wy, wz, seed, commit) -> {
                // Rotation is drawn from the seed alone, so probe and commit agree.
                int rot = Math.floorMod(new java.util.Random(seed).nextInt(), 4);
                int[][] markers = STAIRS_2_MARKERS[rot];
                int[] u = STAIRS_2_UNION[rot];
                Rectangle2D fp = new Rectangle2D(wx + u[0], wz + u[1], u[2], u[3]);
                return Optional.of(new DungeonStackPlanner.AssembledTransition(
                        fp, List.of(), List.of(),
                        // top-floor side: the top piece's connector pair
                        List.of(new Coords2D(wx + markers[2][0], wz + markers[2][1]),
                                new Coords2D(wx + markers[3][0], wz + markers[3][1])),
                        // bottom-floor side: the bottom piece's connector pair
                        List.of(new Coords2D(wx + markers[0][0], wz + markers[0][1]),
                                new Coords2D(wx + markers[1][0], wz + markers[1][1]))));
            };

}
