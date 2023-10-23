package com.someguyssoftware.dungeons2.tileentity;

import java.io.FileNotFoundException;
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
import com.someguyssoftware.dungeons2.persistence.DungeonsGenSavedData;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeons2.worldgen.DungeonsWorldGen;
import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.tileentity.AbstractModTileEntity;
import com.someguyssoftware.gottschcore.world.WorldInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ITickable;
import net.minecraft.world.biome.Biome;

public class DeferredDungeonGeneratorTileEntity extends AbstractModTileEntity implements ITickable{

	private static final int FIELD_SIZE = 96;
	private static final int FIELD_RADIUS = FIELD_SIZE / 2;

	public DeferredDungeonGeneratorTileEntity() {
		Dungeons2.log.debug("created deferred dungeon generator tile entity");
	}
	
	@Override
	public void update() {
        if (WorldInfo.isClientSide()) {
        	return;
        }      
   
        // TODO determine the size of dungeon to create
        // TODO get the field size - or should it always be the largest size?
        
        // check the four corners of the dungeon field size to determine if the chunks are loaded
        ICoords coords = new Coords(getPos());
        ICoords c1 = coords.add(FIELD_RADIUS, 0, FIELD_RADIUS);
        ICoords c2 = coords.add(FIELD_RADIUS, 0, -FIELD_RADIUS);
        ICoords c3 = coords.add(-FIELD_RADIUS, 0, FIELD_RADIUS);
        ICoords c4 = coords.add(-FIELD_RADIUS, 0, -FIELD_RADIUS);
        
        boolean isLoaded = getWorld().getChunkFromBlockCoords(c1.toPos()).isLoaded();
        isLoaded &= getWorld().getChunkFromBlockCoords(c2.toPos()).isLoaded();
        isLoaded &= getWorld().getChunkFromBlockCoords(c3.toPos()).isLoaded();
        isLoaded &= getWorld().getChunkFromBlockCoords(c4.toPos()).isLoaded();
        
        if (!isLoaded) {
        	Dungeons2.log.debug("the field chunks are not entirely loaded.");
        	return;
        }
        
        // square the raidus of the dungeon field        
        double proximitySq = (FIELD_SIZE/2) * (FIELD_SIZE/2);
        
        // TODO check for any players within distance

        // for each player
        for (int playerIndex = 0; playerIndex < getWorld().playerEntities.size(); ++playerIndex) {
            EntityPlayer player = (EntityPlayer)getWorld().playerEntities.get(playerIndex);
            // get the distance
            double distanceSq = player.getDistanceSq(this.getPos().add(0.5D, 0.5D, 0.5D));
            Dungeons2.log.debug("deferred dungeon block ticking when player is -> {} blocks away", Math.sqrt(distanceSq));
           
            if (distanceSq < proximitySq) {
            	// TODO generate dungeon
            	
				// get the biome ID
				Biome biome = world.getBiome(coords.toPos());
				Integer biomeID = Biome.getIdForBiome(biome);
				Dungeons2.log.debug("biome ID -> {}", biomeID);
            	
			    // get the dungeons for this biome
			    List<IDungeonConfig> dungeonConfigs = Dungeons2.CONFIG_MANAGER.getByBiome(biomeID);
			    // select one
			    if (dungeonConfigs == null || dungeonConfigs.size() == 0) {
			    	Dungeons2.log.debug("could not find any dungeon configs for biomeID -> {}", biomeID);
			    	selfDestruct();
			    	return;
			    }
			    IDungeonConfig dc = dungeonConfigs.get(world.rand.nextInt(dungeonConfigs.size()));
			    Dungeons2.log.debug("selected dungeon config -> {}", dc);
				
			    DungeonsWorldGen worldGenerator = Dungeons2.dungeonsWorldGen;
			    Theme theme = worldGenerator.getStyleSheet().getThemes()
			    		.get(worldGenerator.getStyleSheet().getThemes().keySet()
			    				.toArray()[world.rand.nextInt(worldGenerator.getStyleSheet().getThemes().size())]);
	   			
			    // get the level builder
				LevelBuilder levelBuilder = new LevelBuilder();
			    
				// create a dungeon builder using the defined level builder(s)
				IDungeonBuilder builder = new DungeonBuilderTopDown(levelBuilder);	
				
				// build the dungeon
				Dungeons2.log.debug("starting BUILD process...");
				Dungeon dungeon = builder.build(world, world.rand, coords, dc);
				Dungeons2.log.debug("BUILD process complete.");
				
				// update the dungeon with the theme
	   			dungeon.setTheme(theme);
				
	   			if (dungeon != null && dungeon != IDungeonBuilder.EMPTY_DUNGEON) {
					// generate the dungeon
					try {
						Dungeons2.log.debug("Start GENERATE process...");
						boolean isGenerated = worldGenerator.getGenerator().generate(world, world.rand, dungeon, worldGenerator.getStyleSheet(), /*chestSheet*/null, worldGenerator.getSpawnSheet());
						Dungeons2.log.debug("GENERATE process complete.");
						
						if (!isGenerated) {
							selfDestruct();
							return;
						}
					} catch (FileNotFoundException e) {
						Dungeons2.log.error("error generating dungeon @ " + coords.toShortString(), e);
						selfDestruct();
						return;
					}
					
					// update the cache
					DelayedFeatureSimpleDistanceCache<DungeonInfo> cache = FeatureCaches.CACHE;
					List<DungeonInfo> infos = cache.getData(coords.add(-1, 0, -1), coords.add(1, 0, 1));
					DungeonInfo info;
					if (infos == null || infos.size() == 0) {
						info = new DungeonInfo(dungeon, null, null, null, null);
						cache.cache(info.getCoords(), info);
					} else {
						info = infos.get(0);
						info.setCoords(dungeon.getEntrance().getBottomCenter());
					}
					
					Dungeons2.log.info("dungeon generated -> {}", coords.toShortString());
					
			     	// save world data
					DungeonsGenSavedData savedData = DungeonsGenSavedData.get(world);
			    	if (savedData != null) {
			    		savedData.markDirty();
			    	}
				}
	   				
            	// self-destruct is not done already
            	selfDestruct();
            	
            	// break out of loop
            	break;
            }

        }
	}
	
	private void selfDestruct() {
		if (getWorld().getBlockState(getPos()) == DungeonsBlocks.DEFERRED_DUNGEON_GENERATOR.getDefaultState()) {
			getWorld().setBlockToAir(getPos());
			getWorld().removeTileEntity(getPos());
		}
	}
}
