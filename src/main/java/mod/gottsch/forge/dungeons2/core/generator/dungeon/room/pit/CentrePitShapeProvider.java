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

import mod.gottsch.forge.dungeons2.core.config.CeilingPatternEntry.SurfaceOrient;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A square sunken court in the middle of the room, {@code size} cells on a side, terraced.
 *
 * <p><strong>It never touches the interior's edge</strong>: a size that would reach the wall ring
 * is shrunk until at least one walkable cell survives on every side, and a size that cannot fit at
 * all yields no court. A court flush against a wall is a different feature (you step into it
 * leaving the door) and would want its own provider rather than being what this degrades into.</p>
 *
 * <p>An authored {@code centreBlock} stands one block in the middle cell &mdash; the sunken dais of
 * backlog #86. See {@link mod.gottsch.forge.dungeons2.core.config.pit.CentrePitShape} for what it is
 * for; the parity handling is here, because only this class knows what the size SHRANK to.</p>
 */
public class CentrePitShapeProvider implements IPitShapeProvider {

    private final int size;
    private final int depth;
    private final String rimBlock;
    private final SurfaceOrient rimOrient;
    private final String centreBlock;
    private final Map<String, String> centreProperties;
    private final mod.gottsch.forge.dungeons2.core.config.ChestConfig centreChest;

    public CentrePitShapeProvider(int size, int depth) {
        this(size, depth, null, SurfaceOrient.OUTWARD);
    }

    public CentrePitShapeProvider(int size, int depth, String rimBlock, SurfaceOrient rimOrient) {
        this(size, depth, rimBlock, rimOrient, null, Map.of());
    }

    public CentrePitShapeProvider(int size, int depth, String rimBlock, SurfaceOrient rimOrient,
                                  String centreBlock, Map<String, String> centreProperties) {
        this(size, depth, rimBlock, rimOrient, centreBlock, centreProperties, null);
    }

    public CentrePitShapeProvider(int size, int depth, String rimBlock, SurfaceOrient rimOrient,
                                  String centreBlock, Map<String, String> centreProperties,
                                  mod.gottsch.forge.dungeons2.core.config.ChestConfig centreChest) {
        this.size = size;
        this.depth = depth;
        this.rimBlock = rimBlock;
        this.rimOrient = rimOrient;
        this.centreBlock = centreBlock;
        this.centreProperties = centreProperties == null ? Map.of() : centreProperties;
        this.centreChest = centreChest;
    }

    /** Whether anything is authored to stand in the middle cell -- a block, or a chest. */
    private boolean hasCentre() {
        return (centreBlock != null && !centreBlock.isEmpty()) || centreChest != null;
    }

    @Override
    public PitPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        // Leave a walkable ring: the widest court an interior can hold is two cells short of it.
        int fit = Math.min(size, Math.min(interiorWidth - 2, interiorDepth - 2));
        // A centrepiece needs a middle cell to stand in, and `size` is a MAXIMUM -- an odd authored
        // size shrinks to an even fit in a tight interior, which the codec's parity check cannot
        // see. Step down rather than drop the court: one cell narrower still reads as the same
        // feature, where a court that silently vanishes in small rooms is the bug this whole item
        // exists to avoid.
        if (hasCentre() && fit % 2 == 0) {
            fit--;
        }
        if (fit < 1) {
            return PitPlan.empty();
        }
        Set<Coords2D> footprint = new HashSet<>();
        int startX = (interiorWidth - fit) / 2;
        int startZ = (interiorDepth - fit) / 2;
        for (int x = 0; x < fit; x++) {
            for (int z = 0; z < fit; z++) {
                footprint.add(new Coords2D(startX + x, startZ + z));
            }
        }
        // RoomPitGenerator writes a fill at the terrace floor + 1, so at depth 1 the centrepiece's
        // top lands flush with the room's own walking plane: something stood around, not a pillar
        // rising out of a hole.
        Map<Coords2D, BlockState> fills = Map.of();
        Map<Coords2D, mod.gottsch.forge.dungeons2.core.data.BlockEntityData> fillData = Map.of();
        Coords2D middle = new Coords2D(startX + fit / 2, startZ + fit / 2);
        if (centreChest != null) {
            // Drawn through RoomChestGenerator, not built here: the weighted variant, the weighted
            // table and the non-zero loot seed are its rules, and an unresolvable table means NO
            // chest rather than an empty one -- which is why this can come back empty and leave the
            // court bare.
            var drawn = mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomChestGenerator
                    .drawChest(centreChest, random);
            if (drawn.isPresent()) {
                Map<Coords2D, BlockState> single = new HashMap<>();
                single.put(middle, BlockStateCodec.withProperties(
                        BlockStateCodec.block(drawn.get().block(), Blocks.CHEST),
                        Map.of("facing", mod.gottsch.forge.dungeons2.core.generator.dungeon.room
                                .RoomChestGenerator.randomHorizontalFacing(random))));
                fills = single;
                Map<Coords2D, mod.gottsch.forge.dungeons2.core.data.BlockEntityData> data =
                        new HashMap<>();
                data.put(middle, drawn.get().data());
                fillData = data;
            }
        } else if (hasCentre()) {
            BlockState centre = BlockStateCodec.withProperties(
                    BlockStateCodec.block(centreBlock, Blocks.CHISELED_STONE_BRICKS),
                    centreProperties);
            Map<Coords2D, BlockState> single = new HashMap<>();
            single.put(middle, centre);
            fills = single;
        }
        if (rimBlock == null || rimBlock.isEmpty()) {
            return new PitPlan(PitPlans.terraced(footprint, depth), fills, Map.of(), fillData);
        }
        return new PitPlan(PitPlans.terraced(footprint, depth), fills,
                PitPlans.stairRim(footprint,
                        BlockStateCodec.block(rimBlock, Blocks.COBBLESTONE_STAIRS), rimOrient),
                fillData);
    }
}
