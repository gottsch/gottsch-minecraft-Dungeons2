/**
 * 
 */
package com.someguyssoftware.dungeons2.command;

import java.util.Random;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.builder.DungeonBuilderTopDown;
import com.someguyssoftware.dungeons2.builder.IDungeonBuilder;
import com.someguyssoftware.dungeons2.builder.LevelBuilder;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.chest.ChestSheetLoader;
import com.someguyssoftware.dungeons2.generator.DungeonGenerator;
import com.someguyssoftware.dungeons2.generator.IRoomGenerator;
import com.someguyssoftware.dungeons2.generator.RoomGeneratorFactory;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.printer.RoomPrettyPrinter;
import com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import com.someguyssoftware.dungeons2.spawner.SpawnSheetLoader;
import com.someguyssoftware.dungeons2.style.IRoomDecorator;
import com.someguyssoftware.dungeons2.style.LayoutAssigner;
import com.someguyssoftware.dungeons2.style.RoomDecorator;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.StyleSheetLoader;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * 
 * @author Mark
 *
 */
public class BuildEntranceCommand extends CommandBase {

	@Override
	public String getName() {
		return "dgn2entrance";
	}

	@Override
	public String getUsage(ICommandSender var1) {
		return "/dgn2entrance <x> <y> <z> [terrianCheck] : generates the dungeon entrance at location (x,y,z)";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender commandSender, String[] args) {
		EntityPlayer player = (EntityPlayer) commandSender.getCommandSenderEntity();
		try {

			// get the location to build
			int x, y, z = 0;
			x = Integer.parseInt(args[0]);
			y = Integer.parseInt(args[1]);
			z = Integer.parseInt(args[2]);
			
			// get the terrian arg
			String terrainCheck = "0";
			if (args.length > 3) {
				terrainCheck = args[3];
			}
			
			if (player != null) {
    			World world = commandSender.getEntityWorld();
    			Dungeons2.log.info(String.format("Buidling Dungeons2! entrance @ %d %d %d...", x, y, z));
    			
    			// initialize a dungeons builder
    			IDungeonBuilder builder = new DungeonBuilderTopDown(null);	
    			
	    		// get the start point
    			ICoords startPoint = new Coords(x, y, z);
    			Random random = new Random();
    			
    			Room entranceRoom =builder.buildEntranceRoom(world, random, startPoint);
    			
    			DungeonGenerator gen = new DungeonGenerator();    
    			Multimap<String, IRoomGenerator> roomGenerators = ArrayListMultimap.create();
    			LayoutAssigner layoutAssigner = new LayoutAssigner(gen.getDefaultStyleSheet());
    			IRoomDecorator roomDecorator = new RoomDecorator(gen.getDefaultChestSheet(), gen.getDefaultSpawnSheet());
    			RoomGeneratorFactory factory = new RoomGeneratorFactory(roomGenerators);
    			////////////// from buildEntrance ///////////////////
    			LevelConfig entranceLevelConfig = new LevelConfig();
    			entranceLevelConfig.setDecayMultiplier(Math.min(1, entranceLevelConfig.getDecayMultiplier())); // increase the decay multiplier to a minimum of 5
    			// assign a layout to the entrance room
    			layoutAssigner.assign(random, entranceRoom);
    			IRoomGenerator roomGen = factory.createRoomGenerator(random, entranceRoom, false); // support
    			// TODO need to provide the entrance room generator with a different level config that uses a higher decay multiplier
    			// to create a much more decayed surface structure.
    			roomGen.generate(world, random, entranceRoom, gen.getDefaultStyleSheet().getThemes().get(0), gen.getDefaultStyleSheet(), entranceLevelConfig);
    			roomDecorator.decorate(world, random, roomGen.getGenerationStrategy().getBlockProvider(), entranceRoom, entranceLevelConfig);

    			RoomPrettyPrinter printer = new RoomPrettyPrinter();
    			if (entranceRoom != null) {
    				String s = printer.print(entranceRoom);
    				Dungeons2.log.info(s);
    			}
    			else {
    				Dungeons2.log.debug("Exit was null ?");
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
