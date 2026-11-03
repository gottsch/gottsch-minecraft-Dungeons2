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
package mod.gottsch.neo.dungeons2.core.loader;

import mod.gottsch.neo.dungeons2.diagnostic.ProjectFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every sibling dependency's minimum version is the same for the build and for a player's game.
 *
 * <h2>Why this is worth a test</h2>
 * <p>The two say the same thing for different audiences and neither can see the other. The build
 * decides what this mod is <em>compiled and dev-run</em> against; {@code neoforge.mods.toml} decides
 * what a <em>player's</em> game will accept. Raise one and forget the other and nothing fails here
 * &mdash; the build is green, the dev run is green, and the mismatch only shows up as a player either
 * loading a version too old to have the behaviour this mod now relies on, or being told a perfectly
 * good install is unsupported.</p>
 *
 * <p>This matters more than usual right now: Dungeons2 is developed alongside GottschCore, GMM and
 * the rest, so the floors get raised repeatedly as bugs are found and fixed in them, and the
 * raising is a two-place chore every time. 2026-08-20 was the first &mdash; GMM 1.1.0, where the
 * Shrieker and Violet Fungus became unpushable, which this mod's floor decoration depends on.</p>
 *
 * <h2>Where the two live on NeoForge</h2>
 * <p>On Forge this compared {@code build.gradle}'s {@code fg.deobf} lines with {@code mods.toml}.
 * On NeoForge both are fed from {@code gradle.properties}: {@code build.gradle} depends on
 * {@code <name>_version} and the toml template expands {@code <name>_version_range}. So the pair to
 * keep in step is those two properties, and this also checks that the build and the shipped toml
 * really do read them, rather than having a version written in by hand.</p>
 *
 * <h2>What it does not check</h2>
 * <p>Only the <em>floor</em>. The upper bound is a different judgement (the next major, usually) and
 * the build pins an exact version where the toml names a range, which is correct: the build wants
 * one artifact, the player's game wants a window.</p>
 */
class DependencyFloorsAgreeTest {

    /**
     * {@code gradle.properties} prefix &rarr; the mod id the same project registers itself under.
     *
     * <p>They are not the same string for every dependency &mdash; {@code monster-manual} publishes
     * as {@code gmm}. A sibling property pair missing from here fails the test rather than being
     * skipped, so adding one is not something that can be quietly forgotten.</p>
     */
    private static final Map<String, String> PREFIX_TO_MOD_ID = Map.of(
            "gottschcore", "gottschcore",
            "dungeonblocks", "dungeonblocks",
            "gmm", "gmm",
            "gottschbooks", "gottschbooks",
            "treasure2", "treasure2",
            "eechelonsapi", "eechelonsapi");

    /** Not sibling mods: the platform, and this mod's own version. */
    private static final Set<String> NOT_SIBLINGS =
            Set.of("minecraft", "neo", "loader", "mod", "parchment_minecraft");

    /** The first version in a spec, two or three parts, whether that spec is a range or a pin. */
    private static final Pattern FLOOR = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    @Test
    void everyDependencyFloorMatchesBetweenTheBuildAndTheModsToml() throws IOException {
        Properties properties = gradleProperties();
        String buildGradle = Files.readString(ProjectFiles.path("build.gradle"), StandardCharsets.UTF_8);
        Map<String, String> toml = tomlRanges();

        Map<String, String> pairs = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.endsWith("_version_range")) {
                String prefix = key.substring(0, key.length() - "_version_range".length());
                if (!NOT_SIBLINGS.contains(prefix)) {
                    pairs.put(prefix, properties.getProperty(prefix + "_version"));
                }
            }
        }
        assertTrue(pairs.size() >= 4,
                "only found " + pairs.size() + " sibling version pairs in gradle.properties, so the"
                        + " naming has changed rather than the deps having gone away: " + pairs.keySet());

        for (Map.Entry<String, String> pair : pairs.entrySet()) {
            String prefix = pair.getKey();
            String modId = PREFIX_TO_MOD_ID.get(prefix);
            assertNotNull(modId, "gradle.properties has " + prefix + "_version_range but this test"
                    + " does not know which mod id that is -- add it to PREFIX_TO_MOD_ID");
            assertNotNull(pair.getValue(), "gradle.properties has " + prefix + "_version_range but no "
                    + prefix + "_version for the build to use");

            String range = properties.getProperty(prefix + "_version_range");
            assertEquals(floorOf(pair.getValue()), floorOf(range),
                    "the minimum version of '" + modId + "' disagrees: the build uses "
                            + pair.getValue() + ", the toml allows " + range + ". Raising one and not"
                            + " the other means this mod is BUILT against a version its own toml"
                            + " would let a player run without");

            assertTrue(buildGradle.contains("${" + prefix + "_version}"),
                    "build.gradle does not depend on ${" + prefix + "_version}, so the build is not"
                            + " using the version this test checked");
            assertEquals(range, toml.get(modId),
                    "neoforge.mods.toml does not declare '" + modId + "' with " + prefix
                            + "_version_range, so a player's game will not enforce it");
        }
    }

    private static Properties gradleProperties() throws IOException {
        Path path = ProjectFiles.path("gradle.properties");
        assertTrue(Files.exists(path), "gradle.properties not found at " + path.toAbsolutePath());
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    /**
     * Mod id &rarr; versionRange, from the shipped toml's dependency blocks. Read off the classpath,
     * which is the expanded OUTPUT of the template, because that is what ships.
     */
    private static Map<String, String> tomlRanges() throws IOException {
        String toml;
        try (InputStream in = DependencyFloorsAgreeTest.class
                .getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertNotNull(in, "neoforge.mods.toml is not on the test classpath");
            toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, String> ranges = new LinkedHashMap<>();
        // modId then, further down the same block, versionRange. Comments may sit between them, so
        // this pairs each modId with the NEXT versionRange rather than assuming adjacency.
        Matcher matcher = Pattern.compile(
                        "modId\\s*=\\s*\"([a-z0-9_-]+)\"(.*?)versionRange\\s*=\\s*\"([^\"]+)\"",
                        Pattern.DOTALL)
                .matcher(toml);
        while (matcher.find()) {
            ranges.put(matcher.group(1), matcher.group(3));
        }
        return ranges;
    }

    /** The first version in a spec as three parts, so {@code [1.3,2.0)} and {@code 1.3.0} agree. */
    private static String floorOf(String spec) {
        Matcher matcher = FLOOR.matcher(spec);
        if (!matcher.find()) {
            return fail("could not read a version out of '" + spec + "'");
        }
        String patch = matcher.group(3) == null ? "0" : matcher.group(3);
        return matcher.group(1) + "." + matcher.group(2) + "." + patch;
    }
}
