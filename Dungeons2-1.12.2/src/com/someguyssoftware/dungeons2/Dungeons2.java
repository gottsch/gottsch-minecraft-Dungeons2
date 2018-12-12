/**
 * 
 */
package com.someguyssoftware.dungeons2;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;

import com.someguyssoftware.dungeons2.block.BasicFacadeBlock;
import com.someguyssoftware.dungeons2.block.CorniceFacadeBlock;
import com.someguyssoftware.dungeons2.block.CrownMoldingFacadeBlock;
import com.someguyssoftware.dungeons2.block.FlutePillarBlock;
import com.someguyssoftware.dungeons2.block.FlutePillarFacadeBlock;
import com.someguyssoftware.dungeons2.block.SevenEightsPillarFacadeBlock;
import com.someguyssoftware.dungeons2.block.TeePillarFacadeBlock;
import com.someguyssoftware.dungeons2.block.TeeThinPillarFacadeBlock;
import com.someguyssoftware.dungeons2.command.BuildCommand;
import com.someguyssoftware.dungeons2.command.BuildEntranceCommand;
import com.someguyssoftware.dungeons2.command.ChestCommand;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.worldgen.DungeonsWorldGen;
import com.someguyssoftware.dungeonsengine.chest.ChestSheetLoader;
import com.someguyssoftware.dungeonsengine.rotate.RotatorHelper;
import com.someguyssoftware.dungeonsengine.rotate.RotatorRegistry;
import com.someguyssoftware.dungeonsengine.spawner.SpawnSheetLoader;
import com.someguyssoftware.dungeonsengine.style.StyleSheetLoader;
import com.someguyssoftware.gottschcore.GottschCore;
import com.someguyssoftware.gottschcore.annotation.Credits;
import com.someguyssoftware.gottschcore.command.ShowVersionCommand;
import com.someguyssoftware.gottschcore.config.IConfig;
import com.someguyssoftware.gottschcore.mod.AbstractMod;
import com.someguyssoftware.gottschcore.mod.IMod;
import com.someguyssoftware.gottschcore.proxy.IProxy;
import com.someguyssoftware.gottschcore.version.BuildVersion;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;


/**
 * 
 * @author Mark Gottschling on Jul 27, 2016
 *
 */
@Mod(
	modid=Dungeons2.MODID,
	name=Dungeons2.NAME,
	version=Dungeons2.VERSION,
	dependencies="required-after:gottschcore@[1.5.1,)",
	acceptedMinecraftVersions = "[1.12.2]",
	updateJSON = Dungeons2.UPDATE_JSON_URL
)
@Credits(values={"Dungeons2! was first developed by Mark Gottschling on July 1, 2016."})
public class Dungeons2 extends AbstractMod {
		// constants
		private static final String VERSION_URL = "https://www.dropbox.com/s/fjcnqmbji9ujvbt/dungeons2-versions.json?dl=1";
		private static final BuildVersion MINECRAFT_VERSION = new BuildVersion(1, 12, 2);
		
		public static final String MODID = "dungeons2";
		public static final String NAME = "Dungeons2!";
		public static final String VERSION = "1.3.5"; 
		public static final String UPDATE_JSON_URL = "https://raw.githubusercontent.com/gottsch/gottsch-minecraft-Dungeons2/master/Dungeons2-1.12.2/update.json";
		
		// latest VERSION
		public static BuildVersion latestVersion;

		// logger
		public static final String LOGGER_NAME = "Dungeons2";
		public static Logger log = LogManager.getLogger(LOGGER_NAME);
		
		@Instance(value=Dungeons2.MODID)
		public static Dungeons2 instance = new Dungeons2();
		
		@SidedProxy(clientSide="com.someguyssoftware.dungeons2.proxy.ClientProxy", serverSide="com.someguyssoftware.dungeons2.proxy.ServerProxy")
		public static IProxy proxy;
		
		// simple network wrapper
	    public static SimpleNetworkWrapper network = null;
		
		// forge world generators
	    public static DungeonsWorldGen dungeonsWorldGen;
	    
		/*
		 * config
		 */
		private static final String DUNGEONS_CONFIG_DIR = "dungeons2";
		private static ModConfig config;
	    		
	    /**
	     * 
	     */
	    public Dungeons2() {
	    	
	    }
		
