/**
 * 
 */
package com.someguyssoftware.dungeons2.eventhandler;

import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;

import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 
 * @author Mark Gottschling on Jan 5, 2017
 *
 */
public class WorldEventHandler {

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		if (!event.getWorld().isRemote) {
			
			// only fire for overworld since dungeons only exist there (for now :))
			if (event.getWorld().provider.getDimension() == 0) {
				// load world gen data
				DungeonsGenSavedData genData = DungeonsGenSavedData.get(event.getWorld());
			}
		}
	}
}
