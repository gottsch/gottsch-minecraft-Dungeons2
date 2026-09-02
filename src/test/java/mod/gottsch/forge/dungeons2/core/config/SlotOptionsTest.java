package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SlotOptions} &mdash; #65, a scheme slot holding weighted <em>alternatives</em> instead of
 * one treatment.
 *
 * <p>The two halves that matter are pinned separately. The <strong>codec</strong> half has to keep
 * every scheme authored before this existed decoding to exactly what it did, since migration is
 * meant to be optional and one slot at a time. The <strong>roll</strong> half has to leave those
 * schemes' random streams untouched as well &mdash; a slot with one option draws nothing, so an
 * unconverted motif generates the same dungeons from the same seed. A test suite that only checked
 * the new shape would let either of those regress silently.</p>
 */
class SlotOptionsTest {

    private static final Gson GSON = new Gson();

    private static final String POT_A =
            "\"loot_table\": \"dungeons2:pots/a\", \"variants\": [{\"entity\": \"dungeonblocks:pot\"}]";
    private static final String POT_B =
            "\"loot_table\": \"dungeons2:pots/b\", \"variants\": [{\"entity\": \"dungeonblocks:pot\"}]";

    private static RoomScheme scheme(String slots) {
        DataResult<RoomScheme> result = parse(RoomScheme.CODEC,
                "{\"name\": \"s\", " + slots + "}");
        return result.result().orElseThrow(() -> new AssertionError(
                "expected this scheme to decode: " + result.error().orElseThrow().message()));
    }

