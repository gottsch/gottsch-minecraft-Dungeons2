/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.door;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Renders a single doorway ({@link DoorData}) as a 4-block column of
 * {@link BlockPlacement}s (floor sill, two door halves, lintel above).
 *
 * <p>Unlike the corridor builder, this one doesn't need the surrounding grid
 * &mdash; {@link DoorData#getFacing()} already carries the orientation the
 * door should open along.</p>
 */
public interface IDoorGenerator {
    void build(DoorData door, int floorY, IDungeonMotif motif, RandomSource random, List<BlockPlacement> out);
}
