/**
 * 
 */
package com.someguyssoftware.dungeons2.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeonsengine.builder.DungeonBuilder;
import com.someguyssoftware.dungeonsengine.builder.IDungeonBuilder;
import com.someguyssoftware.dungeonsengine.builder.IRoomBuilder;
import com.someguyssoftware.dungeonsengine.builder.ISurfaceRoomBuilder;
import com.someguyssoftware.dungeonsengine.builder.LevelBuilder;
import com.someguyssoftware.dungeonsengine.builder.RoomBuilder;
import com.someguyssoftware.dungeonsengine.builder.SurfaceLevelBuilder;
import com.someguyssoftware.dungeonsengine.builder.SurfaceRoomBuilder;
import com.someguyssoftware.dungeonsengine.chest.ChestSheet;
import com.someguyssoftware.dungeonsengine.chest.ChestSheetLoader;
import com.someguyssoftware.dungeonsengine.config.DungeonConfig;
import com.someguyssoftware.dungeonsengine.config.IDungeonsEngineConfig;
import com.someguyssoftware.dungeonsengine.config.LevelConfig;
import com.someguyssoftware.dungeonsengine.generator.DungeonGenerator;
import com.someguyssoftware.dungeonsengine.model.IDungeon;
import com.someguyssoftware.dungeonsengine.model.ILevel;
import com.someguyssoftware.dungeonsengine.model.IRoom;
import com.someguyssoftware.dungeonsengine.printer.DungeonPrettyPrinter;
import com.someguyssoftware.dungeonsengine.spawner.SpawnSheet;
import com.someguyssoftware.dungeonsengine.spawner.SpawnSheetLoader;
import com.someguyssoftware.dungeonsengine.style.StyleSheet;
import com.someguyssoftware.dungeonsengine.style.StyleSheetLoader;
import com.someguyssoftware.dungeonsengine.style.Theme;
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
    			ICoords startPoint = new Coords(x, y, z);
    			
    			// build a level
    			LevelConfig config = new LevelConfig();
    			config.setNumberOfRooms(new Quantity(25, 50)); // VAST = 25-50
    			double factor = 3.2;
    			config.setWidth(new Quantity(5, 15));
    			config.setDepth(new Quantity(5, 15));
    			config.setHeight(new Quantity(5, 10));
    			config.setDegrees(new Quantity(2, 4));
    			config.setXDistance(new Quantity(-(30*factor), (30*factor)));
    			config.setZDistance(new Quantity(-30*factor, 30*factor));
    			config.setYVariance(new Quantity(0, 0));
    			config.setMinecraftConstraintsOn(false);
    			config.setSupportOn(false);	
    			
    			AxisAlignedBB dungeonField = new AxisAlignedBB(startPoint.add(-100,  0, -100).toPos(), startPoint.add(100, 0, 100).toPos());
    			AxisAlignedBB roomField = new AxisAlignedBB(startPoint.add(-50, 0, -50).toPos(), startPoint.add(50, 0, 50).toPos());
    			
    			// select the dungeon config to use
    			DungeonConfig dConfig = new DungeonConfig();
    			dConfig.setNumberOfLevels(new Quantity(2, 3));
//    			dConfig.setUseSupport(false);
    			dConfig.setUseSupport(true);
    			
    			List<IRoom> plannedRooms = new ArrayList<>();
    			IRoomBuilder roomBuilder = new RoomBuilder(roomField);
    			IRoom startRoom = roomBuilder.buildStartRoom(random, startPoint, config);
    			plannedRooms.add(startRoom);
     			IRoom endRoom = roomBuilder.buildEndRoom(random, roomField, startPoint, config, plannedRooms);//.setAnchor(false);

     			/*
     			 * create the main level builder
     			 */
     			// TODO constructor needs to take a config
     			LevelBuilder levelBuilder = new LevelBuilder(server.getWorld(0), random, dungeonField, startPoint);
       			levelBuilder = (LevelBuilder) levelBuilder
        				.withStartRoom(startRoom)
        				.withEndRoom(endRoom);
       			levelBuilder.setConfig(config);
       			
       			/*
       			 * create the surface level builder
       			 */
       			LevelConfig surfaceConfig = config.copy();
       			surfaceConfig.setNumberOfRooms(new Quantity(1, 1));
       			ISurfaceRoomBuilder sfRoomBuilder = new SurfaceRoomBuilder(server.getWorld(0), roomField);
       			SurfaceLevelBuilder sfBuilder = new SurfaceLevelBuilder(null, random, dungeonField, startPoint);
       			sfBuilder.setConfig(config);
       			sfBuilder.setRoomBuilder((IRoomBuilder) sfRoomBuilder);
       			IRoom entranceRoom = sfRoomBuilder.buildEntranceRoom(random, startPoint, config);
       			ILevel sfLevel = sfBuilder
       					.withStartRoom(entranceRoom)
       					.build();		
       			Dungeons2.log.debug(sfLevel);
       			Dungeons2.log.debug(sfLevel.getField());
       			Dungeons2.log.debug(sfLevel);
       			
       			/*
       			 * create the dungeon builder
       			 */
    			IDungeonBuilder dungeonBuilder = new DungeonBuilder(Dungeons2.instance, sfBuilder, levelBuilder, dConfig);
    			
    			/*
    			 * add the level builders
    			 */
//    			dungeonBuilder.setLevelBuilder(levelBuilder);
    			
    			// TODO this is all that should be needed at this point... not necessary to set up the level builder, room builder etc
    			// that should be  init in dungeon builder if it doesn't exist.
    			// build the dungeon
      			IDungeon dungeon = dungeonBuilder.build(server.getWorld(0), new Random(), dungeonField, startPoint, dConfig);
    			if (dungeon == DungeonBuilder.EMPTY_DUNGEON) {
    				Dungeons2.log.warn("Empty Dungeon");
    				return;
    			}
    			StyleSheet styleSheet = StyleSheetLoader.load();
    			ChestSheet chestSheet = ChestSheetLoader.load();
    			SpawnSheet spawnSheet = SpawnSheetLoader.load();
    			Theme theme = styleSheet.getThemes().get(styleSheet.getThemes().keySet().toArray()[random.nextInt(styleSheet.getThemes().size())]);

    			Dungeons2.log.debug("theme:" + theme);
    			// assign theme to dungeon
    			dungeon.setTheme(theme);
    			DungeonGenerator gen = new DungeonGenerator((IDungeonsEngineConfig) Dungeons2.instance.getConfig());
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
						Dungeons2.log.info("Dungeon dumps enabled.");
						try {
							Dungeons2.log.info("Dumping dungeon...");
							Dungeons2.dungeonsWorldGen.dump(dungeon);
						}
						catch(Exception e ) {
							Dungeons2.log.info("Dungeon dump failed... try plan B...");
						DungeonPrettyPrinter printer  =new DungeonPrettyPrinter();
						String s = printer.print(dungeon, ModConfig.dungeonsFolder + "dumps/");
						Dungeons2.log.debug("\n" + s);
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
