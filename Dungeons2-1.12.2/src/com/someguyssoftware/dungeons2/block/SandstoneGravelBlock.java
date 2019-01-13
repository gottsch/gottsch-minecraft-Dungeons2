/**
 * 
 */
package com.someguyssoftware.dungeons2.block;

import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.block.ModFallingBlock;

import net.minecraft.block.Block;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 
 * @author Mark Gottschling on Feb 27, 2018
 *
 */
public class SandstoneGravelBlock extends ModFallingBlock {

	/**
	 * 
	 * @param modID
	 * @param name
	 * @param material
	 */
	public SandstoneGravelBlock(String modID, String name, Material material) {
		super(modID, name, material);
		setNormalCube(true);
		setSoundType(SoundType.STONE);
		setCreativeTab(Dungeons2.DUNGEONS_TAB);
	}
	
    /**
     * Get the MapColor for this Block and the given BlockState
     */
    public MapColor getMapColor(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return MapColor.STONE;
    }

	/**
	 * Called when a neighboring block was changed and marks that this state should perform any checks during a neighbor
	 * change. Cases may include when redstone power is updated, cactus blocks popping off due to a neighboring solid
	 * block, etc.
	 */
//	@Override
//	public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
//		//        worldIn.scheduleUpdate(pos, this, this.tickRate(worldIn));
//		// check if block should fall
//		this.checkFallable(worldIn, pos);
//	}


    /**
     * Determines if an entity can path through this block
     */
	@Override
    public boolean isPassable(IBlockAccess worldIn, BlockPos pos) {
        return false;
    }
    
	/**
	 * 
	 * @param state
	 * @return
	 */	
	public static boolean canFallThrough(IBlockState state) {
		Material material = state.getMaterial();
		return material == Material.AIR;
	}

//
//	/**
//	 * 
//	 * @param worldIn
//	 * @param pos
//	 */
//	private void checkFallable(World worldIn, BlockPos pos) {
//		if ((worldIn.isAirBlock(pos.down()) || canFallThrough(worldIn.getBlockState(pos.down()))) && pos.getY() >= 0) {
//			int i = 32;
//
//			if (worldIn.isAreaLoaded(pos.add(-32, -32, -32), pos.add(32, 32, 32))) {
//				if (!worldIn.isRemote) {
//					EntityFallingBlock entityfallingblock = new EntityFallingBlock(worldIn, (double) pos.getX() + 0.5D,
//							(double) pos.getY(), (double) pos.getZ() + 0.5D, worldIn.getBlockState(pos).withProperty(CHECK_DECAY,(Boolean)true).withProperty(ACTIVATED, (Boolean)false));
//					worldIn.spawnEntity(entityfallingblock);
//				}
//			}
//		}
//	}


}
