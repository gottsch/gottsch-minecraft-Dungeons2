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
 * Backlog #6's guard: <strong>the entrance's jigsaw chain still points where it is supposed to,
 * per motif.</strong>
 *
 * <h2>Why the entrance needs this and rooms/transitions do not</h2>
 * <p>A room prefab is a single self-contained piece with no outgoing joint, and a transition's
 * shipped pieces are the same. The entrance is the one chain: the surface building names the pool
 * that holds the shaft, which names the pool that holds the room below. Motif-scoping the pools
 * (2026-08-13) therefore meant rewriting ids that live in <strong>compressed NBT</strong>, not in
 * any JSON &mdash; which is precisely the class of reference nothing else in this project can
 * check.</p>
 *
 * <h2>The failure mode this exists for</h2>
 * <p>Re-saving a template from a Structure Block writes back whatever the jigsaw block in that
 * world says. A piece re-saved from a world whose copy predates the motif-scoping silently restores
 * the old un-scoped {@code pool} id, and the only symptom is an entrance that stops assembling
 * &mdash; which falls back to the synthetic layout, generates a dungeon with no built entrance, and
 * logs nothing. That already happened once on this chain (the on-disk jigsaw patches reverted by an
 * in-game Save, during the entrance-chain work). A broken link must be a build failure.</p>
 *
 * <h2>What is deliberately not checked</h2>
 * <p>Only {@code pool} is required to be motif-scoped. {@code name} and {@code target} are joint
 * labels vanilla matches against each other; the pool already restricts the candidate set, so
 * scoping them would buy nothing and would force each new motif to re-label joints meaning the same
 * thing. {@link #everyJointTargetIsAnsweredWithinItsMotif} checks they agree with each other, not
 * what they are called.</p>
 *
 * <p>Templates sitting loose at the root of {@code structures/entrances} (rather than under a motif
 * folder) are skipped, because a motif is what these checks are relative to &mdash; there is no
 * motif to scope a root-level template's pool against. The two that were there
 * ({@code descent_1.nbt}, {@code surface_exit.nbt}, the monolithic pair the three-piece chain
 * replaced) were deleted on 2026-08-13 once this test confirmed no pool referenced them. Anything
 * that appears at the root from here is either a stray or belongs under a motif folder.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
class EntrancePoolWiringTest {

    private static final String ENTRANCE_TEMPLATES = "/data/dungeons2/structures/entrances";
    private static final String ENTRANCE_POOLS = "/data/dungeons2/worldgen/template_pool/entrance";
    private static final String EMPTY = "minecraft:empty";

    /** Mirrors {@code DungeonStructure.entranceStartPool} -- if that changes, this must. */
    private static final String START_POOL = "dungeons2:entrance/%s/surface_entrance";

    /** One jigsaw block, reduced to the three fields that matter here. */
    private record Joint(String template, String motif, String name, String pool, String target) {
    }

    // ---------- the checks ----------

    @Test
    void everyMotifHasTheStartPoolTheStructureAsksFor() {
        for (String motif : motifs()) {
            String expected = String.format(START_POOL, motif);
            assertTrue(poolsByName().containsKey(expected),
                    "motif '" + motif + "' ships entrance templates but no start pool named "
                            + expected + " -- DungeonStructure would find nothing and fall back to"
                            + " the synthetic layout, silently. Pools found: " + poolsByName().keySet());
        }
    }

    @Test
    void everyBakedPoolReferenceResolvesAndIsScopedToItsOwnMotif() {
        List<String> broken = new ArrayList<>();
        for (Joint joint : joints()) {
            if (joint.pool().isEmpty() || EMPTY.equals(joint.pool())) {
                continue;
            }
            String expectedPrefix = "dungeons2:entrance/" + joint.motif() + "/";
            if (!joint.pool().startsWith(expectedPrefix)) {
                broken.add(joint.template() + " joint '" + joint.name() + "' -> pool " + joint.pool()
                        + "  (not scoped to motif '" + joint.motif() + "'; expected "
                        + expectedPrefix + "...). An in-game re-save reverts this field.");
            } else if (!poolsByName().containsKey(joint.pool())) {
                broken.add(joint.template() + " joint '" + joint.name() + "' -> pool " + joint.pool()
                        + "  (no such template_pool ships)");
            }
        }
        if (!broken.isEmpty()) {
            fail(broken.size() + " entrance jigsaw pool reference(s) are broken. The entrance would"
                    + " stop assembling and the dungeon would generate with no built entrance,"
                    + " logging nothing:\n  " + String.join("\n  ", broken));
        }
    }

    @Test
    void everyJointTargetIsAnsweredWithinItsMotif() {
        List<String> dangling = new ArrayList<>();
        for (Joint joint : joints()) {
            if (joint.target().isEmpty() || EMPTY.equals(joint.target())) {
                continue;
            }
            boolean answered = joints().stream()
                    .anyMatch(other -> other.motif().equals(joint.motif())
                            && other.name().equals(joint.target()));
            if (!answered) {
                dangling.add(joint.template() + " joint '" + joint.name() + "' targets '"
                        + joint.target() + "', which no jigsaw in motif '" + joint.motif()
                        + "' answers");
            }
        }
        if (!dangling.isEmpty()) {
            fail(dangling.size() + " entrance joint(s) target a label nothing provides:\n  "
                    + String.join("\n  ", dangling));
        }
    }

    @Test
    void everyEntrancePoolElementNamesATemplateThatShips() {
        List<String> missing = new ArrayList<>();
        poolsByName().forEach((name, elements) -> elements.forEach(location -> {
            String path = location.substring(location.indexOf(':') + 1);
            if (EntrancePoolWiringTest.class.getResource(
                    "/data/dungeons2/structures/" + path + ".nbt") == null) {
                missing.add(name + " -> " + location + "  (no such .nbt)");
            }
        }));
        if (!missing.isEmpty()) {
            fail(missing.size() + " entrance pool element(s) name a template that does not ship:\n  "
                    + String.join("\n  ", missing));
        }
    }

    /** All four checks above pass vacuously if the chain is not being read. */
    @Test
    void theChainIsActuallyBeingRead() {
        assertTrue(motifs().contains("classic"), "expected the classic entrance, found " + motifs());
        assertTrue(joints().stream().anyMatch(joint -> !EMPTY.equals(joint.pool())),
                "expected at least one outgoing pool reference -- the entrance is the one chained"
                        + " assembly, so finding none means the NBT is not being read");
        assertTrue(joints().stream().anyMatch(joint -> !EMPTY.equals(joint.target())),
                "expected at least one joint target");
    }

    // ---------- reading the shipped content ----------

    private static Set<String> motifs() {
        Set<String> motifs = new LinkedHashSet<>();
        for (Path template : filesUnder(ENTRANCE_TEMPLATES, ".nbt")) {
            String motif = motifOf(template);
            if (motif != null) {
                motifs.add(motif);
            }
        }
        return motifs;
    }

    /** The folder directly under {@code entrances/}; null for a template loose at the root. */
    private static String motifOf(Path template) {
        Path parent = template.getParent();
        return parent == null || parent.getFileName().toString().equals("entrances")
                ? null : parent.getFileName().toString();
    }

    private static List<Joint> joints() {
        List<Joint> joints = new ArrayList<>();
        for (Path template : filesUnder(ENTRANCE_TEMPLATES, ".nbt")) {
            String motif = motifOf(template);
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
                    joints.add(new Joint(template.getFileName().toString(), motif,
                            data.getString("name"), data.getString("pool"), data.getString("target")));
                }
            }
        }
        return joints;
    }

    /** Pool id -> the element locations it offers. */
    private static Map<String, List<String>> poolsByName() {
        Map<String, List<String>> pools = new LinkedHashMap<>();
        for (Path file : filesUnder(ENTRANCE_POOLS, ".json")) {
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

    private static List<Path> filesUnder(String resourceDir, String extension) {
        URL url = EntrancePoolWiringTest.class.getResource(resourceDir);
        if (url == null) {
            return fail("no shipped content at " + resourceDir);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(extension))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + resourceDir + ": " + unreadable);
        }
    }
}
