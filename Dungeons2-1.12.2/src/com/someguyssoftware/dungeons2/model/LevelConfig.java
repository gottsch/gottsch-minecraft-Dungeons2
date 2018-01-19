/**
 * 
 */
package com.someguyssoftware.dungeons2.model;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.dungeons2.chest.ChestCategory;
import com.someguyssoftware.gottschcore.Quantity;

/**
 * @author Mark Gottschling on Jul 18, 2016
 *
 */
public class LevelConfig {

	/*
	 * min/max # of rooms on a level
	 */
	private Quantity numberOfRooms;
	/*
	 * min/max dimensions of a room
	 */
	private Quantity width;
	private Quantity depth;
	private Quantity height;
	
	/*
	 * min/max distance from start point.
	 * used for controlling the pattern of the randomness
	 */
	private Quantity xDistance;
	private Quantity zDistance;
	
	/*
	 * min/max values for the y-plane that a room's y dimension must fit inside
	 */
	private Quantity yRange;
	
	/*
	 * min/max offset distance from the start point.
	 * used for controlling the pattern of the randomness.
	 * ex if distance = 0-500 and offset = 250 then the rooms will be generated within a circle around the start point
	 */
//	private Quantity xOffset;
//	private Quantity zOffset;
	
	/*
	 * min/max addition to the start point y value when generating a room.
	 * a level is contrained to the XZ plane, all on the same Y. this value gives some variance to the
	 * Y value but still remains in the same general plane.
	 * should be a low number ie 0-4
	 */
	@Deprecated
	private Quantity yVariance;
	
	/*
	 * min/max number of edges or hallways that each room can have
	 */
	private Quantity degrees;
	
	/**
	 * Number of times to perform decay eval on block
	 */
	private int decayMultiplier;
	
	/*
	 * 
	 */
	private Integer surfaceBuffer;
	
	/*
	 * 
	 */
	private Quantity spawnerFrequency;
	
	/*
	 * Chest properties
	 */
	private Quantity chestFrequency;
	
	private List<String> chestCategories;
	
	/*
	 * Decoration properties
	 */
	private Quantity webFrequency;	
	private Quantity numberOfWebs;
	
	private Quantity vineFrequency;	
	private Quantity numberOfVines;

	private Quantity anywhereDecorationFrequency;
	private Quantity numberOfAnywhereDecorations;
	
	private Quantity bloodFrequency;
	private Quantity numberOfBlood;
	
	private Quantity puddleFrequency;
	private Quantity numberOfPuddles;
	
	/**
	 * Toggle if rooms should be decorated (webs, vines, etc). To speed up processing, set to false.
	 */
	private boolean decorationsOn = true;
	/*
	 * 
	 */
	private boolean minecraftConstraintsOn = true;

	/**
	 * the world sea level. default = 63
	 */
	private int seaLevel;
	
	/*
	 * 
	 */
	private boolean supportOn = true;

	
	/**
	 * 
	 */
	public LevelConfig() {
		setNumberOfRooms(new Quantity(25.0, 35.0));
		setNumberOfEdges(new Quantity(2.0, 4.0));
		setWidth(new Quantity(5, 15));
		setDepth(new Quantity(5, 15));
		setHeight(new Quantity(5, 8));
		setXDistance(new Quantity(-100, 100));
		setZDistance(new Quantity(-100,100));
		/*
		 * default value. should be updated by DungeonBuilder to a specific strata
		 */
		setYRange(new Quantity(3, 230));
		setDegrees(new Quantity(3, 4));
		setYVariance(new Quantity(0, 0));
		setSurfaceBuffer(10);
		setDecayMultiplier(5);
		setSpawnerFrequency(new Quantity(5, 10));
		setChestFrequency(new Quantity(5, 10));
		this.chestCategories = new ArrayList<>();
		this.chestCategories.add(ChestCategory.COMMON.name().toLowerCase());
		this.chestCategories.add(ChestCategory.UNCOMMON.name().toLowerCase());
		this.chestCategories.add(ChestCategory.RARE.name().toLowerCase());
		
		setWebFrequency(new Quantity(10, 20));
		setNumberOfWebs(new Quantity(10, 10));
		setVineFrequency(new Quantity(10, 20));
		setNumberOfVines(new Quantity(10, 10));
		setAnywhereDecorationFrequency(new Quantity(20, 30));
		setNumberOfAnywhereDecorations(new Quantity(20, 20));
		setBloodFrequency(new Quantity(10, 20));
		setNumberOfBlood(new Quantity(5, 5));
		setPuddleFrequency(new Quantity(10, 20));
		setNumberOfPuddles(new Quantity(5, 5));
		setDecorationsOn(true);
		setSeaLevel(63);
		setMinecraftConstraintsOn(true);
		setSupportOn(true);
	}

