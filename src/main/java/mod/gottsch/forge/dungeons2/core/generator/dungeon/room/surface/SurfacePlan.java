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

import net.minecraft.world.level.block.state.BlockState;

/**
 * A pattern drawn in a surface's own 2D space: {@code u} along the surface, {@code v} up it, with
 * one nullable {@link BlockState} per cell. <strong>Null means "leave this cell to the base
 * block"</strong> &mdash; it is not the same as air, which is a state a pattern can deliberately
 * place (a niche, an arrow slit).
 *
 * <h2>Why not the floor's {@code boolean[][]}</h2>
 * <p>The floor pattern providers plan in booleans because a floor cell is accent-or-base and a
 * floor block has no orientation. Neither holds on a wall: a single course of stairs places two
 * different {@code facing} values in one row, and a niche places air and a sill block in the same
 * plan. Carrying states rather than a flag is the difference that makes the same machinery work for
 * both.</p>
 *
 * <h2>Why sparse, and why there is no build/overlay split</h2>
 * <p>{@code IFloorOverlayGenerator} exists because a floor generator's {@code build} fills every
 * cell, so layering one over another needed a second entry point that emits only marked cells. A
 * sparse plan removes the problem instead of solving it: every plan is inherently an overlay,
 * because the cells it does not care about are already null. Composition is then just
 * {@link #overlay}, applied in order, later non-null winning &mdash; the same
 * ordering-is-execution-order convention the {@code processor_list} files and
 * {@code CompositeFloorPatternProvider} use, with no second interface to implement.</p>
 *
 * @author Mark Gottschling on Aug 1, 2026
 */
public final class SurfacePlan {

    private final int uSize;
    private final int vSize;
    private final BlockState[][] cells;

    private SurfacePlan(int uSize, int vSize) {
        this.uSize = Math.max(0, uSize);
        this.vSize = Math.max(0, vSize);
        this.cells = new BlockState[this.uSize][this.vSize];
    }

    /**
     * An all-null plan of the given extent: every cell left to the base block. A degenerate extent
     * (a room too thin to have that wall run at all) clamps to zero rather than throwing, the same
     * graceful degradation an empty pattern always has elsewhere in this codebase.
     */
    public static SurfacePlan of(int uSize, int vSize) {
        return new SurfacePlan(uSize, vSize);
    }

    public int uSize() {
        return uSize;
    }

    public int vSize() {
        return vSize;
    }

    /** The state at {@code (u, v)}, or null for "base block". Out-of-range reads return null. */
    public BlockState get(int u, int v) {
        return inRange(u, v) ? cells[u][v] : null;
    }

    /**
     * Sets a cell. Out-of-range writes are <strong>ignored</strong>, deliberately: a pattern
     * anchored to the top of a wall (a crown molding course) is written against a height the room
     * may not have, and clamping at the edge is what lets those providers stay arithmetic rather
     * than every one of them repeating the same bounds check.
     */
    public void set(int u, int v, BlockState state) {
        if (inRange(u, v)) {
            cells[u][v] = state;
        }
    }

    /** Lays {@code other} over this one: each of its non-null cells wins. */
    public void overlay(SurfacePlan other) {
        int uLimit = Math.min(uSize, other.uSize);
        int vLimit = Math.min(vSize, other.vSize);
        for (int u = 0; u < uLimit; u++) {
            for (int v = 0; v < vLimit; v++) {
                BlockState state = other.cells[u][v];
                if (state != null) {
                    cells[u][v] = state;
                }
            }
        }
    }

    /** How many cells this plan actually marks &mdash; mostly useful to tests. */
    public int markedCells() {
        int count = 0;
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                if (cells[u][v] != null) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean inRange(int u, int v) {
        return u >= 0 && u < uSize && v >= 0 && v < vSize;
    }
}
