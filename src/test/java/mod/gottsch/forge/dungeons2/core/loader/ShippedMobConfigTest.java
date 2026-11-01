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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.gmm.core.config.MobConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sweeps {@code data/dungeons2/gmm/mob_config/*.json}, the per-mob tuning gmm reads.
 *
 * <h2>Why this needs a test at all</h2>
 * <p>A mob config fails <strong>completely silently</strong>, in both directions.
 * {@code MobConfigHelper.get} returns {@link MobConfig#DEFAULT} when no entry matches, so a file
 * whose name does not match a registered entity id simply never applies and the mob behaves exactly
 * as it would with no file &mdash; which is also exactly how a correct file looks if you only test
 * by playing. And {@code MobConfig.number(key, default)} falls back per key, so a misspelt property
 * is one silently ignored line in an otherwise working file. Neither is a crash, a log line, or a
 * load error.</p>
 *
 * <p>The registry is keyed by the mob's {@code EntityType} id, and a file at
 * {@code data/<namespace>/gmm/mob_config/<name>.json} registers as {@code <namespace>:<name>}. So
 * the file name IS the entity name, and checking it against the mod's own registrations is the
 * whole of the first test.</p>
 *
 * <p>Read out of {@code DungeonsEntities} as <strong>text</strong>, the same trick
 * {@code MobSpawnExclusionTest} uses: loading the registration class headlessly means constructing
 * its {@code DeferredRegister} entries, which is the kind of thing that works until it does not.</p>
 *
 * @author Mark Gottschling on Sep 3, 2026
 */
class ShippedMobConfigTest {

    private static final String MOB_CONFIG = "/data/dungeons2/gmm/mob_config";
    private static final String ENTITIES_SOURCE =
            "src/main/java/mod/gottsch/forge/dungeons2/core/entity/DungeonsEntities.java";

    /** A file named after nothing is tuning that never applies, and nothing anywhere says so. */
    @Test
    void everyConfigIsNamedAfterARegisteredEntity() {
        String source = sourceOf(ENTITIES_SOURCE);
        List<String> orphans = new ArrayList<>();
        for (Path file : configFiles()) {
            String name = file.getFileName().toString().replace(".json", "");
            // The registration constants are `public static final String BODAK = "bodak";`, so the
            // quoted name appearing in the source is the check. Deliberately loose: it proves the
            // name is one this mod knows, without needing a populated Forge registry.
            if (!source.contains("\"" + name + "\"")) {
                orphans.add(name);
            }
        }
        assertTrue(orphans.isEmpty(), "a gmm mob_config names an entity DungeonsEntities does not"
                + " register, so it silently applies to nothing: " + orphans);
    }

    /** It must actually decode, or gmm drops the whole entry and every knob falls back. */
    @Test
    void everyConfigDecodes() {
        List<String> broken = new ArrayList<>();
        for (Path file : configFiles()) {
            MobConfig.CODEC.parse(JsonOps.INSTANCE, read(file))
                    .error().ifPresent(error -> broken.add(file.getFileName() + ": " + error.message()));
        }
        assertTrue(broken.isEmpty(), "a gmm mob_config does not decode: " + broken);
    }

    /**
     * The mini-bosses are spawner-only, and this is the second lock on that.
     *
     * <p>{@code MobConfig.SpawnSettings} defaults {@code enabled} to <em>true</em>, so a config that
     * simply omits the block would opt a mini-boss into natural spawning the day Dungeons2 grows a
     * spawn predicate that reads it. Nothing reads it today, which is precisely why this has to be
     * asserted rather than noticed: the field is inert now and load-bearing later.</p>
     */
    @Test
    void aMiniBossConfigDoesNotEnableNaturalSpawning() {
        List<String> enabled = new ArrayList<>();
        for (Path file : configFiles()) {
            String name = file.getFileName().toString().replace(".json", "");
            if (!MINI_BOSSES.contains(name)) {
                continue;
            }
            JsonObject json = read(file).getAsJsonObject();
            boolean spawns = !json.has("spawn")
                    || !json.getAsJsonObject("spawn").has("enabled")
                    || json.getAsJsonObject("spawn").get("enabled").getAsBoolean();
            if (spawns) {
                enabled.add(name);
            }
        }
        assertTrue(enabled.isEmpty(), "a mini-boss config leaves natural spawning enabled (the"
                + " default when the key is absent); mini-bosses are placed, never spawned: " + enabled);
    }

    /** Kept in step with {@code MobSpawnExclusionTest} by that test's own list-parity check. */
    private static final List<String> MINI_BOSSES =
            List.of("skeleton_champion", "wight", "bodak", "beholder", "death_tyrant", "daemon");

    /** The directory is allowed to be empty, but not to vanish -- that would silence every test above. */
    @Test
    void theDirectoryExists() {
        assertFalse(configFiles().isEmpty(),
                "no gmm mob_config files at all; if the last one was deleted deliberately, this"
                        + " test should go with it");
    }

    private static List<Path> configFiles() {
        try {
            Path dir = Paths.get(ShippedMobConfigTest.class.getResource(MOB_CONFIG).toURI());
            try (Stream<Path> files = Files.walk(dir)) {
                return files.filter(f -> f.toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::toString)).toList();
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not list " + MOB_CONFIG, e);
        }
    }

    private static JsonElement read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + file, e);
        }
    }

    private static String sourceOf(String path) {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("could not read " + path, e);
        }
    }
}
