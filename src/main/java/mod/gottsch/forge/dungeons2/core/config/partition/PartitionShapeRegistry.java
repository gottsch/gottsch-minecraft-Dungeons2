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
package mod.gottsch.forge.dungeons2.core.config.partition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.registry.PatternTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The {@code partition} slot's shape types, dispatched on {@code type}. Backlog #74.
 *
 * <p>The seventh of these registries. See
 * {@code mod.gottsch.forge.dungeons2.core.config.floor.FloorPatternRegistry} for why these are plain
 * static maps rather than Forge registries, and why an unregistered id is a load error naming what
 * IS registered rather than a room that quietly has no partition.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public final class PartitionShapeRegistry {

    private static final PatternTypeRegistry<PartitionShapePattern> TYPES =
            new PatternTypeRegistry<>("partition shape", PartitionShapePattern::codec);

    private PartitionShapeRegistry() {}

    /** Registers a shape type. Call from your mod's common setup, before any datapack load. */
    public static void register(ResourceLocation id, MapCodec<? extends PartitionShapePattern> codec) {
        TYPES.register(id, codec);
    }

    static void register(String path, MapCodec<? extends PartitionShapePattern> codec) {
        register(new ResourceLocation(Dungeons.MOD_ID, path), codec);
    }

    public static Set<ResourceLocation> ids() {
        return TYPES.ids();
    }

    /** {@code type} + {@code config}, embedded flat beside the entry's own fields. */
    public static final MapCodec<PartitionShapePattern> MAP_CODEC = TYPES.mapCodec();

    public static final Codec<PartitionShapePattern> CODEC = MAP_CODEC.codec();

    /** Idempotent; called from {@code Registration.init} and from the initializer below. */
    public static synchronized void registerBuiltIns() {
        if (!TYPES.isEmpty()) {
            return;
        }
        register(CornerPartitionShape.NAME, CornerPartitionShape.CODEC);
        register(StripPartitionShape.NAME, StripPartitionShape.CODEC);
    }

    static {
        registerBuiltIns();
    }
}
