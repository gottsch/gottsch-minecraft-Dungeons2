/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import static com.someguyssoftware.dungeonsengine.enums.RoomTag.*;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.dungeonsengine.enums.RoomTag;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Dec 25, 2018
 *
 */
public class Room extends AbstractSpace implements IRoom {

	private int id;
	private String name;
//	private ICoords coords;

//	private int depth;
//	private int width;
//	private int height;
	
	private Direction direction;	
	List<RoomTag> tags;
	
	/*
	 * the number of edges that a room (vertice) can have when graphing
	 */
	private int degrees;
	
	/*
	 * 
	 */
//	private List<IExit> exits;
	
	/**
	 * 
	 */
	public Room() {}

	/**
	 * 
	 * @param space
	 */
	public Room(IRoom space) {
		if (space != null) {
			setID(space.getID());

		}
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#copy()
	 */
	@Override
	public IRoom copy() {
		return new Room(this);
	}

	/**
	 * 
	 * @param coords
	 */
	@Override
	public void centerOn(ICoords coords) {
		setCoords(
				new Coords(coords.getX()-(getWidth()/2),
						coords.getY(),
						coords.getZ()-(getDepth()/2)));
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getId()
	 */
	@Override
	public int getID() {
		return id;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setId(int)
	 */
	@Override
	public IRoom setID(int id) {
		this.id = id;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setName(java.lang.String)
	 */
	@Override
	public IRoom setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Convenience method
	 * @param width
	 * @param height
	 * @param depth
	 * @return
	 */
	@Override
	public IRoom setDimensions(int width, int height, int depth) {
		setWidth(width);
		setHeight(height);
		setDepth(depth);
		return this;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getCoords()
	 */
//	@Override
//	public ICoords getCoords() {
//		return coords;
//	}
//
//	/* (non-Javadoc)
//	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setCoords(com.someguyssoftware.gottschcore.positional.ICoords)
//	 */
//	@Override
//	public IRoom setCoords(ICoords coords) {
//		this.coords = coords;
//		return this;
//	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDepth()
	 */
//	@Override
//	public int getDepth() {
//		return depth;
//	}
//
//	/* (non-Javadoc)
//	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setDepth(int)
//	 */
//	@Override
//	public IRoom setDepth(int depth) {
//		this.depth = depth;
//		return this;
//	}
//
//	/* (non-Javadoc)
//	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getWidth()
//	 */
//	@Override
//	public int getWidth() {
//		return width;
//	}
//
//	/* (non-Javadoc)
//	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setWidth(int)
//	 */
//	@Override
//	public IRoom setWidth(int width) {
//		this.width = width;
//		return this;
//	}

//	/* (non-Javadoc)
//	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getHeight()
//	 */
//	@Override
//	public int getHeight() {
//		return height;
//	}
//
//	/* (non-Javadoc)
//	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setHeight(int)
//	 */
//	@Override
//	public IRoom setHeight(int height) {
//		this.height = height;
//		return this;
//	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDirection()
	 */
	@Override
	public Direction getDirection() {
		return direction;
	}	

	@Override
	public IRoom setDirection(Direction direction) {
		this.direction = direction;
		return this;
	}


	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDegrees()
	 */
	@Override
	public int getDegrees() {
		return degrees;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setDegrees(int)
	 */
	@Override
	public IRoom setDegrees(int degrees) {
		this.degrees = degrees;
		return this;
	}

	/**
	 * @return the tags
	 */
	@Override
	public List<RoomTag> getTags() {
		if (tags == null) tags = new ArrayList<>();
		return tags;
	}

	/**
	 * @param tags the tags to set
	 */
	@Override
	public void setTags(List<RoomTag> tags) {
		this.tags = tags;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#isAnchor()
	 */
	@Override
	public boolean isAnchor() {
		if (getTags().contains(ANCHOR)) return true;
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setAnchor(boolean)
	 */
	@Override
	public IRoom setAnchor(boolean anchor) {
		if (anchor)
			if (!getTags().contains(ANCHOR)) getTags().add(ANCHOR);
		else {
			if (getTags().contains(ANCHOR)) getTags().remove(ANCHOR);
		}
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#isStart()
	 */
	@Override
	public boolean isStart() {
		if (getTags().contains(START)) return true;
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setStart(boolean)
	 */
	@Override
	public IRoom setStart(boolean start) {
		if (start)
			if (!getTags().contains(START)) getTags().add(START);
		else {
			if (getTags().contains(START)) getTags().remove(START);
		}
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#isEnd()
	 */
	@Override
	public boolean isEnd() {
		if (getTags().contains(END)) return true;
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setEnd(boolean)
	 */
	@Override
	public IRoom setEnd(boolean end) {
		if (end)
			if (!getTags().contains(END)) getTags().add(END);
		else {
			if (getTags().contains(END)) getTags().remove(END);
		}
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#isObstacle()
	 */
	@Override
	public boolean isObstacle() {
		if (getTags().contains(OBSTACLE)) return true;
		return false;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setObstacle(boolean)
	 */
	@Override
	public IRoom setObstacle(boolean obstacle) {
		if (obstacle)
			if (!getTags().contains(OBSTACLE)) getTags().add(RoomTag.OBSTACLE);
		else {
			if (getTags().contains(OBSTACLE)) getTags().remove(OBSTACLE);
		}
		return this;
	}

	public List<IExit> getExits() {
		return exits;
	}

	public void setExits(List<IExit> exits) {
		this.exits = exits;
	}

	@Override
	public String toString() {
		return String.format(
				"Room [\n\tid=%s, \n\tname=%s, \n\tcoords=%s, \n\tdepth=%s, \n\twidth=%s, \n\theight=%s, \n\tdirection=%s, \n\ttags=%s, \n\tdegrees=%s, \n\texits=%s]",
				id, name, coords, depth, width, height, direction, tags, degrees, exits);
	}
	
	
}
