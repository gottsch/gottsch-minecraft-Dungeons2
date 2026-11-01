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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arch's one adjacency invariant: <strong>no haunch stands directly in front of another haunch
 * leaning the same way.</strong>
 *
 * <p>Reported in game as "stairs stacked in front of stairs". A haunch leans into a wall, so the
 * cell it leans <em>toward</em> is normally solid and the question cannot arise — except for the
 * convex-corner caps from {@code BasicCorridorGenerator}'s {@code CORNERS} table, which are
 * deliberately the one kind of haunch that leans over open corridor. Nothing checked whether the
 * chamfer had already arrived along that axis, so ~86% of caps landed as a second stair immediately
 * in front of the first: measured at 160 such pairs across 12 MEDIUM dungeons, now 0.</p>
 *
 * <p><strong>Perpendicular</strong> neighbours are deliberately still allowed and must stay that
 * way: a cap meeting a haunch at right angles is the corner closure the {@code CORNERS} table was
 * added for in the first place, and removing it reopens the notch that fix was chasing. This test
 * would pass just as well if caps were deleted outright, so it is paired with
 * {@link #convexCornerTipsStillGetTheirCap}.</p>
 *
 * @author Mark Gottschling on Aug 04, 2026
 */
class ArchHaunchGeometryTest {

    private static final int SEEDS = 12;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Every stair the corridor generator emits for one dungeon, keyed by position. */
    private static Map<Long, BlockState> haunches(long seed, MotifConfig motifConfig) {
        DungeonLayout layout = new DungeonStackPlanner(
                seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                .withSize(DungeonSize.MEDIUM)
                .withCorridorWidth(3)
                .withCorridorHeight(7)
                .plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));

        Map<Long, BlockState> byPos = new HashMap<>();
        for (FloorLayout floor : layout.getFloors()) {
            BasicCorridorGenerator generator = new BasicCorridorGenerator().withMotifConfig(motifConfig);
            for (CorridorData corridor : floor.getCorridors()) {
                List<BlockPlacement> out = new ArrayList<>();
                generator.build(corridor, floor.getFloorY(), DungeonMotif.CLASSIC,
                        RandomSource.create(seed), out);
                for (BlockPlacement placement : out) {
                    BlockState state = BlockStateCodec.resolve(placement);
                    if (state.getBlock() instanceof StairBlock) {
                        byPos.put(key(placement.getX(), placement.getY(), placement.getZ()), state);
                    }
                }
            }
        }
        return byPos;
    }

    @Test
    void noHaunchStandsInFrontOfAnotherFacingTheSameWay() {
        MotifConfig motifConfig = MotifConfigs.load("classic");
        int total = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Map<Long, BlockState> byPos = haunches(seed, motifConfig);
            total += byPos.size();

            for (Map.Entry<Long, BlockState> entry : byPos.entrySet()) {
                BlockState state = entry.getValue();
                Direction facing = state.getValue(StairBlock.FACING);
                // The cell this stair leans toward. Two stairs in a line, both leaning the same
                // way, is one stair standing in front of another.
                BlockState ahead = byPos.get(shift(entry.getKey(), facing));
                if (ahead == null) {
                    continue;
                }
                assertFalse(ahead.getValue(StairBlock.FACING) == facing,
                        "seed " + seed + ": a haunch at " + describe(entry.getKey())
                                + " facing " + facing + " has another haunch facing the same way"
                                + " directly in front of it -- see this class's comment");
            }
        }
        assertTrue(total > 0, "no haunches were generated at all; this test proved nothing");
    }

    /**
     * The other half of the invariant. Suppressing the same-facing duplicate must not have
     * suppressed the caps outright &mdash; a cap meeting a haunch at right angles is what closes a
     * convex corner, and it is the thing whose absence read in game as "the outers aren't
     * populating".
     */
    @Test
    void convexCornerTipsStillGetTheirCap() {
        MotifConfig motifConfig = MotifConfigs.load("classic");
        int perpendicularPairs = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            Map<Long, BlockState> byPos = haunches(seed, motifConfig);
            for (Map.Entry<Long, BlockState> entry : byPos.entrySet()) {
                Direction facing = entry.getValue().getValue(StairBlock.FACING);
                BlockState ahead = byPos.get(shift(entry.getKey(), facing));
                if (ahead != null && ahead.getValue(StairBlock.FACING).getAxis() != facing.getAxis()) {
                    perpendicularPairs++;
                }
            }
        }
        assertTrue(perpendicularPairs > 0,
                "no cap meets a haunch at right angles any more -- the convex-corner caps have been "
                        + "removed rather than de-duplicated, which reopens the notch at every bend");
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0xFFFFF) << 22 | ((long) z & 0x3FFFFF);
    }

    private static long shift(long key, Direction direction) {
        int x = (int) (key >> 42) & 0x3FFFFF;
        int y = (int) (key >> 22) & 0xFFFFF;
        int z = (int) key & 0x3FFFFF;
        return key(x + direction.getStepX(), y, z + direction.getStepZ());
    }

    private static String describe(long key) {
        return "(" + ((key >> 42) & 0x3FFFFF) + "," + ((key >> 22) & 0xFFFFF) + "," + (key & 0x3FFFFF) + ")";
    }
}
