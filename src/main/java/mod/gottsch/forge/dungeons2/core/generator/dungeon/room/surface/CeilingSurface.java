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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The ceiling plane, and the mapping between a {@link SurfacePlan}'s {@code (u, v)} space and world
 * coordinates. The horizontal counterpart of {@link WallSurface}.
 *
 * <p>Simpler than a wall in the two ways that made walls awkward. There is exactly one ceiling per
 * room, so there are no runs to join and no corner-ownership rule to pick; and doorways sit on the
 * perimeter ring, which the ceiling does not cover, so there is no mask to apply. What is left is
 * the inset: the ceiling covers the <strong>interior</strong> cells only
 * ({@code [1..width-2] x [1..depth-2]}), the wall tops occupying the perimeter. So
 * {@code u = x - 1} and {@code v = z - 1}, and a pattern's extent is the interior footprint rather
 * than the room's.</p>
 *
 * <p>{@link #facing} is {@link Direction#DOWN} &mdash; the decorated face points down into the room.
 * Nothing uses it yet (coffers and rings are full cubes), but a slab- or stairs-based soffit would,
 * and it is what keeps {@link ISurfacePatternProvider} honest about being surface-generic rather
 * than wall-specific.</p>
 *
 * <p>A ceiling projects <em>down</em>, which is the one thing it has that a flat plan cannot express:
 * a coffer lattice drawn in the ceiling plane is flush with its own panels and reads as a pattern
 * painted on, where the same lattice hanging one cell below reads as the structural rib it is meant
 * to be. See {@link #emitProjected}.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public record CeilingSurface(int originX, int originZ, int uSize, int vSize, int ceilingY) {

    /** The decorated face points down into the room. */
    public Direction facing() {
        return Direction.DOWN;
    }

    /**
     * The world direction {@code u} advances in, and the one {@code v} does &mdash; read straight off
     * {@link #xAt} and {@link #zAt}, which add {@code u} to X and {@code v} to Z.
     *
     * <h2>Why a pattern needs these when a wall pattern does not</h2>
     * <p>{@link #facing()} answers "which way does this surface face", which is all a wall pattern
     * ever needs: every cell of one wall run faces the same way, so the run carries the answer. A
     * ceiling has no runs. Its cells face DOWN uniformly, and the direction that actually matters to
     * a ring &mdash; which way is <em>outward</em> &mdash; differs per cell and is a horizontal
     * direction that {@code facing()} cannot express at all.</p>
     *
     * <p>Rather than hand a provider this whole record (which would let it read {@code ceilingY} and
     * stop being surface-generic), the two axis directions travel to it as constructor arguments.
     * They live here because this is the class whose coordinate mapping defines them: change
     * {@link #xAt} or {@link #zAt} and these must change with it.</p>
     */
    public static final Direction U_DIRECTION = Direction.EAST;

    /** See {@link #U_DIRECTION}. */
    public static final Direction V_DIRECTION = Direction.SOUTH;

    /**
     * The ceiling of a room. A room with no interior yields a zero extent, which simply emits
     * nothing &mdash; the same graceful degradation an empty pattern always has here.
     */
    public static CeilingSurface forRoom(RoomData room, int floorY) {
        return new CeilingSurface(
                room.getOriginX() + 1,
                room.getOriginZ() + 1,
                Math.max(0, room.getWidth() - 2),
                Math.max(0, room.getDepth() - 2),
                floorY + room.getHeight() - 1);
    }

    /** Floor-local X of the cell at {@code u}. */
    public int xAt(int u) {
        return originX + u;
    }

    /** Floor-local Z of the cell at {@code v}. */
    public int zAt(int v) {
        return originZ + v;
    }

    /** Writes the plan's non-null cells, and {@code base} everywhere else. */
    public void emit(SurfacePlan plan, BlockState base, List<BlockPlacement> out) {
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                BlockState planned = plan.get(u, v);
                out.add(BlockStateCodec.placement(
                        xAt(u), ceilingY, zAt(v), planned != null ? planned : base));
            }
        }
    }

    /**
     * Writes a layer hanging {@code depth} cells below the ceiling &mdash; the ribs of a real
     * coffered ceiling rather than a lattice drawn flat on it.
     *
     * <p><strong>Only marked cells are written</strong>, mirroring {@link WallSurface#emitProjected}:
     * a null cell here is not "use the base block", it is the room's open air, and filling it would
     * drop the whole ceiling by a block.</p>
     *
     * <h2>Where this meets the walls' own trim</h2>
     * <p>The ring of cells touching the walls is also where a projecting crown molding hangs, so a
     * scheme carrying both a coffered ceiling and a cornice writes twice to the cells where a rib
     * meets that ring. <strong>The ceiling wins them</strong>, because {@code BasicRoomGenerator}
     * emits it after the walls and a later placement overwrites an earlier one.</p>
     *
     * <p>That is the intended result, not a tolerated collision: a rib running into the cornice and
     * interrupting it is what coffering does where it meets a cornice. The alternative &mdash;
     * insetting the lattice to keep clear &mdash; was tried and shipped visibly wrong, because the
     * gap it left showed up on schemes whose crown is <em>flush</em> and therefore never contested
     * the ring at all. Every rib ran to the wall except in the one case it shouldn't have.</p>
     */
    public void emitProjected(SurfacePlan plan, int depth, List<BlockPlacement> out) {
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                BlockState planned = plan.get(u, v);
                if (planned != null) {
                    out.add(BlockStateCodec.placement(xAt(u), ceilingY - depth, zAt(v), planned));
                }
            }
        }
    }

    /**
     * Writes a layer {@code rise} cells ABOVE the ceiling plane, opening the column beneath it
     * &mdash; a vault that gains headroom instead of spending it (#68).
     *
     * <h2>Why this is not just {@link #emitProjected} with a negative depth</h2>
     * <p>Arithmetically it very nearly is: {@code emitProjected} writes at {@code ceilingY - depth},
     * so a negative depth already lands above the plane, and that is exactly what made #68 look
     * cheap. What it does not do is EXCAVATE. A projecting rib hangs into air the room generator has
     * already hollowed out; a rising vault reaches into cells that are still solid stone, and
     * &mdash; worse &mdash; the plane cell under it has just been paved with the ceiling's own base
     * block. Write only the block at the top and the room is unchanged, with a slab of ceiling
     * material buried in the rock above it.</p>
     *
     * <p>So this clears {@code ceilingY} through {@code ceilingY + rise - 1} for every marked cell
     * before it writes the block at {@code ceilingY + rise}. Clearing the plane cell is the load
     * bearing half: it is the one cell an author cannot see is wrong from the JSON.</p>
     *
     * <h2>Ordering, and stepping</h2>
     * <p>Air here is a placement like any other, so it obeys the same later-wins rule as everything
     * else &mdash; which is what makes a stepped vault work. Author the steps in ascending order and
     * each one's excavation reopens the cell the step below it just roofed, leaving only the highest
     * block standing over any given cell. Author them descending and a lower step will roof the one
     * above it.</p>
     *
     * <p>{@code rise} 0 is legal and writes exactly what a flush layer writes. That is the shape of
     * the render-time clamp in {@code BasicCeilingGenerator}: a room with no spare floor budget gets
     * the vault drawn flat rather than getting a hole into the rock above it.</p>
     */
    public void emitRaised(SurfacePlan plan, int rise, List<BlockPlacement> out) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int rows = Math.max(0, rise);
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                BlockState planned = plan.get(u, v);
                if (planned == null) {
                    continue;
                }
                for (int row = 0; row < rows; row++) {
                    out.add(BlockStateCodec.placement(xAt(u), ceilingY + row, zAt(v), air));
                }
                out.add(BlockStateCodec.placement(xAt(u), ceilingY + rows, zAt(v), planned));
            }
        }
    }
}
