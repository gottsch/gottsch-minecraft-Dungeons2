/**
 * 
 */
package com.someguyssoftware.dungeons2.persistence;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.cache.FeatureCaches;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

/**
 * 
 * @author Mark Gottschling on Oct 7, 2015
 *
 */
public class DungeonsGenSavedData extends WorldSavedData {
	public static final String DUNGEONS_GEN_DATA_KEY = "dungeons2GenData";
	
	/**
	 * Empty constructor
	 */
	public DungeonsGenSavedData() {
		super(DUNGEONS_GEN_DATA_KEY);
	}
	
	/**
	 * 
	 * @param key
	 */
	public DungeonsGenSavedData(String key) {
		super(key);
	}

	/* (non-Javadoc)
	 * @see net.minecraft.world.WorldSavedData#readFromNBT(net.minecraft.nbt.NBTTagCompound)
	 */
	@Override
	public void readFromNBT(NBTTagCompound tag) {
		Dungeons2.log.debug("loading Dungeons2! saved gen data...");

		NBTTagCompound cache = tag.getCompoundTag("cache");
		FeatureCaches.load(cache);
		
		// dungeons
//		NBTTagCompound dungeonGen = tag.getCompoundTag("dungeonGenerator");
//		Dungeons2.dungeonsWorldGen.setChunksSinceLastDungeon(dungeonGen.getInteger("chunksSinceLastDungeon"));

//		NBTTagCompound pos = tag.getCompoundTag("lastDungeonBlockPos");		
//		// retrieve data from NBT
//		int x = pos.getInteger("x");
//		int y = pos.getInteger("y");
//		int z = pos.getInteger("z");
//		Dungeons2.dungeonsWorldGen.setLastDungeonCoords(new Coords(x, y, z));
		
//		NBTTagList registryTagList = tag.getTagList("registry", 10);
		
//		// process each meta in the list
//		for (int i = 0; i < registryTagList.tagCount(); i++) {
//			NBTTagCompound infoTag = registryTagList.getCompoundTagAt(i);
//			
//			// retrieve data from NBT
//			int dx = infoTag.getInteger("x");
//			int dy = infoTag.getInteger("y");
//			int dz = infoTag.getInteger("z");
//			int minX = infoTag.getInteger("minX");
//			int minY = infoTag.getInteger("minY");
//			int minZ = infoTag.getInteger("minZ");
//			int maxX = infoTag.getInteger("maxX");
//			int maxY = infoTag.getInteger("maxY");
//			int maxZ = infoTag.getInteger("maxZ");			
//			int levels = infoTag.getInteger("levels");
//			String theme = infoTag.getString("theme");
//			String pattern = infoTag.getString("pattern");
//			String size = infoTag.getString("size");
//			String levelSize = infoTag.getString("levelSize");
//			String direction = infoTag.getString("direction");
//			int bossX = infoTag.getInteger("bossChestX");
//			int bossY = infoTag.getInteger("bossChestY");
//			int bossZ = infoTag.getInteger("bossChestZ");
//			
//			try {		
//				// create a meta
//				DungeonInfo info = new DungeonInfo();
//				info.setCoords(new Coords(dx, dy, dz));
//				info.setMinX(minX);
//				info.setMinY(minY);
//				info.setMinZ(minZ);
//				info.setMaxX(maxX);
//				info.setMaxY(maxY);
//				info.setMaxZ(maxZ);
//				info.setLevels(levels);
//				info.setThemeName(theme);
//				if (!pattern.equals("")) info.setPattern(BuildPattern.valueOf(pattern));
//				if (!size.equals("")) info.setSize(BuildSize.valueOf(size));
//				if (!levelSize.equals("")) info.setLevelSize(BuildSize.valueOf(levelSize));
//				if (!direction.equals("")) info.setDirection(BuildDirection.valueOf(direction));
//				info.setBossChestCoords(new Coords(bossX, bossY, bossZ));
//			
//				// register the meta
//				DungeonRegistry.getInstance().register(info.getCoords().toShortString(), info);
//			}
//			catch(Exception e) {
//				Dungeons2.log.error(e);					
//			}
//		}
	}

