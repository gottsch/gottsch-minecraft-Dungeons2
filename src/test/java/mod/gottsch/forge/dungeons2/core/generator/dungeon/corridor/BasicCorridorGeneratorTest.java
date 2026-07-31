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

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.decorator.BlockProvider;
import mod.gottsch.forge.dungeons2.core.decorator.BlockSet;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.CellType;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.pattern.floor.CorridorFloorPattern;
import mod.gottsch.forge.dungeons2.core.registry.BlockProivderRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicCorridorGeneratorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A 7x7 grid with a 3-cell straight corridor in the middle (the rest is rock/wall). */
    private Grid2D simpleGrid() {
        Grid2D grid = new Grid2D(7, 7);
        // Grid is initialized with WALL borders and ROCK interior. Carve a corridor.
        for (int x = 2; x <= 4; x++) {
            grid.get(x, 3).setType(CellType.CORRIDOR);
            grid.get(x, 3).setRegionId(10);
        }
        return grid;
    }

    private CorridorData simpleCorridor() {
        CorridorData c = new CorridorData(10);
        c.getCells().add(new Coords2D(2, 3));
        c.getCells().add(new Coords2D(3, 3));
        c.getCells().add(new Coords2D(4, 3));
        return c;
    }

    @Test
    void corridorEmitsFloorAndAirColumnPerCell() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60,
                DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // Per corridor cell: 1 floor + 3 air = 4 placements.
        // Per cell also: up to 8 wall neighbors. For 3-cell line, neighbor walls
        // are deduped. We just verify the corridor cells got their 4 each.
        long corridorCellPlacements = out.stream()
                .filter(bp -> bp.getY() >= 60 && bp.getY() <= 63
                        && bp.getX() >= 2 && bp.getX() <= 4 && bp.getZ() == 3)
                .count();
        assertEquals(3 * 4, corridorCellPlacements,
                "Each corridor cell should have 1 floor + 3 air = 4 placements");
    }

    @Test
    void corridorWallsAreEmittedAroundCorridor() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60,
                DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        // For corridor at (2..4, 3), neighbor cells include (1..5, 2), (1..5, 3), (1..5, 4)
        // minus the corridor itself. All neighbors are WALL/ROCK in this grid.
        // Each wall cell emits 5 vertical blocks (y=60..64).
        boolean sawWallColumn = false;
        for (BlockPlacement bp : out) {
            if (bp.getX() == 1 && bp.getZ() == 3 && bp.getY() == 62) {
                sawWallColumn = true;
                break;
            }
        }
        assertTrue(sawWallColumn,
                "Expected a wall column to the west of corridor at (1,*,3)");
    }

    /**
     * The corridor half of the lichen-on-doors fix: a bordering DOOR cell must
     * not carry a full cube at the two door-half levels, or the corridor's
     * decoration pass anchors lichen there facing the door cell and the lichen
     * ends up rendered onto the door {@code DungeonDoorPiece} carves in later.
     */
    @Test
    void aBorderingDoorCellIsPiercedAtTheTwoDoorHalfLevels() {
        Grid2D grid = simpleGrid();
        grid.get(1, 3).setType(CellType.DOOR); // west end of the corridor run

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertColumnIsPierced(out, 1, 3, 60);
    }

    /** A CONNECTOR is still a plain wall — no door piece follows it, so no hole. */
    @Test
    void aBorderingConnectorCellStaysSolid() {
        Grid2D grid = simpleGrid();
        grid.get(1, 3).setType(CellType.CONNECTOR);

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), grid, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        for (BlockPlacement bp : out) {
            if (bp.getX() == 1 && bp.getZ() == 3) {
                assertEquals(false, "minecraft:air".equals(bp.getBlockId()),
                        "connector column must stay solid: " + bp);
            }
        }
    }

    /**
     * The grid-free overload (what the deserialized piece uses) must agree with
     * the grid-based one, reading door cells off {@code CorridorData}.
     */
    @Test
    void theGridFreeOverloadPiercesDoorCellsToo() {
        CorridorData corridor = simpleCorridor();
        corridor.getDoorCells().add(new Coords2D(1, 3));

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(corridor, 60, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        assertColumnIsPierced(out, 1, 3, 60);
    }

    /** Air at floorY+1 / floorY+2, solid at floorY / +3 / +4, exactly 5 blocks. */
    private static void assertColumnIsPierced(List<BlockPlacement> out, int x, int z, int floorY) {
        int seen = 0;
        for (BlockPlacement bp : out) {
            if (bp.getX() != x || bp.getZ() != z) continue;
            seen++;
            int offset = bp.getY() - floorY;
            if (offset == 1 || offset == 2) {
                assertEquals("minecraft:air", bp.getBlockId(),
                        "door-half level " + offset + " must be air: " + bp);
            } else {
                assertTrue(!"minecraft:air".equals(bp.getBlockId()),
                        "level " + offset + " must stay solid: " + bp);
            }
        }
        assertEquals(5, seen, "a doorway column is still a full 5-block column");
    }

    /**
     * Regression test for the bug fixed alongside this: {@code palette()} used to resolve the
     * corridor floor from the WALL_PATTERN BlockSet using a CorridorFloorPattern.FLOOR key --
     * since BlockSet is keyed by enum instance, not name, that BlockSet (populated only with
     * WallPattern constants) never actually had that key, so the corridor floor always silently
     * fell through to the DEFAULT stone_bricks regardless of what block_provider's
     * corridor_floor_pattern authored. Registers a fake {@code BlockProvider} directly (bypassing
     * datapack JSON loading, which needs a running Forge instance) under a motif no other test
     * touches, and confirms the corridor floor now actually reflects it.
     */
    @Test
    void corridorFloorIsSourcedFromItsOwnCorridorFloorPatternBlockSet() {
        BlockSet floorBlockSet = new BlockSet("default");
        floorBlockSet.set(CorridorFloorPattern.FLOOR, Blocks.GRANITE);
        floorBlockSet.set(CorridorFloorPattern.ALTERNATE_FLOOR, Blocks.DIORITE);
        BlockProvider provider = new BlockProvider();
        provider.register("corridor_floor_pattern", floorBlockSet);
        BlockProivderRegistry.register(DungeonMotif.DESERT, provider);

        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> out = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.DESERT, RandomSource.create(7L), out);

        boolean sawConfiguredFloor = out.stream().anyMatch(bp -> bp.getY() == 60
                && ("minecraft:granite".equals(bp.getBlockId()) || "minecraft:diorite".equals(bp.getBlockId())));
        assertTrue(sawConfiguredFloor,
                "corridor floor should reflect the registered corridor_floor_pattern, not fall back to DEFAULT");
    }

    @Test
    void corridorBuilderIsDeterministic() {
        BasicCorridorGenerator gen = new BasicCorridorGenerator();
        List<BlockPlacement> a = new ArrayList<>();
        List<BlockPlacement> b = new ArrayList<>();
        gen.build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), a);
        gen.build(simpleCorridor(), simpleGrid(), 60, DungeonMotif.CLASSIC, RandomSource.create(7L), b);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).toString(), b.get(i).toString());
        }
    }
}
