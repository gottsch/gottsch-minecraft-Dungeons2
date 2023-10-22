package com.someguyssoftware.dungeons2.model;

import com.someguyssoftware.dungeons2.config.BuildDirection;
import com.someguyssoftware.dungeons2.config.BuildPattern;
import com.someguyssoftware.dungeons2.config.BuildSize;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.nbt.NBTTagCompound;

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
		setCoords(dungeon.getEntrance().getBottomCenter());
		setPattern(pattern);
		setSize(dungeonSize);
		setLevelSize(levelSize);
		setDirection(direction);
	}
	
	/**
	 * 
	 * @return
	 */
	public NBTTagCompound save() {		
		NBTTagCompound tag = new NBTTagCompound();

		if (getCoords() != null) {
			tag.setInteger("x", getCoords().getX());
			tag.setInteger("y", getCoords().getY());
			tag.setInteger("z", getCoords().getZ());
		}
		tag.setInteger("minX", getMinX());
		tag.setInteger("minY", getMinY());
		tag.setInteger("minZ", getMinZ());
		tag.setInteger("maxX", getMaxX());
		tag.setInteger("maxY", getMaxY());
		tag.setInteger("maxZ", getMaxZ());
		
		tag.setInteger("levels", getLevels());
		if (getThemeName() != null) {
			tag.setString("theme", getThemeName());
		}
		if (getPattern() != null) {
			tag.setString("pattern", getPattern().name());
		}
		if (getSize() != null) {
			tag.setString("size", getSize().name());
		}
		if (getLevelSize() != null) {
			tag.setString("levelSize", getLevelSize().name());
		}
		if (getDirection() != null) {
			tag.setString("direction", getDirection().name());
		}
		if (getBossChestCoords() != null) {
			tag.setInteger("bossChestX", getBossChestCoords().getX());
			tag.setInteger("bossChestY", getBossChestCoords().getY());
			tag.setInteger("bossChestZ", getBossChestCoords().getZ());
		}
		return tag;
	}
	
	/**
	 * 
	 * @param tag
	 */
	public void load(NBTTagCompound tag) {
		if (tag.hasKey("x") && tag.hasKey("y") && tag.hasKey("z")) {
			setCoords(new Coords(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
		}
		else {
			return;
		}
		
		if (tag.hasKey("minX")) {
			setMinX(tag.getInteger("minX"));
		}
		if (tag.hasKey("minY")) {
			setMinX(tag.getInteger("minY"));
		}
		if (tag.hasKey("minZ")) {
			setMinX(tag.getInteger("minZ"));
		}
		if (tag.hasKey("maxX")) {
			setMaxX(tag.getInteger("maxX"));
		}
		if (tag.hasKey("maxY")) {
			setMaxX(tag.getInteger("maxY"));
		}
		if (tag.hasKey("maxZ")) {
			setMaxX(tag.getInteger("maxZ"));
		}
		
		if (tag.hasKey("levels")) {
			setLevels(tag.getInteger("levels"));
		}

		if (tag.hasKey("theme")) {
			setThemeName(tag.getString("theme"));
		}
		
		if (tag.hasKey("pattern")) {
			String pattern = tag.getString("pattern");
			if (!pattern.equals("")) setPattern(BuildPattern.valueOf(pattern));
		}

		if (tag.hasKey("size")) {
			String size = tag.getString("size");
			if (!size.equals("")) setSize(BuildSize.valueOf(size));
		}
		
		if (tag.hasKey("levelSize")) {
			String levelSize = tag.getString("levelSize");
			if (!levelSize.equals("")) setLevelSize(BuildSize.valueOf(levelSize));
		}
		
		if (tag.hasKey("direction")) {
			String direction = tag.getString("direction");
			if (!direction.equals("")) setDirection(BuildDirection.valueOf(direction));
		}

		if (tag.hasKey("bossChestX") && tag.hasKey("bossChestY") && tag.hasKey("bossChestZ")) {
			setBossChestCoords(new Coords(tag.getInteger("bossChestX"), tag.getInteger("bossChestY"), tag.getInteger("bossChestZ")));
		}
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
	}

	@Override
	public String toString() {
		return "DungeonInfo [coords=" + coords + ", levels=" + levels + ", minX=" + minX + ", maxX=" + maxX + ", minY="
				+ minY + ", maxY=" + maxY + ", minZ=" + minZ + ", maxZ=" + maxZ + ", themeName=" + themeName
				+ ", bossChestCoords=" + bossChestCoords + ", pattern=" + pattern + ", levelSize=" + levelSize
				+ ", size=" + size + ", direction=" + direction + "]";
	};
}
