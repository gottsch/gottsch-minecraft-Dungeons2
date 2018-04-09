/**
 * 
 */
package com.someguyssoftware.gottschcore.block;


import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * 
 * @author Mark Gottschling on Aug 5, 2016
 *
 */
public class RelativeDirectionFacadeBlock extends ModBlock {
	public static final PropertyEnum<EnumFacing> BASE = PropertyDirection.create("base", EnumFacing.class);

	// the different bounding boxes for different facing directions
	protected AxisAlignedBB NORTH_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    protected AxisAlignedBB SOUTH_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    protected AxisAlignedBB EAST_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    protected AxisAlignedBB WEST_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    protected AxisAlignedBB UP_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    protected AxisAlignedBB DOWN_AABB = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    
    /**
     * 
     * @param modID
     * @param name
     * @param material
     */
	public RelativeDirectionFacadeBlock(String modID, String name, Material material) {
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
	
    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
    
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
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
    	return state.getValue(BASE).getIndex();
    }

	/**
	 * 
	 */
	@Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		switch (state.getValue(BASE)) {
		// TODO need UP and DOWN
		    case NORTH:
		        return NORTH_AABB;
		    case SOUTH:
		        return SOUTH_AABB;
		    case WEST:
		        return WEST_AABB;
		    case EAST:
		    	return EAST_AABB;
		    case UP:
		    	return UP_AABB;
		    case DOWN:
		    	return DOWN_AABB;
		    default:
		    	return NORTH_AABB;
		}
	}
	
	   /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed
     * blockstate.
     */
	@Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(BASE, rot.rotate(state.getValue(BASE)));
    }
    
    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed
     * blockstate.
     */
    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
        return state.withRotation(mirrorIn.toRotation(state.getValue(BASE)));
    }
    
	
    /**
     * 
     * @param n
     * @param e
     * @param s
     * @param w
     */
    public void setBoundingBox(AxisAlignedBB n, AxisAlignedBB e, AxisAlignedBB s, AxisAlignedBB w) {
    	this.NORTH_AABB = n;
    	this.EAST_AABB = e;
    	this.SOUTH_AABB = s;
    	this.WEST_AABB = w;
    }
    
    /**
     * 
     * @param n
     * @param e
     * @param w
     * @param s
     * @param u
     * @param d
     */
	public void setBoundingBox(AxisAlignedBB n, AxisAlignedBB e, AxisAlignedBB s, AxisAlignedBB w, AxisAlignedBB u, AxisAlignedBB d) {
    	this.NORTH_AABB = n;
    	this.EAST_AABB = e;
    	this.SOUTH_AABB = s;
    	this.WEST_AABB = w;
    	this.UP_AABB = u;
    	this.DOWN_AABB = d;		
	}
}
