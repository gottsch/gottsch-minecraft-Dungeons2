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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor;

import mod.gottsch.forge.dungeons2.core.config.CorridorConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Phase 3 invariant: the grid-free {@link BasicCorridorGenerator} overload (which
 * reads wall columns from {@link CorridorData#getWallCells()}) produces the same
 * set of placements as the grid-based overload for every corridor the planner
 * emits. This is what lets the {@code DungeonCorridorPiece} render correctly
 * after NBT deserialization, when the transient maze grid is gone.
 *
 * @author Mark Gottschling on Jun 16, 2026
 */
class CorridorGridFreeParityTest {

    private static final long SEED = 0xC0FFEE_1234L;
    private static final ICoords ANCHOR = new Coords(0, 0, 0);
    private static final int SURFACE_Y = 72;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void gridFreeMatchesGridBasedForEveryCorridor() {
        assertParity(MotifConfig.DEFAULT, CorridorData.DEFAULT_WALL_HEIGHT);
    }

    /**
     * The arch is the first thing either overload decides from <em>neighbouring</em> cells rather
     * than from the cell it is emitting, so it is the first real chance for the two to disagree:
     * the grid-based side asks the live {@code Grid2D}, the grid-free side asks the wall/door cells
     * the planner folded in. Same question, two sources.
     */
    @Test
    void gridFreeMatchesGridBasedForAnArchedMotifToo() {
        CorridorConfig arched = new CorridorConfig(
                "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks",
                7, CorridorConfig.Profile.ARCHED, Optional.of("minecraft:stone_brick_stairs"));
        MotifConfig motif = new MotifConfig(
                MotifConfig.DEFAULT.wall(), MotifConfig.DEFAULT.ceiling(), MotifConfig.DEFAULT.door(),
                arched, MotifConfig.DEFAULT.floor(), MotifConfig.DEFAULT.schemes());

        assertParity(motif, 7);
    }

    private void assertParity(MotifConfig motifConfig, int corridorHeight) {
        DungeonLayout layout = new DungeonStackPlanner(SEED, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                .withSize(DungeonSize.SMALL)
                .withCorridorHeight(corridorHeight)
                .plan()
                .orElseThrow();

        BasicCorridorGenerator gen = new BasicCorridorGenerator().withMotifConfig(motifConfig);
        int corridorsChecked = 0;

        for (FloorLayout floor : layout.getFloors()) {
            int floorY = floor.getFloorY();
            for (CorridorData corridor : floor.getCorridors()) {
                if (corridor.getCells().isEmpty()) {
                    continue;
                }
                List<BlockPlacement> gridBased = new ArrayList<>();
                gen.build(corridor, floor.getGrid(), floorY, DungeonMotif.CLASSIC,
                        RandomSource.create(SEED), gridBased);

                List<BlockPlacement> gridFree = new ArrayList<>();
                gen.build(corridor, floorY, DungeonMotif.CLASSIC,
                        RandomSource.create(SEED), gridFree);

                assertEquals(sortedStrings(gridBased), sortedStrings(gridFree),
                        "corridor " + corridor.getId() + " on floor " + floor.getFloorIndex()
                                + " should render identically grid-free");
                corridorsChecked++;
            }
        }
        assertFalse(corridorsChecked == 0, "expected at least one corridor to verify");
    }

    private static List<String> sortedStrings(List<BlockPlacement> placements) {
        List<String> out = new ArrayList<>(placements.size());
        for (BlockPlacement p : placements) {
            out.add(p.toString());
        }
        Collections.sort(out);
        return out;
    }
}
