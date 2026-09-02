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
    /** See {@link #withRiseBudget}. Zero -- the default -- means nothing may rise. */
    private int riseBudget;

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

    /**
     * How many rows of the floor's budget are still unspent ABOVE this room's ceiling &mdash; the
     * hard cap on how far a rising vault may reach (#68). Zero, the default, means no layer rises
     * however its scheme is authored, exactly as {@code sink_offset} 0 means no room gets a pit.
     *
     * <h2>The clamp is on the OUTPUT, and that is deliberate</h2>
     * <p>Same rule {@code RoomPitGenerator} follows for {@code sink_offset}, and for the same reason:
     * the ceiling pattern registry is open to other mods, so "respect the floor's budget or you open
     * a hole into the floor above" is a rule a third party would forget. It is enforced here, where
     * the room's actual height is known, rather than by any field range &mdash; no {@code intRange}
     * can see the room.</p>
     *
     * <p>The value is {@code ceiling_budget - height}: a floor owns {@code ceiling_budget} rows above
     * its walking plane, and a room {@code height} high has used {@code height} of them. So a rise
     * can never reach the stone buffer, let alone the floor above &mdash; the arithmetic is the exact
     * mirror of a pit's, which can never reach the ceiling below.</p>
     *
     * <p>A layer asking for more than this is CLAMPED rather than dropped, so a scheme authored for
     * tall rooms still draws in a short one: it comes out flatter, which is the same picture as a
     * plain ceiling and never a hole. At a budget of 0 a rising vault therefore renders exactly as
     * today's flush ceiling does.</p>
     */
    public BasicCeilingGenerator withRiseBudget(int riseBudget) {
        this.riseBudget = Math.max(0, riseBudget);
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
                int depth = layer.getKey();
                if (depth >= 0) {
                    surface.emitProjected(layer.getValue(), depth, out);
                } else {
                    // #68: a NEGATIVE depth rises above the plane instead of hanging below it, and
                    // unlike a hanging rib it has to excavate on the way -- see
                    // CeilingSurface#emitRaised. Clamped to what this floor has left above the room
                    // (withRiseBudget), which is the only place the room's own height is known.
                    surface.emitRaised(layer.getValue(), Math.min(-depth, riseBudget), out);
                }
            }
        }
    }
}
