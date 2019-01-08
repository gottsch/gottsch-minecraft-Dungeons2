package com.someguyssoftware.dungeonsengine.chest;

import java.util.Random;

import com.someguyssoftware.dungeonsengine.config.IChestConfig;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface IPopulator {

	void populate(IInventory inventory, IChestConfig config);

	void populate(TileEntityChest entity, IChestConfig config);

	void populate(World world, Random random, ICoords coords, IChestConfig config);

}