/**
 * 
 */
package com.someguyssoftware.dungeons2.command;

import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.builder.DungeonBuilderTopDown;
import com.someguyssoftware.dungeons2.builder.IDungeonBuilder;
import com.someguyssoftware.dungeons2.builder.LevelBuilder;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.chest.ChestSheetLoader;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.generator.DungeonGenerator;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.printer.DungeonPrettyPrinter;
import com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import com.someguyssoftware.dungeons2.spawner.SpawnSheetLoader;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.StyleSheetLoader;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * 
 * @author Mark Gottschling on Jul 27, 2016
 *
 */
public class BuildCommand extends CommandBase {

	@Override
	public String getName() {
		return "dgn2build";
	}

	@Override
	public String getUsage(ICommandSender var1) {
		return "/dgn2build <x> <y> <z> [pattern] [size] [terrianCheck] : generates the dungeon structure at location (x,y,z)";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender commandSender, String[] args) {
		EntityPlayer player = (EntityPlayer) commandSender.getCommandSenderEntity();
		try {

			int x, y, z = 0;
			x = Integer.parseInt(args[0]);
			y = Integer.parseInt(args[1]);
			z = Integer.parseInt(args[2]);
			
			String pattern = "";
			if (args.length > 3) {
				pattern = args[3];
			}
			String size = "";
			if (args.length >4) {
				size = args[4];
			}
			
			// TODO set config
			String terrainCheck = "0";
			if (args.length > 5) {
				terrainCheck = args[5];
			}
			
			if (player != null) {
    			World world = commandSender.getEntityWorld();
    			Dungeons2.log.debug("Starting to build Dungeons2! dungeon ...");
    			Random random = new Random();
    			
    			// initialize a dungeons generator
//	    		DungeonsGenerator gen = new DungeonsGenerator();
    			LevelConfig config = new LevelConfig();
    			config.setMinecraftConstraintsOn(false);
    			config.setSupportOn(false);
    			config.setNumberOfRooms(new Quantity(10, 25));
    			config.setWidth(new Quantity(5, 15));
    			config.setDepth(new Quantity(5, 15));
    			config.setHeight(new Quantity(5, 8));
    			config.setYVariance(new Quantity(0, 0));
    			// epicenter style settings
    			// NOTE epicenter style needs to have a smaller distance, else you get a lot of long hallways
    			config.setXDistance(new Quantity(-25, 25));
    			config.setZDistance(new Quantity(-25, 25));
//    			config.setXOffset(new Quantity(30, 30));
//    			config.setZOffset(new Quantity(30, 30));
//    			
    			ICoords startPoint = new Coords(x, y, z);
    			
    			Biome biome = world.getBiome(startPoint.toPos());
				// get the biome ID
				Integer biomeID = Biome.getIdForBiome(biome);
			    List<IDungeonConfig> dcList = Dungeons2.dgnCfgMgr.getByBiome(/*biome.getBiomeName()*/biomeID);
			    // select one
			    IDungeonConfig dc = dcList.get(random.nextInt(dcList.size()));
			    Dungeons2.log.debug("selected dungeon config -> {}", dc);
			    
//    			LevelBuilder builder = new LevelBuilder();
//    			Level level = builder.build(server.getEntityWorld(), new Random(), startPoint, config);
//    			Dungeon dungeon = new Dungeon();
//    			dungeon.getLevels().add(level);
    			
    			LevelBuilder levelBuilder = new LevelBuilder(config);
    			// select the dungeon config to use
    			DungeonConfig dConfig = new DungeonConfig();
    			dConfig.setNumberOfLevels(new Quantity(2, 5));
    			dConfig.setUseSupport(false);
//    			dConfig.setUseSupport(true);
    			
    			// use a bottom-up dungeonBuilder
//    			DungeonBuilderBottomUp dungeonBuilder = new DungeonBuilderBottomUp();    			
//    			dungeonBuilder.setLevelBuilder(levelBuilder);
    			
    			// create field
    			AxisAlignedBB dungeonField = new AxisAlignedBB(startPoint.toPos()).grow(80);
    			
    			// use a top-down dungeonBuilder
    			IDungeonBuilder dungeonBuilder = new DungeonBuilderTopDown();
    			dungeonBuilder
    			.withBoundary(dungeonField)
    			.withStartPoint(startPoint)
    			.setLevelBuilder(levelBuilder);    			
    			
    			// build the dungeon
      			Dungeon dungeon = dungeonBuilder.build(world, random, startPoint, dConfig);
    			if (dungeon == DungeonBuilderTopDown.EMPTY_DUNGEON) {
    				Dungeons2.log.warn("Empty Dungeon");
    				return;
    			}
    			
    			// TODO move all the gson create/load into a separate class/method
//    			GsonBuilder gsonBuilder = new GsonBuilder();
//    			gsonBuilder.excludeFieldsWithoutExposeAnnotation();
//    			gsonBuilder.setPrettyPrinting();
//    			Gson gson = gsonBuilder.create();
//    			JsonReader jsonReader = null;
//    			// TEMP
//    			InputStream is = getClass().getResourceAsStream("/resources/test.json");
//    			Reader reader = new InputStreamReader(is);
//    			jsonReader = new JsonReader(reader);
//    			StyleSheet styleSheet = gson.fromJson(reader, StyleSheet.class);
    			StyleSheet styleSheet = StyleSheetLoader.load();
    			ChestSheet chestSheet = ChestSheetLoader.load();
    			SpawnSheet spawnSheet = SpawnSheetLoader.load();
//    			Dungeons2.log.debug("stylesheet:" + styleSheet);
//    			Dungeons2.log.debug("themes size:" + styleSheet.getThemes().size());
    			// TODO load the styleSheet here and pass it and the selected Theme in.
    			// TODO what is theme is null
    			Theme theme = styleSheet.getThemes().get(styleSheet.getThemes().keySet().toArray()[random.nextInt(styleSheet.getThemes().size())]);

    			Dungeons2.log.debug("theme:" + theme);
    			// assign theme to dungeon
    			dungeon.setTheme(theme);
    			DungeonGenerator gen = new DungeonGenerator();
    			gen.generate(world, random, dungeon, styleSheet, chestSheet, spawnSheet);
    			
//				if (terrainCheck.equals("1") || terrainCheck.equalsIgnoreCase("true")) {
//					config.setEnableTerrainChecks(true);
//				}
//				else {
//						config.setEnableTerrainChecks(false);
//				}
				
    			if (dungeon!= null && dungeon.getLevels() != null && dungeon.getLevels().size() > 0) {
//        		if (level != null && !level.getRooms().isEmpty()) {
        			Dungeons2.log.info(String.format("Dungeons2! dungeon generated @ %d %d %d", x, y, z));
        			player.sendMessage(new TextComponentString(String.format("Dungeons2! dungeon generated @ %d %d %d", x, y, z)));
        			
					if (ModConfig.enableDumps) {
						try {
							Dungeons2.dungeonsWorldGen.dump(dungeon);
						}
						catch(Exception e ) {
//						DungeonPrettyPrinter printer  =new DungeonPrettyPrinter();
//						String s = printer.print(dungeon, ModConfig.dungeonsFolder + "dumps/");
//						Dungeons2.log.debug("\n" + s);
						}
					}
        		}
    		}
		}
		catch(Exception e) {
			player.sendMessage(new TextComponentString("Error:  " + e.getMessage()));
			Dungeons2.log.error("Error generating Dungeons2! dungeon:", e);
			e.printStackTrace();
		}
	}
}
