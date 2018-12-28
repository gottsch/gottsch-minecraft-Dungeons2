/**
 * 
 */
package com.someguyssoftware.dungeonsengine.model;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import com.someguyssoftware.dungeonsengine.config.LevelConfig;
import com.someguyssoftware.gottschcore.positional.BBox;
import com.someguyssoftware.gottschcore.positional.ICoords;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/**
 * @author Mark Gottschling on Jul 18, 2016
 * @version 2.0
 * @since 1.0.0
 *
 */
public class Level implements ILevel {
	private int id;
	private String name;
	private ICoords spawnPoint;
	private ICoords startPoint;
	private IVoid start;
	private IVoid end;
	private List<IVoid> voids;
	
	private Boundary boundary;
	// TODO maybe create a VisualLevel extends Level that contains the transient data and is only set if a flag is set
	// transient - needed only for visualizing
//	private List<Edge> edges;
	// transient - needed only for visualizing
//	private List<Edge> paths;

	/**
	 * @since 2.0
	 */
//	private List<Hallway> hallways;
//	private List<IShaft> shafts;
	
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

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getStartRoom()
	 */
	@Override
	public IVoid getStart() {
		return start;
	}


	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setStartRoom(com.someguyssoftware.dungeonsengine.model.IVoid)
	 */
	@Override
	public void setStart(IVoid start) {
		this.start = start;
	}


	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getEndRoom()
	 */
	@Override
	public IVoid getEnd() {
		return end;
	}


	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setEndRoom(com.someguyssoftware.dungeonsengine.model.IVoid)
	 */
	@Override
	public void setEnd(IVoid end) {
		this.end = end;
	}


	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getRooms()
	 */
	@Override
	public List<IVoid> getVoids() {
		if (this.voids == null) this.voids = new ArrayList<>();
		return voids;
	}


	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setRooms(java.util.List)
	 */
	@Override
	public void setVoids(List<IVoid> rooms) {
		this.voids = rooms;
	}


