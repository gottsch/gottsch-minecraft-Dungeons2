package com.someguyssoftware.dungeons2.tileentity;

import java.util.List;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.block.DungeonsBlocks;
import com.someguyssoftware.dungeons2.builder.DungeonBuilderTopDown;
import com.someguyssoftware.dungeons2.builder.IDungeonBuilder;
import com.someguyssoftware.dungeons2.builder.LevelBuilder;
import com.someguyssoftware.dungeons2.cache.DelayedFeatureSimpleDistanceCache;
import com.someguyssoftware.dungeons2.cache.FeatureCaches;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonInfo;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeons2.world.ChunkUtil;
import com.someguyssoftware.dungeons2.worldgen.DungeonsWorldGen;
import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.tileentity.AbstractModTileEntity;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.util.ITickable;
import net.minecraft.world.biome.Biome;

public class DeferredDungeonGeneratorTileEntity extends AbstractModTileEntity implements ITickable{

	private static final int FIELD_SIZE = 96;
	private static final int FIELD_RADIUS = FIELD_SIZE / 2;
	// field radius + a chunk of margin for walls, hallways that touch the boundary and neighbor updates
	private static final int GEN_RADIUS = FIELD_RADIUS + 16;
	private static final double THRESHOLD = 256D;
	// only evaluate once a second instead of every tick
	private static final long TICK_INTERVAL = 20L;

	public DeferredDungeonGeneratorTileEntity() {
	}

	@Override
	public void update() {
		if (getWorld() == null || getWorld().isRemote) {
			return;
		}

		// stagger the checks so many deferred blocks don't all evaluate on the same tick
		if (Math.floorMod(getWorld().getTotalWorldTime() + getPos().hashCode(), TICK_INTERVAL) != 0) {
			return;
		}

		ICoords coords = new Coords(getPos());

		/*
		 * every chunk the dungeon field can touch must already be loaded AND populated.
		 * NOTE getChunkFromBlockCoords().isLoaded() loads/generates the chunk, so it can't be used as a test.
		 * writing into unloaded chunks forces them to generate (lag spikes), and writing into unpopulated chunks
		 * lets their later decoration (trees, lakes, other mods) stamp over the dungeon.
		 */
		if (!ChunkUtil.isAreaReady(getWorld(), getPos(), GEN_RADIUS)) {
			return;
		}

		// any player within the threshold. (the old loop returned on the first player out of range)
		if (!getWorld().isAnyPlayerWithinRangeAt(getPos().getX() + 0.5D, getPos().getY() + 0.5D, getPos().getZ() + 0.5D, THRESHOLD)) {
			return;
		}

		/*
		 * an exception thrown from a tile entity tick crashes the server, and since this tile entity is saved with
		 * the chunk, the world would crash again on every load. always self-destruct instead.
		 */
		try {
			generate(coords);
		}
		catch(Exception e) {
			Dungeons2.log.error("error generating dungeon @ " + coords.toShortString(), e);
		}
		selfDestruct();
	}

