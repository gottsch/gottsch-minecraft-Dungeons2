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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit;

import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.config.PitPatternEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes a scheme's {@code pit} into a room's floor. Backlog #3.
 *
 * <h2>The provider decides the shape; this decides what is legal</h2>
 * <p>A provider returns a {@link PitPlan} &mdash; a depth per cell, and optionally something
 * standing on it. Whether that is a terraced court or a sheer shaft full of stalagmites is entirely
 * the provider's business, exactly as an arrangement of courses is the wall provider's. This class
 * owns two things a provider must not: <strong>the budget clamp</strong> and the write order.</p>
 *
 * <h2>THE CLAMP IS ON THE OUTPUT, and that is the point</h2>
 * <p>Every depth is clamped to {@code sinkOffset} as it is written, so a provider
 * <strong>cannot</strong> dig past the floor's own budget into the gap between floors, however its
 * config is authored and whoever wrote it. The rule used to live on a field, which worked only as
 * long as every provider remembered it &mdash; and providers are the extension point, so "remember
 * this or you open a hole into the room below" was a rule waiting to be forgotten by someone who
 * had never read it.</p>
 *
 * <p>{@code sinkOffset} 0 therefore writes nothing at all, which is what ships today.</p>
 *
 * <h2>It runs AFTER the floor, and it overwrites</h2>
 * <p>The placement list is a layering order &mdash; a later placement in the same cell wins &mdash;
 * so the pit does not have to coordinate with the floor generator or ask it to skip cells. The
 * floor paves the whole plane, then the pit takes the cells it wants back.</p>
 *
 * @author Mark Gottschling on Aug 27, 2026
 */
public final class RoomPitGenerator {

    private RoomPitGenerator() {}

    /**
     * @param sinkOffset the floor's budget below its walking plane, from the generation config;
     *                   0 means no pit can be dug and this is a no-op
     * @return the floor-local cells that were excavated, so the caller can CLAIM them &mdash;
     *         nothing may stand on a terrace it did not account for
     */
    public static Set<Coords2D> excavate(RoomData room, int floorY, PitPatternEntry entry,
                                         int sinkOffset, FloorConfig floorConfig,
                                         RandomSource random, List<BlockPlacement> out) {
        Set<Coords2D> excavated = new HashSet<>();
        if (sinkOffset < 1) {
            return excavated;
        }
        int interiorWidth = room.getWidth() - 2;
        int interiorDepth = room.getDepth() - 2;
        if (interiorWidth < 1 || interiorDepth < 1) {
            return excavated;
        }

        PitPlan plan = entry.shape().provider().plan(interiorWidth, interiorDepth, random);
        if (plan.isEmpty()) {
            return excavated;
        }

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState floorState = entry.floorBlock()
                .map(id -> BlockStateCodec.block(id, Blocks.STONE_BRICKS))
                .orElseGet(floorConfig::baseState);

        int originX = room.getOriginX();
        int originZ = room.getOriginZ();
        for (Map.Entry<Coords2D, Integer> cell : plan.depths().entrySet()) {
            // Interior-local (0,0) is floor-local (originX + 1, originZ + 1) -- the same convention
            // the pillar providers use, and why a provider cannot dig out the wall ring's cells.
            int x = originX + 1 + cell.getKey().getX();
            int z = originZ + 1 + cell.getKey().getY();
            int depth = Math.min(cell.getValue(), sinkOffset);
            if (depth < 1) {
                continue;
            }
            excavated.add(new Coords2D(x, z));

            int y = floorY - depth;
            out.add(BlockStateCodec.placement(x, y, z, floorState));
            for (int above = y + 1; above <= floorY; above++) {
                out.add(BlockStateCodec.placement(x, above, z, air));
            }
            BlockState fill = plan.fills().get(cell.getKey());
            if (fill != null) {
                // On the terrace, not in it -- a spike stands on the floor it was planned for, and
                // the clamp may have raised that floor since the provider chose the cell.
                out.add(BlockStateCodec.placement(x, y + 1, z, fill));
            }
        }
        // The rim sits at the room's OWN walking plane and is not excavated: those cells stay
        // walkable floor, and are deliberately not returned, so props may still stand on them.
        for (Map.Entry<Coords2D, BlockState> step : plan.rim().entrySet()) {
            out.add(BlockStateCodec.placement(originX + 1 + step.getKey().getX(), floorY,
                    originZ + 1 + step.getKey().getY(), step.getValue()));
        }
        return excavated;
    }
}
