package com.someguyssoftware.dungeonsengine.model;

import java.util.Comparator;
import java.util.List;

import com.someguyssoftware.dungeonsengine.enums.RoomTag;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.BBox;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.positional.Intersect;

/**
 * 
 * @author Mark Gottschling on Dec 18, 2018
 *
 */
public interface IRoom extends ISpace {
	public static final int MIN_DEPTH = 5;
	public static final int MIN_WIDTH = 5;
	public static final int MIN_HEIGHT = 4;
	public static final int MIN_SPECIAL_WIDTH = 7;
	public static final int MIN_SPECIAL_DEPTH = 7;
	
	/**
	 * 
	 * @return
	 */
	IRoom copy();
	
	/**
	 * 
	 * @return
	 */
	int getID();

	/**
	 * 
	 * @param id
	 */
	IRoom setID(int id);
	
	/**
	 * 
	 * @return
	 */
	String getName();

	/**
	 * 
	 * @param name
	 */
	IRoom setName(String name);
	
//	/**
//	 * @return the coords
//	 */
//	ICoords getCoords();
//
//	/**
//	 * @param coords the coords to set
//	 */
//	IRoom setCoords(ICoords coords);	

//	/**
//	 * @return the depth
//	 */
//	int getDepth();
//
//	/**
//	 * @param depth the depth to set
//	 */
//	IRoom setDepth(int depth);
//
//	/**
//	 * @return the width
//	 */
//	int getWidth();
//
//	/**
//	 * @param width the width to set
//	 */
//	IRoom setWidth(int width);
//
//	/**
//	 * @return the height
//	 */
//	int getHeight();
//
//	/**
//	 * @param height the height to set
//	 */
//	IRoom setHeight(int height);

	/**
	 * @return the direction
	 */
	Direction getDirection();

	/**
	 * @param direction the direction to set
	 */
	IRoom setDirection(Direction direction);
	
	/**
	 * 
	 * @return
	 */
	int getDegrees();  /// ? probably shouldn't be here

	/**
	 * 
	 * @param degrees
	 * @return
	 */
	IRoom setDegrees(int degrees);
	
	/**
	 * 
	 * @return
	 */
	default public int getMinX() {
		return this.getCoords().getX();
	}

	/**
	 * 
	 * @return
	 */
	default public int getMaxX() {
		return this.getCoords().getX() + this.getWidth() - 1;
	}

	/*
	 * 
	 */
	default public int getMinY() {
		return this.getCoords().getY();
	}
	
	/*
	 * 
	 */
	default public int getMaxY() {
		return this.getCoords().getY() + this.getHeight() - 1;
	}
	
	/**
	 * 
	 * @return
	 */
	default public int getMinZ() {
		return this.getCoords().getZ();
	}
	
	/**
	 * 
	 * @return
	 */
	default public int getMaxZ() {
		return this.getCoords().getZ() + this.getDepth() - 1;
	}
	
	/**
	 * 
	 * @return
	 */
	default public BBox getBoundingBox() {
		BBox bb = new BBox(getCoords(), getCoords().add(getWidth(), getHeight(), getDepth()));
		return bb;
	}
	
	/**
	 * Creates a bounding box by the XZ dimensions with a height (Y) of 1
	 * @return
	 */
	default public BBox getXZBoundingBox() {
		BBox bb = new BBox(new Coords(getCoords().getX(), 0, getCoords().getZ()),
				getCoords().add(getWidth(), 1, getDepth()));
		return bb;
	}
	
	/**
	 * 
	 * @return
	 */
	default public ICoords getCenter() {
		int x = this.getCoords().getX()  + ((this.getWidth()-1) / 2) ;
		int y = this.getCoords().getY()  + ((this.getHeight()-1) / 2);
		int z = this.getCoords().getZ()  + ((this.getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;
	}
	
	/**
	 * 
	 * @return
	 */
	default public ICoords getBottomCenter() {
		int x = this.getCoords().getX()  + ((this.getWidth()-1) / 2);
		int y = this.getCoords().getY();
		int z = this.getCoords().getZ()  + ((this.getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;	
	}

	/**
	 * 
	 * @return
	 */
	default public ICoords getTopCenter() {
		int x = this.getCoords().getX()  + ((this.getWidth()-1) / 2);
		int y = this.getCoords().getY() + this.getHeight();
		int z = this.getCoords().getZ()  + ((this.getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;	
	}
	
	/**
	 * 
	 * @return
	 */
	default public ICoords getXZCenter() {
		int x = this.getCoords().getX()  + ((this.getWidth()-1) / 2);
		int y = this.getCoords().getY();
		int z = this.getCoords().getZ()  + ((this.getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;
	}
	
	/**
	 * 
	 * @param room
	 * @return
	 */
	default public Intersect getIntersect(IRoom room) {
		return Intersect.getIntersect(this.getBoundingBox(), room.getBoundingBox());
	}
	
	/**
	 * Returns a new ISpace with the force applied at the angle on the XZ plane.
	 * @param angle
	 * @param force
	 * @return
	 */
	default public IRoom addXZForce(double angle, double force) {
		double xForce = Math.sin(angle) * force;
        double zForce = Math.cos(angle) * force;

        IRoom room = copy();
        room.setCoords(room.getCoords().add((int)xForce, 0, (int)zForce));
        return room;
	}
	
	/**
	 * Comparator to sort by Id
	 */
	public static Comparator<IRoom> idComparator = new Comparator<IRoom>() {
		@Override
		public int compare(IRoom p1, IRoom p2) {
			if (p1.getID() > p2.getID()) {
				// greater than
				return 1;
			}
			else {
				// less than
				return -1;
			}
		}
	};
	
	default public String printDimensions() {
		return String.format("Dimensions -> [w: %d, h: %d, d: %d]", getWidth(), getHeight(), getDepth());
	}
	
	default public String printCoords() {
		return String.format("Coords -> [x: %d, y: %d, z: %d]", getCoords().getX(), getCoords().getY(), getCoords().getZ());
	}
	
	default public String printCenter() {
		return String.format("Center -> [x: %d, y: %d, z: %d]", getCenter().getX(), getCenter().getY(), getCenter().getZ());		
	}

	boolean isAnchor();

	IRoom setAnchor(boolean anchor);

	boolean isStart();

	IRoom setStart(boolean start);

	boolean isEnd();

	IRoom setEnd(boolean end);

	boolean isObstacle();

	IRoom setObstacle(boolean obstacle);

	void centerOn(ICoords coords);

	List<RoomTag> getTags();

	void setTags(List<RoomTag> tags);

	IRoom setDimensions(int width, int height, int depth);
}