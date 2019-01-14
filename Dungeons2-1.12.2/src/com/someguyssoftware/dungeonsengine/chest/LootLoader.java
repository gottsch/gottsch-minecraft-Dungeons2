/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeonsengine.chest.ILootLoader;
import com.someguyssoftware.dungeonsengine.config.IChestConfig;
import com.someguyssoftware.dungeonsengine.config.LootTableMethod;
import com.someguyssoftware.gottschcore.enums.Rarity;
import com.someguyssoftware.gottschcore.loot.LootTable;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Jan 8, 2019
 *
 */
public class StandardLootLoader implements ILootLoader {

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

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.chest.ILootLoader#populate(net.minecraft.world.World, java.util.Random, com.someguyssoftware.gottschcore.positional.ICoords, com.someguyssoftware.dungeonsengine.config.IChestConfig)
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
					// select a random rarity from the config
					Rarity rarity = config.getRarity().get(random.nextInt(config.getRarity().size()-1));
					List<LootTable> lootTables = Dungeons2.LOOT_TABLES.getLootTableByRarity(rarity);
					if (lootTables != null) {
						Dungeons2.log.debug("found loot tables -> {}", lootTables.size());
						int index = RandomHelper.randomInt(random, 0, lootTables.size()-1);
						LootTable table = lootTables.get(index);
						table.fillInventory((TileEntityChest)te, random, Dungeons2.LOOT_TABLES.getContext());
					}
					else
						Dungeons2.log.debug("did not find any loot tables by rarity -> {}", rarity);
				}
			}
		}
		else {
			Dungeons2.log.warn(String.format("TileEntity is not an instance of TileEntityChest @ %s", coords.toShortString()));
		}
	}

}
