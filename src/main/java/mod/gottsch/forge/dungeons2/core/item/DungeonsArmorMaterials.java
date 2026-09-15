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

import mod.gottsch.forge.dungeons2.Dungeons;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * This mod's armour materials. There is exactly one, and it dresses exactly one slot.
 *
 * <p>A counter-item is not a set. The Boots of Shock Absorption answer <strong>one attack</strong>
 * &mdash; the Stone Colossus's ground slam &mdash; and the value of that answer is in the
 * {@code LivingHurtEvent} hook, not in the armour points. So the numbers here are deliberately
 * unremarkable: a little under diamond boots, with the toughness and knockback resistance that a
 * slab of shock-absorbing stone on each foot should plausibly carry, and nothing for the three
 * slots that will never be filled.</p>
 *
 * @author Mark Gottschling on Sep 14, 2026
 */
public enum DungeonsArmorMaterials implements ArmorMaterial {
    /**
     * Boots only. The other three slots return zero rather than being left unwritten: an
     * {@link ArmorMaterial} is asked for every slot whether or not an item exists for it, and a
     * switch that silently fell through would be a harder thing to read than four explicit zeroes.
     */
    SHOCK_ABSORPTION("shock_absorption", 26, new int[]{3, 0, 0, 0}, 12,
            SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.1F, () -> Ingredient.of(Items.STONE));

    /** Vanilla's per-slot durability multipliers, in {@code EquipmentSlot} index order. */
    private static final int[] SLOT_DURABILITY = {13, 15, 16, 11};

    private final String name;
    private final int durabilityMultiplier;
    private final int[] protectionByType;
    private final int enchantmentValue;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredient;

    DungeonsArmorMaterials(String name, int durabilityMultiplier, int[] protectionByType,
                           int enchantmentValue, SoundEvent equipSound, float toughness,
                           float knockbackResistance, Supplier<Ingredient> repairIngredient) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.protectionByType = protectionByType;
        this.enchantmentValue = enchantmentValue;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredient = repairIngredient;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return SLOT_DURABILITY[type.getSlot().getIndex()] * this.durabilityMultiplier;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return this.protectionByType[type.getSlot().getIndex()];
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantmentValue;
    }

    @Override
    public SoundEvent getEquipSound() {
        return this.equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return this.repairIngredient.get();
    }

    /**
     * <strong>Namespaced.</strong> This string is what the worn-armour render layer is looked up by
     * ({@code <namespace>:textures/models/armor/<name>_layer_1.png}); an unqualified name would
     * send vanilla looking in {@code minecraft} and draw the worn boots as the missing texture.
     */
    @Override
    public String getName() {
        return Dungeons.MOD_ID + ":" + this.name;
    }

    @Override
    public float getToughness() {
        return this.toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return this.knockbackResistance;
    }
}
