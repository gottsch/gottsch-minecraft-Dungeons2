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
package mod.gottsch.forge.dungeons2.core.item.crafting;

import mod.gottsch.forge.dungeons2.core.item.DungeonsItems;
import mod.gottsch.forge.dungeons2.core.map.DungeonMaps;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A Dungeon Map, an Empty Map and a coal make a Blank Dungeon Map, and the Dungeon Map comes back.
 *
 * <p>The only way to craft one (Mark, 2026-10-26): a map for every floor from an empty map and a
 * coal made the maps in chests, barrels and pots pointless. Copied from one you have found, the
 * first is still a find, and the rest - one for each floor below - are what it buys. Any Dungeon
 * Map serves, of any floor, drawn or not.</p>
 *
 * <p><strong>The coal is what keeps it apart from vanilla's map copying</strong>, which also
 * matches a filled map and an empty map and, found first, makes a second copy of the SAME map
 * (Mark, 2026-10-26: the copies stacked). That one is worth keeping - a second copy of this floor,
 * for a friend - so the blank is the one that asks for something more.</p>
 *
 * <p>A special recipe, as vanilla's map copying is, so it has no recipe-book entry: the found map's
 * tooltip says how.</p>
 *
 * @author Mark Gottschling on 10/26/2026
 */
public class DungeonMapCopyRecipe extends CustomRecipe {

    public DungeonMapCopyRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    /** Exactly one Dungeon Map, one Empty Map and one coal, anywhere in the grid, and nothing else. */
    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int maps = 0;
        int blanks = 0;
        int coals = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (DungeonMaps.stampOf(stack) != null) {
                maps++;
            } else if (stack.is(Items.MAP)) {
                blanks++;
            } else if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
                coals++;
            } else {
                return false;
            }
        }
        return maps == 1 && blanks == 1 && coals == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return new ItemStack(DungeonsItems.BLANK_DUNGEON_MAP.get());
    }

    /** The Dungeon Map stays in the grid. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(container);
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (DungeonMaps.stampOf(stack) != null) {
                remaining.set(i, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Registration.DUNGEON_MAP_COPY_RECIPE.get();
    }
}
