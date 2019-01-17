package com.someguyssoftware.dungeonsengine.chest;

import java.util.Random;

import com.someguyssoftware.dungeonsengine.config.IChestConfig;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 
 * @author Mark Gottschling on Jan 8, 2019
 *
 */
public interface ILootLoader {

	/**
	 * 
	 * @param world
	 * @param inventory
	 * @param config
	 * @param random
	 */
	void fill(World world, IInventory inventory, IChestConfig config, Random random);

	/**
	 * 
	 * @param world
	 * @param random
	 * @param entity
	 * @param config
	 */
	void fill(World world, Random random, TileEntityChest entity, IChestConfig config);

	/**
	 * 
	 * @param world
	 * @param random
	 * @param coords
	 * @param config
	 */
	void fill(World world, Random random, ICoords coords, IChestConfig config);

}