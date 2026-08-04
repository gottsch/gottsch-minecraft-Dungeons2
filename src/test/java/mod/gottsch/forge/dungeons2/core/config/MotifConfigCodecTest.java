package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip, defaulting and merge tests for the {@link MotifConfigFragment} codec and the
 * {@link MotifConfig} it resolves to. Pure codec work &mdash; block ids stay as strings and are only
 * resolved against the registry at render time, so no Minecraft bootstrap is required here.
 */
class MotifConfigCodecTest {

    private static final Gson GSON = new Gson();

    private static MotifConfigFragment fragment(String json) {
        return MotifConfigFragment.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class))
                .result().orElseThrow();
    }

    private static boolean fails(String json) {
        return MotifConfigFragment.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class))
                .error().isPresent();
    }

    @Test
    void fullFragmentRoundTrips() {
        MotifConfigFragment config = new MotifConfigFragment(
                Optional.of(new WallConfig("minecraft:stone_bricks")),
                Optional.of(new CeilingConfig("minecraft:stone_bricks")),
                Optional.of(new DoorConfig("dungeonblocks:spruce_dungeon_door",
                        "minecraft:polished_andesite", "minecraft:polished_andesite")),
                Optional.of(new CorridorConfig("minecraft:cobblestone", "minecraft:gravel",
                        "minecraft:stone_bricks", CorridorConfig.DEFAULT_HEIGHT)),
                Optional.of(new FloorConfig("minecraft:stone_bricks", "minecraft:stone_bricks")),
                List.of(new RoomScheme("plain", 8, 0, 0),
                        new RoomScheme("bordered", 1, 6, 5,
                                Optional.of(new FloorPatternEntry("border", 1, 2)),
                                Optional.of(new WallPatternEntry("courses", List.of(
                                        new WallPatternEntry.CourseEntry("minecraft:polished_andesite",
                                                WallPatternEntry.CourseAnchor.TOP, 0)))),
                                Optional.of(new CeilingPatternEntry(List.of(
                                        new CeilingPatternEntry.SurfacePatternEntry(
                                                "coffers", "minecraft:polished_andesite")))),
                                Optional.empty())));

        JsonElement json = MotifConfigFragment.CODEC.encodeStart(JsonOps.INSTANCE, config).result().orElseThrow();
        MotifConfigFragment back = MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(config, back);
    }

    /**
     * The catacombs/deep_slate case: a motif that only overrides its wall. Every absent section
     * must fall back to that section's DEFAULT, reproducing the pre-merge behaviour where a
     * BlockProvider with no entry for a pattern left the generator on its hardcoded stone_bricks.
     */
    @Test
    void absentSectionsFallBackToTheirDefaults() {
        MotifConfig config = MotifConfigFragment.resolve(
                List.of(fragment("{\"wall\": {\"wall\": \"minecraft:bricks\"}}")));

        assertEquals("minecraft:bricks", config.wall().wall());
        assertEquals(CeilingConfig.DEFAULT, config.ceiling());
        assertEquals(DoorConfig.DEFAULT, config.door());
        assertEquals(CorridorConfig.DEFAULT, config.corridor());
        assertEquals(FloorConfig.DEFAULT, config.floor());
        assertEquals(List.of(RoomScheme.PLAIN), config.schemes());
    }

    /**
     * The whole reason fragments are a separate type from {@link MotifConfig}: a file that says
     * nothing about walls must not carry the default wall into the merge and stomp the file that
     * did author one. Absence has to survive decoding.
     */
    @Test
    void aFragmentThatOmitsASectionDoesNotStompAnEarlierOne() {
        MotifConfig config = MotifConfigFragment.resolve(List.of(
                fragment("{\"wall\": {\"wall\": \"minecraft:bricks\"}}"),
                fragment("{\"schemes\": [{\"name\": \"only_schemes_here\"}]}")));

        assertEquals("minecraft:bricks", config.wall().wall(),
                "a schemes-only fragment must leave the base file's wall alone");
        assertNotEquals(List.of(RoomScheme.PLAIN), config.schemes());
    }

    // -------- corridor height --------

    private static final String CORRIDOR = "{\"corridor\": {\"floor\": \"minecraft:stone_bricks\","
            + "\"alternateFloor\": \"minecraft:stone_bricks\",\"ceiling\": \"minecraft:stone_bricks\"%s}}";

    /** A corridor section that authors no height generates exactly what it did before. */
    @Test
    void aCorridorWithNoAuthoredHeightKeepsTheHistoricalFive() {
        assertEquals(CorridorConfig.DEFAULT_HEIGHT,
                fragment(String.format(CORRIDOR, "")).corridor().orElseThrow().height());
    }

    @Test
    void anAuthoredCorridorHeightIsRead() {
        assertEquals(7, fragment(String.format(CORRIDOR, ",\"height\": 7")).corridor().orElseThrow().height());
    }

    /**
     * The rule the plan is explicit about: an over-tall corridor is a <em>load error</em>, not a
     * silent clamp back to 5. This is exactly what DFU's {@code optionalFieldOf} gets wrong &mdash;
     * it cannot tell "absent" from "present but out of range" and hands back the default for both,
     * which would let a datapack ask for 12 and generate 5 with no complaint anywhere.
     */
    @Test
    void anOutOfRangeCorridorHeightIsALoadErrorNotASilentClamp() {
        assertTrue(fails(String.format(CORRIDOR, ",\"height\": 12")),
                "a height above the cap must fail to load");
        assertTrue(fails(String.format(CORRIDOR, ",\"height\": 3")),
                "a height that would swallow the door column must fail to load");
        assertTrue(fails(String.format(CORRIDOR, ",\"height\": \"tall\"")),
                "a non-numeric height must fail to load");
    }

    // -------- corridor profile --------

    @Test
    void aCorridorDefaultsToTheFlatProfile() {
        assertEquals(CorridorConfig.Profile.FLAT,
                fragment(String.format(CORRIDOR, "")).corridor().orElseThrow().profile());
    }

    @Test
    void anArchedCorridorIsRead() {
        CorridorConfig corridor = fragment(String.format(CORRIDOR,
                ",\"height\": 7,\"profile\": \"arched\",\"archBlock\": \"minecraft:stone_brick_stairs\""))
                .corridor().orElseThrow();

        assertTrue(corridor.isArched());
        assertEquals("minecraft:stone_brick_stairs", corridor.archBlock().orElseThrow());
    }

    /**
     * An arch one block too short would put its haunch row on the doorway's lintel. Failing beats
     * quietly falling back to flat: a dungeon that generates fine but isn't what was authored is
     * indistinguishable, in game, from the feature not working at all.
     */
    @Test
    void anArchedCorridorTooShortForItsHaunchIsALoadError() {
        assertTrue(fails(String.format(CORRIDOR,
                        ",\"height\": 5,\"profile\": \"arched\",\"archBlock\": \"minecraft:stone_brick_stairs\"")),
                "arched at height 5 must fail to load");
    }

    /** Same rule as a `door` section with no `lintel`: never invent a block the author didn't name. */
    @Test
    void anArchedCorridorWithNoArchBlockIsALoadError() {
        assertTrue(fails(String.format(CORRIDOR, ",\"height\": 7,\"profile\": \"arched\"")),
                "arched with no archBlock must fail rather than defaulting to stone brick stairs");
    }

    @Test
    void anUnknownProfileIsALoadError() {
        assertTrue(fails(String.format(CORRIDOR, ",\"profile\": \"vaulted\"")),
                "a typo'd profile must fail rather than silently reading as flat");
    }

    /** Later fragment wins a section outright; sections are whole, never merged field by field. */
    @Test
    void aLaterFragmentReplacesASectionItAuthors() {
        MotifConfig config = MotifConfigFragment.resolve(List.of(
                fragment("{\"wall\": {\"wall\": \"minecraft:bricks\"}}"),
                fragment("{\"wall\": {\"wall\": \"minecraft:deepslate_bricks\"}}")));

        assertEquals("minecraft:deepslate_bricks", config.wall().wall());
    }

    /** Schemes concatenate across files -- the point of splitting a motif's list up. */
    @Test
    void schemesConcatenateAcrossFragments() {
        MotifConfig config = MotifConfigFragment.resolve(List.of(
                fragment("{\"schemes\": [{\"name\": \"plain\", \"weight\": 12}]}"),
                fragment("{\"schemes\": [{\"name\": \"floors_a\"}, {\"name\": \"floors_b\"}]}"),
                fragment("{\"schemes\": [{\"name\": \"walls_a\"}]}")));

        assertEquals(List.of("plain", "floors_a", "floors_b", "walls_a"),
                config.schemes().stream().map(RoomScheme::name).toList());
    }

    /**
     * A scheme is addressable by name: a later file retunes it rather than duplicating it, and does
     * so without reordering the ones around it.
     */
    @Test
    void aLaterSchemeOfTheSameNameReplacesTheEarlierInPlace() {
        MotifConfig config = MotifConfigFragment.resolve(List.of(
                fragment("{\"schemes\": [{\"name\": \"plain\", \"weight\": 12}, {\"name\": \"other\"}]}"),
                fragment("{\"schemes\": [{\"name\": \"plain\", \"weight\": 3}]}")));

        assertEquals(List.of("plain", "other"), config.schemes().stream().map(RoomScheme::name).toList());
        assertEquals(3, config.schemes().get(0).weight());
    }

    /** No fragments at all is the "motif has no folder" case, and is not an error. */
    @Test
    void noFragmentsResolvesToTheDefault() {
        assertEquals(MotifConfig.DEFAULT, MotifConfigFragment.resolve(List.of()));
    }

    /**
     * A section that IS present must be complete -- there are no per-slot defaults, so a
     * half-authored section fails loudly at load rather than silently rendering someone else's
     * block. This is the failure mode the pre-merge string-to-enum lookup could not produce.
     */
    @Test
    void aPartiallyAuthoredSectionFailsToDecode() {
        assertTrue(fails("{\"door\": {\"door\": \"minecraft:oak_door\"}}"),
                "a door section missing lintel/floor should fail to decode, not silently default");
    }

    @Test
    void floorBaseBlocksAreRequiredWhenTheSectionIsPresent() {
        fragment("{\"floor\": {\"base\": \"minecraft:stone_bricks\", "
                + "\"alternateBase\": \"minecraft:stone_bricks\"}}");

        assertTrue(fails("{\"floor\": {}}"),
                "base/alternateBase are required when a floor section is present");
    }

    /**
     * A scheme's element slots are all optional -- a scheme with nothing but a name is the
     * undecorated room -- but a slot that IS present must decode, same strictness the sections get.
     */
    @Test
    void schemeSlotsAreOptionalButMustDecodeWhenPresent() {
        MotifConfigFragment config = fragment("{\"schemes\": [{\"name\": \"plain\"}]}");
        assertEquals(1, config.schemes().size());
        assertTrue(config.schemes().get(0).floor().isEmpty(), "an absent floor slot means undecorated");

        assertTrue(fails("{\"schemes\": [{\"name\": \"broken\", \"floor\": {\"weight\": 1}}]}"),
                "a floor slot missing its required 'type' should fail to decode, not silently default");
    }

    /** {@code name} is the one required scheme field -- it is what a log line can identify. */
    @Test
    void aSchemeWithoutANameFailsToDecode() {
        assertTrue(fails("{\"schemes\": [{\"weight\": 3}]}"));
    }

    /**
     * A course's alternate/corner blocks default to its base block, so a band authored the old way
     * is still a uniform band.
     */
    @Test
    void courseAlternateAndCornerDefaultToTheBaseBlock() {
        MotifConfigFragment config = fragment("{\"schemes\": [{\"name\": \"trim\", \"wall\": "
                + "{\"type\": \"courses\", \"courses\": [{\"block\": \"minecraft:polished_andesite\"}]}}]}");

        WallPatternEntry.CourseEntry course =
                config.schemes().get(0).wall().orElseThrow().courses().get(0);
        assertTrue(course.alternateBlock().isEmpty());
        assertTrue(course.cornerBlock().isEmpty());
        assertEquals("minecraft:polished_andesite", course.alternateBlockOrBase());
        assertEquals("minecraft:polished_andesite", course.cornerBlockOrBase());
    }

    @Test
    void courseAlternateAndCornerAreReadWhenAuthored() {
        MotifConfigFragment config = fragment("{\"schemes\": [{\"name\": \"trim\", \"wall\": "
                + "{\"type\": \"courses\", \"courses\": [{\"block\": \"minecraft:polished_andesite\", "
                + "\"alternateBlock\": \"minecraft:andesite\", "
                + "\"cornerBlock\": \"minecraft:chiseled_stone_bricks\"}]}}]}");

        WallPatternEntry.CourseEntry course =
                config.schemes().get(0).wall().orElseThrow().courses().get(0);
        assertEquals("minecraft:andesite", course.alternateBlockOrBase());
        assertEquals("minecraft:chiseled_stone_bricks", course.cornerBlockOrBase());
    }
}
