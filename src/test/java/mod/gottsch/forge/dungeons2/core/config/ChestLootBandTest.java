package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chest depth axis. Backlog #48 step 2.
 *
 * <p>Mirrors {@code MobSetBand}'s behaviour deliberately, so these assertions are the same
 * assertions: bands open-ended downward, the deepest started band wins, and a scheme that names its
 * own tables keeps them.</p>
 */
class ChestLootBandTest {

    private static ChestConfig.LootTableEntry entry(String table) {
        return new ChestConfig.LootTableEntry(table, 1);
    }

    private static final List<ChestLootBand> TABLE = List.of(
            new ChestLootBand(0, List.of(entry("d2:shallow"))),
            new ChestLootBand(2, List.of(entry("d2:deep"))),
            new ChestLootBand(4, List.of(entry("d2:hoard"))));

    @Test
    void theDeepestStartedBandWins() {
        assertEquals("d2:shallow", ChestLootBand.forFloor(TABLE, 0).orElseThrow().lootTables().get(0).lootTable());
        assertEquals("d2:shallow", ChestLootBand.forFloor(TABLE, 1).orElseThrow().lootTables().get(0).lootTable());
        assertEquals("d2:deep", ChestLootBand.forFloor(TABLE, 2).orElseThrow().lootTables().get(0).lootTable());
        assertEquals("d2:deep", ChestLootBand.forFloor(TABLE, 3).orElseThrow().lootTables().get(0).lootTable());
        assertEquals("d2:hoard", ChestLootBand.forFloor(TABLE, 4).orElseThrow().lootTables().get(0).lootTable());
    }

    /**
     * The property that makes an uncovered floor unrepresentable: the deepest band runs forever, so
     * a dungeon deeper than the author imagined still has loot rather than empty chests.
     */
    @Test
    void theDeepestBandCoversEveryFloorBelowIt() {
        assertEquals("d2:hoard", ChestLootBand.forFloor(TABLE, 40).orElseThrow().lootTables().get(0).lootTable());
    }

    @Test
    void anEmptyTableCoversNothing() {
        assertTrue(ChestLootBand.forFloor(List.of(), 0).isEmpty());
    }

    /** A scheme naming its own tables is a treasury on every floor; the band must not overwrite it. */
    @Test
    void aSchemeThatNamesItsOwnTablesKeepsThem() {
        ChestConfig scheme = new ChestConfig(1, 1, "d2:treasury",
                List.of(new ChestConfig.ChestVariant("minecraft:chest", 1)));
        ChestConfig resolved = scheme.resolvedAgainst(ChestLootBand.forFloor(TABLE, 4));
        assertEquals(List.of(entry("d2:treasury")), resolved.declaredLootTables());
    }

    @Test
    void aSchemeThatNamesNoneTakesTheBands() {
        ChestConfig scheme = new ChestConfig(1, 1, Optional.empty(),
                List.of(new ChestConfig.ChestVariant("minecraft:chest", 1)));
        assertTrue(scheme.declaredLootTables().isEmpty(), "unresolved, it would place nothing");

        ChestConfig resolved = scheme.resolvedAgainst(ChestLootBand.forFloor(TABLE, 2));
        assertEquals(List.of(entry("d2:deep")), resolved.declaredLootTables());
    }

    /**
     * No band and no scheme tables means no chest, NOT an empty chest. The generator relies on this
     * returning empty; see {@code RoomChestGenerator#placeChests}.
     */
    @Test
    void nothingAnywhereResolvesToNoTables() {
        ChestConfig scheme = new ChestConfig(1, 1, Optional.empty(),
                List.of(new ChestConfig.ChestVariant("minecraft:chest", 1)));
        assertTrue(scheme.resolvedAgainst(Optional.empty()).declaredLootTables().isEmpty());
    }

    /** An empty band is a load error: it would generate chests that hold nothing. */
    @Test
    void anEmptyBandIsALoadError() {
        var result = ChestLootBand.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"min_floor_index\": 0, \"loot_tables\": []}"));
        assertTrue(result.error().isPresent(), "an empty loot_tables list must not load");
    }

    @Test
    void aBandRoundTripsThroughItsCodec() {
        String json = "{\"min_floor_index\": 2, \"loot_tables\": ["
                + "{\"loot_table\": \"d2:deep\", \"weight\": 8},"
                + "{\"loot_table\": \"d2:shallow\"}]}";
        ChestLootBand band = ChestLootBand.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(false, err -> { throw new AssertionError(err); });
        assertEquals(2, band.minFloorIndex());
        assertEquals(2, band.lootTables().size());
        assertEquals(8, band.lootTables().get(0).weight());
        assertEquals(1, band.lootTables().get(1).weight(), "weight defaults to 1");
    }
}
