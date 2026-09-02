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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfacePatternEntry;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CeilingPattern;
import mod.gottsch.forge.dungeons2.core.config.ceiling.CeilingPatternRegistry;
import mod.gottsch.forge.dungeons2.core.config.ceiling.OculusCeilingPattern;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code oculus} (#77): a lit shaft over the middle of the room.
 *
 * <h2>What actually needs proving</h2>
 * <p>The geometry is #68's, already covered by {@code RisingVaultTest}. What is new, and what these
 * tests are about, is the <strong>stack</strong>: a lamp at the top with a grate one row under it.
 * That comes entirely out of the order the two layers are added, because each raised layer clears
 * every row from the ceiling plane up to its own before writing itself &mdash; so cap-then-grate
 * leaves both standing and grate-then-cap erases the grate. Neither the shaft nor the lamp would
 * look wrong on its own; only the pair would be missing a piece.</p>
 */
class OculusTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final String CAP = "minecraft:glowstone";
    private static final String GRATE = "minecraft:iron_bars";
    private static final String AIR = "minecraft:air";
    private static final String PLAIN_CEILING = "minecraft:stone_bricks";

    private static final int FLOOR_Y = 60;

    /** 9x9, interior 7x7, 6 high: the ceiling plane sits at floorY + 5. */
    private static RoomData room() {
        return new RoomData(1, 10, 20, 9, 9, 6, RoomRole.NORMAL);
    }

    private static int ceilingY() {
        return FLOOR_Y + room().getHeight() - 1;
    }

    private static SurfacePatternEntry oculus(OculusCeilingPattern pattern) {
        return new SurfacePatternEntry(pattern, 0, 0, SizeGate.UNBOUNDED);
    }

    private static Map<String, String> render(int riseBudget, OculusCeilingPattern pattern) {
        List<BlockPlacement> out = new ArrayList<>();
        new BasicCeilingGenerator()
                .withRiseBudget(riseBudget)
                .withCeilingPattern(CeilingPatternSelector.providerFor(
                        Optional.of(new CeilingPatternEntry(List.of(oculus(pattern))))))
                .build(room(), FLOOR_Y, DungeonMotif.CLASSIC, RandomSource.create(1L), out);
        // Last placement in a cell wins -- the renderer's own rule, and the rule the whole stack
        // depends on.
        Map<String, String> world = new HashMap<>();
        for (BlockPlacement bp : out) {
            world.put(bp.getX() + "," + bp.getY() + "," + bp.getZ(), bp.getBlockId());
        }
        return world;
    }

    private static String at(Map<String, String> world, int x, int y, int z) {
        return world.get(x + "," + y + "," + z);
    }

    // ---- the stack ------------------------------------------------------------------------------

    /**
     * The middle of the interior, bottom to top: the plane is opened, the shaft is air, the grate is
     * one row under the cap, and the cap is on top.
     *
     * <p>The surface's origin is the room's plus one (the wall ring is not ceiling), so interior
     * {@code (3, 3)} is world {@code (14, 24)} &mdash; worth spelling out, because an off-by-one
     * here silently tests the cell next door, which the shaft also covers.</p>
     */
    @Test
    void theShaftIsOpenedWithTheGrateOneRowUnderTheCap() {
        Map<String, String> world = render(4,
                new OculusCeilingPattern(CAP, Optional.of(GRATE), 3, 3, Map.of()));
        int ceilingY = ceilingY();

        assertEquals(AIR, at(world, 14, ceilingY, 24),
                "the plane cell was left paved, so there is no shaft at all");
        assertEquals(AIR, at(world, 14, ceilingY + 1, 24));
        assertEquals(GRATE, at(world, 14, ceilingY + 2, 24), "the grate hides the lamp");
        assertEquals(CAP, at(world, 14, ceilingY + 3, 24), "and the lamp is above it");
    }

    /**
     * The order is what makes the pair work, so it is worth asserting on the LAYERS as well as on
     * the world: the cap has to be added first, or its taller excavation clears the grate away.
     */
    @Test
    void theCapLayerIsAddedBeforeTheGrateLayer() {
        List<CeilingPatternSelector.Layer> layers = new ArrayList<>();
        new OculusCeilingPattern(CAP, Optional.of(GRATE), 3, 3, Map.of()).addLayers(0, layers);
        assertEquals(2, layers.size());
        assertEquals(-3, layers.get(0).depth(), "the cap rises the full depth");
        assertEquals(-2, layers.get(1).depth(), "the grate one row under it");
    }

    /** Without a grate it is a bare lit shaft: air all the way up to the cap. */
    @Test
    void withNoGrateTheShaftIsOpenToTheLamp() {
        Map<String, String> world = render(4, new OculusCeilingPattern(CAP));
        int ceilingY = ceilingY();
        assertEquals(AIR, at(world, 14, ceilingY, 24));
        assertEquals(AIR, at(world, 14, ceilingY + 1, 24));
        assertEquals(CAP, at(world, 14, ceilingY + 2, 24));
    }

    /** The shaft is centred and `size` cells across; the ceiling outside it is untouched. */
    @Test
    void onlyTheCentredShaftIsOpened() {
        Map<String, String> world = render(4,
                new OculusCeilingPattern(CAP, Optional.of(GRATE), 3, 2, Map.of()));
        int ceilingY = ceilingY();
        // A 3-wide shaft in a 7x7 interior: u,v 2..4, i.e. world x,z 13..15 / 23..25.
        assertEquals(AIR, at(world, 13, ceilingY, 23), "the shaft's near corner");
        assertEquals(AIR, at(world, 15, ceilingY, 25), "and its far corner");
        assertEquals(PLAIN_CEILING, at(world, 12, ceilingY, 24), "one cell outside it is still ceiling");
        assertNull(at(world, 12, ceilingY + 1, 24),
                "and nothing is written above a cell the shaft does not cover");
    }

    // ---- the shallow-room degrade ---------------------------------------------------------------

    /**
     * With one row of budget the cap and the grate land on the same cell, the grate wins, and the
     * oculus reads as an unlit grille set just above the ceiling. With none it flattens into the
     * plane. Both are a dark grate rather than a hole into rock, which is the right way for this to
     * fail in a room whose floor has nothing to spare.
     */
    @Test
    void aRoomWithNoSpareBudgetGetsAGrateRatherThanAHole() {
        int ceilingY = ceilingY();

        Map<String, String> oneRow = render(1,
                new OculusCeilingPattern(CAP, Optional.of(GRATE), 3, 3, Map.of()));
        assertEquals(AIR, at(oneRow, 14, ceilingY, 24), "one row of shaft is still opened");
        assertEquals(GRATE, at(oneRow, 14, ceilingY + 1, 24), "and the grate caps it");

        Map<String, String> noRows = render(0,
                new OculusCeilingPattern(CAP, Optional.of(GRATE), 3, 3, Map.of()));
        assertEquals(GRATE, at(noRows, 14, ceilingY, 24), "flat: a grate set in the ceiling plane");
        assertNull(at(noRows, 14, ceilingY + 1, 24), "and nothing opened above it");
    }

    // ---- the schema -----------------------------------------------------------------------------

    private static DataResult<CeilingPattern> parse(String json) {
        return CeilingPatternRegistry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void theTypeIsRegisteredAndDecodesFromItsAuthoredForm() {
        DataResult<CeilingPattern> result = parse("""
                {
                  "type": "dungeons2:oculus",
                  "config": {
                    "cap_block": "minecraft:glowstone",
                    "grate_block": "dungeonblocks:dark_iron_grate",
                    "size": 3, "depth": 3
                  }
                }""");
        OculusCeilingPattern pattern = (OculusCeilingPattern) result.result().orElseThrow(
                () -> new AssertionError(result.error().map(Object::toString).orElse("")));
        assertEquals(3, pattern.size());
        assertEquals(3, pattern.depth());
        assertEquals("dungeonblocks:dark_iron_grate", pattern.grateBlock().orElseThrow());
    }

    @Test
    void theCapIsRequired() {
        assertTrue(parse("""
                {"type": "dungeons2:oculus", "config": {"grate_block": "minecraft:iron_bars"}}""")
                .result().isEmpty(), "an oculus with no cap is not a dark shaft, it is no shaft");
    }

    /**
     * A depth of one is a load error. At one the grate would sit in the ceiling PLANE, which is a
     * flush layer -- and flush layers are written before the raised ones excavate, so the grate
     * would be cleared away by its own shaft. Rejecting is better than a silently empty hole.
     */
    @Test
    void aDepthOfOneIsALoadError() {
        assertTrue(parse("""
                {"type": "dungeons2:oculus",
                 "config": {"cap_block": "minecraft:glowstone", "depth": 1}}""").result().isEmpty());
    }

    @Test
    void aDepthPastTheMaximumRiseIsALoadError() {
        assertTrue(parse("""
                {"type": "dungeons2:oculus",
                 "config": {"cap_block": "minecraft:glowstone", "depth": %d}}"""
                .formatted(CeilingPatternEntry.MAX_RISE + 1)).result().isEmpty());
    }

    @Test
    void aStrayKeyIsALoadError() {
        assertTrue(parse("""
                {"type": "dungeons2:oculus",
                 "config": {"cap_block": "minecraft:glowstone", "dpeth": 2}}""").result().isEmpty());
    }

    @Test
    void bothBlockFieldsReadAMaterialRole() {
        OculusCeilingPattern resolved = (OculusCeilingPattern)
                new OculusCeilingPattern("$lamp", "$grille")
                        // The resolver is handed the role NAME, already stripped of its $.
                        .withRoles(role -> "minecraft:" + role);
        assertEquals("minecraft:lamp", resolved.capBlock());
        assertEquals("minecraft:grille", resolved.grateBlock().orElseThrow());
        assertEquals(OculusCeilingPattern.DEFAULT_SIZE, resolved.size(),
                "and keeps what it did not resolve");
    }

    @Test
    void anOculusOfLiteralsIsNotEvenCopied() {
        OculusCeilingPattern pattern = new OculusCeilingPattern(CAP, GRATE);
        assertSame(pattern, pattern.withRoles(role -> "minecraft:dirt"));
    }
}
