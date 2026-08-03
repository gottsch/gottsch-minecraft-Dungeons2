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
package mod.gottsch.forge.dungeons2.diagnostic;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigFragment;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads a shipped motif off the classpath the same way {@code MotifConfigHelper} assembles it from
 * the datapack registry in game &mdash; every fragment under the motif's folder, in id order, folded
 * through {@link MotifConfigFragment#resolve}.
 *
 * <p>Test-side only. It exists because the two things that most want to read the real shipped
 * content &mdash; the incidence test and the floor-plan viewer &mdash; are both outside a running
 * server and so have no registry access. Reading the JSON directly means neither one drifts from
 * what a player gets: add a scheme file and both pick it up with no code change.</p>
 *
 * <p>Both authoring layouts are handled: a folder ({@code motif_config/classic/*.json}, which is
 * what {@code classic} uses) and the older flat file ({@code motif_config/catacombs.json}).</p>
 */
public final class MotifConfigs {

    private static final String ROOT = "/data/dungeons2/dungeons2/motif_config";

    private MotifConfigs() {}

    /**
     * @param motif a motif value such as {@code "classic"}
     * @return the resolved config, or {@link MotifConfig#DEFAULT} if the motif ships no files at all
     */
    public static MotifConfig load(String motif) {
        List<MotifConfigFragment> fragments = new ArrayList<>();
        try {
            Path dir = resource(ROOT + "/" + motif);
            if (dir != null && Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    List<Path> sorted = files.filter(f -> f.toString().endsWith(".json"))
                            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                            .toList();
                    for (Path file : sorted) {
                        fragments.add(read(file));
                    }
                }
            }
            // A flat <motif>.json sorts before every <motif>/... id, so it is the base layer.
            Path flat = resource(ROOT + "/" + motif + ".json");
            if (flat != null && Files.isRegularFile(flat)) {
                fragments.add(0, read(flat));
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not read motif '" + motif + "'", e);
        }
        return fragments.isEmpty() ? MotifConfig.DEFAULT : MotifConfigFragment.resolve(fragments);
    }

    private static Path resource(String path) throws Exception {
        URL url = MotifConfigs.class.getResource(path);
        return url == null ? null : Paths.get(url.toURI());
    }

    private static MotifConfigFragment read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = new Gson().fromJson(reader, JsonElement.class);
            return MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, json).result()
                    .orElseThrow(() -> new IllegalStateException("could not decode " + file));
        }
    }
}
