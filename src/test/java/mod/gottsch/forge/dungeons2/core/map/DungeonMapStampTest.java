package mod.gottsch.forge.dungeons2.core.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonPieceEmitter;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonRoomPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor a Dungeon Map is made from: every room carries its own floor's stamp, the stamp covers
 * the room and survives NBT, every floor above the bottom marks its stairs down, and the shallow and
 * deep chests can hand one out.
 *
 * @author Mark Gottschling on 10/25/2026
 */
class DungeonMapStampTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRoomCarriesItsOwnFloor() {
        for (DungeonSize size : DungeonSize.values()) {
            DungeonLayout layout = plan(0xDA_7A_11L + size.ordinal(), size);
            List<FloorLayout> floors = layout.getFloors();
            int bottom = floors.stream().mapToInt(FloorLayout::getFloorIndex).max().orElseThrow();
            List<DungeonRoomPiece> rooms = rooms(layout, 100, -40);
            assertFalse(rooms.isEmpty(), size.name());
            for (DungeonRoomPiece room : rooms) {
                FloorMapStamp stamp = room.getFloorMap();
                assertNotNull(stamp, size + " room " + room.getRoom().getId());
                assertEquals(room.getFloorIndex(), stamp.floorIndex());
                FloorLayout floor = floors.stream().filter(f -> f.getFloorIndex() == stamp.floorIndex())
                        .findFirst().orElseThrow();
                assertEquals(floor.getFloorY(), stamp.floorY());
                // the room's interior lies on the map: its box is the interior plus a wall all round
                BoundingBox box = room.getBoundingBox();
                assertTrue(box.minX() + 1 >= stamp.minX() && box.maxX() - 1 <= stamp.maxX()
                        && box.minZ() + 1 >= stamp.minZ() && box.maxZ() - 1 <= stamp.maxZ(),
                        size + " room " + room.getRoom().getId() + " is off its floor's map");
                assertEquals(stamp.floorIndex() != bottom, stamp.down() != null,
                        size + " floor " + stamp.floorIndex() + ": stairs down only above the bottom");
                assertEquals(0, stamp.scale(), size + ": a floor fits a one-block-a-pixel map");
            }
        }
    }

    @Test
    void theStampRoundTrips() {
        FloorMapStamp stamp = new FloorMapStamp(2, -31, 10, -20, 84, 50, new int[]{40, 0, 46, 6});
        FloorMapStamp back = FloorMapStamp.load(stamp.save());
        assertNotNull(back);
        assertEquals(stamp.floorIndex(), back.floorIndex());
        assertEquals(stamp.floorY(), back.floorY());
        assertEquals(stamp.minX(), back.minX());
        assertEquals(stamp.maxZ(), back.maxZ());
        assertArrayEquals(stamp.down(), back.down());
        assertTrue(back.isStairsDown(40, 6));
        assertFalse(back.isStairsDown(47, 6));

        FloorMapStamp bottom = FloorMapStamp.load(new FloorMapStamp(3, 0, 0, 0, 10, 10, null).save());
        assertNotNull(bottom);
        assertNull(bottom.down());
        assertFalse(bottom.isStairsDown(5, 5));
    }

    @Test
    void theScaleGrowsOnlyPastAMap() {
        assertEquals(0, new FloorMapStamp(0, 0, 0, 0, 120, 60, null).scale());
        assertEquals(1, new FloorMapStamp(0, 0, 0, 0, 200, 60, null).scale());
    }

    @Test
    void theFloorContainersHandOutMaps() throws Exception {
        for (String table : List.of("chests/classic_shallow", "chests/classic_deep", "barrels/classic", "pots/classic")) {
            int pools = 0;
            for (JsonElement pool : read("/data/dungeons2/loot_tables/" + table + ".json").getAsJsonArray("pools")) {
                if (pool.toString().contains("\"dungeons2:dungeon_map\"")) {
                    pools++;
                    assertTrue(pool.toString().contains("\"minecraft:filled_map\""), table);
                }
            }
            assertEquals(1, pools, table + " should carry exactly one map pool");
        }
    }

    private static DungeonLayout plan(long seed, DungeonSize size) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                .withSize(size)
                .withCorridorWidth(3)
                .withCorridorStyles(DungeonStructure.corridorStyleWeights(MotifConfigs.load("classic").corridor()))
                .plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
    }

    private static List<DungeonRoomPiece> rooms(DungeonLayout layout, int anchorX, int anchorZ) {
        List<StructurePiece> pieces = DungeonPieceEmitter.emitTerrain(layout, anchorX, anchorZ, 0, 0, null, null);
        return pieces.stream().filter(DungeonRoomPiece.class::isInstance).map(DungeonRoomPiece.class::cast).toList();
    }

    private static JsonObject read(String path) throws Exception {
        try (InputStream in = DungeonMapStampTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
