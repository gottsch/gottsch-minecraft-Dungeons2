/**
 * 
 */
package com.someguyssoftware.dungeonsengine.chest;

import java.util.Random;

import com.someguyssoftware.dungeonsengine.config.IChestConfig;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Jan 8, 2019
 *
 */
public class Populator implements IPopulator {

	/*
	 * NOTE what should the populator contain? a link to the loot tables
	 * Currently, Treasure and Dungeons only use Rarity to determine which loot tables to get-
	 * how can we can that more generic?
	 */
	/**
	 * 
	 */
	public Populator() {
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.chest.IPopulator#populate(net.minecraft.inventory.IInventory, com.someguyssoftware.dungeonsengine.config.IChestConfig)
	 */
	@Override
	public void populate(IInventory inventory, IChestConfig config) {
		
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.chest.IPopulator#populate(net.minecraft.tileentity.TileEntityChest, com.someguyssoftware.dungeonsengine.config.IChestConfig)
	 */
	@Override
	public void populate(TileEntityChest entity, IChestConfig config) {
		
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.chest.IPopulator#populate(net.minecraft.world.World, net.minecraft.util.math.BlockPos, com.someguyssoftware.dungeonsengine.config.IChestConfig)
	 */
	@Override
	public void populate(World world, Random random, ICoords coords, IChestConfig config) {
		
	}
}
