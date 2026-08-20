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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every sibling dependency's minimum version is the same in {@code build.gradle} and
 * {@code mods.toml}.
 *
 * <h2>Why this is worth a test</h2>
 * <p>The two files say the same thing for different audiences and neither can see the other.
 * {@code build.gradle} decides what this mod is <em>compiled and dev-run</em> against;
 * {@code mods.toml} decides what a <em>player's</em> game will accept. Raise one and forget the
 * other and nothing fails here &mdash; the build is green, the dev run is green, and the mismatch
 * only shows up as a player either loading a version too old to have the behaviour this mod now
 * relies on, or being told a perfectly good install is unsupported.</p>
 *
 * <p>This matters more than usual right now: Dungeons2 is developed alongside GottschCore, GMM and
 * the rest, so the floors get raised repeatedly as bugs are found and fixed in them, and the
 * raising is a two-file chore every time. 2026-08-20 was the first &mdash; GMM 1.1.0, where the
 * Shrieker and Violet Fungus became unpushable, which this mod's floor decoration depends on.</p>
 *
 * <h2>What it does not check</h2>
 * <p>Only the <em>floor</em>. The upper bound is a different judgement (the next major, usually) and
 * {@code build.gradle} often pins an exact version where {@code mods.toml} names a range, which is
 * correct: the build wants one artifact, the player's game wants a window.</p>
 */
class DependencyFloorsAgreeTest {

    /**
     * Maven artifact id &rarr; the mod id the same project registers itself under.
     *
     * <p>They are not the same string for every dependency, and there is no way to derive one from
     * the other &mdash; {@code monster-manual} publishes as {@code gmm}. A dependency missing from
     * here fails the test rather than being skipped, so adding one is not something that can be
     * quietly forgotten.</p>
     */
    private static final Map<String, String> ARTIFACT_TO_MOD_ID = Map.of(
            "gottschcore", "gottschcore",
            "dungeonblocks", "dungeonblocks",
            "monster-manual", "gmm",
            "treasure2", "treasure2");

    /** {@code fg.deobf("gottsch:<artifact>:<spec>")} on a line that is not commented out. */
    private static final Pattern GRADLE_DEP = Pattern.compile(
            "^[^/\\n]*fg\\.deobf\\(\"gottsch:([a-z0-9-]+):([^\"]+)\"\\)", Pattern.MULTILINE);

    /** A version, with the {@code 1.20.1-} Minecraft prefix build.gradle carries and toml does not. */
    private static final Pattern FLOOR = Pattern.compile("(?:\\d+\\.\\d+\\.\\d+-)?(\\d+\\.\\d+\\.\\d+)");

    @Test
    void everyDependencyFloorMatchesBetweenBuildGradleAndModsToml() throws IOException {
        Map<String, String> gradle = gradleFloors();
        Map<String, String> toml = tomlFloors();

        assertTrue(gradle.size() >= 4,
                "only found " + gradle.size() + " gottsch dependencies in build.gradle, so the"
                        + " pattern has stopped matching rather than the deps having gone away");

        for (Map.Entry<String, String> entry : gradle.entrySet()) {
            String artifact = entry.getKey();
            String modId = ARTIFACT_TO_MOD_ID.get(artifact);
            assertNotNull(modId, "build.gradle depends on gottsch:" + artifact + " but this test"
                    + " does not know its mod id -- add it to ARTIFACT_TO_MOD_ID so its floor is"
                    + " checked rather than silently skipped");

            String declared = toml.get(modId);
            assertNotNull(declared, "build.gradle depends on gottsch:" + artifact + " but mods.toml"
                    + " declares no dependency on '" + modId + "', so a player's game will not"
                    + " enforce it at all");

            assertEquals(entry.getValue(), declared,
                    "the minimum version of '" + modId + "' disagrees: build.gradle says "
                            + entry.getValue() + ", mods.toml says " + declared + ". Raising one"
                            + " and not the other means this mod is BUILT against a version its"
                            + " own toml would let a player run without");
        }
    }

    /** Artifact id &rarr; floor, from the uncommented {@code fg.deobf} lines. */
    private static Map<String, String> gradleFloors() throws IOException {
        Path path = Path.of("build.gradle");
        assertTrue(Files.exists(path),
                "build.gradle not found at " + path.toAbsolutePath() + "; this test reads it as a"
                        + " file because it is not a resource on the test classpath");
        Map<String, String> floors = new LinkedHashMap<>();
        Matcher matcher = GRADLE_DEP.matcher(Files.readString(path, StandardCharsets.UTF_8));
        while (matcher.find()) {
            // A range keeps its lower bound; a pinned version IS the floor.
            floors.put(matcher.group(1), floorOf(matcher.group(2)));
        }
        return floors;
    }

    /** Mod id &rarr; floor, from mods.toml's dependency blocks. */
    private static Map<String, String> tomlFloors() throws IOException {
        String toml;
        try (InputStream in = DependencyFloorsAgreeTest.class
                .getResourceAsStream("/META-INF/mods.toml")) {
            assertNotNull(in, "mods.toml is not on the test classpath");
            toml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, String> floors = new LinkedHashMap<>();
        // modId then, further down the same block, versionRange. Comments may sit between them, so
        // this pairs each modId with the NEXT versionRange rather than assuming adjacency.
        Matcher matcher = Pattern.compile(
                        "modId\\s*=\\s*\"([a-z0-9_-]+)\"(.*?)versionRange\\s*=\\s*\"([^\"]+)\"",
                        Pattern.DOTALL)
                .matcher(toml);
        while (matcher.find()) {
            String modId = matcher.group(1);
            // Only the sibling mods. forge and minecraft are declared here too, and their ranges
            // are NOT the two-part-safe shape this parses -- forge's is "[47,)". They are templated
            // as ${forge_version_range} in the source file, but this reads the resource off the
            // CLASSPATH, which is the processResources OUTPUT with every placeholder already
            // expanded. That is the right file to read (it is what ships) and the reason the
            // obvious "skip anything starting with ${" guard does not work.
            if (!ARTIFACT_TO_MOD_ID.containsValue(modId)) {
                continue;
            }
            floors.put(modId, floorOf(matcher.group(3)));
        }
        return floors;
    }

    /** The first three-part version in a spec, whether that spec is a range or a pin. */
    private static String floorOf(String spec) {
        Matcher matcher = FLOOR.matcher(spec);
        if (!matcher.find()) {
            return fail("could not read a version out of '" + spec + "'");
        }
        return matcher.group(1);
    }
}
