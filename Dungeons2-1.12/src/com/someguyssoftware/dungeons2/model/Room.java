package com.someguyssoftware.dungeons2.model;

import java.util.Comparator;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.someguyssoftware.dungeons2.style.DesignElement;
import com.someguyssoftware.dungeons2.style.Layout;
import com.someguyssoftware.gottschcore.enums.Direction;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.positional.Intersect;
import com.someguyssoftware.gottschcore.random.RandomHelper;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * 
 * @author Mark Gottschling on Jul 9, 2016
 *
 */
public class Room {
	public static final int MIN_DEPTH = 5;
	public static final int MIN_WIDTH = 5;
	public static final int MIN_HEIGHT = 4;
	
	private int id;
	private String name;
	private Type type;
	private ICoords coords;

	private int depth;
	private int width;
	private int height;
	
	private Direction direction;

	private double distance;
	
	private boolean anchor;
	private boolean obstacle;
	private boolean reject;
	private boolean start;
	private boolean end;

	// graphing
	private int degrees;
	
	// ui / styling
	private Layout layout;
	private boolean crown;
	private boolean trim;
	private boolean pilaster;	
	private boolean pillar;
	private boolean gutter;
	private boolean grate;
	private boolean coffer;
	
	private boolean wallBase;
	private boolean wallCapital;
	
	// TODO these next sets only belong to surface/exterior rooms and probably should be moved to a subclass
	private boolean cornice;
	private boolean plinth;	
	private boolean column;
	private boolean crenellation;
	private boolean parapet;
	private boolean merlon;
	

	private Multimap<DesignElement, ICoords> floorMap;
	
	/**
	 * 
	 */
	public Room() {
		setId(RandomHelper.randomInt(-5000, 5000));
		coords = new Coords(0,0,0);
		setType(Type.GENERAL);		
		setDirection(Direction.SOUTH); // South
	}

	/**
	 * 
	 * @param id
	 */
	public Room(int id) {
		this();
		setId(id);
	}
	
	/**
	 * 
	 * @param NAME
	 */
	public Room(String name) {
		this();
		setName(name);
	}
	
//	/**
//	 * initialize
//	 */
//	private void init() {
//		// generate (pseudo) unique id
//		//setId(new Random().nextInt(5000));
//		setId(WorldUtil.randomInt(-5000, 5000));
//		coords = new Coords(0,0,0);		
//	}
	
	/**
	 * 
	 * @param room
	 */
	public Room(Room room) {
		if (room != null) {
			setId(room.getId());
			setAnchor(room.isAnchor());
//			setCenter(new Coords(room.getCenter()));
			setCoords(new Coords(room.getCoords()));
			setDepth(room.getDepth());
			setDistance(room.getDistance());
			setHeight(room.getHeight());
//			setQuad(room.getQuad());
			setWidth(room.getWidth());		
			setStart(room.isStart());
			setEnd(room.isEnd());
			setReject(room.isReject());
			setType(room.getType());
			setDirection(room.getDirection());
			setDegrees(room.getDegrees());
			setName(room.getName());
			setObstacle(room.isObstacle());
			
			// todo copy all the styling properties
		}
	}
	
	/**
	 * Copy Constructor
	 * @return
	 */
	public Room copy() {
		return new Room(this);
	}
	
	// TODO this is probably wrong needs to -1 ??
	/**
	 * 
	 * @return
	 */
	public AxisAlignedBB getBoundingBox() {
		BlockPos bp1 = getCoords().toPos();
		BlockPos bp2 = getCoords().add(getWidth(), getHeight(), getDepth()).toPos();
		AxisAlignedBB bb = new AxisAlignedBB(bp1, bp2);
		return bb;
	}
	
	/**
	 * Creates a bounding box by the XZ dimensions with a height (Y) of 1
	 * @return
	 */
	public AxisAlignedBB getXZBoundingBox() {
		BlockPos bp1 = new BlockPos(getCoords().getX(), 0, getCoords().getZ());
		BlockPos bp2 = getCoords().add(getWidth(), 1, getDepth()).toPos();
		AxisAlignedBB bb = new AxisAlignedBB(bp1, bp2);
		return bb;
	}
	
