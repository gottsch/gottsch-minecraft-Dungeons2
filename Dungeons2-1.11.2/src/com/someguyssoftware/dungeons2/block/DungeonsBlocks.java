/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.gottschcore.AbstractModObjectHolder;
import com.someguyssoftware.gottschcore.block.CardinalDirectionBlock;
import com.someguyssoftware.gottschcore.block.RelativeDirectionFacadeBlock;
import com.someguyssoftware.gottschcore.block.CardinalDirectionBlock;
import com.someguyssoftware.gottschcore.block.RelativeDirectionFacadeBlock;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.IForgeRegistry;

/**
 * @author Mark Gottschling on Jul 29, 2017
 *
 */
public class DungeonsBlocks extends AbstractModObjectHolder {
	/*
	 *  blocks 
	 */
	// facade blocks
	public static Block basicStoneFacade;
	public static Block basicCobblestoneFacade;
	public static Block basicMossyCobblestoneFacade;
	public static Block basicStonebrickFacade;
	public static Block basicMossyStonebrickFacade;
	public static Block basicCrackedStonebrickFacade;
	public static Block basicChiseledStonebrickFacade;
	public static Block basicObsidianbrickFacade;

	// "T" Pillar
	public static Block teePillarStoneFacade;
	public static Block teePillarCobblestoneFacade;
	public static Block teePillarMossyCobblestoneFacade;
	public static Block teePillarStonebrickFacade;
	public static Block teePillarMossyStonebrickFacade;
	public static Block teePillarCrackedStonebrickFacade;
	public static Block teePillarObsidianbrickFacade;

	// thin "T" Pillar	
	public static Block teeThinPillarStoneFacade;
	public static Block teeThinPillarCobblestoneFacade;
	public static Block teeThinPillarMossyCobblestoneFacade;
	public static Block teeThinPillarStonebrickFacade;
	public static Block teeThinPillarMossyStonebrickFacade;
	public static Block teeThinPillarCrackedStonebrickFacade;
	public static Block teeThinPillarObsidianbrickFacade;

	// cornice
	public static Block corniceStoneFacade;
	public static Block corniceCobblestoneFacade;
	public static Block corniceMossyCobblestoneFacade;
	public static Block corniceStonebrickFacade;
	public static Block corniceMossyStonebrickFacade;
	public static Block corniceCrackedStonebrickFacade;
	public static Block corniceObsidianbrickFacade;

	// flute pillar
	public static Block flutePillarStoneBlock;
	public static Block flutePillarCobblestoneBlock;
	public static Block flutePillarMossyCobblestoneBlock;
	public static Block flutePillarStonebrickBlock;
	public static Block flutePillarMossyStonebrickBlock;
	public static Block flutePillarCrackedStonebrickBlock;
	public static Block flutePillarObsidianbrickBlock;

	// flute thin pillar
	public static Block fluteThinPillarStoneFacade;
	public static Block fluteThinPillarCobblestoneFacade;
	public static Block fluteThinPillarMossyCobblestoneFacade;
	public static Block fluteThinPillarStonebrickFacade;
	public static Block fluteThinPillarMossyStonebrickFacade;
	public static Block fluteThinPillarCrackedStonebrickFacade;
	public static Block fluteThinPillarObsidianbrickFacade;

	// seven eights
	public static Block sevenEightsPillarStoneFacade;
	public static Block sevenEightsPillarCobblestoneFacade;
	public static Block sevenEightsPillarMossyCobblestoneFacade;
	public static Block sevenEightsPillarStonebrickFacade;
	public static Block sevenEightsPillarMossyStonebrickFacade;
	public static Block sevenEightsPillarCrackedStonebrickFacade;
	public static Block sevenEightsPillarObsidianbrickFacade;

	// sills
	public static Block sillStoneBlock;
	public static Block sillCobblestoneBlock;
	public static Block sillMossyCobblestoneBlock;
	public static Block sillStonebrickBlock;
	public static Block sillMossyStonebrickBlock;
	public static Block sillCrackedStonebrickBlock;
	public static Block sillObsidianbrickBlock;

	// double sills
	public static Block doubleSillStoneBlock;
	public static Block doubleSillCobblestoneBlock;
	public static Block doubleSillMossyCobblestoneBlock;
	public static Block doubleSillStonebrickBlock;
	public static Block doubleSillMossyStonebrickBlock;
	public static Block doubleSillCrackedStonebrickBlock;	
	public static Block doubleSillObsidianbrickBlock;

