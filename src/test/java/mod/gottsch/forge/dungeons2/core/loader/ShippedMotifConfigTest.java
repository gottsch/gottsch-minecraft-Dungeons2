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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigFragment;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <strong>Every shipped motif-config fragment must decode through the real codec.</strong>
 *
 * <h2>The gap this closes</h2>
 * <p>{@code ShippedBlockIdsTest} sweeps these files for block <em>ids</em> and
 * {@code MotifConfigCodecTest} exercises the codec against hand-written JSON, but until now
 * <strong>nothing put the two together</strong> &mdash; no test had ever decoded a file this mod
 * actually ships. A shipped fragment could be malformed, name a pattern type that is not
 * registered, or carry a key the closed schema rejects, and the whole build would stay green; the
 * failure would surface as a datapack load error in game, which is the slowest possible place to
 * find it.</p>
 *
 * <p>Written 2026-08-26 alongside the floor-pattern registry, which changed the authored shape of
 * {@code strata.json}'s {@code pattern} slot from a bare {@code type} word to a registry id with
 * its arguments nested under {@code config}. That migration had no test that could fail.</p>
 *
 * <p>Comments are read the way the game reads them: these files carry {@code //} notes, and a
 * lenient {@link JsonReader} skips them &mdash; the same leniency vanilla's own resource loading
 * applies. If that ever stopped being true, every shipped fragment would fail to parse in game,
 * and this test would say so first.</p>
 */
class ShippedMotifConfigTest {

    private static final String CONFIG_ROOT = "/data/dungeons2/dungeons2/motif_config";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyShippedFragmentDecodes() {
        List<String> failures = new ArrayList<>();
        int decoded = 0;
        for (Path file : fragments()) {
            DataResult<MotifConfigFragment> result =
                    MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, read(file));
            if (result.error().isPresent()) {
                failures.add(file.getFileName() + ": " + result.error().orElseThrow().message());
            } else {
                decoded++;
            }
        }
        assertTrue(decoded + failures.size() >= 3,
                "expected the shipped motif configs, found " + (decoded + failures.size()));
        if (!failures.isEmpty()) {
            fail(failures.size() + " shipped motif-config fragment(s) do not decode. These load as"
                    + " datapack errors in game and nothing else in the build catches them:\n  "
                    + String.join("\n  ", failures));
        }
    }

    /**
     * <strong>Two shipped strata must resolve to two DIFFERENT pattern providers.</strong>
     *
     * <p>This is the registry's real end-to-end check, and it is here rather than left to someone
     * walking a dungeon. With only one type ever authored, every test in the build would still pass
     * if the dispatch were quietly hard-wired to it &mdash; nothing would distinguish "looked the
     * id up" from "always returns speckle". Two distinct types decoded out of one shipped file
     * cannot both be a hard-wire.</p>
     *
     * <p>It asserts <em>distinctness</em>, not which two, so retuning a band's look does not break
     * it. Dropping to a single paved band would.</p>
     */
    @Test
    void twoShippedStrataResolveToTwoDifferentPatternProviders() {
        Path strata = fragments().stream()
                .filter(path -> path.getFileName().toString().equals("strata.json"))
                .findFirst()
                .orElseGet(() -> fail("classic/strata.json is not shipped any more"));

        MotifConfigFragment fragment = MotifConfigFragment.CODEC
                .parse(JsonOps.INSTANCE, read(strata))
                .getOrThrow(false, message -> fail("strata.json does not decode: " + message));

        assertTrue(fragment.strataByFloorIndex().isPresent(),
                "strata.json should carry strataByFloorIndex");

        List<String> patterns = fragment.strataByFloorIndex().orElseThrow().stream()
                .map(band -> band.floor().flatMap(floor -> floor.pattern()))
                .flatMap(Optional::stream)
                .map(entry -> entry.pattern().getClass().getSimpleName())
                .toList();

        assertTrue(patterns.size() >= 2,
                "expected at least two paved strata, found " + patterns.size()
                        + ". One is not enough to prove the registry dispatches on the id.");
        assertEquals(patterns.size(), Set.copyOf(patterns).size(),
                "the paved strata must use DIFFERENT pattern types, but all resolved to the same"
                        + " class: " + patterns);
    }

    private static JsonElement read(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            // Lenient, so the `//` notes these files carry are skipped rather than being a parse
            // error -- see the class doc.
            JsonReader json = new JsonReader(reader);
            json.setLenient(true);
            return JsonParser.parseReader(json);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static List<Path> fragments() {
        URL url = ShippedMotifConfigTest.class.getResource(CONFIG_ROOT);
        if (url == null) {
            return fail("no shipped motif configs at " + CONFIG_ROOT);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + CONFIG_ROOT + ": " + unreadable);
        }
    }
}
