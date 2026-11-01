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

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigFragment;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.IDungeonPillarGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform.IDungeonPlatformGenerator;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <strong>A dais is never built over a column.</strong>
 *
 * <p>Mark walked into a room with a brazier skewered on a pillar, reported it, and then walked into
 * another one after the fix &mdash; which is the useful half of the story. The first fix was at the
 * SCHEME level: {@code hall} carried a centre pillar and a centre dais, so the dais was capped to
 * {@code max_size} 11 against the pier's {@code min_size} 13, and a sweep was added forbidding two
 * centre-seeking slots in one scheme.</p>
 *
 * <p>That could only ever stop the pair it named. {@code corners}, {@code grid}, {@code quartet}
 * and {@code colonnade} all reach positions the others reach, so the collision was never about the
 * centre: {@code BasicPlatformGenerator} was handed the pit-and-partition set and <strong>nothing
 * about the columns</strong>, while a comment claimed that running the dais second let it "place
 * its own cells around a column already there". Running second made the columns exist. It did not
 * tell the dais where they were.</p>
 *
 * <h2>Why the fixture is authored here rather than read off the shipped schemes</h2>
 * <p>Because a datapack-driven version could not see the bug. Every shipped pillar names
 * {@code $shaft}, which resolves to a {@code dungeonblocks:} block, and a layout whose block will
 * not resolve is dropped &mdash; so under a bare bootstrap the shipped schemes draw NO columns and
 * the test would pass by finding nothing on either side. The first draft of this file did exactly
 * that, and the same trap had cost the cornice its coverage a few hours earlier.</p>
 *
 * @author Mark Gottschling on Sep 9, 2026
 */
class DaisAndColumnTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int SIZE = 11;
    private static final int HEIGHT = 8;
    private static final int FLOOR_Y = 60;
    private static final int ORIGIN = 10;

    private static final String MOTIF_HEAD = """
            {
              "wall": { "wall": "minecraft:stone_bricks" },
              "ceiling": { "ceiling": "minecraft:stone_bricks" },
              "floor": { "base": "minecraft:stone_bricks",
                         "alternate_base": "minecraft:stone_bricks" },
              "schemes": [ """;

    private static final String MOTIF_TAIL = " ] }";

    /** A centre column and a centre dais in one scheme: the exact pair Mark walked into. */
    private static final String COLLIDING = """
            {
              "name": "collision_fixture",
              "pillars": { "patterns": [
                 { "type": "dungeons2:centre", "block": "minecraft:stone_bricks",
                   "config": { "inset": 1 } } ] },
              "platforms": { "patterns": [
                 { "type": "dais", "layout": "dungeons2:centre",
                   "block": "minecraft:stone_bricks", "stair_block": "minecraft:stone_brick_stairs",
                   "centre_block": "minecraft:chiseled_stone_bricks", "size": 3,
                   "config": { "inset": 1 }, "orient": "inward",
                   "top_block": "minecraft:lantern" } ] }
            }""";

    /** The same dais with no column under it, to prove it would otherwise have been built. */
    private static final String DAIS_ALONE = """
            {
              "name": "dais_fixture",
              "platforms": { "patterns": [
                 { "type": "dais", "layout": "dungeons2:centre",
                   "block": "minecraft:stone_bricks", "stair_block": "minecraft:stone_brick_stairs",
                   "centre_block": "minecraft:chiseled_stone_bricks", "size": 3,
                   "config": { "inset": 1 }, "orient": "inward",
                   "top_block": "minecraft:lantern" } ] }
            }""";

    /**
     * The fixture scheme folded into a whole motif, so that {@link BasicRoomGenerator#build} --
     * which rolls its own scheme out of the motif it was given -- has no choice but to pick it.
     *
     * <p>Going through the REAL entry point is the whole point of this file. An earlier draft
     * mirrored the room generator's sequence by hand, passing the column cells to the platform
     * generator itself, and so tested only that the platform generator honours what it is handed.
     * It stayed green with the actual defect -- the room generator not handing it anything --
     * put back. A test that reimplements the wiring cannot see the wiring being wrong.</p>
     */
    private static MotifConfig motif(String schemeJson) {
        String json = MOTIF_HEAD + schemeJson + MOTIF_TAIL;
        MotifConfigFragment fragment = MotifConfigFragment.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, error -> {
                    throw new AssertionError("the fixture no longer decodes: " + error);
                });
        return MotifConfigFragment.resolve(List.of(fragment));
    }

    /**
     * The guard on the guard, in two halves. Either generator drawing nothing would leave the
     * overlap check below comparing empty sets and saying nothing &mdash; which is precisely how
     * the first draft of this file was green while the bug was live. So: the column must draw in
     * the colliding room, and the same dais must draw when nothing is in its way.
     */
    @Test
    void bothHalvesOfTheFixtureCanActuallyDraw() {
        assertFalse(build(COLLIDING).columns().isEmpty(),
                "the fixture drew no column, so the overlap check proves nothing");
        assertFalse(build(DAIS_ALONE).daises().isEmpty(),
                "the dais does not draw even with the room to itself, so its absence in the "
                        + "colliding case says nothing about the columns");
    }

    /**
     * All-or-nothing, which is the right answer rather than merely the convenient one: a dais with
     * a pillar through the middle of it is not a dais, and its centrepiece is exactly the cell the
     * column occupies.
     */
    @Test
    void theDaisIsDroppedRatherThanBuiltOverTheColumn() {
        Run run = build(COLLIDING);
        Set<Coords2D> shared = new HashSet<>(run.columns());
        shared.retainAll(run.daises());
        assertTrue(shared.isEmpty(), "a dais and a column share " + shared + ". A dais carries its "
                + "centrepiece on the middle of its footprint, so in game that is a brazier "
                + "skewered on a pillar -- the platform generator is not being handed the columns");
        assertTrue(run.daises().isEmpty(), "the dais was built with a bite taken out of it around "
                + "the column (" + run.daises() + "); the footprint check is meant to decline the "
                + "position outright");
    }

    private record Run(Set<Coords2D> columns, Set<Coords2D> daises) {}

    /**
     * One whole room, built the way the game builds it, read back off the emitted blocks.
     *
     * <p>The dais is found by its {@code top_block} (a lantern) and the column by its shaft, both
     * projected to x/z. Reading the OUTPUT rather than the generators' bookkeeping is deliberate:
     * the bookkeeping is what the two generators agree between themselves, and the symptom Mark
     * reported was in the blocks.</p>
     */
    private static Run build(String schemeJson) {
        RoomData room = new RoomData(1, ORIGIN, ORIGIN, SIZE, SIZE, HEIGHT, RoomRole.NORMAL);
        RoomPlacements out = new RoomPlacements();
        new BasicRoomGenerator().withMotifConfig(motif(schemeJson))
                .build(room, FLOOR_Y, 1, DungeonMotif.CLASSIC, RandomSource.create(7L), out);

        Set<Coords2D> columns = new LinkedHashSet<>();
        Set<Coords2D> daises = new LinkedHashSet<>();
        for (BlockPlacement placement : out.getBlocks()) {
            // The shaft runs the room's full interior height; the walls are the perimeter ring, so
            // anything stone-brick strictly inside it and above the floor is a column.
            boolean inside = placement.getX() > ORIGIN && placement.getX() < ORIGIN + SIZE - 1
                    && placement.getZ() > ORIGIN && placement.getZ() < ORIGIN + SIZE - 1;
            if (!inside) {
                continue;
            }
            if ("minecraft:lantern".equals(placement.getBlockId())) {
                daises.add(new Coords2D(placement.getX(), placement.getZ()));
            } else if ("minecraft:stone_bricks".equals(placement.getBlockId())
                    && placement.getY() > FLOOR_Y + 1) {
                columns.add(new Coords2D(placement.getX(), placement.getZ()));
            }
        }
        return new Run(columns, daises);
    }
}
