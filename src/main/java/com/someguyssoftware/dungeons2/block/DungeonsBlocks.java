/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.gottschcore.AbstractModObjectHolder;
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
import net.minecraftforge.registries.IForgeRegistry;

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
	public static Block BASIC_SANDSTONE_FACADE;
	public static Block BASIC_SMOOTH_SANDSTONE_FACADE; //basicSmoothSandstoneFacade

	// quarter facade
	public static Block QUARTER_STONE_FACADE;
	public static Block QUARTER_COBBLESTONE_FACADE;
	public static Block QUARTER_MOSSY_COBBLESTONE_FACADE;
	public static Block QUARTER_STONEBRICK_FACADE;
	public static Block QUARTER_MOSSY_STONEBRICK_FACADE;
	public static Block QUARTER_CRACKED_STONEBRICK_FACADE;
	public static Block QUARTER_CHISELED_STONEBRICK_FACADE;
	public static Block QUARTER_OBSIDIANBRICK_FACADE;
	public static Block QUARTER_SANDSTONE_FACADE;
	public static Block QUARTER_SMOOTH_SANDSTONE_FACADE;
	
//	public static Block basicCobblestoneFacade;
//	public static Block basicMossyCobblestoneFacade;
//	public static Block basicStonebrickFacade;
//	public static Block basicMossyStonebrickFacade;
//	public static Block basicCrackedStonebrickFacade;
//	public static Block basicChiseledStonebrickFacade;
//	public static Block basicObsidianbrickFacade;	
//	public static Block BASIC_SANDSTONE_FACADE;
//	public static Block BASIC_SMOOTH_SANDSTONE_FACADE;
	
	// "T" Pillar
	public static Block teePillarStoneFacade;
	public static Block teePillarCobblestoneFacade;
	public static Block teePillarMossyCobblestoneFacade;
	public static Block teePillarStonebrickFacade;
	public static Block teePillarMossyStonebrickFacade;
	public static Block teePillarCrackedStonebrickFacade;
	public static Block teePillarObsidianbrickFacade;
	
	public static Block TEE_PILLAR_SANDSTONE_FACADE;
	public static Block TEE_PILLAR_SMOOTH_SANDSTONE_FACADE;

	// thin "T" Pillar	
	public static Block teeThinPillarStoneFacade;
	public static Block teeThinPillarCobblestoneFacade;
	public static Block teeThinPillarMossyCobblestoneFacade;
	public static Block teeThinPillarStonebrickFacade;
	public static Block teeThinPillarMossyStonebrickFacade;
	public static Block teeThinPillarCrackedStonebrickFacade;
	public static Block teeThinPillarObsidianbrickFacade;

	public static Block TEE_THIN_PILLAR_SANDSTONE_FACADE;
	public static Block TEE_THIN_PILLAR_SMOOTH_SANDSTONE_FACADE;
	
	// cornice
	public static Block corniceStoneFacade;
	public static Block corniceCobblestoneFacade;
	public static Block corniceMossyCobblestoneFacade;
	public static Block corniceStonebrickFacade;
	public static Block corniceMossyStonebrickFacade;
	public static Block corniceCrackedStonebrickFacade;
	public static Block corniceObsidianbrickFacade;

	public static Block CORNICE_SANDSTONE_FACADE;
	public static Block CORNICE_SMOOTH_SANDSTONE_FACADE;
	
	// flute pillar
	public static Block flutePillarStoneBlock;
	public static Block flutePillarCobblestoneBlock;
	public static Block flutePillarMossyCobblestoneBlock;
	public static Block flutePillarStonebrickBlock;
	public static Block flutePillarMossyStonebrickBlock;
	public static Block flutePillarCrackedStonebrickBlock;
	public static Block flutePillarObsidianbrickBlock;

	public static Block FLUTE_PILLAR_SANDSTONE_BLOCK;
	public static Block FLUTE_PILLAR_SMOOTH_SANDSTONE_BLOCK;
	
	// flute thin pillar
	public static Block fluteThinPillarStoneFacade;
	public static Block fluteThinPillarCobblestoneFacade;
	public static Block fluteThinPillarMossyCobblestoneFacade;
	public static Block fluteThinPillarStonebrickFacade;
	public static Block fluteThinPillarMossyStonebrickFacade;
	public static Block fluteThinPillarCrackedStonebrickFacade;
	public static Block fluteThinPillarObsidianbrickFacade;

	public static Block FLUTE_THIN_PILLAR_SANDSTONE_FACADE;
	public static Block FLUTE_THIN_PILLAR_SMOOTH_SANDSTONE_FACADE;
	
	// seven eights
	public static Block sevenEightsPillarStoneFacade;
	public static Block sevenEightsPillarCobblestoneFacade;
	public static Block sevenEightsPillarMossyCobblestoneFacade;
	public static Block sevenEightsPillarStonebrickFacade;
	public static Block sevenEightsPillarMossyStonebrickFacade;
	public static Block sevenEightsPillarCrackedStonebrickFacade;
	public static Block sevenEightsPillarObsidianbrickFacade;

	public static Block SEVEN_EIGHTS_PILLAR_SANDSTONE_FACADE;
	public static Block SEVEN_EIGHTS_PILLAR_SMOOTH_SANDSTONE_FACADE;
	
	// sills
	public static Block sillStoneBlock;
	public static Block sillCobblestoneBlock;
	public static Block sillMossyCobblestoneBlock;
	public static Block sillStonebrickBlock;
	public static Block sillMossyStonebrickBlock;
	public static Block sillCrackedStonebrickBlock;
	public static Block sillObsidianbrickBlock;

	public static Block SILL_SANDSTONE_BLOCK;
	public static Block SILL_SMOOTH_SANDSTONE_BLOCK;
	
	// double sills
	public static Block doubleSillStoneBlock;
	public static Block doubleSillCobblestoneBlock;
	public static Block doubleSillMossyCobblestoneBlock;
	public static Block doubleSillStonebrickBlock;
	public static Block doubleSillMossyStonebrickBlock;
	public static Block doubleSillCrackedStonebrickBlock;	
	public static Block doubleSillObsidianbrickBlock;

	public static Block DOUBLE_SILL_SANDSTONE_BLOCK;
	public static Block DOUBLE_SILL_SMOOTH_SANDSTONE_BLOCK;
	
	// half pillar base
	public static Block halfPillarBaseStoneBlock;
	public static Block halfPillarBaseCobblestoneBlock;
	public static Block halfPillarBaseMossyCobblestoneBlock;
	public static Block halfPillarBaseStonebrickBlock;
	public static Block halfPillarBaseMossyStonebrickBlock;
	public static Block halfPillarBaseCrackedStonebrickBlock;
	public static Block halfPillarBaseObsidianbrickBlock;

	public static Block HALF_PILLAR_BASE_SANDSTONE_BLOCK;
	public static Block HALF_PILLAR_BASE_SMOOTH_SANDSTONE_BLOCK;
	
	// half pillar
	public static Block halfPillarStoneBlock;
	public static Block halfPillarCobblestoneBlock;
	public static Block halfPillarMossyCobblestoneBlock;
	public static Block halfPillarStonebrickBlock;
	public static Block halfPillarMossyStonebrickBlock;
	public static Block halfPillarCrackedStonebrickBlock;
	public static Block halfPillarObsidianbrickBlock;

	public static Block HALF_PILLAR_SANDSTONE_BLOCK;
	public static Block HALF_PILLAR_SMOOTH_SANDSTONE_BLOCK;
	
	// coffer middle
	public static Block cofferMiddleStoneBlock;
	public static Block cofferMiddleCobblestoneBlock;
	public static Block cofferMiddleMossyCobblestoneBlock;
	public static Block cofferMiddleStonebrickBlock;
	public static Block cofferMiddleMossyStonebrickBlock;
	public static Block cofferMiddleCrackedStonebrickBlock;
	public static Block cofferMiddleObsidianbrickBlock;

	public static Block COFFER_MIDDLE_SANDSTONE_BLOCK;
	public static Block COFFER_MIDDLE_SMOOTH_SANDSTONE_BLOCK;
	
	// coffer
	public static Block cofferStoneBlock;
	public static Block cofferCobblestoneBlock;
	public static Block cofferMossyCobblestoneBlock;
	public static Block cofferStonebrickBlock;
	public static Block cofferMossyStonebrickBlock;
	public static Block cofferCrackedStonebrickBlock;
	public static Block cofferObsidianbrickBlock;

	public static Block COFFER_SANDSTONE_BLOCK;
	public static Block COFFER_SMOOTH_SANDSTONE_BLOCK;
	
	// crown molding
	public static Block crownMoldingStoneFacade;
	public static Block crownMoldingCobblestoneFacade;
	public static Block crownMoldingMossyCobblestoneFacade;
	public static Block crownMoldingStonebrickFacade;
	public static Block crownMoldingMossyStonebrickFacade;
	public static Block crownMoldingCrackedStonebrickFacade;
	public static Block crownMoldingObsidianbrickFacade;

	public static Block CROWN_MOLDING_SANDSTONE_FACADE;
	public static Block CROWN_MOLDING_SMOOTH_SANDSTONE_FACADE;
	
	// wall sconce 
	public static Block wallSconceStoneFacade;
	public static Block wallSconceCobblestoneFacade;
	public static Block wallSconceMossyCobblestoneFacade;
	public static Block wallSconceStonebrickFacade;
	public static Block wallSconceMossyStonebrickFacade;
	public static Block wallSconceCrackedStonebrickFacade;
	public static Block wallSconceObsidianbrickFacade;

	public static Block WALL_SCONCE_SANDSTONE_FACADE;
	public static Block WALL_SCONCE_SMOOTH_SANDSTONE_FACADE;
	
	// grate
	public static Block grateBlock;

	// other
	public static Block SANDSTONE_GRAVEL;
	
	// decorations
	//	public static Block moss;
	//	public static Block moss2;
	//	public static Block lichen;
	//	public static Block lichen2;
	public static final Block PUDDLE;
	//	public static Block blood;
	//	public static Block mold1;

	public static final Block DEFERRED_DUNGEON_GENERATOR;
	
	public static List<Block> BLOCKS = new ArrayList<>(100);
	
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
		BASIC_SANDSTONE_FACADE = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.BASIC_SANDSTONE_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		BASIC_SMOOTH_SANDSTONE_FACADE = new BasicFacadeBlock(Dungeons2.MODID, ModConfig.BASIC_SMOOTH_SANDSTONE_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// quarter facade
		QUARTER_STONE_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_STONE_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		QUARTER_COBBLESTONE_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_COBBLESTONE_FACADE_ID, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		QUARTER_MOSSY_COBBLESTONE_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_MOSSY_COBBLESTONE_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		QUARTER_STONEBRICK_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_STONEBRICK_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		QUARTER_MOSSY_STONEBRICK_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_MOSSY_STONEBRICK_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		QUARTER_CRACKED_STONEBRICK_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_CRACKED_STONEBRICK_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		QUARTER_CHISELED_STONEBRICK_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_CHISELED_STONEBRICK_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		QUARTER_OBSIDIANBRICK_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_OBSIDIANBRICK_FACADE_ID, Material.ROCK).setHardness(50F).setResistance(2000F);
		QUARTER_SANDSTONE_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_SANDSTONE_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		QUARTER_SMOOTH_SANDSTONE_FACADE = new QuarterFacadeBlock(Dungeons2.MODID, ModConfig.QUARTER_SMOOTH_SANDSTONE_FACADE_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		
		// t pillar
		teePillarStoneFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teePillarCobblestoneFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		teePillarMossyCobblestoneFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		teePillarStonebrickFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teePillarMossyStonebrickFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teePillarCrackedStonebrickFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teePillarObsidianbrickFacade = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.teePillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);
		TEE_PILLAR_SANDSTONE_FACADE = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.TEE_PILLAR_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		TEE_PILLAR_SMOOTH_SANDSTONE_FACADE = new TeePillarFacadeBlock(Dungeons2.MODID, ModConfig.TEE_PILLAR_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// t pillar facade
		teeThinPillarStoneFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teeThinPillarCobblestoneFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		teeThinPillarMossyCobblestoneFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		teeThinPillarStonebrickFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teeThinPillarMossyStonebrickFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarMossyStonebrickBlockId, Material.ROCK ).setHardness(1.5F).setResistance(10.0F);
		teeThinPillarCrackedStonebrickFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarCrackedStonebrickBlockId,Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		teeThinPillarObsidianbrickFacade = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.teeThinPillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);
		TEE_THIN_PILLAR_SANDSTONE_FACADE = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.TEE_THIN_PILLAR_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		TEE_THIN_PILLAR_SMOOTH_SANDSTONE_FACADE = new TeeThinPillarFacadeBlock(Dungeons2.MODID, ModConfig.TEE_THIN_PILLAR_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// flute pillar
		flutePillarStoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		flutePillarCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		flutePillarMossyCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		flutePillarStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		flutePillarMossyStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		flutePillarCrackedStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		flutePillarObsidianbrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.flutePillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);
		FLUTE_PILLAR_SANDSTONE_BLOCK = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.FLUTE_PILLAR_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		FLUTE_PILLAR_SMOOTH_SANDSTONE_BLOCK = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.FLUTE_PILLAR_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// flute pillar facade
		fluteThinPillarStoneFacade = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		fluteThinPillarCobblestoneFacade= new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		fluteThinPillarMossyCobblestoneFacade= new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		fluteThinPillarStonebrickFacade = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		fluteThinPillarMossyStonebrickFacade= new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		fluteThinPillarCrackedStonebrickFacade = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		fluteThinPillarObsidianbrickFacade = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.fluteThinPillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);
		FLUTE_THIN_PILLAR_SANDSTONE_FACADE = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.FLUTE_THIN_PILLAR_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		FLUTE_THIN_PILLAR_SMOOTH_SANDSTONE_FACADE = new FlutePillarFacadeBlock(Dungeons2.MODID, ModConfig.FLUTE_THIN_PILLAR_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// cornice
		corniceStoneFacade = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		corniceCobblestoneFacade= new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		corniceMossyCobblestoneFacade= new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		corniceStonebrickFacade = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		corniceMossyStonebrickFacade= new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		corniceCrackedStonebrickFacade = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		corniceObsidianbrickFacade = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.corniceObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);
		CORNICE_SANDSTONE_FACADE = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.CORNICE_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		CORNICE_SMOOTH_SANDSTONE_FACADE = new CorniceFacadeBlock(Dungeons2.MODID, ModConfig.CORNICE_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// crown molding
		crownMoldingStoneFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingCobblestoneFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingCobblestoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingMossyCobblestoneFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingMossyCobblestoneBlockId, Material.ROCK).setHardness(1.5F);
		crownMoldingStonebrickFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingMossyStonebrickFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingCrackedStonebrickFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		crownMoldingObsidianbrickFacade = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.crownMoldingObsidianbrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(2000.0F);
		CROWN_MOLDING_SANDSTONE_FACADE = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.CROWN_MOLDING_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		CROWN_MOLDING_SMOOTH_SANDSTONE_FACADE = new CrownMoldingFacadeBlock(Dungeons2.MODID, ModConfig.CROWN_MOLDING_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// seven eights
		sevenEightsPillarStoneFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		sevenEightsPillarCobblestoneFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		sevenEightsPillarMossyCobblestoneFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F);
		sevenEightsPillarStonebrickFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		sevenEightsPillarMossyStonebrickFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		sevenEightsPillarCrackedStonebrickFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		sevenEightsPillarObsidianbrickFacade = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.sevenEightsPillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000F);
		SEVEN_EIGHTS_PILLAR_SANDSTONE_FACADE = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.SEVEN_EIGHTS_PILLAR_SANDSTONE, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		SEVEN_EIGHTS_PILLAR_SMOOTH_SANDSTONE_FACADE = new SevenEightsPillarFacadeBlock(Dungeons2.MODID, ModConfig.SEVEN_EIGHTS_PILLAR_SMOOTH_SANDSTONE, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// sills
		sillStoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		sillCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		sillMossyCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB); 
		sillStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		sillMossyStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		sillCrackedStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		sillObsidianbrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.sillObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		SILL_SANDSTONE_BLOCK = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.SILL_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		SILL_SMOOTH_SANDSTONE_BLOCK = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.SILL_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);

		// double sills
		doubleSillStoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		doubleSillCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		doubleSillMossyCobblestoneBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillMossyCobblestoneBlockId, Material.ROCK).setHardness(2.0F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		doubleSillStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		doubleSillMossyStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		doubleSillCrackedStonebrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		doubleSillObsidianbrickBlock = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.doubleSillObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		DOUBLE_SILL_SANDSTONE_BLOCK = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.DOUBLE_SILL_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);
		DOUBLE_SILL_SMOOTH_SANDSTONE_BLOCK = new CardinalDirectionBlock(Dungeons2.MODID, ModConfig.DOUBLE_SILL_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F).setCreativeTab(Dungeons2.DUNGEONS_TAB);

		// half pillar bases
		halfPillarBaseStoneBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarBaseCobblestoneBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		halfPillarBaseMossyCobblestoneBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseMossyCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		halfPillarBaseStonebrickBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarBaseMossyStonebrickBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F);
		halfPillarBaseCrackedStonebrickBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseCrackedStonebrickBlockId,Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarBaseObsidianbrickBlock = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.halfPillarBaseObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F);
		HALF_PILLAR_BASE_SANDSTONE_BLOCK = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.HALF_PILLAR_BASE_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		HALF_PILLAR_BASE_SMOOTH_SANDSTONE_BLOCK = new RelativeDirectionFacadeBlock(Dungeons2.MODID, ModConfig.HALF_PILLAR_BASE_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// half pillars
		halfPillarStoneBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarCobblestoneBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		halfPillarMossyCobblestoneBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarMossyCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		halfPillarStonebrickBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarMossyStonebrickBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarCrackedStonebrickBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		halfPillarObsidianbrickBlock = new HalfPillarBlock(Dungeons2.MODID, ModConfig.halfPillarObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F);
		HALF_PILLAR_SANDSTONE_BLOCK = new HalfPillarBlock(Dungeons2.MODID, ModConfig.HALF_PILLAR_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		HALF_PILLAR_SMOOTH_SANDSTONE_BLOCK = new HalfPillarBlock(Dungeons2.MODID, ModConfig.HALF_PILLAR_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// middle coffer
		cofferMiddleStoneBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMiddleCobblestoneBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		cofferMiddleMossyCobblestoneBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleMossyCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		cofferMiddleStonebrickBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMiddleMossyStonebrickBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMiddleCrackedStonebrickBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMiddleObsidianbrickBlock = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.cofferMiddleObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F);
		COFFER_MIDDLE_SANDSTONE_BLOCK = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.COFFER_MIDDLE_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		COFFER_MIDDLE_SMOOTH_SANDSTONE_BLOCK = new CofferMiddleFacadeBlock(Dungeons2.MODID, ModConfig.COFFER_MIDDLE_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// coffer
		cofferStoneBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferStoneBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferCobblestoneBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		cofferMossyCobblestoneBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferMossyCobblestoneBlockId, Material.ROCK).setHardness(2F).setResistance(10.0F);
		cofferStonebrickBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferMossyStonebrickBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferMossyStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferCrackedStonebrickBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferCrackedStonebrickBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		cofferObsidianbrickBlock = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.cofferObsidianbrickBlockId, Material.ROCK).setHardness(50F).setResistance(2000.0F);
		COFFER_SANDSTONE_BLOCK = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.COFFER_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);
		COFFER_SMOOTH_SANDSTONE_BLOCK = new CofferFacadeBlock(Dungeons2.MODID, ModConfig.COFFER_SMOOTH_SANDSTONE_BLOCK_ID, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// wall sconce
		wallSconceStoneFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceStoneBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceCobblestoneFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceCobblestoneBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceMossyCobblestoneFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceMossyCobblestoneBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceStonebrickFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceStonebrickBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceMossyStonebrickFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceMossyStonebrickBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceCrackedStonebrickFacade = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceCrackedStonebrickBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		wallSconceObsidianbrickFacade = 	new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.wallSconceObsidianbrickBlockId).setHardness(0.0F).setLightLevel(0.9375F);
		WALL_SCONCE_SANDSTONE_FACADE = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.WALL_SCONCE_SANDSTONE_BLOCK_ID).setHardness(0.0F).setLightLevel(0.9375F);
		WALL_SCONCE_SMOOTH_SANDSTONE_FACADE = new DungeonsTorchBlock(Dungeons2.MODID, ModConfig.WALL_SCONCE_SMOOTH_SANDSTONE_BLOCK_ID).setHardness(0.0F).setLightLevel(0.9375F);

		// grate
		grateBlock = new GrateBlock(Dungeons2.MODID, ModConfig.grateBlockId, Material.ROCK).setHardness(1.5F).setResistance(10.0F);

		// other
		SANDSTONE_GRAVEL = new SandstoneGravelBlock(Dungeons2.MODID, ModConfig.SANDSTONE_GRAVEL_BLOCK_ID, Material.ROCK);

		// decorations - moss
		//	    moss = new DecorationBlock(Material.GRASS, ModConfig.mossBlockId);
		//	    moss2 = new DecorationBlock(Material.GRASS, ModConfig.moss2BlockId);
		//	    lichen = new DecorationBlock(Material.GRASS, ModConfig.lichenBlockId);
		//	    lichen2 = new DecorationBlock(Material.GRASS, ModConfig.lichen2BlockId);
		PUDDLE = new DecorationBlock(Dungeons2.MODID, ModConfig.puddleBlockId, Material.GRASS);
		//	    blood = new DecorationBlock(Material.GRASS, ModConfig.bloodBlockId);
		//	    mold1 = new DecorationBlock(Material.GRASS, ModConfig.mold1BlockId);
		
		DEFERRED_DUNGEON_GENERATOR = new DeferredDungeonGeneratorBlock(Dungeons2.MODID, "deferred_dungeon_generator");
		
		/*
		 *  setup array
		 */
		
		// basic
		BLOCKS.add(basicStoneFacade);
		BLOCKS.add(basicCobblestoneFacade);
		BLOCKS.add(basicMossyCobblestoneFacade);
		BLOCKS.add(basicStonebrickFacade);
		BLOCKS.add(basicMossyStonebrickFacade);
		BLOCKS.add(basicCrackedStonebrickFacade);					
		BLOCKS.add(basicChiseledStonebrickFacade);
		BLOCKS.add(basicObsidianbrickFacade);
		BLOCKS.add(BASIC_SMOOTH_SANDSTONE_FACADE);
		BLOCKS.add(BASIC_SANDSTONE_FACADE);
		// quarter basic
		BLOCKS.add(QUARTER_STONE_FACADE);
		BLOCKS.add(QUARTER_COBBLESTONE_FACADE);
		BLOCKS.add(QUARTER_MOSSY_COBBLESTONE_FACADE);
		BLOCKS.add(QUARTER_STONEBRICK_FACADE);
		BLOCKS.add(QUARTER_MOSSY_STONEBRICK_FACADE);
		BLOCKS.add(QUARTER_CRACKED_STONEBRICK_FACADE);
		BLOCKS.add(QUARTER_CHISELED_STONEBRICK_FACADE);
		BLOCKS.add(QUARTER_OBSIDIANBRICK_FACADE);
		BLOCKS.add(QUARTER_SANDSTONE_FACADE);
		BLOCKS.add(QUARTER_SMOOTH_SANDSTONE_FACADE);
		
		// tee
		BLOCKS.add(teePillarStoneFacade);
		BLOCKS.add(teePillarCobblestoneFacade);
		BLOCKS.add(teePillarMossyCobblestoneFacade);
		BLOCKS.add(teePillarStonebrickFacade);
		BLOCKS.add(teePillarMossyStonebrickFacade);
		BLOCKS.add(teePillarCrackedStonebrickFacade);
		BLOCKS.add(teePillarObsidianbrickFacade);
		BLOCKS.add(TEE_PILLAR_SMOOTH_SANDSTONE_FACADE);
		BLOCKS.add(TEE_PILLAR_SANDSTONE_FACADE);
		// thin tee
		BLOCKS.add(teeThinPillarStoneFacade);
		BLOCKS.add(teeThinPillarCobblestoneFacade);
		BLOCKS.add(teeThinPillarMossyCobblestoneFacade);
		BLOCKS.add(teeThinPillarStonebrickFacade);
		BLOCKS.add(teeThinPillarMossyStonebrickFacade);
		BLOCKS.add(teeThinPillarCrackedStonebrickFacade);
		BLOCKS.add(teeThinPillarObsidianbrickFacade);
		BLOCKS.add(TEE_THIN_PILLAR_SMOOTH_SANDSTONE_FACADE);
		BLOCKS.add(TEE_THIN_PILLAR_SANDSTONE_FACADE);
		// flute
		BLOCKS.add(flutePillarStoneBlock);
		BLOCKS.add(flutePillarCobblestoneBlock);
		BLOCKS.add(flutePillarMossyCobblestoneBlock);
		BLOCKS.add(flutePillarStonebrickBlock);
		BLOCKS.add(flutePillarMossyStonebrickBlock);
		BLOCKS.add(flutePillarCrackedStonebrickBlock);
		BLOCKS.add(flutePillarObsidianbrickBlock);
		BLOCKS.add(FLUTE_PILLAR_SANDSTONE_BLOCK);
		BLOCKS.add(FLUTE_PILLAR_SMOOTH_SANDSTONE_BLOCK);
		// flute thin
		BLOCKS.add(fluteThinPillarStoneFacade);
		BLOCKS.add(fluteThinPillarCobblestoneFacade);
		BLOCKS.add(fluteThinPillarMossyCobblestoneFacade);
		BLOCKS.add(fluteThinPillarStonebrickFacade);
		BLOCKS.add(fluteThinPillarMossyStonebrickFacade);
		BLOCKS.add(fluteThinPillarCrackedStonebrickFacade);
		BLOCKS.add(fluteThinPillarObsidianbrickFacade);
		BLOCKS.add(FLUTE_THIN_PILLAR_SANDSTONE_FACADE);
		BLOCKS.add(FLUTE_THIN_PILLAR_SMOOTH_SANDSTONE_FACADE);
		// cornice
		BLOCKS.add(corniceStoneFacade);
		BLOCKS.add(corniceCobblestoneFacade);
		BLOCKS.add(corniceMossyCobblestoneFacade);
		BLOCKS.add(corniceStonebrickFacade);
		BLOCKS.add(corniceMossyStonebrickFacade);
		BLOCKS.add(corniceCrackedStonebrickFacade);
		BLOCKS.add(corniceObsidianbrickFacade);
		BLOCKS.add(CORNICE_SANDSTONE_FACADE);
		BLOCKS.add(CORNICE_SMOOTH_SANDSTONE_FACADE);	
		// crown
		BLOCKS.add(crownMoldingStoneFacade);
		BLOCKS.add(crownMoldingCobblestoneFacade);
		BLOCKS.add(crownMoldingMossyCobblestoneFacade);
		BLOCKS.add(crownMoldingStonebrickFacade);
		BLOCKS.add(crownMoldingMossyStonebrickFacade);
		BLOCKS.add(crownMoldingCrackedStonebrickFacade);
		BLOCKS.add(crownMoldingObsidianbrickFacade);
		BLOCKS.add(CROWN_MOLDING_SANDSTONE_FACADE);
		BLOCKS.add(CROWN_MOLDING_SMOOTH_SANDSTONE_FACADE);
		// seven-eights
		BLOCKS.add(sevenEightsPillarStoneFacade);
		BLOCKS.add(sevenEightsPillarCobblestoneFacade);
		BLOCKS.add(sevenEightsPillarMossyCobblestoneFacade);
		BLOCKS.add(sevenEightsPillarStonebrickFacade);
		BLOCKS.add(sevenEightsPillarMossyStonebrickFacade);
		BLOCKS.add(sevenEightsPillarCrackedStonebrickFacade);
		BLOCKS.add(sevenEightsPillarObsidianbrickFacade);
		BLOCKS.add(SEVEN_EIGHTS_PILLAR_SANDSTONE_FACADE);
		BLOCKS.add(SEVEN_EIGHTS_PILLAR_SMOOTH_SANDSTONE_FACADE);	
		// sills
		BLOCKS.add(sillStoneBlock);
		BLOCKS.add(sillCobblestoneBlock);
		BLOCKS.add(sillMossyCobblestoneBlock);
		BLOCKS.add(sillStonebrickBlock);
		BLOCKS.add(sillMossyStonebrickBlock);
		BLOCKS.add(sillCrackedStonebrickBlock);
		BLOCKS.add(sillObsidianbrickBlock);
		BLOCKS.add(SILL_SANDSTONE_BLOCK);
		BLOCKS.add(SILL_SMOOTH_SANDSTONE_BLOCK);
		// double sills
		BLOCKS.add(doubleSillStoneBlock);
		BLOCKS.add(doubleSillCobblestoneBlock);
		BLOCKS.add(doubleSillMossyCobblestoneBlock);
		BLOCKS.add(doubleSillStonebrickBlock);
		BLOCKS.add(doubleSillMossyStonebrickBlock);
		BLOCKS.add(doubleSillCrackedStonebrickBlock);
		BLOCKS.add(doubleSillObsidianbrickBlock);
		BLOCKS.add(DOUBLE_SILL_SANDSTONE_BLOCK);
		BLOCKS.add(DOUBLE_SILL_SMOOTH_SANDSTONE_BLOCK);	
		// half pillar base
		BLOCKS.add(halfPillarBaseStoneBlock);
		BLOCKS.add(halfPillarBaseCobblestoneBlock);
		BLOCKS.add(halfPillarBaseMossyCobblestoneBlock);
		BLOCKS.add(halfPillarBaseStonebrickBlock);
		BLOCKS.add(halfPillarBaseMossyStonebrickBlock);
		BLOCKS.add(halfPillarBaseCrackedStonebrickBlock);
		BLOCKS.add(halfPillarBaseObsidianbrickBlock);
		BLOCKS.add(HALF_PILLAR_BASE_SANDSTONE_BLOCK);
		BLOCKS.add(HALF_PILLAR_BASE_SMOOTH_SANDSTONE_BLOCK);
		// half pillar
		BLOCKS.add(halfPillarStoneBlock);
		BLOCKS.add(halfPillarCobblestoneBlock);
		BLOCKS.add(halfPillarMossyCobblestoneBlock);
		BLOCKS.add(halfPillarStonebrickBlock);
		BLOCKS.add(halfPillarMossyStonebrickBlock);
		BLOCKS.add(halfPillarCrackedStonebrickBlock);
		BLOCKS.add(halfPillarObsidianbrickBlock);
		BLOCKS.add(HALF_PILLAR_SANDSTONE_BLOCK);
		BLOCKS.add(HALF_PILLAR_SMOOTH_SANDSTONE_BLOCK);
		// middle coffer
		BLOCKS.add(cofferMiddleStoneBlock);
		BLOCKS.add(cofferMiddleCobblestoneBlock);
		BLOCKS.add(cofferMiddleMossyCobblestoneBlock);
		BLOCKS.add(cofferMiddleStonebrickBlock);
		BLOCKS.add(cofferMiddleMossyStonebrickBlock);
		BLOCKS.add(cofferMiddleCrackedStonebrickBlock);
		BLOCKS.add(cofferMiddleObsidianbrickBlock);
		BLOCKS.add(COFFER_MIDDLE_SANDSTONE_BLOCK);
		BLOCKS.add(COFFER_MIDDLE_SMOOTH_SANDSTONE_BLOCK);
		// coffer
		BLOCKS.add(cofferStoneBlock);
		BLOCKS.add(cofferCobblestoneBlock);
		BLOCKS.add(cofferMossyCobblestoneBlock);
		BLOCKS.add(cofferStonebrickBlock);
		BLOCKS.add(cofferMossyStonebrickBlock);
		BLOCKS.add(cofferCrackedStonebrickBlock);
		BLOCKS.add(cofferObsidianbrickBlock);
		BLOCKS.add(COFFER_SANDSTONE_BLOCK);
		BLOCKS.add(COFFER_SMOOTH_SANDSTONE_BLOCK);
		// wall sconce
		BLOCKS.add(wallSconceStoneFacade);
		BLOCKS.add(wallSconceCobblestoneFacade);
		BLOCKS.add(wallSconceMossyCobblestoneFacade);
		BLOCKS.add(wallSconceStonebrickFacade);
		BLOCKS.add(wallSconceMossyStonebrickFacade);
		BLOCKS.add(wallSconceCrackedStonebrickFacade);
		BLOCKS.add(wallSconceObsidianbrickFacade);
		BLOCKS.add(WALL_SCONCE_SANDSTONE_FACADE);
		BLOCKS.add(WALL_SCONCE_SMOOTH_SANDSTONE_FACADE);
		// grate
		BLOCKS.add(grateBlock);
		// other
		BLOCKS.add(SANDSTONE_GRAVEL);
	}

	/**
	 * 
	 * @author Mark Gottschling on Jan 13, 2019
	 *
	 */
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
		
			for (Block b : BLOCKS) {
				registry.register(b);
			}
		}

		/**
		 * Register this mod's {@link ItemBlock}s.
		 *
		 * @param event The event
		 */
		@SubscribeEvent
		public static void registerItemBlocks(final RegistryEvent.Register<Item> event) {
			final IForgeRegistry<Item> registry = event.getRegistry();

			for (Block b : BLOCKS) {
				ItemBlock itemBlock = new ItemBlock(b);
				final ResourceLocation registryName = Preconditions.checkNotNull(b.getRegistryName(), "Block %s has null registry name", b);
				registry.register(itemBlock.setRegistryName(registryName));
				ITEM_BLOCKS.add(itemBlock);			
			}
		}

	}
}
