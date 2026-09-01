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
package mod.gottsch.forge.dungeons2.core.config.wall;

import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.surface.ISurfacePatternProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * One authored wall treatment: its config, and the provider that draws it.
 *
 * <h2>The worst of the five flat records, and what it cost</h2>
 * <p>{@code WallPatternEntry.PatternEntry} carried <strong>fourteen</strong> fields for four types
 * with near-disjoint needs. {@code courses} meant nothing to {@code panels}, {@code width} nothing
 * outside {@code panels}, {@code base_block}/{@code cap_block} nothing outside the pilasters. Each was
 * a silent no-op, and the schema could not say so because every type shared the record.</p>
 *
 * <p><strong>Three validation rules disappear as a result</strong>, none of them deleted &mdash;
 * all three became impossible to author. {@code WallPatternEntry.validate} rejected {@code courses}
 * on a non-courses pattern, {@code block} on a courses pattern, and a missing {@code block} on a
 * type that needs one. The first two are now stray keys; the third is a required {@code fieldOf} on
 * the two types that have it. Every one of those checks existed only to police a record that was
 * wider than any of its types.</p>
 *
 * <p>Everything moves in, including {@code projection} and {@code orient}: unlike the ceiling,
 * where {@code projection} positions a pattern in a layer stack and stays on the entry, a wall
 * pattern's projection is its own shape. {@code WallPatternEntry.PatternEntry} is left holding the
 * pattern and its gate.</p>
 */
public interface WallPattern {

    /**
     * This pattern's own codec, as registered. An implementation must return the <em>same</em>
     * instance it was registered with; that identity is how the id is recovered on encode.
     */
    MapCodec<? extends WallPattern> codec();

    /**
     * The provider that draws this pattern, or {@code null} when a block it needs will not resolve.
     *
     * <p>{@code null} degrades the <em>whole</em> pattern to plain wall rather than drawing the
     * rest of it: a half-drawn treatment reads as a bug, where a plain wall reads as a plain wall.
     * That is unchanged.</p>
     */
    ISurfacePatternProvider provider();

    /** The default state of {@code id} with {@code properties} applied, or null if it won't resolve. */
    static BlockState state(String id, Map<String, String> properties) {
        Block block = BlockStateCodec.blockOrNull(id);
        return block == null ? null : BlockStateCodec.withProperties(block.defaultBlockState(), properties);
    }
}
