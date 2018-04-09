/**
 * 
 */
package com.someguyssoftware.gottschcore.proxy;

import javax.annotation.Nullable;

import com.someguyssoftware.gottschcore.config.IConfig;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * 
 * @author Mark Gottschling on Jul 13, 2017
 *
 */
public interface IProxy {

	/**
	 * 
	 * @param event
	 */
	public void preInit(final FMLPreInitializationEvent event);
	
	/**
	 * 
	 * @param event
	 */
	public void init(FMLInitializationEvent event);
	
	/**
	 * 
	 * @param event
	 */
	public void postInit(final FMLPostInitializationEvent event);
	
	/**
	 * Get the client player.
	 *
	 * @return The client player
	 */
	@Nullable
	EntityPlayer getClientPlayer();

	/**
	 * Get the client {@link World}.
	 *
	 * @return The client World
	 */
	@Nullable
	World getClientWorld();

	
	public void registerRenderers(IConfig config);
}
