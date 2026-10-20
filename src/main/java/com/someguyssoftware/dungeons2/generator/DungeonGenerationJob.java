/**
 *
 */
package com.someguyssoftware.dungeons2.generator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.builder.DungeonBuilderTopDown;
import com.someguyssoftware.dungeons2.builder.IDungeonBuilder;
import com.someguyssoftware.dungeons2.builder.LevelBuilder;
import com.someguyssoftware.dungeons2.cache.DelayedFeatureSimpleDistanceCache;
import com.someguyssoftware.dungeons2.cache.FeatureCaches;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonInfo;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeons2.world.ChunkUtil;
import com.someguyssoftware.dungeons2.worldgen.DungeonsWorldGen;
import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * A planned dungeon whose blocks are written into the world a few steps (rooms, hallways, shafts) at a time,
 * so that generating a large dungeon is spread over several ticks instead of stalling one.
 *
 * @author Mark Gottschling on 10/19/2026
 */
public class DungeonGenerationJob {
	// the dungeon field size used by DungeonBuilderTopDown
	private static final int FIELD_RADIUS = 96 / 2;
	// field radius + a chunk of margin for walls, hallways that touch the boundary and neighbor updates
	public static final int PLAN_RADIUS = FIELD_RADIUS + 16;

	private final World world;
	private final ICoords site;
	private final Dungeon dungeon;
	private final Deque<Runnable> steps;
	// the x/z extent of the dungeon, +1 block for neighbor updates along the outer walls
	private final int minX, minZ, maxX, maxZ;

	private DungeonGenerationJob(World world, ICoords site, Dungeon dungeon, List<Runnable> steps) {
		this.world = world;
		this.site = site;
		this.dungeon = dungeon;
		this.steps = new ArrayDeque<>(steps);

		Room entrance = dungeon.getEntrance();
		int x1 = entrance.getMinX();
		int z1 = entrance.getMinZ();
		int x2 = entrance.getMaxX();
		int z2 = entrance.getMaxZ();
		if (dungeon.getMinX() != null) {
			x1 = Math.min(x1, dungeon.getMinX());
			z1 = Math.min(z1, dungeon.getMinZ());
			x2 = Math.max(x2, dungeon.getMaxX());
			z2 = Math.max(z2, dungeon.getMaxZ());
		}
		this.minX = x1 - 1;
		this.minZ = z1 - 1;
		this.maxX = x2 + 1;
		this.maxZ = z2 + 1;
	}

	/**
	 * Plans (builds the model of) a dungeon at the site. Reads the world but writes nothing.
	 * The caller must ensure every chunk within PLAN_RADIUS of the site is loaded and populated.
	 * @param world
	 * @param site
	 * @return the job, or null if a dungeon can't be placed at the site
	 * @throws Exception
	 */
	public static DungeonGenerationJob plan(World world, ICoords site) throws Exception {
		// get land coords - need this so as not to replace something with nothing
		ICoords spawnCoords = WorldInfo.getDryLandSurfaceCoords(world, site);
		if (spawnCoords == null || spawnCoords == WorldInfo.EMPTY_COORDS) {
			Dungeons2.log.debug("no dry land surface for dungeon @ {}", site.toShortString());
			return null;
		}

		// get the biome ID
		Biome biome = world.getBiome(site.toPos());
		Integer biomeID = Biome.getIdForBiome(biome);

		// get the dungeons for this biome
		List<IDungeonConfig> dungeonConfigs = Dungeons2.CONFIG_MANAGER.getByBiome(biomeID);
		if (dungeonConfigs == null || dungeonConfigs.isEmpty()) {
			Dungeons2.log.debug("could not find any dungeon configs for biomeID -> {}", biomeID);
			return null;
		}
		// select one
		IDungeonConfig dungeonConfig = dungeonConfigs.get(world.rand.nextInt(dungeonConfigs.size()));
		Dungeons2.log.debug("selected dungeon config -> {}", dungeonConfig);

		DungeonsWorldGen worldGenerator = Dungeons2.dungeonsWorldGen;
		if (worldGenerator == null || worldGenerator.getGenerator() == null) {
			Dungeons2.log.warn("dungeons world generator is not initialized.");
			return null;
		}
		StyleSheet styleSheet = worldGenerator.getStyleSheet();
		if (styleSheet == null || styleSheet.getThemes() == null || styleSheet.getThemes().isEmpty()) {
			Dungeons2.log.warn("style sheet has no themes. unable to generate dungeon.");
			return null;
		}
		Theme theme = styleSheet.getThemes()
				.get(styleSheet.getThemes().keySet()
						.toArray()[world.rand.nextInt(styleSheet.getThemes().size())]);

		// build the dungeon model
		IDungeonBuilder builder = new DungeonBuilderTopDown(new LevelBuilder());
		Dungeon dungeon = builder.build(world, world.rand, site, dungeonConfig);

		// NOTE EMPTY_DUNGEON is a shared instance - don't modify it (ie setTheme)
		if (dungeon == null || dungeon == IDungeonBuilder.EMPTY_DUNGEON) {
			return null;
		}
		dungeon.setTheme(theme);

		List<Runnable> steps = worldGenerator.getGenerator().createSteps(world, world.rand, dungeon, styleSheet, worldGenerator.getSpawnSheet());
		DungeonGenerationJob job = new DungeonGenerationJob(world, site, dungeon, steps);

		// the builder only validates the rooms, so confirm the full extent of the dungeon is ready
		if (!job.isAreaReady()) {
			Dungeons2.log.debug("dungeon extends into chunks that are not loaded/populated @ {}", site.toShortString());
			return null;
		}
		Dungeons2.log.debug("planned dungeon @ {} with {} steps", site.toShortString(), steps.size());
		return job;
	}

