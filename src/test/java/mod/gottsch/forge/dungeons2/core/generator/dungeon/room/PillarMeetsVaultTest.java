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
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * <strong>A column under a rising vault reaches the vault.</strong>
 *
 * <p>Mark saw the {@code hall} scheme's grid columns end a block under the springing course with
 * four rows of air over their capitals (2026-10-24). Nothing overwrote them: the vault had cleared
 * the ceiling plane and the rows above it, and the column stopped where the plane used to be. Read
 * off the emitted blocks through {@link BasicRoomGenerator#build}, last write winning per cell, for
 * the reason {@link DaisAndColumnTest} gives &mdash; the symptom is in the blocks.</p>
 *
 * <p>Vanilla blocks throughout, since a {@code dungeonblocks:} shaft does not resolve headlessly
 * and a column that never draws passes every check below by finding nothing.</p>
 *
 * @author Mark Gottschling on Oct 24, 2026
 */
class PillarMeetsVaultTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int SIZE = 13;
    private static final int HEIGHT = 8;
    private static final int FLOOR_Y = 60;
    private static final int ORIGIN = 10;
    private static final int CEILING_Y = FLOOR_Y + HEIGHT - 1;

    private static final String SHAFT = "minecraft:oak_log";
    private static final String CAP = "minecraft:gold_block";
    private static final String CROWN = "minecraft:diamond_block";

    private static final String SCHEME = """
            {
              "wall": { "wall": "minecraft:stone_bricks" },
              "ceiling": { "ceiling": "minecraft:stone_bricks" },
              "floor": { "base": "minecraft:stone_bricks",
                         "alternate_base": "minecraft:stone_bricks" },
              "schemes": [ {
                "name": "vault_fixture",
                "pillars": { "patterns": [
                   { "type": "dungeons2:grid", "block": "%s", "cap_block": "%s",
                     "config": { "spacing": 4, "inset": 2 } } ] },
                "ceiling": { "patterns": [ %s ] }
              } ]
            }""";

    /** The hall's own vault, materials swapped for vanilla. */
    private static final String VAULT = """
            { "type": "dungeons2:vaulted", "config": { "block": "minecraft:stone_bricks",
              "springing_block": "minecraft:polished_andesite", "crown_block": "%s",
              "steps": 2, "step_height": 2 } }""".formatted(CROWN);

    private static final String FLAT = """
            { "type": "dungeons2:border", "config": { "block": "minecraft:polished_andesite" } }""";

    @Test
    void underAVaultTheCapitalSitsDirectlyUnderTheCrown() {
        Map<String, String> world = build(VAULT, 15);
        Set<Coords2D> columns = columns(world);
        assertFalse(columns.isEmpty(), "the fixture drew no column, so this proves nothing");
        for (Coords2D c : columns) {
            int crownY = lowest(world, c, CROWN);
            assertEquals(CAP, at(world, c, crownY - 1), "column " + c + " does not carry the crown");
            for (int y = FLOOR_Y + 2; y < crownY - 1; y++) {
                assertEquals(SHAFT, at(world, c, y), "column " + c + " has a gap at y " + y);
            }
        }
    }

    /** Nothing changes under an ordinary ceiling: the capital is still the row below the plane. */
    @Test
    void underAFlatCeilingTheColumnIsUnchanged() {
        Map<String, String> world = build(FLAT, 15);
        Set<Coords2D> columns = columns(world);
        assertFalse(columns.isEmpty());
        for (Coords2D c : columns) {
            assertEquals(CAP, at(world, c, CEILING_Y - 1));
            assertEquals("minecraft:stone_bricks", at(world, c, CEILING_Y));
        }
    }

    /** No spare budget: the vault draws flat, and so does the column. */
    @Test
    void aVaultClampedFlatLeavesTheColumnAtThePlane() {
        Map<String, String> world = build(VAULT, HEIGHT);
        for (Coords2D c : columns(world)) {
            assertEquals(CAP, at(world, c, CEILING_Y - 1));
            assertFalse("minecraft:air".equals(at(world, c, CEILING_Y)));
        }
    }

    private static Map<String, String> build(String ceiling, int ceilingBudget) {
        String json = SCHEME.formatted(SHAFT, CAP, ceiling);
        MotifConfigFragment fragment = MotifConfigFragment.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, error -> {
                    throw new AssertionError("the fixture no longer decodes: " + error);
                });
        MotifConfig motif = MotifConfigFragment.resolve(List.of(fragment));
        RoomData room = new RoomData(1, ORIGIN, ORIGIN, SIZE, SIZE, HEIGHT, RoomRole.NORMAL);
        RoomPlacements out = new RoomPlacements();
        new BasicRoomGenerator().withMotifConfig(motif).withCeilingBudget(ceilingBudget)
                .build(room, FLOOR_Y, 1, DungeonMotif.CLASSIC, RandomSource.create(7L), out);
        Map<String, String> world = new HashMap<>();
        for (BlockPlacement p : out.getBlocks()) {
            world.put(key(p.getX(), p.getY(), p.getZ()), p.getBlockId());
        }
        return world;
    }

    private static Set<Coords2D> columns(Map<String, String> world) {
        Set<Coords2D> columns = new LinkedHashSet<>();
        for (int x = ORIGIN + 1; x < ORIGIN + SIZE - 1; x++) {
            for (int z = ORIGIN + 1; z < ORIGIN + SIZE - 1; z++) {
                if (SHAFT.equals(world.get(key(x, FLOOR_Y + 3, z)))) {
                    columns.add(new Coords2D(x, z));
                }
            }
        }
        return columns;
    }

    private static int lowest(Map<String, String> world, Coords2D c, String block) {
        for (int y = CEILING_Y; y < CEILING_Y + 16; y++) {
            if (block.equals(at(world, c, y))) {
                return y;
            }
        }
        throw new AssertionError("no " + block + " over " + c + "; the column is not under the "
                + "vault's crown and the fixture needs moving");
    }

    private static String at(Map<String, String> world, Coords2D c, int y) {
        return world.get(key(c.getX(), y, c.getY()));
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
