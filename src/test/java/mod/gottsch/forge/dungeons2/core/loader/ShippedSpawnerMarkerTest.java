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

import mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.SpawnerMarkerProcessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Backlog #10's content sweep, over the shipped {@code .nbt} templates.
 *
 * <p>Two invariants, and the second is the one that was learned the expensive way.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
class ShippedSpawnerMarkerTest {

    private static final String STRUCTURES = "/data/dungeons2/structures";
    private static final String STRUCTURE_BLOCK = "minecraft:structure_block";

    /**
     * The spawner has a live consumer. Without this the whole #10 path could rot silently: every
     * other test would still pass with no template exercising it, which is exactly the state the
     * feature was in on the day it was written.
     */
    @Test
    void atLeastOneShippedTemplateCarriesTheMarkerBlock() {
        String marker = SpawnerMarkerProcessor.DEFAULT_MARKER_BLOCK.toString();
        Set<String> hosts = new LinkedHashSet<>();
        for (Path template : templates()) {
            if (palette(template).contains(marker)) {
                hosts.add(template.getFileName().toString());
            }
        }
        assertFalse(hosts.isEmpty(),
                "no shipped template carries a " + marker + " block, so nothing in the game ever"
                        + " exercises the spawner");
    }

    /**
     * <strong>No shipped template may contain a DATA structure block at all.</strong>
     *
     * <p>Every authored Dungeons2 template is placed as a jigsaw pool element, and
     * {@code SinglePoolElement.getSettings} installs {@code BlockIgnoreProcessor.STRUCTURE_BLOCK}
     * ahead of the pool's own processors &mdash; which <em>removes</em> the block rather than
     * replacing it. The cell is then never written and the terrain the dungeon was carved out of
     * shows through: a stone or ore block sitting in the middle of a finished room. That is not a
     * marker that fails to fire, it is a hole in the room, and it is silent.</p>
     *
     * <p>So a DATA structure block is never useful here regardless of what it says, which makes
     * "none at all" the honest rule rather than an allowlist of marker strings. See
     * {@code JigsawStripsStructureBlocksTest} for the mechanism, and the DATA-marker section of
     * {@code structures/README.md}.</p>
     */
    @Test
    void noShippedTemplateContainsADataStructureBlock() {
        List<String> offenders = new ArrayList<>();
        for (Path template : templates()) {
            CompoundTag root = read(template);
            for (Tag blockTag : root.getList("blocks", Tag.TAG_COMPOUND)) {
                CompoundTag block = (CompoundTag) blockTag;
                if (!block.contains("nbt")) {
                    continue;
                }
                CompoundTag data = block.getCompound("nbt");
                if (STRUCTURE_BLOCK.equals(data.getString("id"))) {
                    offenders.add(template.getFileName() + " mode=" + data.getString("mode")
                            + " metadata='" + data.getString("metadata") + "'");
                }
            }
        }
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " shipped template(s) contain a structure block. A jigsaw pool"
                    + " element strips these before any processor sees them, leaving the cell"
                    + " unwritten and the surrounding terrain visible inside the room:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    /** The checks above pass vacuously if nothing is being read. */
    @Test
    void theSweepFindsTheShippedTemplates() {
        assertFalse(templates().isEmpty(), "no shipped templates found at " + STRUCTURES);
    }

    // ---------- reading ----------

    private static Set<String> palette(Path template) {
        Set<String> names = new LinkedHashSet<>();
        CompoundTag root = read(template);
        for (Tag entry : root.getList("palette", Tag.TAG_COMPOUND)) {
            names.add(((CompoundTag) entry).getString("Name"));
        }
        return names;
    }

    private static CompoundTag read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read " + file, unreadable);
        }
    }

    private static List<Path> templates() {
        URL url = ShippedSpawnerMarkerTest.class.getResource(STRUCTURES);
        if (url == null) {
            return fail("no shipped content at " + STRUCTURES);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + STRUCTURES + ": " + unreadable);
        }
    }
}
