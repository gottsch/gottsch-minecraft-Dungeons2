package com.someguyssoftware.dungeonsengine.builder;

import java.util.List;

import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.dungeonsengine.config.LevelConfig;
import com.someguyssoftware.dungeonsengine.model.ILevel;
import com.someguyssoftware.dungeonsengine.model.IRoom;

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

	IRoomBuilder getRoomBuilder();
//	void setVoidBuilder(IRoomBuilder builder);
	ILevelBuilder with(IRoomBuilder builder);
	
	List<IRoom> getPlannedRooms();
	
	/**
	 * Adds a void space to the planned voids
	 * @param room
	 */
	ILevelBuilder with(IRoom room);
	
	void reset();

//	IShaft join(IRoom sourceVoid, IRoom destVoid);
//	IShaft join(ILevel sourceLevel, ILevel destLevel);
}