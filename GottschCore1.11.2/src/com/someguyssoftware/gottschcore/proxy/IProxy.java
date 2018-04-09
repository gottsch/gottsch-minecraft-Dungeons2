/**
 * 
 */
package com.someguyssoftware.gottschcore.proxy;

import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * @author Mark Gottschling on Jul 9, 2016
 *
 */
public interface IProxy {

	/**
	 * 
	 */
	public void doRegistrations();
	
	public void registerRenderers();
	public void registerBlocks();
	public void registerItems();
	public void registerTileEntities();
	
	/**
	 * Register a Block with the default ItemBlock class.
	 *
	 * @param block The Block instance
	 * @param <T> The Block type
	 * @return The Block instance
	 */
	public default <T extends Block> T registerBlock(T block) {
		return registerBlock(block, ItemBlock::new);
	}

	/**
	 * Register a Block with a custom ItemBlock class.
	 *
	 * @param <T> The Block type
	 * @param block The Block instance
	 * @param itemFactory A function that creates the ItemBlock instance, or null if no ItemBlock should be created
	 * @return The Block instance
	 */
	public default <T extends Block> T registerBlock(T block, @Nullable Function<T, ItemBlock> itemFactory) {
		// register the block
		GameRegistry.register(block);

		if (itemFactory != null) {
			final ItemBlock itemBlock = itemFactory.apply(block);
			GameRegistry.register(itemBlock.setRegistryName(block.getRegistryName()));
		}
		return block;
	}

}
