/**
 *
 */
package com.someguyssoftware.dungeons2.worldgen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.config.ModConfig;
import com.someguyssoftware.dungeons2.generator.DungeonGenerationJob;
import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;
import com.someguyssoftware.dungeons2.world.ChunkUtil;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

/**
 * Dungeon sites chosen during chunk population, waiting to be generated.
 * <p>
 * Chunk population only records the site (no blocks are written, so no chunks are loaded). On the server tick a
 * site is planned once every chunk the dungeon could touch is loaded and populated and a player is near (the
 * nearest site to a player first), or with ModConfig.buildWithoutPlayers, as soon as its chunks are ready.
 * The dungeon is then written a few rooms per tick, or all at once when no players are online. Several dungeons can be in progress at once; they share the
 * per-tick budget (ModConfig.generationMsPerTick). A dungeon whose chunks unload (everyone walked away) pauses
 * without holding up the others, and resumes when its chunks are loaded again.
 * Pending sites are saved with the world; dungeons still in progress when the server stops are finished first.
 *
 * @author Mark Gottschling on 10/19/2026
 */
public final class DungeonGenQueue {
	private static final String SITES_KEY = "sites";
	// how often (ticks) pending sites are checked
	private static final long CHECK_INTERVAL = 20L;
	// a player must be within this many blocks (x/z) of a site to start it
	private static final double PLAYER_RANGE = 256D;
	private static final double PLAYER_RANGE_SQ = PLAYER_RANGE * PLAYER_RANGE;
	// the maximum number of dungeons being written at the same time. paused dungeons don't count.
	private static final int MAX_RUNNING_JOBS = 4;

	private static final List<ICoords> PENDING = new ArrayList<>();
	private static final List<DungeonGenerationJob> ACTIVE = new ArrayList<>();

	private DungeonGenQueue() {}

	/**
	 *
	 * @param site
	 */
	public static synchronized void add(ICoords site) {
		if (!PENDING.contains(site)) {
			PENDING.add(site);
		}
	}

	/**
	 *
	 * @return a copy of the sites waiting to be planned
	 */
	public static synchronized List<ICoords> getPendingSites() {
		return new ArrayList<>(PENDING);
	}

	/**
	 *
	 * @return the sites of the dungeons being written
	 */
	public static synchronized List<ICoords> getActiveSites() {
		List<ICoords> sites = new ArrayList<>();
		ACTIVE.forEach(job -> sites.add(job.getSite()));
		return sites;
	}

	/**
	 *
	 */
	public static synchronized void clear() {
		PENDING.clear();
		ACTIVE.clear();
	}

	/**
	 * Called every server tick for the overworld.
	 * @param world
	 */
	public static synchronized void tick(World world) {
		int running = runActiveJobs(world);

		if (PENDING.isEmpty() || running >= MAX_RUNNING_JOBS || world.getTotalWorldTime() % CHECK_INTERVAL != 0) {
			return;
		}

		ICoords site = ModConfig.buildWithoutPlayers ? findReadySite(world) : findSiteNearestPlayer(world);
		if (site == null) {
			return;
		}

		// a site is only ever planned once
		PENDING.remove(site);
		markDirty(world);
		try {
			DungeonGenerationJob job = DungeonGenerationJob.plan(world, site);
			if (job == null) {
				return;
			}
			/*
			 * with nobody online (ie pre-generating with buildWithoutPlayers) a lag spike doesn't matter, and every
			 * chunk the dungeon writes into is loaded right now. build it all at once instead of a few rooms per tick,
			 * which could need the chunks reloaded from disk after the pre-generator moves on.
			 */
			if (isServerEmpty(world)) {
				job.finish();
				job.complete();
			}
			else {
				ACTIVE.add(job);
			}
		}
		catch(Exception e) {
			Dungeons2.log.error("error planning dungeon @ " + site.toShortString(), e);
		}
	}

	/**
	 * Runs the jobs whose chunks are loaded, sharing the per-tick budget between them.
	 * @param world
	 * @return the number of jobs that are running (not paused)
	 */
	private static int runActiveJobs(World world) {
		if (ACTIVE.isEmpty()) {
			return 0;
		}

		List<DungeonGenerationJob> ready = new ArrayList<>();
		for (DungeonGenerationJob job : ACTIVE) {
			/*
			 * a job pauses (doesn't write) while any of its chunks are unloaded.
			 * with buildWithoutPlayers (ie pre-generation) nobody may come back to load them, so keep writing instead:
			 * the chunks were all generated when the job was planned, so this only loads them from disk.
			 */
			if (ModConfig.buildWithoutPlayers || job.isAreaReady()) {
				ready.add(job);
			}
		}
		if (ready.isEmpty()) {
			return 0;
		}

		long budgetNanos = ModConfig.generationMsPerTick * 1_000_000L / ready.size();
		for (DungeonGenerationJob job : ready) {
			if (job.run(budgetNanos)) {
				job.complete();
				ACTIVE.remove(job);
				markDirty(world);
			}
		}
		return ready.size();
	}

