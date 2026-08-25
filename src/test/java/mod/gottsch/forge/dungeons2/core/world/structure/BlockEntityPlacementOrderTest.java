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

import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.diagnostic.FakeWorldGenLevel;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block-entity placement must survive a plain placement in the same cell.
 *
 * <h2>The bug this pins</h2>
 * <p>{@code DungeonPiece.placeAll} does not write in list order. Plain placements are collected,
 * run through the motif's decoration processors and written <em>after</em> that pass; anything
 * written inside the collection loop is therefore written <strong>first</strong>, whatever its
 * position in the list. Block-entity placements used to be written inside that loop.</p>
 *
 * <p>Harmless for as long as the only block entities came in through the jigsaw path, which does not
 * use this method at all. The moment a room scheme emitted one, it stopped being harmless: a room's
 * {@code hollow()} step emits air for every interior cell, including the one the scheme had just put
 * a spawner in, so the air landed on top of the spawner and the room came out exactly as empty as
 * before &mdash; with nothing logged, and nothing to see.</p>
 *
 * <p>{@code minecraft:chest} rather than the spawner block: this runs under a bare
 * {@code Bootstrap} where Dungeons2's own blocks are not registered, and the invariant is about
 * write order rather than about any particular block.</p>
 *
 * @author Mark Gottschling on Aug 17, 2026
 */
class BlockEntityPlacementOrderTest {

    /** Every piece here is built on the entrance floor; depth is not what these cases are about. */
    private static final int TEST_FLOOR_INDEX = 0;

    private static final int ANCHOR_X = 0;
    private static final int ANCHOR_Z = 0;
    private static final int FLOOR_Y = 64;
    private static final BlockPos CELL = new BlockPos(4, FLOOR_Y + 1, 4);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A corridor piece, used purely as a concrete {@link DungeonPiece} to reach the shared
     * {@code placeAll}. Its own generated placements are not involved -- the list below is handed in
     * directly, which is what makes the ordering visible on its own.
     */
    private static DungeonCorridorPiece piece() {
        List<Coords2D> cells = new ArrayList<>();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                cells.add(new Coords2D(x, z));
            }
        }
        CorridorData corridor = new CorridorData(1, cells);
        corridor.setWallHeight(6);
        return new DungeonCorridorPiece(corridor, "classic", FLOOR_Y, TEST_FLOOR_INDEX, ANCHOR_X, ANCHOR_Z);
    }

    private static BlockPlacement plainAir() {
        return new BlockPlacement(CELL.getX(), CELL.getY(), CELL.getZ(), "minecraft:air");
    }

    private static BlockPlacement withBlockEntity() {
        BlockPlacement placement = new BlockPlacement(
                CELL.getX(), CELL.getY(), CELL.getZ(), "minecraft:chest");
        placement.setBlockEntityNbt(new BlockEntityData("minecraft:chest")
                .with("LootTable", "dungeons2:chests/classic"));
        return placement;
    }

    private static FakeWorldGenLevel place(List<BlockPlacement> placements) {
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        DungeonCorridorPiece piece = piece();
        piece.placeAll(level.level(), piece.getBoundingBox(), java.util.Optional.empty(), placements);
        return level;
    }

    /** The list order that used to lose: air first, block entity second. */
    @Test
    void aBlockEntityPlacementIsNotOverwrittenByAPlainOneEmittedBeforeIt() {
        FakeWorldGenLevel level = place(List.of(plainAir(), withBlockEntity()));
        assertEquals(Blocks.CHEST, level.blockAt(CELL).getBlock(),
                "the plain placement was written after the block entity's -- see the class comment");
    }

    /**
     * And the other order too. Block entities going last is a rule about the two <em>channels</em>,
     * not about who happened to be emitted first: a container's contents must not be swapped out by
     * the decoration pass, so the channel wins either way.
     */
    @Test
    void aBlockEntityPlacementAlsoWinsWhenItComesFirstInTheList() {
        FakeWorldGenLevel level = place(List.of(withBlockEntity(), plainAir()));
        assertEquals(Blocks.CHEST, level.blockAt(CELL).getBlock());
    }

    /** The clip to the chunk box still applies -- a cell this call does not own is left alone. */
    @Test
    void aBlockEntityOutsideTheBoxIsNotWritten() {
        FakeWorldGenLevel level = FakeWorldGenLevel.create();
        DungeonCorridorPiece piece = piece();
        BoundingBox elsewhere = new BoundingBox(512, FLOOR_Y, 512, 520, FLOOR_Y + 8, 520);
        piece.placeAll(level.level(), elsewhere, java.util.Optional.empty(), List.of(withBlockEntity()));
        assertTrue(level.blocks().isEmpty(),
                "a placement outside the chunk box was written anyway: " + level.blocks());
    }
}