		@EventHandler
		public void preInt(FMLPreInitializationEvent event) {
			super.preInt(event);
			// register additional events

			// create and load the config file		
			config = new ModConfig(this, event.getModConfigurationDirectory(), DUNGEONS_CONFIG_DIR, "general.cfg");
			
			/*
			 *  configure logging
			 */
//			addRollingFileAppenderToLogger(LOGGER_NAME, LOGGER_NAME + "Appender", config);
			// create a rolling file appender
			Appender appender = createRollingFileAppender(LOGGER_NAME + "Appender", config);
			// add appender to mod logger
			addAppenderToLogger(appender, LOGGER_NAME, config);
			// add appender to the GottschCore logger
			addAppenderToLogger(appender, GottschCore.instance.getName(), config);
			// add the appender to the DungeonsEngine logger
			addAppenderToLogger(appender, "DungeonsEngine", config);
		
	        // register the packet handlers
	        network = NetworkRegistry.INSTANCE.newSimpleChannel(Dungeons2.MODID);
    
			// check if the styleSheet exists, else create it from the resource
			StyleSheetLoader.exposeStyleSheet(ModConfig.styleSheetFile);
			
			// check if the chestSheet exists, else create it from the resource
			ChestSheetLoader.exposeChestSheet(ModConfig.chestSheetFile);
			
			// check if the spawnSheet exists, else create it from the resource
			SpawnSheetLoader.exposeSpawnSheet(ModConfig.spawnSheetFile);
			// TODO later when setting up the DungeonGenerator, get the style sheet from the file system and pass to generator
	
			
			// register blocks
//			proxy.registerBlocks();
			
			// register items
//			proxy.registerItems();
			
			// regsiter tile entities
//			proxy.registerTileEntities();
		}
		
		@EventHandler
		public void init(FMLInitializationEvent event) {
			// load the world (minecraft) low-level generators map (NOTE must be AFTER all blocks are registered)
			log.debug("Setting up generator map");
			
	        if (ModConfig.enableDungeons) {		
	    		// register client renderers
	    		proxy.registerRenderers(getConfig());
	    		
				/*
				 * load biome types.
				 * has to come before any generators are loaded.
				 */
//				WorldGenUtil.loadBiomeTypeMap();
				
	    		// add to world generators
				Dungeons2.dungeonsWorldGen = new DungeonsWorldGen();
	        	GameRegistry.registerWorldGenerator(Dungeons2.dungeonsWorldGen, 100);
	        	
	        	// register block rotators
	    		RotatorRegistry registry = RotatorRegistry.getInstance();
	    		registry.registerBlockRotator(BasicFacadeBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);
	    		registry.registerBlockRotator(FlutePillarFacadeBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);	
	    		registry.registerBlockRotator(CrownMoldingFacadeBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);
	    		registry.registerBlockRotator(CorniceFacadeBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);
	    		registry.registerBlockRotator(TeeThinPillarFacadeBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);
	    		registry.registerBlockRotator(TeePillarFacadeBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);
//	    		registry.registerBlockRotator(FlutePillarBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);
	    		registry.registerBlockRotator(FlutePillarFacadeBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);
	    		registry.registerBlockRotator(SevenEightsPillarFacadeBlock.class, RotatorHelper.cardinalDirectionBlockRotator, Dungeons2.instance);
	        }
	        
		}
		
	    @EventHandler
	    public void serverStarted(FMLServerStartingEvent event) {
	    	// add a show version command
	    	event.registerServerCommand(new ShowVersionCommand(this));
	    	// add other commmands
	    	event.registerServerCommand(new BuildCommand());
	    	event.registerServerCommand(new ChestCommand());
	    	event.registerServerCommand(new BuildEntranceCommand());
	    }

	    
		@EventHandler
		public void postInit(FMLPostInitializationEvent event) {
			if (!getConfig().isModEnabled()) return;	
			super.postInit(event);
		}


		@Override
		public IConfig getConfig() {
			return config;
		}

		@Override
		public String getId() {
			return Dungeons2.MODID;
		}
		
		@Override
		public String getName() {
			return Dungeons2.NAME;
		}
		
		@Override
		public IMod getInstance() {
			return instance;
		}

		@Override
		public BuildVersion getModLatestVersion() {
			return latestVersion;
		}
		
		@Override
		public void setModLatestVersion(BuildVersion version) {
			Dungeons2.latestVersion = version;			
		}

		@Override
		public BuildVersion getMinecraftVersion() {
			return Dungeons2.MINECRAFT_VERSION;
		}

		@Override
		public String getVerisionURL() {
			return Dungeons2.VERSION_URL;
		}

		@Override
		public String getVersion() {
			return Dungeons2.VERSION;
		}
		
		@Override
		public String getUpdateURL() {
			return Dungeons2.UPDATE_JSON_URL;
		}
}
