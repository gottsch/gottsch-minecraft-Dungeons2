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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scheme inheritance (backlog #23): {@code extends}, {@code abstract}, and the two faults that can
 * only be caught after the fragments have merged.
 *
 * <p>Pure codec and folding work &mdash; block ids stay strings until render time, so no Minecraft
 * bootstrap is needed, the same as {@link MotifConfigCodecTest}.</p>
 */
class SchemeInheritanceTest {

    private static final Gson GSON = new Gson();

    private static MotifConfigFragment fragment(String json) {
        return MotifConfigFragment.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(json, JsonElement.class))
                .result().orElseThrow();
    }

    private static String schemes(String... entries) {
        return "{\"schemes\":[" + String.join(",", entries) + "]}";
    }

    /** A parent worth inheriting: a wall course and a pot config, both nameable in an assertion. */
    private static final String ABSTRACT_PARENT = """
            {"name":"grand","abstract":true,
             "wall":{"patterns":[{"type":"courses","courses":[
                 {"block":"minecraft:polished_andesite","anchor":"top"}]}]},
             "pots":{"minCount":1,"maxCount":2,"lootTable":"dungeons2:pots/classic",
                     "variants":[{"entity":"dungeonblocks:pot","weight":1}]}}""";

    private static List<RoomScheme> resolved(String... fragments) {
        return resolved(new ArrayList<>(), fragments);
    }

    private static List<RoomScheme> resolved(List<String> problems, String... fragments) {
        List<MotifConfigFragment> parsed = new ArrayList<>();
        for (String json : fragments) {
            parsed.add(fragment(json));
        }
        return MotifConfigFragment.resolve(parsed, problems::add).schemes();
    }

    private static RoomScheme byName(List<RoomScheme> schemes, String name) {
        return schemes.stream().filter(s -> name.equals(s.name())).findFirst()
                .orElseThrow(() -> new AssertionError("no scheme '" + name + "' in " + schemes));
    }

    // ---------- what inherits ----------

    @Test
    void aChildTakesTheSlotsItDoesNotFillFromItsParent() {
        List<RoomScheme> schemes = resolved(schemes(ABSTRACT_PARENT,
                "{\"name\":\"child\",\"extends\":\"grand\",\"minSize\":9}"));

        RoomScheme child = byName(schemes, "child");
        assertTrue(child.wall().isPresent(), "the wall course should come from the parent");
        assertTrue(child.pots().isPresent(), "and the pots");
        assertFalse(child.ceiling().isPresent(), "slots neither declares stay empty");
    }

    /**
     * <strong>A slot the child fills replaces the parent's wholesale.</strong> No merging of the
     * lists inside it: a list-merge cannot express removing an inherited entry, and "override with
     * less" is the commoner intent.
     */
    @Test
    void aSlotTheChildFillsWinsOutrightRatherThanMerging() {
        List<RoomScheme> schemes = resolved(schemes(ABSTRACT_PARENT, """
                {"name":"child","extends":"grand",
                 "wall":{"patterns":[{"type":"courses","courses":[
                     {"block":"minecraft:deepslate_bricks","anchor":"bottom"}]}]}}"""));

        WallPatternEntry wall = byName(schemes, "child").wall().orElseThrow();
        assertEquals(1, wall.patterns().size(), "one pattern, not the parent's plus the child's");
        assertEquals("minecraft:deepslate_bricks",
                wall.patterns().get(0).courses().get(0).block());
    }

    /**
     * <strong>Weight and the size bounds never inherit.</strong> Two reasons, and the second is the
     * real one: a primitive cannot distinguish "omitted" from "wrote the default", and a variant
     * exists <em>because</em> its eligibility differs -- quietly copying a parent's {@code minSize}
     * is how a whole size band ends up with no scheme at all.
     */
    @Test
    void weightAndSizeBoundsStayTheChildsOwn() {
        List<RoomScheme> schemes = resolved(schemes("""
                {"name":"grand","abstract":true,"weight":50,"minSize":15,"minHeight":9,
                 "maxSize":40,
                 "pots":{"minCount":1,"maxCount":1,"lootTable":"dungeons2:pots/classic",
                         "variants":[{"entity":"dungeonblocks:pot","weight":1}]}}""",
                "{\"name\":\"child\",\"extends\":\"grand\"}"));

        RoomScheme child = byName(schemes, "child");
        assertEquals(1, child.weight(), "the codec default, not the parent's 50");
        assertEquals(0, child.minSize(), "not the parent's 15");
        assertEquals(0, child.minHeight(), "not the parent's 9");
        assertTrue(child.maxSize().isEmpty(), "not the parent's 40");
        assertTrue(child.pots().isPresent(), "but the content still comes across");
    }

    // ---------- abstract ----------

    /**
     * A template must not compete in the roll. It cannot be silenced with {@code weight: 0} either
     * -- the codec's floor is 1 -- so without the flag a half-authored parent would render as a room
     * in its own right.
     */
    @Test
    void anAbstractSchemeIsNeverRolled() {
        List<RoomScheme> schemes = resolved(schemes(ABSTRACT_PARENT,
                "{\"name\":\"child\",\"extends\":\"grand\"}"));

        assertEquals(List.of("child"), schemes.stream().map(RoomScheme::name).toList());
    }

    /** Even one nothing extends: {@code abstract} means "not a room", not "not yet used". */
    @Test
    void anUnusedAbstractSchemeIsStillDropped() {
        assertEquals(List.of("solid"), resolved(schemes(ABSTRACT_PARENT,
                "{\"name\":\"solid\"}")).stream().map(RoomScheme::name).toList());
    }

    /** A motif whose every scheme is abstract is undecorated, not unrenderable. */
    @Test
    void aMotifOfNothingButTemplatesFallsBackToPlain() {
        assertEquals(List.of(RoomScheme.PLAIN), resolved(schemes(ABSTRACT_PARENT)));
    }

    /** Extending a concrete scheme is allowed -- it simply also keeps rolling on its own. */
    @Test
    void aConcreteParentIsInheritableAndStillRolls() {
        List<RoomScheme> schemes = resolved(schemes(
                "{\"name\":\"plain\",\"pots\":{\"minCount\":1,\"maxCount\":1,"
                        + "\"lootTable\":\"dungeons2:pots/classic\","
                        + "\"variants\":[{\"entity\":\"dungeonblocks:pot\",\"weight\":1}]}}",
                "{\"name\":\"variant\",\"extends\":\"plain\"}"));

        assertEquals(List.of("plain", "variant"), schemes.stream().map(RoomScheme::name).toList());
        assertTrue(byName(schemes, "variant").pots().isPresent());
    }

    // ---------- across files, which is the whole point ----------

    /**
     * <strong>The prize.</strong> A parent may live in another fragment, so an addon can retune the
     * parent and reach every child without restating any of them. This is why inheritance is
     * resolved after the merge and cannot be a codec concern.
     */
    @Test
    void aParentInAnotherFragmentIsFound() {
        List<RoomScheme> schemes = resolved(
                schemes("{\"name\":\"child\",\"extends\":\"grand\"}"),
                schemes(ABSTRACT_PARENT));

        assertTrue(byName(schemes, "child").wall().isPresent());
    }

    /** And an override of the parent propagates, which is the reason to author it this way at all. */
    @Test
    void overridingTheParentReachesItsChildren() {
        List<RoomScheme> schemes = resolved(
                schemes(ABSTRACT_PARENT, "{\"name\":\"child\",\"extends\":\"grand\"}"),
                schemes("""
                        {"name":"grand","abstract":true,
                         "wall":{"patterns":[{"type":"courses","courses":[
                             {"block":"minecraft:deepslate_bricks","anchor":"top"}]}]}}"""));

        WallPatternEntry wall = byName(schemes, "child").wall().orElseThrow();
        assertEquals("minecraft:deepslate_bricks", wall.patterns().get(0).courses().get(0).block());
        assertTrue(byName(schemes, "child").pots().isEmpty(),
                "the override replaced the parent wholesale, so its pots went with it");
    }

    // ---------- the two faults ----------

    /**
     * A missing parent <strong>drops the child</strong> and says so. It cannot be a load error the
     * way a misspelled key is: the parent is addressed across the whole motif, so one file's codec
     * cannot know whether it exists. Dropping is the honest outcome -- a scheme half of whose
     * content is missing would draw a room nobody authored.
     */
    @Test
    void aMissingParentDropsTheChildAndReportsIt() {
        List<String> problems = new ArrayList<>();
        List<RoomScheme> schemes = resolved(problems, schemes(
                "{\"name\":\"solid\"}",
                "{\"name\":\"orphan\",\"extends\":\"no_such_scheme\"}"));

        assertEquals(List.of("solid"), schemes.stream().map(RoomScheme::name).toList());
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("orphan") && problems.get(0).contains("no_such_scheme"),
                "the report should name both ends: " + problems.get(0));
    }

    /**
     * <strong>One hop only.</strong> It makes cycles unrepresentable rather than something to
     * detect, and bounds what an author has to hold in their head to two files.
     */
    @Test
    void aGrandparentChainIsRejected() {
        List<String> problems = new ArrayList<>();
        List<RoomScheme> schemes = resolved(problems, schemes(
                "{\"name\":\"root\",\"abstract\":true}",
                "{\"name\":\"middle\",\"abstract\":true,\"extends\":\"root\"}",
                "{\"name\":\"leaf\",\"extends\":\"middle\"}"));

        assertTrue(schemes.stream().map(RoomScheme::name).noneMatch("leaf"::equals),
                "the two-hop child is dropped: " + schemes);
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.get(0).contains("one hop"), problems.get(0));
    }

    /** Self-extension is the one inheritance fault a single file's codec CAN see, so it is fatal. */
    @Test
    void aSchemeThatExtendsItselfFailsTheLoad() {
        assertTrue(MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, GSON.fromJson(
                        schemes("{\"name\":\"ouroboros\",\"extends\":\"ouroboros\"}"), JsonElement.class))
                .error().isPresent(), "expected a load error");
    }

    /** A cycle of two is unreachable through the one-hop rule, but must not hang or resolve. */
    @Test
    void aTwoSchemeCycleIsRejectedRatherThanFollowed() {
        List<String> problems = new ArrayList<>();
        List<RoomScheme> schemes = resolved(problems, schemes(
                "{\"name\":\"a\",\"extends\":\"b\"}",
                "{\"name\":\"b\",\"extends\":\"a\"}"));

        assertEquals(List.of(RoomScheme.PLAIN), schemes, "both drop, leaving the plain fallback");
        assertEquals(2, problems.size(), problems.toString());
    }

    // ---------- the shape that must not change ----------

    /** A scheme with no {@code extends} is passed through untouched, not copied. */
    @Test
    void aSchemeWithoutExtendsIsTheSameInstance() {
        MotifConfigFragment only = fragment(schemes("{\"name\":\"solid\",\"minSize\":9}"));
        assertSame(only.schemes().get(0),
                MotifConfigFragment.resolve(List.of(only)).schemes().get(0));
    }

    /** {@code extends} and {@code abstract} survive a round trip through the closed codec. */
    @Test
    void theNewFieldsRoundTrip() {
        RoomScheme scheme = new RoomScheme("child", 3, 7, 9,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.of("grand"), true);

        JsonElement json = RoomScheme.CODEC.encodeStart(JsonOps.INSTANCE, scheme)
                .result().orElseThrow();
        assertEquals(scheme, RoomScheme.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
    }
}
