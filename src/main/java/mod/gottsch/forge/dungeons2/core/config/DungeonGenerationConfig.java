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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Datapack-driven, codec-backed dungeon generation tuning.
 * <p>
 * Entries live at {@code data/dungeons2/dungeons2/generation_config/<name>.json}. There is
 * currently one shipped entry, {@code default}, looked up via
 * {@link DungeonGenerationConfigHelper#get}. This replaces the old
 * {@code Config.SERVER.dungeons.corridorWidth} {@code ForgeConfigSpec} field with the same
 * datapack-registry + {@link Codec} pattern already used by gmm's {@code MobConfig}
 * ({@code mod.gottsch.forge.gmm.core.config.MobConfig}) &mdash; reloadable with the world's
 * datapacks, no restart required.
 * <p>
 * <strong>Future knobs:</strong> per-size-tier values (room count range, floor count range,
 * footprint range &mdash; currently hard-coded per tier in {@link mod.gottsch.forge.dungeons2.core.data.DungeonSize})
 * are a natural follow-up, but are a separate migration: {@code DungeonStackPlanner} is
 * deliberately Minecraft-import-free and rolls its own size tier internally from the seed, so
 * per-size config can't be resolved by the caller up front the way {@code corridorWidth} is
 * here (the caller already knows what it wants before planning starts). Threading per-size
 * config in would need the same builder-injection approach used for {@code withCorridorWidth}/
 * {@code withTransitionAssembler}, just resolved per-tier once the planner has picked one
 * &mdash; likely its own registry keyed by tier name ({@code small}/{@code medium}/{@code large}).
 *
 * @author Mark Gottschling on Jul 25, 2026
 */
public record DungeonGenerationConfig(int corridorWidth) {

    /** Fallback used when no entry exists, so lookups never NPE. */
    public static final DungeonGenerationConfig DEFAULT = new DungeonGenerationConfig(3);

    public static final Codec<DungeonGenerationConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 3).optionalFieldOf("corridorWidth", DEFAULT.corridorWidth())
                    .forGetter(DungeonGenerationConfig::corridorWidth)
    ).apply(instance, DungeonGenerationConfig::new));
}
