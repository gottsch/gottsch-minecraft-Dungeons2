/**
 * 
 */
package com.someguyssoftware.gottschcore.command;

import com.someguyssoftware.gottschcore.GottschCore;
import com.someguyssoftware.gottschcore.mod.IMod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * 
 * @author Mark Gottschling on Feb 10, 2017
 *
 */
public class ShowVersionCommand extends CommandBase {
	private IMod mod;
	
	/**
	 * 
	 * @param mod
	 */
	public ShowVersionCommand(IMod mod) {
		this.mod = mod;
	}
	
	@Override
	public void execute(MinecraftServer server, ICommandSender commandSender, String[] args) {
		EntityPlayer player = (EntityPlayer) commandSender.getCommandSenderEntity();
		try {

			if (args[0] == null || args[0].equals("")) player.sendMessage(new TextComponentString("Missing mod ID."));
			
			String modid;
			modid =args[0];
			
			if (player != null) {
				player.sendMessage(new TextComponentString(mod.getName() + " version: " + mod.getVersion()));
				player.sendMessage(new TextComponentString("Latest released version: " + mod.getModLatestVersion().toString()));
    		}
		}
		catch(Exception e) {
			player.sendMessage(new TextComponentString("Error:  " + e.getMessage()));
			GottschCore.logger.error("Error generating Dungeons2! chest:", e);
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#getName()
	 */
	@Override
	public String getName() {
		return "showversion " + this.mod.getId();
	}

	/* (non-Javadoc)
	 * @see net.minecraft.command.ICommand#getUsage(net.minecraft.command.ICommandSender)
	 */
	@Override
	public String getUsage(ICommandSender sender) {
		return "showversion " + this.mod.getId();
	}
}
