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
package mod.gottsch.forge.dungeons2.core.config.floor;

import com.mojang.serialization.MapCodec;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;

/**
 * One authored floor treatment: the <em>config</em> for a pattern, plus the ability to build the
 * {@link IDungeonFloorGenerator} that draws it.
 *
 * <h2>What this replaces, and why</h2>
 * <p>Until now a floor treatment was a {@code type} string switched over in
 * {@code FloorPatternSelector}, reading its arguments out of one flat record that carried
 * <em>every</em> pattern's slots at once &mdash; {@code cornerBlock}, {@code edgeLeftBlock},
 * {@code edgeRightBlock}, {@code primaryBlock}, {@code secondaryBlock}, {@code probability},
 * {@code thickness}, {@code spokes}, {@code inset}. That shape only works because the selector
 * privately knows which fields each type reads, which has two costs: the set of types is closed
 * to this mod, and nothing stops an author writing {@code spokes} on a {@code border} entry.</p>
 *
 * <p>Each implementation now owns its own {@link MapCodec}, so it declares exactly the fields it
 * uses and no others, and the set of implementations is open &mdash; see
 * {@link FloorPatternRegistry}.</p>
 *
 * <h2>Implementations declare only their own fields</h2>
 * <p>A pattern's codec is nested under {@code config} rather than flattened alongside {@code type}.
 * That is forced by the closed-schema rule (#31): {@code Codecs#closed} derives its permitted key
 * set from {@link MapCodec#keys}, and a dispatching codec cannot know its subtype's keys until it
 * has read {@code type} &mdash; so a flat layout would reject every type-specific field. Nesting
 * keeps both levels closed with no bespoke codec machinery, and has the side benefit that a
 * pattern's arguments are visibly scoped to the pattern.</p>
 *
 * @see FloorPatternRegistry
 */
public interface FloorPattern {

    /**
     * This pattern's own codec, as registered. Used to write it back out; the registry id is
     * recovered by looking this up, which is why an implementation must return the <em>same</em>
     * codec instance it was registered with.
     */
    MapCodec<? extends FloorPattern> codec();

    /**
     * The generator that draws this pattern.
     *
     * @param config the motif or stratum's floor materials, for the patterns that fill their
     *               unmarked cells from it ({@code cross} and {@code spokes} draw their accent over
     *               the config's own base; {@code border} fills its interior with it). A pattern
     *               that names every block it draws ignores this.
     */
    IDungeonFloorGenerator generator(FloorConfig config);
}
