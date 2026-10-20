package com.someguyssoftware.dungeons2.tileentity;

import com.someguyssoftware.dungeons2.block.DungeonsBlocks;
import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;
import com.someguyssoftware.dungeons2.worldgen.DungeonGenQueue;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.tileentity.AbstractModTileEntity;

import net.minecraft.util.ITickable;

/**
 * Legacy. Dungeons are now queued in DungeonGenQueue during chunk population instead of placing this block.
 * Any deferred generator block already in a world hands its site to the queue and removes itself.
 */
public class DeferredDungeonGeneratorTileEntity extends AbstractModTileEntity implements ITickable{

	public DeferredDungeonGeneratorTileEntity() {
	}

	@Override
	public void update() {
		if (getWorld() == null || getWorld().isRemote) {
			return;
		}

		DungeonGenQueue.add(new Coords(getPos()));
		DungeonsGenSavedData savedData = DungeonsGenSavedData.get(getWorld());
		if (savedData != null) {
			savedData.markDirty();
		}

		if (getWorld().getBlockState(getPos()).getBlock() == DungeonsBlocks.DEFERRED_DUNGEON_GENERATOR) {
			getWorld().setBlockToAir(getPos());
		}
		getWorld().removeTileEntity(getPos());
	}
}