	// half pillar base
	public static Block halfPillarBaseStoneBlock;
	public static Block halfPillarBaseCobblestoneBlock;
	public static Block halfPillarBaseMossyCobblestoneBlock;
	public static Block halfPillarBaseStonebrickBlock;
	public static Block halfPillarBaseMossyStonebrickBlock;
	public static Block halfPillarBaseCrackedStonebrickBlock;
	public static Block halfPillarBaseObsidianbrickBlock;

	// half pillar
	public static Block halfPillarStoneBlock;
	public static Block halfPillarCobblestoneBlock;
	public static Block halfPillarMossyCobblestoneBlock;
	public static Block halfPillarStonebrickBlock;
	public static Block halfPillarMossyStonebrickBlock;
	public static Block halfPillarCrackedStonebrickBlock;
	public static Block halfPillarObsidianbrickBlock;

	// coffer middle
	public static Block cofferMiddleStoneBlock;
	public static Block cofferMiddleCobblestoneBlock;
	public static Block cofferMiddleMossyCobblestoneBlock;
	public static Block cofferMiddleStonebrickBlock;
	public static Block cofferMiddleMossyStonebrickBlock;
	public static Block cofferMiddleCrackedStonebrickBlock;
	public static Block cofferMiddleObsidianbrickBlock;

	// coffer
	public static Block cofferStoneBlock;
	public static Block cofferCobblestoneBlock;
	public static Block cofferMossyCobblestoneBlock;
	public static Block cofferStonebrickBlock;
	public static Block cofferMossyStonebrickBlock;
	public static Block cofferCrackedStonebrickBlock;
	public static Block cofferObsidianbrickBlock;

	// crown molding
	public static Block crownMoldingStoneFacade;
	public static Block crownMoldingCobblestoneFacade;
	public static Block crownMoldingMossyCobblestoneFacade;
	public static Block crownMoldingStonebrickFacade;
	public static Block crownMoldingMossyStonebrickFacade;
	public static Block crownMoldingCrackedStonebrickFacade;
	public static Block crownMoldingObsidianbrickFacade;

	// wall sconce 
	public static Block wallSconceStoneFacade;
	public static Block wallSconceCobblestoneFacade;
	public static Block wallSconceMossyCobblestoneFacade;
	public static Block wallSconceStonebrickFacade;
	public static Block wallSconceMossyStonebrickFacade;
	public static Block wallSconceCrackedStonebrickFacade;
	public static Block wallSconceObsidianbrickFacade;

	// grate
	public static Block grateBlock;

	// decorations
	//	public static Block moss;
	//	public static Block moss2;
	//	public static Block lichen;
	//	public static Block lichen2;
	public static final Block PUDDLE;
	//	public static Block blood;
	//	public static Block mold1;

	static {
		// basic facade
		basicStoneFacade = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.basicStoneFacadeId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		basicCobblestoneFacade = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.basicCobblestoneFacadeId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		basicMossyCobblestoneFacade = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.basicMossyCobblestoneFacadeId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		basicStonebrickFacade = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.basicStonebrickFacadeId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		basicMossyStonebrickFacade = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.basicMossyStonebrickFacadeId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		basicCrackedStonebrickFacade = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.basicCrackedStonebrickFacadeId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		basicChiseledStonebrickFacade = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.basicChiseledStonebrickFacadeId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		basicObsidianbrickFacade = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.basicObsidianbrickFacadeId, Material.ROCK).setHardness(50F).setResistance(2000F);

