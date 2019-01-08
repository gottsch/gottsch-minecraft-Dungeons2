/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.block.CardinalDirectionFacadeBlock;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Aug 31, 2016
 *
 */
public class BasicFacadeBlock extends CardinalDirectionFacadeBlock {

	// shape property (similar to stairs)
	public static final PropertyEnum<BasicFacadeBlock.EnumShape> SHAPE = PropertyEnum.create("shape", BasicFacadeBlock.EnumShape.class);

	/*
	 * An array of AxisAlignedBB bounds for the bounding box.
	 * Replaces the default bounding boxes as defined in CardinalDirectionFacadeBlock.
	 */
	AxisAlignedBB[] bounds = //new AxisAlignedBB[4];
		{
			// straight
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 0.5F),	// S
			new AxisAlignedBB(0.5F, 0F, 0F, 1F, 1F, 1F),	// W
			new AxisAlignedBB(0F, 0F, 0.5F, 1F, 1F, 1F),	// N (starts at half)
			new AxisAlignedBB(0F, 0F, 0F, 0.5F, 1F, 1F),	// E (starts at zero)
			
			// inner left
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// S
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// W
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// N
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// E
						
			// inner right
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// S
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// W
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// N
			new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 1F),	// E
			
			// outer left
			new AxisAlignedBB(0.5F, 0F, 0F, 1F, 1F, 0.5F),	// S
			new AxisAlignedBB(0.5F, 0F, 0.5F, 1F, 1F, 1F),	// W
			new AxisAlignedBB(0F, 0F, 0.5F, 0.5F, 1F, 1F),	// N (starts at half)
			new AxisAlignedBB(0F, 0F, 0F, 0.5F, 1F, 0.5F),	// E (starts at zero)		
			
			// outer right
			new AxisAlignedBB(0F, 0F, 0F, 0.5F, 1F, 0.5F),	// S
			new AxisAlignedBB(0.5F, 0F, 0F, 1F, 1F, 0.5F),	// W
			new AxisAlignedBB(0.5F, 0F, 0.5F, 1F, 1F, 1F),	// N (starts at half)
			new AxisAlignedBB(0F, 0F, 0.5F, 0.5F, 1F, 1F)	// E (starts at zero)				
		};
	
	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public BasicFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(Dungeons2.DUNGEONS_TAB);
