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
package mod.gottsch.forge.dungeons2.core.config.pit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.registry.PatternTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The {@code pit} slot's shape types, dispatched on {@code type}. Backlog #3.
 *
 * <p>The sixth of these registries and the plainest &mdash; one discriminator, and no vocabulary
 * shared with another slot, so there is no reason to look anywhere else before adding a shape. See
 * {@code mod.gottsch.forge.dungeons2.core.config.floor.FloorPatternRegistry} for why these are
 * plain static maps rather than Forge registries, and why an unregistered id is a load error naming
 * what IS registered rather than a silently flat floor.</p>
 *
 * @author Mark Gottschling on Aug 27, 2026
 */
public final class PitShapeRegistry {

    private static final PatternTypeRegistry<PitShapePattern> TYPES =
            new PatternTypeRegistry<>("pit shape", PitShapePattern::codec);

    private PitShapeRegistry() {}

    /** Registers a shape type. Call from your mod's common setup, before any datapack load. */
    public static void register(ResourceLocation id, MapCodec<? extends PitShapePattern> codec) {
        TYPES.register(id, codec);
    }

    static void register(String path, MapCodec<? extends PitShapePattern> codec) {
        register(new ResourceLocation(Dungeons.MOD_ID, path), codec);
    }

    public static Set<ResourceLocation> ids() {
        return TYPES.ids();
    }

    /** {@code type} + {@code config}, embedded flat beside the entry's own fields. */
    public static final MapCodec<PitShapePattern> MAP_CODEC = TYPES.mapCodec();

    public static final Codec<PitShapePattern> CODEC = MAP_CODEC.codec();

    /** Idempotent; called from {@code Registration.init} and from the initializer below. */
    public static synchronized void registerBuiltIns() {
        if (!TYPES.isEmpty()) {
            return;
        }
        register(CentrePitShape.NAME, CentrePitShape.CODEC);
        register(InsetPitShape.NAME, InsetPitShape.CODEC);
        register(HazardPitShape.NAME, HazardPitShape.CODEC);
    }

    static {
        registerBuiltIns();
    }
}
