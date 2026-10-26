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

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigFragment;
import mod.gottsch.forge.dungeons2.core.world.structure.MotifBiomes.Paint;
import mod.gottsch.forge.dungeons2.core.world.structure.MotifBiomes.PieceBox;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which cells {@link MotifBiomes} paints (#129): piece boxes only, clipped to the chunk, a pool
 * piece split by floor height, nothing above the dungeon's own pieces.
 *
 * @author Mark Gottschling on 10/26/2026
 */
class MotifBiomesTest {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Chunk (0, 0), full height. */
    private static final BoundingBox CHUNK = new BoundingBox(0, -64, 0, 15, 319, 15);

    /** Floor n's biome is "f<n>". */
    private static Optional<String> floorBiome(int floorIndex) {
        return Optional.of("f" + floorIndex);
    }

    @Test
    void aD2PieceIsPaintedWithItsOwnFloorClippedToTheChunk() {
        List<Paint<String>> paints = MotifBiomes.plan(
                List.of(new PieceBox(new BoundingBox(10, 20, 4, 30, 28, 9), OptionalInt.of(2))),
                CHUNK, MotifBiomesTest::floorBiome, MotifBiomesTest::noTemplateBiome);
        assertEquals(List.of(new Paint<>(new BoundingBox(10, 20, 4, 15, 28, 9), "f2")), paints);
    }

    @Test
    void aPieceOutsideTheChunkPaintsNothing() {
        List<Paint<String>> paints = MotifBiomes.plan(
                List.of(new PieceBox(new BoundingBox(16, 20, 0, 30, 28, 9), OptionalInt.of(0))),
                CHUNK, MotifBiomesTest::floorBiome, MotifBiomesTest::noTemplateBiome);
        assertTrue(paints.isEmpty(), paints.toString());
    }

    /**
     * A transition spanning floors 0 and 1 is split at floor 0's bottom, and the part of a pool
     * piece above floor 0's highest D2 box (the surface entrance) is not painted at all.
     */
    @Test
    void aPoolPieceIsSplitByFloorAndCappedAtTheDungeonTop() {
        PieceBox floor0 = new PieceBox(new BoundingBox(0, 40, 0, 8, 50, 8), OptionalInt.of(0));
        PieceBox floor1 = new PieceBox(new BoundingBox(0, 20, 0, 8, 30, 8), OptionalInt.of(1));
        PieceBox pool = new PieceBox(new BoundingBox(10, 10, 10, 12, 90, 12), OptionalInt.empty());
        List<Paint<String>> paints = MotifBiomes.plan(List.of(pool, floor0, floor1), CHUNK,
                MotifBiomesTest::floorBiome, MotifBiomesTest::noTemplateBiome);
        assertEquals(List.of(
                new Paint<>(new BoundingBox(10, 40, 10, 12, 50, 12), "f0"),
                // the deepest floor runs downward without limit
                new Paint<>(new BoundingBox(10, 10, 10, 12, 39, 12), "f1"),
                new Paint<>(floor0.box(), "f0"),
                new Paint<>(floor1.box(), "f1")), paints);
    }

    private static Optional<String> noTemplateBiome(String templateId) {
        return Optional.empty();
    }

    /**
     * A prefab whose template has its own biome paints its whole box with it (#129 step 2), still
     * under the dungeon-top cap; an unmapped prefab beside it keeps its floor's.
     */
    @Test
    void aMappedPrefabPaintsItsOwnBiome() {
        PieceBox floor0 = new PieceBox(new BoundingBox(0, 40, 0, 8, 50, 8), OptionalInt.of(0));
        PieceBox sewer = new PieceBox(new BoundingBox(10, 30, 0, 14, 60, 4), OptionalInt.empty(),
                Optional.of("test:rooms/sewer"));
        PieceBox hall = new PieceBox(new BoundingBox(10, 40, 6, 14, 45, 9), OptionalInt.empty(),
                Optional.of("test:rooms/hall"));
        List<Paint<String>> paints = MotifBiomes.plan(List.of(sewer, hall, floor0), CHUNK,
                MotifBiomesTest::floorBiome,
                id -> id.equals("test:rooms/sewer") ? Optional.of("flooded") : Optional.empty());
        assertEquals(List.of(
                new Paint<>(new BoundingBox(10, 30, 0, 14, 50, 4), "flooded"),
                new Paint<>(hall.box(), "f0"),
                new Paint<>(floor0.box(), "f0")), paints);
    }

    /**
     * An L-shaped corridor paints its own cell columns, not the rectangle around them: the far corner
     * of its box is rock (or another room) it never touches. Seen in game as a sewer prefab inside a
     * corridor's box painted half the band's biome.
     */
    @Test
    void aCorridorPaintsItsCellsNotItsBox() {
        BoundingBox box = new BoundingBox(0, 20, 0, 15, 24, 15);
        List<BoundingBox> cells = new java.util.ArrayList<>();
        for (int x = 0; x <= 15; x++) {
            cells.add(new BoundingBox(x, 20, 0, x, 24, 0));
        }
        for (int z = 1; z <= 15; z++) {
            cells.add(new BoundingBox(0, 20, z, 0, 24, z));
        }
        List<Paint<String>> paints = MotifBiomes.plan(
                List.of(new PieceBox(box, OptionalInt.of(1), Optional.empty(), cells)),
                CHUNK, MotifBiomesTest::floorBiome, MotifBiomesTest::noTemplateBiome);
        assertEquals(cells.size(), paints.size());
        assertEquals("f1", MotifBiomes.cellBiome(paints, 0, 5, 0));
        assertEquals("f1", MotifBiomes.cellBiome(paints, 3, 5, 0));
        assertEquals("f1", MotifBiomes.cellBiome(paints, 0, 5, 3));
        assertNull(MotifBiomes.cellBiome(paints, 3, 5, 3), "the far corner of the box is not corridor");
    }

    @Test
    void aFloorWithNoBiomeIsLeftAlone() {
        List<Paint<String>> paints = MotifBiomes.plan(
                List.of(new PieceBox(new BoundingBox(0, 20, 0, 8, 28, 8), OptionalInt.of(1))),
                CHUNK, floor -> Optional.empty(), MotifBiomesTest::noTemplateBiome);
        assertTrue(paints.isEmpty(), paints.toString());
    }

    @Test
    void aStartWithNoD2PiecesPaintsNothing() {
        List<Paint<String>> paints = MotifBiomes.plan(
                List.of(new PieceBox(new BoundingBox(0, 20, 0, 8, 28, 8), OptionalInt.empty())),
                CHUNK, MotifBiomesTest::floorBiome, MotifBiomesTest::noTemplateBiome);
        assertTrue(paints.isEmpty(), paints.toString());
    }

    /** A cell is painted when it overlaps the box at all; the next cell out is not. */
    @Test
    void aCellIsPaintedWhenItOverlapsTheBox() {
        // blocks x 5..9 touch quart cells 1 (4..7) and 2 (8..11), not 0 or 3
        List<Paint<String>> paints = List.of(new Paint<>(new BoundingBox(5, 5, 5, 9, 9, 9), "a"));
        assertEquals("a", MotifBiomes.cellBiome(paints, 1, 1, 1));
        assertEquals("a", MotifBiomes.cellBiome(paints, 2, 2, 2));
        assertNull(MotifBiomes.cellBiome(paints, 0, 1, 1));
        assertNull(MotifBiomes.cellBiome(paints, 3, 1, 1));
        assertNull(MotifBiomes.cellBiome(paints, 1, 1, 3));
    }

    @Test
    void aLaterPaintWinsASharedCell() {
        List<Paint<String>> paints = List.of(
                new Paint<>(new BoundingBox(0, 0, 0, 7, 7, 7), "corridor"),
                new Paint<>(new BoundingBox(4, 0, 0, 11, 7, 7), "room"));
        assertEquals("corridor", MotifBiomes.cellBiome(paints, 0, 0, 0));
        assertEquals("room", MotifBiomes.cellBiome(paints, 1, 0, 0));
    }

    /** A band's biome wins; a band without one falls through to the MOTIF's, not the band above. */
    @Test
    void biomeForFallsThroughToTheMotif() {
        MotifConfigFragment fragment = MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "biome": "test:motif",
                  "strata_by_floor_index": [
                    { "min_floor_index": 0, "biome": "test:top" },
                    { "min_floor_index": 1 },
                    { "min_floor_index": 3, "biome": "test:bottom" }
                  ]
                }""")).getOrThrow(false, message -> { throw new AssertionError(message); });
        MotifConfig motif = MotifConfigFragment.resolve(List.of(fragment));
        assertEquals(Optional.of(new ResourceLocation("test:top")), motif.biomeFor(0));
        assertEquals(Optional.of(new ResourceLocation("test:motif")), motif.biomeFor(1));
        assertEquals(Optional.of(new ResourceLocation("test:motif")), motif.biomeFor(2));
        assertEquals(Optional.of(new ResourceLocation("test:bottom")), motif.biomeFor(9));
        assertEquals(Optional.empty(), MotifConfig.DEFAULT.biomeFor(0));
    }
}
