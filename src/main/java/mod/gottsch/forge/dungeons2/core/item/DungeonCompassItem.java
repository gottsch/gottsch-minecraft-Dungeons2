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
package mod.gottsch.forge.dungeons2.core.item;

import mod.gottsch.forge.dungeons2.core.world.DungeonLocator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Points to the nearest dungeon. Loot only - no recipe - so a player still finds their first
 * dungeon by exploring. Near a dungeon it points at that one; walk away and, once another is
 * nearer, it swings to that (Mark, 2026-10-26: no skipping, no re-aiming by hand).
 *
 * <p>Searched on the server from where its holder stands: the first time it sits in an inventory,
 * then again whenever the holder is {@link #RESEARCH_DISTANCE} blocks from the last search, or in
 * another dimension. A search where nothing is in reach leaves it spinning, as a compass does in
 * the Nether, until the next one.</p>
 *
 * <p>Rendered by vanilla's own needle ({@code CompassItemPropertyFunction}), registered for this
 * item in {@code ClientSetup}; in another dimension it spins.</p>
 *
 * @author Mark Gottschling on 10/26/2026
 */
public class DungeonCompassItem extends Item {

    private static final String TAG = "D2DungeonCompass";
    private static final String TARGET = "Target";
    private static final String DIMENSION = "Dimension";
    private static final String SEARCHED_FROM = "SearchedFrom";

    /** D2 dungeons are at least 256 blocks apart, so which is nearest changes slowly. */
    static final int RESEARCH_DISTANCE = 32;
    /** Placement regions searched outwards: 40 chunks each, so about 5,000 blocks. */
    static final int MAX_RINGS = 8;

    public DungeonCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos from = entity.blockPosition();
        String dimension = level.dimension().location().toString();
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag != null && dimension.equals(tag.getString(DIMENSION)) && tag.contains(SEARCHED_FROM)) {
            BlockPos last = NbtUtils.readBlockPos(tag.getCompound(SEARCHED_FROM));
            long dx = from.getX() - last.getX();
            long dz = from.getZ() - last.getZ();
            if (dx * dx + dz * dz < (long) RESEARCH_DISTANCE * RESEARCH_DISTANCE) {
                return;
            }
        }
        CompoundTag fresh = new CompoundTag();
        fresh.putString(DIMENSION, dimension);
        fresh.put(SEARCHED_FROM, NbtUtils.writeBlockPos(from));
        DungeonLocator.nearest(serverLevel, from, MAX_RINGS)
                .ifPresent(pos -> fresh.put(TARGET, NbtUtils.writeBlockPos(pos)));
        stack.addTagElement(TAG, fresh);
    }

    /** A re-search rewrites the tag; without this the held compass would bob every 32 blocks. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    /** Where the needle points, or null to spin. Read on the client from the synced stack. */
    @Nullable
    public static GlobalPos target(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        if (tag == null || !tag.contains(TARGET)) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(DIMENSION));
        if (dimension == null) {
            return null;
        }
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound(TARGET));
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), pos);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = stack.getTagElement(TAG) != null && target(stack) == null
                ? "tooltip.dungeons2.dungeon_compass.none" : "tooltip.dungeons2.dungeon_compass";
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
