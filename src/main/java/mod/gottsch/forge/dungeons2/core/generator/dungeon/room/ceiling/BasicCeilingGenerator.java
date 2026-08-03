/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2024 Mark Gottschling (gottsch)
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

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.CeilingSurface;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IProjectingPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Builds the ceiling of a {@link RoomData} as {@link BlockPlacement}s.
 *
 * <p>Ceiling occupies the interior cells (1 inset from the walls) at world
 * Y={@code floorY + height - 1}. Geometry lives in {@link CeilingSurface}; this class resolves the
 * block and hands the surface a {@link SurfacePlan} to render, exactly as
 * {@code BasicWallGenerator} does for its four runs. With no provider injected the plan is empty
 * and every cell falls through to the motif's plain ceiling block.</p>
 *
 * @author Mark Gottschling on Mar 6, 2024 (Phase 2 rewrite May 25, 2026)
 */
public class BasicCeilingGenerator implements IDungeonCeilingGenerator {

    private MotifConfig motifConfig = MotifConfig.DEFAULT;
    /** Null means no ceiling treatment: every cell falls through to the plain ceiling block. */
    private ISurfacePatternProvider ceilingPattern;

    /** See {@code BasicWallGenerator#withMotifConfig}. */
    public BasicCeilingGenerator withMotifConfig(MotifConfig motifConfig) {
        this.motifConfig = motifConfig;
        return this;
    }

    /**
     * Injects the ceiling treatment for this room, already chosen and composed by
     * {@code CeilingPatternSelector}. Null &mdash; the default &mdash; is a plain ceiling.
     */
    public BasicCeilingGenerator withCeilingPattern(ISurfacePatternProvider ceilingPattern) {
        this.ceilingPattern = ceilingPattern;
        return this;
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        BlockState ceilingState = motifConfig.ceiling().ceilingState();
        CeilingSurface surface = CeilingSurface.forRoom(room, floorY);

        SurfacePlan plan = ceilingPattern == null
                ? SurfacePlan.of(surface.uSize(), surface.vSize())
                : ceilingPattern.plan(surface.uSize(), surface.vSize(), surface.facing(), random);

        surface.emit(plan, ceilingState, out);

        // Ribs that hang below the ceiling land in the room's interior air, which
        // RoomVolumeGenerator has already cleared -- so they run after the ceiling plane and simply
        // win those cells. Same shape as BasicWallGenerator's projecting trim, and they win against
        // that too: the room generator runs the ceiling last on purpose, so a rib meeting a
        // projecting cornice interrupts it rather than dodging it.
        if (ceilingPattern instanceof IProjectingPatternProvider projecting) {
            Map<Integer, SurfacePlan> projected = projecting.projectedPlans(
                    surface.uSize(), surface.vSize(), surface.facing(), random);
            for (Map.Entry<Integer, SurfacePlan> layer : projected.entrySet()) {
                surface.emitProjected(layer.getValue(), layer.getKey(), out);
            }
        }
    }
}
