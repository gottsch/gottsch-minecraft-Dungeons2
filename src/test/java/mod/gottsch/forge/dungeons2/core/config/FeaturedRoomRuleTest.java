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

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A big room may not come out bare (Mark, 2026-10-30): one at least {@code featured_room_min_size}
 * on its shorter side must draw a ceiling, pillars, platforms, a pit or a partition. See
 * {@link RoomSlots#resolve(int, int, int, RandomSource, int)}.
 *
 * <p>The rule is only worth having if it changes nothing it does not have to, so half of these
 * prove what it leaves alone: small rooms, and big rooms whose own roll already dressed them, come
 * out byte-identical with the same random stream behind them.</p>
 *
 * @author Mark Gottschling on 10/30/2026
 */
class FeaturedRoomRuleTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int MIN = DungeonGenerationConfig.DEFAULT_FEATURED_ROOM_MIN_SIZE;

    private static final String VAULT = "{\"weight\": 1, \"patterns\": [{\"type\": \"dungeons2:vaulted\","
            + " \"config\": {\"block\": \"minecraft:stone_bricks\"}}]}";
    private static final String LOW_JOISTS = "{\"weight\": 1, \"min_height\": 7, \"patterns\": [{"
            + "\"type\": \"dungeons2:joists\", \"projection\": 1, \"config\": {\"block\": \"minecraft:spruce_log\"}}]}";
    private static final String INSET_PIT = "{\"weight\": 1, \"type\": \"dungeons2:inset\","
            + " \"config\": {\"inset\": 1, \"depth\": 2}}";
    private static final String FALSE_FLOOR_PIT = "{\"weight\": 1, \"type\": \"dungeons2:false_floor\","
            + " \"config\": {\"width\": 3, \"depth\": 4}}";
    private static final String CHANDELIER = "{\"placement\": \"centre\", \"min_count\": 1, \"max_count\": 1,"
            + " \"variants\": [{\"block\": \"dungeonblocks:chandelier\"}]}";

    private static RoomScheme scheme(String slots) {
        DataResult<RoomScheme> result = RoomScheme.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"name\": \"s\", " + slots + "}"));
        return result.result().orElseThrow(() -> new AssertionError(
                "expected this scheme to decode: " + result.error().orElseThrow().message()));
    }

    /** A ceiling that is almost always {@code none}: the authored roll leaves nearly every room bare. */
    private static final String MOSTLY_BARE_CEILING =
            "\"ceiling\": [{\"weight\": 1000, \"none\": true}, " + VAULT + "]";

    /** SplitMix64's finalizer; see RisingVaultTest#mix for why seeds are mixed. */
    private static long mix(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Test
    void aBigRoomTheRollLeftBareGetsACeiling() {
        RoomScheme scheme = scheme(MOSTLY_BARE_CEILING);
        for (long seed = 0; seed < 50; seed++) {
            RoomScheme plain = scheme.resolve(17, 17, 5, RandomSource.create(mix(seed)));
            RoomScheme ruled = scheme.resolve(17, 17, 5, RandomSource.create(mix(seed)), MIN);
            assertTrue(ruled.ceilingFor(17, 17, 5).isPresent(), "seed " + seed + ": still bare");
            if (plain.ceilingFor(17, 17, 5).isEmpty()) {
                assertTrue(ruled.slots().shapesRoom(), "seed " + seed);
            }
        }
    }

    @Test
    void theThresholdIsTheShorterSideAndInclusive() {
        RoomScheme scheme = scheme(MOSTLY_BARE_CEILING);
        int bareJustUnder = 0;
        for (long seed = 0; seed < 50; seed++) {
            assertTrue(scheme.resolve(MIN, 30, 5, RandomSource.create(mix(seed)), MIN)
                    .ceilingFor(MIN, 30, 5).isPresent(), "a room exactly at the threshold is big");
            if (scheme.resolve(MIN - 1, 30, 5, RandomSource.create(mix(seed)), MIN)
                    .ceilingFor(MIN - 1, 30, 5).isEmpty()) {
                bareJustUnder++;
            }
        }
        // long, but its shorter side is one under the threshold: it may be bare, and mostly is
        assertTrue(bareJustUnder > 40, "a room one under the threshold should keep its authored roll");
    }

    /** Small rooms are untouched: same slots, and the same random stream left behind them. */
    @Test
    void aSmallRoomIsExactlyWhatItsSchemeRolled() {
        RoomScheme scheme = scheme(MOSTLY_BARE_CEILING);
        for (long seed = 0; seed < 50; seed++) {
            RandomSource a = RandomSource.create(mix(seed));
            RandomSource b = RandomSource.create(mix(seed));
            assertEquals(scheme.resolve(9, 9, 6, a).slots(), scheme.resolve(9, 9, 6, b, MIN).slots());
            assertEquals(a.nextLong(), b.nextLong(), "seed " + seed + ": the rule drew from a small room");
        }
    }

    /** A big room its own roll already dressed is untouched too, whichever shape slot did it. */
    @Test
    void aBigRoomTheRollAlreadyDressedIsExactlyWhatItRolled() {
        // one option, so the pit always draws and its slot consumes nothing from the stream
        RoomScheme scheme = scheme(MOSTLY_BARE_CEILING + ", \"pit\": [" + INSET_PIT + "]");
        for (long seed = 0; seed < 50; seed++) {
            RandomSource a = RandomSource.create(mix(seed));
            RandomSource b = RandomSource.create(mix(seed));
            RoomScheme plain = scheme.resolve(17, 17, 5, a);
            assertTrue(plain.pitFor(17, 17, 5).isPresent(), "the pit is a single option, always drawn");
            assertEquals(plain.slots(), scheme.resolve(17, 17, 5, b, MIN).slots());
            assertEquals(a.nextLong(), b.nextLong(), "seed " + seed + ": the rule drew from a dressed room");
        }
    }

    /**
     * The mud band's {@code mud_works} shape: its only ceilings need 7 rows, and a big room is
     * lower than that, so the rule falls through to the next shape slot that can draw.
     */
    @Test
    void withNoCeilingThatFitsTheRuleFallsThroughToTheNextShapeSlot() {
        RoomScheme scheme = scheme("\"ceiling\": [{\"weight\": 1, \"none\": true}, " + LOW_JOISTS + "],"
                + " \"pit\": [{\"weight\": 1000, \"none\": true}, " + INSET_PIT + "]");
        for (long seed = 0; seed < 50; seed++) {
            RoomScheme ruled = scheme.resolve(17, 17, 5, RandomSource.create(mix(seed)), MIN);
            assertFalse(ruled.ceilingFor(17, 17, 5).isPresent(), "a 7-row ceiling in a 5-row room");
            assertTrue(ruled.pitFor(17, 17, 5).isPresent(), "seed " + seed + ": still bare");
        }
    }

    /** A chandelier in an empty box is still an empty box. */
    @Test
    void hangingsDoNotCountAsDressingTheRoom() {
        RoomScheme scheme = scheme(MOSTLY_BARE_CEILING + ", \"hangings\": " + CHANDELIER);
        for (long seed = 0; seed < 50; seed++) {
            RoomScheme ruled = scheme.resolve(17, 17, 5, RandomSource.create(mix(seed)), MIN);
            assertTrue(ruled.hangingsFor(17, 17, 5).isPresent());
            assertTrue(ruled.ceilingFor(17, 17, 5).isPresent(), "seed " + seed + ": the chandelier counted");
        }
    }

    /**
     * The room this rule first failed in (Mark, 2026-10-30): a big chamber with no ceiling over a
     * FALSE FLOOR. The pit drew, so the room counted as dressed, and it looked exactly as bare as
     * one with nothing in it. A hidden pit does not count, and is never the one forced.
     */
    @Test
    void aHiddenPitDoesNotCountAsDressingTheRoom() {
        RoomScheme scheme = scheme(MOSTLY_BARE_CEILING + ", \"pit\": [" + FALSE_FLOOR_PIT + "]");
        for (long seed = 0; seed < 50; seed++) {
            RoomScheme ruled = scheme.resolve(17, 17, 5, RandomSource.create(mix(seed)), MIN);
            assertTrue(ruled.pitFor(17, 17, 5).isPresent(), "the trap is kept");
            assertTrue(ruled.ceilingFor(17, 17, 5).isPresent(), "seed " + seed + ": bare over a false floor");
        }
        RoomScheme onlyHidden = scheme("\"ceiling\": [{\"weight\": 1, \"none\": true}, " + LOW_JOISTS + "],"
                + " \"pit\": [{\"weight\": 1, \"none\": true}, " + FALSE_FLOOR_PIT + "]");
        assertFalse(onlyHidden.slots().canShape(17, 17, 5), "a false floor is nothing to force");
    }

    /**
     * The rule's second half, in the generator: a slot the roll drew can still BUILD nothing, and a
     * big room that built nothing gets a ceiling after the fact. Here the pit drew -- so the roll
     * looked dressed -- but the floor has no sink budget, so nothing was dug.
     */
    @Test
    void aBigRoomWhoseDrawnSlotBuiltNothingGetsACeilingAnyway() {
        RoomScheme scheme = scheme(MOSTLY_BARE_CEILING + ", \"pit\": [" + INSET_PIT + "]");
        MotifConfig config = new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT,
                DoorConfig.DEFAULT, CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(scheme));
        RoomData room = new RoomData(1, 0, 0, 17, 17, 5, RoomRole.NORMAL);
        int floorY = 60;
        int ceilingY = floorY + room.getHeight() - 1;
        for (long seed = 0; seed < 10; seed++) {
            // sink offset 0 (the generator's default): the pit is drawn and cannot be dug
            assertTrue(rises(new BasicRoomGenerator().withMotifConfig(config).withCeilingBudget(15)
                            .withFeaturedRoomMinSize(MIN), room, floorY, ceilingY, mix(seed)),
                    "seed " + seed + ": the room built nothing and got no ceiling");
            assertFalse(rises(new BasicRoomGenerator().withMotifConfig(config).withCeilingBudget(15),
                            room, floorY, ceilingY, mix(seed)),
                    "seed " + seed + ": without the rule this room should have stayed bare");
        }
    }

    /** Whether the built room has a vault: anything solid above its ceiling plane. */
    private static boolean rises(BasicRoomGenerator generator, RoomData room, int floorY, int ceilingY,
                                 long seed) {
        RoomPlacements out = new RoomPlacements();
        generator.build(room, floorY, 1, DungeonMotif.CLASSIC, RandomSource.create(seed), out);
        for (BlockPlacement bp : out.getBlocks()) {
            if (bp.getY() > ceilingY && !"minecraft:air".equals(bp.getBlockId())) {
                return true;
            }
        }
        return false;
    }

    /** Nothing to force: the room stays bare rather than inventing a treatment nobody authored. */
    @Test
    void aSchemeWithNoShapeTreatmentAtAllIsLeftAlone() {
        RoomScheme scheme = scheme("\"ceiling\": [{\"weight\": 1, \"none\": true}, " + LOW_JOISTS + "]");
        RoomScheme ruled = scheme.resolve(17, 17, 5, RandomSource.create(1L), MIN);
        assertFalse(ruled.slots().shapesRoom());
        assertFalse(scheme.slots().canShape(17, 17, 5));
    }

    /** The scheme roll itself is untouched; the rule only changes what the winner puts in the room. */
    @Test
    void theSelectorPassesTheThresholdThrough() {
        List<RoomScheme> schemes = List.of(scheme(MOSTLY_BARE_CEILING));
        for (long seed = 0; seed < 50; seed++) {
            RoomScheme rolled = RoomSchemeSelector.select(schemes, 17, 17, 5, 1, -1,
                    RoomSchemeSelector.SecretEligibility.NONE, MIN, RandomSource.create(mix(seed)));
            assertTrue(rolled.ceilingFor(17, 17, 5).isPresent(), "seed " + seed);
        }
    }

    /**
     * The rule can only force what a scheme authored, and it tries the ceiling first because that is
     * what a big room wants: so every shipped scheme a big room can roll must have a CEILING that
     * fits it, at every height the taper gives a big room. Stricter than the rule needs (any shape
     * slot would do), on purpose -- {@code mud_works} once had only 7-row joists, so its big low
     * rooms fell through to a pit; it now has a low barrel (2026-10-30), and this keeps it so.
     */
    @Test
    void everyShippedSchemeABigRoomCanRollHasACeilingThatFits() {
        MotifConfig classic = MotifConfigs.load("classic");
        List<String> bare = new ArrayList<>();
        for (int floor = 0; floor <= 6; floor++) {
            for (RoomScheme scheme : classic.forFloor(floor).schemes()) {
                if (scheme.isSecret() || !scheme.fitsFloor(floor)) {
                    continue;
                }
                // the taper gives a room 11 or more across 5 to 9 rows; 10 covers a band retuned
                // upward by a row without this test silently skipping it
                for (int size = MIN; size <= 25; size++) {
                    for (int height = 5; height <= 10; height++) {
                        int s = size;
                        int h = height;
                        if (scheme.fits(size, size, height) && scheme.slots().ceiling().all()
                                .noneMatch(entry -> entry.gate().fits(s, s, h))) {
                            bare.add("floor " + floor + " " + scheme.name() + " " + size + "x" + size
                                    + " h" + height);
                        }
                    }
                }
            }
        }
        assertTrue(bare.isEmpty(), "big rooms these schemes can roll have no ceiling to force: " + bare);
    }

    @Test
    void theThresholdDecodesDefaultsAndIsBounded() {
        assertEquals(MIN, parseConfig("{}").result().orElseThrow().featuredRoomMinSize());
        assertEquals(17, parseConfig("{\"featured_room_min_size\": 17}").result().orElseThrow()
                .featuredRoomMinSize());
        assertTrue(parseConfig("{\"featured_room_min_size\": 4}").result().isEmpty(),
                "below the smallest room the bands make, the rule would force treatments that cannot fit");
    }

    private static DataResult<DungeonGenerationConfig> parseConfig(String json) {
        return DungeonGenerationConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
