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
 * The room floor <em>materials</em> of a {@link MotifConfig}: the plain {@code base}/{@code
 * alternateBase} pair that {@code BasicFloorGenerator} rolls per cell at 45/55, and that every
 * decorative floor pattern draws its unmarked cells from.
 *
 * <p>Decoration is <em>not</em> here. This record held a weighted {@code patterns} list until the
 * scheme migration moved that roll up to {@link MotifConfig#schemes}, so that a room's floor,
 * walls and ceiling are chosen together rather than independently &mdash; see {@link RoomScheme}.
 * The split this leaves is a clean one and worth keeping to: the element sections of a motif config
 * say what the motif is <em>made of</em>, and the scheme list says how a room is <em>dressed</em>.
 * </p>
 *
 * <p>Setting {@code base} and {@code alternateBase} to the <em>same</em> block makes the floor
 * uniform before weathering, which is how {@code classic} ships: the weathering processor list
 * already produces graduated stone_bricks &rarr; cracked/mossy &rarr; cobblestone &rarr; dirt
 * &rarr; gravel variation, and pre-baking a second block here both duplicated that and skipped the
 * deeper decay stages (the aging chains are keyed on the source block).</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record FloorConfig(String base, String alternateBase) {

    /** Plain stone_bricks &mdash; the always-plain fallback. */
    public static final FloorConfig DEFAULT = new FloorConfig(
            "minecraft:stone_bricks", "minecraft:stone_bricks");

    public static final Codec<FloorConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("base").forGetter(FloorConfig::base),
            Codec.STRING.fieldOf("alternateBase").forGetter(FloorConfig::alternateBase)
    ).apply(instance, FloorConfig::new));

    public BlockState baseState() {
        return BlockStateCodec.block(base, Blocks.STONE_BRICKS);
    }

    public BlockState alternateBaseState() {
        return BlockStateCodec.block(alternateBase, Blocks.STONE_BRICKS);
    }
}
