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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stratum's own {@code schemes}: what a depth is <em>dressed</em> with, not just what its shell is
 * made of.
 *
 * <p>The gap this closes (Gottsch, 2026-08-27). {@link Stratum} could repaint five element
 * sections but not touch the roll, so every depth drew from one scheme list. The workaround was a
 * band-level {@code pattern} on a surface section &mdash; which is <strong>tier 2</strong>, drawing
 * in every room whose rolled scheme names no slot of its own, so it became the depth's default look
 * rather than an accent (the mud band's joists measured 55.9%). Naming the depth's rooms is the fix
 * the workaround was standing in for.</p>
 *
 * <p>Two behaviours carry the weight here, and they live in different places on purpose:</p>
 * <ul>
 *   <li><strong>Merge by name, in {@link MotifConfig#forFloor}.</strong> The one Stratum section
 *       that does not replace whole &mdash; {@code MotifConfigFragment}'s own rule of thumb is
 *       <em>coherent whole &rarr; replace, independent entries &rarr; merge by key</em>, and schemes
 *       are its example of independent entries. Put here rather than in {@code resolve} so it holds
 *       for a config built by hand as well as one folded from a datapack.</li>
 *   <li><strong>{@code extends}/{@code abstract}, in {@link MotifConfigFragment#resolve}.</strong>
 *       A parent is addressed by name across the whole motif, so it is only resolvable once every
 *       fragment has folded &mdash; and skipping the pass fails silently in both directions: an
 *       abstract band scheme would roll as a real room, and an {@code extends} would render with
 *       half its content missing.</li>
 * </ul>
 *
 * <p>Pure codec and folding work, like {@link SchemeInheritanceTest} &mdash; no bootstrap.</p>
 */
class PerStratumSchemesTest {

    private static final Gson GSON = new Gson();

    private static final RoomScheme PLAIN = new RoomScheme("plain", 1, 0, 0);
    private static final RoomScheme GRAND = new RoomScheme("grand", 3, 7, 9);

    private static Stratum band(int minFloorIndex, RoomScheme... schemes) {
        return new Stratum(minFloorIndex, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(List.of(schemes)));
    }

    /** A motif dressing rooms with {@link #PLAIN} and {@link #GRAND}, carrying {@code table}. */
    private static MotifConfig motif(List<Stratum> table) {
        return new MotifConfig(WallConfig.DEFAULT, CeilingConfig.DEFAULT, DoorConfig.DEFAULT,
                CorridorConfig.DEFAULT, FloorConfig.DEFAULT, List.of(PLAIN, GRAND),
                List.of(), List.of(), Map.of(), table);
    }

    private static List<String> names(List<RoomScheme> schemes) {
        return schemes.stream().map(RoomScheme::name).toList();
    }

    private static RoomScheme byName(List<RoomScheme> schemes, String name) {
        return schemes.stream().filter(s -> name.equals(s.name())).findFirst()
                .orElseThrow(() -> new AssertionError("no scheme '" + name + "' in " + names(schemes)));
    }

    // ---------- the merge ----------

    @Test
    void aBandEntryReplacesTheMotifSchemeOfTheSameName() {
        List<RoomScheme> banded = motif(List.of(band(0, new RoomScheme("plain", 9, 0, 0))))
                .forFloor(0).schemes();

        assertEquals(9, byName(banded, "plain").weight(), "the band's entry wins its name outright");
        assertEquals(3, byName(banded, "grand").weight(), "and leaves every other entry alone");
    }

    @Test
    void aBandEntryWithANewNameIsAppended() {
        List<RoomScheme> banded = motif(List.of(band(0, new RoomScheme("mud_hall", 5, 0, 0))))
                .forFloor(0).schemes();

        assertEquals(List.of("plain", "grand", "mud_hall"), names(banded));
    }

    /**
     * Overriding keeps the entry's POSITION, the same half of the rule
     * {@code MotifConfigFragment.resolve} follows &mdash; so a band retuning one scheme cannot
     * quietly reorder the rest, and a weighted roll over the list stays reproducible.
     */
    @Test
    void overridingAnEntryKeepsItsPosition() {
        List<RoomScheme> banded = motif(List.of(band(0,
                new RoomScheme("grand", 9, 7, 9), new RoomScheme("plain", 9, 0, 0))))
                .forFloor(0).schemes();

        assertEquals(List.of("plain", "grand"), names(banded),
                "the band declared grand first, but plain was already first in the motif");
    }

    /**
     * The list itself, not a copy. A band that repaints the shell and says nothing about dressing
     * is the ordinary case &mdash; every stratum shipped today &mdash; and it must not churn.
     */
    @Test
    void aBandThatDeclaresNoSchemesRollsTheMotifs() {
        MotifConfig base = motif(List.of(new Stratum(0, Optional.empty(),
                Optional.of(new WallConfig("minecraft:cobblestone")), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty())));

        assertSame(base.schemes(), base.forFloor(0).schemes());
    }

    /**
     * {@code "schemes": []} is a NO-OP, not "this depth has no schemes". An empty list merges onto
     * the motif's and changes nothing; there is deliberately no way to subtract a scheme at a
     * depth, which is the same "a later file can only add to or replace something nameable" rule
     * the fragment merge is built on.
     */
    @Test
    void aDeclaredButEmptyListIsANoOp() {
        MotifConfig base = motif(List.of(band(0)));
        assertSame(base.schemes(), base.forFloor(0).schemes());
    }

    @Test
    void aBandOnlyDressesItsOwnDepth() {
        MotifConfig base = motif(List.of(band(0), band(2, new RoomScheme("deep_hall", 5, 0, 0))));

        assertEquals(List.of("plain", "grand"), names(base.forFloor(0).schemes()));
        assertEquals(List.of("plain", "grand"), names(base.forFloor(1).schemes()));
        assertEquals(List.of("plain", "grand", "deep_hall"), names(base.forFloor(2).schemes()));
        assertEquals(List.of("plain", "grand", "deep_hall"), names(base.forFloor(9).schemes()),
                "open-ended downward, like every other section");
    }

    /**
     * A projection carries an empty strata table, so the merge cannot happen twice &mdash; the same
     * guarantee {@link StrataByFloorTest} pins for the element sections, and it matters more here:
     * re-projecting would merge floor 0's band onto floor 3's already-banded list.
     */
    @Test
    void aProjectionCannotBeProjectedAgain() {
        MotifConfig floor0 = motif(List.of(band(0, new RoomScheme("shallow", 5, 0, 0)),
                band(3, new RoomScheme("deep", 5, 0, 0)))).forFloor(0);

        assertEquals(List.of("plain", "grand", "shallow"), names(floor0.forFloor(3).schemes()));
    }

    // ---------- extends / abstract ----------

    private static MotifConfig resolve(String json) {
        return resolve(new ArrayList<>(), json);
    }

    private static MotifConfig resolve(List<String> problems, String json) {
        MotifConfigFragment fragment = MotifConfigFragment.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class))
                .getOrThrow(false, message -> {
                    throw new AssertionError(message);
                });
        return MotifConfigFragment.resolve(List.of(fragment), problems::add);
    }

    private static final String WALL_SLOT = """
            "wall":{"patterns":[{"type":"dungeons2:courses","config":{"courses":[            {"block":"minecraft:polished_andesite","anchor":"top"}]}}]}""";

    private static final String GRAND_TEMPLATE =
            "{\"name\":\"grand\",\"abstract\":true,\"weight\":1," + WALL_SLOT + "}";

    /**
     * The authoring win that makes a band short: the band names one scheme and inherits the wall
     * course from a template declared at motif level, in another file entirely.
     */
    @Test
    void aBandSchemeMayExtendAnAbstractMotifTemplate() {
        MotifConfig resolved = resolve("""
                {"schemes":[%s,{"name":"plain","weight":1}],
                 "strata_by_floor_index":[{"min_floor_index":0,
                    "schemes":[{"name":"mud_hall","weight":5,"extends":"grand"}]}]}"""
                .formatted(GRAND_TEMPLATE));

        RoomScheme mudHall = byName(resolved.forFloor(0).schemes(), "mud_hall");
        assertTrue(mudHall.wall().isPresent(), "the wall course should come from the motif template");
        assertEquals(5, mudHall.weight(), "and the child keeps what it declared itself");
    }

    /**
     * The parent must be FINDABLE at motif level without starting to roll at this depth because a
     * band extended it. {@code grand} is abstract here so it never rolls anywhere; the assertion
     * that matters is that the band contributed exactly one entry rather than the whole inherited
     * list.
     */
    @Test
    void extendingAMotifTemplateDoesNotDragTheRestOfTheMotifIntoTheBand() {
        MotifConfig resolved = resolve("""
                {"schemes":[%s,{"name":"plain","weight":1},{"name":"cellar","weight":2}],
                 "strata_by_floor_index":[{"min_floor_index":0,
                    "schemes":[{"name":"mud_hall","weight":5,"extends":"grand"}]}]}"""
                .formatted(GRAND_TEMPLATE));

        assertEquals(List.of("plain", "cellar", "mud_hall"), names(resolved.forFloor(0).schemes()));
    }

    /** An abstract band scheme is a template, never a room &mdash; the same rule as at motif level. */
    @Test
    void anAbstractBandSchemeIsNeverRolled() {
        MotifConfig resolved = resolve("""
                {"schemes":[{"name":"plain","weight":1}],
                 "strata_by_floor_index":[{"min_floor_index":0,"schemes":[
                    {"name":"mud_base","abstract":true,"weight":1,
                     "pots":{"min_count":1,"max_count":2,"loot_table":"dungeons2:pots/classic",
                             "variants":[{"entity":"dungeonblocks:pot","weight":1}]}},
                    {"name":"mud_hall","weight":5,"extends":"mud_base"}]}]}""");

        assertEquals(List.of("plain", "mud_hall"), names(resolved.forFloor(0).schemes()));
        assertTrue(byName(resolved.forFloor(0).schemes(), "mud_hall").pots().isPresent(),
                "a band's own template still inherits into its siblings");
    }

    /**
     * A band entry shadowing a motif name is the parent its OWN siblings see. Without the overlay
     * the sibling would inherit the motif's version of the parent, which is not the one at this
     * depth &mdash; a wrong answer with nothing to announce it. The two versions of {@code base}
     * fill different slots so the answer says which one was read.
     */
    @Test
    void aBandSchemeExtendsTheBandsOwnShadowOfAMotifName() {
        MotifConfig resolved = resolve("""
                {"schemes":[{"name":"base","abstract":true,"weight":1,
                             "pots":{"min_count":1,"max_count":2,"loot_table":"dungeons2:pots/classic",
                                     "variants":[{"entity":"dungeonblocks:pot","weight":1}]}},
                            {"name":"plain","weight":1}],
                 "strata_by_floor_index":[{"min_floor_index":0,"schemes":[
                    {"name":"base","abstract":true,"weight":1,%s},
                    {"name":"mud_hall","weight":5,"extends":"base"}]}]}""".formatted(WALL_SLOT));

        RoomScheme mudHall = byName(resolved.forFloor(0).schemes(), "mud_hall");
        assertTrue(mudHall.wall().isPresent(), "the band's shadow of 'base' is the parent");
        assertFalse(mudHall.pots().isPresent(), "so the motif's version of 'base' is not");
    }

    @Test
    void aBandSchemeExtendingAMissingParentIsDroppedAndNamesTheBand() {
        List<String> problems = new ArrayList<>();
        MotifConfig resolved = resolve(problems, """
                {"schemes":[{"name":"plain","weight":1}],
                 "strata_by_floor_index":[{"min_floor_index":0,"schemes":[
                    {"name":"mud_hall","weight":5,"extends":"nope"}]}]}""");

        assertEquals(List.of("plain"), names(resolved.forFloor(0).schemes()));
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).startsWith("stratum at floor 0:"), problems.get(0));
        assertTrue(problems.get(0).contains("mud_hall"), problems.get(0));
    }

    /**
     * A motif-level fault is reached by the motif's inheritance pass AND by every band's, since a
     * band resolves its parents against the same map. Saying it three times says nothing new.
     */
    @Test
    void aMotifLevelFaultIsReportedOnceNoMatterHowManyBandsThereAre() {
        List<String> problems = new ArrayList<>();
        resolve(problems, """
                {"schemes":[{"name":"plain","weight":1},{"name":"broken","weight":1,"extends":"nope"}],
                 "strata_by_floor_index":[
                    {"min_floor_index":0,"schemes":[{"name":"a","weight":1}]},
                    {"min_floor_index":2,"schemes":[{"name":"b","weight":1}]}]}""");

        assertEquals(1, problems.size(), problems.toString());
        assertFalse(problems.get(0).startsWith("stratum"),
                "the motif's own pass reaches it first, so it is reported unprefixed: " + problems.get(0));
    }

    // ---------- the schema ----------

    /** {@code schemes} is a declared key on a stratum now; the closed schema would reject it if not. */
    @Test
    void aStratumMayCarrySchemesBesideItsElementSections() {
        MotifConfig resolved = resolve("""
                {"schemes":[{"name":"plain","weight":1}],
                 "strata_by_floor_index":[{"min_floor_index":0,"name":"mud",
                    "wall":{"wall":"minecraft:mud_bricks"},
                    "schemes":[{"name":"mud_hall","weight":5}]}]}""");

        assertEquals("minecraft:mud_bricks", resolved.forFloor(0).wall().wall());
        assertEquals(List.of("plain", "mud_hall"), names(resolved.forFloor(0).schemes()));
    }
}
