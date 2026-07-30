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
 * using {@code inset} (only meaningful for that type).</p>
 *
 * @author Mark Gottschling on Jul 30, 2026
 */
public record FloorPatternEntry(String type, int weight, int inset) {

    public static final Codec<FloorPatternEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(FloorPatternEntry::type),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(FloorPatternEntry::weight),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("inset", FloorBorderPatternProvider.DEFAULT_INSET)
                    .forGetter(FloorPatternEntry::inset)
    ).apply(instance, FloorPatternEntry::new));
}
