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

import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fungus growth handoff: {@code floor_growth} emits a marker, {@code DungeonPiece} turns it into
 * a mob and never writes the block.
 *
 * <p>The wiring half is the one that matters. The two sides of this feature live in different
 * languages &mdash; a marker id in a datapack, a map in Java &mdash; and a mismatch does not throw:
 * a marker the map does not know is simply <strong>placed as a block</strong> and left standing in
 * the finished dungeon, which reads as a texture bug rather than as missing wiring.</p>
 *
 * <h2>Driven by id, because a marker BlockState cannot exist here</h2>
 * <p>Forge wraps {@code BuiltInRegistries.BLOCK} in a locked {@code NamespacedWrapper}, so unlike
 * the structure processor types {@code TestRegistries} registers, this mod's blocks cannot be added
 * to it headlessly &mdash; {@code Registry.register} throws "Can not register to a locked registry".
 * So these drive {@link FungusGrowth}'s id overloads, which is everything except the single
 * {@code getKey} call that turns a state into an id.</p>
 *
 * <p>One consequence worth knowing when reading other tests: in a headless run the two marker ids
 * resolve to {@code minecraft:air} inside the {@code floor_growth} palette, because GottschCore's
 * {@code BlockIds} codec degrades an unknown id rather than failing. The palette therefore has the
 * right <em>size</em> but grows nothing on the fungus draws. Nothing asserts on growth composition
 * today, so this costs nothing -- but it means a headless test can never see a fungus placed.</p>
 */
class FungusGrowthTest {

    private static final String WEATHERING =
            "/data/dungeons2/worldgen/processor_list/classic_weathering.json";

    @Test
    void everyMarkerItKnowsIsActuallyShipped() throws IOException {
        Set<String> inJson = markersInFloorGrowth();
        for (String id : FungusGrowth.markerIds()) {
            assertTrue(inJson.contains(id),
                    "FungusGrowth converts " + id + " but no shipped floor_growth list emits one,"
                            + " so the conversion is dead code");
        }
    }

    @Test
    void everyMarkerShippedIsOneItKnows() throws IOException {
        for (String id : markersInFloorGrowth()) {
            assertTrue(FungusGrowth.markerIds().contains(id),
                    "floor_growth grows " + id + " but FungusGrowth does not convert it, so it"
                            + " would be PLACED AS A BLOCK and left standing in the dungeon");
        }
    }

    @Test
    void aMarkerIsRecognisedAndAnOrdinaryPlantIsNot() {
        for (String id : FungusGrowth.markerIds()) {
            assertTrue(FungusGrowth.isMarker(id), id + " is not being recognised");
        }
        // The rest of the growth palette has to fall straight through, or the pass would start
        // eating its own mushrooms.
        assertFalse(FungusGrowth.isMarker("minecraft:brown_mushroom"));
        assertFalse(FungusGrowth.isMarker("minecraft:red_mushroom"));
        assertFalse(FungusGrowth.isMarker("minecraft:fern"));
        assertFalse(FungusGrowth.isMarker("minecraft:air"));
        assertFalse(FungusGrowth.isMarker((String) null));
        // A near-miss on the id must not match: these two differ only by suffix from the mob ids.
        assertFalse(FungusGrowth.isMarker("dungeons2:shrieker"));
        assertNull(FungusGrowth.toPlacement("minecraft:fern", new BlockPos(0, 0, 0), 0, 0));
    }

    @Test
    void theMarkerBecomesItsOwnMob() {
        EntityPlacement shrieker = FungusGrowth.toPlacement(
                "dungeons2:shrieker_marker", new BlockPos(10, 40, 20), 0, 0);
        assertNotNull(shrieker);
        assertEquals("dungeons2:shrieker", shrieker.getEntityId());

        EntityPlacement fungus = FungusGrowth.toPlacement(
                "dungeons2:violet_fungus_marker", new BlockPos(10, 40, 20), 0, 0);
        assertNotNull(fungus);
        assertEquals("dungeons2:violet_fungus", fungus.getEntityId());
    }

    /**
     * The anchor is subtracted from XZ and Y is left absolute, because that is the space
     * {@code DungeonPiece#placeEntities} re-adds the anchor in. Getting this wrong puts every
     * fungus in the dungeon at double the anchor offset, which is off the piece entirely and
     * therefore clipped away silently -- no fungi, no error.
     */
    @Test
    void coordinatesAreFloorLocalXzWithAbsoluteY() {
        EntityPlacement placement = FungusGrowth.toPlacement(
                "dungeons2:shrieker_marker", new BlockPos(137, 41, -64), 100, -80);
        assertNotNull(placement);
        assertEquals(37, placement.getX());
        assertEquals(41, placement.getY(), "Y must stay absolute");
        assertEquals(16, placement.getZ());
    }

    /**
     * postProcess runs once per chunk the piece overlaps and has to plan identically each time, or
     * the chunk-box clip in placeEntities stops being a correct exactly-once filter. A yaw drawn
     * from a shared stream would depend on visit order, which differs per chunk.
     */
    @Test
    void theYawIsAPureFunctionOfPosition() {
        BlockPos pos = new BlockPos(9, 33, -12);
        float first = FungusGrowth.toPlacement("dungeons2:shrieker_marker", pos, 0, 0)
                .getYRot();
        for (int i = 0; i < 20; i++) {
            assertEquals(first,
                    FungusGrowth.toPlacement("dungeons2:shrieker_marker", pos, 0, 0).getYRot(),
                    "the same cell produced a different yaw on re-run " + i);
        }
        assertTrue(first >= 0.0F && first < 360.0F, "yaw out of range: " + first);

        // ...and it must actually vary, or every fungus in the dungeon faces the same way.
        Set<Float> yaws = new LinkedHashSet<>();
        for (int x = 0; x < 40; x++) {
            yaws.add(FungusGrowth.toPlacement("dungeons2:shrieker_marker",
                    new BlockPos(x, 33, -12), 0, 0).getYRot());
        }
        assertTrue(yaws.size() > 30,
                "only " + yaws.size() + " distinct yaws over 40 cells, so the position seed is"
                        + " barely reaching the draw");
    }

    /** These are killed for their own drops; a loot table here would override the mob's. */
    @Test
    void aFungusCarriesNoLootTable() {
        EntityPlacement placement = FungusGrowth.toPlacement(
                "dungeons2:shrieker_marker", new BlockPos(1, 2, 3), 0, 0);
        assertNull(placement.getLootTable());
    }

    /** Every {@code dungeons2:*_marker} named inside the shipped {@code floor_growth} palette. */
    private static Set<String> markersInFloorGrowth() throws IOException {
        String json = read();
        int start = json.indexOf("\"floor_growth\"");
        assertTrue(start >= 0, "the shipped weathering list has no floor_growth block any more");
        int blocks = json.indexOf('[', start);
        int end = json.indexOf(']', blocks);
        String palette = json.substring(blocks, end);

        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\"(dungeons2:[a-z0-9_]+)\"").matcher(palette);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String read() throws IOException {
        try (InputStream in = FungusGrowthTest.class.getResourceAsStream(WEATHERING)) {
            assertNotNull(in, "missing " + WEATHERING);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
