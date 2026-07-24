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
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.decorator.data.BlockProviderDefinition;
import mod.gottsch.forge.dungeons2.core.registry.BlockProivderRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Datapack loader for the decorator/material palette. Reads every
 * {@code data/<namespace>/block_provider/<motif>.json}, decodes it with
 * {@link BlockProviderDefinition#CODEC}, and rebuilds {@link BlockProivderRegistry}
 * (the runtime {@code BlockProvider} per motif). Runs on datapack load and on every
 * {@code /reload}, so the palette is live-editable.
 *
 * <p>The motif id is the file's path within the {@code block_provider} folder
 * (e.g. {@code classic.json} -> motif {@code "classic"}). A file that fails to parse
 * is logged and skipped; the rest still load.</p>
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
public class BlockProviderReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "block_provider";

    public BlockProviderReloadListener() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, BlockProviderDefinition> byMotif = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
            ResourceLocation id = entry.getKey();
            BlockProviderDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .resultOrPartial(err -> Dungeons.LOGGER.error(
                            "Failed to parse block provider '{}': {}", id, err))
                    .ifPresent(def -> byMotif.put(id.getPath(), def));
        }
        BlockProivderRegistry.clear();
        BlockProivderRegistry.loadDefinitions(byMotif);
        Dungeons.LOGGER.info("Loaded {} block provider motif(s) from datapack", byMotif.size());
    }
}
