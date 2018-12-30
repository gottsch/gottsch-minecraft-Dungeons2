/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import java.util.Random;

import javax.annotation.Nullable;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.block.CardinalDirectionFacadeBlock;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Mark Gottschling on Oct 12, 2015
 *
 */
public class DungeonsTorchBlock extends CardinalDirectionFacadeBlock {
	
	/**
	 * 
	 * @param modID
	 * @param name
	 */
	public DungeonsTorchBlock(String modID, String name) {
		super(modID, name, Material.CIRCUITS);
		this.setTickRandomly(true);
		setSoundType(SoundType.WOOD);
		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);	
		setBoundingBox(
				new AxisAlignedBB(0.15F, 0.2F, 0.7F, 0.85F, 0.8F, 1F),
				new AxisAlignedBB(0.7F, 0.2F, 0.15F, 1F, 0.8F, 0.85F), 
				new AxisAlignedBB(0.15F, 0.2F, 0F, 0.85F, 0.8F, 0.3F),
				new AxisAlignedBB(0F, 0.2F, 0.15F, 0.3F, 0.8F, 0.85F)
				);		
	}
	
   @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, World worldIn, BlockPos pos) {
        return NULL_AABB;
    }
    
    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(IBlockState state, World worldIn, BlockPos pos, Random rand) {
        EnumFacing enumfacing = (EnumFacing)state.getValue(FACING);
        double d0 = (double)pos.getX() + 0.5D;
        double d1 = (double)pos.getY() + 0.7D;
        double d2 = (double)pos.getZ() + 0.5D;
        double d3 = 0.30D; // y offset
        double d4 = 0.22D; // horizontal offset middle
        double d5 = (double)pos.getX() + 0.25D;
        double d6 = (double)pos.getZ() + 0.25D;
        double d7 = (double)pos.getX() + 0.75D;
        double d8 = (double)pos.getZ() + 0.75D;
        double d9 = 0.27D; // horizontal offset for side torches

        if (enumfacing.getAxis().isHorizontal())
        {
            EnumFacing enumfacing1 = enumfacing.getOpposite();
            // middle
            worldIn.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0 + d4 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d2 + d4* (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
            worldIn.spawnParticle(EnumParticleTypes.FLAME, d0 + d4 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d2 + d4 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
            
            // right
            if (enumfacing1.getFrontOffsetX() != 0) {
	            worldIn.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0 + d9 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d6 + d9 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
	            worldIn.spawnParticle(EnumParticleTypes.FLAME, d0 + d9 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d6 + d9 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
            }
            else {
                worldIn.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d5 + d9 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d2 + d9 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
                worldIn.spawnParticle(EnumParticleTypes.FLAME, d5 + d9 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d2 + d9 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
           }
            
            // left
            if (enumfacing1.getFrontOffsetX() != 0) {
            	worldIn.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d0 + d4 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d8 + d4 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
            	worldIn.spawnParticle(EnumParticleTypes.FLAME, d0 + d4 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d8 + d4 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
            }
            else {
            	worldIn.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, d7 + d4 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d2 + d4 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
            	worldIn.spawnParticle(EnumParticleTypes.FLAME, d7 + d4 * (double)enumfacing1.getFrontOffsetX(), d1 + d3, d2 + d4 * (double)enumfacing1.getFrontOffsetZ(), 0.0D, 0.0D, 0.0D, new int[0]);
            }
        }
    }
    
    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
