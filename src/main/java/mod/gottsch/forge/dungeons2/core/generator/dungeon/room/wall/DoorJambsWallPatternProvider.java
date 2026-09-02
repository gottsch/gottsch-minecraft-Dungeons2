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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.wall;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.IDoorAwarePatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.SurfacePlan;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.WallSurface;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Set;

/**
 * A jamb up each side of every doorway in this run, with an optional lintel over the opening
 * &mdash; the first pattern that draws where the MAZE put something rather than where the author
 * did (#72).
 *
 * <h2>Why this is worth having</h2>
 * <p>A player in a dark room cannot see which of four identical walls has the way out. Framing the
 * openings answers that from across the room, and it costs the author nothing per room: the doors
 * are wherever the maze opened them, so one authored line dresses every doorway in the dungeon
 * correctly, including the ones on a wall shared with a corridor.</p>
 *
 * <h2>Runs of columns, not columns</h2>
 * <p>A 2-wide door is stored as two adjacent doorway cells, so bracketing each column separately
 * would put a jamb in the middle of a wide opening. This brackets each RUN of adjacent columns
 * instead: a jamb goes at {@code first - 1} and {@code last + 1}, and only if that cell is not
 * itself a doorway.</p>
 *
 * <h2>What it deliberately does not do</h2>
 * <p>It draws no cell in the opening except the lintel, which is above the door halves and is a
 * full cube in the finished doorway anyway. The two rows the door occupies
 * ({@link WallSurface#DOOR_HALF_LOW_V} and {@link WallSurface#DOOR_HALF_HIGH_V}) come out as air
 * regardless of what any pattern marks, so writing there would be silently discarded rather than
 * wrong &mdash; but it would also be a lie in the plan, which the projecting path does read.</p>
 *
 * <p>It is also FLUSH. A projecting jamb would be the more dramatic thing and is deliberately left
 * out: a projecting cell at a doorway is exactly the case {@code WallSurface#emitProjected} spends
 * its corner-ownership rule avoiding, and a jamb standing proud beside an opening is the first
 * thing a player walks into.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public class DoorJambsWallPatternProvider implements IDoorAwarePatternProvider {

    private final BlockState jamb;
    private final BlockState base;
    private final BlockState cap;
    private final BlockState lintel;

    /**
     * @param base   optional distinct block on the lowest row, null to use the jamb's own
     * @param cap    optional distinct block on the highest row, null to use the jamb's own
     * @param lintel optional block over the opening, null to leave the wall's own there
     */
    public DoorJambsWallPatternProvider(BlockState jamb, BlockState base, BlockState cap,
                                        BlockState lintel) {
        this.jamb = Objects.requireNonNull(jamb, "jamb");
        this.base = base;
        this.cap = cap;
        this.lintel = lintel;
    }

    /** The row a lintel sits on: directly above the two the door itself fills. */
    public static final int LINTEL_V = WallSurface.DOOR_HALF_HIGH_V + 1;

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, Set<Integer> doorColumns,
                            RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        if (doorColumns.isEmpty() || vSize <= 0) {
            return plan;
        }
        for (int u = 0; u < uSize; u++) {
            if (!doorColumns.contains(u)) {
                continue;
            }
            // The start of a run of adjacent doorway columns carries the left jamb; the end, the
            // right. A one-column door is both.
            if (!doorColumns.contains(u - 1)) {
                jambAt(plan, u - 1, vSize);
            }
            if (!doorColumns.contains(u + 1)) {
                jambAt(plan, u + 1, vSize);
            }
            if (lintel != null && LINTEL_V < vSize) {
                plan.set(u, LINTEL_V, lintel);
            }
        }
        return plan;
    }

    /**
     * One jamb column. Out-of-range writes are swallowed by {@code SurfacePlan#set}, which is what
     * handles a door hard against the end of a run &mdash; the jamb on that side simply has nowhere
     * to go, and the run next door owns that column anyway.
     */
    private void jambAt(SurfacePlan plan, int u, int vSize) {
        if (u < 0 || u >= plan.uSize()) {
            return;
        }
        for (int v = 0; v < vSize; v++) {
            plan.set(u, v, jamb);
        }
        if (base != null) {
            plan.set(u, 0, base);
        }
        if (cap != null) {
            plan.set(u, vSize - 1, cap);
        }
    }
}
