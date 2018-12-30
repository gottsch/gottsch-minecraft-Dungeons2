/**
 * 
 */
package com.someguyssoftware.dungeons2.generator;

import java.util.Random;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.generator.strategy.IRoomGenerationStrategy;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;

import net.minecraft.world.World;

/**
 * 
 * @author Mark Gottschling on Aug 30, 2016
 *
 */
public class BossRoomGenerator extends AbstractRoomGenerator {

	private IRoomGenerationStrategy roomGenerationStrategy;
	
	/**
	 * Enforce that the room generator has to have a structure generator.
	 * @param generator
	 */
	public BossRoomGenerator(IRoomGenerationStrategy generator) {
		setGenerationStrategy(generator);
	}
	
	@Override
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet,
			ILevelConfig config) {
		Dungeons2.log.debug("In Boss Room generator using strat -> {}", getGenerationStrategy().getClass().getSimpleName());
		// generate the room structure
		getGenerationStrategy().generate(world, random, room, theme, styleSheet, config);
	}
	
	@Deprecated
	@Override
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet,
			LevelConfig config) {
		
		// generate the room structure
		getGenerationStrategy().generate(world, random, room, theme, styleSheet, config);

	}

	/**
	 * @return the roomGenerationStrategy
	 */
	public IRoomGenerationStrategy getGenerationStrategy() {
		return roomGenerationStrategy;
	}

	/**
	 * @param roomGenerationStrategy the roomGenerationStrategy to set
	 */
	public void setGenerationStrategy(IRoomGenerationStrategy roomGenerationStrategy) {
		this.roomGenerationStrategy = roomGenerationStrategy;
	}

}
