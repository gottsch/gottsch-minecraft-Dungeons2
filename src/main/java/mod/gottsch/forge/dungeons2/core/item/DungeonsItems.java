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
import mod.gottsch.forge.dungeons2.core.entity.DungeonsEntities;
import mod.gottsch.forge.dungeons2.core.setup.Registration;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

/**
 * Spawn eggs for the dungeon's mobs (backlog #40 / #41).
 *
 * <p>They go in vanilla's {@code SPAWN_EGGS} tab rather than a Dungeons2 tab, because this mod does
 * not have one &mdash; nothing was registered into {@code Registration.ITEMS} before these two, so
 * inventing a creative tab to hold exactly two eggs would be the larger change. Vanilla's egg tab is
 * where a player looks for an egg anyway.</p>
 *
 * @author Mark Gottschling on Aug 13, 2026
 */
@Mod.EventBusSubscriber(modid = Dungeons.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DungeonsItems {

    public static final RegistryObject<Item> RAT_EGG = Registration.ITEMS.register(
            DungeonsEntities.RAT + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.RAT_ENTITY, 0x5a4a3f, 0x2e2620, new Item.Properties()));

    public static final RegistryObject<Item> GIANT_RAT_EGG = Registration.ITEMS.register(
            DungeonsEntities.GIANT_RAT + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.GIANT_RAT_ENTITY, 0x3d3128, 0x1c1712, new Item.Properties()));

    @SubscribeEvent
    public static void addItemsToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(RAT_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.accept(GIANT_RAT_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    /** See {@link DungeonsEntities#register()} -- same class-loading reason. */
    public static void register() {
        // Intentionally empty.
    }

    private DungeonsItems() {}
}
