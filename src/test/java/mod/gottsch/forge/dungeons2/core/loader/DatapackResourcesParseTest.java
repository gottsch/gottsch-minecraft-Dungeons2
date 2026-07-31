/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the <em>shipped</em> datapack JSON (under {@code data/dungeons2/}) against
 * schema drift: every file must decode cleanly with its Codec. Pure POJO &mdash; block
 * ids are parsed as {@code ResourceLocation}, so no Minecraft bootstrap is required.
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
class DatapackResourcesParseTest {

    private static final Gson GSON = new Gson();

    private static <T> void assertParses(String resourcePath, Codec<T> codec) {
        try (InputStream in = DatapackResourcesParseTest.class.getResourceAsStream(resourcePath)) {
            assertTrue(in != null, "missing datapack resource on classpath: " + resourcePath);
            JsonElement json = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
            DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
            assertTrue(result.result().isPresent(),
                    "failed to decode " + resourcePath + ": " + result.error().map(Object::toString).orElse(""));
        } catch (Exception e) {
            throw new AssertionError("error reading " + resourcePath, e);
        }
    }

    @Test
    void motifConfigFilesDecode() {
        for (String motif : new String[]{"classic", "catacombs", "deep_slate"}) {
            assertParses("/data/dungeons2/dungeons2/motif_config/" + motif + ".json", MotifConfig.CODEC);
        }
    }

    // The shipped worldgen/processor_list file (weathering) is guarded by
    // WeatheringProcessorListTest instead -- decoding it needs BuiltInRegistries, and
    // this class deliberately stays Minecraft-bootstrap-free.
}
