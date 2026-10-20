/**
 * 
 */
package com.someguyssoftware.dungeons2.eventhandler;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.cache.FeatureCaches;
import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;
import com.someguyssoftware.dungeons2.worldgen.DungeonGenQueue;
import com.someguyssoftware.gottschcore.loot.LootTableMaster;
import com.someguyssoftware.gottschcore.mod.IMod;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 
 * @author Mark Gottschling on Jan 5, 2017
 *
 */
public class WorldEventHandler {
	// reference to the mod.
	private IMod mod;
	
	/**
	 * 
	 * @param mod
	 */
	public WorldEventHandler(IMod mod) {
		setMod(mod);
	}
	
	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		if (WorldInfo.isServerSide(event.getWorld())/*!event.getWorld().isRemote*/) {
			
			// only fire for overworld since dungeons only exist there (for now :))
			if (event.getWorld().provider.getDimension() == 0) {
				// the cache is static, so clear the previous world's entries (single player) before loading.
				// readFromNBT is not called for a new world, which would otherwise inherit them.
				FeatureCaches.clear();
				FeatureCaches.CACHE.setDelayCount(0);
				DungeonGenQueue.clear();
				// load world gen data
				DungeonsGenSavedData genData = DungeonsGenSavedData.get(event.getWorld());
				WorldServer world = (WorldServer) event.getWorld();
				Dungeons2.LOOT_TABLES.init(world);
				Dungeons2.LOOT_TABLES.register(getMod().getId());
			}
		}
	}

	/**
	 * Generates queued dungeons, a few rooms per tick.
	 * @param event
	 */
	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.world.isRemote || event.world.provider.getDimension() != 0) {
			return;
		}
		DungeonGenQueue.tick(event.world);
	}

	/**
	 * @return the mod
	 */
	public IMod getMod() {
		return mod;
	}

	/**
	 * @param mod the mod to set
	 */
	public void setMod(IMod mod) {
		this.mod = mod;
	}
}
