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

import mod.gottsch.forge.dungeons2.core.event.MirrorShieldEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Backlog #97: the Beholder-kin's counter-item. An ordinary shield in every way but one &mdash;
 * raised toward eye-magic, it sends the bolt (or the Annihilation Ray) back at the caster. That
 * part lives in {@link MirrorShieldEvent} and {@code AnnihilationRay}; this class is the item.
 *
 * <p>Otherwise ordinary on purpose: a counter-item that also out-blocked a vanilla shield would
 * be the shield everybody carries, and would stop teaching the one fight it exists for.</p>
 *
 * @author Mark Gottschling on Sep 24, 2026
 */
public class MirrorShield extends ShieldItem {

    public MirrorShield(Properties properties) {
        super(properties);
    }

    /**
     * True when {@code entity} is blocking with a Mirror Shield and faces {@code from} &mdash; the
     * same front-half test vanilla's {@code isDamageSourceBlocked} makes, so a reflect happens
     * exactly where an ordinary block would.
     */
    public static boolean isReflecting(LivingEntity entity, Vec3 from) {
        if (!entity.isBlocking() || !(entity.getUseItem().getItem() instanceof MirrorShield)) {
            return false;
        }
        Vec3 toSource = from.vectorTo(entity.position()).normalize();
        toSource = new Vec3(toSource.x, 0.0D, toSource.z);
        return toSource.dot(entity.getViewVector(1.0F)) < 0.0D;
    }

    /** Silvered iron, not planks: this is not the wooden shield it is shaped like. */
    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(Items.IRON_INGOT);
    }

    /** No banner patterns: ShieldItem's name lookup reads a banner colour this shield never has. */
    @Override
    public String getDescriptionId(ItemStack stack) {
        return this.getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dungeons2.mirror_shield")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dungeons2.mirror_shield.raise")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
