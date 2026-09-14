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
package mod.gottsch.forge.dungeons2.core.setup;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every entity type this mod registers has a renderer.
 *
 * <p>An entity type with no renderer is a crash, but only on the <strong>client</strong>, only
 * the first time one comes into <strong>view</strong> &mdash; {@code EntityRenderDispatcher
 * .shouldRender} NPEs on the null. A server never notices, and nothing headless can see it. Five
 * spell projectiles shipped that way on 2026-09-04 and were found six days later, when a Beholder
 * cast its first Paralysis bolt at a player.</p>
 *
 * <p>Read as <em>source text</em> because it cannot be done any other way here: loading
 * {@code DungeonsEntities} initialises its {@code RegistryObject}s against Forge registries that do
 * not exist in a test (see {@code ShippedLangCoverageTest}'s class note), and {@code ClientSetup}
 * is client-only. The two patterns below are the only shapes either file uses.</p>
 *
 * @author Mark Gottschling on Sep 10, 2026
 */
class RendererCoverageTest {

    private static final Path SOURCES = Paths.get("src/main/java/mod/gottsch/forge/dungeons2/core");
    private static final Pattern REGISTERED = Pattern.compile("RegistryObject<EntityType<[^>]+>>\\s+(\\w+)\\s*=");
    private static final Pattern RENDERED = Pattern.compile("registerEntityRenderer\\(\\s*DungeonsEntities\\.(\\w+)\\.get\\(\\)");

    @Test
    void everyRegisteredEntityTypeHasARenderer() throws IOException {
        Set<String> registered = matches(REGISTERED, SOURCES.resolve("entity/DungeonsEntities.java"));
        Set<String> rendered = matches(RENDERED, SOURCES.resolve("setup/ClientSetup.java"));
        assertTrue(registered.size() > 40, "expected the whole roster, found only " + registered
                + " -- has the declaration shape in DungeonsEntities changed?");

        Set<String> missing = new TreeSet<>(registered);
        missing.removeAll(rendered);
        assertTrue(missing.isEmpty(), missing.size() + " entity type(s) have no renderer and will crash"
                + " the client the first time one is on screen: " + missing);
    }

    private static Set<String> matches(Pattern pattern, Path file) throws IOException {
        Matcher m = pattern.matcher(Files.readString(file, StandardCharsets.UTF_8));
        Set<String> names = new LinkedHashSet<>();
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
