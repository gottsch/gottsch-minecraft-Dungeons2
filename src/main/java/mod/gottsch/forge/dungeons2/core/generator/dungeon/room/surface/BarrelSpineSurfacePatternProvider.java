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
 * The spine of a double barrel vault: the one or two cells between its bays, running the full
 * length of the room, in the springing material.
 *
 * <p>The spine is the shared springing line of both barrels &mdash; the lip each bay rises from on
 * its inner side, as the wall-side springing ring is on its outer side. Neither bay's steps cover
 * it (see {@link BarrelBays}), so it stays on the ceiling plane on its own; this only gives it the
 * same material as the ring, so the two barrels read as springing off a rib rather than off a strip
 * of bare ceiling. Without a {@code springing_block} the vault adds none of these and the spine is
 * plain ceiling, as the lips are.</p>
 *
 * <p>Its own provider rather than a mode of the ring, because it must ask the vault's shared
 * {@link BarrelAxis} which way to run and its {@link BarrelBays} whether there are two barrels at
 * all: a room too narrow to split falls back to one barrel and must not then grow a rib down the
 * middle of it.</p>
 *
 * @author Mark Gottschling on 10/29/2026
 */
public class BarrelSpineSurfacePatternProvider implements ISurfacePatternProvider {

    private final BlockState block;
    private final BarrelAxis barrel;
    private final BarrelBays bays;

    public BarrelSpineSurfacePatternProvider(BlockState block, BarrelAxis barrel, BarrelBays bays) {
        this.block = Objects.requireNonNull(block, "block");
        this.barrel = Objects.requireNonNull(barrel, "barrel");
        this.bays = Objects.requireNonNull(bays, "bays");
    }

    @Override
    public SurfacePlan plan(int uSize, int vSize, Direction facing, RandomSource random) {
        SurfacePlan plan = SurfacePlan.of(uSize, vSize);
        boolean alongU = barrel.runsAlongU(uSize, vSize, random);
        int cross = alongU ? vSize : uSize;
        if (!bays.doubled(cross)) {
            return plan;
        }
        int run = alongU ? uSize : vSize;
        for (int c = BarrelBays.spineStart(cross); c < BarrelBays.spineEnd(cross); c++) {
            for (int r = 0; r < run; r++) {
                if (alongU) {
                    plan.set(r, c, block);
                } else {
                    plan.set(c, r, block);
                }
            }
        }
        return plan;
    }
}
