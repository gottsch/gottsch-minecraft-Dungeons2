/**
 * 
 */
package com.someguyssoftware.dungeons2.proxy;

import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.gottschcore.config.IConfig;
import com.someguyssoftware.gottschcore.proxy.AbstractClientProxy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;

/**
 * TODO remove rendering and move to another class that is registered by events
 * @author Mark Gottschling on Jul 15, 2017
 *
 */
public class ClientProxy extends AbstractClientProxy {

	@Override
	public void registerRenderers(IConfig config) {
		registerItemRenderers((ModConfig) config);
	}
	
	/**
	 * 
	 * @param config
	 */
	public void registerItemRenderers(ModConfig config) {
	}
}
