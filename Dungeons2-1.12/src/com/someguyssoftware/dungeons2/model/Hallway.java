/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.dungeons2.graph.Wayline;
import com.someguyssoftware.dungeons2.graph.Waypoint;
import com.someguyssoftware.gottschcore.enums.Alignment;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Aug 6, 2016
 *
 */
public class Hallway extends Room {
	Alignment alignment;
	List<Door> doors;

	public Hallway() {
		super();
		doors = new ArrayList<>(2);
	}
	
	/**
	 * 
	 * @param wayline
	 * @param rooms
	 * @return
	 */
	public static Hallway fromWayline(Wayline wayline, List<Room> rooms) {
		int width = 3;
		int depth = 3;
		
		// work with temp way points
		Waypoint startPoint = null;
		Waypoint endPoint = null;
		ICoords startCoords = null;
		boolean isElbowJoint = false;
		
		// HORIZONTAL (WEST <--> EAST)
		if (wayline.getAlignment() == Alignment.HORIZONTAL) {
			// determine which point is the "start point" - having the smallest coords
			if (wayline.getPoint1().getX() < wayline.getPoint2().getX()) {
				startPoint = wayline.getPoint1();
				endPoint = wayline.getPoint2();
			}
			else {
				startPoint = wayline.getPoint2();
				endPoint = wayline.getPoint1();
			}

			// determine if this is a "elbow joint" wayline
			if (!startPoint.isTerminated() || !endPoint.isTerminated()) {
				isElbowJoint = true;
			}
			
			/*
			 * update start/end point depending on isTerminal
			 * this makes the elbow joint 1 block longer so that they line up correctly
			 */
			if (isElbowJoint) {
				if (!startPoint.isTerminated()) {
					startPoint.setCoords(startPoint.getCoords().add(-1, 0, 0));
				}
				
				if (!endPoint.isTerminated()) {
					endPoint.setCoords(endPoint.getCoords().add(1, 0, 0));
				}
			}
			// update the width
			width = Math.abs(startPoint.getX() - endPoint.getX()) + 1;
			
			/*
			 *  this is to maintain the actual hallway (air part) to still be along the wayline,
			 *  since the hallway is 3 wide (2 walls and 1 air)
			 */
			startCoords = startPoint.getCoords();
			startCoords = startCoords.add(0, 0, -1);
			
			// shift if non-terminal (ie an elbow joint)

		}
		// VERTICAL (NORTH <--> SOUTH)
		else {
			// determine which point is the "start point" - having the smallest coords
			if (wayline.getPoint1().getZ() < wayline.getPoint2().getZ()) {
				startPoint = wayline.getPoint1();
				endPoint = wayline.getPoint2();
			}
			else {
				startPoint = wayline.getPoint2();
				endPoint = wayline.getPoint1();
			}
			
			/*
			 * update start/end point depending on isTerminal
			 * this makes the elbow joint 1 block longer so that they line up correctly
			 */		
			if (isElbowJoint) {
				if (!startPoint.isTerminated()) {
					startPoint.setCoords(startPoint.getCoords().add(0, 0, -1));
				}				
				if (!endPoint.isTerminated()) {
					endPoint.setCoords(endPoint.getCoords().add(0, 0, 1));
				}
			}
			// update the depth
			depth = Math.abs(startPoint.getZ( ) - endPoint.getZ()) + 1;
			
			// left-shift by one since horiztonal hallways are 3 depth
			// this is to maintain the actual hallway (air part) to still be along the wayline.
			startCoords = startPoint.getCoords();
			startCoords = startCoords.add(-1, 0, 0);
		}
		
		// get the rooms referenced by the waypoints
		Room room1 = rooms.get(startPoint.getId());
		Room room2 = rooms.get(endPoint.getId());		

		 // the start/end points y-vlaue isn't set, so update them.
		startPoint.setCoords(startPoint.getCoords().resetY(room1.getCoords().getY()));
		endPoint.setCoords(endPoint.getCoords().resetY(room2.getCoords().getY()));

		// calculate what the dimensions should be
		int height = Math.abs(
				Math.min(room1.getMinY(), room2.getMinY()) - 
				Math.max(room1.getMinY(), room2.getMinY())
				) + 1+ 3; // NOTE why 3? because a doorway is 2 + ceiling block
		
		// create a temp room out of the dimensions
		Hallway hallway = (Hallway) new Hallway().setCoords(
				new Coords(
						startCoords.getX(),
						startPoint.getCoords().getY(),
						startCoords.getZ()))
				.setWidth(width)
				.setDepth(depth)
				.setHeight(height)
				.setType(Type.HALLWAY);
		// update the alignment (Hallway specific property)
		hallway.setAlignment(wayline.getAlignment());
		
		// store the start/end point as doorCoords iff they are terminated.
		if (startPoint.isTerminated()) {
			hallway.getDoors().add(new Door(startPoint.getCoords(), room1));
			// TODO at this point, could update Room with Door coords.
		}
		if (endPoint.isTerminated()) {
			hallway.getDoors().add(new Door(endPoint.getCoords(), room2));
		}
		return hallway;
	}
	
	/**
	 * @return the alignment
	 */
	public Alignment getAlignment() {
		return alignment;
	}

	/**
	 * @param alignment the alignment to set
	 */
	public void setAlignment(Alignment alignment) {
		this.alignment = alignment;
	}

	/**
	 * @return the doors
	 */
	public List<Door> getDoors() {
		return doors;
	}

	/**
	 * @param doors the doors to set
	 */
	public void setDoors(List<Door> doors) {
		this.doors = doors;
	}
	
}
