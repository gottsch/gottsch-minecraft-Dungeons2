/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import com.someguyssoftware.gottschcore.block.CardinalDirectionFacadeBlock;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * @author Mark Gottschling on Aug 31, 2016
 *
 */
public class CorniceFacadeBlock extends CardinalDirectionFacadeBlock {

	// shape property (similar to stairs)
	public static final PropertyEnum<CorniceFacadeBlock.EnumShape> SHAPE = PropertyEnum.create("shape", CorniceFacadeBlock.EnumShape.class);
	
	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public CorniceFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		setBoundingBox(
				new AxisAlignedBB(0F, 0F, 0.1875F, 1F, 1F, 1F),
				new AxisAlignedBB(0F, 0F, 0F, 0.8125F, 1F, 1F),
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 0.8125F),
				new AxisAlignedBB(0.1875F, 0F, 0F, 1F, 1F, 1F)
				);
		this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(SHAPE, EnumShape.STRAIGHT));

	}
	
    /**
     * Checks if a block is cornice
     */
    public static boolean isBlockCornice(Block block) {
        return block instanceof CorniceFacadeBlock;
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
            if (isBlockCornice(block)) {
                enumFacing1 = state1.getValue(FACING);
                // if it is facing north and the block to the south of cornice doesn't have same properties, then this is a outer shape
                if (enumFacing1 == EnumFacing.NORTH && !isSameCornice(world, pos.south(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                // visa versa with south
                else if (enumFacing1 == EnumFacing.SOUTH && !isSameCornice(world, pos.north(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                return shapeState;
            }
            // test the block to the west of the cornice (outer test)
            state2 = world.getBlockState(pos.west());
            block = state2.getBlock();
            if (isBlockCornice(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.NORTH && !isSameCornice(world, pos.north(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            	else if (enumFacing2 == EnumFacing.SOUTH && !isSameCornice(world, pos.south(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            }
        }
        else if (enumFacing == EnumFacing.WEST) {
            state1 = world.getBlockState(pos.west());
            block = state1.getBlock();
            if (isBlockCornice(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.NORTH && !isSameCornice(world, pos.south(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                else if (enumFacing1 == EnumFacing.SOUTH && !isSameCornice(world, pos.north(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                return shapeState;
            }
            state2 = world.getBlockState(pos.east());
            block = state2.getBlock();
            if (isBlockCornice(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.NORTH && !isSameCornice(world, pos.north(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            	else if (enumFacing2 == EnumFacing.SOUTH && !isSameCornice(world, pos.south(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            }
        }
        else if (enumFacing == EnumFacing.SOUTH) {
            state1 = world.getBlockState(pos.south());
            block = state1.getBlock();

            if (isBlockCornice(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.WEST && !isSameCornice(world, pos.east(), state)) {
                    shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                else if (enumFacing1 == EnumFacing.EAST && !isSameCornice(world, pos.west(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                return shapeState;
            }

            state2 = world.getBlockState(pos.north());
            block = state2.getBlock();
            if (isBlockCornice(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.WEST && !isSameCornice(world, pos.west(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            	else if (enumFacing2 == EnumFacing.EAST && !isSameCornice(world, pos.east(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            }
        }
        else if (enumFacing == EnumFacing.NORTH) {
            state1 = world.getBlockState(pos.north());
            block = state1.getBlock();

            if (isBlockCornice(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.WEST && !isSameCornice(world, pos.east(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                else if (enumFacing1 == EnumFacing.EAST && !isSameCornice(world, pos.west(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                return shapeState;      
            }
            
            state2 = world.getBlockState(pos.south());
            block = state2.getBlock();
            if (isBlockCornice(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.WEST && !isSameCornice(world, pos.west(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            	else if (enumFacing2 == EnumFacing.EAST && !isSameCornice(world, pos.east(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            }
        }
        return shapeState;
    }
	
    /**
     * Check whether there is a cornice block at the given position and it has the same properties as the given BlockState
     */
    public static boolean isSameCornice(IBlockAccess worldIn, BlockPos pos, IBlockState stateIn)
    {
        IBlockState atPosState = worldIn.getBlockState(pos);
        Block block = atPosState.getBlock();
        /**
         * Checks if a block is stairs
         */
        return isBlockCornice(block) && atPosState.getValue(FACING) == stateIn.getValue(FACING);
    }

	/**
	 * 
	 */
	@Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {FACING, SHAPE});
    }
 
	/**
	 * 
	 * @author Mark Gottschling on Nov 16, 2015
	 *
	 */
    public static enum EnumShape implements IStringSerializable
    {
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
}
