package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.FloorRange;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.SizeGate;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room-level weighted roll, and the eligibility filter that is the reason this exists as its
 * own step rather than staying inside the floor selector. Pure data &mdash; no block ids are
 * resolved here, so no Minecraft bootstrap is needed.
 */
class RoomSchemeSelectorTest {

    /** The entrance floor. These cases are about size and weight, not depth. */
    private static final int ENTRANCE_FLOOR = 0;

    /** A scheme with no dimensional constraints. */
    private static RoomScheme scheme(String name, int weight) {
        return new RoomScheme(name, weight, 0, 0);
    }

    private static RoomScheme gated(String name, int weight, int minHeight, int minSize) {
        return new RoomScheme(name, weight, minHeight, minSize);
    }

    /** A scheme confined to the small end: bounded above, unbounded below. */
    private static RoomScheme capped(String name, int weight, Integer maxHeight, Integer maxSize) {
        return new RoomScheme(name, weight, 0, 0,
                Optional.ofNullable(maxHeight), Optional.ofNullable(maxSize),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    // ---------- upper bounds ----------

    /**
     * The gap minimums cannot close. Without maxSize a modest scheme stays eligible in the biggest
     * rooms, so the grand schemes are permanently a minority of the weight there and no amount of
     * raising their weight fixes it.
     */
    @Test
    void aSchemeCappedBelowTheRoomIsNeverRolled() {
        List<RoomScheme> schemes = List.of(scheme("grand", 1), capped("cosy", 50, null, 7));
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            assertEquals("grand", RoomSchemeSelector.select(schemes, 15, 15, 10, ENTRANCE_FLOOR, random).name(),
                    "a maxSize 7 scheme must never fire in a 15-wide room");
        }
    }

    @Test
    void aCappedSchemeStillFiresInsideItsBand() {
        List<RoomScheme> schemes = List.of(capped("cosy", 1, 6, 7));
        assertEquals("cosy", RoomSchemeSelector.select(schemes, 7, 7, 6, ENTRANCE_FLOOR, RandomSource.create(3)).name());
        assertEquals("cosy", RoomSchemeSelector.select(schemes, 5, 5, 5, ENTRANCE_FLOOR, RandomSource.create(3)).name());
    }

    /** Both bounds are inclusive, on the same numbers their minimums use. */
    @Test
    void boundsAreInclusive() {
        RoomScheme capped = capped("cosy", 1, 7, 9);
        assertTrue(capped.fits(9, 9, 7), "exactly on both bounds should still fit");
        // Both sides have to grow: maxSize measures the smaller one, so 11x9 is still a 9.
        assertFalse(capped.fits(11, 11, 7), "one over maxSize");
        assertFalse(capped.fits(9, 9, 8), "one over maxHeight");
    }

    /** maxSize measures the SMALLER side, same as minSize -- a long thin room is judged by its narrow axis. */
    @Test
    void maxSizeMeasuresTheSmallerSide() {
        RoomScheme capped = capped("cosy", 1, null, 7);
        assertTrue(capped.fits(17, 5, 5), "a 17x5 room is narrow, so it is inside a maxSize of 7");
        assertFalse(capped.fits(9, 9, 5));
    }

    /** A room outside every band falls through to the undecorated room, not to an ineligible scheme. */
    @Test
    void aRoomOutsideEveryBandDegradesToPlain() {
        List<RoomScheme> schemes = List.of(capped("cosy", 5, 6, 7), gated("grand", 5, 9, 11));
        assertEquals(RoomScheme.PLAIN,
                RoomSchemeSelector.select(schemes, 9, 9, 7, ENTRANCE_FLOOR, RandomSource.create(1)));
    }

    /** A roomy room: nothing is gated out of a 15x15x10. */
    private static RoomScheme select(List<RoomScheme> schemes, RandomSource random) {
        return RoomSchemeSelector.select(schemes, 15, 15, 10, ENTRANCE_FLOOR, random);
    }

    @Test
    void anEmptySchemeListDegradesToPlain() {
        assertEquals(RoomScheme.PLAIN, select(List.of(), RandomSource.create(1)));
    }

    @Test
    void weightedPickReturnsEverySchemeOverManyRolls() {
        List<RoomScheme> schemes = List.of(scheme("plain", 8), scheme("bordered", 1));
        RandomSource random = RandomSource.create(42);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(select(schemes, random).name());
        }
        assertEquals(Set.of("plain", "bordered"), seen);
    }

