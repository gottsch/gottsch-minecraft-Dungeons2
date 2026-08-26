package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Codecs#closed} &mdash; a datapack field this package does not declare is a load error
 * rather than a silent no-op.
 *
 * <p>Everything here is a <em>negative</em> test of a shape that used to decode cleanly. That is the
 * point: {@code "widht": 3} produced a pattern of the default width, {@code "projecton": 1} left the
 * trim flush, and the pattern still drew in both cases &mdash; no error, no log line, and nothing in
 * game to distinguish the result from a correctly authored plain one. The positive side (that every
 * shipped scheme still parses, gates and all) is covered by {@code DatapackResourcesParseTest} and
 * {@code MotifConfigCodecTest}, which would fail loudly if this over-tightened.
 */
class ClosedSchemaCodecTest {

    private static final Gson GSON = new Gson();

    private static <A> DataResult<A> parse(Codec<A> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static <A> String errorOf(Codec<A> codec, String json) {
        DataResult<A> result = parse(codec, json);
        return result.error()
                .orElseThrow(() -> new AssertionError("expected a load error, but this decoded: " + json))
                .message();
    }

    private static <A> void decodes(Codec<A> codec, String json) {
        assertTrue(parse(codec, json).result().isPresent(),
                () -> "expected this to decode, but it failed: "
                        + parse(codec, json).error().map(DataResult.PartialResult::message).orElse(""));
    }

    // ---- the misspellings named in the backlog ------------------------------------------------

    @Test
    void aMisspelledWidthOnAPanelIsALoadError() {
        String message = errorOf(WallPatternEntry.PatternEntry.CODEC,
                "{\"type\": \"panels\", \"block\": \"minecraft:stone_bricks\", \"widht\": 3}");
        assertTrue(message.contains("widht"), () -> "the error must name the offending key: " + message);
        assertTrue(message.contains("width"), () -> "and suggest the one meant: " + message);
    }

    @Test
    void aMisspelledProjectionOnACourseIsALoadError() {
        String message = errorOf(WallPatternEntry.CourseEntry.CODEC,
                "{\"block\": \"minecraft:stone_brick_stairs\", \"anchor\": \"top\", \"projecton\": 1}");
        assertTrue(message.contains("projecton"), () -> message);
        assertTrue(message.contains("projection"), () -> "did-you-mean should fire here: " + message);
    }

    @Test
    void aMisspelledSchemeSlotIsALoadError() {
        // The worst of the three: the whole ceiling treatment vanishes, and an undecorated room is
        // a legitimate authored outcome, so nothing downstream can tell this from what was meant.
        String message = errorOf(RoomScheme.CODEC,
                "{\"name\": \"typo\", \"celing\": {\"patterns\": []}}");
        assertTrue(message.contains("celing"), () -> message);
        assertTrue(message.contains("ceiling"), () -> message);
    }

    /**
     * A field that is spelled correctly but written on a type that cannot use it is a different
     * fault, caught by each record's own {@code validate}; this pins that the two do not collide.
     */
    @Test
    void aKnownFieldOnTheWrongTypeStillFailsThroughValidate() {
        String message = errorOf(WallPatternEntry.CODEC,
                "{\"patterns\": [{\"type\": \"pilasters\", \"block\": \"minecraft:stone_bricks\","
                        + " \"courses\": [{\"block\": \"minecraft:stone_bricks\"}]}]}");
        assertTrue(message.contains("courses"), () -> message);
    }

    // ---- the trap: SizeGate is embedded flat --------------------------------------------------

    /**
     * {@link SizeGate#MAP_CODEC} is embedded <em>flat</em> in five records, so its four keys have to
     * count as part of each enclosing record's key set. {@link com.mojang.serialization.codecs.RecordCodecBuilder}
     * composes {@code keys()} from its field codecs, so this comes for free &mdash; but "for free"
     * is exactly the kind of thing that stops being true after a refactor, and if it broke, every
     * gated entry in the shipped schemes would fail to load at once.
     */
    @Test
    void theFlatSizeGateKeysAreNotMistakenForStrays() {
        decodes(WallPatternEntry.PatternEntry.CODEC,
                "{\"type\": \"pilasters\", \"block\": \"minecraft:stone_bricks\","
                        + " \"minHeight\": 7, \"minSize\": 5, \"maxHeight\": 9, \"maxSize\": 11}");
        decodes(WallPatternEntry.CourseEntry.CODEC,
                "{\"block\": \"minecraft:stone_bricks\", \"minHeight\": 7, \"maxSize\": 11}");
        decodes(FloorPatternEntry.CODEC, "{\"type\": \"dungeons2:plain\", \"minHeight\": 7, \"maxSize\": 11}");
        decodes(CeilingPatternEntry.CODEC, "{\"patterns\": [], \"minHeight\": 7, \"maxSize\": 11}");
        decodes(CeilingPatternEntry.SurfacePatternEntry.CODEC,
                "{\"type\": \"centre\", \"block\": \"minecraft:stone_bricks\","
                        + " \"minSize\": 11, \"maxHeight\": 9}");
        decodes(PotConfig.CODEC, "{\"lootTable\": \"dungeons2:pots/classic\","
                + " \"variants\": [{\"entity\": \"dungeonblocks:pot\"}], \"minHeight\": 7, \"maxSize\": 11}");
    }

    // ---- coverage: every record in the package -------------------------------------------------

    @Test
    void everySchemeRecordRejectsAStrayKey() {
        assertTrue(parse(RoomScheme.CODEC, "{\"name\": \"n\", \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(FloorPatternEntry.CODEC, "{\"type\": \"dungeons2:plain\", \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(WallPatternEntry.CODEC,
                "{\"patterns\": [], \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(WallPatternEntry.PatternEntry.CODEC,
                "{\"type\": \"courses\", \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(WallPatternEntry.CourseEntry.CODEC,
                "{\"block\": \"minecraft:stone_bricks\", \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(CeilingPatternEntry.CODEC,
                "{\"patterns\": [], \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(CeilingPatternEntry.SurfacePatternEntry.CODEC,
                "{\"type\": \"border\", \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(PotConfig.CODEC, "{\"lootTable\": \"dungeons2:pots/classic\","
                + " \"variants\": [], \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(PotConfig.PotVariant.CODEC,
                "{\"entity\": \"dungeonblocks:pot\", \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(PillarPatternEntry.CODEC,
                "{\"patterns\": [], \"nonsense\": 1}").error().isPresent());
        assertTrue(parse(PillarPatternEntry.PillarEntry.CODEC,
                "{\"type\": \"grid\", \"block\": \"minecraft:stone_bricks\", \"nonsense\": 1}")
                .error().isPresent());
    }

    /** The pillars slot, end to end through a scheme, gates and all. */
    @Test
    void thePillarsSlotDecodes() {
        RoomScheme scheme = parse(RoomScheme.CODEC,
                "{\"name\": \"hypostyle\", \"pillars\": {\"minSize\": 13, \"patterns\": ["
                        + "{\"type\": \"grid\", \"block\": \"dungeonblocks:stone_bricks_pillar_block\","
                        + " \"baseBlock\": \"dungeonblocks:stone_bricks_pillar_base_block\","
                        + " \"capBlock\": \"dungeonblocks:stone_bricks_pillar_base_block\","
                        + " \"baseProperties\": {\"base\": \"up\"},"
                        + " \"capProperties\": {\"base\": \"down\"},"
                        + " \"spacing\": 4, \"inset\": 2}]}}")
                .result().orElseThrow();

        assertTrue(scheme.pillars().isPresent());
        assertEquals(13, scheme.pillars().orElseThrow().gate().minSize());
        assertEquals("up", scheme.pillars().orElseThrow().patterns().get(0)
                .basePropertiesOrBase().get("base"));
    }

    /** A pillar entry with no {@code block} fails: there is no default material for a column. */
    @Test
    void aPillarWithNoBlockIsALoadError() {
        assertTrue(parse(PillarPatternEntry.PillarEntry.CODEC, "{\"type\": \"grid\"}")
                .error().isPresent());
    }

    /** {@code patterns} is required on the pillars slot too -- the WallPatternEntry lesson. */
    @Test
    void aPillarsSlotWithNoPatternsKeyIsALoadError() {
        assertTrue(parse(PillarPatternEntry.CODEC, "{\"minSize\": 13}").error().isPresent());
    }

    /** A nested {@code generators} entry is the same codec, so it is closed too. */
    @Test
    void aStrayKeyInsideANestedFloorGeneratorIsALoadError() {
        assertTrue(parse(FloorPatternEntry.CODEC,
                "{\"type\": \"dungeons2:composite\", \"config\": {\"generators\": ["
                        + "{\"type\": \"dungeons2:cross\", \"config\": {"
                        + "\"block\": \"minecraft:stone_bricks\", \"thicknesss\": 2}}]}}")
                .error().isPresent());
    }

    /**
     * The pre-Aug-2026 wall slot shape. {@code patterns} being required already failed this; now it
     * fails naming the stale keys as well, which is the more actionable half of the message.
     */
    @Test
    void anUnmigratedSingleTypeWallSlotFails() {
        assertTrue(parse(WallPatternEntry.CODEC,
                "{\"type\": \"courses\", \"courses\": [{\"block\": \"minecraft:stone_bricks\"}]}")
                .error().isPresent());
    }

    // ---- the message ---------------------------------------------------------------------------

    @Test
    void theErrorListsEveryStrayKeyAndTheKnownSet() {
        String message = errorOf(CeilingPatternEntry.SurfacePatternEntry.CODEC,
                "{\"type\": \"border\", \"blokc\": \"minecraft:stone_bricks\", \"zzzzzzzzzz\": 1}");
        assertTrue(message.contains("blokc") && message.contains("zzzzzzzzzz"),
                () -> "both strays should be reported in one pass: " + message);
        assertTrue(message.contains("known fields:") && message.contains("cornerBlock"),
                () -> "the known set is the fastest way for an author to find the right spelling: " + message);
    }

    /** No suggestion is better than a bad one: nothing within edit distance means no "did you mean". */
    @Test
    void aKeyNothingResemblesGetsNoSuggestion() {
        String message = errorOf(PotConfig.PotVariant.CODEC,
                "{\"entity\": \"dungeonblocks:pot\", \"zzzzzzzzzz\": 1}");
        assertEquals(-1, message.indexOf("did you mean"), () -> message);
    }
}
