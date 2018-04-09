/**
 * 
 */
package com.someguyssoftware.gottschcore.enums;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.util.EnumFacing;

/**
 * 
 * @author Mark Gottschling on Feb 27, 2015
 *
 */
public enum Direction implements IEnum {
	UP(0, "Up", Alignment.VERTICAL),
	DOWN(1, "Down", Alignment.VERTICAL),
	NORTH(2, "North", Alignment.HORIZONTAL),
	EAST(3, "East", Alignment.HORIZONTAL),
	SOUTH(4, "South", Alignment.HORIZONTAL),
	WEST(5, "West", Alignment.HORIZONTAL);
	
	private static final Map<Integer, IEnum> codes = new HashMap<Integer, IEnum>();
	private static final Map<String, IEnum> values = new HashMap<String, IEnum>();
	private Integer code;
	private String value;
	private Alignment alignment;
	
	// setup reverse lookup
	static {
		for (Direction ps : EnumSet.allOf(Direction.class)) {
			codes.put(ps.getCode(), ps);
			values.put(ps.getValue(), ps);
		}
	}
	
	/**
	 * Full constructor
	 * @param code
	 * @param value
	 */
	Direction(Integer code, String value) {
		this.code = code;
		this.value = value;
	}

	/**
	 * 
	 * @param code
	 * @param value
	 * @param plane
	 */
	Direction(Integer code, String value, Alignment plane) {
		this.code = code;
		this.value = value;
		this.alignment = plane;
	}

	/**
	 * 
	 * @param plane
	 * @return
	 */
	public boolean isSamePlane(Alignment plane) {
		if (this.getAlignment() == plane) {
			return true;
		}
		return false;
	}
	
	/**
	 * Rotate the current direction by rotation amount.
	 * @param r
	 * @return
	 */
	public Direction rotate(Rotate r) {
		switch(r)	 {
		case NO_ROTATE: 
			return this;
		case ROTATE_90:
			switch (this) {
			case NORTH:
				return Direction.EAST;
			case EAST:
				return Direction.SOUTH;
			case SOUTH:
				return Direction.WEST;
			case WEST:
				return Direction.NORTH;
			default:
				return this;
			}
		case ROTATE_180:
			switch (this) {
			case NORTH:
				return Direction.SOUTH;
			case EAST:
				return Direction.WEST;
			case SOUTH:
				return Direction.NORTH;
			case WEST:
				return Direction.EAST;
			default:
				return this;
			}
		case ROTATE_270:
			switch (this) {
			case NORTH:
				return Direction.WEST;
			case EAST:
				return Direction.NORTH;
			case SOUTH:
				return Direction.EAST;
			case WEST:
				return Direction.SOUTH;
			default:
				return this;
			}			
		default:
			return this;
		}
	}
	
	/**
	 * 
	 * @param direction
	 * @return
	 */
	public Rotate getRotation(Direction direction) {
		switch (direction) {
		case NORTH:
			switch(this) {
			case NORTH: return Rotate.NO_ROTATE;
			case EAST: return Rotate.ROTATE_90;
			case SOUTH: return Rotate.ROTATE_180;
			case WEST: return Rotate.ROTATE_270;
			default: return Rotate.NO_ROTATE;
			}
		case EAST:
			switch(this) {
			case NORTH: return Rotate.ROTATE_90;
			case EAST: return Rotate.NO_ROTATE;
			case SOUTH: return Rotate.ROTATE_270;
			case WEST: return Rotate.ROTATE_180;
			default: return Rotate.NO_ROTATE;
			}				
		case SOUTH:
			switch(this) {
			case NORTH: return Rotate.ROTATE_180;
			case EAST: return Rotate.ROTATE_270;
			case SOUTH: return Rotate.NO_ROTATE;
			case WEST: return Rotate.ROTATE_90;
			default: return Rotate.NO_ROTATE;
			}			
		case WEST:
			switch(this) {
			case NORTH: return Rotate.ROTATE_270;
			case EAST: return Rotate.ROTATE_180;
			case SOUTH: return Rotate.ROTATE_90;
			case WEST: return Rotate.NO_ROTATE;
			default: return Rotate.NO_ROTATE;
			}	
		default:
			return Rotate.NO_ROTATE;
		}
	}
	
	/**
	 * 
	 * @param facing
	 * @return
	 */
	public static Direction fromFacing(EnumFacing facing) {
		switch (facing) {
		case NORTH: return Direction.NORTH;
		case EAST: return Direction.EAST;
		case SOUTH: return Direction.SOUTH;
		case WEST: return Direction.WEST;
		default: return Direction.NORTH;
		}
	}
	
	@Override
	public String getName() {
		return name();
	}
	
	@Override
	public Integer getCode() {
		return code;
	}

	@Override
	public void setCode(Integer code) {
		this.code = code;
	}

	@Override
	public String getValue() {
		return value;
	}

	@Override
	public void setValue(String value) {
		this.value = value;
	}
	
	/**
	 * 
	 * @param code
	 * @return
	 */
	public static Direction getByCode(Integer code) {
		return (Direction) codes.get(code);
	}
	/**
	 * 
	 * @param value
	 * @return
	 */
	public static Direction getByValue(String value) {
		return (Direction) values.get(value);
	}

	/**
	 * 
	 */
	@Override
	public Map<Integer, IEnum> getCodes() {
		return codes;
	}

	/**
	 * 
	 * @return
	 */
	@Override
	public Map<String, IEnum> getValues() {
		return values;
	}
	
	/**
	 * 
	 * @param alignment
	 * @return
	 */
	public Map<String, IEnum> getValuesByAlignment(Alignment alignment) {
		Map<String, IEnum> map = new HashMap<>();
		for (Entry<String, IEnum> e : getValues().entrySet()) {
			if (((Direction)e.getValue()).getAlignment() == alignment) {
				map.put(e.getKey(), e.getValue());
			}
		}
		return map;
	}

	/**
	 * @return the alignment
	 */
	public Alignment getAlignment() {
		return alignment;
	}

	/**
	 * @param alignment the alignment to set
	 */
	public void setAlignment(Alignment alignment) {
		this.alignment = alignment;
	}
}
