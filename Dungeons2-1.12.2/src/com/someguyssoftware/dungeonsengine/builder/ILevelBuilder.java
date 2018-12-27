package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;

import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.dungeonsengine.config.LevelConfig;
import com.someguyssoftware.dungeonsengine.model.ILevel;
import com.someguyssoftware.dungeonsengine.model.ISpace;

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

	LevelConfig getConfig();
	void withConfig(ILevelConfig config);
//	void setConfig(LevelConfig config);

	ISpaceBuilder getSpaceBuilder();
	void setSpaceBuilder(ISpaceBuilder builder);

	List<ISpace> getPlannedSpaces();

	ILevelBuilder withSpace(ISpace r);

	void reset();

//	IShaft join(ISpace sourceSpace, ISpace destSpace);
//	IShaft join(ILevel sourceLevel, ILevel destLevel);
}