/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import java.util.List;
import java.util.Random;

import com.someguyssoftware.dungeons2.graph.Wayline;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;

import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Aug 6, 2016
 *
 */
public interface IHallwayGenerator {

	/**
	 * 
	 * @param world
	 * @param random
	 * @param wayline
	 * @param rooms
	 * @param hallways
	 * @param theme
	 * @param styleSheet
	 * @param config
	 */
	void generate(World world, Random random, Level level, Wayline wayline, List<Room> hallways, Theme theme,
			StyleSheet styleSheet, LevelConfig config);

}
