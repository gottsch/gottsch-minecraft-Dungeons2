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

import mod.gottsch.forge.dungeons2.core.event.ShockAbsorptionEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Backlog #97: the Stone Colossus's counter-item. What it actually does lives in
 * {@link ShockAbsorptionEvent} &mdash; this class is the item, the tooltip, and nothing else.
 *
 * <h2>Why the behaviour is not in here</h2>
 * <p>An {@link ArmorItem} gets no callback when its wearer is hurt, so the mitigation has to be an
 * event handler whichever class it is written in. Putting it in the handler and leaving the item
 * inert keeps the mod's one worn item the same shape as its one worn <em>rule</em>: the colossus
 * knows nothing about the boots, the boots know nothing about the colossus, and the damage type is
 * the only thing either end agrees on.</p>
 *
 * <h2>The tooltip is the teaching</h2>
 * <p>A player who finds these will not have read the backlog. The line names the attack in the
 * words the fight uses &mdash; the thing that slams the floor &mdash; rather than saying
 * "resistance to {@code dungeons2:ground_slam}", which is true and useless.</p>
 *
 * @author Mark Gottschling on Sep 14, 2026
 */
public class BootsOfShockAbsorption extends ArmorItem {

    public BootsOfShockAbsorption(Properties properties) {
        super(DungeonsArmorMaterials.SHOCK_ABSORPTION, Type.BOOTS, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dungeons2.boots_of_shock_absorption")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dungeons2.boots_of_shock_absorption.fall")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
