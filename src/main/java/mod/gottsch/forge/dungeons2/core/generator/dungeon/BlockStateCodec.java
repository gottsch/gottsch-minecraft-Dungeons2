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
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges Minecraft's {@link BlockState} and the loader-agnostic
 * {@link BlockPlacement} POJO.
 *
 * <p>This class is the <strong>one place</strong> in the Phase 2 builder
 * pipeline that depends on {@code net.minecraft.*}. The builders themselves
 * use it to <em>emit</em> placements; the Phase 3 piece renderers use it to
 * <em>resolve</em> placements back into block states for
 * {@code level.setBlock} calls.</p>
 *
 * <p>By centralizing the encode/decode logic here:</p>
 * <ul>
 *     <li>The {@link BlockPlacement} POJO stays Minecraft-free.</li>
 *     <li>Future loader ports (NeoForge 1.21.1) only need to swap
 *         {@code ForgeRegistries.BLOCKS} for the equivalent NeoForge registry
 *         lookup &mdash; the rest of the pipeline doesn't move.</li>
 * </ul>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public final class BlockStateCodec {

    private BlockStateCodec() {}

    /**
     * Encodes a {@link BlockState} into a {@link BlockPlacement} at the given coords.
     *
     * <p>All non-default property values are preserved as stringified entries
     * in {@link BlockPlacement#getProperties()}. Properties matching the
     * block's default state are omitted to keep placements compact.</p>
     */
    public static BlockPlacement placement(int x, int y, int z, BlockState state) {
        Block block = state.getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        String blockId = id != null ? id.toString() : "minecraft:air";

        Map<String, String> props = encodeProperties(state);
        return new BlockPlacement(x, y, z, blockId, props);
    }

    /**
     * Encodes a {@link BlockState}'s non-default property values as a string map.
     * Returns an empty map if no properties differ from the default state.
     */
    public static Map<String, String> encodeProperties(BlockState state) {
        BlockState defaultState = state.getBlock().defaultBlockState();
        Map<String, String> props = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            Comparable<?> value = state.getValue(property);
            Comparable<?> defaultValue = defaultState.getValue(property);
            if (!value.equals(defaultValue)) {
                props.put(property.getName(), stringifyValue(property, value));
            }
        }
        return props;
    }

    /**
     * Resolves a {@link BlockPlacement} back into a {@link BlockState}.
     *
     * <p>Returns {@link Blocks#AIR}'s default state if the registry lookup
     * fails (block id was renamed or removed). Property values that don't
     * round-trip are silently dropped &mdash; the block keeps its default
     * for that property.</p>
     */
    public static BlockState resolve(BlockPlacement placement) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(placement.getBlockId()));
        if (block == null || block == Blocks.AIR) {
            return block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> entry : placement.getProperties().entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property != null) {
                state = applyProperty(state, property, entry.getValue());
            }
        }
        return state;
    }

    // -- private helpers --

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String stringifyValue(Property property, Comparable value) {
        return property.getName(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Comparable<T>> BlockState applyProperty(
            BlockState state, Property<T> property, String stringValue) {
        Optional<T> parsed = property.getValue(stringValue);
        if (parsed.isPresent()) {
            return state.setValue(property, parsed.get());
        }
        return state;
    }
}
