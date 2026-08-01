package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.FloorPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
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

    /** A scheme with no dimensional constraints. */
    private static RoomScheme scheme(String name, int weight) {
        return new RoomScheme(name, weight, 0, 0, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static RoomScheme gated(String name, int weight, int minHeight, int minSize) {
        return new RoomScheme(name, weight, minHeight, minSize, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** A roomy room: nothing is gated out of a 15x15x10. */
    private static RoomScheme select(List<RoomScheme> schemes, RandomSource random) {
        return RoomSchemeSelector.select(schemes, 15, 15, 10, random);
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
            assertEquals("plain", RoomSchemeSelector.select(schemes, 15, 15, 5, random).name());
        }
    }

    @Test
    void aSchemeWiderThanTheRoomIsNeverRolled() {
        // minSize gates on the SMALLER axis: a 20x5 corridor-shaped room is as unsuitable for a
        // centred radial pattern as a 5x5 one, however long it is.
        List<RoomScheme> schemes = List.of(scheme("plain", 1), gated("spokes", 50, 0, 7));
        RandomSource random = RandomSource.create(7);
        for (int i = 0; i < 200; i++) {
            assertEquals("plain", RoomSchemeSelector.select(schemes, 20, 5, 10, random).name());
        }
    }

    @Test
    void aRoomMatchingNoSchemeDegradesToPlainRatherThanForcingOne() {
        List<RoomScheme> schemes = List.of(gated("grand", 1, 10, 12), gated("vaulted", 1, 9, 0));
        assertEquals(RoomScheme.PLAIN, RoomSchemeSelector.select(schemes, 5, 5, 5, RandomSource.create(1)));
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
            String name = RoomSchemeSelector.select(schemes, 15, 15, 6, random).name();
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
        FloorPatternEntry border = new FloorPatternEntry("border", 1, 2);
        RoomScheme only = new RoomScheme("bordered", 1, 0, 0, Optional.of(border), Optional.empty(), Optional.empty());
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
}
