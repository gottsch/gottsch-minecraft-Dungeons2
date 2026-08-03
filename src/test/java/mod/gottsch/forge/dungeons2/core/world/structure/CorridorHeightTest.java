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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plumbing that makes corridor height configurable, end to end: the planner stamps the
 * motif-resolved height onto every {@link CorridorData}, {@link DungeonCorridorPiece} sizes its
 * bounding box from it at construction, and it survives the NBT round-trip so a deserialized
 * piece still renders the corridor it was planned as.
 *
 * <p>The bounding box is the part worth guarding. A piece's box is computed before it can reach
 * the datapack registry, and vanilla silently drops any block written outside it &mdash; so a box
 * that disagrees with the generator does not fail loudly, it just quietly decapitates every
 * corridor in the dungeon.</p>
 *
 * @author Mark Gottschling on Aug 03, 2026
 */
class CorridorHeightTest {

    private static final long SEED = 0xD2_4A_2026L;
    private static final int ANCHOR_X = 128;
    private static final int ANCHOR_Z = 256;
    private static final int SURFACE_Y = 64;
    private static final String MOTIF = "classic";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DungeonLayout plan(Integer corridorHeight) {
        ICoords anchor = new Coords(ANCHOR_X, 0, ANCHOR_Z);
        DungeonStackPlanner planner = new DungeonStackPlanner(SEED, anchor, SURFACE_Y, MOTIF, new TemplateCatalog())
                .withSize(DungeonSize.SMALL)
                .withFloorCount(1);
        if (corridorHeight != null) {
            planner.withCorridorHeight(corridorHeight);
        }
        return planner.plan().orElseThrow(() -> new AssertionError("planner returned empty for fixed seed"));
    }

    private static List<CorridorData> corridors(DungeonLayout layout) {
        return layout.getFloors().stream().map(FloorLayout::getCorridors).flatMap(List::stream).toList();
    }

    /** A caller that never injects a height gets the historical 5, so existing dungeons are unchanged. */
    @Test
    void aPlannerWithNoInjectedHeightKeepsTheHistoricalFive() {
        List<CorridorData> corridors = corridors(plan(null));
        assertFalse(corridors.isEmpty(), "fixed seed produced no corridors to check");
        for (CorridorData corridor : corridors) {
            assertEquals(CorridorData.DEFAULT_WALL_HEIGHT, corridor.getWallHeight());
        }
    }

    @Test
    void thePlannerStampsTheInjectedHeightOntoEveryCorridor() {
        List<CorridorData> corridors = corridors(plan(7));
        assertFalse(corridors.isEmpty(), "fixed seed produced no corridors to check");
        for (CorridorData corridor : corridors) {
            assertEquals(7, corridor.getWallHeight());
        }
    }

    /**
     * The clipping guard: every block the generator emits has to land inside the box the piece
     * declared, at whatever height the motif asked for.
     */
    @Test
    void everyEmittedBlockFitsInsideThePiecesBoundingBox() {
        for (int height = 5; height <= 8; height++) {
            DungeonLayout layout = plan(height);
            int floorY = layout.getFloors().get(0).getFloorY();
            for (CorridorData corridor : corridors(layout)) {
                DungeonCorridorPiece piece =
                        new DungeonCorridorPiece(corridor, MOTIF, floorY, ANCHOR_X, ANCHOR_Z);
                BoundingBox box = piece.getBoundingBox();
                assertEquals(floorY, box.minY(), "box floor at height " + height);
                assertEquals(floorY + height - 1, box.maxY(), "box ceiling at height " + height);

                for (BlockPlacement bp : piece.renderPlacements()) {
                    int worldX = ANCHOR_X + bp.getX();
                    int worldZ = ANCHOR_Z + bp.getZ();
                    assertTrue(box.isInside(new net.minecraft.core.BlockPos(worldX, bp.getY(), worldZ)),
                            "height " + height + ": block at (" + worldX + "," + bp.getY() + "," + worldZ
                                    + ") falls outside the piece box " + box + " and would be clipped");
                }
            }
        }
    }

    @Test
    void heightSurvivesTheNbtRoundTrip() {
        DungeonLayout layout = plan(8);
        CorridorData corridor = corridors(layout).get(0);

        CompoundTag tag = PieceNbt.writeCorridor(corridor);
        assertEquals(8, PieceNbt.readCorridor(tag).getWallHeight());
    }

    /**
     * A corridor saved before the field existed carries no {@code WallHeight} tag; it has to keep
     * generating the 5-high corridor the rest of that world was built with.
     */
    @Test
    void aPreHeightSaveStillReadsAsFiveHigh() {
        CompoundTag tag = PieceNbt.writeCorridor(corridors(plan(8)).get(0));
        tag.remove("WallHeight");

        assertEquals(CorridorData.DEFAULT_WALL_HEIGHT, PieceNbt.readCorridor(tag).getWallHeight());
    }
}
