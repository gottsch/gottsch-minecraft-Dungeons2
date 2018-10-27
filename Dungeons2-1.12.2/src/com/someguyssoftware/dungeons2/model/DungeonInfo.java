package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.dungeons2.config.BuildDirection;
import com.someguyssoftware.dungeons2.config.BuildPattern;
import com.someguyssoftware.dungeons2.config.BuildSize;
import com.someguyssoftware.dungeonsengine.model.Dungeon;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * 
 * @author Mark Gottschling on Aug 19, 2017
 *
 */
public class DungeonInfo {
	private ICoords coords;
	private int levels;
	private int minX, maxX = 0;
	private int minY, maxY = 0;
	private int minZ, maxZ = 0;
	private String themeName;
	private ICoords bossChestCoords;
	private BuildPattern pattern;
	private BuildSize levelSize;
	private BuildSize size;
	private BuildDirection direction;
	
	/**
	 * 
	 */
	public DungeonInfo() {}

	/**
	 * 
	 * @param dungeon
	 */
	public DungeonInfo(Dungeon dungeon) {
		this.setCoords(dungeon.getEntrance().getBottomCenter());
		this.setLevels(dungeon.getLevels().size());
		this.setThemeName(dungeon.getTheme().getName());
		this.setMinX(dungeon.getMinX());
		this.setMaxX(dungeon.getMaxX());
		this.setMinY(dungeon.getMinY());
		this.setMaxY(dungeon.getMaxY());
		this.setMinZ(dungeon.getMinZ());
		this.setMaxZ(dungeon.getMaxZ());
		// TODO get the boss chest coords somehow?		
	}

	/**
	 * 
	 * @param dungeon
	 * @param pattern
	 * @param levelSize
	 * @param direction
	 */
	public DungeonInfo(Dungeon dungeon, BuildPattern pattern, BuildSize dungeonSize, BuildSize levelSize, BuildDirection direction) {
		this(dungeon);
		setPattern(pattern);
		setSize(dungeonSize);
		setLevelSize(levelSize);
		setDirection(direction);
	}
	
	public ICoords getCoords() {
		return coords;
	}

	public void setCoords(ICoords coords) {
		this.coords = coords;
	}

	public int getLevels() {
		return levels;
	}

	public void setLevels(int levels) {
		this.levels = levels;
	}

	public int getMinX() {
		return minX;
	}

	public void setMinX(int minX) {
		this.minX = minX;
	}

	public int getMaxX() {
		return maxX;
	}

	public void setMaxX(int maxX) {
		this.maxX = maxX;
	}

	public int getMinY() {
		return minY;
	}

	public void setMinY(int minY) {
		this.minY = minY;
	}

	public int getMaxY() {
		return maxY;
	}

	public void setMaxY(int maxY) {
		this.maxY = maxY;
	}

	public int getMinZ() {
		return minZ;
	}

	public void setMinZ(int minZ) {
		this.minZ = minZ;
	}

	public int getMaxZ() {
		return maxZ;
	}

	public void setMaxZ(int maxZ) {
		this.maxZ = maxZ;
	}

	public String getThemeName() {
		return themeName;
	}

	public void setThemeName(String themeName) {
		this.themeName = themeName;
	}

	public ICoords getBossChestCoords() {
		return bossChestCoords;
	}

	public void setBossChestCoords(ICoords bossChestCoords) {
		this.bossChestCoords = bossChestCoords;
	}

	public BuildPattern getPattern() {
		return pattern;
	}

	public void setPattern(BuildPattern pattern) {
		this.pattern = pattern;
	}

	public BuildSize getLevelSize() {
		return levelSize;
	}

	public void setLevelSize(BuildSize size) {
		this.levelSize = size;
	}

	public BuildDirection getDirection() {
		return direction;
	}

	public void setDirection(BuildDirection direction) {
		this.direction = direction;
	}

	public BuildSize getSize() {
		return size;
	}

	public void setSize(BuildSize size) {
		this.size = size;
	};
}
