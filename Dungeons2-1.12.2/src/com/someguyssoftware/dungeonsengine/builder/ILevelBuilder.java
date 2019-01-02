package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;

import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.dungeonsengine.config.LevelConfig;
import com.someguyssoftware.dungeonsengine.model.ILevel;
import com.someguyssoftware.dungeonsengine.model.IVoid;

/**
 * 
 * @author Mark Gottschling on Dec 26, 2018
 *
 */
public interface ILevelBuilder {
//	public static final Shaft EMPTY_SHAFT = new Shaft(); // TODO should this go here or in ISpaceBuilder
	
	/**
	 * 
	 * @return
	 */
	ILevel build();

	ILevelConfig getConfig();
	ILevelBuilder with(ILevelConfig config);
	
//	void setConfig(LevelConfig config);

	IVoidBuilder getVoidBuilder();
//	void setVoidBuilder(IVoidBuilder builder);
	ILevelBuilder with(IVoidBuilder builder);
	
	List<IVoid> getPlannedVoids();
	
	/**
	 * Adds a void space to the planned voids
	 * @param voidSpace
	 */
	ILevelBuilder with(IVoid voidSpace);
	
	void reset();

//	IShaft join(IVoid sourceVoid, IVoid destVoid);
//	IShaft join(ILevel sourceLevel, ILevel destLevel);
}