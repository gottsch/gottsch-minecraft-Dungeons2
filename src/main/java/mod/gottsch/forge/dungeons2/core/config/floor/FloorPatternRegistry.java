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
package mod.gottsch.forge.dungeons2.core.config.floor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.config.registry.PatternTypeRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The open set of {@link FloorPattern} types, keyed by {@link ResourceLocation} so another mod can
 * add one and a datapack can name it as {@code "type": "yourmod:yourpattern"}.
 *
 * <p>All the machinery &mdash; the {@code type}/{@code config} dispatch, the closed schema on both
 * levels, and the load error for an unknown id &mdash; lives in {@link PatternTypeRegistry}, which
 * this was the pilot for. Read that class for why it works the way it does.</p>
 */
public final class FloorPatternRegistry {

    private static final PatternTypeRegistry<FloorPattern> TYPES =
            new PatternTypeRegistry<>("floor pattern", FloorPattern::codec);

    /** @see PatternTypeRegistry#TYPE_KEY */
    public static final String TYPE_KEY = PatternTypeRegistry.TYPE_KEY;

    /** @see PatternTypeRegistry#CONFIG_KEY */
    public static final String CONFIG_KEY = PatternTypeRegistry.CONFIG_KEY;

    private FloorPatternRegistry() {}

    /** Registers a pattern type. Call from your mod's common setup, before any datapack load. */
    public static void register(ResourceLocation id, MapCodec<? extends FloorPattern> codec) {
        TYPES.register(id, codec);
    }

    /** Convenience for this mod's own types. */
    static void register(String path, MapCodec<? extends FloorPattern> codec) {
        register(new ResourceLocation(Dungeons.MOD_ID, path), codec);
    }

    public static Set<ResourceLocation> ids() {
        return TYPES.ids();
    }

    /** {@code type} + {@code config}, for embedding flat beside a {@code SizeGate}. */
    public static final MapCodec<FloorPattern> MAP_CODEC = TYPES.mapCodec();

    /** Standalone form, for a pattern nested inside a composite. */
    public static final Codec<FloorPattern> CODEC = MAP_CODEC.codec();

    /**
     * Registers this mod's own types. Idempotent, and called from both {@code Registration.init}
     * and the static initializer below, because a datapack can be parsed by a test that never runs
     * mod setup.
     */
    public static synchronized void registerBuiltIns() {
        if (!TYPES.isEmpty()) {
            return;
        }
        register(PlainFloorPattern.NAME, PlainFloorPattern.CODEC);
        register(BorderFloorPattern.NAME, BorderFloorPattern.CODEC);
        register(CheckerboardFloorPattern.NAME, CheckerboardFloorPattern.CODEC);
        register(SpeckleFloorPattern.NAME, SpeckleFloorPattern.CODEC);
        register(CrossFloorPattern.NAME, CrossFloorPattern.CODEC);
        register(SpokesFloorPattern.NAME, SpokesFloorPattern.CODEC);
        register(GradientFloorPattern.NAME, GradientFloorPattern.CODEC);
        register(WornPathFloorPattern.NAME, WornPathFloorPattern.CODEC);
        register(FieldFloorPattern.NAME, FieldFloorPattern.CODEC);
        register(ChevronFloorPattern.NAME, ChevronFloorPattern.CODEC);
        register(DiagonalFloorPattern.NAME, DiagonalFloorPattern.CODEC);
        register(CompositeFloorPattern.NAME, CompositeFloorPattern.CODEC);
    }

    static {
        registerBuiltIns();
    }
}
