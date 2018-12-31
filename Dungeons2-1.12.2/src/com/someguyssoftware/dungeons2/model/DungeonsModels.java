package com.someguyssoftware.dungeons2.model;


import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.block.DungeonsBlocks;
import com.someguyssoftware.dungeons2.items.DungeonsItems;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 
 * @author Mark Gottschling on Dec 30, 2018
 *
 */
@Mod.EventBusSubscriber(modid = Dungeons2.MODID, value =  Side.CLIENT)
public class DungeonsModels {	
	
	@SubscribeEvent
	public static void registerModels(ModelRegistryEvent event) {
		// TAB
		registerItemModel(DungeonsItems.DUNGEONS_TAB);
		
		// register all the items
		for (Block b : DungeonsBlocks.BLOCKS) {
			registerItemModel(Item.getItemFromBlock(b));
		}
	}
	
	/**
	 * Register the default model for an {@link Item}.
	 *
	 * @param item The item
	 */
	private static void registerItemModel(Item item) {
		final ModelResourceLocation location = new ModelResourceLocation(item.getRegistryName(), "inventory");
		ModelLoader.setCustomMeshDefinition(item, MeshDefinitionFix.create(stack -> location));			
	}
}
