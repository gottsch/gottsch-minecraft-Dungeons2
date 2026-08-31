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

import java.util.HashMap;
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
 * <h2>AND THE LINING, which is the same kind of rule</h2>
 * <p>Every vertical face a pit cuts is <strong>lined</strong> here, in the neighbouring column,
 * from the pit's own floor up to the underside of whatever that neighbour stands on. A room's floor
 * plane is the only thing between a room and the terrain it was carved into; dig below it and the
 * terrain is what you are looking at, and terrain includes caves, ravines and aquifers. Observed in
 * game (Gottsch, 2026-08-29): a hazard shaft opened into a cavern and poured a waterfall down its
 * own side.</p>
 *
 * <p><strong>It is not a provider's job and not an authored field</strong>, for the reason the clamp
 * is not either &mdash; a shaft open along one side is never what anyone asked for, so the fix
 * belongs where it cannot be forgotten, not in a config key every pack and every third-party
 * provider has to remember. {@code PitPatternEntry} once carried a {@code wallBlock} and it was
 * removed as dead weight when the courts terraced; the courts were right about themselves and wrong
 * about the shapes that came later.</p>
 *
 * <p>The lining is the pit's own floor block, so a shaft reads as one excavation rather than as a
 * floor with a differently-built liner. <strong>A terraced court barely notices</strong>: its faces
 * are one block tall and are already the side of the next terrace's slab, so it takes one cell per
 * face &mdash; the cell UNDER that slab, which is the sliver of terrain a court could show and
 * nobody had looked for. The eight neighbours are lined rather than the four: a cavern that eats a
 * diagonal column leaves a gap at the corner exactly as an orthogonal one does.</p>
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

        // Interior-local cell -> the depth actually dug, which is the AUTHORED depth after the
        // clamp. The lining pass reads this rather than plan.depths(), or a neighbour clamped to a
        // shallower floor than it asked for would be lined to a face that was never cut.
        Map<Coords2D, Integer> dug = new HashMap<>();
        for (Map.Entry<Coords2D, Integer> cell : plan.depths().entrySet()) {
            int depth = Math.min(cell.getValue(), sinkOffset);
            if (depth >= 1) {
                dug.put(cell.getKey(), depth);
            }
        }

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
        line(dug, room, originX, originZ, floorY, floorState, out);

        // The rim sits at the room's OWN walking plane and is not excavated: those cells stay
        // walkable floor, and are deliberately not returned, so props may still stand on them.
        for (Map.Entry<Coords2D, BlockState> step : plan.rim().entrySet()) {
            out.add(BlockStateCodec.placement(originX + 1 + step.getKey().getX(), floorY,
                    originZ + 1 + step.getKey().getY(), step.getValue()));
        }
        return excavated;
    }

    /**
     * Backs every cut face with the pit's own floor block, so a pit is a closed box whatever the
     * terrain around it turned out to be.
     *
     * <p>For each dug cell, each of its eight neighbours is filled from the cell's own floor up to
     * the block below whatever that neighbour stands on &mdash; the room's floor plane for an
     * undug neighbour, the neighbour's terrace slab for a shallower dug one. A neighbour dug at
     * least as deep has no face to back and is skipped, which is why a sheer shaft's interior costs
     * nothing: those columns are already air the pass never touches.</p>
     *
     * <p>Lining REACHES INTO THE WALL RING when a provider digs against the interior edge, and
     * deliberately so: those cells are under the wall rather than in the room, and a wall's footing
     * standing on nothing is the same hole seen from the other side. It stops at the room's own
     * footprint, which is the last coordinate this generator has any business writing.</p>
     */
    private static void line(Map<Coords2D, Integer> dug, RoomData room, int originX, int originZ,
                             int floorY, BlockState liningState, List<BlockPlacement> out) {
        for (Map.Entry<Coords2D, Integer> cell : dug.entrySet()) {
            int depth = cell.getValue();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    Coords2D neighbour = new Coords2D(cell.getKey().getX() + dx,
                            cell.getKey().getY() + dz);
                    int neighbourDepth = dug.getOrDefault(neighbour, 0);
                    if (neighbourDepth >= depth) {
                        continue; // dug at least as deep: there is no face here to back
                    }
                    int x = originX + 1 + neighbour.getX();
                    int z = originZ + 1 + neighbour.getY();
                    if (x < originX || x > originX + room.getWidth() - 1
                            || z < originZ || z > originZ + room.getDepth() - 1) {
                        continue;
                    }
                    for (int y = floorY - depth; y <= floorY - neighbourDepth - 1; y++) {
                        out.add(BlockStateCodec.placement(x, y, z, liningState));
                    }
                }
            }
        }
    }
}
