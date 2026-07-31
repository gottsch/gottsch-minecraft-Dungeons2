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
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The doorway section of a {@link MotifConfig}: the door itself plus the sill ({@code floor}) it
 * stands on and the {@code lintel} above it. See {@code BasicDoorGenerator} for the 4-block column
 * these fill.
 *
 * <p>{@code door} is expected to be an actual {@code DoorBlock} &mdash; the generator sets
 * {@code FACING}/{@code HALF} on it. Its fallback is {@code minecraft:oak_door} rather than the
 * stone_bricks the other slots use, since a non-door block there would silently lose those
 * properties.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record DoorConfig(String door, String lintel, String floor) {

    public static final DoorConfig DEFAULT =
            new DoorConfig("minecraft:oak_door", "minecraft:stone_bricks", "minecraft:stone_bricks");

    public static final Codec<DoorConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("door").forGetter(DoorConfig::door),
            Codec.STRING.fieldOf("lintel").forGetter(DoorConfig::lintel),
            Codec.STRING.fieldOf("floor").forGetter(DoorConfig::floor)
    ).apply(instance, DoorConfig::new));

    public BlockState doorState() {
        return BlockStateCodec.block(door, Blocks.OAK_DOOR);
    }

    public BlockState lintelState() {
        return BlockStateCodec.block(lintel, Blocks.STONE_BRICKS);
    }

    public BlockState floorState() {
        return BlockStateCodec.block(floor, Blocks.STONE_BRICKS);
    }
}