//		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
//		setBoundingBox(
//				new AxisAlignedBB(0F, 0F, 0.5F, 1F, 1F, 1F), 	// N (starts at half)
//				new AxisAlignedBB(0F, 0F, 0F, 0.5F, 1F, 1F),  	// E (starts at zero)
//				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 0.5F),  	// S
//				new AxisAlignedBB(0.5F, 0F, 0F, 1F, 1F, 1F)	// W
//				);
		
		this.setDefaultState(this.blockState.getBaseState()
				.withProperty(FACING, EnumFacing.NORTH)
				.withProperty(SHAPE, EnumShape.STRAIGHT));
	}
	
	@Override
	public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
		return false;
	}
	
    /**
     * Checks if a block is basic
     */
    public static boolean isBlockBasic(Block block) {
        return block instanceof BasicFacadeBlock;
    }
    
    /**
     * Check whether there is a cornice block at the given position and it has the same properties as the given BlockState
     */
    public static boolean isSameBasic(IBlockAccess worldIn, BlockPos pos, IBlockState stateIn) {
        IBlockState atPosState = worldIn.getBlockState(pos);
        Block block = atPosState.getBlock();
        /**
         * Checks if a block is BasicFacadeBlock
         */
        return isBlockBasic(block) && atPosState.getValue(FACING) == stateIn.getValue(FACING);
    }
    
	/**
	 * 
	 */
	@Override
	public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
		return getShape(world, pos);
	}
	
	/**
	 * 
	 * @param world
	 * @param pos
	 * @return
	 */
	public IBlockState getShape(IBlockAccess world, BlockPos pos) {
		IBlockState state = world.getBlockState(pos);
		return getShape(world, state, pos);
	}
	
	/**
	 * 
	 * @param world
	 * @param pos
	 * @return
	 */
	public IBlockState getShape(IBlockAccess world, IBlockState state, BlockPos pos) {
//        IBlockState state = world.getBlockState(pos);
        EnumFacing enumFacing = state.getValue(FACING);

       // boolean isInnerFlag = true;
        IBlockState shapeState = state.withProperty(SHAPE, EnumShape.STRAIGHT);
        
        // TODO update BlockBouns to full cube on XZ plane
        
        IBlockState state1;
        IBlockState state2;
        Block block;
        EnumFacing enumFacing1;
        EnumFacing enumFacing2;

        // test the direction the cornice block is facing
        if (enumFacing == EnumFacing.EAST) {
        	// get one block to the east of the cornice block
            state1 = world.getBlockState(pos.east());
            block = state1.getBlock();

            // if the other block is a cornice block as well
            if (isBlockBasic(block)) {
                enumFacing1 = state1.getValue(FACING);
                // if it is facing north and the block to the south of cornice doesn't have same properties, then this is a outer shape
                if (enumFacing1 == EnumFacing.NORTH && !isSameBasic(world, pos.south(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                // visa versa with south
                else if (enumFacing1 == EnumFacing.SOUTH && !isSameBasic(world, pos.north(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                return shapeState;
            }
            // test the block to the west of the cornice (outer test)
            state2 = world.getBlockState(pos.west());
            block = state2.getBlock();
            if (isBlockBasic(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.NORTH && !isSameBasic(world, pos.north(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            	else if (enumFacing2 == EnumFacing.SOUTH && !isSameBasic(world, pos.south(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            }
        }
        else if (enumFacing == EnumFacing.WEST) {
            state1 = world.getBlockState(pos.west());
            block = state1.getBlock();
            if (isBlockBasic(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.NORTH && !isSameBasic(world, pos.south(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                else if (enumFacing1 == EnumFacing.SOUTH && !isSameBasic(world, pos.north(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                return shapeState;
            }
            state2 = world.getBlockState(pos.east());
            block = state2.getBlock();
            if (isBlockBasic(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.NORTH && !isSameBasic(world, pos.north(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            	else if (enumFacing2 == EnumFacing.SOUTH && !isSameBasic(world, pos.south(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            }
        }
        else if (enumFacing == EnumFacing.SOUTH) {
            state1 = world.getBlockState(pos.south());
            block = state1.getBlock();

            if (isBlockBasic(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.WEST && !isSameBasic(world, pos.east(), state)) {
                    shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                else if (enumFacing1 == EnumFacing.EAST && !isSameBasic(world, pos.west(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                return shapeState;
            }

            state2 = world.getBlockState(pos.north());
            block = state2.getBlock();
            if (isBlockBasic(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.WEST && !isSameBasic(world, pos.west(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            	else if (enumFacing2 == EnumFacing.EAST && !isSameBasic(world, pos.east(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            }
        }
        else if (enumFacing == EnumFacing.NORTH) {
            state1 = world.getBlockState(pos.north());
            block = state1.getBlock();

            if (isBlockBasic(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.WEST && !isSameBasic(world, pos.east(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                else if (enumFacing1 == EnumFacing.EAST && !isSameBasic(world, pos.west(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                return shapeState;      
            }
            
            state2 = world.getBlockState(pos.south());
            block = state2.getBlock();
            if (isBlockBasic(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.WEST && !isSameBasic(world, pos.west(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            	else if (enumFacing2 == EnumFacing.EAST && !isSameBasic(world, pos.east(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            }
        }
        return shapeState;
    }
	
	/**
	 * 
	 */
	@Override
	public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
		int facingIndex = 0;
		int shapeIndex = 0;
		
		IBlockState actualState = getShape(source, state, pos);
		
		switch(actualState.getValue(FACING)) {
		case NORTH:
			facingIndex = EnumFacing.NORTH.getHorizontalIndex();
			break;
		case SOUTH:
			facingIndex = EnumFacing.SOUTH.getHorizontalIndex();
			break;
		case EAST:
			facingIndex = EnumFacing.EAST.getHorizontalIndex();
			break;
		case WEST:
			facingIndex = EnumFacing.WEST.getHorizontalIndex();
			break;
		default:
			facingIndex = EnumFacing.NORTH.getHorizontalIndex();			
			break;
		}

		switch(actualState.getValue(SHAPE)) {
		case STRAIGHT:
			shapeIndex = 0;
			break;
		case INNER_LEFT:
			shapeIndex = 4;
			break;			
		case INNER_RIGHT:
			shapeIndex = 8;
			break;
		case OUTER_LEFT:
			shapeIndex = 12;
			break;
		case OUTER_RIGHT:
			shapeIndex = 16;
			break;			
		default:
			shapeIndex = 0;
			break;
		}		
		return bounds[facingIndex + shapeIndex];
	}
	
//    public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState) {
//        if (!isActualState)
//        {
//            state = this.getActualState(state, worldIn, pos);
//        }
//
//        for (AxisAlignedBB axisalignedbb : getCollisionBoxList(state))
//        {
//            addCollisionBoxToList(pos, entityBox, collidingBoxes, axisalignedbb);
//        }
//    }
    
//    private static List<AxisAlignedBB> getCollisionBoxList(IBlockState bstate)
//    {
//        List<AxisAlignedBB> list = Lists.<AxisAlignedBB>newArrayList();
//        boolean flag = bstate.getValue(HALF) == BlockStairs.EnumHalf.TOP;
//        list.add(flag ? AABB_SLAB_TOP : AABB_SLAB_BOTTOM);
//        BlockStairs.EnumShape blockstairs$enumshape = (BlockStairs.EnumShape)bstate.getValue(SHAPE);
//
//        if (blockstairs$enumshape == BlockStairs.EnumShape.STRAIGHT || blockstairs$enumshape == BlockStairs.EnumShape.INNER_LEFT || blockstairs$enumshape == BlockStairs.EnumShape.INNER_RIGHT)
//        {
//            list.add(getCollQuarterBlock(bstate));
//        }
//
//        if (blockstairs$enumshape != BlockStairs.EnumShape.STRAIGHT)
//        {
//            list.add(getCollEighthBlock(bstate));
//        }
//
//        return list;
//    }
    
	/**
	 * 
	 */
	@Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {FACING, SHAPE});
    }
	
	/**
	 * 
	 * @author Mark Gottschling on Jan 7, 2019
	 *
	 */
    public static enum EnumShape implements IStringSerializable {
        STRAIGHT("straight"),
        INNER_LEFT("inner_left"),
        INNER_RIGHT("inner_right"),
        OUTER_LEFT("outer_left"),
        OUTER_RIGHT("outer_right");
        private final String name;

        private EnumShape(String name) {
            this.name = name;
        }

        @Override
		public String toString() {
            return this.name;
        }

        @Override
		public String getName() {
            return this.name;
        }
    }

	/**
	 * @return the bounds
	 */
	public AxisAlignedBB[] getBounds() {
		return bounds;
	}

	/**
	 * @param bounds the bounds to set
	 */
	public void setBounds(AxisAlignedBB[] bounds) {
		this.bounds = bounds;
	}
}
