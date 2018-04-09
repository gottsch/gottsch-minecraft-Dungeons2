/**
 * 
 */
package com.someguyssoftware.gottschcore.eventhandler;

import net.minecraft.util.text.TextFormatting;

import com.someguyssoftware.gottschcore.config.IConfig;
import com.someguyssoftware.gottschcore.mod.IMod;
import com.someguyssoftware.gottschcore.version.BuildVersion;
import com.someguyssoftware.gottschcore.version.VersionChecker;

import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 
 * @author Mark Gottschling on Apr 29, 2017
 *
 */
public class PlayerFMLEventHandler {
	// reference to the mod.
	private IMod mod;
	
	/**
	 * 
	 * @param mod
	 */
	public PlayerFMLEventHandler(IMod mod) {
		this.mod = mod;
	}
	
	/**
	 * Check current mod's build version against the published version when the player logs into a world.
	 * @param event
	 */
	@SubscribeEvent
	public void checkVersionOnLogIn(PlayerEvent.PlayerLoggedInEvent event) {
		// proceed only if the latest version is not empty and enabled in the config
		if (!mod.getConfig().isEnableVersionChecker() || mod.getModLatestVersion().isEmpty()) {
			return;
		}
		
		// get the latest version recorded in the config
		BuildVersion configVersion = new BuildVersion(mod.getConfig().getLatestVersion());

		// TODO update config
		
		boolean isCurrent = VersionChecker.checkVersion(mod.getModLatestVersion(), new BuildVersion(mod.getClass().getAnnotation(Mod.class).version()));
		boolean isConfigCurrent = VersionChecker.checkVersion(mod.getModLatestVersion(), configVersion);
		boolean isReminderOn = mod.getConfig().isLatestVersionReminder();
		
		if (!isConfigCurrent) {
			// update config
			mod.getConfig().setProperty(IConfig.MOD_CATEGORY, "latestVersion", mod.getModLatestVersion().toString());
			// turn the reminder back on for the latest version
			mod.getConfig().setProperty(IConfig.MOD_CATEGORY, "latestVersionReminder", true);
		}
		
		if (!isCurrent && isReminderOn) {
			StringBuilder builder = new StringBuilder();
			builder
				.append(TextFormatting.WHITE)
				.append("A new ")
				.append(TextFormatting.GOLD)
				.append(mod.getName() + " ")
				.append(TextFormatting.WHITE)
				.append("version is available: ")
				.append(TextFormatting.GOLD)
				.append(mod.getModLatestVersion().toString());

			event.player.sendMessage(new TextComponentString(builder.toString()));
			
			// TODO spin a new thread and have it sleep for 8 seconds before displaying messageepi
			// TODO present the user with an interface to toggle version reminder instead of text message
			// TODO or present a command usage to stop reminder for this version.
			// TODO update config
		}
	}
}
