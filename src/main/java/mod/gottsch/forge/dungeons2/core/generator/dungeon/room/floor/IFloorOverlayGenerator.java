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
 * Implemented by an {@link IDungeonFloorGenerator} that can also be layered <em>on top of</em>
 * another generator's full floor fill, e.g. {@link FloorBorderPatternProvider}'s ring drawn over
 * a {@link CheckerboardFloorPatternProvider} fill. Unlike {@link IDungeonFloorGenerator#build},
 * {@code overlay} only emits placements for the cells it actually wants to change &mdash; it must
 * never emit a "plain floor" placement for cells outside its own pattern, or it would stomp
 * whatever the base generator already put there.
 *
 * <p>Used by {@link CompositeFloorPatternProvider}, which runs one base generator's {@code build}
 * followed by each overlay's {@code overlay}, in order.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public interface IFloorOverlayGenerator {
    void overlay(RoomData room, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out);
}