	/**
	 * 
	 * @param config
	 */
	public LevelConfig(LevelConfig config) {
		setNumberOfEdges(config.getNumberOfEdges());
		setNumberOfRooms(config.getNumberOfRooms());
		setWidth(new Quantity(config.getWidth()));
		setDepth(new Quantity(config.getDepth()));
		setHeight(new Quantity(config.getHeight()));
		setXDistance(new Quantity(config.getXDistance()));
		setZDistance(new Quantity(config.getZDistance()));
		setYRange(new Quantity(config.getYRange()));
		setYVariance(new Quantity(config.getYVariance()));
		setDegrees(new Quantity(config.getDegrees()));
		setSurfaceBuffer(config.getSurfaceBuffer());
		setDecayMultiplier(config.getDecayMultiplier());
		setChestFrequency(config.getChestFrequency());
		getChestCategories().addAll(config.getChestCategories());		
		setSpawnerFrequency(config.getSpawnerFrequency());
		// decorations
		setWebFrequency(config.getWebFrequency());
		setNumberOfWebs(config.getNumberOfWebs());
		setVineFrequency(config.getVineFrequency());
		setNumberOfVines(config.getNumberOfVines());
		setAnywhereDecorationFrequency(config.getAnywhereDecorationFrequency());
		setNumberOfAnywhereDecorations(config.getNumberOfAnywhereDecorations());
		setBloodFrequency(config.getBloodFrequency());
		setNumberOfBlood(config.getNumberOfBlood());
		setPuddleFrequency(config.getPuddleFrequency());
		setNumberOfPuddles(config.getNumberOfPuddles());
		setDecorationsOn(config.isDecorationsOn());
		setSeaLevel(config.getSeaLevel());
		// minecraft world constraints
		setMinecraftConstraintsOn(config.isMinecraftConstraintsOn());
		// toggle blocks requiring support
		setSupportOn(config.isSupportOn());		
	}
	
	/**
	 * 
	 * @return
	 */
	public LevelConfig copy() {
		return new LevelConfig(this);
	}
	
	/**
	 * @return the numberOfRooms
	 */
	public Quantity getNumberOfRooms() {
		return numberOfRooms;
	}
	/**
	 * @param numberOfRooms the numberOfRooms to set
	 */
	public void setNumberOfRooms(Quantity numberOfRooms) {
		this.numberOfRooms = numberOfRooms;
	}

	/**
	 * @return the width
	 */
	public Quantity getWidth() {
		return width;
	}

	/**
	 * @param width the width to set
	 */
	public void setWidth(Quantity width) {
		this.width = width;
	}

	/**
	 * @return the depth
	 */
	public Quantity getDepth() {
		return depth;
	}

	/**
	 * @param depth the depth to set
	 */
	public void setDepth(Quantity depth) {
		this.depth = depth;
	}

	/**
	 * @return the height
	 */
	public Quantity getHeight() {
		return height;
	}

	/**
	 * @param height the height to set
	 */
	public void setHeight(Quantity height) {
		this.height = height;
	}

	/**
	 * @return the xDistance
	 */
	public Quantity getXDistance() {
		return xDistance;
	}

	/**
	 * @param xDistance the xDistance to set
	 */
	public void setXDistance(Quantity distance) {
		this.xDistance = distance;
	}

//	/**
//	 * @return the xOffset
//	 */
//	public Quantity getXOffset() {
//		return xOffset;
//	}
//
//	/**
//	 * @param xOffset the xOffset to set
//	 */
//	public void setXOffset(Quantity offset) {
//		this.xOffset = offset;
//	}

	/**
	 * @return the numberOfEdges
	 */
	public Quantity getNumberOfEdges() {
		return degrees;
	}

	/**
	 * @param numberOfEdges the numberOfEdges to set
	 */
	public void setNumberOfEdges(Quantity numberOfEdges) {
		this.degrees = numberOfEdges;
	}

