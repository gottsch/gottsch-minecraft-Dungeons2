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
 * 
 * @author Mark Gottschling on Sep 2, 2016
 *
 */
public class CrownMoldingFacadeBlock extends CardinalDirectionFacadeBlock {

	// shape property (similar to stairs)
	public static final PropertyEnum<CrownMoldingFacadeBlock.EnumShape> SHAPE = PropertyEnum.create("shape", CrownMoldingFacadeBlock.EnumShape.class);
	
	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public CrownMoldingFacadeBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setSoundType(SoundType.STONE);
		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		setBoundingBox(
				new AxisAlignedBB(0F, 0F, 0.625F, 1F, 1F, 1F),	// N
				new AxisAlignedBB(0F, 0F, 0F, 0.375F, 1F, 1F),	// E
				new AxisAlignedBB(0F, 0F, 0F, 1F, 1F, 0.375F),	// S
				new AxisAlignedBB(0.625F, 0F, 0F, 1F, 1F, 1F)	// W
				);
		this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(SHAPE, EnumShape.STRAIGHT));
	}
	
	/**
	 * 
	 */
	@Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, new IProperty[] {FACING, SHAPE});
    } 
	
    /**
     * Checks if a block is cornice
     */
    public static boolean isBlockCrownMolding(Block block) {
        return block instanceof CrownMoldingFacadeBlock;
    }
    
    /**
     * Check whether there is a cornice block at the given position and it has the same properties as the given BlockState
     */
    public static boolean isSameCrownMolding(IBlockAccess worldIn, BlockPos pos, IBlockState stateIn)
    {
        IBlockState atPosState = worldIn.getBlockState(pos);
        Block block = atPosState.getBlock();
        /**
         * Checks if a block is stairs
         */
        return isBlockCrownMolding(block) && atPosState.getValue(FACING) == stateIn.getValue(FACING);
    }

	/**
	 * 
	 */
	@Override
	// TODO put getShape code in onPlaced to set the state.  Implement getMetaFromState and getStateFrom meta to include the SHAPE property
	public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {		
		return getShape(state, world, pos);
	}


	/**
	 * 
	 * @param world
	 * @param pos
	 * @return
	 */
	public IBlockState getShape(IBlockState state, IBlockAccess world, BlockPos pos) {
        //IBlockState state = world.getBlockState(pos);
        EnumFacing enumFacing = state.getValue(FACING);

       // boolean isInnerFlag = true;
        IBlockState shapeState = state.withProperty(SHAPE, EnumShape.STRAIGHT);
        
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

            // if the other block is a crown block as well
            if (isBlockCrownMolding(block)) {
                enumFacing1 = state1.getValue(FACING);
                // if it is facing north and the block to the south of crown doesn't have same properties, then this is a inner shape
                if (enumFacing1 == EnumFacing.NORTH && !isSameCrownMolding(world, pos.south(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);                	
                }
                // visa versa with south
                else if (enumFacing1 == EnumFacing.SOUTH && !isSameCrownMolding(world, pos.north(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                return shapeState;
            }
            // test the block to the west of the cornice (outer test)
            state2 = world.getBlockState(pos.west());
            block = state2.getBlock();
            if (isBlockCrownMolding(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.NORTH && !isSameCrownMolding(world, pos.north(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);            		
            	}
            	else if (enumFacing2 == EnumFacing.SOUTH && !isSameCrownMolding(world, pos.south(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            }
        }
        else if (enumFacing == EnumFacing.WEST) {
            state1 = world.getBlockState(pos.west());
            block = state1.getBlock();
            if (isBlockCrownMolding(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.NORTH && !isSameCrownMolding(world, pos.south(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                else if (enumFacing1 == EnumFacing.SOUTH && !isSameCrownMolding(world, pos.north(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }      
                return shapeState;
            }
            state2 = world.getBlockState(pos.east());
            block = state2.getBlock();
            if (isBlockCrownMolding(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.NORTH && !isSameCrownMolding(world, pos.north(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
            	else if (enumFacing2 == EnumFacing.SOUTH && !isSameCrownMolding(world, pos.south(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
            }
        }
        else if (enumFacing == EnumFacing.SOUTH) {
            state1 = world.getBlockState(pos.south());
            block = state1.getBlock();

            if (isBlockCrownMolding(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.WEST && !isSameCrownMolding(world, pos.east(), state)) {
                    shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                else if (enumFacing1 == EnumFacing.EAST && !isSameCrownMolding(world, pos.west(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                return shapeState;
            }

            state2 = world.getBlockState(pos.north());
            block = state2.getBlock();
            if (isBlockCrownMolding(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.WEST && !isSameCrownMolding(world, pos.west(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);    
            	}
            	else if (enumFacing2 == EnumFacing.EAST && !isSameCrownMolding(world, pos.east(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT);
            	}
             }
        }
        else if (enumFacing == EnumFacing.NORTH) {
            state1 = world.getBlockState(pos.north());
            block = state1.getBlock();

            if (isBlockCrownMolding(block)) {
                enumFacing1 = state1.getValue(FACING);
                if (enumFacing1 == EnumFacing.WEST && !isSameCrownMolding(world, pos.east(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_LEFT);
                }
                else if (enumFacing1 == EnumFacing.EAST && !isSameCrownMolding(world, pos.west(), state)) {
                	shapeState = state.withProperty(SHAPE, EnumShape.INNER_RIGHT);
                }
                return shapeState;      
            }
            
            state2 = world.getBlockState(pos.south());
            block = state2.getBlock();
            if (isBlockCrownMolding(block)) {
            	enumFacing2 = state2.getValue(FACING);
            	if (enumFacing2 == EnumFacing.WEST && !isSameCrownMolding(world, pos.west(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_RIGHT); 
            	}
            	else if (enumFacing2 == EnumFacing.EAST && !isSameCrownMolding(world, pos.east(), state)) {
            		shapeState = state.withProperty(SHAPE, EnumShape.OUTER_LEFT);
            	}
             }
        }

        // return the state
        return shapeState;
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
