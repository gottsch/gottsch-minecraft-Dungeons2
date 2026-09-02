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

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Every cell at or inside {@code inset} &mdash; a filled rectangle rather than
 * {@link BorderSurfacePatternProvider}'s ring around one.
 *
 * <h2>What this is for, and why a ring could not do it</h2>
 * <p>It is the step of a RISING vault (#68). A vault that hangs below the ceiling is drawn as rings,
 * because the plane behind each ring stays where it is and only the perimeter comes down. A vault
 * that rises inverts that: the perimeter stays on the plane and everything inside it has to come
 * <em>up</em>, which is a filled area, not an outline. Drawn as rings, a rising vault would be a set
 * of floating square hoops with the original ceiling still sitting between them.</p>
 *
 * <p>So a stepped rising vault is a stack of these at decreasing extent and increasing rise: inset 1
 * at rise 1, inset 2 at rise 2, inset 3 at rise 3. Each step's own excavation reopens the roof the
 * step below it laid over the same cells (see {@code CeilingSurface#emitRaised}), so what survives
 * over any given cell is the highest step that covers it &mdash; which is exactly a corbelled dome,
 * and it falls out of the ordering rather than needing a per-cell height field.</p>
 *
 * <p>It is not vault-only. Flush, it is a plain inner field of a second material &mdash; a
 * bordered ceiling with a distinct centre panel, which previously needed a {@code centre} boss sized
 * by hand to the room and therefore could not follow the room's size.</p>
 *
 * <h2>An inset with no field left</h2>
 * <p>An {@code inset} that meets or crosses the middle marks nothing at all, and that is right
 * rather than degenerate: the step has run out of ceiling to raise. It is also the shape of the
 * clamp one level up &mdash; a four-step vault authored into a small room simply stops stepping when
 * it runs out of room, instead of drawing a one-cell spike.</p>
 *
 * @author Mark Gottschling on Sep 1, 2026
 */
public class FieldSurfacePatternProvider implements ISurfacePatternProvider {

    /** Matches {@link BorderSurfacePatternProvider#DEFAULT_INSET}: the ring just inside the wall. */
    public static final int DEFAULT_INSET = 0;

    private final int inset;
    private final BlockState block;

    public FieldSurfacePatternProvider(int inset, BlockState block) {
        this.inset = Math.max(0, inset);
        this.block = Objects.requireNonNull(block, "block");
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        for (int u = inset; u < uSize - inset; u++) {
            for (int v = inset; v < vSize - inset; v++) {
                plan.set(u, v, block);
            }
        }
        return plan;
    }
}
