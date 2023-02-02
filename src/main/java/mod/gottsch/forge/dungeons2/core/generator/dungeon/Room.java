/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2020 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import java.util.ArrayList;
import java.util.List;

import mod.gottsch.forge.dungeons2.core.generator.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.INode;
import mod.gottsch.forge.dungeons2.core.generator.NodeType;
import mod.gottsch.forge.dungeons2.core.generator.Rectangle2D;


/**
 * @author Mark Gottschling on Sep 15, 2020
 *
 */
public class Room extends BaseRoom implements IDungeonElement {
	private int id;
	private int maxDegrees;
	private NodeType nodeType;
	// TODO move below out to BaseRoom
	private Rectangle2D box;
	private IRoomRole roomRole;
	private List<IRoomFlag> flags; // TODO should be a Set<>
	private List<Coords2D> exits;
	
	/**
	 * Empty constructor
	 */
	public Room() {
		// ensure all required fields (ex box) are generated lazily in getters if allowing empty constructors
		super();
	}
	
	/*
	 * 
	 */
	public Room(int x, int y, int width, int depth) {
		this(new Coords2D(x, y), width, depth);
	}
	
	/**
	 * 
	 * @param origin
	 * @param width
	 * @param depth
	 */
	public Room(Coords2D origin, int width, int depth) {
		super();
		this.box = new Rectangle2D(origin, width, depth);
		this.maxDegrees = 3;
		this.nodeType = NodeType.STANDARD;
	}
	
	public Rectangle2D getBox() {
		if (box == null) {
			box = new Rectangle2D(0, 0, 0, 0);
		}
		return box;
	}
	
	public void setBox(Rectangle2D box) {
		this.box = box;
	}
	
	@Override
	public Coords2D getOrigin() {
		return getBox().getOrigin();
	}

	@Override
	public void setOrigin(Coords2D origin) {
		this.getBox().setOrigin(origin);
	}
	
	@Override
	public Coords2D getCenter() {
		return getBox().getCenter();
	}
	
	@Override
	public int getMinX() {
		return this.getBox().getMinX();
	}
	
	@Override
	public int getMaxX() {
		return this.getBox().getMaxX();
	}
	
	@Override
	public int getMinY() {
		return this.getBox().getMinY();
	}
	
	@Override
	public int getMaxY() {
		return this.getBox().getMaxY();
	}

	@Override
	public NodeType getType() {
		return nodeType;
	}

	@Override
	public INode setType(NodeType type) {
		this.nodeType = type;
		return this;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public void setId(int id) {
		this.id = id;
	}

	@Override
	public int getMaxDegrees() {
		return maxDegrees;
	}

	@Override
	public INode setMaxDegrees(int degrees) {
		this.maxDegrees = degrees;
		return this;
	}

	@Override
	public IRoomRole getRole() {
		return roomRole;
	}

	@Override
	public IRoom setRole(IRoomRole roomRole) {
		this.roomRole = roomRole;
		return this;
	}

	@Override
	public String toString() {
		return "DungeonRoom [id=" + id + ", box=" + box + "]";
	}

	@Override
	public List<Coords2D> getExits() {
		if (exits == null) {
			exits = new ArrayList<>();
		}
		return exits;
	}

	@Override
	public void setExits(List<Coords2D> exits) {
		this.exits = exits;
	}

	@Override
	public List<IRoomFlag> getFlags() {
		if (flags == null) {
			flags = new ArrayList<>();
		}
		return flags;
	}

	@Override
	public void setFlags(List<IRoomFlag> flags) {
		this.flags = flags;
	}
	
	@Override
	public boolean hasFlag(IRoomFlag flag) {
		return getFlags().contains(flag);
	}
}
