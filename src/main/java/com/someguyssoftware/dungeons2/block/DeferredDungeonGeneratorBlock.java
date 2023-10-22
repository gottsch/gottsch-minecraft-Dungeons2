package com.someguyssoftware.dungeons2.block;

import javax.annotation.Nullable;

import com.someguyssoftware.dungeons2.tileentity.DeferredDungeonGeneratorTileEntity;
import com.someguyssoftware.gottschcore.block.ModBlock;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class DeferredDungeonGeneratorBlock extends ModBlock implements ITileEntityProvider {

	public DeferredDungeonGeneratorBlock(String modID, String name) {
		this(modID, name, Material.AIR);
	}
	
	public DeferredDungeonGeneratorBlock(String modID, String name, Material material) {
		super(modID, name, material);
	}
	
	/**
	 * Create the proximity tile entity.
	 */
	@Override
	public TileEntity createNewTileEntity(World worldIn, int meta) {
		DeferredDungeonGeneratorTileEntity entity = null;
		try {
			entity = new DeferredDungeonGeneratorTileEntity();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return (TileEntity) entity;
	}
	
	/**
	 * 
	 */
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }
    
    @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }
    
    /**
     * Used to determine ambient occlusion and culling when rebuilding chunks for render
     */
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    /**
     * 
     */
    public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid) {
        return false;
    }
    
    /**
     * Spawns this Block's drops into the World as EntityItems.
     */
    public void dropBlockAsItemWithChance(World worldIn, BlockPos pos, IBlockState state, float chance, int fortune) {
    }

    /**
     * Whether this Block can be replaced directly by other blocks (true for e.g. tall grass)
     */
    public boolean isReplaceable(IBlockAccess worldIn, BlockPos pos) {
        return true;
    }

    /**
     * 
     */
    public BlockFaceShape getBlockFaceShape(IBlockAccess worldIn, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }
    
    /**
     * 
     */
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
