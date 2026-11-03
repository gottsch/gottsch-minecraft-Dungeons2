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
package mod.gottsch.neo.dungeons2.core.event;

import net.neoforged.fml.common.EventBusSubscriber;
import mod.gottsch.neo.dungeons2.Dungeons;
import mod.gottsch.neo.dungeons2.core.map.DungeonMaps;
import mod.gottsch.neo.dungeons2.core.map.FloorMapStamp;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.List;

/**
 * Draws a held Dungeon Map as its holder explores, and tidies its tooltip. See {@link DungeonMaps}.
 *
 * @author Mark Gottschling on 10/25/2026
 */
@EventBusSubscriber(modid = Dungeons.MOD_ID)
public class DungeonMapEvent {

    /** Either hand, as a vanilla map draws in either. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || event.getEntity().tickCount % DungeonMaps.REVEAL_INTERVAL != 0) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = event.getEntity().getItemInHand(hand);
            FloorMapStamp stamp = DungeonMaps.stampOf(stack);
            if (stamp != null) {
                DungeonMaps.reveal(level, event.getEntity(), stack, stamp);
            }
        }
    }

    /**
     * Drops vanilla's "Locked" line and says how the map works instead. The data is locked only so
     * vanilla will not paint the surface over the floor; to the player the map is not locked at all
     * - it fills in as they go. The lines go straight after the name, ahead of anything advanced
     * tooltips add.
     */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        FloorMapStamp stamp = DungeonMaps.stampOf(event.getItemStack());
        if (stamp == null) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        tooltip.removeIf(DungeonMapEvent::isLockedLine);
        int at = Math.min(1, tooltip.size());
        tooltip.add(at, Component.translatable("tooltip.dungeons2.dungeon_map.copy").withStyle(ChatFormatting.GRAY));
        tooltip.add(at, Component.translatable("tooltip.dungeons2.dungeon_map.key").withStyle(ChatFormatting.GRAY));
        tooltip.add(at, Component.translatable("tooltip.dungeons2.dungeon_map").withStyle(ChatFormatting.GRAY));
        if (stamp.hasDungeon()) {
            tooltip.add(at, Component.translatable("tooltip.dungeons2.dungeon_map.near",
                    stamp.dungeonX(), stamp.dungeonZ()).withStyle(ChatFormatting.DARK_GRAY));
        }
        // Why the marker is missing: a map draws only its own dungeon's own level. Added below the
        // name, so it is the first thing read.
        Player player = event.getEntity();
        if (player != null) {
            if (!stamp.covers(player.getX(), player.getZ(), NEAR)) {
                tooltip.add(at, Component.translatable("tooltip.dungeons2.dungeon_map.elsewhere")
                        .withStyle(ChatFormatting.RED));
            } else if (!DungeonMaps.onLevel(stamp, player.getY())) {
                tooltip.add(at, Component.translatable("tooltip.dungeons2.dungeon_map.other_level")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    /** How far off a floor's footprint still counts as in its dungeon: a doorway's width or so. */
    private static final int NEAR = 4;

    private static boolean isLockedLine(Component line) {
        return line.getContents() instanceof TranslatableContents contents
                && "filled_map.locked".equals(contents.getKey());
    }
}
