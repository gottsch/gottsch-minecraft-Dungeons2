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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.neo.dungeons2.diagnostic.ProjectFiles;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.loot.LootTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every shipped loot table decodes with the game's own {@link LootTable#DIRECT_CODEC}.
 *
 * <p>The other loot tests read the JSON by hand, for what it names. None of them asked the game
 * whether it would <em>load</em> the table, and on the NeoForge port seven tables did not: Forge's
 * {@code minecraft:set_nbt} (the Gottsch Books entries) and a {@code loot_table} entry's
 * {@code name} (now {@code value}) are both gone in 1.21. A table that fails to load is logged once
 * at datapack load and then rolls nothing, so the chest is simply empty &mdash; found by a player,
 * not by the build.</p>
 *
 * <p>The ops carry {@link VanillaRegistries#createLookup()}, so item, enchantment and component ids
 * resolve; this mod's own functions and conditions are registered because the unit-test runner
 * loads the mod.</p>
 *
 * @author Mark Gottschling on 11/02/2026
 */
class ShippedLootTablesDecodeTest {

    private static final Path ROOT = ProjectFiles.path("src/main/resources/data/dungeons2/loot_table");

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void lookup() {
        ops = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
    }

    @Test
    void everyShippedLootTableDecodes() throws IOException {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                decode(json).error().ifPresent(error -> bad.add(ROOT.relativize(file) + ": " + error.message()));
                checked++;
            }
        }
        assertTrue(checked > 0, "no loot tables found under " + ROOT);
        if (!bad.isEmpty()) {
            fail(bad.size() + " of " + checked + " shipped loot table(s) would not load, so they roll"
                    + " nothing:\n  " + String.join("\n  ", bad));
        }
    }

    /** The guard on the guard: a pre-1.21 function must be rejected, or the check above proves nothing. */
    @Test
    void aForgeEraFunctionIsRejected() {
        JsonElement legacy = JsonParser.parseString("""
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:stone",
                  "functions":[{"function":"minecraft:set_nbt","tag":"{}"}]}]}]}""");
        assertTrue(decode(legacy).error().isPresent(), "set_nbt decoded -- the codec is not being exercised");
    }

    private static DataResult<LootTable> decode(JsonElement json) {
        return LootTable.DIRECT_CODEC.parse(ops, json);
    }
}
