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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Runs one base {@link IDungeonFloorGenerator}'s full floor fill, then layers each {@link
 * IFloorOverlayGenerator} on top of it in order &mdash; e.g. a {@link
 * CheckerboardFloorPatternProvider} base with a {@link FloorBorderPatternProvider} ring overlaid,
 * for a {@code "composite"} {@code floor_pattern_config} entry.
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public class CompositeFloorPatternProvider implements IDungeonFloorGenerator {

    private final IDungeonFloorGenerator base;
    private final List<IFloorOverlayGenerator> overlays;

    public CompositeFloorPatternProvider(IDungeonFloorGenerator base, List<IFloorOverlayGenerator> overlays) {
        this.base = base;
        this.overlays = List.copyOf(overlays);
    }

    @Override
    public void build(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out) {
        base.build(room, floorY, motif, random, out);
        for (IFloorOverlayGenerator overlay : overlays) {
            overlay.overlay(room, floorY, motif, random, out);
        }
    }
}
