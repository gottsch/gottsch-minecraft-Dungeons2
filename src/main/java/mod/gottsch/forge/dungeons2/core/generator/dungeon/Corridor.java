/*
 * This file is part of  GottschCore.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
 * 
 * All rights reserved.
 *
 * GottschCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GottschCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GottschCore.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import java.util.ArrayList;
import java.util.List;

import mod.gottsch.forge.dungeons2.core.generator.Axis;
import mod.gottsch.forge.dungeons2.core.generator.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.Rectangle2D;

/**
 * A corridor is a room-like object with a width of a least 3 (1 for hall, 2 for
 * walls)
 * 
 * @author Mark Gottschling on Sep 22, 2020
 *
 */
public class Corridor implements IDungeonElement {
	private int id;
	private Rectangle2D box;
	private List<Coords2D> exits;
	private Axis axis;
	private List<IRoom> connectsTo;
	private List<IRoom> intersectsWith;

	/**
	 * 
	 */
	public Corridor() {
	}

	/**
	 * 
	 * @param box
	 */
	public Corridor(Rectangle2D box) {
		this.box = box;
	}

	/**
	 * 
	 */
	public void findIntersections(List<IRoom> rooms) {

		for (IRoom intersectingRoom : rooms) {
			if (intersectingRoom.getBox().intersects(getBox())) {
				getIntersectsWith().add(intersectingRoom);
			}
		}
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Rectangle2D getBox() {
		return box;
	}

	public void setBox(Rectangle2D box) {
		this.box = box;
	}

	public List<Coords2D> getExits() {
		if (exits == null) {
			exits = new ArrayList<Coords2D>();
		}
		return exits;
	}

	public void setExits(List<Coords2D> exits) {
		this.exits = exits;
	}

	public Axis getAxis() {
		return axis;
	}

	public void setAxis(Axis axis) {
		this.axis = axis;
	}

	@Override
	public String toString() {
		return "Corridor [id=" + id + ", box=" + box + ", axis=" + axis + "]";
	}

	public List<IRoom> getConnectsTo() {
		if (connectsTo == null) {
			connectsTo = new ArrayList<>();
		}
		return connectsTo;
	}

	public void setConnectsTo(List<IRoom> connectsTo) {
		this.connectsTo = connectsTo;
	}

	public List<IRoom> getIntersectsWith() {
		if (intersectsWith == null) {
			intersectsWith = new ArrayList<>();
		}
		return intersectsWith;
	}

	public void setIntersectsWith(List<IRoom> intersectsWith) {
		this.intersectsWith = intersectsWith;
	}
}
