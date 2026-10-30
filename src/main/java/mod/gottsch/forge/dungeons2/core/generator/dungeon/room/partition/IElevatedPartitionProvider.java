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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Set;

/**
 * A partition provider that says what each cell of the run is made of, row by row, and not only
 * where the run goes.
 *
 * <p>The surface slots' {@code IDoorAwarePatternProvider} move: an OPTIONAL extension, tested for by
 * {@code RoomPartitionGenerator}, leaving {@link IPartitionShapeProvider} and both shipped shapes
 * untouched. The base contract is a 2D question with one material and one height, which is exactly
 * right for bars and exactly what an arcade is not &mdash; its piers, corbels and keystone are
 * different blocks at different rows, and its openings are not doors.</p>
 *
 * <h2>It is handed the cells it must avoid</h2>
 * <p>The base contract finds out about doorways by being refused. A wall run across the middle of a
 * room meets the middle of the two end walls, which is where the maze puts doors more often than
 * anywhere else, so a refusal-only provider would be refused in most of the rooms it was drawn for.
 * Given the cells, it can move the run over instead. The generator still checks.</p>
 *
 * @author Mark Gottschling on 10/30/2026
 */
public interface IElevatedPartitionProvider extends IPartitionShapeProvider {

    /**
     * The elevation for a room of this interior size, or {@link ElevatedPartitionPlan#EMPTY}.
     *
     * @param blocked interior-local cells the run must not touch: doorway approaches and anything
     *                already built in the room's footprint
     */
    ElevatedPartitionPlan elevate(int interiorWidth, int interiorDepth, Set<Coords2D> blocked,
                                  RandomSource random);

    /**
     * The 2D answer, for a caller that only asks where the run goes: every cell of it as wall, no
     * gap. {@code RoomPartitionGenerator} never asks an elevated provider this.
     */
    @Override
    default PartitionPlan plan(int interiorWidth, int interiorDepth, RandomSource random) {
        ElevatedPartitionPlan elevation = elevate(interiorWidth, interiorDepth, Set.of(), random);
        if (elevation.isEmpty()) {
            return PartitionPlan.EMPTY;
        }
        return new PartitionPlan(elevation.columns().stream()
                .map(ElevatedPartitionPlan.Column::cell).toList(), List.of(), "north");
    }
}
