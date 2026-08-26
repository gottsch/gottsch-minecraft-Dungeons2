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
package mod.gottsch.forge.dungeons2.core.config.platform;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.registry.PatternTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The open set of {@link PlatformLayoutPattern} types, keyed by {@link ResourceLocation}.
 *
 * <p><strong>Dispatches on {@code layout}, not {@code type}</strong> &mdash; the {@code platforms}
 * slot carries both, and only {@code layout} was ever a switch. See {@link PlatformLayoutPattern}
 * for why the slot has two discriminators at all.</p>
 *
 * <p>Machinery, and the reasoning behind it, live in {@link PatternTypeRegistry}.</p>
 */
public final class PlatformLayoutRegistry {

    /** The key this slot authors its dispatch axis under. */
    public static final String LAYOUT_KEY = "layout";

    private static final PatternTypeRegistry<PlatformLayoutPattern> TYPES =
            new PatternTypeRegistry<>("platform layout", LAYOUT_KEY, PlatformLayoutPattern::codec);

    private PlatformLayoutRegistry() {}

    /** Registers a layout type. Call from your mod's common setup, before any datapack load. */
    public static void register(ResourceLocation id, MapCodec<? extends PlatformLayoutPattern> codec) {
        TYPES.register(id, codec);
    }

    static void register(String path, MapCodec<? extends PlatformLayoutPattern> codec) {
        register(new ResourceLocation(Dungeons.MOD_ID, path), codec);
    }

    public static Set<ResourceLocation> ids() {
        return TYPES.ids();
    }

    /** {@code layout} + {@code config}, embedded flat beside the entry's own fields. */
    public static final MapCodec<PlatformLayoutPattern> MAP_CODEC = TYPES.mapCodec();

    public static final Codec<PlatformLayoutPattern> CODEC = MAP_CODEC.codec();

    /** Idempotent; called from {@code Registration.init} and from the initializer below. */
    public static synchronized void registerBuiltIns() {
        if (!TYPES.isEmpty()) {
            return;
        }
        register(CentrePlatformLayout.NAME, CentrePlatformLayout.CODEC);
        register(CornersPlatformLayout.NAME, CornersPlatformLayout.CODEC);
        register(GridPlatformLayout.NAME, GridPlatformLayout.CODEC);
        register(QuartetPlatformLayout.NAME, QuartetPlatformLayout.CODEC);
        register(ColonnadePlatformLayout.NAME, ColonnadePlatformLayout.CODEC);
    }

    static {
        registerBuiltIns();
    }
}
