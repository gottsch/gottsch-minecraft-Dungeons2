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
import java.util.Map;

import mod.gottsch.forge.dungeons2.core.generator.ILevel;
import mod.gottsch.forge.dungeons2.core.graph.mst.Edge;

/**
 * 
 * @author Mark Gottschling
 *
 */
public class DungeonLevel implements ILevel {
	private int width;
	private int depth;
	
	/*
	 *  map of cells in a level.
	 *  each room consists of length * width of cells
	 */
//	private boolean[][] cellMap;
	
	/*
	 * a list of all the rooms
	 */
	private List<IRoom> rooms;

	/*
	 * map of all the rooms by id
	 */
	private Map<Integer, IRoom> roomMap;
	
	// TODO don't really like this part of DungeonLevel as it is a graphing object
	/*
	 * list of all edges as a result of triangulation of rooms
	 */
	private List<Edge> edges;
	
	private List<Edge> paths;
	
	private List<Wayline> waylines;
	
	private List<Corridor> corridors;
	
//	public boolean[][] getCellMap() {
//		return cellMap;
//	}
//
//	public void setCellMap(boolean[][] cellMap) {
//		this.cellMap = cellMap;
//	}

	public List<IRoom> getRooms() {
		if (rooms == null) {
			rooms = new ArrayList<>();
		}
		return rooms;
	}

	public void setRooms(List<IRoom> rooms) {
		this.rooms = rooms;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public void setWidth(int width) {
		this.width = width;
	}

	@Override
	public int getDepth() {
		return depth;
	}

	@Override
	public void setDepth(int depth) {
		this.depth = depth;
	}

	public List<Edge> getEdges() {
		return edges;
	}

	public void setEdges(List<Edge> edges) {
		this.edges = edges;
	}

	public Map<Integer, IRoom> getRoomMap() {
		return roomMap;
	}

	public void setRoomMap(Map<Integer, IRoom> roomMap) {
		this.roomMap = roomMap;
	}

	public List<Edge> getPaths() {
		return paths;
	}

	public void setPaths(List<Edge> paths) {
		this.paths = paths;
	}

	public List<Wayline> getWaylines() {
		return waylines;
	}

	public void setWaylines(List<Wayline> waylines) {
		this.waylines = waylines;
	}

	public List<Corridor> getCorridors() {
		return corridors;
	}

	public void setCorridors(List<Corridor> corridors) {
		this.corridors = corridors;
	}
	
}
