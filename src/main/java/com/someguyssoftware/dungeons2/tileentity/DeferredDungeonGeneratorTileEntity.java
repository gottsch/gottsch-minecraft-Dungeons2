package com.someguyssoftware.dungeons2.tileentity;

import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.gottschcore.GottschCore;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.tileentity.AbstractModTileEntity;
import com.someguyssoftware.gottschcore.tileentity.IProximityTileEntity;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ITickable;
import net.minecraft.world.World;

public class DeferredDungeonGeneratorTileEntity extends AbstractModTileEntity implements ITickable{

	public DeferredDungeonGeneratorTileEntity() {
		
	}
	
	@Override
	public void update() {
        if (WorldInfo.isClientSide()) {
        	return;
        }      
        
    	// get all players within range
        EntityPlayer player = null;
   
        // TODO determine the size of dungeon to create
        // TODO get the field size - or should it always be the largest size?
        
        // TODO check the four corners of the dungeon field size to determine if the chunks are loaded
        // TODO square the raidus of the dungeon field
        
        // TODO check for any players within distance
        double proximitySq = (96/2) * (96/2);

        
        // for each player
        for (int playerIndex = 0; playerIndex < getWorld().playerEntities.size(); ++playerIndex) {
            player = (EntityPlayer)getWorld().playerEntities.get(playerIndex);
            // get the distance
            double distanceSq = player.getDistanceSq(this.getPos().add(0.5D, 0.5D, 0.5D));
            Dungeons2.log.debug("deferred dungeon block ticking when player is -> {} blocks away", Math.sqrt(distanceSq));
           
            if (distanceSq < proximitySq) {
            	// TODO generate dungeon
            	
            	// TODO self-destruct is not done already
            }

        }
	}
}
