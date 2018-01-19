/**
 * 
 */
package com.someguyssoftware.dungeons2.generator.strategy;

import java.util.List;

import com.someguyssoftware.dungeons2.model.Room;

/**
 * @author Mark Gottschling on Aug 30, 2016
 *
 */
public interface IHallwayGenerationStrategy extends IRoomGenerationStrategy {
	public List<Room> getHallways();
	public void setHallways(List<Room> hallways);
}
