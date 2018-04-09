/**
 * 
 */
package com.someguyssoftware.gottschcore.version;

/**
 * Class represententation of the JSON version file.
 * @author Mark Gottschling on Jan 3, 2016
 *
 */
public class VersionPackage {
	private BuildVersion minecraft;
	private BuildVersion mod;
	
	/**
	 * Empty constructor
	 */
	public VersionPackage() {}

	/**
	 * 
	 * @param minecraft the minecraft version
	 * @param mod the mod version
	 */
	public VersionPackage(BuildVersion minecraft, BuildVersion mod) {
		this.minecraft = minecraft;
		this.mod = mod;
	}
	
	/**
	 * 
	 * @return
	 */
	public BuildVersion getMinecraft() {
		return minecraft;
	}

	/**
	 * 
	 * @param minecraft
	 */
	public void setMinecraft(BuildVersion minecraft) {
		this.minecraft = minecraft;
	}

	/**
	 * 
	 * @return
	 */
	public BuildVersion getMod() {
		return mod;
	}

	/**
	 * 
	 * @param mod
	 */
	public void setMod(BuildVersion mod) {
		this.mod = mod;
	}
}
