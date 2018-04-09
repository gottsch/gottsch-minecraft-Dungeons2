/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import com.someguyssoftware.gottschcore.block.RelativeDirectionFacadeBlock;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Mark Gottschling on Sep 19, 2016
 *
 */
public class DecorationBlock extends RelativeDirectionFacadeBlock implements IShearable {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public DecorationBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		this.setDefaultState(blockState.getBaseState().withProperty(BASE, EnumFacing.NORTH));
		setBoundingBox(
				new AxisAlignedBB(0.0D, 0.0D, 1.0D, 1.0D, 1.0D, 1.0D),	// n
				new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 1.0D),	// e
				new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.0D),	// s 
				new AxisAlignedBB(1.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D),	// w 
				new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 1.0D),	// u
				new AxisAlignedBB(0.0D, 1.0D, 0.0D, 1.0D, 1.0D, 1.0D));	// d
		setHardness(0F);
		setResistance(0F);
	}

    @Override
	@Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }
    
	/**
	 * This is required to process alpha channels in block textures
	 */
    @Override
	@SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        
    	if (Minecraft.isFancyGraphicsEnabled()) {
    		return BlockRenderLayer.TRANSLUCENT;
    	}
    	else {
    		return BlockRenderLayer.CUTOUT_MIPPED;
    	}
    }
    
    /**
     * Whether this Block can be replaced directly by other blocks (true for e.g. tall grass)
     */
    @Override
	public boolean isReplaceable(IBlockAccess worldIn, BlockPos pos) {
        return true;
    }
    
    @Override
    public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
    	return false;
    }
    
    /**
     * Check whether this Block can be placed on the given side
     */
    @Override
    public boolean canPlaceBlockOnSide(World worldIn, BlockPos pos, EnumFacing side) {
    	switch (side) {
    	case UP:
    		return this.canAttachDecorationOn(worldIn.getBlockState(pos.down()));
    	case DOWN:
    		return this.canAttachDecorationOn(worldIn.getBlockState(pos.up()));    		
    	case NORTH:
    	case SOUTH:
    	case EAST:
    	case WEST:
    		return this.canAttachDecorationOn(worldIn.getBlockState(pos.offset(side.getOpposite())));
    	default:
    		return false;
    	}
    }

    /**
     * Determines whether you can place a vine block on this kind of block.
     */
    private boolean canAttachDecorationOn(IBlockState blockState) {
        return blockState.isFullCube() && blockState.getMaterial().blocksMovement();
    }

    /**
     * Called when a neighboring block changes.
     */
    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos pos2) {
        EnumFacing enumfacing = state.getValue(BASE);
        if (!this.canPlaceBlockOnSide(worldIn, pos, enumfacing)) {
            this.dropBlockAsItem(worldIn, pos, state, 0);
            worldIn.setBlockToAir(pos);
        }
    }
    
    /**
     * Get the Item that this Block should drop when harvested.
     */
    @Override
	public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return null;
    }

    /**
     * Returns the quantity of items to drop on block destruction.
     */
    @Override
	public int quantityDropped(Random random) {
        return 0;
    }
    
	/* (non-Javadoc)
	 * @see net.minecraftforge.common.IShearable#isShearable(net.minecraft.item.ItemStack, net.minecraft.world.IBlockAccess, net.minecraft.util.math.BlockPos)
	 */
	@Override
	public boolean isShearable(ItemStack item, IBlockAccess world, BlockPos pos) {
		return true;
	}

	/* (non-Javadoc)
	 * @see net.minecraftforge.common.IShearable#onSheared(net.minecraft.item.ItemStack, net.minecraft.world.IBlockAccess, net.minecraft.util.math.BlockPos, int)
	 */
	@Override
	public List<ItemStack> onSheared(ItemStack item, IBlockAccess world, BlockPos pos, int fortune) {
		return java.util.Arrays.asList(new ItemStack(this, 1));
	}
}
