/**
 * 
 */
package com.someguyssoftware.gottschcore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.someguyssoftware.gottschcore.annotation.Credits;
import com.someguyssoftware.gottschcore.command.ShowVersionCommand;
import com.someguyssoftware.gottschcore.config.GottschCoreConfig;
import com.someguyssoftware.gottschcore.config.IConfig;
import com.someguyssoftware.gottschcore.eventhandler.PlayerFMLEventHandler;
import com.someguyssoftware.gottschcore.mod.AbstractMod;
import com.someguyssoftware.gottschcore.mod.IMod;
import com.someguyssoftware.gottschcore.version.BuildVersion;
import com.someguyssoftware.gottschcore.version.VersionChecker;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

// TODO how to incorporate method references
// TODO how to incorporate lambda expressions and functional interfaces
// TODO how to incorporate default method in interface
// TODO how to incorporate Stream API
// TODO how to incorporate forEach()
// TODO how to incorporate java.util.function.Consumer (function interface)

/**
 * @author Mark Gottschling on Apr 29, 2017
 *
 */
@Mod(
		modid=GottschCore.MODID,
		name=GottschCore.NAME,
		version=GottschCore.VERSION
	)
@Credits(values={"GottschCore for Minecraft 1.11.2 was first developed by Mark Gottschling on Apr 29, 2017."})
public class GottschCore extends AbstractMod {
	// constants
	protected static final String MODID = "gottschcore";
	protected static final String NAME = "GottschCore";
	protected static final String VERSION = "1.0.0";

	// TODO [back-burner]add a message file (messages.json) to check from.... global message and mod specific messages
	
	// the url to check the for the latest release version
	private static final String VERSION_URL = "https://www.dropbox.com/s/tddts75k1ptrn84/gottschcore-versions.json?dl=1";
	// the version of Minecraft that this mod is developed for
	private static final BuildVersion MINECRAFT_VERSION = new BuildVersion(1, 11, 2);
	
	/*
	 * NOTE not used. Mods that used this library should define their own config path.
	 */
	private static final String GOTTSCHCORE_CONFIG_DIR = "gottschcore";
	private static GottschCoreConfig config;
	
	// logger
	public static Logger logger = LogManager.getLogger(GottschCore.NAME);
	
	/**
	 * Required for Forge
	 */
	@Instance(value=GottschCore.MODID)
	public static GottschCore instance;
		
	/**
	 * 
	 */
	public GottschCore() {}
	
	/**
	 * 
	 * @param event
	 */
	@Override
	@EventHandler
	public void preInt(FMLPreInitializationEvent event) {
		super.preInt(event);
		// register additional events
		
		// create and load the config file		
		config = new GottschCoreConfig(this, event.getModConfigurationDirectory(), GOTTSCHCORE_CONFIG_DIR, "general.cfg");
	}
		
    @Override
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
    	super.postInit(event);
	}
    
    @EventHandler
    public void serverStarted(FMLServerStartingEvent event) {
    	event.registerServerCommand(new ShowVersionCommand(this));
     }
	
	@Override
	public String getName() {
		return GottschCore.NAME;
	}

	@Override
	public String getId() {
		return GottschCore.MODID;
	}

	@Override
	public String getVersion() {
		return GottschCore.VERSION;
	}
	
	@Override
	public IMod getInstance() {
		return GottschCore.instance;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.IMod#getConfig()
	 */
	@Override
	public IConfig getConfig() {
		return GottschCore.config;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.IMod#getMinecraftVersion()
	 */
	@Override
	public BuildVersion getMinecraftVersion() {
		return GottschCore.MINECRAFT_VERSION;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.IMod#getVerisionURL()
	 */
	@Override
	public String getVerisionURL() {
		return GottschCore.VERSION_URL;
	}
}
