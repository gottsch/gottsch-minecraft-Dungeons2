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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomVolumeGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.WallSurface;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the four walls of a {@link RoomData} as {@link BlockPlacement}s &mdash; the perimeter ring
 * only. The interior air the room stands in is {@link RoomVolumeGenerator}'s job.
 *
 * <p>Geometry lives in {@link WallSurface}: this class works out the wall height and the doorway
 * set, then hands each of the four runs a {@link SurfacePlan} to render. The plan here is empty, so
 * every cell falls through to the motif's plain wall block &mdash; which is exactly what a room
 * with no wall treatment should be. A wall pattern is then a provider that fills cells of that
 * plan, with no change to this class beyond asking one for it.</p>
 *
 * <p>Output coords are floor-local X/Z and absolute world Y. The room occupies
 * {@code [originX..originX+width-1] x [originZ..originZ+depth-1]} on the floor; vertically the
 * walls span {@code [floorY+1..floorY+height-2]} (the floor block at Y=floorY and the ceiling at
 * Y=floorY+height-1 are emitted by {@code BasicFloorGenerator} / {@code BasicCeilingGenerator}).
 * That gives a wall {@code height - 2} rows tall &mdash; between <strong>3 and 8</strong>, since
 * {@code DungeonStackPlanner#pickRoomHeight} rolls {@code min(rand(5..10), max(width, depth))}. Any
 * pattern measured from the top has to cope with the low end of that.</p>
 *
 * @author Mark Gottschling on Mar 6, 2024 (Phase 2 rewrite May 25, 2026; surface frame Aug 1, 2026)
 */
public class BasicWallGenerator implements IDungeonWallGenerator {

    private MotifConfig motifConfig = MotifConfig.DEFAULT;
    /** Null means no wall treatment: every cell falls through to the plain wall block. */
    private ISurfacePatternProvider wallPattern;

    /**
     * Injects the resolved motif config. Same "resolve once where {@code RegistryAccess} is
     * available, inject the resolved value" shape the rest of this pipeline uses; left at
     * {@link MotifConfig#DEFAULT} (plain stone_bricks) when not supplied.
     */
    public BasicWallGenerator withMotifConfig(MotifConfig motifConfig) {
        this.motifConfig = motifConfig;
        return this;
    }

    /**
     * Injects the wall treatment for this room, already chosen by the room's scheme (see
     * {@code WallPatternSelector}). Null &mdash; the default &mdash; is a plain wall.
     */
    public BasicWallGenerator withWallPattern(ISurfacePatternProvider wallPattern) {
        this.wallPattern = wallPattern;
        return this;
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif,
                      RandomSource random, List<BlockPlacement> out) {
        BlockState wallState = motifConfig.wall().wallState();

        // Interior rows only: the floor and ceiling planes belong to their own generators.
        int wallHeight = room.getHeight() - 2;

        // Doorway cells are floor-local grid coords, the same space as the surfaces' xAt/zAt.
        Set<Coords2D> doorways = new HashSet<>(room.getDoorways());

        for (WallSurface surface : WallSurface.forRoom(room)) {
            SurfacePlan plan = planFor(surface, wallHeight);
            surface.emit(plan, floorY, doorways, wallState, out);
        }
    }

    /**
     * The pattern for one wall run. With no provider injected this is an all-null plan, so every
     * cell renders as the motif's wall block &mdash; a room with no wall treatment.
     *
     * <p>Each run is planned separately and handed its own {@code facing}, which is the whole point
     * of the {@code (u, v)} frame: one authored pattern comes out correctly oriented on all four
     * walls without the provider knowing anything about the room.</p>
     */
    protected SurfacePlan planFor(WallSurface surface, int wallHeight) {
        return wallPattern == null
                ? SurfacePlan.of(surface.length(), wallHeight)
                : wallPattern.plan(surface.length(), wallHeight, surface.facing());
    }
}