    @Test
    void aSchemeTallerThanTheRoomIsNeverRolled() {
        // 5 is the shortest room DungeonStackPlanner#pickRoomHeight can produce, and leaves only
        // three interior wall rows -- two door halves and the lintel. Nothing fits above that.
        List<RoomScheme> schemes = List.of(scheme("plain", 1), gated("vaulted", 50, 9, 0));
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            assertEquals("plain", RoomSchemeSelector.select(schemes, 15, 15, 5, ENTRANCE_FLOOR, random).name());
        }
    }

    @Test
    void aSchemeWiderThanTheRoomIsNeverRolled() {
        // minSize gates on the SMALLER axis: a 20x5 corridor-shaped room is as unsuitable for a
        // centred radial pattern as a 5x5 one, however long it is.
        List<RoomScheme> schemes = List.of(scheme("plain", 1), gated("spokes", 50, 0, 7));
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            assertEquals("plain", RoomSchemeSelector.select(schemes, 20, 5, 10, ENTRANCE_FLOOR, random).name());
        }
    }

    @Test
    void aRoomMatchingNoSchemeDegradesToPlainRatherThanForcingOne() {
        List<RoomScheme> schemes = List.of(gated("grand", 1, 10, 12), gated("vaulted", 1, 9, 0));
        assertEquals(RoomScheme.PLAIN, RoomSchemeSelector.select(schemes, 5, 5, 5, ENTRANCE_FLOOR, RandomSource.create(1)));
    }

    /**
     * The filter runs before weights are totalled, so an ineligible scheme's weight leaves the
     * denominator entirely. If it merely skipped, the 90 weight below would still be drawn ~90% of
     * the time and pool into whichever scheme happened to follow it.
     */
    @Test
    void anIneligibleSchemesWeightLeavesTheDenominator() {
        List<RoomScheme> schemes = List.of(
                gated("grand", 90, 10, 0), scheme("a", 5), scheme("b", 5));
        RandomSource random = RandomSource.create(3);
        int aCount = 0;
        int total = 2000;
        for (int i = 0; i < total; i++) {
            String name = RoomSchemeSelector.select(schemes, 15, 15, 6, ENTRANCE_FLOOR, random).name();
            assertFalse("grand".equals(name), "a scheme needing height 10 must not be rolled at 6");
            if ("a".equals(name)) {
                aCount++;
            }
        }
        // 5:5 between the two survivors, not 5:95.
        assertTrue(Math.abs(aCount - total / 2) < total / 10,
                "surviving schemes should keep their relative weights, got " + aCount + "/" + total);
    }

    @Test
    void theChosenSchemeCarriesItsFloorSlotThrough() {
        FloorPatternEntry border = FloorPatternEntry.PLAIN;
        RoomScheme only = new RoomScheme("bordered", 1, 0, 0, Optional.of(border),
                Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(Optional.of(border), select(List.of(only), RandomSource.create(1)).floor());
    }

    /**
     * One draw per call regardless of how many schemes survive the filter -- callers rely on the
     * room's random advancing by a fixed amount, since a room is built once per overlapping chunk.
     */
    @Test
    void exactlyOneRandomValueIsConsumedPerCall() {
        List<RoomScheme> oneScheme = List.of(scheme("only", 1));
        List<RoomScheme> threeSchemes = List.of(scheme("a", 1), scheme("b", 1), scheme("c", 1));

        RandomSource a = RandomSource.create(99);
        select(oneScheme, a);
        RandomSource b = RandomSource.create(99);
        select(threeSchemes, b);

        assertEquals(a.nextLong(), b.nextLong(),
                "both calls should have advanced the stream by exactly one draw");
    }

    // ---------- per-element gates ----------

    private static WallPatternEntry crown(SizeGate gate) {
        return new WallPatternEntry("courses", List.of(
                new WallPatternEntry.CourseEntry("minecraft:polished_andesite", CourseAnchor.TOP, 0)),
                gate);
    }

    private static SizeGate from(int minHeight) {
        return new SizeGate(minHeight, 0, Optional.empty(), Optional.empty());
    }

    /** A gated slot the room fails is dropped; the rest of the scheme still draws. */
    @Test
    void anElementOutsideItsGateIsDroppedButTheSchemeStillApplies() {
        RoomScheme scheme = new RoomScheme("bordered", 5, 0, 0,
                Optional.empty(), Optional.empty(),
                Optional.of(FloorPatternEntry.PLAIN),
                Optional.of(crown(from(6))), Optional.empty(), Optional.empty());

        assertTrue(scheme.fits(9, 9, 5), "the scheme itself is ungated, so it still gets rolled");
        assertTrue(scheme.floorFor(9, 9, 5).isPresent(), "the floor is ungated and still draws");
        assertTrue(scheme.wallFor(9, 9, 5).isEmpty(), "no headroom for a crown in a 5-high room");

        assertTrue(scheme.wallFor(9, 9, 6).isPresent(), "one block taller and the crown appears");
    }

    /**
     * The distinction the whole feature rests on: an element gate changes what is drawn, never any
     * probability. The scheme keeps its full weight in a room where one of its slots drops out.
     */
    @Test
    void anElementGateDoesNotChangeTheRoll() {
        RoomScheme gatedWall = new RoomScheme("bordered", 50, 0, 0,
                Optional.empty(), Optional.empty(),
                Optional.of(FloorPatternEntry.PLAIN),
                Optional.of(crown(from(9))), Optional.empty(), Optional.empty());
        List<RoomScheme> schemes = List.of(scheme("plain", 1), gatedWall);

        RandomSource random = RandomSource.create(11);
        int bordered = 0;
        for (int i = 0; i < 400; i++) {
            // A 5-high room: the crown is gated out, but the scheme still competes at weight 50.
            if (RoomSchemeSelector.select(schemes, 9, 9, 5, ENTRANCE_FLOOR, random).name().equals("bordered")) {
                bordered++;
            }
        }
        assertTrue(bordered > 350, "expected ~50/51 of rolls, got " + bordered + " of 400");
    }

    /** An ungated slot draws everywhere, which is what every existing scheme decodes to. */
    @Test
    void anUngatedElementDrawsInEveryRoom() {
        RoomScheme scheme = new RoomScheme("trim", 1, 0, 0,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(crown(SizeGate.UNBOUNDED)), Optional.empty(), Optional.empty());

        assertTrue(scheme.wallFor(5, 5, 5).isPresent());
        assertTrue(scheme.wallFor(17, 17, 10).isPresent());
    }

    /**
     * The hazard gates introduce: every slot can drop out at once, so a room wins a decorated scheme
     * and renders bare. The incidence numbers would look healthy while the dungeon looked empty.
     */
    @Test
    void aSchemeCanReportWhetherItDrawsAnythingAtAll() {
        RoomScheme scheme = new RoomScheme("all_gated", 1, 0, 0,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of(crown(from(9))), Optional.empty(), Optional.empty());

        assertFalse(scheme.drawsAnything(9, 9, 5), "every slot gated out -- this room renders bare");
        assertTrue(scheme.drawsAnything(9, 9, 9));
    }

    /**
     * Gate overlap, which is what lets the shipped-content rules stay precise. A rule like "pots and
     * a floor-level projecting course must not coexist" only actually fires when the two slots can
     * be drawn in the same room; gated to disjoint bands, they never meet.
     */
    @Test
    void gatesOverlapOnlyWhenSomeRoomSatisfiesBoth() {
        SizeGate shortRooms = new SizeGate(0, 0, Optional.of(6), Optional.empty());
        SizeGate tallRooms = from(7);

        assertFalse(shortRooms.overlaps(tallRooms), "maxHeight 6 and minHeight 7 share no room");
        assertFalse(tallRooms.overlaps(shortRooms), "and it is symmetric");
        assertTrue(shortRooms.overlaps(from(5)), "height 5-6 is in both");
        assertTrue(SizeGate.UNBOUNDED.overlaps(tallRooms), "unbounded meets everything");

        // Disjoint on either axis is enough to keep them apart.
        SizeGate narrow = new SizeGate(0, 0, Optional.empty(), Optional.of(7));
        SizeGate wide = new SizeGate(0, 9, Optional.empty(), Optional.empty());
        assertFalse(narrow.overlaps(wide));
    }

    /** An inverted range is rejected at load rather than silently fitting nothing. */
    @Test
    void anInvertedElementGateIsALoadError() {
        String json = "{\"name\": \"broken\", \"wall\": {"
                + "\"minHeight\": 7, \"maxHeight\": 5, \"patterns\": [{\"type\": \"courses\", "
                + "\"courses\": [{\"block\": \"minecraft:polished_andesite\"}]}]}}";
        var result = RoomScheme.CODEC.parse(JsonOps.INSTANCE,
                new com.google.gson.Gson().fromJson(json, com.google.gson.JsonElement.class));
        assertTrue(result.error().isPresent(), "maxHeight below minHeight should fail to decode");
        assertTrue(result.error().get().message().contains("wall"),
                "the error should name the slot it came from, got: " + result.error().get().message());
    }

    // ---------- the depth axis ----------

    private static RoomScheme onFloors(String name, int weight, int minFloorIndex,
                                       Optional<Integer> maxFloorIndex) {
        return new RoomScheme(name, weight, 0, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                new FloorRange(minFloorIndex, maxFloorIndex), Optional.empty(), false);
    }

    private static String rollOnFloor(List<RoomScheme> schemes, int floorIndex) {
        return RoomSchemeSelector.select(schemes, 15, 15, 10, floorIndex,
                RandomSource.create(7L)).name();
    }

    /** A scheme gated deep must not appear near the entrance, and vice versa. */
    @Test
    void aSchemeIsOnlyRolledOnTheFloorsItAllows() {
        List<RoomScheme> schemes = List.of(
                onFloors("shallow", 1, 0, Optional.of(1)),
                onFloors("deep", 1, 2, Optional.empty()));

        assertEquals("shallow", rollOnFloor(schemes, 0));
        assertEquals("shallow", rollOnFloor(schemes, 1));
        assertEquals("deep", rollOnFloor(schemes, 2));
        assertEquals("deep", rollOnFloor(schemes, 9));
    }

    /**
     * The gate filters BEFORE weights are totalled, exactly as the size gates do. An out-of-range
     * scheme's weight must not sit in the denominator -- otherwise a heavy deep scheme would
     * quietly suppress the shallow ones near the entrance without ever being rolled itself.
     */
    @Test
    void anOutOfRangeSchemesWeightNeverEntersTheDenominator() {
        List<RoomScheme> schemes = List.of(
                onFloors("shallow", 1, 0, Optional.of(0)),
                onFloors("mighty", 999, 3, Optional.empty()));

        for (long seed = 0; seed < 50; seed++) {
            assertEquals("shallow", RoomSchemeSelector.select(schemes, 15, 15, 10, 0,
                    RandomSource.create(seed)).name(),
                    "the deep scheme's weight leaked into floor 0's roll at seed " + seed);
        }
    }

    /** A floor no scheme allows degrades to the undecorated room, like every other empty set. */
    @Test
    void aFloorNoSchemeAllowsDegradesToPlain() {
        List<RoomScheme> schemes = List.of(onFloors("deep", 1, 5, Optional.empty()));
        assertEquals(RoomScheme.PLAIN, RoomSchemeSelector.select(schemes, 15, 15, 10, 0,
                RandomSource.create(1L)));
    }

    /**
     * {@code maxFloorIndex: 0} means the entrance floor only -- a real thing to author, and the
     * reason that bound accepts 0 where maxHeight/maxSize start at 1.
     */
    @Test
    void anEntranceOnlySchemeIsExpressible() {
        RoomScheme entranceOnly = onFloors("lobby", 1, 0, Optional.of(0));
        assertTrue(entranceOnly.fitsFloor(0));
        assertFalse(entranceOnly.fitsFloor(1));
    }

    /** Depth and size are independent: passing one does not excuse failing the other. */
    @Test
    void depthAndSizeBothHaveToPass() {
        RoomScheme deepAndLarge = new RoomScheme("grand_vault", 1, 0, 11,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), new FloorRange(3, Optional.empty()), Optional.empty(), false);

        assertTrue(deepAndLarge.fits(15, 15, 10, 3), "deep enough and big enough");
        assertFalse(deepAndLarge.fits(15, 15, 10, 1), "big enough but too shallow");
        assertFalse(deepAndLarge.fits(7, 7, 6, 3), "deep enough but too small");
    }
}
