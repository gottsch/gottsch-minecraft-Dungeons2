package mod.gottsch.forge.dungeons2.core.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    // -------- corridor styles (per-floor geometry) --------

    private static final String STYLES = ",\"styles\": [%s]";
    private static final String VAULTED =
            "{\"name\": \"vaulted\",\"weight\": 3,\"height\": 7,\"narrowHeight\": 6,"
                    + "\"profile\": \"arched\",\"archBlock\": \"minecraft:stone_brick_stairs\"}";
    private static final String CRAMPED = "{\"name\": \"cramped\",\"weight\": 2,\"height\": 5}";

    private static CorridorConfig corridorWithStyles(String styleJson) {
        return fragment(String.format(CORRIDOR, String.format(STYLES, styleJson)))
                .corridor().orElseThrow();
    }

    /** Every motif that exists today authors no styles, and must keep behaving as one fixed shape. */
    @Test
    void aCorridorWithNoAuthoredStylesRollsOnlyItsBaseline() {
        CorridorConfig corridor = fragment(String.format(CORRIDOR, ",\"height\": 7")).corridor().orElseThrow();

        assertTrue(corridor.styles().isEmpty());
        assertEquals(List.of(corridor.baseline()), corridor.rollableStyles());
        assertEquals(7, corridor.baseline().height());
        assertEquals(CorridorStyle.BASELINE, corridor.baseline().name());
    }

    @Test
    void authoredStylesAreReadWithTheirWeightsAndGeometry() {
        CorridorConfig corridor = corridorWithStyles(VAULTED + "," + CRAMPED);

        assertEquals(List.of("vaulted", "cramped"),
                corridor.styles().stream().map(CorridorStyle::name).toList());
        assertEquals(3, corridor.styleFor("vaulted").weight());
        assertEquals(7, corridor.styleFor("vaulted").height());
        assertEquals(6, corridor.styleFor("vaulted").narrowCellHeight());
        assertTrue(corridor.styleFor("vaulted").isArched());
        assertEquals(5, corridor.styleFor("cramped").height());
        assertFalse(corridor.styleFor("cramped").isArched());
        // No narrowHeight authored means no drop -- the default arrived at the hard way, see
        // CorridorConfig#narrowCellHeight.
        assertEquals(5, corridor.styleFor("cramped").narrowCellHeight());
    }

    /**
     * A style name arrives from a <em>saved piece</em>, not from a datapack, so this one lookup is
     * deliberately lenient where the rest of the config is strict: renaming a style must not crash
     * chunk load for a world generated before the rename.
     */
    @Test
    void anUnknownStyleNameFallsBackToTheBaselineRatherThanFailing() {
        CorridorConfig corridor = corridorWithStyles(VAULTED);

        assertEquals(corridor.baseline(), corridor.styleFor("renamed_last_week"));
        assertEquals(corridor.baseline(), corridor.styleFor(null));
        assertEquals(corridor.baseline(), corridor.styleFor(""));
    }

    /**
     * A corridor stores only its style's <em>name</em>, so two styles sharing one would make which
     * geometry a corridor gets depend on the order of the list -- silently, and only for whichever
     * floors happened to roll it.
     */
    @Test
    void duplicateStyleNamesAreALoadError() {
        assertTrue(fails(String.format(CORRIDOR, String.format(STYLES, VAULTED + "," + VAULTED))),
                "two styles of the same name must fail to load");
    }

    @Test
    void aStyleWithABlankNameIsALoadError() {
        assertTrue(fails(String.format(CORRIDOR,
                        String.format(STYLES, "{\"name\": \"  \",\"height\": 6}"))),
                "a blank style name must fail -- it would shadow the baseline");
        assertTrue(fails(String.format(CORRIDOR, String.format(STYLES, "{\"height\": 6}"))),
                "a style with no name at all must fail");
    }

    /**
     * The three geometry rules apply per style, not just to the baseline. A styles list is otherwise
     * a way to smuggle in exactly the shapes the section itself rejects.
     */
    @Test
    void aStyleIsHeldToTheSameGeometryRulesAsTheCorridorItself() {
        assertTrue(fails(String.format(CORRIDOR, String.format(STYLES,
                        "{\"name\": \"squat\",\"height\": 5,\"profile\": \"arched\","
                                + "\"archBlock\": \"minecraft:stone_brick_stairs\"}"))),
                "an arched style at height 5 must fail, same as an arched corridor at height 5");
        assertTrue(fails(String.format(CORRIDOR, String.format(STYLES,
                        "{\"name\": \"bare\",\"height\": 7,\"profile\": \"arched\"}"))),
                "an arched style with no archBlock must fail rather than inventing stairs");
        assertTrue(fails(String.format(CORRIDOR, String.format(STYLES,
                        "{\"name\": \"tall\",\"height\": 12}"))),
                "an over-tall style must fail, not clamp");
        assertTrue(fails(String.format(CORRIDOR, String.format(STYLES,
                        "{\"name\": \"odd\",\"height\": 6,\"narrowHeight\": 8}"))),
                "a narrowHeight above the style's own height must fail");
    }

    @Test
    void aStyleWithNoWeightIsAsLikelyAsAnyOtherSingleWeightStyle() {
        assertEquals(CorridorStyle.DEFAULT_WEIGHT,
                corridorWithStyles("{\"name\": \"plainish\",\"height\": 6}").styleFor("plainish").weight());
        assertTrue(fails(String.format(CORRIDOR, String.format(STYLES,
                        "{\"name\": \"never\",\"weight\": 0,\"height\": 6}"))),
                "weight 0 must fail rather than authoring a style that can never be rolled");
    }

    // -------- corridor courses --------

    private static final String COURSES = ",\"courses\": [%s]";
    private static final String PLINTH = "{\"block\": \"minecraft:polished_andesite\",\"anchor\": \"bottom\"}";

    private static CorridorConfig corridorWithCourses(String courseJson) {
        return fragment(String.format(CORRIDOR, String.format(COURSES, courseJson)))
                .corridor().orElseThrow();
    }

    @Test
    void aCorridorDefaultsToNoCourses() {
        assertTrue(fragment(String.format(CORRIDOR, "")).corridor().orElseThrow().courses().isEmpty());
    }

    @Test
    void corridorCoursesReuseTheRoomCourseEntryVerbatim() {
        CorridorConfig corridor = corridorWithCourses(PLINTH + ",{\"block\": \"minecraft:andesite\","
                + "\"alternateBlock\": \"minecraft:stone\",\"alternate\": \"strict\","
                + "\"anchor\": \"top\",\"offset\": 2,\"orient\": \"toward_wall\"}");

        assertEquals(2, corridor.courses().size());
        WallPatternEntry.CourseEntry crown = corridor.courses().get(1);
        assertEquals(WallPatternEntry.CourseAnchor.TOP, crown.anchor());
        assertEquals(2, crown.offset());
        assertEquals(WallPatternEntry.CourseAlternate.STRICT, crown.alternate());
        assertEquals(WallPatternEntry.CourseOrient.TOWARD_WALL, crown.orient());
        assertEquals("minecraft:stone", crown.alternateBlockOrBase());
    }

    /** The baseline's courses flow onto the baseline style, which is what the generator reads. */
    @Test
    void baselineCoursesReachTheBaselineStyle() {
        CorridorConfig corridor = corridorWithCourses(PLINTH);

        assertEquals(corridor.courses(), corridor.baseline().courses());
        assertEquals(corridor.courses(), corridor.styleFor("nonexistent").courses());
    }

    /** A style's courses are its own -- that is the point of hanging them off the style. */
    @Test
    void aStyleCarriesItsOwnCoursesIndependentOfTheBaseline() {
        CorridorConfig corridor = fragment(String.format(CORRIDOR,
                        String.format(COURSES, PLINTH)
                                + String.format(STYLES, "{\"name\": \"plain\",\"height\": 6,\"courses\": []}")))
                .corridor().orElseThrow();

        assertEquals(1, corridor.baseline().courses().size());
        assertTrue(corridor.styleFor("plain").courses().isEmpty(),
                "an explicitly empty styles course list must not inherit the baseline's");
    }

    /**
     * The three parts of a room course that have no corridor meaning. Each is a load error rather
     * than a silent drop, because all three fail invisibly -- the course still draws, just not the
     * way it was authored.
     */
    @Test
    void theThreeRoomOnlyCourseKnobsAreLoadErrorsOnACorridor() {
        assertTrue(fails(String.format(CORRIDOR, String.format(COURSES,
                        "{\"block\": \"minecraft:andesite\",\"cornerBlock\": \"minecraft:stone\"}"))),
                "cornerBlock must fail -- a corridor wall has no four runs to own corners");
        assertTrue(fails(String.format(CORRIDOR, String.format(COURSES,
                        "{\"block\": \"minecraft:andesite\",\"projection\": 1}"))),
                "a projecting corridor course must fail -- it would project into the passage itself");
        assertTrue(fails(String.format(CORRIDOR, String.format(COURSES,
                        "{\"block\": \"minecraft:andesite\",\"minHeight\": 6}"))),
                "a size gate must fail -- it gates on room dimensions a corridor does not have");
    }

    /** The same rules apply inside a style, or a styles list is a way to smuggle them back in. */
    @Test
    void aStyleCourseIsHeldToTheSameCorridorRules() {
        assertTrue(fails(String.format(CORRIDOR, String.format(STYLES,
                        "{\"name\": \"trimmed\",\"height\": 6,\"courses\": ["
                                + "{\"block\": \"minecraft:andesite\",\"projection\": 1}]}"))),
                "a projecting course inside a style must fail too");
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
