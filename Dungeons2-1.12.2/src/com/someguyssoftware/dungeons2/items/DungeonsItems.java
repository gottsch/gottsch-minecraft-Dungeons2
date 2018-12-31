/**
 * 
 */
package com.someguyssoftware.dungeons2.items;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.gottschcore.item.ModItem;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * @author Mark Gottschling on Dec 30, 2018
 *
 */
public class DungeonsItems {
	// tab
	public static Item DUNGEONS_TAB;
	
	static {
		// TAB
		DUNGEONS_TAB = new ModItem().setItemName(Dungeons2.MODID, ModConfig.DUNGEONS2_TAB_ID);	
	}
	
	/**
	 * 
	 */
	public DungeonsItems() {	
		
	}
	
	/**
	 * 
	 * @author Mark Gottschling on Jan 12, 2018
	 *
	 */
	@Mod.EventBusSubscriber(modid = Dungeons2.MODID)
	public static class RegistrationHandler {
		
		@SubscribeEvent
		public static void registerItems(final RegistryEvent.Register<Item> event) {
			
			final IForgeRegistry<Item> registry = event.getRegistry();
	
			final Item[] items = {
					DUNGEONS_TAB,
			};
			registry.registerAll(items);	
		}
	}
}
