/**
 * 
 */
package com.someguyssoftware.gottschcore.block;

import java.util.Random;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A full-sized (meaning, the bounding box is full-sized) block that has direction and is always facing the player when placed, or north by default.
 * @author Mark Gottschling on Aug 31, 2016
 *
 */
public class CardinalDirectionBlock extends ModBlock {

	public static final PropertyEnum<EnumFacing> FACING = PropertyDirection.create("facing", EnumFacing.class);

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param materialIn
	 */
	public CardinalDirectionBlock(String modID, String name, Material materialIn) {
		super(modID, name, materialIn);
		this.setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
	}

	/**
	 * 
	 */
	@Override
	public Item getItemDropped(IBlockState state, Random rand, int fortune) {
		return super.getItemDropped(state, rand, fortune);
	}
	
    @Override
    public boolean isFullCube(IBlockState state) {
        return true;
    }
    
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }
    
    /*
     * FUTURE
     * add a member property "isCutout" which can be set in constructor or setter.
     * this will be checked in getBlockLayer()
     * @see net.minecraft.block.Block#getBlockLayer()
     */
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }

	@Override
	public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY,
			float hitZ, int meta, EntityLivingBase placer) {
        return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
	}
	
	/*
	 * NOTE
	 * for some reason use the EnumFacing.getHortizontal and EnumFacing.getHoriztonalIndex() in my
	 * custom block would not allow the block to be registered in 1.9.4.
	 */
	
	/**
	 * 
	 */
	@Override
    protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, new IProperty[] {FACING});
    }
	
    /**
     * Convert the given metadata into a BlockState for this Block
     */
	@Override
    public IBlockState getStateFromMeta(int meta) {
      EnumFacing enumFacing = EnumFacing.getFront(meta);

      if (enumFacing.getAxis() == EnumFacing.Axis.Y) {
          enumFacing = EnumFacing.NORTH;
      }
      return this.getDefaultState().withProperty(FACING, enumFacing);
    }

    /**
     * Convert the BlockState into the correct metadata value
     */
    @Override
    public int getMetaFromState(IBlockState state) {
    	return ((EnumFacing)state.getValue(FACING)).getIndex();
    }
}
