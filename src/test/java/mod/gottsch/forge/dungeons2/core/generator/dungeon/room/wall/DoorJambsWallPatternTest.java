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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.wall.DoorJambsWallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.WallPattern;
import mod.gottsch.forge.dungeons2.core.config.wall.WallPatternRegistry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.WallSurface;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Door-aware wall runs (#72) and the pattern that proves them.
 *
 * <h2>What #72 actually was</h2>
 * <p>Less than the backlog entry claimed. {@code BasicWallGenerator} always had the room's doorways
 * &mdash; it passes them to {@code WallSurface#emit} to blank the two door rows. What never reached
 * the PATTERN was the same set, because {@code ISurfacePatternProvider#plan} takes a size, a facing
 * and a random by design. So the work was a run-to-column mapping and an optional interface, not new
 * data.</p>
 */
class DoorJambsWallPatternTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BlockState jamb() {
        return Blocks.POLISHED_ANDESITE.defaultBlockState();
    }

    private static BlockState lintel() {
        return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    }

    private static DoorJambsWallPatternProvider provider() {
        return new DoorJambsWallPatternProvider(jamb(), null, null, lintel());
    }

    // ---------- the plumbing: a run knows which of its columns are an opening ----------

    @Test
    void aRunReportsItsOwnDoorwayColumnsAndNobodyElsesDoors() {
        // 9x9 room at origin 10,20. The SOUTH run spans the full width at z = 20, so a doorway at
        // floor-local (14, 20) is its column 4 -- and a doorway on the far wall is not its business.
        RoomData room = new RoomData(1, 10, 20, 9, 9, 6, RoomRole.NORMAL);
        Set<Coords2D> doorways = new HashSet<>(List.of(
                new Coords2D(14, 20), new Coords2D(14, 28)));

        List<WallSurface> runs = WallSurface.forRoom(room);
        assertEquals(Set.of(4), runs.get(0).doorColumns(doorways), "south run");
        assertEquals(Set.of(4), runs.get(1).doorColumns(doorways), "north run");
        assertEquals(Set.of(), runs.get(2).doorColumns(doorways), "east run has no door");
    }

    @Test
    void aRunWithNoDoorsAsksForNothing() {
        RoomData room = new RoomData(1, 0, 0, 9, 9, 6, RoomRole.NORMAL);
        assertTrue(WallSurface.forRoom(room).get(0).doorColumns(Set.of()).isEmpty());
    }

    // ---------- the geometry ----------

    @Test
    void aJambStandsEachSideOfTheOpeningAndNotInIt() {
        SurfacePlan plan = provider().plan(9, 4, Direction.SOUTH, Set.of(4),
                RandomSource.create(1L));

        for (int v = 0; v < 4; v++) {
            assertEquals(jamb(), plan.get(3, v), "left jamb missing at v=" + v);
            assertEquals(jamb(), plan.get(5, v), "right jamb missing at v=" + v);
        }
        // The opening itself keeps the wall's own block, except the lintel row above the door.
        assertNull(plan.get(4, WallSurface.DOOR_HALF_LOW_V));
        assertNull(plan.get(4, WallSurface.DOOR_HALF_HIGH_V));
        assertEquals(lintel(), plan.get(4, DoorJambsWallPatternProvider.LINTEL_V));
    }

    @Test
    void aTwoWideDoorIsBracketed_notSplitDownTheMiddle() {
        // The maze stores a 2-wide door as two adjacent doorway cells. Bracketing each column
        // separately would put a jamb in the middle of the opening, which is the whole reason this
        // works on RUNS of adjacent columns.
        SurfacePlan plan = provider().plan(9, 4, Direction.SOUTH, Set.of(4, 5),
                RandomSource.create(1L));

        assertEquals(jamb(), plan.get(3, 0), "left jamb missing");
        assertEquals(jamb(), plan.get(6, 0), "right jamb missing");
        assertNull(plan.get(4, 0), "a jamb was drawn inside the opening");
        assertNull(plan.get(5, 0), "a jamb was drawn inside the opening");
    }

    @Test
    void aDoorAtTheEndOfARunGetsTheJambItHasRoomFor() {
        // Out-of-range writes are swallowed, and the column beyond the run's end belongs to the
        // wall around the corner anyway.
        SurfacePlan plan = provider().plan(9, 4, Direction.SOUTH, Set.of(0),
                RandomSource.create(1L));

        assertEquals(jamb(), plan.get(1, 0), "the inboard jamb should still be drawn");
        // One 4-tall jamb plus the lintel, and not a cell more: the jamb that would have stood at
        // u = -1 belongs to the run around the corner.
        assertEquals(4 + 1, plan.markedCells());
    }

    @Test
    void aBaseAndCapDressTheJambsEnds() {
        DoorJambsWallPatternProvider dressed = new DoorJambsWallPatternProvider(jamb(),
                Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                null);
        SurfacePlan plan = dressed.plan(9, 5, Direction.SOUTH, Set.of(4), RandomSource.create(1L));

        assertEquals(Blocks.STONE_BRICKS.defaultBlockState(), plan.get(3, 0));
        assertEquals(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), plan.get(3, 4));
        assertEquals(jamb(), plan.get(3, 2), "the shaft between them is the jamb block");
    }

    @Test
    void withNoDoorsItDrawsNothingAtAll() {
        SurfacePlan plan = provider().plan(9, 4, Direction.SOUTH, Set.of(), RandomSource.create(1L));
        assertEquals(0, plan.markedCells());
        // The door-blind form of plan() must agree: a ceiling has no doorways, and the selector for
        // one would call it.
        assertEquals(0, provider().plan(9, 4, Direction.SOUTH, RandomSource.create(1L)).markedCells());
    }

    // ---------- end to end, through the real generator ----------

    @Test
    void theGeneratorFeedsTheRunsDoorsToTheePatternAndTheJambsLandBesideTheDoor() {
        RoomData room = new RoomData(1, 10, 20, 9, 9, 6, RoomRole.NORMAL);
        room.getDoorways().add(new Coords2D(14, 20));

        List<BlockPlacement> out = new ArrayList<>();
        new BasicWallGenerator()
                .withWallPattern(new DoorJambsWallPattern("minecraft:polished_andesite",
                        java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.of("minecraft:chiseled_stone_bricks"),
                        java.util.Map.of()).provider())
                .build(room, 60, DungeonMotif.CLASSIC, RandomSource.create(1L), out);

        String jambId = ForgeRegistries.BLOCKS.getKey(Blocks.POLISHED_ANDESITE).toString();
        Set<String> jambColumns = new HashSet<>();
        for (BlockPlacement bp : out) {
            if (jambId.equals(bp.getBlockId())) {
                jambColumns.add(bp.getX() + "," + bp.getZ());
            }
        }

        // Either side of the door at x=14 on the z=20 wall, and nowhere else in the room.
        assertEquals(Set.of("13,20", "15,20"), jambColumns);
        assertFalse(out.isEmpty());
    }

    // ---------- the schema ----------

    @Test
    void theTypeIsRegisteredAndDecodesFromItsAuthoredForm() {
        DataResult<WallPatternEntry> result = WallPatternEntry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "patterns": [
                            { "type": "dungeons2:door_jambs", "config": {
                                "block": "minecraft:polished_andesite",
                                "base_block": "minecraft:stone_bricks",
                                "lintel_block": "minecraft:chiseled_stone_bricks" } }
                          ]
                        }"""));

        WallPatternEntry entry = result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        WallPattern pattern = entry.patterns().get(0).pattern();
        assertInstanceOf(DoorJambsWallPattern.class, pattern);
        assertNotNull(pattern.provider());
    }

    @Test
    void anUnresolvableJambDropsThePatternRatherThanDrawingAFrameOfAir() {
        assertNull(new DoorJambsWallPattern("dungeons2:no_such_block").provider());
    }

    @Test
    void theBuiltInSetIncludesIt() {
        assertTrue(WallPatternRegistry.ids().contains(
                new net.minecraft.resources.ResourceLocation("dungeons2", "door_jambs")));
    }
}
