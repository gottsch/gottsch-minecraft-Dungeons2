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

import mod.gottsch.forge.dungeons2.core.config.pit.GratePitShape.Liquid;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A grate flush in the floor over a sheer cavity, dry, flooded or molten. See
 * {@link mod.gottsch.forge.dungeons2.core.config.pit.GratePitShape} for the fields and why this is a
 * pit rather than a floor pattern.
 *
 * <p>The cavity is dug {@link PitPlans#sheer sheer}, like {@code hazard}'s shaft, but nobody falls
 * in: the grate is the lid, and {@code RoomPitGenerator} writes it last in each cell, after the
 * clearing and the flood.</p>
 */
public class GratePitShapeProvider implements IPitShapeProvider {

    private final String grateBlock;
    private final Map<String, String> grateProperties;
    private final int size;
    private final int depth;
    private final Liquid liquid;
    private final int offsetX;
    private final int offsetZ;

    public GratePitShapeProvider(String grateBlock, Map<String, String> grateProperties, int size,
                                 int depth, Liquid liquid, int offsetX, int offsetZ) {
        this.grateBlock = grateBlock;
        this.grateProperties = grateProperties;
        this.size = size;
        this.depth = depth;
        this.liquid = liquid;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
    }

    @Override
    public PitPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        // The walkable-ring rule every pit keeps: a sump you cannot walk round blocks the room.
        int fit = Math.min(size, Math.min(interiorWidth - 2, interiorDepth - 2));
        if (fit < 1) {
            return PitPlan.empty();
        }
        int startX = clamp((interiorWidth - fit) / 2 + offsetX, interiorWidth, fit);
        int startZ = clamp((interiorDepth - fit) / 2 + offsetZ, interiorDepth, fit);

        Set<Coords2D> footprint = new HashSet<>();
        for (int x = 0; x < fit; x++) {
            for (int z = 0; z < fit; z++) {
                footprint.add(new Coords2D(startX + x, startZ + z));
            }
        }

        // Author's properties first, then the liquid's word on waterlogged: an unset flag takes
        // dungeonblocks' default, which is TRUE, and would flood a dry sump's lid.
        BlockState grate = BlockStateCodec.withProperties(
                BlockStateCodec.block(grateBlock, Blocks.IRON_BARS), grateProperties);
        grate = BlockStateCodec.withProperties(grate,
                Map.of("waterlogged", String.valueOf(liquid == Liquid.WATER)));

        BlockState fluid = switch (liquid) {
            case WATER -> Blocks.WATER.defaultBlockState();
            case LAVA -> Blocks.LAVA.defaultBlockState();
            case NONE -> null;
        };

        Map<Coords2D, BlockState> cover = new HashMap<>();
        Map<Coords2D, BlockState> flood = new HashMap<>();
        for (Coords2D cell : footprint) {
            cover.put(cell, grate);
            if (fluid != null) {
                flood.put(cell, fluid);
            }
        }
        return new PitPlan(PitPlans.sheer(footprint, depth), Map.of(), Map.of(), Map.of(),
                flood, cover);
    }

    /** Keeps an offset sump inside the interior's walkable ring rather than under a wall. */
    private static int clamp(int start, int interior, int fit) {
        return Math.max(1, Math.min(start, interior - fit - 1));
    }
}
