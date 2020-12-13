/**
 * 
 */
package com.someguyssoftware.dungeons2.rotate;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.block.BasicFacadeBlock;
import com.someguyssoftware.dungeons2.block.CorniceFacadeBlock;
import com.someguyssoftware.dungeons2.block.CrownMoldingFacadeBlock;
import com.someguyssoftware.dungeons2.block.FlutePillarBlock;
import com.someguyssoftware.dungeons2.block.FlutePillarFacadeBlock;
import com.someguyssoftware.dungeons2.block.SevenEightsPillarFacadeBlock;
import com.someguyssoftware.dungeons2.block.TeePillarFacadeBlock;
import com.someguyssoftware.dungeons2.block.TeeThinPillarFacadeBlock;
import com.someguyssoftware.gottschcore.block.CardinalDirectionBlock;
import com.someguyssoftware.gottschcore.block.CardinalDirectionFacadeBlock;
import com.someguyssoftware.gottschcore.block.RelativeDirectionBlock;
import com.someguyssoftware.gottschcore.block.RelativeDirectionFacadeBlock;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.enums.Rotate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockVine;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

/**
 * @author Mark Gottschling on Aug 4, 2016
 *
 */
public class RotatorHelper {
	private static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
	
	// rotators
	private static IRotator facingRotator = new DirectionalRotator();
	private static IRotator logRotator = new LogRotator();
	private static IRotator leverRotator = new LeverRotator();
	
	/*
	 * on classload, register all blocks with rotators
	 */
	static {
		// register all the blocks with the rotators in the RotatorRegistry
		RotatorHelper.registerBlocks();
	}
	
	/**
	 * 
	 */
	private RotatorHelper() {	}
	
	/**
	 * 
	 */
	private static void registerBlocks() {
		RelativeDirectionRotator relativeDirectionBlockRotator = new RelativeDirectionRotator();
		CardinalDirectionRotator cardinalDirectionBlockRotator = new CardinalDirectionRotator();
		VineRotator vineRotator = new VineRotator();
		
		RotatorRegistry registry = RotatorRegistry.getInstance();
		registry.registerBlockRotator(BlockLog.class, logRotator, Dungeons2.instance);
		registry.registerBlockRotator(BlockOldLog.class, logRotator, Dungeons2.instance);
		registry.registerBlockRotator(BlockLever.class, leverRotator, Dungeons2.instance);
		registry.registerBlockRotator(BlockVine.class, vineRotator, Dungeons2.instance);
		registry.registerBlockRotator(RelativeDirectionBlock.class, relativeDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(RelativeDirectionFacadeBlock.class, relativeDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(CardinalDirectionBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(CardinalDirectionFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);		
		// register all the blocks
		registry.registerBlockRotator(BasicFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(FlutePillarFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);	
		registry.registerBlockRotator(CrownMoldingFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(CorniceFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(TeeThinPillarFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(TeePillarFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(FlutePillarBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(FlutePillarFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
		registry.registerBlockRotator(SevenEightsPillarFacadeBlock.class, cardinalDirectionBlockRotator, Dungeons2.instance);
	}
	
	/**
	 * 
	 * @param blockState
	 * @param direction
	 * @return
	 */
	public static IBlockState rotateBlock(IBlockState blockState, Direction direction) {
		Block block = blockState.getBlock();

		// check against the list of blocks to ignore
		if (block == Blocks.AIR
				|| block instanceof BlockSlab) return blockState;
		
		// determine which rotator implementation to use
		IRotator rotator = null;
		if (RotatorRegistry.getInstance().has(block.getClass())) {
			rotator = RotatorRegistry.getInstance().get(block.getClass());
//			Dungeons2.log.debug("Using rotator: " + rotator.getClass().getSimpleName());
		}
		// most common property/rotator
		else if (blockState.getProperties().containsKey(FACING)) {
//			Dungeons2.log.debug(blockState.getBlock().getUnlocalizedName() + ": Using default FACING rotator");
			rotator = facingRotator;
		}
		else {
			Dungeons2.log.debug("Can not locate rotator for block: " + block.getClass().getName());
		}
		
		if (rotator == null) {
			Dungeons2.log.debug("Rotator is null.");
			return blockState;
		}
		
		return rotator.rotate(blockState, direction);
	}
	
	/**
	 * 
	 * @param blockState
	 * @param rotate
	 * @return
	 */
	public static IBlockState rotateBlock(IBlockState blockState, Rotate rotate) {
		
		Block block = blockState.getBlock();

		// determine which rotator implementation to use
		IRotator rotator = null;

		// most common property/rotator
		if (blockState.getProperties().containsKey(FACING)) {
			rotator = facingRotator;
		}

		else if (RotatorRegistry.getInstance().has(block.getClass())) {
			rotator = RotatorRegistry.getInstance().get(block.getClass());
		}
		
		if (rotator == null) {
			return blockState;
		}
		int rotatedMeta = 0;
		rotatedMeta = rotator.rotate(blockState, rotate);

		// update the rotated block with the meta
		IBlockState rotatedBlockState = block.getStateFromMeta(rotatedMeta);
		
		return rotatedBlockState;
	}
}
