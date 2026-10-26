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
package mod.gottsch.forge.dungeons2.core.event;

import mod.gottsch.forge.dungeons2.Dungeons;
import mod.gottsch.forge.dungeons2.core.map.DungeonMaps;
import mod.gottsch.forge.dungeons2.core.map.FloorMapStamp;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws a held Dungeon Map as its holder explores, and tidies its tooltip. See {@link DungeonMaps}.
 *
 * @author Mark Gottschling on 10/25/2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DungeonMapEvent {

    /** Either hand, as a vanilla map draws in either. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player.level() instanceof ServerLevel level)
                || event.player.tickCount % DungeonMaps.REVEAL_INTERVAL != 0) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = event.player.getItemInHand(hand);
            FloorMapStamp stamp = DungeonMaps.stampOf(stack);
            if (stamp != null) {
                DungeonMaps.reveal(level, event.player, stack, stamp);
            }
        }
    }

    /**
     * Drops vanilla's "Locked" line. The data is locked only so vanilla will not paint the surface
     * over the floor; to the player the map is not locked at all - it fills in as they go.
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (DungeonMaps.stampOf(event.getItemStack()) == null) {
            return;
        }
        event.getToolTip().removeIf(DungeonMapEvent::isLockedLine);
    }

    private static boolean isLockedLine(Component line) {
        return line.getContents() instanceof TranslatableContents contents
                && "filled_map.locked".equals(contents.getKey());
    }
}
