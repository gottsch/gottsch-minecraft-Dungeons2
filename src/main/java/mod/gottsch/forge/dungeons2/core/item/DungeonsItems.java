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
     * A spawn egg for every mob in the GMM roster, <strong>including the three that nothing
     * spawns</strong>.
     *
     * <p>The mini-bosses need one more than the rest do, not less: they are excluded from the mob
     * sets and the structure's spawn overrides, so an egg is the only way to look at one at all.
     * That is the same argument the fungi's eggs are here on.</p>
     *
     * <p>Colours are Dungeon Denizens' verbatim, so the same mob reads the same in any pack carrying
     * both mods.</p>
     */
    public static final RegistryObject<Item> SKELETON_WARRIOR_EGG = Registration.ITEMS.register(
            DungeonsEntities.SKELETON_WARRIOR + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.SKELETON_WARRIOR_ENTITY, 0xf5f6d2, 0xcdc3bb,
                    new Item.Properties()));

    public static final RegistryObject<Item> WINGED_SKELETON_EGG = Registration.ITEMS.register(
            DungeonsEntities.WINGED_SKELETON + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.WINGED_SKELETON_ENTITY, 0xe2ded0, 0x8a8478,
                    new Item.Properties()));

    public static final RegistryObject<Item> IRON_SKELETON_EGG = Registration.ITEMS.register(
            DungeonsEntities.IRON_SKELETON + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.IRON_SKELETON_ENTITY, 0xc8c8d0, 0x6e6e78,
                    new Item.Properties()));

    public static final RegistryObject<Item> MAGMA_SKELETON_EGG = Registration.ITEMS.register(
            DungeonsEntities.MAGMA_SKELETON + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.MAGMA_SKELETON_ENTITY, 0x4b0000, 0xff7900,
                    new Item.Properties()));


    public static final RegistryObject<Item> TAINTED_SKELETON_EGG = Registration.ITEMS.register(
            DungeonsEntities.TAINTED_SKELETON + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.TAINTED_SKELETON_ENTITY, 0xa8a596, 0x8a1420,
                    new Item.Properties()));

    public static final RegistryObject<Item> ACID_SKELETON_EGG = Registration.ITEMS.register(
            DungeonsEntities.ACID_SKELETON + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.ACID_SKELETON_ENTITY, 0xb2e278, 0x2c4e1c,
                    new Item.Properties()));

    public static final RegistryObject<Item> ELECTRIC_SKELETON_EGG = Registration.ITEMS.register(
            DungeonsEntities.ELECTRIC_SKELETON + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.ELECTRIC_SKELETON_ENTITY, 0x2d3a6e, 0xf5e642,
                    new Item.Properties()));

    public static final RegistryObject<Item> BURNING_SKELETON_EGG = Registration.ITEMS.register(
            DungeonsEntities.BURNING_SKELETON + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.BURNING_SKELETON_ENTITY, 0x1c1512, 0xffb02e,
                    new Item.Properties()));

    public static final RegistryObject<Item> BLOODY_BONES_EGG = Registration.ITEMS.register(
            DungeonsEntities.BLOODY_BONES + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.BLOODY_BONES_ENTITY, 0xe8e0d0, 0x8a1420,
                    new Item.Properties()));

    public static final RegistryObject<Item> SKELETON_CHAMPION_EGG = Registration.ITEMS.register(
            DungeonsEntities.SKELETON_CHAMPION + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.SKELETON_CHAMPION_ENTITY, 0xc9c9c9, 0x17171a,
                    new Item.Properties()));

    public static final RegistryObject<Item> BLOATER_EGG = Registration.ITEMS.register(
            DungeonsEntities.BLOATER + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.BLOATER_ENTITY, 0x6c8a2c, 0x3a4a1e,
                    new Item.Properties()));

    public static final RegistryObject<Item> GRAVE_ZOMBIE_EGG = Registration.ITEMS.register(
            DungeonsEntities.GRAVE_ZOMBIE + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.GRAVE_ZOMBIE_ENTITY, 0x8b5a2b, 0x3e2711,
                    new Item.Properties()));

    public static final RegistryObject<Item> WIGHT_EGG = Registration.ITEMS.register(
            DungeonsEntities.WIGHT + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.WIGHT_ENTITY, 0xced1d6, 0x2a2c33,
                    new Item.Properties()));

    public static final RegistryObject<Item> BODAK_EGG = Registration.ITEMS.register(
            DungeonsEntities.BODAK + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.BODAK_ENTITY, 0x6b6a63, 0x2b2a26,
                    new Item.Properties()));

    public static final RegistryObject<Item> BEHOLDER_EGG = Registration.ITEMS.register(
            DungeonsEntities.BEHOLDER + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.BEHOLDER_ENTITY, 0x4a2e1a, 0xd4b896,
                    new Item.Properties()));

    public static final RegistryObject<Item> DEATH_TYRANT_EGG = Registration.ITEMS.register(
            DungeonsEntities.DEATH_TYRANT + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.DEATH_TYRANT_ENTITY, 0x2a2a2e, 0x7fd936,
                    new Item.Properties()));

    public static final RegistryObject<Item> SPECTATOR_EGG = Registration.ITEMS.register(
            DungeonsEntities.SPECTATOR + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.SPECTATOR_ENTITY, 0x344133, 0xabb685,
                    new Item.Properties()));

    public static final RegistryObject<Item> DAEMON_EGG = Registration.ITEMS.register(
            DungeonsEntities.DAEMON + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.DAEMON_ENTITY, 0xff0000, 0xff8c00,
                    new Item.Properties()));

    public static final RegistryObject<Item> GHOUL_EGG = Registration.ITEMS.register(
            DungeonsEntities.GHOUL + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.GHOUL_ENTITY, 0x9fc2b8, 0x4f5b56,
                    new Item.Properties()));

    public static final RegistryObject<Item> GELATINOUS_CUBE_EGG = Registration.ITEMS.register(
            DungeonsEntities.GELATINOUS_CUBE + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.GELATINOUS_CUBE_ENTITY, 0x9adfc7, 0x5fae95,
                    new Item.Properties()));

    public static final RegistryObject<Item> OCHRE_JELLY_EGG = Registration.ITEMS.register(
            DungeonsEntities.OCHRE_JELLY + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.OCHRE_JELLY_ENTITY, 0xadb83d, 0x5f6b1f,
                    new Item.Properties()));

    public static final RegistryObject<Item> GRAY_OOZE_EGG = Registration.ITEMS.register(
            DungeonsEntities.GRAY_OOZE + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.GRAY_OOZE_ENTITY, 0x7d8a8f, 0x4a5459,
                    new Item.Properties()));

    public static final RegistryObject<Item> BLACK_PUDDING_EGG = Registration.ITEMS.register(
            DungeonsEntities.BLACK_PUDDING + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.BLACK_PUDDING_ENTITY, 0x2b1d33, 0x5e456b,
                    new Item.Properties()));

    public static final RegistryObject<Item> ANIMATED_ARMOR_EGG = Registration.ITEMS.register(
            DungeonsEntities.ANIMATED_ARMOR + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.ANIMATED_ARMOR_ENTITY, 0xc0c0c8, 0x4a4a52,
                    new Item.Properties()));

    public static final RegistryObject<Item> MARGOYLE_EGG = Registration.ITEMS.register(
            DungeonsEntities.MARGOYLE + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.MARGOYLE_ENTITY, 0x7f7f7f, 0x5a6d41,
                    new Item.Properties()));

    public static final RegistryObject<Item> ORC_EGG = Registration.ITEMS.register(
            DungeonsEntities.ORC + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.ORC_ENTITY, 0x6b8e4e, 0x3f5c2c,
                    new Item.Properties()));

    public static final RegistryObject<Item> ORC_SHAMAN_EGG = Registration.ITEMS.register(
            DungeonsEntities.ORC_SHAMAN + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.ORC_SHAMAN_ENTITY, 0x6b8e4e, 0x4b2e83,
                    new Item.Properties()));

    public static final RegistryObject<Item> ALLIGATOR_GAR_EGG = Registration.ITEMS.register(
            DungeonsEntities.ALLIGATOR_GAR + "_egg",
            () -> new ForgeSpawnEggItem(DungeonsEntities.ALLIGATOR_GAR_ENTITY, 0x4a5c3a, 0x8f9f6b,
                    new Item.Properties()));

    /**
     * The two projectiles that render as a spinning <em>item</em> rather than a model, and the
     * items they render as.
     *
     * <p>GMM's {@code Rock} and {@code WitheringGazeSpell} each expose an {@code itemSupplier} hook
     * and fall back to a FIRE CHARGE when it is unset &mdash; a working projectile that looks like a
     * fireball. These give them their real look; {@code CommonSetup.wireRangedAttacks} hands them
     * over.</p>
     *
     * <p><strong>They are not craftable, obtainable or in any creative tab</strong> &mdash; nothing
     * registers a recipe and nothing adds them to a tab. They exist only to be the texture on a
     * thrown entity, which is the same job Dungeon Denizens' rock item does.</p>
     *
     * <p><strong>Both textures live in GMM and are referenced across the namespace</strong>
     * ({@code gmm:item/rock}, {@code gmm:item/withering_gaze_spell}) rather than copied here. gmm is
     * a hard dependency, so they are always present.</p>
     *
     * <p>The rock's texture was moved INTO gmm on 2026-08-31 (Mark's call) for exactly this reason.
     * It had only ever existed in Dungeon Denizens, so the first version of this file copied it &mdash;
     * which would have meant every consumer of GMM's Rock carrying its own duplicate of the same art,
     * with nothing keeping them in step. The art belongs with the projectile that throws it; the
     * gaze's texture was already there and is the precedent.</p>
     */
    public static final RegistryObject<Item> ROCK_ITEM = Registration.ITEMS.register(
            DungeonsEntities.ROCK, () -> new Item(new Item.Properties()));

    /** See {@link #ROCK_ITEM}. */
    public static final RegistryObject<Item> WITHERING_GAZE_SPELL_ITEM = Registration.ITEMS.register(
            DungeonsEntities.WITHERING_GAZE_SPELL, () -> new Item(new Item.Properties()));


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

    /**
     * Backlog #56: the item form of the pot marker. Same reason the other two markers have one
     * &mdash; a template author places it by hand, and a block with no item is reachable only by
     * {@code /setblock}.
     */
    public static final RegistryObject<Item> POT_MARKER = Registration.ITEMS.register(
            "pot_marker",
            () -> new BlockItem(DungeonsBlocks.POT_MARKER.get(), new Item.Properties()));

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
            event.accept(POT_MARKER.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    /** See {@link DungeonsEntities#register()} -- same class-loading reason. */
    public static void register() {
        // Intentionally empty.
    }

    private DungeonsItems() {}
}
