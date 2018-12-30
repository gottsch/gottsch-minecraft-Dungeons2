/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.dungeons2.graph.Wayline;
import com.someguyssoftware.dungeons2.graph.mst.Edge;
import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * @author Mark Gottschling on Jul 18, 2016
 * @version 2.0
 * @since 1.0.0
 *
 */
public class Level {
	private int id;
	private String name;
	private ICoords startPoint;
	private Room startRoom;
	private Room endRoom;
	private List<Room> rooms;
	private List<Edge> edges;
	private List<Edge> paths;
	@Deprecated
	private List<Wayline> waylines;
	/**
	 * @since 2.0
	 */
	private List<Hallway> hallways;
	private List<Shaft> shafts;
	
	private int minX, maxX;
	private int minY, maxY;
	private int minZ, maxZ;

	private ILevelConfig config;

	/**
	 * 
	 */
	public Level() {
		super();
	}

	/**
	 * @return the startRoom
	 */
	public Room getStartRoom() {
		return startRoom;
	}


	/**
	 * @param startRoom the startRoom to set
	 */
	public void setStartRoom(Room startRoom) {
		this.startRoom = startRoom;
	}


	/**
	 * @return the endRoom
	 */
	public Room getEndRoom() {
		return endRoom;
	}


	/**
	 * @param endRoom the endRoom to set
	 */
	public void setEndRoom(Room endRoom) {
		this.endRoom = endRoom;
	}


	/**
	 * @return the rooms
	 */
	public List<Room> getRooms() {
		if (this.rooms == null) this.rooms = new ArrayList<>();
		return rooms;
	}


	/**
	 * @param rooms the rooms to set
	 */
	public void setRooms(List<Room> rooms) {
		this.rooms = rooms;
	}


	/**
	 * @param startRoom
	 * @param endRoom
	 * @param rooms
	 */
	public Level(Room startRoom, Room endRoom, List<Room> rooms) {
		super();
		this.startRoom = startRoom;
		this.endRoom = endRoom;
		this.rooms = rooms;
	}

	/**
	 * @return the id
	 */
	public int getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(int id) {
		this.id = id;
	}

	/**
	 * @return the NAME
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param NAME the NAME to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the edges
	 */
	public List<Edge> getEdges() {
		return edges;
	}

	/**
	 * @param edges the edges to set
	 */
	public void setEdges(List<Edge> edges) {
		this.edges = edges;
	}

	/**
	 * @return the paths
	 */
	public List<Edge> getPaths() {
		return paths;
	}

	/**
	 * @param paths the paths to set
	 */
	public void setPaths(List<Edge> paths) {
		this.paths = paths;
	}

	/**
	 * @return the waylines
	 */
	public List<Wayline> getWaylines() {
		return waylines;
	}

	/**
	 * @param waylines the waylines to set
	 */
	public void setWaylines(List<Wayline> waylines) {
		this.waylines = waylines;
	}

	/**
	 * @return the minX
	 */
	public int getMinX() {
		return minX;
	}

	/**
	 * @param minX the minX to set
	 */
	public void setMinX(int minX) {
		this.minX = minX;
	}

	/**
	 * @return the maxX
	 */
	public int getMaxX() {
		return maxX;
	}

	/**
	 * @param maxX the maxX to set
	 */
	public void setMaxX(int maxX) {
		this.maxX = maxX;
	}

	/**
	 * @return the minY
	 */
	public int getMinY() {
		return minY;
	}

	/**
	 * @param minY the minY to set
	 */
	public void setMinY(int minY) {
		this.minY = minY;
	}

	/**
	 * @return the maxY
	 */
	public int getMaxY() {
		return maxY;
	}

	/**
	 * @param maxY the maxY to set
	 */
	public void setMaxY(int maxY) {
		this.maxY = maxY;
	}

	/**
	 * @return the minZ
	 */
	public int getMinZ() {
		return minZ;
	}

	/**
	 * @param minZ the minZ to set
	 */
	public void setMinZ(int minZ) {
		this.minZ = minZ;
	}

	/**
	 * @return the maxZ
	 */
	public int getMaxZ() {
		return maxZ;
	}

	/**
	 * @param maxZ the maxZ to set
	 */
	public void setMaxZ(int maxZ) {
		this.maxZ = maxZ;
	}

	/**
	 * @return the startPoint
	 */
	public ICoords getStartPoint() {
		return startPoint;
	}

	/**
	 * @param startPoint the startPoint to set
	 */
	public void setStartPoint(ICoords startPoint) {
		this.startPoint = startPoint;
	}

	/**
	 * @return the config
	 */
	public ILevelConfig getConfig() {
		return config;
	}

	/**
	 * @param config the config to set
	 */
	public void setConfig(ILevelConfig config) {
		this.config = config;
	}

	/**
	 * @return the shafts
	 */
	public List<Shaft> getShafts() {
		if (shafts == null) {
			this.shafts = new ArrayList<>();
		}
		return shafts;
	}

	/**
	 * @param shafts the shafts to set
	 */
	public void setShafts(List<Shaft> shafts) {
		this.shafts = shafts;
	}

	public List<Hallway> getHallways() {
		if (hallways == null) {
			this.hallways = new ArrayList<>();
		}
		return hallways;
	}

	public void setHallways(List<Hallway> hallways) {
		this.hallways = hallways;
	}

	@Override
	public String toString() {
		return "Level [id=" + id + ", name=" + name + ", startPoint=" + startPoint + ", startRoom=" + startRoom
				+ ", endRoom=" + endRoom + ", rooms=" + rooms.size() + ", minX=" + minX + ", maxX=" + maxX
				+ ", minY=" + minY + ", maxY=" + maxY + ", minZ=" + minZ + ", maxZ=" + maxZ + ", config=" + config
				+ "]";
	}
	
}