	/*
	 * NOTE thrown exceptions are silently handled, so they need to be caught here instead
	 *  (non-Javadoc)
	 * @see net.minecraft.world.WorldSavedData#writeToNBT(net.minecraft.nbt.NBTTagCompound)
	 */
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {

		try {
			tag.setTag("cache", FeatureCaches.save());
			// dungeon generator
//			NBTTagCompound dungeonGen = new NBTTagCompound();
//			dungeonGen.setInteger("chunksSinceLastDungeon", Dungeons2.dungeonsWorldGen.getChunksSinceLastDungeon());
//			tag.setTag("dungeonGenerator", dungeonGen);
//			
//			NBTTagCompound pos = new NBTTagCompound();		
//			if (Dungeons2.dungeonsWorldGen.getLastDungeonCoords() != null) {
//				pos.setInteger("x", Dungeons2.dungeonsWorldGen.getLastDungeonCoords().getX());
//				pos.setInteger("y", Dungeons2.dungeonsWorldGen.getLastDungeonCoords().getY());
//				pos.setInteger("z", Dungeons2.dungeonsWorldGen.getLastDungeonCoords().getZ());
//				tag.setTag("lastDungeonBlockPos", pos);			
//			}
//			
//			// write the Dungeon Registry to NBT
//			NBTTagList registryTagList = new NBTTagList();
//			for (DungeonInfo info : DungeonRegistry.getInstance().getEntries()) {
//				NBTTagCompound infoTag = new NBTTagCompound();
//
//				if (info != null) {
//					if (info.getCoords() != null) {
//						infoTag.setInteger("x", info.getCoords().getX());
//						infoTag.setInteger("y", info.getCoords().getY());
//						infoTag.setInteger("z", info.getCoords().getZ());
//					}
//					infoTag.setInteger("minX", info.getMinX());
//					infoTag.setInteger("minY", info.getMinY());
//					infoTag.setInteger("minZ", info.getMinZ());
//					infoTag.setInteger("maxX", info.getMaxX());
//					infoTag.setInteger("maxY", info.getMaxY());
//					infoTag.setInteger("maxZ", info.getMaxZ());
//					
//					infoTag.setInteger("levels", info.getLevels());
//					if (info.getThemeName() != null) {
//						infoTag.setString("theme", info.getThemeName());
//					}
//					if (info.getPattern() != null) {
//						infoTag.setString("pattern", info.getPattern().name());
//					}
//					if (info.getSize() != null) {
//						infoTag.setString("size", info.getSize().name());
//					}
//					if (info.getLevelSize() != null) {
//						infoTag.setString("levelSize", info.getLevelSize().name());
//					}
//					if (info.getDirection() != null) {
//						infoTag.setString("direction", info.getDirection().name());
//					}
//					if (info.getBossChestCoords() != null) {
//						infoTag.setInteger("bossChestX", info.getBossChestCoords().getX());
//						infoTag.setInteger("bossChestY", info.getBossChestCoords().getY());
//						infoTag.setInteger("bossChestZ", info.getBossChestCoords().getZ());
//					}
//
//				}
//				
//				// add the poi to the list
//				registryTagList.appendTag(infoTag);
//			}
//			
//			// add the poi regsitry to the main tag
//			tag.setTag("registry", registryTagList);	
		} catch(Exception e) {
			e.printStackTrace();
			Dungeons2.log.error("An exception occurred:", e);
		}
		
		return tag;
	}

	/**
	 * NOTE world.loadItemData is cached to a HashMap, so you don't have to worry about performing too many get()s that read from the disk.
	 * @param world
	 * @return
	 */
	public static DungeonsGenSavedData get(World world) {
		
		DungeonsGenSavedData data = (DungeonsGenSavedData)world.loadData(DungeonsGenSavedData.class, DUNGEONS_GEN_DATA_KEY);
		
		if (data == null) {
			data = new DungeonsGenSavedData();
			world.setData(DUNGEONS_GEN_DATA_KEY, data);
		}
		return data;
	}
}
