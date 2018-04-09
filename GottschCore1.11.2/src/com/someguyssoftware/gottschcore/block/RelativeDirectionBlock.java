/**
 * 
 */
package com.someguyssoftware.gottschcore.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 
 * @author Mark Gottschling on Aug 5, 2016
 *
 */
public class RelativeDirectionBlock extends ModBlock {

	public static final PropertyEnum<EnumFacing> BASE = PropertyDirection.create("base", EnumFacing.class);
	
	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public RelativeDirectionBlock(String modID, String name, Material material) {
		super(modID, name, material);
 		this.setDefaultState(blockState.getBaseState().withProperty(BASE, EnumFacing.UP));
	}

	/**
	 * 
	 */
	@Override
    protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, new IProperty[] {BASE});
    }

	/**
	 * 
	 */
	@Override
	public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY,
			float hitZ, int meta, EntityLivingBase placer) {
		IBlockState blockState = super.getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, meta, placer);
        blockState = blockState.withProperty(BASE, facing);
    	
        return blockState;
	}
	
	/**
	 * 
	 */
	@Override
	public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY,
			float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
		IBlockState state = super.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, meta, placer, hand);
        state = state.withProperty(BASE, facing);
        return state;
	}
	
    /**
     * Convert the given metadata into a BlockState for this Block
     */
	@Override
    public IBlockState getStateFromMeta(int meta) {
		IBlockState blockState = this.getDefaultState().withProperty(BASE, EnumFacing.getFront(meta));
		return  blockState;
    }

    /**
     * Convert the BlockState into the correct metadata value
     */
    @Override
    public int getMetaFromState(IBlockState state) {
    	return ((EnumFacing)state.getValue(BASE)).getIndex();
    }
}
