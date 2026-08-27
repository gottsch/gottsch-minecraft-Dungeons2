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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <strong>Every element slot shown in the Room Schemes Manual must actually decode.</strong>
 *
 * <h2>Why a test reads a document</h2>
 * <p>The manual is the wiki-facing how-to: it is where a pack author learns the JSON, so an example
 * that no longer loads teaches people to write something the game rejects. Nothing else in the
 * build looks at it, and the examples went stale twice in one day &mdash; once when wall slots
 * gained a required {@code patterns} list, and again when every {@code type} became a namespaced
 * registry id with its fields under {@code config}. Both were caught by eye, which is not a
 * process.</p>
 *
 * <p>It parses the fenced {@code json} blocks and decodes any {@code floor} / {@code wall} /
 * {@code ceiling} / {@code pillars} / {@code platforms} / {@code pots} object it finds, at whatever
 * depth &mdash; the blocks are a mix of whole schemes, bare slots and fragments, so it looks for
 * slots rather than assuming a shape.</p>
 *
 * <h2>It skips rather than fails when the manual is absent</h2>
 * <p>The document lives outside the repository (Mark's notes tree), so a clone without it must not
 * go red. {@code MANUAL} is the one path it looks at; if that is gone the test is skipped, loudly
 * enough to notice in the report but without failing a build that is otherwise fine.</p>
 */
class ManualExamplesTest {

    private static final Path MANUAL = Path.of("C:", "Development", "claude", "minecraft", "forge",
            "dungeons2", "1.20.1", "dungeons2-forge-1.20.1-RoomSchemesManual.md");

    /**
     * Scheme slot name to the codec that must accept it.
     *
     * <p>{@code floor}, {@code wall} and {@code ceiling} are ambiguous by name: a motif's own
     * MATERIAL section shares each one. They are told apart by shape in {@link #codecFor} rather
     * than by position, because the manual shows both, sometimes in the same block.</p>
     */
    private static final Map<String, com.mojang.serialization.Codec<?>> SLOTS = Map.of(
            "floor", FloorPatternEntry.CODEC,
            "wall", WallPatternEntry.CODEC,
            "ceiling", CeilingPatternEntry.CODEC,
            "pillars", PillarPatternEntry.CODEC,
            "platforms", PlatformPatternEntry.CODEC,
            "pots", PotConfig.CODEC);

    /** The motif-level material sections, which carry a block and an optional {@code pattern}. */
    private static final Map<String, com.mojang.serialization.Codec<?>> SECTIONS = Map.of(
            "floor", FloorConfig.CODEC,
            "wall", WallConfig.CODEC,
            "ceiling", CeilingConfig.CODEC);

    /**
     * Which codec an object under {@code name} should be read with, or null if it is neither.
     *
     * <p>A motif section is recognised by its own required block key &mdash; {@code base} for a
     * floor, {@code wall} for a wall, {@code ceiling} for a ceiling. Everything else under those
     * names is a scheme slot. Getting this right matters twice over: it is also what puts the
     * appendix's {@code pattern} examples through {@code FloorConfig}/{@code WallConfig}/{@code
     * CeilingConfig} rather than through the slot codecs, which would reject them.</p>
     */
    private static com.mojang.serialization.Codec<?> codecFor(String name, JsonObject body) {
        if (SECTIONS.containsKey(name)) {
            boolean section = switch (name) {
                case "floor" -> body.has("base");
                case "wall" -> body.has("wall");
                default -> body.has("ceiling");
            };
            if (section) {
                return SECTIONS.get(name);
            }
        }
        return SLOTS.get(name);
    }

    /**
     * The manual elides some examples with a {@code "..."} key, to show a shape without the noise.
     * Those are illustrations, not things to copy, and they are not expected to decode.
     */
    private static boolean isElided(JsonObject object) {
        return object.keySet().stream().anyMatch(key -> key.contains("..."));
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everySlotShownInTheManualDecodes() {
        Assumptions.assumeTrue(Files.exists(MANUAL),
                "the Room Schemes Manual is not present at " + MANUAL + " -- skipping");

        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (JsonElement block : jsonBlocks()) {
            checked += check(block, failures);
        }
        assertTrue(checked >= 20,
                "expected the manual's examples, decoded only " + checked + " slot(s)");
        if (!failures.isEmpty()) {
            fail(failures.size() + " example(s) in the Room Schemes Manual no longer decode."
                    + " The manual is what pack authors copy from:\n  "
                    + String.join("\n  ", failures));
        }
    }

    /** Decodes every slot object anywhere in {@code element}; returns how many it found. */
    private static int check(JsonElement element, List<String> failures) {
        int found = 0;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                found += check(child, failures);
            }
            return found;
        }
        if (!element.isJsonObject()) {
            return 0;
        }
        JsonObject object = element.getAsJsonObject();
        if (isElided(object)) {
            return 0;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            // A slot is an OBJECT. `"floor": "minecraft:packed_mud"` inside `door` or `corridor` is
            // a block id that happens to share the name.
            com.mojang.serialization.Codec<?> codec = entry.getValue().isJsonObject()
                    ? codecFor(entry.getKey(), entry.getValue().getAsJsonObject())
                    : null;
            if (codec != null && !isElided(entry.getValue().getAsJsonObject())) {
                DataResult<?> result = codec.parse(JsonOps.INSTANCE, entry.getValue());
                if (result.error().isPresent()) {
                    failures.add(entry.getKey() + ": " + result.error().orElseThrow().message()
                            + "\n      " + abbreviate(entry.getValue()));
                }
                found++;
            } else {
                found += check(entry.getValue(), failures);
            }
        }
        return found;
    }

    private static String abbreviate(JsonElement element) {
        String text = element.toString();
        return text.length() <= 160 ? text : text.substring(0, 160) + "...";
    }

    /** Every fenced {@code json} block that parses; the manual has a few deliberate fragments. */
    private static List<JsonElement> jsonBlocks() {
        List<String> lines;
        try {
            lines = Files.readAllLines(MANUAL);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + MANUAL, unreadable);
        }
        List<JsonElement> blocks = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).trim().equals("```json")) {
                continue;
            }
            int end = i + 1;
            while (end < lines.size() && !lines.get(end).trim().equals("```")) {
                end++;
            }
            String body = String.join("\n", lines.subList(i + 1, end));
            // Whole object, or a fragment of one -- try both, and ignore what is neither. Some
            // blocks are deliberately elided with `...` and cannot parse at all; those are the
            // ones a reader is not meant to copy verbatim.
            for (String candidate : new String[] {body, "{" + body + "}"}) {
                try {
                    blocks.add(JsonParser.parseString(candidate));
                    break;
                } catch (RuntimeException notJson) {
                    // try the other shape
                }
            }
            i = end;
        }
        return blocks;
    }
}