	/**
	 *
	 * @return true if every chunk the dungeon writes into is loaded and populated
	 */
	public boolean isAreaReady() {
		return ChunkUtil.isAreaReady(world, minX, minZ, maxX, maxZ);
	}

	/**
	 * Runs steps until the time budget is spent. Always runs at least one step.
	 * @param budgetNanos
	 * @return true if the dungeon is completely generated
	 */
	public boolean run(long budgetNanos) {
		long start = System.nanoTime();
		do {
			Runnable step = steps.poll();
			if (step == null) {
				break;
			}
			runStep(step);
		} while (System.nanoTime() - start < budgetNanos);
		return steps.isEmpty();
	}

	/**
	 * Runs all the remaining steps.
	 */
	public void finish() {
		while (!steps.isEmpty()) {
			runStep(steps.poll());
		}
	}

	/**
	 * A failed room shouldn't stop the rest of the dungeon from generating.
	 * @param step
	 */
	private void runStep(Runnable step) {
		try {
			step.run();
		}
		catch(Exception e) {
			Dungeons2.log.error("error generating a dungeon step @ " + site.toShortString(), e);
		}
	}

	/**
	 * Records the generated dungeon in the feature cache, replacing the site's placeholder.
	 */
	public void complete() {
		DelayedFeatureSimpleDistanceCache<DungeonInfo> cache = FeatureCaches.CACHE;
		// NOTE the cached intervals are at y = 0 (the interval tree also tests y), so search at y = 0
		ICoords cacheCoords = site.withY(0);
		List<DungeonInfo> infos = cache.getData(cacheCoords.add(-1, 0, -1), cacheCoords.add(1, 0, 1));
		DungeonInfo info;
		if (infos == null || infos.isEmpty()) {
			info = new DungeonInfo(dungeon);
			cache.cache(info.getCoords(), info);
		} else {
			// update the placeholder
			info = infos.get(0);
			info.setCoords(dungeon.getEntrance().getBottomCenter());
			info.setLevels(dungeon.getLevels().size());
			info.setThemeName(dungeon.getTheme().getName());
			if (dungeon.getMinX() != null) {
				info.setMinX(dungeon.getMinX());
				info.setMaxX(dungeon.getMaxX());
				info.setMinY(dungeon.getMinY());
				info.setMaxY(dungeon.getMaxY());
				info.setMinZ(dungeon.getMinZ());
				info.setMaxZ(dungeon.getMaxZ());
			}
		}
		Dungeons2.log.info("dungeon generated -> {}", info);
	}

	public ICoords getSite() {
		return site;
	}

	public int getRemainingSteps() {
		return steps.size();
	}
}