	/**
	 * @return the degrees
	 */
	public Quantity getDegrees() {
		return degrees;
	}

	/**
	 * @param degrees the degrees to set
	 */
	public void setDegrees(Quantity degrees) {
		this.degrees = degrees;
	}

	/**
	 * @return the zDistance
	 */
	public Quantity getZDistance() {
		return zDistance;
	}

	/**
	 * @param zDistance the zDistance to set
	 */
	public void setZDistance(Quantity zDistance) {
		this.zDistance = zDistance;
	}

//	/**
//	 * @return the zOffset
//	 */
//	public Quantity getZOffset() {
//		return zOffset;
//	}
//
//	/**
//	 * @param zOffset the zOffset to set
//	 */
//	public void setZOffset(Quantity offset) {
//		this.zOffset = offset;
//	}

	/**
	 * @return the yVariance
	 */
	public Quantity getYVariance() {
		return yVariance;
	}

	/**
	 * @param yVariance the yVariance to set
	 */
	public void setYVariance(Quantity yVariance) {
		this.yVariance = yVariance;
	}

	/**
	 * @return
	 */
	public Integer getSurfaceBuffer() {
		return this.surfaceBuffer;
	}

	/**
	 * @param surfaceBuffer the surfaceBuffer to set
	 */
	public void setSurfaceBuffer(Integer surfaceBuffer) {
		this.surfaceBuffer = surfaceBuffer;
	}

	/**
	 * @return the minecraftConstraintsOn
	 */
	public boolean isMinecraftConstraintsOn() {
		return minecraftConstraintsOn;
	}

	/**
	 * @param minecraftConstraintsOn the minecraftConstraintsOn to set
	 */
	public void setMinecraftConstraintsOn(boolean minecraftConstraintsOn) {
		this.minecraftConstraintsOn = minecraftConstraintsOn;
	}

	/**
	 * @return the decayMultiplier
	 */
	public int getDecayMultiplier() {
		return decayMultiplier;
	}

	/**
	 * @param decayMultiplier the decayMultiplier to set
	 */
	public void setDecayMultiplier(int decayMultiplier) {
		this.decayMultiplier = decayMultiplier;
	}

	/**
	 * @return the supportOn
	 */
	public boolean isSupportOn() {
		return supportOn;
	}

	/**
	 * @param supportOn the supportOn to set
	 */
	public void setSupportOn(boolean supportOn) {
		this.supportOn = supportOn;
	}

	/**
	 * @return the spawnerFrequency
	 */
	public Quantity getSpawnerFrequency() {
		return spawnerFrequency;
	}

	/**
	 * @param spawnerFrequency the spawnerFrequency to set
	 */
	public void setSpawnerFrequency(Quantity spawnerFrequency) {
		this.spawnerFrequency = spawnerFrequency;
	}

	/**
	 * @return the chestFrequency
	 */
	public Quantity getChestFrequency() {
		return chestFrequency;
	}

	/**
	 * @param chestFrequency the chestFrequency to set
	 */
	public void setChestFrequency(Quantity chestFrequency) {
		this.chestFrequency = chestFrequency;
	}

	/**
	 * @return the decorationsOn
	 */
	public boolean isDecorationsOn() {
		return decorationsOn;
	}

	/**
	 * @param decorationsOn the decorationsOn to set
	 */
	public void setDecorationsOn(boolean decorationsOn) {
		this.decorationsOn = decorationsOn;
	}

	/**
	 * @return the webFrequency
	 */
	public Quantity getWebFrequency() {
		return webFrequency;
	}

	/**
	 * @param webFrequency the webFrequency to set
	 */
	public void setWebFrequency(Quantity webFrequency) {
		this.webFrequency = webFrequency;
	}

	/**
	 * @return the vineFrequency
	 */
	public Quantity getVineFrequency() {
		return vineFrequency;
	}

	/**
	 * @param vineFrequency the vineFrequency to set
	 */
	public void setVineFrequency(Quantity vineFrequency) {
		this.vineFrequency = vineFrequency;
	}

	/**
	 * @return the numberOfWebs
	 */
	public Quantity getNumberOfWebs() {
		return numberOfWebs;
	}

	/**
	 * @param numberOfWebs the numberOfWebs to set
	 */
	public void setNumberOfWebs(Quantity numberOfWebs) {
		this.numberOfWebs = numberOfWebs;
	}

