/**
 * 
 */
package com.someguyssoftware.dungeons2.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.generator.RuinProcessor;
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
import net.minecraft.command.CommandException;
import net.minecraft.command.WrongUsageException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
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
		return "/dgn2entrance [<x> <y> <z>] : generates a dungeon entrance at location (x,y,z), or where you stand. ~ coordinates are allowed.";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
		return args.length > 0 && args.length <= 3 ? getTabCompletionCoordinate(args, 0, targetPos) : Collections.emptyList();
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender commandSender, String[] args) throws CommandException {
		// no coordinates = where the sender stands. ~ (relative) coordinates are supported.
		BlockPos pos = args.length >= 3 ? parseBlockPos(commandSender, args, 0, false) : commandSender.getPosition();
		if (args.length > 0 && args.length < 3) {
			throw new WrongUsageException(getUsage(commandSender));
		}

		try {
			World world = commandSender.getEntityWorld();
			Dungeons2.log.info(String.format("Building Dungeons2! entrance @ %d %d %d...", pos.getX(), pos.getY(), pos.getZ()));

			// initialize a dungeons builder
			IDungeonBuilder builder = new DungeonBuilderTopDown(null);	

			// get the start point
			ICoords startPoint = new Coords(pos);
			Random random = new Random();

			Room entranceRoom = builder.buildEntranceRoom(world, random, startPoint);

			// use the loaded style sheet (the one world generation uses), else the built-in one
			DungeonGenerator gen = new DungeonGenerator();
			StyleSheet styleSheet = Dungeons2.dungeonsWorldGen != null ? Dungeons2.dungeonsWorldGen.getStyleSheet() : null;
			if (styleSheet == null || styleSheet.getThemes() == null || styleSheet.getThemes().isEmpty()) {
				styleSheet = DungeonGenerator.getDefaultStyleSheet();
			}
			// NOTE themes are keyed by name. pick one at random
			List<Theme> themes = new ArrayList<>(styleSheet.getThemes().values());
			Theme theme = themes.get(random.nextInt(themes.size()));

			Multimap<String, IRoomGenerator> roomGenerators = ArrayListMultimap.create();
			LayoutAssigner layoutAssigner = new LayoutAssigner(styleSheet);
			IRoomDecorator roomDecorator = new RoomDecorator(gen.getDefaultChestSheet(), gen.getDefaultSpawnSheet());
			RoomGeneratorFactory factory = new RoomGeneratorFactory(roomGenerators);
			////////////// from buildEntrance ///////////////////
			LevelConfig entranceLevelConfig = new LevelConfig();
			entranceLevelConfig.setDecayMultiplier(Math.min(1, entranceLevelConfig.getDecayMultiplier())); // increase the decay multiplier to a minimum of 5
			// assign a layout to the entrance room
			layoutAssigner.assign(random, entranceRoom);
			IRoomGenerator roomGen = factory.createRoomGenerator(random, entranceRoom, false); // support
			roomGen.generate(world, random, entranceRoom, theme, styleSheet, entranceLevelConfig);
			roomDecorator.decorate(world, random, roomGen.getGenerationStrategy().getBlockProvider(), entranceRoom, entranceLevelConfig);
			// weather it into a ruin, same as a generated dungeon's entrance
			RuinProcessor.ruin(world, random, entranceRoom);

			commandSender.sendMessage(new TextComponentString(String.format("Built a %s entrance at %d %d %d.", theme.getName(), pos.getX(), pos.getY(), pos.getZ())));
			Dungeons2.log.info(new RoomPrettyPrinter().print(entranceRoom));
		}
		catch(Exception e) {
			commandSender.sendMessage(new TextComponentString("Error building the entrance: " + e + " (see the Dungeons2 log)"));
			Dungeons2.log.error("Error generating Dungeons2! entrance:", e);
		}
	}
}
