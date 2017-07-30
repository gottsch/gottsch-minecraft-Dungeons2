package com.someguyssoftware.dungeons2.generator.strategy;

import java.util.Random;

import com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.dungeons2.style.StyleSheet;
import com.someguyssoftware.dungeons2.style.Theme;

import net.minecraft.world.World;

public interface IRoomGenerationStrategy {

	/**
	 * 
	 * @param world
	 * @param random
	 * @param room
	 * @param theme
	 * @param styleSheet
	 * @param config
	 */
	public void generate(World world, Random random, Room room, Theme theme, StyleSheet styleSheet, LevelConfig config);

	
	public IDungeonsBlockProvider getBlockProvider();
//	public void setBlockProvider(IDungeonsBlockProvider blockProvider);

	
	
}