		// t pillar
		teePillarStoneFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teePillarCobblestoneFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		teePillarMossyCobblestoneFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		teePillarStonebrickFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teePillarMossyStonebrickFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teePillarCrackedStonebrickFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teePillarObsidianbrickFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);

		// t pillar facade
		teeThinPillarStoneFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teeThinPillarCobblestoneFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		teeThinPillarMossyCobblestoneFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		teeThinPillarStonebrickFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teeThinPillarMossyStonebrickFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarMossyStonebrickBlockId, Material.ROCK ).setHardness(1.5F).setResistance(10.0F);
		teeThinPillarCrackedStonebrickFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarCrackedStonebrickBlockId,Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teeThinPillarObsidianbrickFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);

		// flute pillar
		flutePillarStoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		flutePillarCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		flutePillarMossyCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		flutePillarStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		flutePillarMossyStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		flutePillarCrackedStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		flutePillarObsidianbrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);

		// flute pillar facade
		fluteThinPillarStoneFacade = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		fluteThinPillarCobblestoneFacade= new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		fluteThinPillarMossyCobblestoneFacade= new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		fluteThinPillarStonebrickFacade = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		fluteThinPillarMossyStonebrickFacade= new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		fluteThinPillarCrackedStonebrickFacade = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		fluteThinPillarObsidianbrickFacade = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);

		// cornice
		corniceStoneFacade = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		corniceCobblestoneFacade= new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		corniceMossyCobblestoneFacade= new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		corniceStonebrickFacade = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		corniceMossyStonebrickFacade= new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		corniceCrackedStonebrickFacade = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		corniceObsidianbrickFacade = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);

		// crown molding
		crownMoldingStoneFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingCobblestoneFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingCobblestoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingMossyCobblestoneFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingMossyCobblestoneBlockId, Material.ROCK).setHardness(1.5F);
		crownMoldingStonebrickFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingMossyStonebrickFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingCrackedStonebrickFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingObsidianbrickFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingObsidianbrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(2000.0F);

		// seven eights
		sevenEightsPillarStoneFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		sevenEightsPillarCobblestoneFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		sevenEightsPillarMossyCobblestoneFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		sevenEightsPillarStonebrickFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		sevenEightsPillarMossyStonebrickFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		sevenEightsPillarCrackedStonebrickFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		sevenEightsPillarObsidianbrickFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);

		// sills
		sillStoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		sillCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		sillMossyCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS); 
		sillStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		sillMossyStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		sillCrackedStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		sillObsidianbrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);

		// double sills
		doubleSillStoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		doubleSillCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		doubleSillMossyCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		doubleSillStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		doubleSillMossyStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		doubleSillCrackedStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		doubleSillObsidianbrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F).setCreativeTab(CreativeTabs.BUILDING_BLOCKS);

		// half pillar bases
		halfPillarBaseStoneBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarBaseCobblestoneBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		halfPillarBaseMossyCobblestoneBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseMossyCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		halfPillarBaseStonebrickBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarBaseMossyStonebrickBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F);
		halfPillarBaseCrackedStonebrickBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseCrackedStonebrickBlockId,Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarBaseObsidianbrickBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F);

		// half pillars
		halfPillarStoneBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarCobblestoneBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		halfPillarMossyCobblestoneBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		halfPillarStonebrickBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarMossyStonebrickBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarCrackedStonebrickBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarObsidianbrickBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F);

		// middle coffer
		cofferMiddleStoneBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMiddleCobblestoneBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		cofferMiddleMossyCobblestoneBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleMossyCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		cofferMiddleStonebrickBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMiddleMossyStonebrickBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMiddleCrackedStonebrickBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMiddleObsidianbrickBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F);

		// coffer
		cofferStoneBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferCobblestoneBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		cofferMossyCobblestoneBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferMossyCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		cofferStonebrickBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMossyStonebrickBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferCrackedStonebrickBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferObsidianbrickBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F);

		// wall sconce
		wallSconceStoneFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceStoneBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceCobblestoneFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceCobblestoneBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceMossyCobblestoneFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceMossyCobblestoneBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceStonebrickFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceStonebrickBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceMossyStonebrickFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceMossyStonebrickBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceCrackedStonebrickFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceCrackedStonebrickBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceObsidianbrickFacade = 	new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceObsidianbrickBlockId).setHardness(0.0F).setLightLevel(0.9375F);

		// grate
		grateBlock = new GrateBlock(Dungeons2.MODID, ModConfig.grateBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// decorations - moss
		//	    moss = new DecorationBlock(Material.GRASS, ModConfig.mossBlockId);
		//	    moss2 = new DecorationBlock(Material.GRASS, ModConfig.moss2BlockId);
		//	    lichen = new DecorationBlock(Material.GRASS, ModConfig.lichenBlockId);
		//	    lichen2 = new DecorationBlock(Material.GRASS, ModConfig.lichen2BlockId);
		PUDDLE = new DecorationBlock(Dungeons2.MODID, ModConfig.puddleBlockId, Material.GRASS);
		//	    blood = new DecorationBlock(Material.GRASS, ModConfig.bloodBlockId);
		//	    mold1 = new DecorationBlock(Material.GRASS, ModConfig.mold1BlockId);
	}

	@Mod.EventBusSubscriber(modid = Dungeons2.MODID)
	public static class RegistrationHandler {
		public static final Set<ItemBlock> ITEM_BLOCKS = new HashSet<>();

		/**
		 * Register this mod's {@link Block}s.
		 *
		 * @param event The event
		 */
		@SubscribeEvent
		public static void registerBlocks(final RegistryEvent.Register<Block> event) {
			final IForgeRegistry<Block> registry = event.getRegistry();

			final Block[] blocks = {
					basicStoneFacade,
					basicCobblestoneFacade
//					basicMossyCobblestoneFacade,
//					basicStonebrickFacade,
//					basicMossyStonebrickFacade,
//					basicCrackedStonebrickFacade,					
//					basicChiseledStonebrickFacade,
//					basicObsidianbrickFacade,
//					// tee
//					teePillarStoneFacade,
//					teePillarCobblestoneFacade,
//					teePillarMossyCobblestoneFacade,
//					teePillarStonebrickFacade,
//					teePillarMossyStonebrickFacade,
//					teePillarCrackedStonebrickFacade,
//					teePillarObsidianbrickFacade,
//					// thin tee
//					teeThinPillarStoneFacade,
//					teeThinPillarCobblestoneFacade,
//					teeThinPillarMossyCobblestoneFacade,
//					teeThinPillarStonebrickFacade,
//					teeThinPillarMossyStonebrickFacade,
//					teeThinPillarCrackedStonebrickFacade,
//					teeThinPillarObsidianbrickFacade,
//					// flute
//					flutePillarStoneBlock,
//					flutePillarCobblestoneBlock,
//					flutePillarMossyCobblestoneBlock,
//					flutePillarStonebrickBlock,
//					flutePillarMossyStonebrickBlock,
//					flutePillarCrackedStonebrickBlock,
//					flutePillarObsidianbrickBlock,
//
//					// flute thin
//					fluteThinPillarStoneFacade,
//					fluteThinPillarCobblestoneFacade,
//					fluteThinPillarMossyCobblestoneFacade,
//					fluteThinPillarStonebrickFacade,
//					fluteThinPillarMossyStonebrickFacade,
//					fluteThinPillarCrackedStonebrickFacade,
//					fluteThinPillarObsidianbrickFacade,
//
//					// cornice
//					corniceStoneFacade,
//					corniceCobblestoneFacade,
//					corniceMossyCobblestoneFacade,
//					corniceStonebrickFacade,
//					corniceMossyStonebrickFacade,
//					corniceCrackedStonebrickFacade,
//					corniceObsidianbrickFacade,				
//					// crown
//					crownMoldingStoneFacade,
//					crownMoldingCobblestoneFacade,
//					crownMoldingMossyCobblestoneFacade,
//					crownMoldingStonebrickFacade,
//					crownMoldingMossyStonebrickFacade,
//					crownMoldingCrackedStonebrickFacade,
//					crownMoldingObsidianbrickFacade,				
//					// seven-eights
//					sevenEightsPillarStoneFacade,
//					sevenEightsPillarCobblestoneFacade,
//					sevenEightsPillarMossyCobblestoneFacade,
//					sevenEightsPillarStonebrickFacade,
//					sevenEightsPillarMossyStonebrickFacade,
//					sevenEightsPillarCrackedStonebrickFacade,
//					sevenEightsPillarObsidianbrickFacade,				
//					// sills
//					sillStoneBlock,
//					sillCobblestoneBlock,
//					sillMossyCobblestoneBlock,
//					sillStonebrickBlock,
//					sillMossyStonebrickBlock,
//					sillCrackedStonebrickBlock,
//					sillObsidianbrickBlock,				
//					// double sills
//					doubleSillStoneBlock,
//					doubleSillCobblestoneBlock,
//					doubleSillMossyCobblestoneBlock,
//					doubleSillStonebrickBlock,
//					doubleSillMossyStonebrickBlock,
//					doubleSillCrackedStonebrickBlock,
//					doubleSillObsidianbrickBlock,						
//					// half pillar base
//					halfPillarBaseStoneBlock,
//					halfPillarBaseCobblestoneBlock,
//					halfPillarBaseMossyCobblestoneBlock,
//					halfPillarBaseStonebrickBlock,
//					halfPillarBaseMossyStonebrickBlock,
//					halfPillarBaseCrackedStonebrickBlock,
//					halfPillarBaseObsidianbrickBlock,					
//					// half pillar
//					halfPillarStoneBlock,
//					halfPillarCobblestoneBlock,
//					halfPillarMossyCobblestoneBlock,
//					halfPillarStonebrickBlock,
//					halfPillarMossyStonebrickBlock,
//					halfPillarCrackedStonebrickBlock,
//					halfPillarObsidianbrickBlock,		
//					// middle coffer
//					cofferMiddleStoneBlock,
//					cofferMiddleCobblestoneBlock,
//					cofferMiddleMossyCobblestoneBlock,
//					cofferMiddleStonebrickBlock,
//					cofferMiddleMossyStonebrickBlock,
//					cofferMiddleCrackedStonebrickBlock,
//					cofferMiddleObsidianbrickBlock,					
//					// coffer
//					cofferStoneBlock,
//					cofferCobblestoneBlock,
//					cofferMossyCobblestoneBlock,
//					cofferStonebrickBlock,
//					cofferMossyStonebrickBlock,
//					cofferCrackedStonebrickBlock,
//					cofferObsidianbrickBlock,						
//					// wall sconce
//					wallSconceStoneFacade,
//					wallSconceCobblestoneFacade,
//					wallSconceMossyCobblestoneFacade,
//					wallSconceStonebrickFacade,
//					wallSconceMossyStonebrickFacade,
//					wallSconceCrackedStonebrickFacade,
//					wallSconceObsidianbrickFacade,
//					grateBlock
			};
			registry.registerAll(blocks);			
		}

		/**
		 * Register this mod's {@link ItemBlock}s.
		 *
		 * @param event The event
		 */
		@SubscribeEvent
		public static void registerItemBlocks(final RegistryEvent.Register<Item> event) {
			final IForgeRegistry<Item> registry = event.getRegistry();

			final ItemBlock[] items = {					
					new ItemBlock(basicStoneFacade),
					new ItemBlock(basicCobblestoneFacade),
					new ItemBlock(basicMossyCobblestoneFacade),
					new ItemBlock(basicStonebrickFacade),
					new ItemBlock(basicMossyStonebrickFacade),
					new ItemBlock(basicCrackedStonebrickFacade),					
					new ItemBlock(basicChiseledStonebrickFacade),
					new ItemBlock(basicObsidianbrickFacade),
					// tee
					new ItemBlock(teePillarStoneFacade),
					new ItemBlock(teePillarCobblestoneFacade),
					new ItemBlock(teePillarMossyCobblestoneFacade),
					new ItemBlock(teePillarStonebrickFacade),
					new ItemBlock(teePillarMossyStonebrickFacade),
					new ItemBlock(teePillarCrackedStonebrickFacade),
					new ItemBlock(teePillarObsidianbrickFacade),
					// thin tee
					new ItemBlock(teeThinPillarStoneFacade),
					new ItemBlock(teeThinPillarCobblestoneFacade),
					new ItemBlock(teeThinPillarMossyCobblestoneFacade),
					new ItemBlock(teeThinPillarStonebrickFacade),
					new ItemBlock(teeThinPillarMossyStonebrickFacade),
					new ItemBlock(teeThinPillarCrackedStonebrickFacade),
					new ItemBlock(teeThinPillarObsidianbrickFacade),
					// flute
					new ItemBlock(flutePillarStoneBlock),
					new ItemBlock(flutePillarCobblestoneBlock),
					new ItemBlock(flutePillarMossyCobblestoneBlock),
					new ItemBlock(flutePillarStonebrickBlock),
					new ItemBlock(flutePillarMossyStonebrickBlock),
					new ItemBlock(flutePillarCrackedStonebrickBlock),
					new ItemBlock(flutePillarObsidianbrickBlock),

					// flute thin
					new ItemBlock(fluteThinPillarStoneFacade),
					new ItemBlock(fluteThinPillarCobblestoneFacade),
					new ItemBlock(fluteThinPillarMossyCobblestoneFacade),
					new ItemBlock(fluteThinPillarStonebrickFacade),
					new ItemBlock(fluteThinPillarMossyStonebrickFacade),
					new ItemBlock(fluteThinPillarCrackedStonebrickFacade),
					new ItemBlock(fluteThinPillarObsidianbrickFacade),

					// cornice
					new ItemBlock(corniceStoneFacade),
					new ItemBlock(corniceCobblestoneFacade),
					new ItemBlock(corniceMossyCobblestoneFacade),
					new ItemBlock(corniceStonebrickFacade),
					new ItemBlock(corniceMossyStonebrickFacade),
					new ItemBlock(corniceCrackedStonebrickFacade),
					new ItemBlock(corniceObsidianbrickFacade),				
					// crown
					new ItemBlock(crownMoldingStoneFacade),
					new ItemBlock(crownMoldingCobblestoneFacade),
					new ItemBlock(crownMoldingMossyCobblestoneFacade),
					new ItemBlock(crownMoldingStonebrickFacade),
					new ItemBlock(crownMoldingMossyStonebrickFacade),
					new ItemBlock(crownMoldingCrackedStonebrickFacade),
					new ItemBlock(crownMoldingObsidianbrickFacade),				
					// seven-eights
					new ItemBlock(sevenEightsPillarStoneFacade),
					new ItemBlock(sevenEightsPillarCobblestoneFacade),
					new ItemBlock(sevenEightsPillarMossyCobblestoneFacade),
					new ItemBlock(sevenEightsPillarStonebrickFacade),
					new ItemBlock(sevenEightsPillarMossyStonebrickFacade),
					new ItemBlock(sevenEightsPillarCrackedStonebrickFacade),
					new ItemBlock(sevenEightsPillarObsidianbrickFacade),				
					// sills
					new ItemBlock(sillStoneBlock),
					new ItemBlock(sillCobblestoneBlock),
					new ItemBlock(sillMossyCobblestoneBlock),
					new ItemBlock(sillStonebrickBlock),
					new ItemBlock(sillMossyStonebrickBlock),
					new ItemBlock(sillCrackedStonebrickBlock),
					new ItemBlock(sillObsidianbrickBlock),				
					// double sills
					new ItemBlock(doubleSillStoneBlock),
					new ItemBlock(doubleSillCobblestoneBlock),
					new ItemBlock(doubleSillMossyCobblestoneBlock),
					new ItemBlock(doubleSillStonebrickBlock),
					new ItemBlock(doubleSillMossyStonebrickBlock),
					new ItemBlock(doubleSillCrackedStonebrickBlock),
					new ItemBlock(doubleSillObsidianbrickBlock),						
					// half pillar base
					new ItemBlock(halfPillarBaseStoneBlock),
					new ItemBlock(halfPillarBaseCobblestoneBlock),
					new ItemBlock(halfPillarBaseMossyCobblestoneBlock),
					new ItemBlock(halfPillarBaseStonebrickBlock),
					new ItemBlock(halfPillarBaseMossyStonebrickBlock),
					new ItemBlock(halfPillarBaseCrackedStonebrickBlock),
					new ItemBlock(halfPillarBaseObsidianbrickBlock),					
					// half pillar
					new ItemBlock(halfPillarStoneBlock),
					new ItemBlock(halfPillarCobblestoneBlock),
					new ItemBlock(halfPillarMossyCobblestoneBlock),
					new ItemBlock(halfPillarStonebrickBlock),
					new ItemBlock(halfPillarMossyStonebrickBlock),
					new ItemBlock(halfPillarCrackedStonebrickBlock),
					new ItemBlock(halfPillarObsidianbrickBlock),		
					// middle coffer
					new ItemBlock(cofferMiddleStoneBlock),
					new ItemBlock(cofferMiddleCobblestoneBlock),
					new ItemBlock(cofferMiddleMossyCobblestoneBlock),
					new ItemBlock(cofferMiddleStonebrickBlock),
					new ItemBlock(cofferMiddleMossyStonebrickBlock),
					new ItemBlock(cofferMiddleCrackedStonebrickBlock),
					new ItemBlock(cofferMiddleObsidianbrickBlock),					
					// coffer
					new ItemBlock(cofferStoneBlock),
					new ItemBlock(cofferCobblestoneBlock),
					new ItemBlock(cofferMossyCobblestoneBlock),
					new ItemBlock(cofferStonebrickBlock),
					new ItemBlock(cofferMossyStonebrickBlock),
					new ItemBlock(cofferCrackedStonebrickBlock),
					new ItemBlock(cofferObsidianbrickBlock),						
					// wall sconce
					new ItemBlock(wallSconceStoneFacade),
					new ItemBlock(wallSconceCobblestoneFacade),
					new ItemBlock(wallSconceMossyCobblestoneFacade),
					new ItemBlock(wallSconceStonebrickFacade),
					new ItemBlock(wallSconceMossyStonebrickFacade),
					new ItemBlock(wallSconceCrackedStonebrickFacade),
					new ItemBlock(wallSconceObsidianbrickFacade),
					new ItemBlock(grateBlock)
			};

			for (final ItemBlock item : items) {
				final Block block = item.getBlock();
				final ResourceLocation registryName = Preconditions.checkNotNull(block.getRegistryName(), "Block %s has null registry name", block);
				registry.register(item.setRegistryName(registryName));
				ITEM_BLOCKS.add(item);
			}
		}

	}
}
