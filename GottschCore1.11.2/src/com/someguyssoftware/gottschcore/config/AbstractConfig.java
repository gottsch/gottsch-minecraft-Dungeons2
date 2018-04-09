/**
 * 
 */
package com.someguyssoftware.gottschcore.config;

import java.io.File;

import com.someguyssoftware.gottschcore.mod.IMod;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Mod;

/**
 * A wrapper class around the Forge Configuration file.
 * @author Mark Gottschling on Apr 30, 2017
 *
 */
public abstract class AbstractConfig implements IConfig, ILoggerConfig {
	private IMod mod;
	private Configuration forgeConfiguration;

	// logging
	private String loggerLevel;
	private String loggerFolder;
	private String loggerSize;
	private String loggerFilename;
	
	// toggle mod enabled
	private boolean modEnabled;
	// base mod folder
	private String modFolder;
	
	// toggle to execute version checker
	private boolean enableVersionChecker;
	// latest version that was checked
	private String latestVersion;
	// toggle to display reminder for latest version
	private boolean latestVersionReminder;
	
	/**
	 * 
	 * @param configDir
	 * @param modDir
	 * @param filename
	 */
	public AbstractConfig(IMod mod, File configDir, String modDir, String filename) {
		this.mod = mod;
		// build the path to the minecraft config directory
		String configPath = (new StringBuilder()).append(configDir).append("/").append(modDir).append("/").toString();
		// create the config file
		File configFile = new File((new StringBuilder()).append(configPath).append(filename).toString());
		// load the config file
		Configuration configuration = load(configFile);
		this.forgeConfiguration = configuration;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.gottschcore.config.IConfig#load(java.io.File)
	 */
	@Override
	public Configuration load(File file) {
		Configuration config = IConfig.super.load(file);
		
		String modid = mod.getClass().getAnnotation(Mod.class).modid();
		// setup the basic settings
		config.setCategoryComment("01-logging", "Logging properties.");        
		setLoggerLevel(config.getString("loggerLevel", "01-logging", "info", "The logging level. Set to 'off' to disable logging. [trace|debug|info|warn|error|off]"));
		setLoggerFolder(config.getString("loggerFolder", "01-logging", "mods/" + modid + "/logs/", "The directory where the logs should be stored. This is relative to the Minecraft install path."));
		setLoggerSize(config.getString("loggerSize", "01-logging", "1000K", "The size a log file can be before rolling over to a new file."));
		setLoggerFilename(config.getString("loggerFilename", "01-logging", modid, "The filename of the  log file."));
        
		config.setCategoryComment("03-mod", "General mod properties.");
		setModFolder(config.getString("modFolder", "01-logging", "mods/" + modid + "/", "Where default mod folder is located."));

        // create/get properties into wrapped properties
        setEnableVersionChecker(config.getBoolean("enableVersionChecker", "03-mod", true, "Enables/Disables version checking."));
        setLatestVersion(config.getString("latestVersion", "03-mod", "", "The latest published version number."));
        setLatestVersionReminder(config.getBoolean("latestVersionReminder", "03-mod", true, "Remind the user of the latest version (as indicated in latestVersion proeprty) update."));

		return config;
	}
	
	// TODO create interface method in IConfig
	public void setProperty(Property property, String value) {
		property.set(value);
		getForgeConfiguration().save();
	}
	
	@Override
	public void setProperty(String category, String key, String value) {
		Property property = getForgeConfiguration().get(category, key, value);
		if (!property.getString().equals(value)) {
			property.set(value);
		}
		getForgeConfiguration().save();
	}
	
	@Override
	public void setProperty(String category, String key, boolean value) {
		Property property = getForgeConfiguration().get(category, key, value);
		if (!property.getBoolean() != value) {
			property.set(value);
		}
		getForgeConfiguration().save();
	}
	
	/**
	 * @return the enableVersionChecker
	 */
	@Override
	public boolean isEnableVersionChecker() {
		return enableVersionChecker;
	}

	/**
	 * @param enableVersionChecker the enableVersionChecker to set
	 */
	@Override
	public void setEnableVersionChecker(boolean enableVersionChecker) {
		this.enableVersionChecker = enableVersionChecker;
	}

	/**
	 * @return the latestVersion
	 */
	@Override
	public String getLatestVersion() {
		return latestVersion;
	}

	/**
	 * @param latestVersion the latestVersion to set
	 */
	@Override
	public void setLatestVersion(String latestVersion) {
		this.latestVersion = latestVersion;
	}

	/**
	 * @return the latestVersionReminder
	 */
	@Override
	public boolean isLatestVersionReminder() {
		return latestVersionReminder;
	}

	/**
	 * @param latestVersionReminder the latestVersionReminder to set
	 */
	@Override
	public void setLatestVersionReminder(boolean latestVersionReminder) {
		this.latestVersionReminder = latestVersionReminder;
	}
	
	/**
	 * @return the forgeConfiguration
	 */
	@Override
	public Configuration getForgeConfiguration() {
		return forgeConfiguration;
	}

	/**
	 * @param forgeConfiguration the forgeConfiguration to set
	 */
	@Override
	public void setForgeConfiguration(Configuration forgeConfiguration) {
		this.forgeConfiguration = forgeConfiguration;
	}

	/**
	 * @return the loggerLevel
	 */
	@Override
	public String getLoggerLevel() {
		return loggerLevel;
	}

	/**
	 * @param loggerLevel the loggerLevel to set
	 */
	public void setLoggerLevel(String loggerLevel) {
		this.loggerLevel = loggerLevel;
	}

	/**
	 * @return the loggerFolder
	 */
	@Override
	public String getLoggerFolder() {
		return loggerFolder;
	}

	/**
	 * @param loggerFolder the loggerFolder to set
	 */
	public void setLoggerFolder(String loggerFolder) {
		this.loggerFolder = loggerFolder;
	}

	/**
	 * @return the loggerSize
	 */
	@Override
	public String getLoggerSize() {
		return loggerSize;
	}

	/**
	 * @param loggerSize the loggerSize to set
	 */
	public void setLoggerSize(String loggerSize) {
		this.loggerSize = loggerSize;
	}

	/**
	 * @return the loggerFilename
	 */
	@Override
	public String getLoggerFilename() {
		return loggerFilename;
	}

	/**
	 * @param loggerFilename the loggerFilename to set
	 */
	public void setLoggerFilename(String loggerFilename) {
		this.loggerFilename = loggerFilename;
	}

	/**
	 * @return the modEnabled
	 */
	@Override
	public boolean isModEnabled() {
		return modEnabled;
	}

	/**
	 * @param modEnabled the modEnabled to set
	 */
	@Override
	public void setModEnabled(boolean modEnabled) {
		this.modEnabled = modEnabled;
	}

	/**
	 * @return the modFolder
	 */
	public String getModFolder() {
		return modFolder;
	}

	/**
	 * @param modFolder the modFolder to set
	 */
	public void setModFolder(String modFolder) {
		this.modFolder = modFolder;
	}
}
