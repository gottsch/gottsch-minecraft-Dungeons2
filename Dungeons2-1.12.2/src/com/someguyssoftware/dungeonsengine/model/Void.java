/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import static com.someguyssoftware.dungeonsengine.enums.SpaceTag.*;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.dungeonsengine.enums.SpaceTag;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Dec 25, 2018
 *
 */
public class Space implements ISpace {

	private int id;
	private String name;
	private ICoords coords;

	private int depth;
	private int width;
	private int height;
	
	private Direction direction;	
	List<SpaceTag> tags;
	
	// graphing
	private int degrees;
	
	/**
	 * 
	 */
	public Space() {}

	/**
	 * 
	 * @param space
	 */
	public Space(ISpace space) {
		if (space != null) {
			setId(space.getId());

		}
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#copy()
	 */
	@Override
	public ISpace copy() {
		return new Space(this);
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
	public int getId() {
		return id;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setId(int)
	 */
	@Override
	public ISpace setId(int id) {
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
	public ISpace setName(String name) {
		this.name = name;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getCoords()
	 */
	@Override
	public ICoords getCoords() {
		return coords;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setCoords(com.someguyssoftware.gottschcore.positional.ICoords)
	 */
	@Override
	public ISpace setCoords(ICoords coords) {
		this.coords = coords;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDepth()
	 */
	@Override
	public int getDepth() {
		return depth;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setDepth(int)
	 */
	@Override
	public ISpace setDepth(int depth) {
		this.depth = depth;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getWidth()
	 */
	@Override
	public int getWidth() {
		return width;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setWidth(int)
	 */
	@Override
	public ISpace setWidth(int width) {
		this.width = width;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getHeight()
	 */
	@Override
	public int getHeight() {
		return height;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#setHeight(int)
	 */
	@Override
	public ISpace setHeight(int height) {
		this.height = height;
		return this;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ISpace#getDirection()
	 */
	@Override
	public Direction getDirection() {
		return direction;
	}	

	@Override
	public ISpace setDirection(Direction direction) {
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
	public ISpace setDegrees(int degrees) {
		this.degrees = degrees;
		return this;
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
	public ISpace setAnchor(boolean anchor) {
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
	public ISpace setStart(boolean start) {
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
	public ISpace setEnd(boolean end) {
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
	public ISpace setObstacle(boolean obstacle) {
		if (obstacle)
			if (!getTags().contains(OBSTACLE)) getTags().add(SpaceTag.OBSTACLE);
		else {
			if (getTags().contains(OBSTACLE)) getTags().remove(OBSTACLE);
		}
		return this;
	}

//	// TODO move to own file or part of IRoom
//	public enum Type {
//		GENERAL("general"),
//		LADDER("ladder"),
//		ENTRANCE("entrance"),
//		EXIT("exit"),
//		TREASURE("treasure"),
//		BOSS("boss"),
//		HALLWAY("hallway");
//
//		private String name;
//		
//		/**
//		 * @param arg0
//		 * @param arg1
//		 */
//		Type(String name) {
//			this.name = name;
//		}
//
//		/**
//		 * @return the NAME
//		 */
//		public String getName() {
//			return name;
//		}
//
//		/**
//		 * @param NAME the NAME to set
//		 */
//		public void setName(String name) {
//			this.name = name;
//		}
//	}
//	
//	public void setType(Type type) {
//		
//	}

	/**
	 * @return the tags
	 */
	@Override
	public List<SpaceTag> getTags() {
		if (tags == null) tags = new ArrayList<>();
		return tags;
	}

	/**
	 * @param tags the tags to set
	 */
	@Override
	public void setTags(List<SpaceTag> tags) {
		this.tags = tags;
	}
	
	
}
