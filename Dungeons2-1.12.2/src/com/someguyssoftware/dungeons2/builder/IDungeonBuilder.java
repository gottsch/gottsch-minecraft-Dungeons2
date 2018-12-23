/**
 * 
 */
package com.someguyssoftware.dungeons2.builder;

import java.util.Random;

import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeonsengine.config.IDungeonConfig;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Aug 18, 2016
 *
 */
public interface IDungeonBuilder {

	public Dungeon EMPTY_DUNGEON = new Dungeon();

	/**
	 * 
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @param config
	 * @return
	 */
	Dungeon build(World world, Random rand, ICoords startPoint, DungeonConfig config);

	/**
	 * @return the levelBuilder
	 */
	LevelBuilder getLevelBuilder();

	/**
	 * @param levelBuilder the levelBuilder to set
	 */
	void setLevelBuilder(LevelBuilder levelBuilder);

	/**
	 * @param world
	 * @param rand
	 * @param startPoint
	 * @return
	 */
	Room buildEntranceRoom(World world, Random rand, ICoords startPoint);

	DungeonBuilderTopDown withField(AxisAlignedBB field);

	IDungeonConfig getConfig();
	
	

}