/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigFragment;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the <em>shipped</em> datapack JSON (under {@code data/dungeons2/}) against
 * schema drift: every file must decode cleanly with its Codec. Pure POJO &mdash; block
 * ids are parsed as {@code ResourceLocation}, so no Minecraft bootstrap is required.
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
class DatapackResourcesParseTest {

    private static final Gson GSON = new Gson();

    private static final String MOTIF_DIR = "/data/dungeons2/dungeons2/motif_config/";

    /** The motifs this mod ships. Each is either a flat {@code <motif>.json} or a folder of files. */
    private static final String[] MOTIFS = {"classic", "catacombs", "deep_slate"};

    /**
     * A shipped motif, assembled exactly the way {@code MotifConfigHelper} assembles it in game:
     * the flat {@code <motif>.json} if there is one, then every file under {@code <motif>/}, in id
     * order. Reading the folder rather than a fixed file list is the point &mdash; a new
     * {@code schemes_*.json} that fails to decode must fail the build without anyone remembering to
     * add it here.
     */
    private static MotifConfig motif(String name) {
        List<MotifConfigFragment> fragments = new ArrayList<>();
        if (DatapackResourcesParseTest.class.getResource(MOTIF_DIR + name + ".json") != null) {
            fragments.add(parse(MOTIF_DIR + name + ".json", MotifConfigFragment.CODEC));
        }
        for (String file : filesIn(MOTIF_DIR + name)) {
            fragments.add(parse(MOTIF_DIR + name + "/" + file, MotifConfigFragment.CODEC));
        }
        assertFalse(fragments.isEmpty(), "motif '" + name + "' ships no files at all");
        return MotifConfigFragment.resolve(fragments);
    }

    /** The .json file names directly under a resource directory, sorted; empty if there is no such directory. */
    private static List<String> filesIn(String resourceDir) {
        URL url = DatapackResourcesParseTest.class.getResource(resourceDir);
        if (url == null) {
            return List.of();
        }
        try {
            Path dir = Paths.get(url.toURI());
            try (Stream<Path> entries = Files.list(dir)) {
                return entries.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(file -> file.endsWith(".json"))
                        .sorted(Comparator.naturalOrder())
                        .toList();
            }
        } catch (Exception e) {
            throw new AssertionError("error listing " + resourceDir, e);
        }
    }

    private static <T> T parse(String resourcePath, Codec<T> codec) {
        try (InputStream in = DatapackResourcesParseTest.class.getResourceAsStream(resourcePath)) {
            assertTrue(in != null, "missing datapack resource on classpath: " + resourcePath);
            JsonElement json = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
            DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
            assertTrue(result.result().isPresent(),
                    "failed to decode " + resourcePath + ": " + result.error().map(Object::toString).orElse(""));
            return result.result().get();
        } catch (Exception e) {
            throw new AssertionError("error reading " + resourcePath, e);
        }
    }

    @Test
    void motifConfigFilesDecode() {
        for (String name : MOTIFS) {
            motif(name);
        }
    }

    /**
     * Decoding cleanly is not enough for {@code schemes}. A misspelled <em>field name</em> is
     * indistinguishable from an absent one without a closed schema (the acknowledged gap in
     * {@code Codecs#strictOptionalFieldOf}), so a typo'd {@code "scheems"} would decode
     * successfully and silently leave classic with nothing but the default plain scheme -- every
     * room in the dungeon undecorated, no error anywhere. Asserting the decoded content, not just
     * that it decoded, is the only thing that catches that.
     */
    /**
     * Every loot table a pots slot names must actually be a shipped file. This is the one pot
     * failure that is completely silent in game: {@code PotEntity#dropLoot} looks the id up, gets
     * nothing back, and the pot shatters into thin air with no error anywhere. A typo'd id is
     * indistinguishable from an empty table at runtime, so it has to be caught here.
     */
    @Test
    void everyPotLootTableReferencedByAMotifExists() {
        for (String name : MOTIFS) {
            MotifConfig config = motif(name);
            for (RoomScheme scheme : config.schemes()) {
                scheme.pots().ifPresent(pots -> {
                    String id = pots.lootTable();
                    assertTrue(id.startsWith("dungeons2:"),
                            "scheme '" + scheme.name() + "' points at a foreign loot table (" + id
                                    + "); only this mod's own tables are guaranteed to ship");
                    String path = "/data/dungeons2/loot_tables/"
                            + id.substring("dungeons2:".length()) + ".json";
                    assertNotNull(DatapackResourcesParseTest.class.getResourceAsStream(path),
                            "scheme '" + scheme.name() + "' names a loot table with no file: " + path);
                    assertFalse(pots.variants().isEmpty(),
                            "scheme '" + scheme.name() + "' has a pots slot with no variants");
                });
            }
        }
    }

