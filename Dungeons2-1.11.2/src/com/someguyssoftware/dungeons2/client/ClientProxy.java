/**
 * 
 */
package com.someguyssoftware.dungeons2.client;

import com.someguyssoftware.dungeons2.CommonProxy;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.config.GeneralConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;

/**
 * 
 * @author Mark Gottschling on Jul 27, 2016
 *
 */
public class ClientProxy extends CommonProxy {

	@Override
	public void registerRenderers() {
		registerItemRenderers();
	}
	
	/**
	 * 
	 */
	public void registerItemRenderers() {
		// TODO update to new way to register items

		/*
		 *  register item renderers
		 */
		// basic facade
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.basicStoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.basicStoneFacadeId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.basicCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.basicCobblestoneFacadeId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.basicMossyCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.basicMossyCobblestoneFacadeId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.basicStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.basicStonebrickFacadeId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.basicMossyStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.basicMossyStonebrickFacadeId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.basicCrackedStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.basicCrackedStonebrickFacadeId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.basicChiseledStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.basicChiseledStonebrickFacadeId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.basicObsidianbrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.basicObsidianbrickFacadeId, "inventory"));
		
		// "T" pillar
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teePillarStoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teePillarStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teePillarCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teePillarCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teePillarMossyCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teePillarMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teePillarStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teePillarStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teePillarMossyStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teePillarMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teePillarCrackedStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teePillarCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teePillarObsidianbrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teePillarObsidianbrickBlockId, "inventory"));

		// "T" pillar facade
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teeThinPillarStoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teeThinPillarStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teeThinPillarCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teeThinPillarCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teeThinPillarMossyCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teeThinPillarMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teeThinPillarStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teeThinPillarStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teeThinPillarMossyStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teeThinPillarMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teeThinPillarCrackedStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teeThinPillarCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.teeThinPillarObsidianbrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.teeThinPillarObsidianbrickBlockId, "inventory"));
		
		// flute pillar
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.flutePillarStoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.flutePillarStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.flutePillarCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.flutePillarCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.flutePillarMossyCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.flutePillarMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.flutePillarStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.flutePillarStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.flutePillarMossyStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.flutePillarMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.flutePillarCrackedStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.flutePillarCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.flutePillarObsidianbrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.flutePillarObsidianbrickBlockId, "inventory"));
	
		// flute pillar facade
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.fluteThinPillarStoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.fluteThinPillarStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.fluteThinPillarCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.fluteThinPillarCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.fluteThinPillarMossyCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.fluteThinPillarMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.fluteThinPillarStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.fluteThinPillarStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.fluteThinPillarMossyStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.fluteThinPillarMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.fluteThinPillarCrackedStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.fluteThinPillarCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.fluteThinPillarObsidianbrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.fluteThinPillarObsidianbrickBlockId, "inventory"));
		
		// cornice
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.corniceStoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.corniceStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.corniceCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.corniceCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.corniceMossyCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.corniceMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.corniceStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.corniceStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.corniceMossyStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.corniceMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.corniceCrackedStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.corniceCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.corniceObsidianbrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.corniceObsidianbrickBlockId, "inventory"));
		
		// crown molding
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.crownMoldingStoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.crownMoldingStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.crownMoldingCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.crownMoldingCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.crownMoldingMossyCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.crownMoldingMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.crownMoldingStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.crownMoldingStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.crownMoldingMossyStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.crownMoldingMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.crownMoldingCrackedStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.crownMoldingCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.crownMoldingObsidianbrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.crownMoldingObsidianbrickBlockId, "inventory"));
		
		// seven eights
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sevenEightsPillarStoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sevenEightsPillarStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sevenEightsPillarCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sevenEightsPillarCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sevenEightsPillarMossyCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sevenEightsPillarMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sevenEightsPillarStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sevenEightsPillarStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sevenEightsPillarMossyStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sevenEightsPillarMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sevenEightsPillarCrackedStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sevenEightsPillarCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sevenEightsPillarObsidianbrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sevenEightsPillarObsidianbrickBlockId, "inventory"));
			
		// sill
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sillStoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sillStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sillCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sillCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sillMossyCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sillMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sillStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sillStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sillMossyStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sillMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sillCrackedStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sillCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.sillObsidianbrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.sillObsidianbrickBlockId, "inventory"));
	
		// double sill
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.doubleSillStoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.doubleSillStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.doubleSillCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.doubleSillCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.doubleSillMossyCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.doubleSillMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.doubleSillStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.doubleSillStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.doubleSillMossyStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.doubleSillMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.doubleSillCrackedStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.doubleSillCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.doubleSillObsidianbrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.doubleSillObsidianbrickBlockId, "inventory"));
			
		// half pillar base
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarBaseStoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarBaseStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarBaseCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarBaseCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarBaseMossyCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarBaseMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarBaseStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarBaseStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarBaseMossyStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarBaseMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarBaseCrackedStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarBaseCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarBaseObsidianbrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarBaseObsidianbrickBlockId, "inventory"));

		// half pillar
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarStoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarMossyCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarMossyStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarCrackedStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.halfPillarObsidianbrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.halfPillarObsidianbrickBlockId, "inventory"));
				
		// coffer middel
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMiddleStoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMiddleStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMiddleCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMiddleCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMiddleMossyCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMiddleMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMiddleStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMiddleStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMiddleMossyStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMiddleMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMiddleCrackedStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMiddleCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMiddleObsidianbrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMiddleObsidianbrickBlockId, "inventory"));

		// coffer
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferStoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMossyCobblestoneBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferMossyStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferCrackedStonebrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.cofferObsidianbrickBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.cofferObsidianbrickBlockId, "inventory"));

		// wall sconce
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.wallSconceStoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.wallSconceStoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.wallSconceCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.wallSconceCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.wallSconceMossyCobblestoneFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.wallSconceMossyCobblestoneBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.wallSconceStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.wallSconceStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.wallSconceMossyStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.wallSconceMossyStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.wallSconceCrackedStonebrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.wallSconceCrackedStonebrickBlockId, "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.wallSconceObsidianbrickFacade), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.wallSconceObsidianbrickBlockId, "inventory"));
		
		// grate 
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.grateBlock), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + GeneralConfig.grateBlockId, "inventory"));

		// moss
//		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.moss), 0, new ModelResourceLocation(Dungeons2.modid + ":" + "moss", "inventory"));
//		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.moss2), 0, new ModelResourceLocation(Dungeons2.modid + ":" + "moss2", "inventory"));
//		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.lichen), 0, new ModelResourceLocation(Dungeons2.modid + ":" + "lichen", "inventory"));
//		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.lichen2), 0, new ModelResourceLocation(Dungeons2.modid + ":" + "lichen2", "inventory"));
		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.puddle), 0, new ModelResourceLocation(Dungeons2.MODID + ":" + "puddle", "inventory"));
//		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.blood), 0, new ModelResourceLocation(Dungeons2.modid + ":" + "blood", "inventory"));
//		Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(Item.getItemFromBlock(Dungeons2.mold1), 0, new ModelResourceLocation(Dungeons2.modid + ":" + GeneralConfig.mold1BlockId, "inventory"));


	}
}
