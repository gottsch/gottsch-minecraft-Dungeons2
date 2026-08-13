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

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The other half of backlog #13: <strong>the block palettes inside the shipped {@code .nbt}
 * templates</strong>.
 *
 * <h2>The blind spot this closes</h2>
 * <p>{@link ShippedBlockIdsTest} sweeps every shipped <em>JSON</em> file, and that is all it can do
 * &mdash; a structure template is gzipped NBT. #26 already caught this from the other end: the
 * "nothing authors this block" analysis that declared six aging rules dead had consulted the JSON
 * and not the palettes, and three of the six turned out to be placed by prefabs. <em>"Nothing
 * authors this block" is a claim about two sources, and one of them is binary.</em></p>
 *
 * <h2>Why bedrock gets its own test</h2>
 * <p>Found in game on Aug 13 2026: a room-shaped mass of {@code minecraft:bedrock} inside a
 * dungeon, which turned out to be the dead quadrant of {@code 11x11_corner_2} &mdash; authored as
 * bedrock while blocking the room out, and never swapped for {@code structure_void}. It placed
 * verbatim. Ten of the shipped templates carried it, 145 cells of it in the one dungeon that was
 * walked.
 *
 * <p>Bedrock is the natural thing to block out with (it is unmistakable in a creative world and
 * nothing else uses it), which is exactly what makes it dangerous: it looks deliberate right up
 * until it ships. Unlike a misspelled id, which quietly becomes air and does nothing, this one is
 * <strong>loud in the world and silent in every log</strong> &mdash; no codec sees it, no registry
 * lookup fails, and the only way to find it is to stand next to it. A test is the only affordance
 * that can catch it before a player does.</p>
 *
 * <p>{@code structure_void} is the intended stand-in: it means <em>leave whatever terrain is
 * here</em>, which is what a walled-off quadrant wants. Note the one place the two are genuinely
 * not equivalent &mdash; bedrock is solid unconditionally, {@code structure_void} is only as solid
 * as the terrain it leaves, so where a cave cuts through, it leaves the cave. That is #38's lesson
 * ("an unbuilt slot is not a hole, it is untouched terrain &mdash; until the terrain is a cave")
 * and it applies to any {@code structure_void} fill authored below cave depth.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
class ShippedTemplateBlocksTest {

    private static final String TEMPLATE_ROOT = "/data/dungeons2/structures";

    /** Where a modded block proves it exists without a running game &mdash; see {@link ShippedBlockIdsTest}. */
    private static final String BLOCKSTATE_DIR = "/assets/%s/blockstates/%s.json";

    /**
     * Blocks that only ever mean "I was still drawing this". Kept as a set rather than a single
     * check because the next one somebody reaches for (a wool marker, a glass shell) belongs here
     * too, and adding it should be one line.
     */
    private static final Set<String> BLOCKOUT_PLACEHOLDERS = Set.of("minecraft:bedrock");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** One palette entry, kept with the file it came from so the failure is actionable. */
    private record PaletteEntry(String file, String block) {
        @Override
        public String toString() {
            return file + " -> " + block;
        }
    }

    // ---------- the sweeps ----------

    @Test
    void noShippedTemplateContainsABlockoutPlaceholder() {
        List<String> offenders = new ArrayList<>();
        for (PaletteEntry entry : sweep()) {
            if (BLOCKOUT_PLACEHOLDERS.contains(entry.block())) {
                offenders.add(entry.toString());
            }
        }
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " shipped template(s) still contain a block-out placeholder."
                    + " These place verbatim into the world and nothing logs it -- swap for"
                    + " minecraft:structure_void (leave the terrain) or a real block:\n  "
                    + String.join("\n  ", offenders));
        }
    }

    @Test
    void everyTemplatePaletteBlockExists() {
        List<String> bad = new ArrayList<>();
        for (PaletteEntry entry : sweep()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.block());
            if (id == null) {
                bad.add(entry + "  (not a valid resource location)");
            } else if (!exists(id)) {
                bad.add(entry + "  (no such block)");
            }
        }
        if (!bad.isEmpty()) {
            fail("shipped templates name " + bad.size() + " block(s) that do not exist:\n  "
                    + String.join("\n  ", bad));
        }
    }

    /** Both sweeps above pass vacuously if the templates are not being found or not being read. */
    @Test
    void theSweepFindsTheShippedTemplates() {
        List<PaletteEntry> all = sweep();
        Set<String> files = new LinkedHashSet<>();
        all.forEach(entry -> files.add(entry.file()));
        assertTrue(files.size() >= 10,
                "expected the shipped template set, read " + files.size() + " file(s)");
        assertTrue(all.stream().anyMatch(entry -> entry.block().startsWith("dungeonblocks:")),
                "expected at least one dungeonblocks block in a palette -- the prefabs are full of"
                        + " them, so finding none means the palettes are not being read");
        assertTrue(all.stream().anyMatch(entry -> entry.block().equals("minecraft:structure_void")),
                "expected structure_void in at least one palette -- it is what the block-out fills"
                        + " were swapped to, and its absence would mean this sweep reads nothing");
    }

    // ---------- reading the binary templates ----------

    private static List<PaletteEntry> sweep() {
        List<PaletteEntry> found = new ArrayList<>();
        for (Path file : templateFiles()) {
            CompoundTag root = read(file);
            String name = file.getFileName().toString();
            // Single-palette templates carry "palette"; ones saved with rotation variants carry
            // "palettes", a list of them. Both shapes ship, so both are swept.
            collect(root.getList("palette", Tag.TAG_COMPOUND), name, found);
            for (Tag palette : root.getList("palettes", Tag.TAG_LIST)) {
                collect((ListTag) palette, name, found);
            }
        }
        return found;
    }

    private static void collect(ListTag palette, String file, List<PaletteEntry> out) {
        for (int i = 0; i < palette.size(); i++) {
            out.add(new PaletteEntry(file, palette.getCompound(i).getString("Name")));
        }
    }

    private static CompoundTag read(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return NbtIo.readCompressed(in);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("could not read template " + file, unreadable);
        }
    }

    private static boolean exists(ResourceLocation id) {
        if ("minecraft".equals(id.getNamespace())) {
            return BuiltInRegistries.BLOCK.containsKey(id);
        }
        return ShippedTemplateBlocksTest.class.getResource(
                String.format(BLOCKSTATE_DIR, id.getNamespace(), id.getPath())) != null;
    }

    private static List<Path> templateFiles() {
        URL url = ShippedTemplateBlocksTest.class.getResource(TEMPLATE_ROOT);
        if (url == null) {
            return fail("no shipped templates at " + TEMPLATE_ROOT);
        }
        try (Stream<Path> paths = Files.walk(Paths.get(url.toURI()))) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .sorted()
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            return fail("could not walk " + TEMPLATE_ROOT + ": " + unreadable);
        }
    }
}
