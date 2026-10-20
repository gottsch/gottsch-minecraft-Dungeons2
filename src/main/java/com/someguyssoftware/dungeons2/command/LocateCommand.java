/**
 *
 */
package com.someguyssoftware.dungeons2.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.someguyssoftware.dungeons2.cache.FeatureCaches;
import com.someguyssoftware.dungeons2.model.DungeonInfo;
import com.someguyssoftware.dungeons2.worldgen.DungeonGenQueue;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

/**
 * Finds the dungeons nearest to the sender, with a click-to-teleport link to each.
 * <p>
 * /dgn2locate : the nearest generated dungeon and the nearest unbuilt dungeon site<br>
 * /dgn2locate list [count] : the nearest generated dungeons (default 10)<br>
 * /dgn2locate sites [count] : the nearest unbuilt dungeon sites (default 10). Teleporting to one (and staying)
 * gets it built, since a site is built once a player is within 256 blocks and its chunks are loaded.
 *
 * @author Mark Gottschling on 10/19/2026
 */
public class LocateCommand extends CommandBase {
	private static final int DEFAULT_LIST_COUNT = 10;
	private static final String NO_DUNGEONS = "No dungeons have been generated yet.";
	private static final String NO_SITES = "No unbuilt dungeon sites. Explore new terrain to find some.";

	@Override
	public String getName() {
		return "dgn2locate";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/dgn2locate [list | sites] [count] : finds the nearest generated dungeons (list) or unbuilt dungeon sites (sites)";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 2;
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, "list", "sites");
		}
		return new ArrayList<>();
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (sender.getEntityWorld().provider.getDimension() != 0) {
			sender.sendMessage(new TextComponentString("Dungeons only generate in the Overworld."));
			return;
		}
		BlockPos from = sender.getPosition();
		List<DungeonInfo> dungeons = getDungeons(from);
		List<Site> sites = getSites(from);

		if (args.length == 0) {
			if (dungeons.isEmpty()) {
				sender.sendMessage(new TextComponentString(NO_DUNGEONS));
			}
			else {
				sender.sendMessage(new TextComponentString("Nearest dungeon: ").appendSibling(describe(from, dungeons.get(0))));
			}
			if (sites.isEmpty()) {
				sender.sendMessage(new TextComponentString(NO_SITES));
			}
			else {
				sender.sendMessage(new TextComponentString(String.format("Nearest unbuilt site (of %d): ", sites.size())).appendSibling(describe(from, sites.get(0))));
			}
			return;
		}

		int count = args.length > 1 ? parseInt(args[1], 1) : DEFAULT_LIST_COUNT;
		if ("list".equalsIgnoreCase(args[0])) {
			if (dungeons.isEmpty()) {
				sender.sendMessage(new TextComponentString(NO_DUNGEONS));
				return;
			}
			sender.sendMessage(new TextComponentString(String.format("Nearest %d of %d dungeons:", Math.min(count, dungeons.size()), dungeons.size())));
			for (DungeonInfo info : dungeons.subList(0, Math.min(count, dungeons.size()))) {
				sender.sendMessage(describe(from, info));
			}
		}
		else if ("sites".equalsIgnoreCase(args[0])) {
			if (sites.isEmpty()) {
				sender.sendMessage(new TextComponentString(NO_SITES));
				return;
			}
			sender.sendMessage(new TextComponentString(String.format("Nearest %d of %d unbuilt dungeon sites (built once a player is within 256 blocks):",
					Math.min(count, sites.size()), sites.size())));
			for (Site site : sites.subList(0, Math.min(count, sites.size()))) {
				sender.sendMessage(describe(from, site));
			}
		}
		else {
			throw new WrongUsageException(getUsage(sender));
		}
	}

	/**
	 *
	 * @param from
	 * @return the generated dungeons, nearest first
	 */
	private List<DungeonInfo> getDungeons(BlockPos from) {
		// generated dungeons have levels. placeholders (sites that failed or are not built yet) don't.
		List<DungeonInfo> dungeons = new ArrayList<>();
		for (DungeonInfo info : FeatureCaches.CACHE.getValues()) {
			if (info.getLevels() > 0 && info.getCoords() != null) {
				dungeons.add(info);
			}
		}
		dungeons.sort(Comparator.comparingDouble(info -> distanceSq(from, info.getCoords())));
		return dungeons;
	}

	/**
	 *
	 * @param from
	 * @return the sites being built or waiting to be built, nearest first
	 */
	private List<Site> getSites(BlockPos from) {
		List<Site> sites = new ArrayList<>();
		DungeonGenQueue.getActiveSites().forEach(coords -> sites.add(new Site(coords, true)));
		DungeonGenQueue.getPendingSites().forEach(coords -> sites.add(new Site(coords, false)));
		sites.sort(Comparator.comparingDouble(site -> distanceSq(from, site.coords)));
		return sites;
	}

	/**
	 *
	 * @param from
	 * @param info
	 * @return a line describing the dungeon, with a link that teleports to its entrance
	 */
	private ITextComponent describe(BlockPos from, DungeonInfo info) {
		ICoords coords = info.getCoords();
		String theme = info.getThemeName() != null ? info.getThemeName() : "unknown";
		ITextComponent line = new TextComponentString(String.format("%d levels, %s theme, at %d %d %d, %d blocks away ",
				info.getLevels(), theme, coords.getX(), coords.getY(), coords.getZ(), distance(from, coords)));
		return line.appendSibling(teleportLink(String.format("/tp @s %d %d %d", coords.getX(), coords.getY() + 1, coords.getZ())));
	}

	/**
	 *
	 * @param from
	 * @param site
	 * @return a line describing the unbuilt site, with a link that teleports to it
	 */
	private ITextComponent describe(BlockPos from, Site site) {
		ITextComponent line = new TextComponentString(String.format("%d, %d, %d blocks away (%s) ",
				site.coords.getX(), site.coords.getZ(), distance(from, site.coords), site.isBuilding ? "being built" : "waiting"));
		/*
		 * the site's y is a placeholder, not the surface, and its chunks may not be loaded to look the surface up.
		 * spreadplayers places the player on a safe top block within 16 blocks of x/z instead.
		 */
		return line.appendSibling(teleportLink(String.format("/spreadplayers %d %d 0 16 false @s", site.coords.getX(), site.coords.getZ())));
	}

	/**
	 *
	 * @param command
	 * @return a clickable [Teleport] link that runs the command
	 */
	private ITextComponent teleportLink(String command) {
		ITextComponent link = new TextComponentString("[Teleport]");
		link.setStyle(new Style()
				.setColor(TextFormatting.GREEN)
				.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
				.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(command))));
		return link;
	}

	private static double distanceSq(BlockPos from, ICoords to) {
		double dx = from.getX() - to.getX();
		double dz = from.getZ() - to.getZ();
		return dx * dx + dz * dz;
	}

	private static int distance(BlockPos from, ICoords to) {
		return (int) Math.sqrt(distanceSq(from, to));
	}

	/**
	 * An unbuilt dungeon site.
	 */
	private static class Site {
		final ICoords coords;
		final boolean isBuilding;

		Site(ICoords coords, boolean isBuilding) {
			this.coords = coords;
			this.isBuilding = isBuilding;
		}
	}
}
