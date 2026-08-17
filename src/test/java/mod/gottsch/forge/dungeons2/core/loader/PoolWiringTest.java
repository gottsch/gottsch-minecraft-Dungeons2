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
package mod.gottsch.forge.dungeons2.core.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Backlog #6 and #11's guard: <strong>every jigsaw pool reference still points where it is supposed
 * to, per motif, in all three pool categories.</strong>
 *
 * <h2>This used to be EntrancePoolWiringTest, and the assumption it was built on was wrong</h2>
 * <p>That class covered the entrance alone, on the stated grounds that "a room prefab is a single
 * self-contained piece with no outgoing joint, and a transition's shipped pieces are the same".
 * <strong>The room half is true; the transition half is not.</strong> {@code stairs_2_bottom} names
 * the pool holding the mid segment and {@code stairs_2_mid} names the pool holding the top &mdash;
 * a three-link chain with its {@code pool} ids baked into compressed NBT, carrying the identical
 * fragility the entrance was given a test for, and it had none. Generalised 2026-08-14 while
 * renaming those pools (#11).</p>
 *
 * <h2>The failure mode this exists for</h2>
 * <p>Re-saving a template from a Structure Block writes back whatever the jigsaw block in that world
 * says, so a piece re-saved from a world whose copy predates a pool rename silently restores the old
 * id. The symptom is silent in both categories and differently bad in each: a broken entrance link
 * falls back to the synthetic layout and generates a dungeon with no built entrance, and a broken
 * transition link truncates a staircase partway between floors. Neither logs anything. That has
 * already happened once, on the entrance chain, during the #6 work. A broken link must be a build
 * failure.</p>
 *
 * <h2>What is deliberately not checked</h2>
 * <p>Only {@code pool} is required to be motif-scoped. {@code name} and {@code target} are joint
 * labels vanilla matches against each other; the pool already restricts the candidate set, so
 * scoping them would buy nothing and would force each new motif to re-label joints meaning the same
 * thing. {@link #everyJointTargetIsAnsweredWithinItsMotif} checks they agree with each other, not
 * what they are called. (The transition chain's labels happen to be chain-scoped already &mdash;
 * {@code dungeons2:stairs_2/mid_down} &mdash; which is authoring taste, not a rule.)</p>
 *
 * <p>Templates sitting loose at the root of a category folder are skipped, because a motif is what
 * these checks are relative to. Anything appearing there is either a stray or belongs under a motif
 * folder.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
class PoolWiringTest {

    private static final String EMPTY = "minecraft:empty";

    /**
     * One pool category.
     *
     * <p>{@code poolPrefix} and {@code startPool} mirror {@code DungeonStructure}'s three
     * {@code *StartPool} methods &mdash; if one of those changes, this must. {@code chained} says
     * whether the category's pieces reference each other at all, which is what
     * {@link #theChainsAreActuallyBeingRead} needs in order to be able to fail.</p>
     */
    private record Category(String id, String templateRoot, String poolRoot,
                            String poolPrefix, String startPool, boolean chained) {
    }

    private static final List<Category> CATEGORIES = List.of(
            new Category("entrance",
                    "/data/dungeons2/structures/entrances",
                    "/data/dungeons2/worldgen/template_pool/entrance",
                    "dungeons2:entrance/%s/", "dungeons2:entrance/%s/surface_entrance", true),
            new Category("transitions",
                    "/data/dungeons2/structures/transitions",
                    "/data/dungeons2/worldgen/template_pool/transitions",
                    "dungeons2:transitions/%s/", "dungeons2:transitions/%s/shaft_bottom", true),
            new Category("rooms",
                    "/data/dungeons2/structures/rooms",
                    "/data/dungeons2/worldgen/template_pool/rooms",
                    "dungeons2:rooms/%s/", "dungeons2:rooms/%s/normal", false));

    /** One jigsaw block, reduced to the fields that matter here. */
    private record Joint(String category, String template, String motif,
                         String name, String pool, String target) {
    }

    // ---------- the checks ----------

    @Test
    void everyMotifHasTheStartPoolTheStructureAsksFor() {
        for (Category category : CATEGORIES) {
            Map<String, List<String>> pools = poolsByName(category);
            for (String motif : motifs(category)) {
                String expected = String.format(category.startPool(), motif);
                assertTrue(pools.containsKey(expected),
                        "motif '" + motif + "' ships " + category.id() + " templates but no start"
                                + " pool named " + expected + " -- DungeonStructure would find"
                                + " nothing and degrade silently. Pools found: " + pools.keySet());
            }
        }
    }

    @Test
    void everyBakedPoolReferenceResolvesAndIsScopedToItsOwnMotif() {
        List<String> broken = new ArrayList<>();
        for (Category category : CATEGORIES) {
            Map<String, List<String>> pools = poolsByName(category);
            for (Joint joint : joints(category)) {
                if (joint.pool().isEmpty() || EMPTY.equals(joint.pool())) {
                    continue;
                }
                String expectedPrefix = String.format(category.poolPrefix(), joint.motif());
                if (!joint.pool().startsWith(expectedPrefix)) {
                    broken.add(joint.category() + "/" + joint.template() + " joint '" + joint.name()
                            + "' -> pool " + joint.pool() + "  (not scoped to motif '"
                            + joint.motif() + "'; expected " + expectedPrefix + "...). An in-game"
                            + " re-save reverts this field.");
                } else if (!pools.containsKey(joint.pool())) {
                    broken.add(joint.category() + "/" + joint.template() + " joint '" + joint.name()
                            + "' -> pool " + joint.pool() + "  (no such template_pool ships)");
                }
            }
        }
        if (!broken.isEmpty()) {
            fail(broken.size() + " jigsaw pool reference(s) are broken. The assembly would stop"
                    + " partway and the dungeon would generate around the gap, logging nothing:\n  "
                    + String.join("\n  ", broken));
        }
    }

    @Test
    void everyJointTargetIsAnsweredWithinItsMotif() {
        List<String> dangling = new ArrayList<>();
        for (Category category : CATEGORIES) {
            List<Joint> joints = joints(category);
            for (Joint joint : joints) {
                if (joint.target().isEmpty() || EMPTY.equals(joint.target())) {
                    continue;
                }
                boolean answered = joints.stream()
                        .anyMatch(other -> other.motif().equals(joint.motif())
                                && other.name().equals(joint.target()));
                if (!answered) {
                    dangling.add(joint.category() + "/" + joint.template() + " joint '"
                            + joint.name() + "' targets '" + joint.target() + "', which no jigsaw"
                            + " in motif '" + joint.motif() + "' answers");
                }
            }
        }
        if (!dangling.isEmpty()) {
            fail(dangling.size() + " joint(s) target a label nothing provides:\n  "
                    + String.join("\n  ", dangling));
        }
    }

    @Test
    void everyPoolElementNamesATemplateThatShips() {
        List<String> missing = new ArrayList<>();
        for (Category category : CATEGORIES) {
            poolsByName(category).forEach((name, elements) -> elements.forEach(location -> {
                String path = location.substring(location.indexOf(':') + 1);
                if (PoolWiringTest.class.getResource(
                        "/data/dungeons2/structures/" + path + ".nbt") == null) {
                    missing.add(name + " -> " + location + "  (no such .nbt)");
                }
            }));
        }
        if (!missing.isEmpty()) {
            fail(missing.size() + " pool element(s) name a template that does not ship:\n  "
                    + String.join("\n  ", missing));
        }
    }

    /** Every check above passes vacuously if the content is not actually being read. */
    @Test
    void theChainsAreActuallyBeingRead() {
        for (Category category : CATEGORIES) {
            assertTrue(motifs(category).contains("classic"),
                    "expected a classic " + category.id() + ", found " + motifs(category));
            assertTrue(!poolsByName(category).isEmpty(),
                    "no " + category.id() + " pools were read at all");
            if (category.chained()) {
                assertTrue(joints(category).stream().anyMatch(joint -> !EMPTY.equals(joint.pool())),
                        category.id() + " is a chained category, so finding no outgoing pool"
                                + " reference means the NBT is not being read");
            }
        }
    }

    // ---------- reading the shipped content ----------

    private static Set<String> motifs(Category category) {
        Set<String> motifs = new LinkedHashSet<>();
        for (Path template : filesUnder(category.templateRoot(), ".nbt")) {
            String motif = motifOf(category, template);
            if (motif != null) {
                motifs.add(motif);
            }
        }
        return motifs;
    }

    /**
     * The first folder <em>below the category root</em>; null for a template loose at the root.
     *
     * <p>Not simply the parent folder, which is what the entrance-only version did: rooms nest a
     * size folder under the motif ({@code rooms/classic/11x11/...}), so a parent lookup would read
     * the motif of every 11x11 room as "11x11" and the checks would compare against a motif that
     * does not exist.</p>
     */
    private static String motifOf(Category category, Path template) {
        Path root = resourceRoot(category.templateRoot());
        Path relative = root.relativize(template);
        return relative.getNameCount() < 2 ? null : relative.getName(0).toString();
    }

    private static List<Joint> joints(Category category) {
        List<Joint> joints = new ArrayList<>();
        for (Path template : filesUnder(category.templateRoot(), ".nbt")) {
            String motif = motifOf(category, template);
            if (motif == null) {
                continue;
            }
            CompoundTag root = readTemplate(template);
            for (Tag blockTag : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) blockTag;
                if (!block.contains("nbt")) {
                    continue;
                }
                CompoundTag data = block.getCompound("nbt");
                // A jigsaw block entity is the only one carrying all three of these.
                if (data.contains("pool") && data.contains("target") && data.contains("name")) {
                    joints.add(new Joint(category.id(), template.getFileName().toString(), motif,
                            data.getString("name"), data.getString("pool"), data.getString("target")));
                }
            }
        }
        return joints;
    }

    /** Pool id -> the element locations it offers. */
    private static Map<String, List<String>> poolsByName(Category category) {
        Map<String, List<String>> pools = new LinkedHashMap<>();
        for (Path file : filesUnder(category.poolRoot(), ".json")) {
            JsonObject pool = parse(file).getAsJsonObject();
            List<String> locations = new ArrayList<>();
            JsonArray elements = pool.getAsJsonArray("elements");
            if (elements != null) {
                for (JsonElement wrapped : elements) {
                    JsonObject element = wrapped.getAsJsonObject().getAsJsonObject("element");
                    if (element != null && element.has("location")) {
                        locations.add(element.get("location").getAsString());
                    }
                }
            }
            pools.put(pool.get("name").getAsString(), locations);
        }
        return pools;
    }

    private static CompoundTag readTemplate(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static JsonElement parse(Path file) {
        try (Reader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static Path resourceRoot(String resourceDir) {
        URL url = PoolWiringTest.class.getResource(resourceDir);
        if (url == null) {
            return fail("no shipped content at " + resourceDir);
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException bad) {
            return fail("could not resolve " + resourceDir + ": " + bad);
        }
    }

    private static List<Path> filesUnder(String resourceDir, String extension) {
        try (Stream<Path> paths = Files.walk(resourceRoot(resourceDir))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(extension))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            return fail("could not walk " + resourceDir + ": " + unreadable);
        }
    }
}
