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
package mod.gottsch.forge.dungeons2.core.config.pillar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.registry.PatternTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The open set of {@link PillarLayoutPattern} types, keyed by {@link ResourceLocation} so another
 * mod can add one and a datapack can name it as {@code "type": "yourmod:yourlayout"}.
 *
 * <p>Machinery, and the reasoning behind it, live in {@link PatternTypeRegistry}.</p>
 */
public final class PillarLayoutRegistry {

    private static final PatternTypeRegistry<PillarLayoutPattern> TYPES =
            new PatternTypeRegistry<>("pillar layout", PillarLayoutPattern::codec);

    private PillarLayoutRegistry() {}

    /** Registers a layout type. Call from your mod's common setup, before any datapack load. */
    public static void register(ResourceLocation id, MapCodec<? extends PillarLayoutPattern> codec) {
        TYPES.register(id, codec);
    }

    static void register(String path, MapCodec<? extends PillarLayoutPattern> codec) {
        register(new ResourceLocation(Dungeons.MOD_ID, path), codec);
    }

    public static Set<ResourceLocation> ids() {
        return TYPES.ids();
    }

    /** {@code type} + {@code config}, embedded flat beside the entry's material fields. */
    public static final MapCodec<PillarLayoutPattern> MAP_CODEC = TYPES.mapCodec();

    public static final Codec<PillarLayoutPattern> CODEC = MAP_CODEC.codec();

    /** Idempotent; called from {@code Registration.init} and from the initializer below. */
    public static synchronized void registerBuiltIns() {
        if (!TYPES.isEmpty()) {
            return;
        }
        register(GridPillarLayout.NAME, GridPillarLayout.CODEC);
        register(ColonnadePillarLayout.NAME, ColonnadePillarLayout.CODEC);
        register(QuartetPillarLayout.NAME, QuartetPillarLayout.CODEC);
    }

    static {
        registerBuiltIns();
    }
}