	/**
	 * @param startRoom
	 * @param endRoom
	 * @param voids
	 */
	public Level(IVoid start, IVoid end, List<IVoid> voids) {
		super();
		this.start = start;
		this.end = end;
		this.voids = voids;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getBoundingBox()
	 */
	@Override
	public BBox getBoundingBox() {
		// TODO don't use vanilla classes
		BlockPos bp1 = getStartPoint().toPos();
		BlockPos bp2 = getStartPoint().add(getWidth(), getStartPoint().getY()+1, getDepth()).toPos(); // TODO update to actual height
		AxisAlignedBB bb = new AxisAlignedBB(bp1, bp2);
		return new BBox(bb);
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getXZBoundingBox()
	 */
//	@Override
//	public AxisAlignedBB getXZBoundingBox() {
//		BlockPos bp1 = new BlockPos(getStartPoint().getX(), 0, getStartPoint().getZ());
//		BlockPos bp2 = getStartPoint().add(getWidth(), 1, getDepth()).toPos();
//		AxisAlignedBB bb = new AxisAlignedBB(bp1, bp2);
//		return bb;
//		return this.getField();
//	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getId()
	 */
	@Override
	public int getId() {
		return id;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setId(int)
	 */
	@Override
	public void setId(int id) {
		this.id = id;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setName(java.lang.String)
	 */
	@Override
	public void setName(String name) {
		this.name = name;
	}

//	/**
//	 * @return the edges
//	 */
//	public List<Edge> getEdges() {
//		return edges;
//	}
//
//	/**
//	 * @param edges the edges to set
//	 */
//	public void setEdges(List<Edge> edges) {
//		this.edges = edges;
//	}
//
//	/**
//	 * @return the paths
//	 */
//	public List<Edge> getPaths() {
//		return paths;
//	}
//
//	/**
//	 * @param paths the paths to set
//	 */
//	public void setPaths(List<Edge> paths) {
//		this.paths = paths;
//	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getMinX()
	 */
	@Override
	public int getMinX() {
		return minX;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setMinX(int)
	 */
	@Override
	public void setMinX(int minX) {
		this.minX = minX;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getMaxX()
	 */
	@Override
	public int getMaxX() {
		return maxX;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setMaxX(int)
	 */
	@Override
	public void setMaxX(int maxX) {
		this.maxX = maxX;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getMinY()
	 */
	@Override
	public int getMinY() {
		return minY;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setMinY(int)
	 */
	@Override
	public void setMinY(int minY) {
		this.minY = minY;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getMaxY()
	 */
	@Override
	public int getMaxY() {
		return maxY;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setMaxY(int)
	 */
	@Override
	public void setMaxY(int maxY) {
		this.maxY = maxY;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getMinZ()
	 */
	@Override
	public int getMinZ() {
		return minZ;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setMinZ(int)
	 */
	@Override
	public void setMinZ(int minZ) {
		this.minZ = minZ;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getMaxZ()
	 */
	@Override
	public int getMaxZ() {
		return maxZ;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setMaxZ(int)
	 */
	@Override
	public void setMaxZ(int maxZ) {
		this.maxZ = maxZ;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getStartPoint()
	 */
	@Override
	public ICoords getStartPoint() {
		return startPoint;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setStartPoint(com.someguyssoftware.gottschcore.positional.ICoords)
	 */
	@Override
	public void setStartPoint(ICoords startPoint) {
		this.startPoint = startPoint;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getConfig()
	 */
	@Override
	public ILevelConfig getConfig() {
		return config;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setConfig(com.someguyssoftware.dungeonsengine.config.LevelConfig)
	 */
	@Override
	public void setConfig(ILevelConfig config) {
		this.config = config;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getShafts()
	 */
//	@Override
//	public List<IShaft> getShafts() {
//		if (shafts == null) {
//			this.shafts = new ArrayList<>();
//		}
//		return shafts;
//	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setShafts(java.util.List)
	 */
//	@Override
//	public void setShafts(List<IShaft> shafts) {
//		this.shafts = shafts;
//	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getHallways()
	 */
//	@Override
//	public List<Hallway> getHallways() {
//		if (hallways == null) {
//			this.hallways = new ArrayList<>();
//		}
//		return hallways;
//	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setHallways(java.util.List)
	 */
//	@Override
//	public void setHallways(List<Hallway> hallways) {
//		this.hallways = hallways;
//	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getSpawnPoint()
	 */
	@Override
	public ICoords getSpawnPoint() {
		return spawnPoint;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setSpawnPoint(com.someguyssoftware.gottschcore.positional.ICoords)
	 */
	@Override
	public void setSpawnPoint(ICoords spawnPoint) {
		this.spawnPoint = spawnPoint;
	}

//	/**
//	 * @return the length
//	 */
//	@Deprecated
//	public int getDepth() {
//		return depth;
//	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getDepth()
	 */
	@Override
	public int getDepth() {
		return (int) (getBoundary().getMaxCoords().getZ() - getBoundary().getMinCoords().getZ());
	}
	
//
//	/**
//	 * @param length the length to set
//	 */
//	public void setDepth(int length) {
//		this.depth = length;
//	}

//	/**
//	 * @return the width
//	 */
//	@Deprecated
//	public int getWidth() {
//		return width;
//	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getWidth()
	 */
	@Override
	public int getWidth() {
		return (int) (getBoundary().getMaxCoords().getX() - getBoundary().getMinCoords().getX());
	}
	
//
//	/**
//	 * @param width the width to set
//	 */
//	public void setWidth(int width) {
//		this.width = width;
//	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#getField()
	 */
	@Override
	public Boundary getBoundary() {
		return boundary;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.model.ILevel#setField(net.minecraft.util.math.AxisAlignedBB)
	 */
	@Override
	public void setBoundary(Boundary boundary) {
		this.boundary = boundary;
	}

}
