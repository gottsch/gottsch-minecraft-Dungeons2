package mod.gottsch.forge.dungeons2.core.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonPieceEmitter;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonRoomPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The boss-tier lore books: each tier's book is wired end to end, the condition refuses a tier
 * that does not exist, and only the rooms above a dungeon's boss carry the stamp it reads.
 *
 * @author Mark Gottschling on 10/22/2026
 */
class BossTierBooksTest {

    /** Each tier's book. */
    private static final Map<String, String> BOOKS = Map.of(
            "small", "sextons_ledger",
            "medium", "branns_account",
            "large", "the_last_delve");

    /** Every table a chest or tomb above the boss rolls. */
    private static final List<String> TABLES = List.of(
            "classic_shallow", "classic_tomb_shallow", "classic_deep", "classic_tomb_deep");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theTiersAreTheDungeonSizes() {
        for (DungeonSize size : DungeonSize.values()) {
            assertTrue(BossTierCondition.TIERS.contains(BossTierCondition.tierOf(size)), size.name());
        }
        assertEquals(BOOKS.keySet(), BossTierCondition.TIERS);
    }

    /** A typo'd tier would be a book that never drops anywhere, so it fails the load instead. */
    @Test
    void anUnknownTierIsALoadError() {
        BossTierCondition.Serializer serializer = new BossTierCondition.Serializer();
        assertEquals("small", serializer.deserialize(json("{\"tier\":\"small\"}"), null).tier());
        assertThrows(JsonSyntaxException.class,
                () -> serializer.deserialize(json("{\"tier\":\"huge\"}"), null));
    }

    /** Book text, server entry, lang, both advancements, and a gated pool in every table. */
    @Test
    void everyTierBookIsWiredEndToEnd() throws Exception {
        JsonObject lang = read("/assets/dungeons2/lang/en_us.json");
        for (Map.Entry<String, String> entry : BOOKS.entrySet()) {
            String tier = entry.getKey();
            String book = entry.getValue();
            assertNotNull(stream("/assets/dungeons2/books/" + book + "/en_us/text.md"), book + " text");
            assertNotNull(stream("/data/dungeons2/gottschbooks/book/" + book + ".json"), book + " entry");
            assertNotNull(stream("/data/dungeons2/advancements/" + book + ".json"), book + " read");
            assertNotNull(stream("/data/dungeons2/advancements/lore/found_" + book + ".json"), book + " found");
            assertTrue(lang.has("book.dungeons2." + book), book + " lang");

            for (String table : TABLES) {
                JsonArray pools = read("/data/dungeons2/loot_tables/chests/" + table + ".json")
                        .getAsJsonArray("pools");
                int found = 0;
                for (JsonElement pool : pools) {
                    String text = pool.toString();
                    if (text.contains("\"dungeons2:boss_tier\"") && text.contains("\"tier\":\"" + tier + "\"")) {
                        found++;
                        assertTrue(text.contains("dungeons2:lore/found_" + book),
                                table + ": the " + tier + " pool is not gated on its own book");
                        assertTrue(text.contains("dungeons2:" + book + "\\\""),
                                table + ": the " + tier + " pool hands out the wrong book");
                    }
                }
                assertEquals(1, found, table + " should carry exactly one " + tier + " pool");
            }
        }
    }

    /**
     * Every lore entry gated on a {@code lore/found_<book>} advancement also grants it as it rolls,
     * so a second chest opened before the first copy is picked up cannot give the book again.
     */
    @Test
    void everyLoreBookIsMarkedFoundAsItRolls() throws Exception {
        int entries = 0;
        for (String table : List.of("classic_shallow", "classic_tomb_shallow", "classic_deep",
                "classic_tomb_deep", "classic_entrance")) {
            for (JsonElement pool : read("/data/dungeons2/loot_tables/chests/" + table + ".json").getAsJsonArray("pools")) {
                for (JsonElement element : pool.getAsJsonObject().getAsJsonArray("entries")) {
                    JsonObject entry = element.getAsJsonObject();
                    String conditions = String.valueOf(entry.get("conditions"));
                    int at = conditions.indexOf("dungeons2:lore/found_");
                    if (at < 0) {
                        continue;
                    }
                    entries++;
                    String advancement = conditions.substring(at, conditions.indexOf('"', at));
                    String functions = String.valueOf(entry.get("functions"));
                    assertTrue(functions.contains("\"dungeons2:grant_advancement\"")
                                    && functions.contains("\"" + advancement + "\""),
                            table + ": the entry gated on " + advancement + " does not grant it");
                }
            }
        }
        // the intro book in three tables, and the three tier books in four
        assertEquals(3 + 3 * 4, entries);
    }

    /**
     * The stamp goes on the rooms ABOVE the boss -- every NORMAL room -- and never on the boss room,
     * and a dungeon with no boss stamps nothing.
     */
    @Test
    void onlyRoomsAboveTheBossAreStamped() {
        for (DungeonSize size : DungeonSize.values()) {
            List<DungeonRoomPiece> rooms = rooms(plan(0x5EED_B00CL + size.ordinal(), size, "dungeons2:minotaur"));
            assertTrue(rooms.stream().anyMatch(r -> r.getRoom().getRole() == RoomRole.NORMAL), size.name());
            for (DungeonRoomPiece room : rooms) {
                String expected = room.getRoom().getRole() == RoomRole.NORMAL
                        ? BossTierCondition.tierOf(size) : null;
                assertEquals(expected, room.getBossTier(), size + " room " + room.getRoom().getId());
            }
        }
        for (DungeonRoomPiece room : rooms(plan(0x5EED_B00CL, DungeonSize.MEDIUM, null))) {
            assertNull(room.getBossTier(), "no boss, no stamp");
        }
    }

    private static DungeonLayout plan(long seed, DungeonSize size, String boss) {
        DungeonLayout layout = new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72, "classic",
                new TemplateCatalog())
                .withSize(size)
                .withCorridorWidth(3)
                .withCorridorStyles(DungeonStructure.corridorStyleWeights(
                        MotifConfigs.load("classic").corridor()))
                .plan().orElseThrow(() -> new AssertionError("planner returned empty for seed " + seed));
        layout.setBoss(boss);
        return layout;
    }

    private static List<DungeonRoomPiece> rooms(DungeonLayout layout) {
        List<StructurePiece> pieces = DungeonPieceEmitter.emitTerrain(layout, 0, 0, 0, 0, null, null);
        return pieces.stream().filter(DungeonRoomPiece.class::isInstance)
                .map(DungeonRoomPiece.class::cast).toList();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static InputStream stream(String path) {
        return BossTierBooksTest.class.getResourceAsStream(path);
    }

    private static JsonObject read(String path) throws Exception {
        try (InputStream in = stream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
