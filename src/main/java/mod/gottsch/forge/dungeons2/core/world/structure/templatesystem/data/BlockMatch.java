/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A set of blocks named either individually or by tag &mdash; which blocks a
 * {@link mod.gottsch.forge.dungeons2.core.world.structure.templatesystem.DecorationProcessor}
 * behaviour applies <em>to</em> (as opposed to a {@link DecorationRule}'s palette, which is
 * what it places).
 *
 * <p>Tags matter here because the interesting sets are large and already exist:
 * {@code minecraft:dirt} covers the whole dirt family, and DungeonBlocks ships
 * {@code dungeonblocks:corbels} / {@code dungeonblocks:ledges} covering one variant per stone
 * type. Enumerating those by hand in a datapack would be miserable and would silently rot
 * whenever a variant is added.</p>
 *
 * <p>Both fields are optional and the match is a union, so
 * {@code {"tags": ["dungeonblocks:corbels"], "blocks": ["minecraft:stone_brick_stairs"]}}
 * is a tag plus an extra.</p>
 *
 * <p><strong>Test note:</strong> {@code BlockState#is(TagKey)} answers from the block's
 * holder, which only carries tags once a server has bound them. In a bare
 * {@code Bootstrap.bootStrap()} unit test every tag match is therefore {@code false} &mdash;
 * test the {@code blocks} path, not the {@code tags} path.</p>
 *
 * @author Mark Gottschling on Jul 28, 2026
 */
public record BlockMatch(List<Block> blocks, List<TagKey<Block>> tags) {

    public static final BlockMatch NONE = new BlockMatch(List.of(), List.of());

    public static final Codec<BlockMatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().listOf()
                    .optionalFieldOf("blocks", List.of()).forGetter(BlockMatch::blocks),
            TagKey.codec(Registries.BLOCK).listOf()
                    .optionalFieldOf("tags", List.of()).forGetter(BlockMatch::tags)
    ).apply(instance, BlockMatch::new));

    public BlockMatch {
        blocks = List.copyOf(blocks);
        tags = List.copyOf(tags);
    }

    public boolean isEmpty() {
        return blocks.isEmpty() && tags.isEmpty();
    }

    public boolean matches(BlockState state) {
        for (Block block : blocks) {
            if (state.is(block)) {
                return true;
            }
        }
        for (TagKey<Block> tag : tags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