    /**
     * Every course of a scheme's wall slot, across all of its patterns.
     *
     * <p>The slot became an ordered list of patterns in Aug 2026 so that a wall could carry courses
     * and pilasters at once. These checks are about the courses wherever they sit, so they flatten
     * rather than caring which pattern each came from.</p>
     */
    private static List<WallPatternEntry.CourseEntry> courses(RoomScheme scheme) {
        return scheme.wall().stream()
                .flatMap(wall -> wall.patterns().stream())
                .flatMap(pattern -> pattern.courses().stream())
                .toList();
    }

    // NOTE: `noSchemeCombinesPotsWithAFloorLevelProjectingCourse` used to live here, forbidding a
    // scheme from carrying both pots and floor-level projecting trim because a pot would spawn
    // inside the trim. It was deleted in Aug 2026 when the collision stopped being possible:
    // BasicWallGenerator now reports the floor-level cells its projecting trim took, and
    // RoomPropGenerator excludes them before drawing pot positions. That was a prerequisite for
    // pilasters, which occupy those cells by construction rather than by an authoring slip, so the
    // restriction could not simply have been widened. The behaviour is covered by
    // RoomPropGeneratorTest and BasicRoomGeneratorTest instead -- a mechanism, not a convention.

    /**
     * A sill (and its double-sill sibling) is a ledge: it only reads correctly standing out from the
     * wall. Set flush in the wall plane it renders as a recessed panel, which is not what the block
     * is for. Matched on the id because the rule belongs to that block family, not to the pattern
     * type -- a naming heuristic, but the alternative is a per-block table nobody would maintain.
     */
    @Test
    void sillBlocksAreAlwaysProjected() {
        for (String name : MOTIFS) {
            MotifConfig config = motif(name);
            for (RoomScheme scheme : config.schemes()) {
                for (WallPatternEntry.CourseEntry course : courses(scheme)) {
                    if (course.block().contains("sill")) {
                        assertTrue(course.projection() > 0, "scheme '" + scheme.name()
                                + "' uses " + course.block() + " flush in the wall; a sill is a "
                                + "ledge and needs \"projection\": 1");
                    }
                }
            }
        }
    }

    /**
     * {@code dungeonblocks}' directional trim blocks are <strong>facing-inverted relative to
     * vanilla</strong>: the same {@code facing} value points their solid side the opposite way from
     * a vanilla stair. So a cornice built from vanilla stairs wants {@code toward_wall} and the same
     * cornice built from a dungeonblocks moulding wants {@code toward_room}, and a scheme that uses
     * one value for both has one of them inside-out.
     *
     * <p>Not derivable from the block ids at runtime -- it is a property of how that mod models its
     * blocks -- so it is pinned here against the shipped content instead.</p>
     */
    @Test
    void projectedTrimIsOrientedForItsBlockFamily() {
        for (String name : MOTIFS) {
            MotifConfig config = motif(name);
            for (RoomScheme scheme : config.schemes()) {
                for (WallPatternEntry.CourseEntry course : courses(scheme)) {
                    if (course.projection() == 0) {
                        continue;
                    }
                    boolean vanilla = course.block().startsWith("minecraft:");
                    WallPatternEntry.CourseOrient expected = vanilla
                            ? WallPatternEntry.CourseOrient.TOWARD_WALL
                            : WallPatternEntry.CourseOrient.TOWARD_ROOM;
                    assertEquals(expected, course.orient(), "scheme '" + scheme.name() + "': "
                            + course.block() + " projects, so it needs orient=" + expected
                            + " -- dungeonblocks trim faces opposite to vanilla");
                }
            }
        }
    }

    @Test
    @Disabled("TEMPORARY -- classic is cut down to base.json (plain + vaulted_hall) for in-game "
            + "testing of the vault. The other scheme files are parked in "
            + "src/main/resources/disabled-schemes/classic/; see the README there. RE-ENABLE THIS "
            + "when they move back -- it is the guard against the scheme list silently collapsing.")
    void classicShipsItsFullSchemeList() {
        MotifConfig classic = motif("classic");

        // Deliberately a floor, not an exact count -- the scheme list is authored content and is
        // expected to grow. What must never happen is it collapsing to the one-element default.
        assertNotEquals(List.of(RoomScheme.PLAIN), classic.schemes(),
                "classic fell back to the default scheme list -- check the 'schemes' field name");
        assertTrue(classic.schemes().size() >= 5,
                "classic should ship a real scheme list, got " + classic.schemes().size());

        Set<String> names = classic.schemes().stream().map(RoomScheme::name).collect(Collectors.toSet());
        assertEquals(classic.schemes().size(), names.size(), "scheme names should be unique: " + names);

        assertTrue(classic.schemes().stream().anyMatch(s -> s.floor().isPresent()),
                "at least one scheme should decorate the floor");
        assertTrue(classic.schemes().stream().anyMatch(s -> s.floor().isEmpty()),
                "classic should keep an undecorated scheme -- and an unconstrained one, so no room "
                        + "can fail to match every scheme and fall through to PLAIN");
    }

