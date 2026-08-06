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
 * The companion to {@code ClosedSchemaCodecTest}: a field whose <em>name</em> is right but whose
 * <em>value</em> is malformed or out of range is a load error too.
 *
 * <p>These were the last of the silent-default family. DFU's own
 * {@link Codec#optionalFieldOf(String, Object)} cannot tell "absent" from "present but failed to
 * parse" and returns the default for both, so {@code "weight": "eight"} decoded to weight 1 and
 * {@code "corridorWidth": 5} to 3 &mdash; the dungeon generated, at a number nobody authored, with
 * no error and no log line. {@link Codecs#strictOptionalFieldOf} keeps the absent case defaulting
 * and lets the malformed case propagate.
 *
 * <p>Both shipped-content audits (keys, then values) were clean before this was switched on, so
 * nothing here changed any data file.
 */
class StrictValueCodecTest {

    private static final Gson GSON = new Gson();

    private static <A> DataResult<A> parse(Codec<A> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class));
    }

    private static <A> void fails(Codec<A> codec, String json) {
        assertTrue(parse(codec, json).error().isPresent(),
                () -> "expected a load error, but this decoded cleanly: " + json);
    }

    private static <A> A decode(Codec<A> codec, String json) {
        return parse(codec, json).result()
                .orElseThrow(() -> new AssertionError("expected this to decode: " + json));
    }

    // ---- wrong type ---------------------------------------------------------------------------

    @Test
    void aNonNumericWeightIsALoadErrorNotWeightOne() {
        fails(RoomScheme.CODEC, "{\"name\": \"n\", \"weight\": \"eight\"}");
        fails(FloorPatternEntry.CODEC, "{\"type\": \"border\", \"weight\": \"eight\"}");
        fails(PotConfig.PotVariant.CODEC, "{\"entity\": \"dungeonblocks:pot\", \"weight\": \"eight\"}");
    }

    @Test
    void aNonStringBlockIdIsALoadErrorNotAnAbsentBlock() {
        // Absent means "fall back to another authored value" for all of these, so swallowing a
        // malformed one produced a wall built from the wrong -- but real -- block.
        fails(WallPatternEntry.CourseEntry.CODEC,
                "{\"block\": \"minecraft:stone_bricks\", \"cornerBlock\": 42}");
        fails(CeilingPatternEntry.SurfacePatternEntry.CODEC,
                "{\"type\": \"border\", \"block\": \"minecraft:stone_bricks\", \"cornerBlock\": 42}");
        fails(FloorPatternEntry.CODEC, "{\"type\": \"border\", \"primaryBlock\": 42}");
    }

    /**
     * The likeliest slip of the lot, and the worst-behaved: one unquoted boolean used to throw the
     * author's <em>entire</em> property map away, silently falling back to {@code properties}.
     */
    @Test
    void anUnquotedPropertyValueIsALoadErrorNotAnEmptyMap() {
        fails(WallPatternEntry.PatternEntry.CODEC,
                "{\"type\": \"pilasters\", \"block\": \"minecraft:stone_brick_stairs\","
                        + " \"capProperties\": {\"half\": \"top\", \"waterlogged\": false}}");
        fails(WallPatternEntry.PatternEntry.CODEC,
                "{\"type\": \"pilasters\", \"block\": \"minecraft:stone_brick_stairs\","
                        + " \"baseProperties\": {\"layers\": 4}}");
    }

    // ---- out of range -------------------------------------------------------------------------

    @Test
    void anOutOfRangeValueIsALoadErrorNotASilentDefault() {
        fails(RoomScheme.CODEC, "{\"name\": \"n\", \"weight\": 0}");
        fails(RoomScheme.CODEC, "{\"name\": \"n\", \"minHeight\": -1}");
        fails(FloorPatternEntry.CODEC, "{\"type\": \"speckle\", \"probability\": 1.5}");
        fails(FloorPatternEntry.CODEC, "{\"type\": \"cross\", \"thickness\": -1}");
        fails(PotConfig.CODEC, "{\"lootTable\": \"dungeons2:pots/classic\", \"variants\": [],"
                + " \"minCount\": -1}");
    }

    /**
     * {@code "weight": 0} deserves its own note: it reads as "turn this scheme off", and it used to
     * do the exact opposite -- decoding to the default of 1 and leaving the scheme in the roll.
     */
    @Test
    void aZeroWeightFailsRatherThanQuietlyEnablingTheScheme() {
        fails(RoomScheme.CODEC, "{\"name\": \"off\", \"weight\": 0}");
    }

    @Test
    void anOutOfRangeCorridorWidthIsALoadErrorNotThreeBlocksWide() {
        fails(DungeonGenerationConfig.CODEC, "{\"corridorWidth\": 5}");
        fails(DungeonGenerationConfig.CODEC, "{\"corridorWidth\": 0}");
        fails(DungeonGenerationConfig.CODEC, "{\"corridorWidth\": \"wide\"}");
        fails(DungeonGenerationConfig.CODEC, "{\"corridorWith\": 2}");
    }

    // ---- inverted per-entry gates ----------------------------------------------------------------

    /**
     * A gate whose maximum is below its minimum fits no room, so the entry silently never draws --
     * indistinguishable at generation time from one that merely never came up. {@code RoomScheme}
     * validated the SLOT gates from the day they shipped, but never reached the per-entry ones, so
     * this was latent on the wall side and arrived with the ceiling's (backlog #24).
     */
    @Test
    void anInvertedGateOnAPatternIsALoadError() {
        fails(CeilingPatternEntry.CODEC,
                "{\"patterns\": [{\"type\": \"centre\", \"block\": \"minecraft:stone_bricks\","
                        + " \"minSize\": 11, \"maxSize\": 5}]}");
        fails(WallPatternEntry.CODEC,
                "{\"patterns\": [{\"type\": \"pilasters\", \"block\": \"minecraft:stone_bricks\","
                        + " \"minHeight\": 9, \"maxHeight\": 6}]}");
    }

    @Test
    void anInvertedGateOnASingleCourseIsALoadError() {
        fails(WallPatternEntry.CODEC,
                "{\"patterns\": [{\"type\": \"courses\", \"courses\": ["
                        + "{\"block\": \"minecraft:stone_bricks\", \"minHeight\": 9, \"maxHeight\": 6}]}]}");
    }

    /** A gate that is merely narrow is fine -- only an empty range is the error. */
    @Test
    void aNarrowButSatisfiableGateStillDecodes() {
        assertEquals(1, decode(WallPatternEntry.CODEC,
                "{\"patterns\": [{\"type\": \"pilasters\", \"block\": \"minecraft:stone_bricks\","
                        + " \"minHeight\": 7, \"maxHeight\": 7}]}").patterns().size());
    }

    // ---- absent still defaults ------------------------------------------------------------------

    /** The half that must NOT change: an omitted field is still the default, not an error. */
    @Test
    void anAbsentFieldStillTakesItsDefault() {
        assertEquals(1, decode(RoomScheme.CODEC, "{\"name\": \"n\"}").weight());
        assertEquals(0, decode(RoomScheme.CODEC, "{\"name\": \"n\"}").minHeight());
        assertEquals(1, decode(FloorPatternEntry.CODEC, "{\"type\": \"border\"}").weight());
        assertEquals(SizeGate.UNBOUNDED,
                decode(FloorPatternEntry.CODEC, "{\"type\": \"border\"}").gate());
        assertEquals(DungeonGenerationConfig.DEFAULT.corridorWidth(),
                decode(DungeonGenerationConfig.CODEC, "{}").corridorWidth());
        assertTrue(decode(WallPatternEntry.CourseEntry.CODEC,
                "{\"block\": \"minecraft:stone_bricks\"}").cornerBlock().isEmpty());
    }

    /** And a well-formed value still decodes, gates and all. */
    @Test
    void wellFormedValuesStillDecode() {
        assertEquals(9, decode(RoomScheme.CODEC,
                "{\"name\": \"n\", \"weight\": 9, \"minHeight\": 7, \"minSize\": 5}").weight());
        assertEquals(2, decode(DungeonGenerationConfig.CODEC, "{\"corridorWidth\": 2}").corridorWidth());
        assertEquals("minecraft:polished_andesite", decode(WallPatternEntry.PatternEntry.CODEC,
                "{\"type\": \"pilasters\", \"block\": \"minecraft:polished_andesite\","
                        + " \"spacing\": 4, \"capProperties\": {\"half\": \"top\"}}")
                .block().orElseThrow());
    }
}
