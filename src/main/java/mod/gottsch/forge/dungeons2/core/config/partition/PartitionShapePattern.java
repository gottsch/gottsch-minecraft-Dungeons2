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
package mod.gottsch.forge.dungeons2.core.config.partition;

import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.partition.IPartitionShapeProvider;

/**
 * Where a room's interior wall runs. Backlog #74.
 *
 * <p>Dispatched on {@code type} over {@link PartitionShapeRegistry}, with the shape's own fields
 * nested under {@code config}, exactly like the {@code pit} slot. The seventh of these registries,
 * and it follows {@code PitShapePattern}'s shape line for line &mdash; one discriminator, no
 * vocabulary shared with another slot.</p>
 *
 * <h2>The shape says WHERE, the entry says HOW TALL and IN WHAT</h2>
 * <p>Height, material and the width of the way through are identical for every cell of a partition,
 * so they live on {@code PartitionPatternEntry}. A shape is a pure 2D question with nothing to get
 * wrong about blocks &mdash; which is also why neither shipped shape needs {@link #withRoles}.</p>
 *
 * @author Mark Gottschling on Sep 2, 2026
 */
public interface PartitionShapePattern {

    /**
     * This shape's own codec, as registered. An implementation must return the <em>same</em>
     * instance it was registered with; that identity is how the id is recovered on encode.
     */
    MapCodec<? extends PartitionShapePattern> codec();

    /**
     * This shape with any {@code $role} in its block fields replaced by the literal the palette in
     * scope names. A {@code default} because the registry is open to other mods and neither shipped
     * shape names a block at all &mdash; the partition's one material is the entry's.
     */
    default PartitionShapePattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        return this;
    }

    /** The provider deciding which interior cells the run occupies, and where the way through is. */
    IPartitionShapeProvider provider();
}
