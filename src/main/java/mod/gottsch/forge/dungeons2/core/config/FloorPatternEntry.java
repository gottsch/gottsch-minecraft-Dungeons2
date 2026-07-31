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
package mod.gottsch.forge.dungeons2.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorBorderPatternProvider;

import java.util.Optional;

/**
 * One weighted option in a {@link FloorPatternConfig}. {@code type} is a plain string
 * discriminator rather than an enum &mdash; deliberately, matching the reasoning already written
 * into {@code FloorBorderPattern}'s TODOs about not over-committing to an enum-per-decorator
 * shape before there's more than one real pattern to compare it against.
 *
 * <p>{@code "empty"} (or any unrecognized type) means no special pattern &mdash; the room's floor
 * generator falls back to the plain/alternating {@code BasicFloorGenerator}, same as an absent
 * config entry always degrades gracefully elsewhere in this codebase. {@code "border"} selects
 * {@link mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.FloorBorderPatternProvider},
 * using {@code inset} and the three optional block-id fields below (only meaningful for that
 * type).</p>
 *
 * <p>{@code cornerBlock}/{@code edgeLeftBlock}/{@code edgeRightBlock} are resource-location
 * strings (e.g. {@code "minecraft:polished_andesite"}) substituting the border pattern's default
 * {@code dungeonblocks:left_large_stone_brick}/{@code right_large_stone_brick} pieces per slot.
 * Any left absent (or pointing at an id that doesn't resolve) falls back to that slot's default
 * independently &mdash; you don't have to specify all three to override one. Set
 * {@code edgeLeftBlock} and {@code edgeRightBlock} to the <em>same</em> id for a single-block
 * edge with no left/right texture variant (e.g. a plain stone type); leave {@code cornerBlock}
 * unset to have corners match {@code edgeRightBlock} automatically.</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public record FloorPatternEntry(String type, int weight, int inset,
                                 Optional<String> cornerBlock,
                                 Optional<String> edgeLeftBlock,
                                 Optional<String> edgeRightBlock) {

    /** Convenience for entries that don't need block substitution (e.g. {@code "empty"}). */
    public FloorPatternEntry(String type, int weight, int inset) {
        this(type, weight, inset, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static final Codec<FloorPatternEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(FloorPatternEntry::type),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(FloorPatternEntry::weight),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("inset", FloorBorderPatternProvider.DEFAULT_INSET)
                    .forGetter(FloorPatternEntry::inset),
            Codec.STRING.optionalFieldOf("cornerBlock").forGetter(FloorPatternEntry::cornerBlock),
            Codec.STRING.optionalFieldOf("edgeLeftBlock").forGetter(FloorPatternEntry::edgeLeftBlock),
            Codec.STRING.optionalFieldOf("edgeRightBlock").forGetter(FloorPatternEntry::edgeRightBlock)
    ).apply(instance, FloorPatternEntry::new));
}
