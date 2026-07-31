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
 * The corridor section of a {@link MotifConfig}: its own floor pair and ceiling, distinct from the
 * room's ({@link FloorConfig}/{@link CeilingConfig}) so corridors can read as rougher passages than
 * the rooms they join.
 *
 * <p>Corridors deliberately have no decorative pattern list &mdash; a border ring or checkerboard
 * needs a room-sized rectangle, and a corridor is a 1-3 cell wide run. The
 * {@code floor}/{@code alternateFloor} pair is rolled per cell at the same 45/55 split
 * {@code BasicFloorGenerator} uses for rooms. Corridor <em>walls</em> come from
 * {@link WallConfig}, shared with rooms.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record CorridorConfig(String floor, String alternateFloor, String ceiling) {

    public static final CorridorConfig DEFAULT =
            new CorridorConfig("minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks");

    public static final Codec<CorridorConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("floor").forGetter(CorridorConfig::floor),
            Codec.STRING.fieldOf("alternateFloor").forGetter(CorridorConfig::alternateFloor),
            Codec.STRING.fieldOf("ceiling").forGetter(CorridorConfig::ceiling)
    ).apply(instance, CorridorConfig::new));

    public BlockState floorState() {
        return BlockStateCodec.block(floor, Blocks.STONE_BRICKS);
    }

    public BlockState alternateFloorState() {
        return BlockStateCodec.block(alternateFloor, Blocks.STONE_BRICKS);
    }

    public BlockState ceilingState() {
        return BlockStateCodec.block(ceiling, Blocks.STONE_BRICKS);
    }
}
