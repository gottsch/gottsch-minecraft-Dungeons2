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
package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The depth axis for spawner content: {@code mobSetsByFloorIndex} bands, and a scheme's ability to
 * override them.
 *
 * <p>Two properties carry most of the weight here, and both are about making a silent failure
 * impossible rather than merely unlikely. <strong>Bands are open-ended downward</strong>, so a floor
 * that no band answers for cannot be expressed &mdash; the alternative leaves a dungeon that looks
 * finished with every spawner on one floor quietly drawing from nothing. And <strong>absent is not
 * empty</strong> on a scheme's {@code mobSets}: absent means "whatever this depth calls for", empty
 * is an authoring mistake and a load error, and only an {@link Optional} can tell them apart.</p>
 */
class MobSetsByFloorTest {

    private static final String VERMIN = "dungeons2:classic_vermin";
    private static final String DEEP = "dungeons2:classic_deep";

    private static MobSetBand band(int minFloorIndex, String... sets) {
        return new MobSetBand(minFloorIndex,
                java.util.Arrays.stream(sets)
                        .map(id -> new SpawnerConfig.MobSetEntry(id, 1))
                        .toList());
    }

    private static List<String> setsFor(List<MobSetBand> table, int floorIndex) {
        return new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT, DoorConfig.DEFAULT,
                CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(RoomScheme.PLAIN), table)
                .mobSetsFor(floorIndex)
                .stream().map(SpawnerConfig.MobSetEntry::mobSet).toList();
    }

    // ---------- band selection ----------

    @Test
    void aFloorTakesTheDeepestBandThatHasStarted() {
        List<MobSetBand> table = List.of(band(0, VERMIN), band(3, DEEP));
        assertEquals(List.of(VERMIN), setsFor(table, 0));
        assertEquals(List.of(VERMIN), setsFor(table, 1));
        assertEquals(List.of(VERMIN), setsFor(table, 2));
        assertEquals(List.of(DEEP), setsFor(table, 3), "the band starting at 3 takes over AT 3");
        assertEquals(List.of(DEEP), setsFor(table, 4));
    }

    /**
     * The deepest band runs forever. This is the property that makes an uncovered floor
     * unrepresentable, so it is worth asserting well past any dungeon the planner builds.
     */
    @Test
    void theDeepestBandCoversEveryFloorBelowIt() {
        List<MobSetBand> table = List.of(band(0, VERMIN), band(2, DEEP));
        assertEquals(List.of(DEEP), setsFor(table, 50));
    }

    /** Declaration order is not selection order -- an author listing bands out of order still works. */
    @Test
    void bandOrderInTheFileDoesNotMatter() {
        assertEquals(List.of(DEEP), setsFor(List.of(band(3, DEEP), band(0, VERMIN)), 4));
        assertEquals(List.of(VERMIN), setsFor(List.of(band(3, DEEP), band(0, VERMIN)), 1));
    }

    @Test
    void anEmptyTableAnswersNothingRatherThanThrowing() {
        assertEquals(List.of(), setsFor(List.of(), 0));
        assertEquals(List.of(), setsFor(List.of(), 7));
    }

    // ---------- table validation ----------

    @Test
    void aTableThatDoesNotCoverTheEntranceFloorIsALoadError() {
        DataResult<List<MobSetBand>> result = MobSetBand.validate(List.of(band(1, VERMIN)));
        assertTrue(result.error().isPresent(),
                "bands run downward, so one starting at 1 leaves floor 0 answered by nothing");
        assertTrue(result.error().get().message().contains("floor 0"));
    }

    @Test
    void twoBandsStartingOnTheSameFloorIsALoadError() {
        DataResult<List<MobSetBand>> result =
                MobSetBand.validate(List.of(band(0, VERMIN), band(0, DEEP)));
        assertTrue(result.error().isPresent(),
                "one of the two is unreachable, and which one depends on list order");
    }

    /** No table at all is a legitimate motif: its schemes must then name their own sets. */
    @Test
    void anEmptyTableIsAllowed() {
        assertTrue(MobSetBand.validate(List.of()).result().isPresent());
    }

    @Test
    void aBandWithNoMobSetsIsALoadError() {
        JsonElement json = JsonParser.parseString("{\"minFloorIndex\":0,\"mobSets\":[]}");
        assertTrue(MobSetBand.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    // ---------- the scheme override ----------

    @Test
    void aSchemeWithNoMobSetsInheritsTheFloorsBand() {
        SpawnerConfig deferring = SpawnerConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"minCount\":1,\"maxCount\":1}"))
                .result().orElseThrow();
        assertTrue(deferring.mobSets().isEmpty(), "the slot should be deferring, not defaulted");

        SpawnerConfig resolved = deferring.resolvedAgainst(
                List.of(new SpawnerConfig.MobSetEntry(DEEP, 1)));
        assertEquals(List.of(DEEP),
                resolved.declaredMobSets().stream().map(SpawnerConfig.MobSetEntry::mobSet).toList());
    }

    @Test
    void aSchemeThatNamesItsOwnSetsOverridesTheBand() {
        SpawnerConfig owning = new SpawnerConfig(VERMIN);
        SpawnerConfig resolved = owning.resolvedAgainst(
                List.of(new SpawnerConfig.MobSetEntry(DEEP, 1)));
        assertSame(owning, resolved, "an overriding slot should not be rebuilt");
        assertEquals(List.of(VERMIN),
                resolved.declaredMobSets().stream().map(SpawnerConfig.MobSetEntry::mobSet).toList());
    }

    /**
     * The distinction the whole override rests on. {@code "mobSets": []} is not a quieter way of
     * omitting the key -- it is a slot that can only place invisible blocks that spawn nothing.
     */
    @Test
    void anExplicitlyEmptyMobSetsListIsALoadError() {
        JsonElement json = JsonParser.parseString("{\"mobSets\":[]}");
        DataResult<SpawnerConfig> result = SpawnerConfig.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("Omit the key"),
                "the error should point at the fix, since 'empty' and 'absent' look alike");
    }

    /** A deferring slot that finds no band places nothing -- degrade, don't invent. */
    @Test
    void aDeferringSchemeOnAMotifWithNoTableResolvesToNothing() {
        SpawnerConfig deferring = SpawnerConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .result().orElseThrow();
        assertTrue(deferring.resolvedAgainst(List.of()).declaredMobSets().isEmpty());
    }

    // ---------- fragment merge ----------

    /**
     * The table is <strong>replaced</strong> by a later fragment, not appended to &mdash; unlike
     * {@code schemes}, which merge by name. A depth table is one coherent progression; half of one
     * spliced into half of another is a curve nobody authored.
     */
    @Test
    void alaterFragmentReplacesTheWholeTable() {
        MotifConfigFragment first = fragment(List.of(band(0, VERMIN), band(2, DEEP)));
        MotifConfigFragment second = fragment(List.of(band(0, DEEP)));

        MotifConfig merged = MotifConfigFragment.resolve(List.of(first, second));
        assertEquals(List.of(DEEP), names(merged.mobSetsFor(0)));
        assertEquals(List.of(DEEP), names(merged.mobSetsFor(2)),
                "the first fragment's deep band should be gone, not still covering floor 2");
    }

    /** A fragment that says nothing about the table leaves an earlier one's alone. */
    @Test
    void aFragmentWithNoTableDoesNotClearAnEarlierOne() {
        MotifConfigFragment withTable = fragment(List.of(band(0, VERMIN)));
        MotifConfigFragment silent = new MotifConfigFragment(Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), List.of());

        MotifConfig merged = MotifConfigFragment.resolve(List.of(withTable, silent));
        assertFalse(merged.mobSetsByFloorIndex().isEmpty(),
                "a fragment holding only schemes would otherwise wipe the motif's depth table");
        assertEquals(List.of(VERMIN), names(merged.mobSetsFor(0)));
    }

    // ---- Per-band mob counts -------------------------------------------------------------
    //
    // Without these a band could change WHAT spawns but never HOW MANY, so "3-5 on the deep
    // floors" needed a near-duplicate scheme per band -- the duplication the table exists to
    // remove. Precedence is scheme's own value, then the band's, then the built-in default.

    private static MobSetBand countingBand(int minFloorIndex, int minMobs, int maxMobs) {
        return new MobSetBand(minFloorIndex,
                List.of(new SpawnerConfig.MobSetEntry(DEEP, 1)),
                Optional.of(minMobs), Optional.of(maxMobs));
    }

    /** A slot that states no counts takes the band's. */
    @Test
    void aBandSuppliesTheMobCountWhenTheSchemeStatesNone() {
        SpawnerConfig deferring = SpawnerConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"minCount\":1,\"maxCount\":1}"))
                .result().orElseThrow();
        assertTrue(deferring.minMobs().isEmpty(), "the slot should state no count, not a defaulted one");

        SpawnerConfig resolved = deferring.resolvedAgainst(Optional.of(countingBand(2, 3, 5)));
        assertEquals(3, resolved.effectiveMinMobs());
        assertEquals(5, resolved.clampedMaxMobs());
    }

    /** The scheme's own count wins -- an author who wrote a number meant it at every depth. */
    @Test
    void aSchemeThatStatesItsOwnCountOverridesTheBand() {
        SpawnerConfig owning = SpawnerConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"minMobs\":1,\"maxMobs\":2}"))
                .result().orElseThrow();

        SpawnerConfig resolved = owning.resolvedAgainst(Optional.of(countingBand(2, 3, 5)));
        assertEquals(1, resolved.effectiveMinMobs());
        assertEquals(2, resolved.clampedMaxMobs());
    }

    /**
     * The two axes are independent: a scheme pinned to its own mob SETS still gets the band's
     * counts. "Which mobs" and "how many" are separate authoring decisions, so overriding one must
     * not silently opt out of the other.
     */
    @Test
    void aSchemeWithItsOwnSetsStillTakesTheBandsCount() {
        SpawnerConfig owningSets = new SpawnerConfig(VERMIN);
        SpawnerConfig resolved = owningSets.resolvedAgainst(Optional.of(countingBand(2, 3, 5)));

        assertEquals(List.of(VERMIN),
                resolved.declaredMobSets().stream().map(SpawnerConfig.MobSetEntry::mobSet).toList(),
                "its own sets must survive");
        assertEquals(3, resolved.effectiveMinMobs(), "but the band still sets the crowd size");
        assertEquals(5, resolved.clampedMaxMobs());
    }

    /** Neither speaks -> the built-in default, unchanged from before the band could carry counts. */
    @Test
    void withNeitherASchemeNorABandCountTheDefaultStands() {
        SpawnerConfig deferring = SpawnerConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .result().orElseThrow();

        SpawnerConfig resolved = deferring.resolvedAgainst(Optional.of(band(0, DEEP)));
        assertEquals(SpawnerConfig.DEFAULT_MIN_MOBS, resolved.effectiveMinMobs());
        assertEquals(SpawnerConfig.DEFAULT_MAX_MOBS, resolved.clampedMaxMobs());
    }

    /**
     * A band restating the default is NOT the same as a band saying nothing, and this is the whole
     * reason the field is an Optional. The first overrides a scheme that stated nothing; the second
     * leaves it alone. Here they happen to agree in value -- what differs is that the band's
     * presence is observable.
     */
    @Test
    void aBandStatingNoCountLeavesTheSchemesOwnAlone() {
        SpawnerConfig owning = SpawnerConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"minMobs\":4,\"maxMobs\":6}"))
                .result().orElseThrow();

        SpawnerConfig resolved = owning.resolvedAgainst(Optional.of(band(0, DEEP)));
        assertEquals(4, resolved.effectiveMinMobs());
        assertEquals(6, resolved.clampedMaxMobs());
    }

    /** An inverted band range clamps rather than failing -- nonsense, but not ambiguous. */
    @Test
    void anInvertedBandRangeClamps() {
        SpawnerConfig deferring = SpawnerConfig.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
                .result().orElseThrow();

        SpawnerConfig resolved = deferring.resolvedAgainst(Optional.of(countingBand(0, 5, 2)));
        assertEquals(5, resolved.effectiveMinMobs());
        assertEquals(5, resolved.clampedMaxMobs(), "max should clamp up to min, as the scheme slot does");
    }

    /** The counts are real datapack keys, and the band's schema stays closed around them. */
    @Test
    void aBandRoundTripsItsCountsAndRejectsAnUnknownKey() {
        JsonElement json = JsonParser.parseString(
                "{\"minFloorIndex\":2,\"mobSets\":[{\"mobSet\":\"" + DEEP + "\"}],"
                        + "\"minMobs\":3,\"maxMobs\":5}");
        MobSetBand decoded = MobSetBand.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(Optional.of(3), decoded.minMobs());
        assertEquals(Optional.of(5), decoded.maxMobs());

        DataResult<MobSetBand> typo = MobSetBand.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"mobSets\":[{\"mobSet\":\"" + DEEP + "\"}],\"minMob\":3}"));
        assertTrue(typo.error().isPresent(), "a misspelled count key must not be silently ignored");
    }

    /** A band omitting the counts decodes to absent, not to the defaults. */
    @Test
    void aBandWithNoCountsDecodesToAbsent() {
        MobSetBand decoded = MobSetBand.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"mobSets\":[{\"mobSet\":\"" + DEEP + "\"}]}")).result().orElseThrow();
        assertTrue(decoded.minMobs().isEmpty());
        assertTrue(decoded.maxMobs().isEmpty());
    }

    private static MotifConfigFragment fragment(List<MobSetBand> table) {
        return new MotifConfigFragment(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of(), Optional.of(table));
    }

    private static List<String> names(List<SpawnerConfig.MobSetEntry> entries) {
        return entries.stream().map(SpawnerConfig.MobSetEntry::mobSet).toList();
    }
}
