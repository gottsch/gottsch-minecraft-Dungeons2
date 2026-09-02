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
package mod.gottsch.forge.dungeons2.core.config.ceiling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.registry.PatternTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The open set of {@link CeilingPattern} types, keyed by {@link ResourceLocation}.
 *
 * <p>Machinery, and the reasoning behind it, live in {@link PatternTypeRegistry}.</p>
 */
public final class CeilingPatternRegistry {

    private static final PatternTypeRegistry<CeilingPattern> TYPES =
            new PatternTypeRegistry<>("ceiling pattern", CeilingPattern::codec);

    private CeilingPatternRegistry() {}

    /** Registers a pattern type. Call from your mod's common setup, before any datapack load. */
    public static void register(ResourceLocation id, MapCodec<? extends CeilingPattern> codec) {
        TYPES.register(id, codec);
    }

    static void register(String path, MapCodec<? extends CeilingPattern> codec) {
        register(new ResourceLocation(Dungeons.MOD_ID, path), codec);
    }

    public static Set<ResourceLocation> ids() {
        return TYPES.ids();
    }

    /** {@code type} + {@code config}, embedded flat beside the entry's projection and gate. */
    public static final MapCodec<CeilingPattern> MAP_CODEC = TYPES.mapCodec();

    public static final Codec<CeilingPattern> CODEC = MAP_CODEC.codec();

    /** Idempotent; called from {@code Registration.init} and from the initializer below. */
    public static synchronized void registerBuiltIns() {
        if (!TYPES.isEmpty()) {
            return;
        }
        register(BorderCeilingPattern.NAME, BorderCeilingPattern.CODEC);
        register(CoffersCeilingPattern.NAME, CoffersCeilingPattern.CODEC);
        register(JoistsCeilingPattern.NAME, JoistsCeilingPattern.CODEC);
        register(CentreCeilingPattern.NAME, CentreCeilingPattern.CODEC);
        register(FieldCeilingPattern.NAME, FieldCeilingPattern.CODEC);
        register(VaultedCeilingPattern.NAME, VaultedCeilingPattern.CODEC);
        register(OculusCeilingPattern.NAME, OculusCeilingPattern.CODEC);
        // Both spellings over the ONE codec -- see CentreCeilingPattern. `idOf` finds the first id
        // a codec is registered under, so `centre` is what an encode writes back.
        register(CentreCeilingPattern.ALIAS, CentreCeilingPattern.CODEC);
    }

    static {
        registerBuiltIns();
    }
}
