/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import java.util.LinkedList;
import java.util.List;

import com.someguyssoftware.dungeons2.graph.Wayline;
import com.someguyssoftware.dungeons2.style.Theme;

/**
 * @author Mark Gottschling on Jul 27, 2016
 *
 */
public class Dungeon {

	private Room entrance;
	private List<Level> levels;
	// TODO shafts needs to be indexed by levelIndex
	private List<Wayline> shafts;
	private Integer minX, maxX;
	private Integer minY, maxY;
	private Integer minZ, maxZ;
	private Theme theme;
	
	private DungeonConfig config;
	
	/**
	 * 
	 */
	public Dungeon() {
		levels = new LinkedList<>();
		this.config = new DungeonConfig();
	}

	/**
	 * 
	 * @param config
	 */
	public Dungeon(DungeonConfig config) {
		levels = new LinkedList<>();
		this.config = config;
	}
	
	/**
	 * @return the levels
	 */
	public List<Level> getLevels() {
		return levels;
	}

	/**
	 * @param levels the levels to set
	 */
	public void setLevels(List<Level> levels) {
		this.levels = levels;
	}

	/**
	 * @return the minX
	 */
	public Integer getMinX() {
		return minX;
	}

	/**
	 * @param minX the minX to set
	 */
	public void setMinX(Integer minX) {
		this.minX = minX;
	}

	/**
	 * @return the maxX
	 */
	public Integer getMaxX() {
		return maxX;
	}

	/**
	 * @param maxX the maxX to set
	 */
	public void setMaxX(Integer maxX) {
		this.maxX = maxX;
	}

	/**
	 * @return the minY
	 */
	public Integer getMinY() {
		return minY;
	}

	/**
	 * @param minY the minY to set
	 */
	public void setMinY(Integer minY) {
		this.minY = minY;
	}

	/**
	 * @return the maxY
	 */
	public Integer getMaxY() {
		return maxY;
	}

	/**
	 * @param maxY the maxY to set
	 */
	public void setMaxY(Integer maxY) {
		this.maxY = maxY;
	}

	/**
	 * @return the minZ
	 */
	public Integer getMinZ() {
		return minZ;
	}

	/**
	 * @param minZ the minZ to set
	 */
	public void setMinZ(Integer minZ) {
		this.minZ = minZ;
	}

	/**
	 * @return the maxZ
	 */
	public Integer getMaxZ() {
		return maxZ;
	}

	/**
	 * @param maxZ the maxZ to set
	 */
	public void setMaxZ(Integer maxZ) {
		this.maxZ = maxZ;
	}

	/**
	 * @return the entrance
	 */
	public Room getEntrance() {
		return entrance;
	}

	/**
	 * @param entrance the entrance to set
	 */
	public void setEntrance(Room entrance) {
		this.entrance = entrance;
	}

	/**
	 * @return the theme
	 */
	public Theme getTheme() {
		return theme;
	}

	/**
	 * @param theme the theme to set
	 */
	public void setTheme(Theme theme) {
		this.theme = theme;
	}

	/**
	 * @return the shafts
	 */
	public List<Wayline> getShafts() {
		return shafts;
	}

	/**
	 * @param shafts the shafts to set
	 */
	public void setShafts(List<Wayline> shafts) {
		this.shafts = shafts;
	}

	/**
	 * @return the config
	 */
	public DungeonConfig getConfig() {
		return config;
	}

	/**
	 * @param config the config to set
	 */
	public void setConfig(DungeonConfig config) {
		this.config = config;
	}
}