	/**
	 * @return the numberOfVines
	 */
	public Quantity getNumberOfVines() {
		return numberOfVines;
	}

	/**
	 * @param numberOfVines the numberOfVines to set
	 */
	public void setNumberOfVines(Quantity numberOfVines) {
		this.numberOfVines = numberOfVines;
	}

	/**
	 * @return the chestCategories
	 */
	public List<String> getChestCategories() {
		if (chestCategories == null) {
			chestCategories = new ArrayList<>();
		}
		return chestCategories;
	}

	/**
	 * @param chestCategories the chestCategories to set
	 */
	public void setChestCategories(List<String> chestCategories) {
		this.chestCategories = chestCategories;
	}

	public Quantity getAnywhereDecorationFrequency() {
		return anywhereDecorationFrequency;
	}

	public Quantity getNumberOfAnywhereDecorations() {
		return numberOfAnywhereDecorations;
	}

	/**
	 * @param anywhereDecorationFrequency the anywhereDecorationFrequency to set
	 */
	public void setAnywhereDecorationFrequency(Quantity anywhereDecorationFrequency) {
		this.anywhereDecorationFrequency = anywhereDecorationFrequency;
	}

	/**
	 * @param numberOfAnywhereDecorations the numberOfAnywhereDecorations to set
	 */
	public void setNumberOfAnywhereDecorations(Quantity numberOfAnywhereDecorations) {
		this.numberOfAnywhereDecorations = numberOfAnywhereDecorations;
	}

	public Quantity getBloodFrequency() {
		return bloodFrequency;
	}

	public Quantity getNumberOfBlood() {
		return numberOfBlood;
	}

	/**
	 * @param bloodFrequency the bloodFrequency to set
	 */
	public void setBloodFrequency(Quantity bloodFrequency) {
		this.bloodFrequency = bloodFrequency;
	}

	/**
	 * @param numberOfBlood the numberOfBlood to set
	 */
	public void setNumberOfBlood(Quantity numberOfBlood) {
		this.numberOfBlood = numberOfBlood;
	}

	public Quantity getPuddleFrequency() {
		return puddleFrequency;
	}

	public Quantity getNumberOfPuddles() {
		return numberOfPuddles;
	}

	/**
	 * @param puddleFrequency the puddleFrequency to set
	 */
	public void setPuddleFrequency(Quantity puddleFrequency) {
		this.puddleFrequency = puddleFrequency;
	}

	/**
	 * @param numberOfPuddles the numberOfPuddles to set
	 */
	public void setNumberOfPuddles(Quantity numberOfPuddles) {
		this.numberOfPuddles = numberOfPuddles;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "LevelConfig [numberOfRooms=" + numberOfRooms + ", width=" + width + ", depth=" + depth + ", height="
				+ height + ", xDistance=" + xDistance + ", zDistance=" + zDistance + ", yVariance=" + yVariance
				+ ", degrees=" + degrees + ", decayMultiplier=" + decayMultiplier + ", surfaceBuffer=" + surfaceBuffer
				+ ", spawnerFrequency=" + spawnerFrequency + ", chestFrequency=" + chestFrequency + ", chestCategories="
				+ chestCategories + ", webFrequency=" + webFrequency + ", numberOfWebs=" + numberOfWebs
				+ ", vineFrequency=" + vineFrequency + ", numberOfVines=" + numberOfVines
				+ ", anywhereDecorationFrequency=" + anywhereDecorationFrequency + ", numberOfAnywhereDecorations="
				+ numberOfAnywhereDecorations + ", bloodFrequency=" + bloodFrequency + ", numberOfBlood="
				+ numberOfBlood + ", puddleFrequency=" + puddleFrequency + ", numberOfPuddles=" + numberOfPuddles
				+ ", decorationsOn=" + decorationsOn + ", minecraftConstraintsOn=" + minecraftConstraintsOn
				+ ", supportOn=" + supportOn + "]";
	}

	/**
	 * @return the yRange
	 */
	public Quantity getYRange() {
		return yRange;
	}

	/**
	 * @param yRange the yRange to set
	 */
	public void setYRange(Quantity yRange) {
		this.yRange = yRange;
	}

	/**
	 * @return
	 */
	public int getSeaLevel() {
		return seaLevel;
	}
	
	public void setSeaLevel(int seaLevel) {
		this.seaLevel = seaLevel;
	}

}
