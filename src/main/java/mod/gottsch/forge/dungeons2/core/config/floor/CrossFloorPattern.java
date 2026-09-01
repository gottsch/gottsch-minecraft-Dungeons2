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

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mod.gottsch.forge.dungeons2.core.config.Codecs;
import mod.gottsch.forge.dungeons2.core.config.FloorConfig;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.CrossFloorPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.floor.IDungeonFloorGenerator;
import net.minecraft.world.level.block.Block;

/**
 * An accent cross through the room's centre, {@code thickness} cells wide, over the
 * {@link FloorConfig}'s own base. Also usable as a composite overlay.
 */
public record CrossFloorPattern(int thickness, String block) implements FloorPattern {

    public static final String NAME = "cross";

    public static final MapCodec<CrossFloorPattern> CODEC = Codecs.closedMap(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codecs.strictOptionalFieldOf(Codec.intRange(0, Integer.MAX_VALUE), "thickness",
                                    CrossFloorPatternProvider.DEFAULT_THICKNESS)
                            .forGetter(CrossFloorPattern::thickness),
                    // `block`, not `primaryBlock`. The flat record reused primaryBlock across four
                    // unrelated patterns because it had only one set of slots to spend; each type
                    // now names its own field, so it can name it after what it actually is.
                    Codecs.BLOCK_ID.fieldOf("block").forGetter(CrossFloorPattern::block)
            ).apply(instance, CrossFloorPattern::new)));

    @Override
    public MapCodec<? extends FloorPattern> codec() {
        return CODEC;
    }

    @Override
    public IDungeonFloorGenerator generator(FloorConfig config) {
        Block accent = FloorPatterns.block(block);
        return accent == null
                ? PlainFloorPattern.INSTANCE.generator(config)
                : new CrossFloorPatternProvider(thickness, accent, config.baseState());
    }
}
