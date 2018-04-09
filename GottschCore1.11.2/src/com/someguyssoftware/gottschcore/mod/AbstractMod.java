/**
 * 
 */
package com.someguyssoftware.gottschcore.mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.BaseConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.someguyssoftware.gottschcore.config.ILoggerConfig;
import com.someguyssoftware.gottschcore.eventhandler.PlayerFMLEventHandler;
import com.someguyssoftware.gottschcore.version.BuildVersion;
import com.someguyssoftware.gottschcore.version.VersionChecker;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * @author Mark Gottschling on Mar 5, 2016
 * @since 2.3.0
 */
public abstract class AbstractMod implements IMod {

	// latest version
	private static BuildVersion latestVersion;

	/**
	 * @param event
	 */
	@EventHandler
	public void preInt(FMLPreInitializationEvent event) {
		// register events
		MinecraftForge.EVENT_BUS.register(new PlayerFMLEventHandler(this));
	}
	
	
	@EventHandler
	public void init(FMLInitializationEvent event) {
		// does nothing currently
	}
	
	/**
	 * 
	 * @param event
	 */
	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		// check config if version check is enabled
		if (getConfig().isEnableVersionChecker())	{
			// get the latest version from the website
			setModLatestVersion(VersionChecker.getVersion(getVerisionURL(), getMinecraftVersion()));
		}
	}
	
    /**
     * Prepend the name with the mod ID, suitable for ResourceLocations such as textures.
     * @param name
     * @return eg "treasure:myblockname"
     */
    public String prependModID(String name) {return this.getId() + ":" + name;}
    
	/*
	 *  TODO this is wrong. getInstance() is a static method for singleton's to get the instance of the class.
	 *  But how else can you get the mod in the api when you don't know what the given mod class is.
	 */
	/* (non-Javadoc)
	 * @see com.someguyssoftware.mod.IMod#getInstance()
	 */
	@Override
	abstract public IMod getInstance();

	@Override
	abstract public String getVersion();
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.mod.IMod#getModLatestVersion()
	 */
	@Override
	public BuildVersion getModLatestVersion() {
		return AbstractMod.latestVersion;
	}
    
	/* (non-Javadoc)
	 * @see com.someguyssoftware.mod.IMod#setModLatestVersion()
	 */
	@Override
	public void setModLatestVersion(BuildVersion latestVersion) {
		AbstractMod.latestVersion = latestVersion;
	}
	
	/**
	 * 
	 * @param logName
	 * @param loggerConfig
	 * @return
	 */
	public Appender createAppender(String logName, ILoggerConfig loggerConfig) {
		// get config properties
		String loggerFolder = loggerConfig.getLoggerFolder();
		
		// ensure the folder ends with a forward-slash
		if (!loggerFolder.endsWith("/")) {
			loggerFolder += "/";
		}		
		
		final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final Configuration config = loggerContext.getConfiguration();

        // create a size-based trigger policy for 500K
        SizeBasedTriggeringPolicy policy = SizeBasedTriggeringPolicy.createPolicy(loggerConfig.getLoggerSize());
        // create the pattern for log statements
        PatternLayout layout = PatternLayout.createLayout("%d [%t] %p %c | %F:%L | %m%n", null, null, null, "true");
        
        // create a rolling file appender for Dungeon logger (which is used by the Dungeon mod)
        Appender appender = RollingFileAppender.createAppender(
        	loggerFolder + loggerConfig.getLoggerFilename() + ".log",
        	loggerFolder + loggerConfig.getLoggerFilename() + "-%d{yyyy-MM-dd-HH_mm_ss}.log",
        	"true",
        	logName,
        	"true",
            "true",
            policy,
            null,
            layout,
            null,
            "true",
            "false",
            null,
            config);
        
        // start the appender
        appender.start();
        
        // add appenders to config
        ((BaseConfiguration) config).addAppender(appender);
        
        return appender;
	}
	
	/**
	 * 
	 * @param logName
	 * @param appender
	 * @param appenderRefName
	 * @param modLoggerConfig
	 */
	public void addAppenderToLogger(String logName, Appender appender,
			String appenderRefName, ILoggerConfig modLoggerConfig) {
		
		final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final Configuration config = loggerContext.getConfiguration();
        
		String loggerLevel = modLoggerConfig.getLoggerLevel();
		
        // create appender references
        AppenderRef appenderReference = AppenderRef.createAppenderRef(appenderRefName, null, null);
        
        // create logger config
        AppenderRef[] refs = new AppenderRef[] {appenderReference};		
        
        // set the logger name to use the rolling file appender
        LoggerConfig loggerConfig = LoggerConfig.createLogger("false", loggerLevel, logName, "true", refs, null, config, null );

        // add appenders to logger config
        loggerConfig.addAppender(appender, null, null);
        
        // add loggers to base configuratoin
        ((BaseConfiguration) config).addLogger(logName, loggerConfig);
        
        // update existing loggers
        loggerContext.updateLoggers();	
	}
}
