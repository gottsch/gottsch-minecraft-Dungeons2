/**
 * 
 */
package com.someguyssoftware.gottschcore.mod;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.AppenderRef;
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
	 * Add rolling file appender to the current logging system.
	 */
	public void addRollingFileAppenderToLogger(String loggerName, String appenderName, ILoggerConfig modConfig) {
		// get config properties
		String loggerLevel = modConfig.getLoggerLevel();
		String loggerFolder = modConfig.getLoggerFolder();
		
		if (!loggerFolder.endsWith("/")) {
			loggerFolder += "/";
		}

		final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final Configuration config = loggerContext.getConfiguration();
        
        // create a sized-based trigger policy, using config setting for size.
        SizeBasedTriggeringPolicy policy = SizeBasedTriggeringPolicy.createPolicy(modConfig.getLoggerSize());
        // create the pattern for log statements
        PatternLayout layout = PatternLayout
        		.newBuilder()
        		.withPattern("%d [%t] %p %c | %F:%L | %m%n")
        		.withAlwaysWriteExceptions(true)
        		.build();
        
        // create a rolling file appender for SGS_Treasure logger (which is used by the Treasure mod)
        Appender appender = RollingFileAppender
        		.newBuilder()
        		.withFileName(loggerFolder + modConfig.getLoggerFilename() + ".log")
        		.withFilePattern(loggerFolder + modConfig.getLoggerFilename() + "-%d{yyyy-MM-dd-HH_mm_ss}.log")
        		.withAppend(true)
        		.withName(appenderName)
        		.withBufferedIo(true)
        		.withImmediateFlush(true)
        		.withPolicy(policy)
//        		.withStrategy(strategy)
        		.withLayout(layout)
//        		.withFilter(filter)
        		.withIgnoreExceptions(true)
        		.withAdvertise(false)
        		.setConfiguration(config)
        		.build();

        // start the appender
        appender.start();
        
        // add appenders to config
        config.addAppender(appender);
        
        // create appender references
        AppenderRef appenderReference = AppenderRef.createAppenderRef(appenderName, null, null);
        
        // create logger config
        AppenderRef[] refs = new AppenderRef[] {appenderReference};

		Level level = Level.getLevel(loggerLevel.toUpperCase());
		
        // set the logger name "FastLadder" to use the rolling file appender
        LoggerConfig loggerConfig = LoggerConfig.createLogger("false", level, loggerName, "true", refs, null, config, null );
        
        // add appenders to logger config
        loggerConfig.addAppender(appender, null, null);

        // add loggers to base configuration
        config.addLogger(loggerName, loggerConfig);
        
        // update existing loggers
        loggerContext.updateLoggers();	
	}
	
	/**
	 * Add rolling file appender to the current logging system.
	 */
	public Appender createRollingFileAppender(String appenderName, ILoggerConfig modConfig) {
		// get config properties
//		String loggerLevel = modConfig.getLoggerLevel();
		String loggerFolder = modConfig.getLoggerFolder();
		
		if (!loggerFolder.endsWith("/")) {
			loggerFolder += "/";
		}

		final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final Configuration config = loggerContext.getConfiguration();
        
        // create a sized-based trigger policy, using config setting for size.
        SizeBasedTriggeringPolicy policy = SizeBasedTriggeringPolicy.createPolicy(modConfig.getLoggerSize());
        // create the pattern for log statements
        PatternLayout layout = PatternLayout
        		.newBuilder()
        		.withPattern("%d [%t] %p %c | %F:%L | %m%n")
        		.withAlwaysWriteExceptions(true)
        		.build();
        
        // create a rolling file appender for SGS_Treasure logger (which is used by the Treasure mod)
        Appender appender = RollingFileAppender
        		.newBuilder()
        		.withFileName(loggerFolder + modConfig.getLoggerFilename() + ".log")
        		.withFilePattern(loggerFolder + modConfig.getLoggerFilename() + "-%d{yyyy-MM-dd-HH_mm_ss}.log")
        		.withAppend(true)
        		.withName(appenderName)
        		.withBufferedIo(true)
        		.withImmediateFlush(true)
        		.withPolicy(policy)
//        		.withStrategy(strategy)
        		.withLayout(layout)
//        		.withFilter(filter)
        		.withIgnoreExceptions(true)
        		.withAdvertise(false)
        		.setConfiguration(config)
        		.build();

        // start the appender
        appender.start();
        
        // add appenders to config
        config.addAppender(appender);

        return appender;
	}
	
	/**
	 * 
	 * @param loggerName
	 * @param appender
	 * @param modConfig
	 */
	public void addAppenderToLogger(Appender appender, String loggerName, ILoggerConfig modConfig) {
		// get config properties
		String loggerLevel = modConfig.getLoggerLevel();
		
		final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        final Configuration config = loggerContext.getConfiguration();
        
		Level level = Level.getLevel(loggerLevel.toUpperCase());
		        
        // create appender references
        AppenderRef appenderReference = AppenderRef.createAppenderRef(appender.getName(), null, null);
        
        // create logger config
        AppenderRef[] refs = new AppenderRef[] {appenderReference};
        
        // set the logger name "FastLadder" to use the rolling file appender
        LoggerConfig loggerConfig = LoggerConfig.createLogger("false", level, loggerName, "true", refs, null, config, null );
        
        // add appenders to logger config
        loggerConfig.addAppender(appender, null, null);

        // add loggers to base configuration
        config.addLogger(loggerName, loggerConfig);
        
        // update existing loggers
        loggerContext.updateLoggers();	
	}
}