	/**
	 * Only pending sites near online players are considered, so a large (ie pre-generated) queue stays cheap.
	 * @param world
	 * @return the ready site nearest to any player, or null
	 */
	private static ICoords findSiteNearestPlayer(World world) {
		if (world.playerEntities.isEmpty()) {
			return null;
		}

		ICoords nearest = null;
		double nearestDistSq = Double.MAX_VALUE;
		for (ICoords site : PENDING) {
			double distSq = getNearestPlayerDistSq(world, site);
			if (distSq > PLAYER_RANGE_SQ || distSq >= nearestDistSq) {
				continue;
			}
			if (ChunkUtil.isAreaReady(world, site.toPos(), DungeonGenerationJob.PLAN_RADIUS)) {
				nearest = site;
				nearestDistSq = distSq;
			}
		}
		return nearest;
	}

	/**
	 *
	 * @param world
	 * @param site
	 * @return the x/z distance squared from the site to the nearest player
	 */
	private static double getNearestPlayerDistSq(World world, ICoords site) {
		double nearest = Double.MAX_VALUE;
		for (EntityPlayer player : world.playerEntities) {
			if (player.isSpectator()) {
				continue;
			}
			double dx = player.posX - (site.getX() + 0.5D);
			double dz = player.posZ - (site.getZ() + 0.5D);
			nearest = Math.min(nearest, dx * dx + dz * dz);
		}
		return nearest;
	}

	/**
	 * For ModConfig.buildWithoutPlayers. Checking a site whose chunks aren't loaded stops at the first chunk,
	 * so scanning a large queue is cheap.
	 * @param world
	 * @return the first site whose chunks are all loaded and populated, or null
	 */
	private static ICoords findReadySite(World world) {
		for (ICoords site : PENDING) {
			if (ChunkUtil.isAreaReady(world, site.toPos(), DungeonGenerationJob.PLAN_RADIUS)) {
				return site;
			}
		}
		return null;
	}

	/**
	 * Finishes every dungeon in progress, paused ones included. Called when the server is stopping, before the
	 * world is saved. A dungeon's chunks were all generated when it was planned, so writing into them only loads
	 * them from disk.
	 * @param world
	 */
	public static synchronized void finishActiveJobs(World world) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<DungeonGenerationJob> iterator = ACTIVE.iterator();
		while (iterator.hasNext()) {
			DungeonGenerationJob job = iterator.next();
			Dungeons2.log.debug("finishing dungeon @ {} ({} steps) before the server stops", job.getSite().toShortString(), job.getRemainingSteps());
			job.finish();
			job.complete();
			iterator.remove();
		}
		markDirty(world);
	}

	/**
	 *
	 * @param world
	 * @return true if no players are online in any dimension
	 */
	private static boolean isServerEmpty(World world) {
		MinecraftServer server = world.getMinecraftServer();
		return server != null && server.getCurrentPlayerCount() == 0;
	}

	private static void markDirty(World world) {
		DungeonsGenSavedData savedData = DungeonsGenSavedData.get(world);
		if (savedData != null) {
			savedData.markDirty();
		}
	}

	/**
	 *
	 * @return
	 */
	public static synchronized NBTTagCompound save() {
		NBTTagCompound tag = new NBTTagCompound();
		NBTTagList sitesTag = new NBTTagList();
		for (ICoords site : PENDING) {
			NBTTagCompound siteTag = new NBTTagCompound();
			siteTag.setInteger("x", site.getX());
			siteTag.setInteger("y", site.getY());
			siteTag.setInteger("z", site.getZ());
			sitesTag.appendTag(siteTag);
		}
		tag.setTag(SITES_KEY, sitesTag);
		return tag;
	}

	/**
	 *
	 * @param tag
	 */
	public static synchronized void load(NBTTagCompound tag) {
		PENDING.clear();
		NBTTagList sitesTag = tag.getTagList(SITES_KEY, 10);
		for (int i = 0; i < sitesTag.tagCount(); i++) {
			NBTTagCompound siteTag = sitesTag.getCompoundTagAt(i);
			PENDING.add(new Coords(siteTag.getInteger("x"), siteTag.getInteger("y"), siteTag.getInteger("z")));
		}
	}
}
