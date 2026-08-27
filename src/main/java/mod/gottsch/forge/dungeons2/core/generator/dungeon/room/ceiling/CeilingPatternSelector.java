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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.ceiling;

import mod.gottsch.forge.dungeons2.core.config.CeilingConfig;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry;
import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfacePatternEntry;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.BorderSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CeilingSurface;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CentreSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.GridSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.JoistSurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps the {@link CeilingPatternEntry} in a room scheme's ceiling slot to a single
 * {@link ISurfacePatternProvider}. Like its floor and wall counterparts, <strong>this does not
 * roll</strong> &mdash; the choice was made once for the whole room by {@code RoomSchemeSelector}.
 *
 * <p>A list of entries collapses into one composed provider here rather than at render time, so
 * {@code BasicCeilingGenerator} stays a single plan-and-emit and does not need to know that layering
 * exists. Composition is {@link SurfacePlan#overlay} in list order.</p>
 *
 * <p><strong>The degradation rule differs from the wall's, deliberately.</strong> A wall
 * {@code courses} entry is a single pattern, so one unresolvable block degrades the whole thing.
 * Here the list is several independent patterns, and dropping the one with the bad id while keeping
 * the rest is both the more useful behaviour and the more predictable one &mdash; a typo in the
 * boss should not silently strip the coffers. An entry that resolves to nothing is simply skipped,
 * matching how an unrecognized {@code type} already behaves.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public final class CeilingPatternSelector {

    private CeilingPatternSelector() {}

    /**
     * The provider for a scheme's ceiling slot, or {@code null} when there is nothing to draw.
     * Patterns whose own {@link mod.gottsch.forge.dungeons2.core.config.SizeGate} this room fails
     * are dropped first; the rest of the list still draws.
     */
    public static ISurfacePatternProvider providerFor(Optional<CeilingPatternEntry> entry,
                                                     int width, int depth, int height) {
        return providerFor(entry, CeilingConfig.DEFAULT, width, depth, height);
    }

    /**
     * As above, resolved against the motif-or-stratum default underneath it: the scheme's own
     * {@code ceiling} entry wins, then the {@link CeilingConfig}'s own {@code pattern}, then plain.
     * See {@code WallPatternSelector#providerFor} for the reasoning; it is the same three tiers.
     */
    public static ISurfacePatternProvider providerFor(Optional<CeilingPatternEntry> entry,
                                                     CeilingConfig config,
                                                     int width, int depth, int height) {
        return entry.or(config::pattern)
                .map(ceiling -> ceiling.forRoom(width, depth, height))
                .map(CeilingPatternSelector::toProvider)
                .orElse(null);
    }

    /** Ungated form, for callers with no room in hand (tests, and any fully unconditional entry). */
    public static ISurfacePatternProvider providerFor(Optional<CeilingPatternEntry> entry) {
        return entry.map(CeilingPatternSelector::toProvider).orElse(null);
    }

    static ISurfacePatternProvider toProvider(CeilingPatternEntry entry) {
        List<Layer> layers = new ArrayList<>(entry.patterns().size());
        boolean anyProjects = false;
        for (SurfacePatternEntry pattern : entry.patterns()) {
            // Each registered pattern adds its own layers -- usually one, and two for a bracketed
            // joists. That used to be a switch here plus a JOISTS string test; both moved onto the
            // types when they became registry entries, which is why this file no longer knows what
            // a joist is.
            pattern.pattern().addLayers(pattern.projection(), layers);
        }
        for (Layer layer : layers) {
            anyProjects |= layer.depth() > 0;
        }
        if (layers.isEmpty()) {
            return null;
        }
        // The bare provider is enough only for a single flush layer; anything else needs the
        // wrapper, either to overlay or to keep the depths apart.
        if (layers.size() == 1 && !anyProjects) {
            return layers.get(0).provider();
        }
        return new LayeredSurfacePatternProvider(layers);
    }


    /** One treatment and the depth it hangs at. Depth 0 is flush in the ceiling plane. */
    public record Layer(int depth, ISurfacePatternProvider provider) {}

    /**
     * Applies several treatments in order, later non-null cells winning, keeping each depth in its
     * own plan.
     *
     * <p>Layers at different depths never overlay each other &mdash; they are different blocks of
     * air &mdash; so the grouping is what makes "ordering is execution order" mean the right thing:
     * a flush boss still lands on top of a flush lattice, and a hanging rib is simply somewhere
     * else.</p>
     */
    record LayeredSurfacePatternProvider(List<Layer> layers)
            implements ISurfacePatternProvider, IProjectingPatternProvider {

        @Override
        public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
            return planFor(0, uSize, vSize, facing, random);
        }

        @Override
        public Map<Integer, SurfacePlan> projectedPlans(int uSize, int vSize, Direction facing,
                                                        RandomSource random) {
            Map<Integer, SurfacePlan> plans = new LinkedHashMap<>();
            for (Layer layer : layers) {
                if (layer.depth() > 0) {
                    plans.computeIfAbsent(layer.depth(),
                            depth -> planFor(depth, uSize, vSize, facing, random));
                }
            }
            return plans;
        }

        private SurfacePlan planFor(int depth, int uSize, int vSize, Direction facing, RandomSource random) {
            SurfacePlan combined = SurfacePlan.of(uSize, vSize);
            for (Layer layer : layers) {
                if (layer.depth() == depth) {
                    combined.overlay(layer.provider().plan(uSize, vSize, facing, random));
                }
            }
            return combined;
        }
    }
}
