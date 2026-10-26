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

import mod.gottsch.forge.dungeons2.core.map.DungeonMaps;
import mod.gottsch.forge.dungeons2.core.map.FloorMapStamp;
import mod.gottsch.forge.dungeons2.core.world.DungeonFloorIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * A crafted Dungeon Map, not yet started: use it on a dungeon floor and it becomes a map of that
 * floor, which fills in as you explore (see {@link DungeonMaps}) - the same map a chest can hold,
 * for a dungeon whose chests turn up none. The floor is looked up in the {@link DungeonFloorIndex}.
 *
 * <p>Anywhere that is not a dungeon floor - outside, on the stairs between floors, or in a dungeon
 * generated before the index existed - it says so and stays blank.</p>
 *
 * <p>The new map always goes into the hand that used the blank, and any blanks left - the rest of a
 * stack, or the one creative never uses up - go into the inventory. Vanilla's Empty Map does the
 * opposite, keeping the blanks in hand and slipping the new map into the inventory, which in
 * creative looks like nothing happened at all (Mark, 2026-10-26: twenty clicks, twenty unseen maps).
 * A Dungeon Map draws only while held, so it belongs in the hand.</p>
 *
 * @author Mark Gottschling on 10/25/2026
 */
public class BlankDungeonMapItem extends Item {

    public BlankDungeonMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dungeons2.blank_dungeon_map").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack blank = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(blank, true);
        }
        Optional<FloorMapStamp> floor = DungeonFloorIndex.get(serverLevel).floorAt(player.blockPosition());
        if (floor.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dungeons2.dungeon_map.no_floor"), true);
            return InteractionResultHolder.fail(blank);
        }
        ItemStack map = DungeonMaps.create(serverLevel, floor.get());
        if (!player.getAbilities().instabuild) {
            blank.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        level.playSound(null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
        ItemStack rest = blank.copy();
        // the map into the hand FIRST: added to the inventory while the blanks still sat in the hand,
        // the rest would merge back into that slot and be overwritten along with it
        player.setItemInHand(hand, map);
        if (!rest.isEmpty() && !player.getInventory().add(rest)) {
            player.drop(rest, false);
        }
        return InteractionResultHolder.consume(map);
    }
}
