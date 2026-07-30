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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Datapack-driven, codec-backed weighted list of floor patterns, resolved once per room. Entries
 * live at {@code data/dungeons2/dungeons2/floor_pattern_config/<name>.json}. There is currently
 * one shipped entry, {@code default}, looked up via {@link FloorPatternConfigHelper#get}. Same
 * datapack-registry + {@link Codec} pattern as {@link DungeonGenerationConfig}.
 *
 * <p>The actual weighted pick and type-&gt;generator mapping lives in
 * {@code mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorPatternSelector}, not
 * here &mdash; this record stays pure data, same separation {@link DungeonGenerationConfig} keeps
 * from {@code DungeonStackPlanner}.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public record FloorPatternConfig(List<FloorPatternEntry> elements) {

    /** Fallback used when no entry exists: always plain floor, so lookups never NPE. */
    public static final FloorPatternConfig DEFAULT =
            new FloorPatternConfig(List.of(new FloorPatternEntry("empty", 1, 0)));

    public static final Codec<FloorPatternConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FloorPatternEntry.CODEC.listOf().fieldOf("elements").forGetter(FloorPatternConfig::elements)
    ).apply(instance, FloorPatternConfig::new));
}