	/**
	 *
	 * @param coords
	 * @throws Exception
	 */
	private void generate(ICoords coords) throws Exception {
		// get land coords - need this so as not to replace something with nothing
		ICoords spawnCoords = WorldInfo.getDryLandSurfaceCoords(world, coords);
		if (spawnCoords == null || spawnCoords == WorldInfo.EMPTY_COORDS) {
			Dungeons2.log.debug("no dry land surface for deferred dungeon @ {}", coords.toShortString());
			return;
		}

		// get the biome ID
		Biome biome = world.getBiome(coords.toPos());
		Integer biomeID = Biome.getIdForBiome(biome);
		Dungeons2.log.debug("biome ID -> {}", biomeID);

		// get the dungeons for this biome
		List<IDungeonConfig> dungeonConfigs = Dungeons2.CONFIG_MANAGER.getByBiome(biomeID);
		// select one
		if (dungeonConfigs == null || dungeonConfigs.isEmpty()) {
			Dungeons2.log.debug("could not find any dungeon configs for biomeID -> {}", biomeID);
			return;
		}
		IDungeonConfig dungeonConfig = dungeonConfigs.get(world.rand.nextInt(dungeonConfigs.size()));
		Dungeons2.log.debug("selected dungeon config -> {}", dungeonConfig);

		DungeonsWorldGen worldGenerator = Dungeons2.dungeonsWorldGen;
		if (worldGenerator == null || worldGenerator.getGenerator() == null) {
			Dungeons2.log.warn("dungeons world generator is not initialized.");
			return;
		}
		StyleSheet styleSheet = worldGenerator.getStyleSheet();
		if (styleSheet == null || styleSheet.getThemes() == null || styleSheet.getThemes().isEmpty()) {
			Dungeons2.log.warn("style sheet has no themes. unable to generate dungeon.");
			return;
		}
		Theme theme = styleSheet.getThemes()
				.get(styleSheet.getThemes().keySet()
						.toArray()[world.rand.nextInt(styleSheet.getThemes().size())]);

		// get the level builder
		LevelBuilder levelBuilder = new LevelBuilder();

		// create a dungeon builder using the defined level builder(s)
		IDungeonBuilder builder = new DungeonBuilderTopDown(levelBuilder);

		// build the dungeon
		Dungeons2.log.debug("starting BUILD process...");
		Dungeon dungeon = builder.build(world, world.rand, coords, dungeonConfig);
		Dungeons2.log.debug("BUILD process complete.");

		// NOTE EMPTY_DUNGEON is a shared instance - don't modify it (ie setTheme)
		if (dungeon == null || dungeon == IDungeonBuilder.EMPTY_DUNGEON) {
			return;
		}

		// update the dungeon with the theme
		dungeon.setTheme(theme);

		// the builder only validates the rooms, so confirm the full extent of the dungeon is ready before writing any blocks
		if (!isDungeonAreaReady(dungeon)) {
			Dungeons2.log.debug("dungeon extends into chunks that are not loaded/populated @ {}", coords.toShortString());
			return;
		}

		// generate the dungeon
		Dungeons2.log.debug("Start GENERATE process...");
		boolean isGenerated = worldGenerator.getGenerator().generate(world, world.rand, dungeon, styleSheet, /*chestSheet*/null, worldGenerator.getSpawnSheet());
		Dungeons2.log.debug("GENERATE process complete.");
		if (!isGenerated) {
			return;
		}

		// update the cache.
		// NOTE the cached intervals are at y = 0 (the interval tree also tests y), so search at y = 0
		DelayedFeatureSimpleDistanceCache<DungeonInfo> cache = FeatureCaches.CACHE;
		ICoords cacheCoords = coords.withY(0);
		List<DungeonInfo> infos = cache.getData(cacheCoords.add(-1, 0, -1), cacheCoords.add(1, 0, 1));
		DungeonInfo info;
		if (infos == null || infos.isEmpty()) {
			info = new DungeonInfo(dungeon);
			Dungeons2.log.info("generated info -> {}", info);
			cache.cache(info.getCoords(), info);
		} else {
			// update the placeholder
			info = infos.get(0);
			info.setCoords(dungeon.getEntrance().getBottomCenter());
			info.setLevels(dungeon.getLevels().size());
			info.setThemeName(theme.getName());
			if (dungeon.getMinX() != null) {
				info.setMinX(dungeon.getMinX());
				info.setMaxX(dungeon.getMaxX());
				info.setMinY(dungeon.getMinY());
				info.setMaxY(dungeon.getMaxY());
				info.setMinZ(dungeon.getMinZ());
				info.setMaxZ(dungeon.getMaxZ());
			}
			Dungeons2.log.info("updated info -> {}", info);
		}

		Dungeons2.log.info("dungeon generated -> {}", coords.toShortString());

		// save world data
		DungeonsGenSavedData savedData = DungeonsGenSavedData.get(world);
		if (savedData != null) {
			savedData.markDirty();
		}
	}

	/**
	 *
	 * @param dungeon
	 * @return
	 */
	private boolean isDungeonAreaReady(Dungeon dungeon) {
		Room entrance = dungeon.getEntrance();
		int minX = entrance.getMinX();
		int minZ = entrance.getMinZ();
		int maxX = entrance.getMaxX();
		int maxZ = entrance.getMaxZ();
		if (dungeon.getMinX() != null) {
			minX = Math.min(minX, dungeon.getMinX());
			minZ = Math.min(minZ, dungeon.getMinZ());
			maxX = Math.max(maxX, dungeon.getMaxX());
			maxZ = Math.max(maxZ, dungeon.getMaxZ());
		}
		// +1 block for neighbor updates along the outer walls
		return ChunkUtil.isAreaReady(world, minX - 1, minZ - 1, maxX + 1, maxZ + 1);
	}

	private void selfDestruct() {
		if (getWorld().getBlockState(getPos()).getBlock() == DungeonsBlocks.DEFERRED_DUNGEON_GENERATOR) {
			getWorld().setBlockToAir(getPos());
		}
		getWorld().removeTileEntity(getPos());
	}
}
