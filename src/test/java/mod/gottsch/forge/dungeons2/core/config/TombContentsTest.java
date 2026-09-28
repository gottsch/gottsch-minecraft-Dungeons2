package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a tomb holds (#104): the band and slot codecs, the key-by-key override, and the NBT the two
 * routes write from one source.
 */
class TombContentsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static DataResult<TombBand> band(String text) {
        return TombBand.CODEC.parse(JsonOps.INSTANCE, json(text));
    }

    private static final String SHALLOW = """
            { "min_floor_index": 0,
              "loot_tables": [ { "loot_table": "dungeons2:chests/classic_tomb_shallow" } ],
              "guardians": [ { "entity": "minecraft:zombie", "weight": 4 },
                             { "entity": "minecraft:skeleton" } ],
              "loot_weight": 3, "guardian_weight": 2, "empty_weight": 5 }""";

    @Test
    void aBandDecodesInTheShippedShape() {
        TombBand decoded = band(SHALLOW).getOrThrow(false, message -> {});
        assertEquals(0, decoded.minFloorIndex());
        assertEquals(2, decoded.contents().guardians().orElseThrow().size());
        assertEquals(1, decoded.contents().guardians().orElseThrow().get(1).weight(),
                "a guardian's weight defaults to 1");
        assertEquals(Optional.of(5), decoded.contents().emptyWeight());
    }

    @Test
    void aMisspelledKeyIsALoadError() {
        assertTrue(band("""
                { "min_floor_index": 0, "guardians": [ { "entity": "minecraft:zombie" } ],
                  "gaurdian_weight": 2 }""").error().isPresent(),
                "a typo'd weight would otherwise decode as 'use the default' and say nothing");
    }

    @Test
    void anEmptyListIsALoadError() {
        assertTrue(band("""
                { "min_floor_index": 0, "loot_tables": [],
                  "guardians": [ { "entity": "minecraft:zombie" } ] }""").error().isPresent());
    }

    /** A band that can only ever come up empty is scenery pretending to be an encounter. */
    @Test
    void aBandThatCanReleaseNothingIsALoadError() {
        assertTrue(band("""
                { "min_floor_index": 0, "empty_weight": 5 }""").error().isPresent());
        assertTrue(band("""
                { "min_floor_index": 0, "guardians": [ { "entity": "minecraft:zombie" } ],
                  "guardian_weight": 0 }""").error().isPresent());
    }

    @Test
    void theTableMustStartAtZeroAndNoStartMayRepeat() {
        TombBand zero = band(SHALLOW).getOrThrow(false, message -> {});
        TombBand two = new TombBand(2, zero.contents());
        assertTrue(TombBand.validate(List.of(zero, two)).result().isPresent());
        assertTrue(TombBand.validate(List.of(two)).error().isPresent(), "floor 0 uncovered");
        assertTrue(TombBand.validate(List.of(zero, two, new TombBand(2, zero.contents())))
                .error().isPresent(), "two bands at floor 2");
        assertEquals(2, TombBand.forFloor(List.of(zero, two), 9).orElseThrow().minFloorIndex(),
                "the deepest band runs forever");
    }

    @Test
    void overTakesEachKeyOnItsOwn() {
        TombContents under = band(SHALLOW).getOrThrow(false, message -> {}).contents();
        TombContents over = new TombContents(Optional.empty(),
                Optional.of(List.of(new TombContents.GuardianEntry("minecraft:husk", 1))),
                Optional.empty(), Optional.of(9), Optional.empty());
        TombContents merged = over.over(under);
        assertEquals(under.lootTables(), merged.lootTables());
        assertEquals("minecraft:husk", merged.guardians().orElseThrow().get(0).entity());
        assertEquals(Optional.of(3), merged.lootWeight());
        assertEquals(Optional.of(9), merged.guardianWeight());
        assertEquals(Optional.of(5), merged.emptyWeight());
    }

    /** DungeonBlocks ignores a weight beside nothing; writing one anyway would mislead a reader. */
    @Test
    void aWeightIsWrittenOnlyBesideWhatItWeighs() {
        TombContents guardianOnly = new TombContents(Optional.empty(),
                Optional.of(List.of(new TombContents.GuardianEntry("minecraft:zombie", 1))),
                Optional.of(3), Optional.of(2), Optional.of(5));
        Map<String, Object> fields = guardianOnly.draw(RandomSource.create(1L)).fields();
        assertFalse(fields.containsKey(TombContents.Drawn.LOOT_WEIGHT));
        assertFalse(fields.containsKey(TombContents.Drawn.LOOT_TABLE));
        assertFalse(fields.containsKey(TombContents.Drawn.LOOT_TABLE_SEED));
        assertEquals("minecraft:zombie", fields.get(TombContents.Drawn.GUARDIAN));
        assertEquals(2, fields.get(TombContents.Drawn.GUARDIAN_WEIGHT));
        assertEquals(5, fields.get(TombContents.Drawn.EMPTY_WEIGHT));
    }

    /** The marker route's NBT, typed as DungeonBlocks reads it. */
    @Test
    void theTagTypesEveryField() {
        CompoundTag tag = band(SHALLOW).getOrThrow(false, message -> {}).contents()
                .draw(RandomSource.create(4L)).tag();
        assertEquals(Tag.TAG_STRING, tag.getTagType(TombContents.Drawn.LOOT_TABLE));
        assertEquals(Tag.TAG_LONG, tag.getTagType(TombContents.Drawn.LOOT_TABLE_SEED));
        assertTrue(tag.getLong(TombContents.Drawn.LOOT_TABLE_SEED) != 0L, "0 means roll on open");
        assertEquals(Tag.TAG_STRING, tag.getTagType(TombContents.Drawn.GUARDIAN));
        assertEquals(Tag.TAG_INT, tag.getTagType(TombContents.Drawn.LOOT_WEIGHT));
        assertEquals(Tag.TAG_INT, tag.getTagType(TombContents.Drawn.GUARDIAN_WEIGHT));
        assertEquals(Tag.TAG_INT, tag.getTagType(TombContents.Drawn.EMPTY_WEIGHT));
    }

    /**
     * The procedural probe only has the placement's STRINGIFIED data by the time it knows where the
     * tomb is; it must print what the draw itself would.
     */
    @Test
    void theProbePrintsFromTheWrittenDataWhatTheDrawSays() {
        TombContents.Drawn drawn = band(SHALLOW).getOrThrow(false, message -> {}).contents()
                .draw(RandomSource.create(2L));
        Map<String, String> written = new java.util.LinkedHashMap<>();
        drawn.fields().forEach((key, value) -> written.put(key, String.valueOf(value)));
        assertEquals(drawn.describe(), TombContents.Drawn.describe(written));
        assertTrue(drawn.describe().contains("weights 3/2/5"), drawn.describe());
    }

    /** No guardian list, no guardian draw: absent content consumes nothing from the stream. */
    @Test
    void anAbsentListDrawsNothing() {
        TombContents lootOnly = new TombContents(
                Optional.of(List.of(new ChestConfig.LootTableEntry("d2:t", 1))), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        RandomSource used = RandomSource.create(8L);
        lootOnly.draw(used);
        RandomSource expected = RandomSource.create(8L);
        expected.nextInt(1);    // the table
        expected.nextLong();    // its seed
        assertEquals(expected.nextLong(), used.nextLong());
    }

    @Test
    void releasesAnythingCountsOnlyNamedContentWithWeight() {
        assertFalse(TombContents.NONE.releasesAnything());
        TombContents lootOnly = new TombContents(
                Optional.of(List.of(new ChestConfig.LootTableEntry("d2:t", 1))), Optional.empty(),
                Optional.empty(), Optional.of(7), Optional.empty());
        assertTrue(lootOnly.releasesAnything(), "a guardian weight with no guardian is ignored");
        assertFalse(new TombContents(lootOnly.lootTables(), Optional.empty(), Optional.of(0),
                Optional.empty(), Optional.of(3)).releasesAnything());
    }

    /** The slot through RoomScheme's closed codec: its defaults, and a typo failing the pack. */
    @Test
    void theSlotDecodesClosedWithTheChestsDefaults() {
        RoomScheme scheme = RoomScheme.CODEC.parse(JsonOps.INSTANCE, json("""
                { "name": "crypt",
                  "tombs": { "variants": [ { "block": "$sarcophagus" } ] } }"""))
                .getOrThrow(false, message -> {});
        TombConfig tombs = scheme.tombs().value().orElseThrow();
        assertEquals(0, tombs.minCount());
        assertEquals(1, tombs.maxCount());
        assertEquals(TombContents.NONE, tombs.contents(), "absent keys defer to the band");

        assertTrue(RoomScheme.CODEC.parse(JsonOps.INSTANCE, json("""
                { "name": "crypt",
                  "tombs": { "variants": [ { "block": "$sarcophagus" } ], "max_cuont": 3 } }"""))
                .error().isPresent());
    }

    @Test
    void theSlotResolvesItsRole() {
        TombConfig tombs = new TombConfig(1, 2, List.of(new TombConfig.TombVariant("$sarcophagus", 1)));
        TombConfig resolved = tombs.withRoles(role -> role.equals("sarcophagus")
                ? "dungeonblocks:deepslate_sarcophagus" : "$" + role);
        assertEquals("dungeonblocks:deepslate_sarcophagus", resolved.variants().get(0).block());
        TombConfig literal = new TombConfig(1, 2,
                List.of(new TombConfig.TombVariant("dungeonblocks:stone_sarcophagus", 1)));
        assertTrue(literal == literal.withRoles(role -> "x"), "no role, no allocation");
    }
}
