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

import java.util.List;

/**
 * The room floor section of a {@link MotifConfig}: the plain {@code base}/{@code alternateBase}
 * pair, plus the weighted {@code patterns} list of optional decorative treatments laid over it.
 *
 * <p>This is where the two pre-merge systems meet. {@code base}/{@code alternateBase} are the old
 * {@code block_provider} {@code floor_pattern} slots ({@code floor}/{@code alternate_floor}), read
 * by {@code BasicFloorGenerator} and rolled per cell at 45/55; {@code patterns} is the old
 * standalone {@code floor_pattern_config} registry's {@code elements} list, rolled once per room by
 * {@code FloorPatternSelector}. The link between them is the {@code "empty"} pattern type, which
 * selects {@code BasicFloorGenerator} &mdash; i.e. "no decoration, just the base pair" &mdash; so a
 * room that rolls {@code empty} renders exactly the base blocks named here.</p>
 *
 * <p>Setting {@code base} and {@code alternateBase} to the <em>same</em> block makes the floor
 * uniform before weathering, which is how {@code classic} ships: the weathering processor list
 * already produces graduated stone_bricks &rarr; cracked/mossy &rarr; cobblestone &rarr; dirt
 * &rarr; gravel variation, and pre-baking a second block here both duplicated that and skipped the
 * deeper decay stages (the aging chains are keyed on the source block).</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public record FloorConfig(String base, String alternateBase, List<FloorPatternEntry> patterns) {

    /** Plain stone_bricks, no decorative patterns &mdash; the always-plain fallback. */
    public static final FloorConfig DEFAULT = new FloorConfig(
            "minecraft:stone_bricks", "minecraft:stone_bricks",
            List.of(new FloorPatternEntry("empty", 1, 0)));

    public static final Codec<FloorConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("base").forGetter(FloorConfig::base),
            Codec.STRING.fieldOf("alternateBase").forGetter(FloorConfig::alternateBase),
            Codecs.strictOptionalFieldOf(FloorPatternEntry.CODEC.listOf(), "patterns", List.of())
                    .forGetter(FloorConfig::patterns)
    ).apply(instance, FloorConfig::new));

    public BlockState baseState() {
        return BlockStateCodec.block(base, Blocks.STONE_BRICKS);
    }

    public BlockState alternateBaseState() {
        return BlockStateCodec.block(alternateBase, Blocks.STONE_BRICKS);
    }
}
