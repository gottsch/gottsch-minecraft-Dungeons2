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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code template_limits} &mdash; backlog #44's declaration half: how many times one authored
 * template may be placed, and how two packs' limits compose.
 *
 * <p>The merge rule is the part worth pinning. It is deliberately the <strong>opposite</strong> of
 * the {@code mob_sets_by_floor_index} table sitting beside it: a depth curve is one coherent
 * progression and is replaced wholesale, while a limits map is a set of independent per-template
 * entries and merges by key. Get that backwards and an addon capping its own room silently wipes
 * every cap the base pack declared.</p>
 */
class TemplateLimitsTest {

    private static final Gson GSON = new Gson();
    private static final String MIGHTY = "dungeons2:rooms/classic/11x11/mighty_hall";
    private static final String SHRINE = "dungeons2:rooms/classic/9x9/shrine";

    private static MotifConfigFragment fragment(String json) {
        return MotifConfigFragment.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class))
                .result().orElseThrow(() -> new AssertionError("did not decode: " + json));
    }

    private static MotifConfig resolve(String... fragments) {
        List<MotifConfigFragment> parsed = java.util.Arrays.stream(fragments)
                .map(TemplateLimitsTest::fragment).toList();
        return MotifConfigFragment.resolve(parsed);
    }

    // ---------- the record ----------

    @Test
    void aTemplateWithNoEntryIsUnlimited() {
        assertTrue(resolve("{}").limitFor(MIGHTY).isEmpty());
    }

    @Test
    void bothBoundsDecodeAndAreReadable() {
        MotifConfig motif = resolve("""
                {"template_limits":{"%s":{"max_per_floor":1,"max_per_dungeon":2}}}""".formatted(MIGHTY));
        TemplateLimit limit = motif.limitFor(MIGHTY).orElseThrow();
        assertEquals(Optional.of(1), limit.maxPerFloor());
        assertEquals(Optional.of(2), limit.maxPerDungeon());
    }

    @Test
    void eitherBoundAloneIsEnough() {
        assertTrue(resolve("""
                {"template_limits":{"%s":{"max_per_dungeon":1}}}""".formatted(MIGHTY))
                .limitFor(MIGHTY).isPresent());
        assertTrue(resolve("""
                {"template_limits":{"%s":{"max_per_floor":1}}}""".formatted(MIGHTY))
                .limitFor(MIGHTY).isPresent());
    }

    /** An entry that caps nothing is an authoring mistake, not a quieter way of saying "unlimited". */
    @Test
    void anEntryWithNeitherBoundIsALoadError() {
        assertTrue(MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson("""
                        {"template_limits":{"%s":{}}}""".formatted(MIGHTY), JsonElement.class))
                .error().isPresent());
    }

    /** A stray key inside an entry fails the pack, like every other closed schema here. */
    @Test
    void aMisspelledBoundIsALoadError() {
        assertTrue(MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson("""
                        {"template_limits":{"%s":{"maxPerLevel":1}}}""".formatted(MIGHTY),
                        JsonElement.class))
                .error().isPresent());
    }

    // ---------- the counting rule ----------

    @Test
    void eachBoundStopsPlacementOnItsOwn() {
        TemplateLimit perFloor = new TemplateLimit(Optional.of(1), Optional.empty());
        assertTrue(perFloor.allows(0, 99), "nothing on this floor yet");
        assertFalse(perFloor.allows(1, 0), "one already on this floor");

        TemplateLimit perDungeon = new TemplateLimit(Optional.empty(), Optional.of(2));
        assertTrue(perDungeon.allows(99, 1));
        assertFalse(perDungeon.allows(0, 2));
    }

    /** Whichever binds first wins -- they compose rather than one overriding the other. */
    @Test
    void theTighterBoundIsTheOneThatBinds() {
        TemplateLimit both = new TemplateLimit(Optional.of(2), Optional.of(3));
        assertTrue(both.allows(1, 2));
        assertFalse(both.allows(2, 2), "the per-floor bound binds");
        assertFalse(both.allows(0, 3), "the per-dungeon bound binds");
    }

    /** {@code maxPerDungeon: 0} disables a template outright -- how a pack switches one off. */
    @Test
    void aZeroBoundMeansNeverPlaceIt() {
        MotifConfig motif = resolve("""
                {"template_limits":{"%s":{"max_per_dungeon":0}}}""".formatted(MIGHTY));
        assertFalse(motif.limitFor(MIGHTY).orElseThrow().allows(0, 0));
    }

    // ---------- the merge rule ----------

    /**
     * An addon capping its own room must not wipe the base pack's caps. This is the whole reason
     * the map merges by key instead of being replaced like the depth table.
     */
    @Test
    void aLaterFragmentAddsToTheMapRatherThanReplacingIt() {
        MotifConfig motif = resolve(
                """
                {"template_limits":{"%s":{"max_per_dungeon":1}}}""".formatted(MIGHTY),
                """
                {"template_limits":{"%s":{"max_per_floor":1}}}""".formatted(SHRINE));

        assertTrue(motif.limitFor(MIGHTY).isPresent(), "the first fragment's cap survived");
        assertTrue(motif.limitFor(SHRINE).isPresent(), "and the second fragment's was added");
    }

    /** Same key twice: the later fragment wins, so a pack can retune a base mod's cap. */
    @Test
    void aLaterFragmentReplacesAnEntryForTheSameTemplate() {
        MotifConfig motif = resolve(
                """
                {"template_limits":{"%s":{"max_per_dungeon":1}}}""".formatted(MIGHTY),
                """
                {"template_limits":{"%s":{"max_per_dungeon":4}}}""".formatted(MIGHTY));

        assertEquals(Optional.of(4), motif.limitFor(MIGHTY).orElseThrow().maxPerDungeon());
    }

    /** A fragment that says nothing about limits leaves an earlier one's alone. */
    @Test
    void aFragmentWithNoLimitsDoesNotClearAnEarlierOne() {
        MotifConfig motif = resolve(
                """
                {"template_limits":{"%s":{"max_per_dungeon":1}}}""".formatted(MIGHTY),
                "{\"schemes\":[{\"name\":\"plain\"}]}");

        assertTrue(motif.limitFor(MIGHTY).isPresent(),
                "a fragment holding only schemes wiped the motif's template limits");
    }
}