    /**
     * Get a Facing by it's horizontal index (0-3). The order is S-W-N-E.
     */
    public static EnumFacing getHorizontal(int direction) {
        return EnumFacing.HORIZONTALS[MathHelper.abs(direction % EnumFacing.HORIZONTALS.length)];
    }
    
	public int getMinX() {
		return this.getCoords().getX();
	}
	
	public int getMaxX() {
		return this.getCoords().getX() + this.getWidth() - 1;
	}
	
	public int getMinY() {
		return this.getCoords().getY();
	}
	
	public int getMaxY() {
		return this.getCoords().getY() + this.getHeight() - 1;
	}
	
	/**
	 * 
	 * @return
	 */
	public int getMinZ() {
		return this.getCoords().getZ();
	}
	
	/**
	 * 
	 * @return
	 */
	public int getMaxZ() {
		return this.getCoords().getZ() + this.getDepth() - 1;
	}
	
	/**
	 * 
	 * @return
	 */
	public ICoords getCenter() {
		int x = this.getCoords().getX()  + ((this.getWidth()-1) / 2) ;
		int y = this.getCoords().getY()  + ((this.getHeight()-1) / 2);
		int z = this.getCoords().getZ()  + ((this.getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;
	}
	
	/**
	 * 
	 * @return
	 */
	public ICoords getXZCenter() {
		int x = this.getCoords().getX()  + ((this.getWidth()-1) / 2);
		int y = this.getCoords().getY();
		int z = this.getCoords().getZ()  + ((this.getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;
	}
	
	/**
	 * 
	 * @return
	 */
	public ICoords getTopCenter() {
		int x = this.getCoords().getX()  + ((this.getWidth()-1) / 2);
		int y = this.getCoords().getY() + this.getHeight();
		int z = this.getCoords().getZ()  + ((this.getDepth()-1) / 2);
		ICoords coords = new Coords(x, y, z);
		return coords;	
	}
	
	/**
	 * 
	 * @param room
	 * @return
	 */
	public Intersect getIntersect(Room room) {
		return Intersect.getIntersect(this.getBoundingBox(), room.getBoundingBox());
	}
	
	/**
	 * Returns a new Room with the force applied at the angle on the XZ plane.
	 * @param angle
	 * @param force
	 * @return
	 */
	public Room addXZForce(double angle, double force) {
		double xForce = Math.sin(angle) * force;
        double zForce = Math.cos(angle) * force;
        
        Room room = new Room(this);
        room.setCoords(room.getCoords().add((int)xForce, 0, (int)zForce));
        return room;
	}
	
	/**
	 * Comparator to sort by Id
	 */
	public static Comparator<Room> idComparator = new Comparator<Room>() {
		@Override
		public int compare(Room p1, Room p2) {
			if (p1.getId() > p2.getId()) {
				// greater than
				return 1;
			}
			else {
				// less than
				return -1;
			}
		}
	};
	
	/**
	 * Comparator to sort plans by set weight
	 */
	public static Comparator<Room> distanceComparator = new Comparator<Room>() {
		@Override
		public int compare(Room p1, Room p2) {
			if (p1.getDistance() > p2.getDistance()) {
				// greater than
				return 1;
			}
			else {
				// less than
				return -1;
			}
		}
	};
		
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
	 * @return the coords
	 */
	public ICoords getCoords() {
		return coords;
	}

	/**
	 * @param coords the coords to set
	 */
	public Room setCoords(ICoords coords) {
		this.coords = coords;
		return this;
	}

	/**
	 * @return the center
	 */
//	public ICoords getCenter() {
//		return center;
//	}

	/**
	 * @param center the center to set
	 */
//	public void setCenter(ICoords center) {
//		this.center = center;
//	}

	/**
	 * @return the depth
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * @param depth the depth to set
	 */
	public Room setDepth(int depth) {
		this.depth = depth;
		return this;
	}

	/**
	 * @return the width
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * @param width the width to set
	 */
	public Room setWidth(int width) {
		this.width = width;
		return this;
	}

	/**
	 * @return the height
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * @param height the height to set
	 */
	public Room setHeight(int height) {
		this.height = height;
		return this;
	}

	/**
	 * @return the anchor
	 */
	public boolean isAnchor() {
		return anchor;
	}

	/**
	 * @param anchor the anchor to set
	 */
	public Room setAnchor(boolean anchor) {
		this.anchor = anchor;
		return this;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Room [\n\tid=" + id + ",\n\tname=" + name + ",\n\tcoords=" + coords + ",\n\tcenter=" + getCenter() + ",\n\tdepth=" + depth
				+ ",\n\twidth=" + width + ",\n\theight=" + height + ",\n\tanchor=" + anchor + ",\n\tdistance=" + distance
				+ ",\n\tdirection=" + direction + ",\n\treject=" + reject
				+ ",\n\tlayout=" + layout + 
				",\n\tstart=" + start + "\n\tend=" + end +  ",\n\tboundingBox" + getBoundingBox() + "\n]";
	}

	/**
	 * @return the distance
	 */
	public double getDistance() {
		return distance;
	}

	/**
	 * @param distance the distance to set
	 */
	public void setDistance(double distance) {
		this.distance = distance;
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
	 * @return the reject
	 */
	public boolean isReject() {
		return reject;
	}

	/**
	 * @param reject the reject to set
	 */
	public void setReject(boolean reject) {
		this.reject = reject;
	}
	
	public enum Type {
		GENERAL("general"),
		LADDER("ladder"),
		ENTRANCE("entrance"),
		EXIT("exit"),
		TREASURE("treasure"),
		BOSS("boss"),
		HALLWAY("hallway");

		private String name;
		
		/**
		 * @param arg0
		 * @param arg1
		 */
		Type(String name) {
			this.name = name;
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
	}

	/**
	 * @return the start
	 */
	public boolean isStart() {
		return start;
	}

	/**
	 * @param start the start to set
	 */
	public Room setStart(boolean start) {
		this.start = start;
		return this;
	}

	/**
	 * @return the end
	 */
	public boolean isEnd() {
		return end;
	}

	/**
	 * @param end the end to set
	 */
	public Room setEnd(boolean end) {
		this.end = end;
		return this;
	}

	/**
	 * @return the type
	 */
	public Type getType() {
		return type;
	}

	/**
	 * @param type the type to set
	 */
	public Room setType(Type type) {
		this.type = type;
		return this;
	}

	/**
	 * @return the degrees
	 */
	public int getDegrees() {
		return degrees;
	}

	/**
	 * @param degrees the degrees to set
	 */
	public Room setDegrees(int degrees) {
		this.degrees = degrees;
		return this;
	}

	/**
	 * @return the obstacle
	 */
	public boolean isObstacle() {
		return obstacle;
	}

	/**
	 * @param obstacle the obstacle to set
	 */
	public void setObstacle(boolean obstacle) {
		this.obstacle = obstacle;
	}

	/**
	 * @return the direction
	 */
	public Direction getDirection() {
		return direction;
	}

	/**
	 * @param direction the direction to set
	 */
	public Room setDirection(Direction direction) {
		this.direction = direction;
		return this;
	}

	/**
	 * @return the layout
	 */
	public Layout getLayout() {
		return layout;
	}

	/**
	 * @param layout the layout to set
	 */
	public Room setLayout(Layout layout) {
		this.layout = layout;
		return this;
	}

	/**
	 * Lazy-loaded getter.
	 * @return the floorMap
	 */
	public Multimap<DesignElement, ICoords> getFloorMap() {
		if (floorMap == null) {
			floorMap = ArrayListMultimap.create();
		}
		return floorMap;
	}

	/**
	 * @param floorMap the floorMap to set
	 */
	public void setFloorMap(Multimap<DesignElement, ICoords> floorMap) {
		this.floorMap = floorMap;
	}

	/**
	 * @return the trim
	 */
	public boolean hasTrim() {
		return trim;
	}

	/**
	 * @param trim the trim to set
	 */
	public void setHasTrim(boolean trim) {
		this.trim = trim;
	}

	/**
	 * @return the cornice
	 */
	public boolean hasCornice() {
		return cornice;
	}

	/**
	 * @param cornice the cornice to set
	 */
	public void setHasCornice(boolean cornice) {
		this.cornice = cornice;
	}

	/**
	 * @return the plinth
	 */
	public boolean hasPlinth() {
		return plinth;
	}

	/**
	 * @param plinth the plinth to set
	 */
	public void setHasPlinth(boolean plinth) {
		this.plinth = plinth;
	}

	/**
	 * @return the pillar
	 */
	public boolean hasPillar() {
		return pillar;
	}

	/**
	 * @param pillar the pillar to set
	 */
	public void setHasPillar(boolean pillar) {
		this.pillar = pillar;
	}

	/**
	 * @return the column
	 */
	public boolean hasColumn() {
		return column;
	}

	/**
	 * @param column the column to set
	 */
	public void setHasColumn(boolean column) {
		this.column = column;
	}

	/**
	 * @return the crown
	 */
	public boolean hasCrown() {
		return crown;
	}

	/**
	 * @param crown the crown to set
	 */
	public void setHasCrown(boolean crown) {
		this.crown = crown;
	}

	/**
	 * @return the crenellation
	 */
	public boolean hasCrenellation() {
		return crenellation;
	}

	/**
	 * @param crenellation the crenellation to set
	 */
	public void setHasCrenellation(boolean crenellation) {
		this.crenellation = crenellation;
	}

	/**
	 * @return the parapet
	 */
	public boolean hasParapet() {
		return parapet;
	}

	/**
	 * @param parapet the parapet to set
	 */
	public void setHasParapet(boolean parapet) {
		this.parapet = parapet;
	}

	/**
	 * @return the merlon
	 */
	public boolean hasMerlon() {
		return merlon;
	}

	/**
	 * @param merlon the merlon to set
	 */
	public void setHasMerlon(boolean merlon) {
		this.merlon = merlon;
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean hasPilaster() {
		return pilaster;
	}
	
	/**
	 * 
	 * @param pilaster
	 */
	public void setHasPilaster(boolean pilaster) {
		this.pilaster = pilaster;
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean hasGutter() {
		return gutter;		
	}
	
	/**
	 * 
	 * @param gutter
	 */
	public void setHasGutter(boolean gutter) {
		this.gutter = gutter;
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean hasGrate() {
		return grate;
	}
	
	/**
	 * 
	 * @param grate
	 */
	public void setHasGrate(boolean grate) {
		this.grate = grate;
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean hasCoffer() {
		return this.coffer;
	}
	
	/**
	 * 
	 * @param coffer
	 */
	public void setHasCoffer(boolean coffer) {
		this.coffer = coffer;
	}
	
	/**
	 * 
	 * @param coffer
	 */
	public void setCoffer(boolean coffer) {
		setHasCoffer(coffer);
	}
	
	/**
	 * 
	 * @param base
	 * @return
	 */
	public boolean hasWallBase() {
		return this.wallBase;
	}
	
	/**
	 * 
	 * @param base
	 */
	public void setHasWallBase(boolean base) {
		this.wallBase = base;
	}
	
	/**
	 * 
	 * @param base
	 */
	public void setWallBase(boolean base) {
		setHasWallBase(base);
	}
	
	/**
	 * 
	 * @return
	 */
	public boolean hasWallCapital() {
		return this.wallCapital;
	}
	
	/**
	 * 
	 * @param capital
	 */
	public void setHasWallCapital(boolean capital) {
		this.wallCapital = capital;
	}
	
	/**
	 * 
	 * @param capital
	 */
	public void setWallCapital(boolean capital) {
		setHasWallCapital(capital);
	}
}
