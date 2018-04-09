/**
 * 
 */
package com.someguyssoftware.gottschcore.proxy;

import com.someguyssoftware.gottschcore.exception.WrongSideException;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * @author Mark Gottschling on Jul 13, 2017
 *
 */
public abstract class AbstractDedicatedServerProxy implements IProxy {

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.proxy.IProxy#preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent)
	 */
	@Override
	public void preInit(FMLPreInitializationEvent event) {
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.proxy.IProxy#init(net.minecraftforge.fml.common.event.FMLInitializationEvent)
	 */
	@Override
	public void init(FMLInitializationEvent event) {
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.proxy.IProxy#postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent)
	 */
	@Override
	public void postInit(FMLPostInitializationEvent event) {
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.proxy.IProxy#getClientPlayer()
	 */
	@Override
	public EntityPlayer getClientPlayer() {
		throw new WrongSideException("Tried to get the client player on the dedicated server");
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.proxy.IProxy#getClientWorld()
	 */
	@Override
	public World getClientWorld() {
		throw new WrongSideException("Tried to get the client world on the dedicated server");
	}

}
