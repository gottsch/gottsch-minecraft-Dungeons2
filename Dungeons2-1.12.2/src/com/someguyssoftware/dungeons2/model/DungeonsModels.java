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
		
//		// basic facade	
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicStoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicMossyCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicMossyStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicCrackedStonebrickFacade));					
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicChiseledStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicObsidianbrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.basicSmoothSandstoneFacade));
//		// tee
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teePillarStoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teePillarCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teePillarMossyCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teePillarStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teePillarMossyStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teePillarCrackedStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teePillarObsidianbrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teePillarSmoothSandstoneFacade));
//		// thin tee
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teeThinPillarStoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teeThinPillarCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teeThinPillarMossyCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teeThinPillarStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teeThinPillarMossyStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teeThinPillarCrackedStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teeThinPillarObsidianbrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.teeThinPillarSmoothSandstoneFacade));
//		// flute
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.flutePillarStoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.flutePillarCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.flutePillarMossyCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.flutePillarStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.flutePillarMossyStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.flutePillarCrackedStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.flutePillarObsidianbrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.flutePillarSmoothSandstoneBlock));
//
//		// flute thin
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.fluteThinPillarStoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.fluteThinPillarCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.fluteThinPillarMossyCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.fluteThinPillarStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.fluteThinPillarMossyStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.fluteThinPillarCrackedStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.fluteThinPillarObsidianbrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.fluteThinPillarSmoothSandstoneFacade));
//
//		// cornice
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.corniceStoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.corniceCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.corniceMossyCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.corniceStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.corniceMossyStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.corniceCrackedStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.corniceObsidianbrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.corniceSmoothSandstoneFacade));		
//		// crown
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.crownMoldingStoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.crownMoldingCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.crownMoldingMossyCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.crownMoldingStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.crownMoldingMossyStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.crownMoldingCrackedStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.crownMoldingObsidianbrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.crownMoldingSmoothSandstoneFacade));
//		// seven-eights
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sevenEightsPillarStoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sevenEightsPillarCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sevenEightsPillarMossyCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sevenEightsPillarStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sevenEightsPillarMossyStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sevenEightsPillarCrackedStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sevenEightsPillarObsidianbrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sevenEightsPillarSmoothSandstoneFacade));				
//		// sills
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sillStoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sillCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sillMossyCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sillStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sillMossyStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sillCrackedStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sillObsidianbrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.sillSmoothSandstoneBlock));
//		// double sills
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.doubleSillStoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.doubleSillCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.doubleSillMossyCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.doubleSillStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.doubleSillMossyStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.doubleSillCrackedStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.doubleSillObsidianbrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.doubleSillSmoothSandstoneBlock));	
//		// half pillar base
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarBaseStoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarBaseCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarBaseMossyCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarBaseStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarBaseMossyStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarBaseCrackedStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarBaseObsidianbrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarBaseSmoothSandstoneBlock));
//		
//		// half pillar
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarStoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarMossyCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarMossyStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarCrackedStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarObsidianbrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.halfPillarSmoothSandstoneBlock));
//		
//		// middle coffer
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMiddleStoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMiddleCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMiddleMossyCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMiddleStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMiddleMossyStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMiddleCrackedStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMiddleObsidianbrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMiddleSmoothSandstoneBlock));
//		// coffer
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferStoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMossyCobblestoneBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferMossyStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferCrackedStonebrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferObsidianbrickBlock));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.cofferSmoothSandstoneBlock));
//		// wall sconce
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.wallSconceStoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.wallSconceCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.wallSconceMossyCobblestoneFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.wallSconceStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.wallSconceMossyStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.wallSconceCrackedStonebrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.wallSconceObsidianbrickFacade));
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.wallSconceSmoothSandstoneFacade));
//		// grate
//		registerItemModel(Item.getItemFromBlock(DungeonsBlocks.grateBlock));
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
