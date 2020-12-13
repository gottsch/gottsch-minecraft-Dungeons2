/**
 * 
 */
package com.someguyssoftware.dungeons2.command;

import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.chest.ChestContainer;
import com.someguyssoftware.dungeons2.chest.ChestPopulator;
import com.someguyssoftware.dungeons2.chest.ChestSheet;
import com.someguyssoftware.dungeons2.chest.ChestSheetLoader;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.gottschcore.random.RandomProbabilityCollection;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * 
 * @author Mark Gottschling on Feb 10, 2017
 *
 */
public class ChestCommand extends CommandBase {

	@Override
	public String getName() {
		return "dgn2chest";
	}

	@Override
	public String getUsage(ICommandSender var1) {
		return "/dgn2chest <x> <y> <z> [category | -n <name>]: generates a dungeon chest at location (x,y,z)";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender commandSender, String[] args) {
		EntityPlayer player = (EntityPlayer) commandSender.getCommandSenderEntity();
		try {

			int x, y, z = 0;
			x = Integer.parseInt(args[0]);
			y = Integer.parseInt(args[1]);
			z = Integer.parseInt(args[2]);
			
			String category = "";
			if (args.length > 3) {
				category = args[3];
			}
			
			if (category.equals("")) category = "common";
			
			if (player != null) {
    			World world = commandSender.getEntityWorld();
    			Dungeons2.log.debug("Starting to build Dungeons2! chest ...");

    			BlockPos pos = new BlockPos(x, y, z);
    			world.setBlockState(pos , Blocks.CHEST.getDefaultState());
    			TileEntityChest chest = (TileEntityChest) world.getTileEntity(pos);
				
    			if (chest != null) {
    				ChestSheet chestSheet = ChestSheetLoader.load(ModConfig.chestSheetFile);
    				ChestPopulator pop = new ChestPopulator(chestSheet);

					List<ChestContainer> containers = (List<ChestContainer>) pop.getMap().get(category);
					Dungeons2.log.debug("Containers found:" + containers.size());
					if (containers != null && !containers.isEmpty()) {
						// add each container to the random prob collection
						Dungeons2.log.info("Selected chest containers:" + containers);
						RandomProbabilityCollection<ChestContainer> chestProbCol = new RandomProbabilityCollection<>(containers);
						// select a container
						ChestContainer container = (ChestContainer) chestProbCol.next();		
						Dungeons2.log.info("Chosen chest container:" + container);
						pop.populate(new Random(), chest, container);
					}
    			}
    		}
		}
		catch(Exception e) {
			player.sendMessage(new TextComponentString("Error:  " + e.getMessage()));
			Dungeons2.log.error("Error generating Dungeons2! chest:", e);
			e.printStackTrace();
		}
	}
}
