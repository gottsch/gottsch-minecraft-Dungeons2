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
package mod.gottsch.forge.dungeons2.core.config.pit;

import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pit.IPitShapeProvider;

/**
 * The shape of a room's sunken pit &mdash; which interior cells are excavated. Backlog #3.
 *
 * <p>Dispatched on {@code type} over {@link PitShapeRegistry}, with the shape's own fields nested
 * under {@code config}, exactly like the {@code floor}, {@code wall} and {@code ceiling} slots.
 * Unlike the {@code platforms} slot there is no second discriminator: a pit is only ever a hole, so
 * <em>what</em> and <em>where</em> collapse into one question and {@code type} answers it.</p>
 *
 * <h2>The shape says WHERE, the entry says HOW DEEP and IN WHAT</h2>
 * <p>The same split the pillar slot makes, and for the same reason: depth and materials are
 * identical for every cell of a pit, so putting them in the shape would force each new shape to
 * re-declare them and to get them right. A shape is a pure 2D question with nothing to get wrong
 * about blocks.</p>
 *
 * @author Mark Gottschling on Aug 27, 2026
 */
public interface PitShapePattern {

    /**
     * This shape's own codec, as registered. An implementation must return the <em>same</em>
     * instance it was registered with; that identity is how the id is recovered on encode.
     */
    MapCodec<? extends PitShapePattern> codec();

    /**
     * This shape with any {@code $role} in its block fields replaced by the literal the palette in
     * scope names. #65 phase 6, and a {@code default} for the same reason the floor, ceiling and
     * wall hooks are: the registry is open to other mods, and a field only carries a role when its
     * codec is {@code Codecs.BLOCK_ID_OR_ROLE}.
     *
     * <p>{@code inset} takes this default and needs nothing: it is the one shape with no block of
     * its own, because a walkable sunken court is paved by the floor around it.</p>
     */
    default PitShapePattern withRoles(java.util.function.UnaryOperator<String> resolver) {
        return this;
    }

    /** The provider deciding which interior cells are excavated. */
    IPitShapeProvider provider();
}
