/**
 * 
 */
package com.someguyssoftware.gottschcore.mod;

import com.someguyssoftware.gottschcore.config.IConfig;
import com.someguyssoftware.gottschcore.version.BuildVersion;

/**
 * @author Mark Gottschling on Jan 5, 2016
 *
 */
public interface IMod {
	/**
	 * 
	 * @return
	 */
	public IConfig getConfig();
	
	/**
	 * Returns the latest published version of the mod.
	 * @return
	 */
	public BuildVersion getModLatestVersion();
	
	/**
	 * Set the latest published verison of the mod.
	 * @param version
	 */
	public void setModLatestVersion(BuildVersion version);
	
	/**
	 * 
	 */
	public BuildVersion getMinecraftVersion();
	
	public String getVerisionURL();
	
	/**
	 * Get the instance of the mod
	 * @return
	 */
	public IMod getInstance();
	
	/**
	 * Get the name of the mod.
	 * @return
	 */
	public String getName();
	
	/**
	 * Get the name of the mod.
	 * @return
	 */
	public String getId();
	
	/**
	 * The the current mod version.
	 * @return
	 */
	public String getVersion();
	
}