    private static <A> DataResult<A> parse(Codec<A> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static String errorOf(String slots) {
        DataResult<RoomScheme> result = parse(RoomScheme.CODEC, "{\"name\": \"s\", " + slots + "}");
        return result.error()
                .orElseThrow(() -> new AssertionError("expected a load error, but this decoded: " + slots))
                .message();
    }

    // ---- back-compatibility: the single-treatment shape is untouched ---------------------------

    /**
     * The whole reason this is {@code Codec.either} on the <em>existing</em> key rather than a
     * parallel {@code potsOptions} one: an unconverted scheme is still a valid scheme, so a motif
     * converts a slot at a time instead of in a rewrite. (It also could not have been a parallel
     * field &mdash; {@code RoomScheme.CODEC} is at fifteen of DFU's sixteen group arguments.)
     */
    @Test
    void aSingleTreatmentIsStillTheShapeItWas() {
        RoomScheme scheme = scheme("\"pots\": {" + POT_A + "}");
        assertTrue(scheme.pots().value().isPresent());
        assertEquals("dungeons2:pots/a", scheme.pots().orElseThrow().lootTable());
        assertFalse(scheme.pots().isUnresolved(), "one treatment is nothing to choose between");
    }

    /** And an absent slot is still absent, rather than becoming an empty option list. */
    @Test
    void anAbsentSlotIsStillAbsent() {
        assertTrue(scheme("\"weight\": 3").pots().isEmpty());
        assertEquals(Optional.empty(), scheme("\"weight\": 3").pots().value());
    }

    // ---- the roll -----------------------------------------------------------------------------

    @Test
    void aWeightedListRollsBetweenTheAlternatives() {
        RoomScheme scheme = scheme("\"pots\": ["
                + "{\"weight\": 3, " + POT_A + "},"
                + "{\"weight\": 1, " + POT_B + "}]");
        Map<String, Integer> counts = resolveMany(scheme, 4000, RandomSource.create(7));
        assertEquals(4000, counts.values().stream().mapToInt(Integer::intValue).sum());
        // 3:1, within a wide band -- this is pinning that the weights are read at all, not the RNG.
        assertTrue(counts.getOrDefault("dungeons2:pots/a", 0) > 2700,
                () -> "the heavier option should win about three times in four: " + counts);
        assertTrue(counts.getOrDefault("dungeons2:pots/b", 0) > 700, () -> counts.toString());
    }

    /**
     * The trap the class doc warns about, from the other side: without a {@code none} option the
     * slot is drawn in <strong>100%</strong> of the rooms the scheme dresses. Band-level joists
     * reached 55.9% incidence that way and became the band's look rather than a room type.
     */
    @Test
    void withoutANoneOptionEveryRoomGetsTheSlot() {
        RoomScheme scheme = scheme("\"pots\": ["
                + "{\"weight\": 1, " + POT_A + "},"
                + "{\"weight\": 1, " + POT_B + "}]");
        assertEquals(0, resolveMany(scheme, 500, RandomSource.create(11)).getOrDefault("<none>", 0));
    }

    @Test
    void aNoneOptionIsHowASlotIsSometimesSkipped() {
        RoomScheme scheme = scheme("\"pots\": ["
                + "{\"weight\": 1, " + POT_A + "},"
                + "{\"weight\": 3, \"none\": true}]");
        Map<String, Integer> counts = resolveMany(scheme, 4000, RandomSource.create(13));
        assertTrue(counts.getOrDefault("<none>", 0) > 2700,
                () -> "three parts in four should draw nothing: " + counts);
        assertTrue(counts.getOrDefault("dungeons2:pots/a", 0) > 700, () -> counts.toString());
    }

    /**
     * A slot that rolled {@code none} is the <em>unfilled</em> slot, not a filled one holding
     * nothing. Two ways to say "nothing here", only one of which most callers test for, is how a
     * distinction goes wrong months later.
     */
    @Test
    void aRolledNoneCollapsesToTheEmptySlot() {
        RoomScheme scheme = scheme("\"pots\": [{\"weight\": 1, \"none\": true},"
                + "{\"weight\": 1, \"none\": true}]");
        RoomScheme resolved = scheme.resolve(9, 9, 7, RandomSource.create(1));
        assertTrue(resolved.pots().isEmpty());
        assertEquals(Optional.empty(), resolved.potsFor(9, 9, 7));
    }

    /**
     * Options the room gates out are dropped BEFORE the weights are totalled, so probability does
     * not pool into whichever option happens to survive by position &mdash; the same rule
     * {@code RoomSchemeSelector} applies to schemes, for the same reason.
     */
    @Test
    void anOptionTheRoomGatesOutNeverEntersTheDenominator() {
        RoomScheme scheme = scheme("\"pots\": ["
                + "{\"weight\": 9, \"min_size\": 21, " + POT_A + "},"
                + "{\"weight\": 1, " + POT_B + "}]");
        Map<String, Integer> counts = resolveMany(scheme, 500, RandomSource.create(17));
        assertEquals(500, counts.getOrDefault("dungeons2:pots/b", 0),
                () -> "the surviving option takes the whole roll, not a ninth of it: " + counts);
    }

    /**
     * <strong>The seed-stability rule.</strong> A slot the author left as a single treatment draws
     * no random value at all, which is what lets a shipped motif be converted one slot at a time
     * without moving every dungeon in every existing world.
     */
    @Test
    void aSchemeWithNoOptionListsConsumesNoRandomValue() {
        RoomScheme scheme = scheme("\"pots\": {" + POT_A + "}, \"floor\": {\"type\": \"dungeons2:plain\"}");
        RandomSource used = RandomSource.create(99);
        scheme.resolve(9, 9, 7, used);
        RandomSource untouched = RandomSource.create(99);
        assertEquals(untouched.nextInt(1_000_000), used.nextInt(1_000_000),
                "resolving a scheme of single treatments must leave the stream where it was");
    }

    /** And the converse, so the test above cannot pass by the roll having been dropped entirely. */
    @Test
    void aSlotWithAlternativesDoesConsumeOne() {
        RoomScheme scheme = scheme("\"pots\": [{\"weight\": 1, " + POT_A + "},"
                + "{\"weight\": 1, " + POT_B + "}]");
        RandomSource used = RandomSource.create(99);
        scheme.resolve(9, 9, 7, used);
        assertNotEquals(RandomSource.create(99).nextInt(1_000_000), used.nextInt(1_000_000));
    }

    /**
     * The draw happens even when every option gated out, so the stream position after a scheme does
     * not depend on the size of the room it was rolled for -- the property
     * {@code RoomSchemeSelector} takes the same care over.
     */
    @Test
    void theNumberOfValuesDrawnDoesNotDependOnTheRoom() {
        RoomScheme scheme = scheme("\"pots\": ["
                + "{\"weight\": 1, \"min_size\": 21, " + POT_A + "},"
                + "{\"weight\": 1, \"min_size\": 21, " + POT_B + "}]");
        RandomSource small = RandomSource.create(5);
        RandomSource large = RandomSource.create(5);
        scheme.resolve(9, 9, 7, small);
        scheme.resolve(25, 25, 9, large);
        assertEquals(large.nextInt(1_000_000), small.nextInt(1_000_000));
    }

    // ---- reading an unresolved slot ------------------------------------------------------------

    /**
     * Loud rather than quiet. A slot that returned "nothing" because nobody rolled it would look in
     * game exactly like an authoring mistake, which is the silent-nothing failure this package's
     * strict codecs exist to prevent.
     */
    @Test
    void readingASlotBeforeItIsRolledThrows() {
        RoomScheme scheme = scheme("\"pots\": [{\"weight\": 1, " + POT_A + "},"
                + "{\"weight\": 1, " + POT_B + "}]");
        assertThrows(IllegalStateException.class, () -> scheme.potsFor(9, 9, 7));
        assertThrows(IllegalStateException.class, () -> scheme.pots().value());
    }

    /** {@code drawsAnything} is asked of UNRESOLVED schemes, so it reads every alternative. */
    @Test
    void drawsAnythingReadsEveryAlternativeRatherThanARolledOne() {
        RoomScheme both = scheme("\"pots\": [{\"weight\": 1, \"min_size\": 21, " + POT_A + "},"
                + "{\"weight\": 1, " + POT_B + "}]");
        assertTrue(both.drawsAnything(9, 9, 7), "the ungated option still draws in a small room");
        RoomScheme neither = scheme("\"pots\": [{\"weight\": 1, \"min_size\": 21, " + POT_A + "},"
                + "{\"weight\": 1, \"min_size\": 21, " + POT_B + "}]");
        assertFalse(neither.drawsAnything(9, 9, 7));
        assertTrue(neither.declaresAnySlot(), "gated out is not the same as never authored");
    }

    // ---- load errors ---------------------------------------------------------------------------

    @Test
    void anEmptyOptionListIsALoadError() {
        assertTrue(errorOf("\"pots\": []").contains("draws nothing"));
    }

    @Test
    void aNoneOptionThatAlsoDeclaresATreatmentIsALoadError() {
        String message = errorOf("\"pots\": [{\"weight\": 1, \"none\": true, " + POT_A + "}]");
        assertTrue(message.contains("loot_table"), () -> "the error should name what was found: " + message);
        assertTrue(message.contains("never both"), () -> message);
    }

    /**
     * The closed schema survives the extra level. An option's key set is the treatment's own plus
     * {@code weight} and {@code none}, and a typo inside one has to stay a load error rather than
     * quietly becoming an option that draws the default.
     */
    @Test
    void aStrayKeyInsideAnOptionIsStillALoadError() {
        String message = errorOf("\"pots\": [{\"weight\": 1, \"lootTabel\": \"x\", " + POT_A + "}]");
        assertTrue(message.contains("lootTabel"), () -> message);
        assertTrue(message.contains("loot_table"), () -> "did-you-mean should fire here: " + message);
    }

    @Test
    void aWeightOfZeroIsALoadError() {
        assertTrue(errorOf("\"pots\": [{\"weight\": 0, " + POT_A + "}]").contains("weight"));
    }

    /** An inverted gate is validated on EVERY option, not just the one a given room would pick. */
    @Test
    void anInvertedGateOnAnyOptionIsALoadError() {
        String message = errorOf("\"pots\": [{\"weight\": 1, " + POT_A + "},"
                + "{\"weight\": 1, \"min_size\": 9, \"max_size\": 5, " + POT_B + "}]");
        assertTrue(message.contains("pots"), () -> message);
    }

    // ---- every slot, and the round trip --------------------------------------------------------

    /**
     * All ten element slots take the new shape. Worth enumerating rather than trusting the shared
     * helper: each slot is wired by hand at its own line in {@code RoomScheme.CODEC}, and a slot
     * that was left on the old field codec would decode its single form perfectly and reject a list
     * only once someone got round to converting it.
     */
    @Test
    void everyElementSlotAcceptsAnOptionList() {
        assertSlotTakesOptions("floor", "{\"type\": \"dungeons2:plain\"}");
        assertSlotTakesOptions("wall", "{\"patterns\": []}");
        assertSlotTakesOptions("ceiling", "{\"patterns\": []}");
        assertSlotTakesOptions("pots", "{" + POT_A + "}");
        assertSlotTakesOptions("pillars", "{\"patterns\": []}");
        assertSlotTakesOptions("platforms", "{\"patterns\": []}");
        assertSlotTakesOptions("spawners", "{\"min_count\": 0, \"max_count\": 1}");
        assertSlotTakesOptions("chests",
                "{\"variants\": [{\"block\": \"minecraft:chest\", \"weight\": 1}]}");
        assertSlotTakesOptions("pit",
                "{\"type\": \"dungeons2:centre\", \"config\": {\"size\": 5, \"depth\": 1}}");
        assertSlotTakesOptions("props",
                "{\"variants\": [{\"block\": \"minecraft:barrel\", \"weight\": 1}]}");
    }

    private static void assertSlotTakesOptions(String slot, String treatment) {
        // The single form, unchanged.
        scheme("\"" + slot + "\": " + treatment);
        // The list form, with the none-option that keeps the slot from reaching 100% incidence.
        String withWeight = "{\"weight\": 2, " + treatment.substring(1);
        RoomScheme options = scheme("\"" + slot + "\": [" + withWeight
                + ", {\"weight\": 1, \"none\": true}]");
        assertTrue(parse(RoomScheme.CODEC, "{\"name\": \"s\", \"" + slot + "\": [" + withWeight
                + ", {\"weight\": 1, \"nonsense\": true}]}").error().isPresent(),
                () -> "the closed check has to reach inside an option of the " + slot + " slot");
        assertEquals("s", options.name());
    }

    /**
     * A slot still holding one unweighted treatment encodes back as the bare object, so dumping an
     * unconverted motif does not rewrite it into the list form.
     */
    @Test
    void aSingleTreatmentRoundTripsBackToTheBareObject() {
        RoomScheme scheme = scheme("\"pots\": {" + POT_A + "}");
        JsonElement dumped = RoomScheme.CODEC.encodeStart(JsonOps.INSTANCE, scheme).result().orElseThrow();
        assertTrue(dumped.getAsJsonObject().get("pots").isJsonObject());
        assertEquals("dungeons2:pots/a", parse(RoomScheme.CODEC, dumped.toString()).result()
                .orElseThrow().pots().orElseThrow().lootTable());
    }

    @Test
    void anOptionListRoundTrips() {
        RoomScheme scheme = scheme("\"pots\": [{\"weight\": 3, " + POT_A + "},"
                + "{\"weight\": 1, \"none\": true}]");
        JsonElement dumped = RoomScheme.CODEC.encodeStart(JsonOps.INSTANCE, scheme).result().orElseThrow();
        assertTrue(dumped.getAsJsonObject().get("pots").isJsonArray());
        RoomScheme reparsed = parse(RoomScheme.CODEC, dumped.toString()).result().orElseThrow();
        assertEquals(scheme.pots(), reparsed.pots());
    }

    // ---- inheritance ---------------------------------------------------------------------------

    /** A child's option list replaces the parent's slot wholesale, exactly as a single one does. */
    @Test
    void aChildsOptionListReplacesTheParentsSlot() {
        RoomScheme parent = scheme("\"pots\": {" + POT_A + "}");
        RoomScheme child = scheme("\"pots\": [{\"weight\": 1, " + POT_B + "},"
                + "{\"weight\": 1, \"none\": true}]");
        assertEquals(child.pots(), child.inheritFrom(parent).pots());
        assertEquals(parent.pots(), scheme("\"weight\": 2").inheritFrom(parent).pots());
    }

    private static Map<String, Integer> resolveMany(RoomScheme scheme, int rooms, RandomSource random) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < rooms; i++) {
            String drawn = scheme.resolve(9, 9, 7, random).pots()
                    .map(PotConfig::lootTable).orElse("<none>");
            counts.merge(drawn, 1, Integer::sum);
        }
        return counts;
    }
}
