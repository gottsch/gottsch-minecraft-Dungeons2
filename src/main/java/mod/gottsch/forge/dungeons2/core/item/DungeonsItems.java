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
import mod.gottsch.forge.dungeons2.core.block.DungeonsBlocks;
import net.minecraft.world.item.BlockItem;
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

    /**
     * The fungi get spawn eggs and their markers deliberately do not.
     *
     * <p>The two are opposite cases. An egg is worth having for a mob nothing spawns naturally --
     * it is the only way to look at one without generating a dungeon and finding decayed dirt. The
     * fungi themselves are never placed by hand: the weathering pass names them directly in its
     * growth palette and {@code dungeons2:decoration} spawns them, so there is no authoring
     * workflow and no marker block to give an item to.</p>
     *
     * <p>Colours match Dungeon Denizens', so the same mob reads the same in any pack carrying both.</p>
     */
    public static final RegistryObject<Item> SHRIEKER_EGG = Registration.ITEMS.register(
            DungeonsEntities.SHRIEKER + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.SHRIEKER_ENTITY, 0x8f4f8f, 0xc9a24a,
                    new Item.Properties()));

    /** See {@link #SHRIEKER_EGG}. */
    public static final RegistryObject<Item> VIOLET_FUNGUS_EGG = Registration.ITEMS.register(
            DungeonsEntities.VIOLET_FUNGUS + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.VIOLET_FUNGUS_ENTITY, 0x6e2878, 0x231528,
                    new Item.Properties()));

    /**
     * Backlog #10: the item form of the spawner marker, so it can be placed by hand while authoring
     * a room template. Without it the block exists but {@code /give} cannot name it and it cannot be
     * put in a hotbar &mdash; only {@code /setblock} reaches a block with no item.
     *
     * <p>In {@code FUNCTIONAL_BLOCKS} beside the vanilla spawner and structure blocks, which is
     * where someone building a dungeon template would look for it.</p>
     */
    public static final RegistryObject<Item> SPAWNER_MARKER = Registration.ITEMS.register(
            "spawner_marker",
            () -> new BlockItem(DungeonsBlocks.SPAWNER_MARKER.get(), new Item.Properties()));

    /**
     * Backlog #48: the item form of the chest marker, for the same reason {@link #SPAWNER_MARKER}
     * has one &mdash; a template author places it by hand.
     *
     * <p>It was missing until 2026-08-29. The block shipped visible and textured and its javadoc
     * said "its author has to be able to see it while building", which was true and not the whole
     * requirement: with no {@code BlockItem} the only way to place one was {@code /setblock}, so it
     * could never be held, and its {@code facing} property could never be set by looking &mdash;
     * {@code /setblock} takes the default. That is easy to miss precisely because #48 was finished
     * and confirmed in game: the feature worked, the authoring path was the awkward part.</p>
     */
    public static final RegistryObject<Item> CHEST_MARKER = Registration.ITEMS.register(
            "chest_marker",
            () -> new BlockItem(DungeonsBlocks.CHEST_MARKER.get(), new Item.Properties()));

    @SubscribeEvent
    public static void addItemsToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(RAT_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.accept(GIANT_RAT_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.accept(SHRIEKER_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.accept(VIOLET_FUNGUS_EGG.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SPAWNER_MARKER.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.accept(CHEST_MARKER.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    /** See {@link DungeonsEntities#register()} -- same class-loading reason. */
    public static void register() {
        // Intentionally empty.
    }

    private DungeonsItems() {}
}
