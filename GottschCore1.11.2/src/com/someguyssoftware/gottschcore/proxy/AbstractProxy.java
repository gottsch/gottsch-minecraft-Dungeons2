/**
 * 
 */
package com.someguyssoftware.gottschcore.proxy;

/**
 * @author Mark Gottschling on May 14, 2017
 *
 */
public abstract class AbstractProxy implements IProxy {

	/**
	 * 
	 */
	@Override
	public void doRegistrations() {
		registerBlocks();
		registerItems();
		registerTileEntities();
		registerRenderers();
	}
}
