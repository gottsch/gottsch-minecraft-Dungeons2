/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import java.util.Random;

import com.someguyssoftware.dungeons2.generator.strategy.IRoomGenerationStrategy;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;

import net.minecraft.world.World;

/**
 * @author Mark Gottschling on Jul 30, 2016
 *
 */
public interface IRoomGenerator {

	/**
	 * @param world
	 * @param coords
	 * @param room
	 * @param layout
	 * @param styleSheet
	 */
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet, LevelConfig config);
	
	public IRoomGenerationStrategy getGenerationStrategy();
}