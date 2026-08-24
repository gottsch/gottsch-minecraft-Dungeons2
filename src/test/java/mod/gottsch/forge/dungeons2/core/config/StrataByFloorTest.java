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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #45's depth axis for the dungeon's <strong>shell</strong>: {@code strataByFloorIndex}
 * bands, and {@link MotifConfig#forFloor}'s overlay of them.
 *
 * <p>Three properties carry the weight, and each exists to make a specific silent failure
 * impossible:</p>
 * <ul>
 *   <li><strong>A motif with no strata returns itself.</strong> Nothing ships a stratum, so this is
 *       what guarantees every dungeon in every existing world still renders byte-identically. It is
 *       asserted by <em>identity</em>, not equality &mdash; an equal-but-new config would still pass
 *       an equality check while proving nothing about the fast path being taken.</li>
 *   <li><strong>Undeclared sections fall through to the motif.</strong> The overlay is the whole
 *       design; a band that had to restate all five sections would drift from the base on the ones
 *       it did not mean to change.</li>
 *   <li><strong>A projection cannot be projected again.</strong> Re-projecting floor 0's config onto
 *       floor 3 would resolve floor 3's undeclared sections against floor 0's <em>banded</em> ones,
 *       silently. The projection carries an empty table so the second call is a no-op.</li>
 * </ul>
 */
class StrataByFloorTest {

    private static final WallConfig COBBLE = new WallConfig("minecraft:cobblestone");
    private static final WallConfig CHISELED = new WallConfig("minecraft:chiseled_stone_bricks");
    private static final CeilingConfig MOSSY = new CeilingConfig("minecraft:mossy_cobblestone");
    private static final CorridorConfig REPAINTED = new CorridorConfig(
            "minecraft:cobblestone", "minecraft:gravel", "minecraft:cobblestone",
            CorridorConfig.DEFAULT.height(), CorridorConfig.DEFAULT.profile(),
            Optional.empty(), Optional.empty(), List.of(), List.of());

    private static final CorridorStyle VAULTED = new CorridorStyle("vaulted", 3, 7,
            CorridorConfig.Profile.ARCHED, Optional.of("minecraft:stone_brick_stairs"),
            Optional.of(6), List.of());

    private static Stratum corridorBand(int minFloorIndex, CorridorConfig corridor) {
        return new Stratum(minFloorIndex, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(corridor), Optional.empty());
    }

    private static Stratum stratum(int minFloorIndex, WallConfig wall) {
        return new Stratum(minFloorIndex, Optional.empty(), Optional.of(wall), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /** A motif whose base sections are all the built-in defaults, carrying {@code table}. */
    private static MotifConfig motif(List<Stratum> table) {
        return motif(CorridorConfig.DEFAULT, table);
    }

    private static MotifConfig motif(CorridorConfig corridor, List<Stratum> table) {
        return new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT, DoorConfig.DEFAULT,
                corridor, FloorConfig.DEFAULT, List.of(RoomScheme.PLAIN),
                List.of(), List.of(), Map.of(), table);
    }

    // ---------- band selection ----------

    @Test
    void aFloorTakesTheDeepestBandThatHasStarted() {
        List<Stratum> table = List.of(stratum(0, COBBLE), stratum(3, CHISELED));
        assertEquals(COBBLE, motif(table).forFloor(0).wall());
        assertEquals(COBBLE, motif(table).forFloor(1).wall());
        assertEquals(COBBLE, motif(table).forFloor(2).wall());
        assertEquals(CHISELED, motif(table).forFloor(3).wall());
        assertEquals(CHISELED, motif(table).forFloor(4).wall());
    }

    /** Open-ended downward: the deepest band runs forever, so no floor is ever unanswered. */
    @Test
    void theDeepestBandRunsForever() {
        List<Stratum> table = List.of(stratum(0, COBBLE), stratum(2, CHISELED));
        for (int floor = 2; floor < 64; floor++) {
            assertEquals(CHISELED, motif(table).forFloor(floor).wall(), "floor " + floor);
        }
    }

    /** Declaration order is not selection order — the table is read for the deepest start, not the last entry. */
    @Test
    void theTableMayBeAuthoredOutOfOrder() {
        List<Stratum> table = List.of(stratum(3, CHISELED), stratum(0, COBBLE));
        assertEquals(COBBLE, motif(table).forFloor(1).wall());
        assertEquals(CHISELED, motif(table).forFloor(3).wall());
    }

    // ---------- the overlay ----------

    @Test
    void aSectionTheBandDoesNotDeclareFallsThroughToTheMotif() {
        Stratum wallOnly = new Stratum(0, Optional.empty(), Optional.of(COBBLE), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
        MotifConfig banded = motif(List.of(wallOnly)).forFloor(0);

        assertEquals(COBBLE, banded.wall());
        assertEquals(CeilingConfig.DEFAULT, banded.ceiling());
        assertEquals(DoorConfig.DEFAULT, banded.door());
        assertEquals(CorridorConfig.DEFAULT, banded.corridor());
        assertEquals(FloorConfig.DEFAULT, banded.floor());
    }

    @Test
    void everySectionCanBeBanded() {
        Stratum all = new Stratum(0, Optional.empty(), Optional.of(COBBLE), Optional.of(MOSSY),
                Optional.of(DoorConfig.DEFAULT), Optional.of(REPAINTED), Optional.of(FloorConfig.DEFAULT));
        MotifConfig banded = motif(List.of(all)).forFloor(0);
        assertEquals(COBBLE, banded.wall());
        assertEquals(MOSSY, banded.ceiling());
        assertEquals(REPAINTED, banded.corridor());
    }

    /** Strata move the shell only. Schemes, mob sets, loot and limits are other axes. */
    @Test
    void theProjectionCarriesEverythingElseUnchanged() {
        RoomScheme scheme = RoomScheme.PLAIN;
        MobSetBand mobs = new MobSetBand(0, List.of(new SpawnerConfig.MobSetEntry("dungeons2:x", 1)));
        Map<String, TemplateLimit> limits = Map.of("dungeons2:rooms/classic/a", new TemplateLimit(Optional.of(1), Optional.empty()));
        MotifConfig source = new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT,
                DoorConfig.DEFAULT, CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(scheme),
                List.of(mobs), List.of(), limits, List.of(stratum(0, COBBLE)));

        MotifConfig banded = source.forFloor(0);
        assertEquals(List.of(scheme), banded.schemes());
        assertEquals(List.of(mobs), banded.mobSetsByFloorIndex());
        assertEquals(limits, banded.templateLimits());
    }

    // ---------- the two guarantees that keep existing worlds intact ----------

    @Test
    void aMotifWithNoStrataReturnsItself() {
        MotifConfig plain = motif(List.of());
        for (int floor = 0; floor < 8; floor++) {
            assertSame(plain, plain.forFloor(floor), "floor " + floor);
        }
    }

    @Test
    void aProjectionCannotBeProjectedAgain() {
        List<Stratum> table = List.of(stratum(0, COBBLE), stratum(3, CHISELED));
        MotifConfig floor0 = motif(table).forFloor(0);

        assertNotSame(motif(table), floor0);
        assertTrue(floor0.strataByFloorIndex().isEmpty(),
                "a projection must not carry the table, or re-projecting it would resolve the"
                        + " second floor's undeclared sections against the first floor's banded ones");
        // The no-op that falls out of it: asking a projection for another floor changes nothing.
        assertSame(floor0, floor0.forFloor(3));
        assertEquals(COBBLE, floor0.forFloor(3).wall());
    }

    // ---------- table validation ----------

    @Test
    void anEmptyTableIsFine() {
        assertTrue(Stratum.validate(List.of()).result().isPresent());
    }

    @Test
    void aTableThatDoesNotCoverFloorZeroIsRejected() {
        DataResult<List<Stratum>> result = Stratum.validate(List.of(stratum(1, COBBLE)));
        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("floor 0"), result.error().get().message());
    }

    @Test
    void twoBandsStartingOnTheSameFloorAreRejected() {
        DataResult<List<Stratum>> result =
                Stratum.validate(List.of(stratum(0, COBBLE), stratum(0, CHISELED)));
        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("never be reached"),
                result.error().get().message());
    }

    // ---------- the codec ----------

    /**
     * A band with no sections is legal, and it is how you end the band above it.
     *
     * <p>Bands overlay the MOTIF, not each other, so {@code {"minFloorIndex": 1}} resolves to the
     * motif exactly as authored. Rejecting it used to force the author to restate a section they
     * did not want to change &mdash; the drift an overlay exists to prevent.</p>
     */
    @Test
    void aBandDeclaringNoSectionsIsTheMotifAsAuthored() {
        DataResult<Stratum> result = Stratum.CODEC.parse(JsonOps.INSTANCE, json("""
                { "minFloorIndex": 1 }"""));
        assertTrue(result.result().isPresent(), String.valueOf(result.error().orElse(null)));

        MotifConfig source = motif(List.of(stratum(0, COBBLE), result.result().get()));
        assertEquals(COBBLE, source.forFloor(0).wall());
        assertEquals(WallConfig.DEFAULT, source.forFloor(1).wall(),
                "floor 1 takes the motif's wall, NOT floor 0's band");
        assertEquals(CeilingConfig.DEFAULT, source.forFloor(1).ceiling());
    }

    /** The overlay's base is the motif. A band never inherits from the band above it. */
    @Test
    void aBandInheritsFromTheMotifNotFromTheBandAbove() {
        Stratum mud = new Stratum(0, Optional.empty(), Optional.of(COBBLE), Optional.of(MOSSY),
                Optional.empty(), Optional.empty(), Optional.empty());
        Stratum wallOnly = stratum(2, CHISELED);
        MotifConfig source = motif(List.of(mud, wallOnly));

        assertEquals(MOSSY, source.forFloor(0).ceiling());
        assertEquals(CeilingConfig.DEFAULT, source.forFloor(2).ceiling(),
                "band 2 says nothing about the ceiling, so it takes the MOTIF's -- not band 0's mossy");
    }

    /** #31's closed schema reaches this type too: a misspelled section is an error, not a no-op. */
    @Test
    void anUndeclaredKeyFailsToLoad() {
        DataResult<Stratum> result = Stratum.CODEC.parse(JsonOps.INSTANCE, json("""
                { "minFloorIndex": 0, "walls": { "wall": "minecraft:cobblestone" } }"""));
        assertTrue(result.error().isPresent());
    }

    @Test
    void minFloorIndexDefaultsToZero() {
        DataResult<Stratum> result = Stratum.CODEC.parse(JsonOps.INSTANCE, json("""
                { "wall": { "wall": "minecraft:cobblestone" } }"""));
        assertTrue(result.result().isPresent(), String.valueOf(result.error().orElse(null)));
        assertEquals(0, result.result().get().minFloorIndex());
    }

    @Test
    void aBandRoundTripsThroughTheCodec() {
        Stratum source = new Stratum(2, Optional.empty(), Optional.of(COBBLE), Optional.of(MOSSY), Optional.empty(),
                Optional.empty(), Optional.empty());
        DataResult<JsonElement> encoded = Stratum.CODEC.encodeStart(JsonOps.INSTANCE, source);
        assertTrue(encoded.result().isPresent(), String.valueOf(encoded.error().orElse(null)));
        assertEquals(source, Stratum.CODEC.parse(JsonOps.INSTANCE, encoded.result().get())
                .result().orElseThrow());
    }

    // ---------- the corridor band is simply used ----------

    /**
     * A band with no styles renders as a PLAIN corridor built from its own materials.
     *
     * <p>This is the whole corridor story. {@code BasicCorridorGenerator} calls
     * {@code styleFor(rolledName)}; with no styles that misses and returns {@code baseline()}, which
     * carries the band's own arch and courses. "No styles" means "no flourishes, build it from the
     * base elements" &mdash; exactly what an unstyled motif has always meant.</p>
     */
    @Test
    void aBandWithNoStylesRendersFromItsOwnBaseline() {
        CorridorConfig base = new CorridorConfig(
                "minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:stone_bricks",
                7, CorridorConfig.Profile.ARCHED, Optional.of("minecraft:stone_brick_stairs"),
                Optional.of(6), List.of(VAULTED), List.of());
        CorridorConfig mud = new CorridorConfig(
                "minecraft:packed_mud", "minecraft:mud_bricks", "minecraft:mud_bricks",
                7, CorridorConfig.Profile.ARCHED, Optional.of("minecraft:mud_brick_stairs"),
                Optional.of(6), List.of(), List.of());

        CorridorConfig projected = motif(base, List.of(corridorBand(0, mud))).forFloor(0).corridor();

        assertEquals(mud, projected, "the band's corridor is handed through untouched");
        assertEquals(projected.baseline(), projected.styleFor("vaulted"),
                "the rolled name misses, so the corridor builds from the band's baseline");
        assertEquals(Optional.of("minecraft:mud_brick_stairs"),
                projected.styleFor("vaulted").archBlock(),
                "which is what makes floor 0 mud all the way through, arch included");
    }

    /**
     * A band that DOES want flourishes declares styles under the same names. No new mechanism.
     *
     * <p>The planner rolled one of the motif's names for this floor, so a band entry under that
     * name is what {@code styleFor} finds. That is per-style banding, and it needs nothing built.</p>
     */
    @Test
    void aBandMayRestateAStyleUnderTheSameNameToRepaintIt() {
        CorridorConfig base = new CorridorConfig(
                "minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:stone_bricks",
                7, CorridorConfig.Profile.ARCHED, Optional.of("minecraft:stone_brick_stairs"),
                Optional.of(6), List.of(VAULTED), List.of());
        CorridorStyle mudVaulted = new CorridorStyle("vaulted", 3, 7, CorridorConfig.Profile.ARCHED,
                Optional.of("minecraft:mud_brick_stairs"), Optional.of(6), List.of());
        CorridorConfig mud = new CorridorConfig(
                "minecraft:packed_mud", "minecraft:mud_bricks", "minecraft:mud_bricks",
                7, CorridorConfig.Profile.ARCHED, Optional.of("minecraft:mud_brick_stairs"),
                Optional.of(6), List.of(mudVaulted), List.of());

        CorridorConfig projected = motif(base, List.of(corridorBand(0, mud))).forFloor(0).corridor();
        assertEquals(mudVaulted, projected.styleFor("vaulted"));
    }

    /** Nothing is rejected and nothing is reported: a corridor band is data, not a claim to check. */
    @Test
    void aCorridorBandIsNeverRejectedOrReported() {
        CorridorConfig anything = new CorridorConfig(
                "minecraft:packed_mud", "minecraft:mud_bricks", "minecraft:mud_bricks",
                5, CorridorConfig.Profile.FLAT, Optional.empty(), Optional.empty(),
                List.of(), List.of());
        List<String> problems = new ArrayList<>();
        MotifConfig resolved = MotifConfigFragment.resolve(
                List.of(fragmentWithStrata(List.of(corridorBand(0, anything)))), problems::add);

        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(anything, resolved.forFloor(0).corridor());
    }

    // ---------- fragment merge ----------

    /** Replaced wholesale, like the other depth tables: a progression is one coherent whole. */
    @Test
    void aLaterFragmentReplacesTheWholeTable() {
        MotifConfig resolved = MotifConfigFragment.resolve(List.of(
                fragmentWithStrata(List.of(stratum(0, COBBLE), stratum(3, CHISELED))),
                fragmentWithStrata(List.of(stratum(0, MOSSY_WALL)))));

        assertEquals(1, resolved.strataByFloorIndex().size(),
                "the second table must replace the first, not merge into it");
        assertEquals(MOSSY_WALL, resolved.forFloor(3).wall(),
                "floor 3's band came from the replaced table and must be gone");
    }

    @Test
    void aFragmentThatDoesNotMentionStrataLeavesThemAlone() {
        MotifConfig resolved = MotifConfigFragment.resolve(List.of(
                fragmentWithStrata(List.of(stratum(0, COBBLE))),
                fragmentWithStrata(null)));
        assertEquals(COBBLE, resolved.forFloor(0).wall());
    }

    @Test
    void aMotifAuthoringNoStrataResolvesToAnEmptyTable() {
        MotifConfig resolved = MotifConfigFragment.resolve(List.of(fragmentWithStrata(null)));
        assertTrue(resolved.strataByFloorIndex().isEmpty());
        assertSame(resolved, resolved.forFloor(4));
    }

    private static final WallConfig MOSSY_WALL = new WallConfig("minecraft:mossy_stone_bricks");

    /** {@code null} means the fragment does not mention the table at all. */
    private static MotifConfigFragment fragmentWithStrata(List<Stratum> table) {
        return new MotifConfigFragment(Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
                Map.of(), Optional.ofNullable(table));
    }

    private static JsonElement json(String raw) {
        return JsonParser.parseString(raw);
    }

    // ---------- step 3: the stratum's name, which is a pool path segment ----------

    private static Stratum named(int minFloorIndex, String name, WallConfig wall) {
        return new Stratum(minFloorIndex, Optional.ofNullable(name), Optional.of(wall),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void stratumNameForNamesTheCoveringBand() {
        MotifConfig config = motif(List.of(named(0, "patched", COBBLE), named(3, "ancient", CHISELED)));
        assertEquals(Optional.of("patched"), config.stratumNameFor(0));
        assertEquals(Optional.of("patched"), config.stratumNameFor(2));
        assertEquals(Optional.of("ancient"), config.stratumNameFor(3));
        assertEquals(Optional.of("ancient"), config.stratumNameFor(99));
    }

    /** An unnamed band draws from the motif's own rooms, which is every motif shipped today. */
    @Test
    void anUnnamedBandNamesNothing() {
        assertEquals(Optional.empty(), motif(List.of(stratum(0, COBBLE))).stratumNameFor(0));
        assertEquals(Optional.empty(), motif(List.of()).stratumNameFor(0));
    }

    /**
     * The name becomes a path segment, so a value that cannot be one is a load error rather than a
     * pool id that could never resolve — which would degrade to the motif's rooms and say nothing.
     */
    @Test
    void aNameThatIsNotAPathSegmentFailsToLoad() {
        for (String bad : List.of("Ancient", "deep rooms", "deep/rooms", "dépôt")) {
            DataResult<Stratum> result = Stratum.CODEC.parse(JsonOps.INSTANCE, json("""
                    { "minFloorIndex": 0, "name": "%s", "wall": { "wall": "minecraft:cobblestone" } }"""
                    .formatted(bad)));
            assertTrue(result.error().isPresent(), "'" + bad + "' should not be a usable name");
        }
    }

    @Test
    void anOrdinaryNameLoads() {
        for (String good : List.of("ancient", "recently_patched", "tier-2", "v1.2")) {
            DataResult<Stratum> result = Stratum.CODEC.parse(JsonOps.INSTANCE, json("""
                    { "minFloorIndex": 0, "name": "%s", "wall": { "wall": "minecraft:cobblestone" } }"""
                    .formatted(good)));
            assertTrue(result.result().isPresent(),
                    "'" + good + "' should load: " + result.error().orElse(null));
            assertEquals(Optional.of(good), result.result().get().name());
        }
    }

    /**
     * A band carrying only a name is legal: it moves that depth's room pools and leaves the shell
     * alone, which is a coherent thing to author.
     */
    @Test
    void aBandMayCarryOnlyAName() {
        DataResult<Stratum> result = Stratum.CODEC.parse(JsonOps.INSTANCE, json("""
                { "minFloorIndex": 0, "name": "ancient" }"""));
        assertTrue(result.result().isPresent(), String.valueOf(result.error().orElse(null)));

        MotifConfig source = motif(List.of(result.result().get()));
        assertEquals(Optional.of("ancient"), source.stratumNameFor(0));
        assertEquals(WallConfig.DEFAULT, source.forFloor(0).wall());
    }

    /** Guards the guard: the fixtures really are different, so the assertions above can fail. */
    @Test
    void theFixturesAreDistinguishable() {
        assertFalse(COBBLE.equals(WallConfig.DEFAULT));
        assertFalse(CHISELED.equals(COBBLE));
        assertFalse(REPAINTED.equals(CorridorConfig.DEFAULT));
    }
}
