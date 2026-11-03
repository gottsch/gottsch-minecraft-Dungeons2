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
package mod.gottsch.neo.dungeons2.core.item;

import mod.gottsch.neo.dungeons2.Dungeons;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * This mod's armour materials. There is exactly one, and it dresses exactly one slot.
 *
 * <p>A counter-item is not a set. The Boots of Shock Absorption answer <strong>one attack</strong>
 * &mdash; the Stone Colossus's ground slam &mdash; and the value of that answer is in the
 * {@code LivingIncomingDamageEvent} hook, not in the armour points. So the numbers here are deliberately
 * unremarkable: a little under diamond boots, with the toughness and knockback resistance that a
 * slab of shock-absorbing stone on each foot should plausibly carry, and nothing for the three
 * slots that will never be filled.</p>
 *
 * <p>On 1.21 an armour material is a registered {@link ArmorMaterial} record, not an enum the item
 * holds, and its durability lives on the item ({@link #BOOTS_DURABILITY}).</p>
 *
 * @author Mark Gottschling on Sep 14, 2026
 */
public final class DungeonsArmorMaterials {

    private static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Dungeons.MOD_ID);

    /** Vanilla's boots multiplier (11) times this material's 26: a little under diamond's 33. */
    public static final int BOOTS_DURABILITY = ArmorItem.Type.BOOTS.getDurability(26);

    /**
     * Boots only. The other three slots are written as zero rather than left out: a material is
     * asked for every slot whether or not an item exists for it, and four explicit zeroes are easier
     * to read than a lookup that silently falls through.
     *
     * <p>The layer id is what the worn-armour texture is found by
     * ({@code dungeons2:textures/models/armor/shock_absorption_layer_1.png}).</p>
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SHOCK_ABSORPTION =
            MATERIALS.register("shock_absorption", () -> {
                EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                defense.put(ArmorItem.Type.BOOTS, 3);
                defense.put(ArmorItem.Type.LEGGINGS, 0);
                defense.put(ArmorItem.Type.CHESTPLATE, 0);
                defense.put(ArmorItem.Type.HELMET, 0);
                defense.put(ArmorItem.Type.BODY, 0);
                return new ArmorMaterial(defense, 12, SoundEvents.ARMOR_EQUIP_IRON,
                        () -> Ingredient.of(Items.STONE),
                        List.of(new ArmorMaterial.Layer(
                                ResourceLocation.fromNamespaceAndPath(Dungeons.MOD_ID, "shock_absorption"))),
                        1.0F, 0.1F);
            });

    public static void register(IEventBus eventBus) {
        MATERIALS.register(eventBus);
    }

    private DungeonsArmorMaterials() {}
}
