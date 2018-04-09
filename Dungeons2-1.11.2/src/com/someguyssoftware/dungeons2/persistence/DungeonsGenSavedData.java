/**
 * 
 */
package com.someguyssoftware.dungeons2.persistence;

import com.someguyssoftware.dungeons2.Dungeons2;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

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
		Dungeons2.log.debug("Loading Dungeons2! saved gen data...");

		// dungeons
		NBTTagCompound dungeonGen = tag.getCompoundTag("dungeonGenerator");
		Dungeons2.dungeonsWorldGen.setChunksSinceLastDungeon(dungeonGen.getInteger("chunksSinceLastDungeon"));

		NBTTagCompound pos = tag.getCompoundTag("lastDungeonBlockPos");		
		// retrieve data from NBT
		int x = pos.getInteger("x");
		int y = pos.getInteger("y");
		int z = pos.getInteger("z");
		Dungeons2.dungeonsWorldGen.setLastDungeonBlockPos(new BlockPos(x, y, z));
	}

	/*
	 * NOTE thrown exceptions are silently handled, so they need to be caught here instead
	 *  (non-Javadoc)
	 * @see net.minecraft.world.WorldSavedData#writeToNBT(net.minecraft.nbt.NBTTagCompound)
	 */
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tag) {

		try {
			// dungeon generator
			NBTTagCompound dungeonGen = new NBTTagCompound();
			dungeonGen.setInteger("chunksSinceLastDungeon", Dungeons2.dungeonsWorldGen.getChunksSinceLastDungeon());
			tag.setTag("dungeonGenerator", dungeonGen);
			
			NBTTagCompound pos = new NBTTagCompound();		
			if (Dungeons2.dungeonsWorldGen.getLastDungeonBlockPos() != null) {
				pos.setInteger("x", Dungeons2.dungeonsWorldGen.getLastDungeonBlockPos().getX());
				pos.setInteger("y", Dungeons2.dungeonsWorldGen.getLastDungeonBlockPos().getY());
				pos.setInteger("z", Dungeons2.dungeonsWorldGen.getLastDungeonBlockPos().getZ());
				tag.setTag("lastDungeonBlockPos", pos);			
			}
		}
		catch(Exception e) {
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
		
		DungeonsGenSavedData data = (DungeonsGenSavedData)world.loadItemData(DungeonsGenSavedData.class, DUNGEONS_GEN_DATA_KEY);
		
		if (data == null) {
			data = new DungeonsGenSavedData();
			world.setItemData(DUNGEONS_GEN_DATA_KEY, data);
		}
		return data;
	}
}