    /**
     * Every room the planner can build must match at least one scheme.
     *
     * <p>This became possible to get wrong the moment {@code maxHeight}/{@code maxSize} existed.
     * With lower bounds only, a single unconstrained scheme guarantees coverage and no arrangement
     * of the others can break it. With upper bounds, a band of room sizes can fall through every
     * scheme at once — and the failure is silent: {@code RoomSchemeSelector} degrades to an
     * undecorated room, which looks exactly like a room that rolled the plain scheme. Nobody would
     * notice a hole until they wondered why 11-wide rooms are always bare.</p>
     *
     * <p>Swept over what the planner actually produces, not over every integer pair: rooms are odd,
     * 5..17 on a side, and height is {@code min(rand(5..10), max(width, depth))}, so a 5x5 room can
     * never be 10 high. Testing impossible shapes would force authors to cover rooms that do not
     * exist.</p>
     */
    @Test
    void everyRoomThePlannerCanBuildMatchesAScheme() {
        for (String name : MOTIFS) {
            List<RoomScheme> schemes = motif(name).schemes();
            for (int width = 5; width <= 17; width += 2) {
                for (int depth = 5; depth <= 17; depth += 2) {
                    int tallest = Math.min(10, Math.max(width, depth));
                    for (int height = 5; height <= tallest; height++) {
                        int w = width;
                        int d = depth;
                        int h = height;
                        assertTrue(schemes.stream().anyMatch(scheme -> scheme.fits(w, d, h)),
                                "motif '" + name + "' has no scheme for a " + width + "x" + depth
                                        + " room " + height + " high; such rooms would silently "
                                        + "generate undecorated");
                    }
                }
            }
        }
    }

    /**
     * A scheme that fills element slots must actually draw <em>somewhere</em> in its own range.
     *
     * <p>Element gates make dead content possible: a wall slot gated to {@code minHeight 9} inside a
     * scheme capped at {@code maxHeight 7} is authored, loads cleanly, and can never render. Nothing
     * else notices — the scheme still wins rooms and the by-scheme counts look healthy.</p>
     *
     * <p><strong>Drawing nothing in part of a range is fine, and is the point of the feature.</strong>
     * The shipped {@code plain} scheme carries a cornice gated at height 6, so in a 5-high room it
     * deliberately renders an undecorated room; that is one scheme doing what used to take two. So
     * the bar is "at least one eligible room shape draws something", not "every eligible room does".
     * An earlier version of this check counted bare rooms globally and failed on exactly that
     * legitimate case.</p>
     *
     * <p>Schemes with no slots at all are skipped: rendering nothing is their entire job.</p>
     */
    @Test
    void everySchemeThatDecoratesDrawsSomethingSomewhereInItsRange() {
        for (String name : MOTIFS) {
            for (RoomScheme scheme : motif(name).schemes()) {
                if (!scheme.declaresAnySlot()) {
                    continue;
                }
                boolean everDraws = false;
                for (int width = 5; width <= 17 && !everDraws; width += 2) {
                    for (int depth = 5; depth <= 17 && !everDraws; depth += 2) {
                        int tallest = Math.min(10, Math.max(width, depth));
                        for (int height = 5; height <= tallest; height++) {
                            if (scheme.fits(width, depth, height)
                                    && scheme.drawsAnything(width, depth, height)) {
                                everDraws = true;
                                break;
                            }
                        }
                    }
                }
                assertTrue(everDraws, "motif '" + name + "', scheme '" + scheme.name()
                        + "' fills element slots but every one of them gates out of every room it "
                        + "is eligible for -- it can never render anything");
            }
        }
    }

    /**
     * The one thing splitting a motif into a folder made possible to get wrong. A duplicate scheme
     * name is a deliberate <em>override</em> in the merge (that is how an addon retunes a shipped
     * scheme), which means two of this mod's own files reusing a name silently drops one of them --
     * a scheme that stops appearing in game with no error anywhere. Checked per file rather than on
     * the merged result, because the merge is exactly what hides it.
     */
    @Test
    void noMotifAuthorsTheSameSchemeNameInTwoFiles() {
        for (String name : MOTIFS) {
            List<String> files = new ArrayList<>();
            if (DatapackResourcesParseTest.class.getResource(MOTIF_DIR + name + ".json") != null) {
                files.add(name + ".json");
            }
            filesIn(MOTIF_DIR + name).forEach(file -> files.add(name + "/" + file));

            Set<String> seen = new java.util.HashSet<>();
            for (String file : files) {
                for (RoomScheme scheme : parse(MOTIF_DIR + file, MotifConfigFragment.CODEC).schemes()) {
                    assertTrue(seen.add(scheme.name()), "scheme '" + scheme.name() + "' is authored "
                            + "twice in motif '" + name + "' (again in " + file + "); the second "
                            + "would silently replace the first");
                }
            }
        }
    }

    // The shipped worldgen/processor_list file (weathering) is guarded by
    // WeatheringProcessorListTest instead -- decoding it needs BuiltInRegistries, and
    // this class deliberately stays Minecraft-bootstrap-free.
}
