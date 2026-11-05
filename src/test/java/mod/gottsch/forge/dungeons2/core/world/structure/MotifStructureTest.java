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
package mod.gottsch.forge.dungeons2.core.world.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dungeon's motif comes from its structure file, and the structure set's weights choose between
 * those files. Nothing else names them, so a structure left out of the locate tag, or a set entry
 * naming no structure, would otherwise fail quietly.
 *
 * @author Mark Gottschling on 11/01/2026
 */
class MotifStructureTest {

    private static final String STRUCTURES = "/data/dungeons2/worldgen/structure";
    private static final String SET = "/data/dungeons2/worldgen/structure_set/dungeons.json";
    private static final String TAG = "/data/dungeons2/tags/worldgen/structure/dungeons.json";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void classicStructureNamesNoMotifAndCatacombsNamesItsOwn() {
        assertFalse(read(STRUCTURES + "/dungeon.json").has("motif"),
                "dungeon.json relies on the codec's default of classic");
        assertEquals("catacombs", read(STRUCTURES + "/catacombs.json").get("motif").getAsString());
    }

    @Test
    void anUnknownMotifIsAnErrorRatherThanAQuietFallback() {
        assertTrue(DungeonStructure.validMotif("catacombs").result().isPresent());
        assertFalse(DungeonStructure.validMotif("catacomb").result().isPresent());
        assertFalse(DungeonStructure.validMotif("unknown").result().isPresent());
    }

    @Test
    void everyStructureIsInTheLocateTag() throws Exception {
        Set<String> tagged = new HashSet<>();
        read(TAG).getAsJsonArray("values").forEach(value -> tagged.add(value.getAsString()));
        assertEquals(structureIds(), tagged, "the locate tag should list exactly the shipped structures");
    }

    @Test
    void everyStructureSetEntryNamesAShippedStructure() throws Exception {
        Set<String> shipped = structureIds();
        for (JsonElement entry : read(SET).getAsJsonArray("structures")) {
            String id = entry.getAsJsonObject().get("structure").getAsString();
            assertTrue(shipped.contains(id), id + " is in the structure set but has no structure file");
        }
    }

    private static Set<String> structureIds() throws Exception {
        Path dir = Paths.get(MotifStructureTest.class.getResource(STRUCTURES).toURI());
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(file -> "dungeons2:" + file.getFileName().toString().replace(".json", ""))
                    .collect(Collectors.toSet());
        }
    }

    private static JsonObject read(String resource) {
        InputStream stream = MotifStructureTest.class.getResourceAsStream(resource);
        assertNotNull(stream, "missing " + resource);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
