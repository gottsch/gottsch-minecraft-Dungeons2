/**
 * 
 */
package com.someguyssoftware.dungeons2.eventhandler;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;
import com.someguyssoftware.gottschcore.loot.LootTableMaster;
import com.someguyssoftware.gottschcore.mod.IMod;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
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
				// load world gen data
				DungeonsGenSavedData genData = DungeonsGenSavedData.get(event.getWorld());
				WorldServer world = (WorldServer) event.getWorld();
				Dungeons2.LOOT_TABLES.init(world);
				Dungeons2.LOOT_TABLES.register(getMod().getId());
			}
		}
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
