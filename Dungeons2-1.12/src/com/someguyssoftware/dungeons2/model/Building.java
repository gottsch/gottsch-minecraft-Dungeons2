/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import java.util.LinkedList;
import java.util.List;

import com.someguyssoftware.dungeons2.style.Layout;
import com.someguyssoftware.gottschcore.enums.Direction;

/**
 * @author Mark Gottschling on Sep 4, 2017
 *
 */
public class Building extends Space {
	private Direction direction;
	// entrance to the building
	private AboveRoom entrance;
	// entrance to the dungeon (ladder room or start)
	private AboveRoom start;	
	
	// should have a list of Floors and each floor has rooms?
	// should have a list of Room where each room is a floor (that would be a tower)
	// where does roof fit in? ex. a first level room could have it's own roof, whereas the room have floors above them	
	// Tower extends Building

	/*
	 * styling
	 */
	// default layout for entire building
	private Layout layout;	
	private boolean plinth;
	private boolean cornice;
	private boolean column;
	private boolean window;
	
	/*
	 * Distance ? should that be in here?
	 */
	
	private List<Floor> floors;
	
	/**
	 * 
	 */
	public Building() {}

	public boolean hasPlinth() {
		return plinth;
	}
		
	public boolean hasCornice() {
		return cornice;
	}
	
	
	/**
	 * @return the direction
	 */
	public Direction getDirection() {
		return direction;
	}

	/**
	 * @param direction the direction to set
	 */
	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	/**
	 * @return the floors
	 */
	public List<Floor> getFloors() {
		if (floors == null) floors = new LinkedList<>();
		return floors;
	}

	/**
	 * @param floors the floors to set
	 */
	public void setFloors(List<Floor> floors) {
		this.floors = floors;
	}

	/**
	 * @return the entrance
	 */
	public AboveRoom getEntrance() {
		return entrance;
	}

	/**
	 * @param entrance the entrance to set
	 */
	public void setEntrance(AboveRoom entrance) {
		this.entrance = entrance;
	}

	/**
	 * @return the start
	 */
	public AboveRoom getStart() {
		return start;
	}

	/**
	 * @param start the start to set
	 */
	public void setStart(AboveRoom start) {
		this.start = start;
	}

	/**
	 * @return the layout
	 */
	public Layout getLayout() {
		return layout;
	}

	/**
	 * @param layout the layout to set
	 */
	public void setLayout(Layout layout) {
		this.layout = layout;
	}

	/**
	 * @param plinth the plinth to set
	 */
	public void setPlinth(boolean plinth) {
		this.plinth = plinth;
	}

	/**
	 * @param cornice the cornice to set
	 */
	public void setCornice(boolean cornice) {
		this.cornice = cornice;
	}
	
}
