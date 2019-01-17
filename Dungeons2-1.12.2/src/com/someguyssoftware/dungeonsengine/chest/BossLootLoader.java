/**
 * 
 */
package com.someguyssoftware.dungeonsengine.chest;

import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeonsengine.config.IChestConfig;
import com.someguyssoftware.dungeonsengine.config.LootTableMethod;
import com.someguyssoftware.gottschcore.enums.Rarity;
import com.someguyssoftware.gottschcore.loot.LootTable;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Jan 8, 2019
 *
 */
public class BossLootLoader implements ILootLoader {

	/**
	 * 
	 */
	public BossLootLoader() {
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.chest.ILootLoader#populate(net.minecraft.inventory.IInventory, com.someguyssoftware.dungeonsengine.config.IChestConfig)
	 */
	@Override
	public void fill(World world, IInventory inventory, IChestConfig config, Random random) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.chest.ILootLoader#populate(net.minecraft.tileentity.TileEntityChest, com.someguyssoftware.dungeonsengine.config.IChestConfig)
	 */
	@Override
	public void fill(World world, Random random, TileEntityChest entity, IChestConfig config) {
		// TODO Auto-generated method stub

	}

	// TODO this should be the only method or one that takes an object ...
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.chest.ILootLoader#populate(net.minecraft.world.World, net.minecraft.util.math.BlockPos, com.someguyssoftware.dungeonsengine.config.IChestConfig)
	 */
	@Override
	public void fill(World world, Random random, ICoords coords, IChestConfig config) {
		TileEntity te = world.getTileEntity(coords.toPos());
		if (te == null) {
			Dungeons2.log.warn("Unable to locate Chest TileEntity @: " + coords.toShortString());
			return;
		}
		
		if (te instanceof TileEntityChest) {
			if (config != null) {
				if (config.getLootTableMethod() == LootTableMethod.CUSTOM) {
					List<LootTable> lootTables = Dungeons2.LOOT_TABLES.getLootTableByRarity(Rarity.BOSS);
					if (lootTables != null) {
						Dungeons2.log.debug("found loot tables -> {}", lootTables.size());
						int index = RandomHelper.randomInt(random, 0, lootTables.size()-1);
						LootTable table = lootTables.get(index);
						table.fillInventory((TileEntityChest)te, random, Dungeons2.LOOT_TABLES.getContext());
					}
					else
						Dungeons2.log.debug("did not find any loot tables by rarity -> {}", Rarity.BOSS);
				}
			}
		}
		else {
			Dungeons2.log.warn(String.format("TileEntity is not an instance of TileEntityChest @ %s", coords.toShortString()));
		}
	}

}
