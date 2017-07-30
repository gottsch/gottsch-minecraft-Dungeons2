/**
 * 
 */
package com.someguyssoftware.dungeons2.style;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.someguyssoftware.gottschcore.enums.IEnum;


/**
 * TODO the entire DesignElement enum has scope-creeped and become very messy.
 * It should be completely re-designed, without sub-elements, but using different sets of enums.
 * Better yet, it should become it's own class, with properties for rotation, provides support, etc.
 * NOTE could potentially use abstract & implemented methods on each of the enum values
 * @author Mark Gottschling on Jul 31, 2016
 *
 */
public enum DesignElement implements IEnum {
	NONE(-1, "none", 0, 0),
	
	// conceptual elements
	AIR(0, "air", 0, 0),	
	SURFACE_AIR(1, "surface_air", 0, 0),
	FLOOR_AIR(2, "floor_air", 0, 0, SURFACE_AIR),
	WALL_AIR(3, "wall_air", 0, 0, SURFACE_AIR),
	CEILING_AIR(4, "ceiling_air", 0, 0, SURFACE_AIR),
	FACADE(5, "facade", 100, 0),

	// concrete elements
	FLOOR(101, "floor", 100, 50),
	WALL(102, "wall", 100, 50),
	CEILING(103, "ceiling", 100, 50),
	CROWN(104, "crown", 100, 0, Face.INTERIOR, FACADE),
	TRIM(105, "trim", 0, 0, Face.INTERIOR, FACADE),
	CORNICE(106, "cornice", 100, 0, Face.EXTERIOR, FACADE),
	PLINTH(107, "plinth", 0, 0, Face.EXTERIOR, FACADE),				// bottom of a wall or pedastal
	PILLAR(108, "pillar", 100, 100, Face.INTERIOR),
	PILASTER(109, "pilaster", 100, 100, Face.INTERIOR),
	COLUMN(110, "column", 100, 100, Face.EXTERIOR),
	BASE(111, "base", 100, 0, Face.EXTERIOR),
	CAPITAL(112, "capital", 100, 0, Face.EXTERIOR),
	COFFERED_MIDBEAM(113, "coffered_mid_beam", 100, 100, Face.INTERIOR),
	COFFERED_CROSSBEAM(114, "coffered_crossbeam", 100, 100, Face.INTERIOR),
	GUTTER(115, "gutter", 100, 50),
	GRATE(116, "grate", 100, 50),
	SCONCE(117, "sconce", 0, 0),
	DOOR(118, "door", 100, 50),
	LADDER(119, "ladder", 100, 0),
	LADDER_PILLAR(120, "ladder_pillar", 100, 100),
	CHEST(122, "chest", 100, 0),
	SPAWNER(123, "spawner", 100, 0),	
	CRENELLATION(124, "crenellation", 100, 50, Face.EXTERIOR),
	PARAPET(125, "parapet", 100, 50, Face.EXTERIOR),
	MERLON(126, "merlon", 100, 50, Face.EXTERIOR),
	PILASTER_CAPITAL(127, "pilaster_capital", 100, 0, Face.INTERIOR),
	PILASTER_BASE(128, "pilaster_base", 100, 0, Face.INTERIOR),
	PILLAR_CAPITAL(129, "pillar_capital", 100, 0, Face.INTERIOR),
	PILLAR_BASE(130, "pillar_base", 100, 0, Face.INTERIOR),
	WINDOW(131, "widow", 100, 0),
	
	FLOOR_ALT(132, "floor_alt", 100, 50, FLOOR),
	WALL_BASE(133, "wall_base", 100, 50, WALL),
	WALL_CAPITAL(134, "wall_capital", 10, 50, WALL),
	
	/*
	 *  conceptual supporting elements
	 */
	// an element that is embedded in wall or ceiling where it will be used to support
	// decorative elements. ie behind cornice or in ceiling so that "overhang" does not decay
	// WALL is the base element to use if the element seeking support isn't of a family FACADE.
	FACADE_SUPPORT(6, "facade_support", 100, 100, WALL);
	
	
	// PEDIMENT (top of building or tower - triangle, decoration)
	// LINTEL (horizontal  support over door, window)
	// BEAM (horizontal support of ceiling, like coffered, but a full block)
	
	private static final Map<Integer, IEnum> codes = new HashMap<Integer, IEnum>();
	private static final Map<String, IEnum> values = new HashMap<String, IEnum>();
	private Integer code;
	private String value;
	private Integer horizontalSupport;
	private Integer verticalSupport;
	private Face face;
	private DesignElement family;
	
	// setup reverse lookup
	static {
		for (DesignElement x : EnumSet.allOf(DesignElement.class)) {
			codes.put(x.getCode(), x);
			values.put(x.getValue(), x);
		}
	}
	

	/**
	 * 
	 * @param code
	 * @param value
	 * @param vSupport
	 * @param hSupport
	 */
	DesignElement(Integer code, String value, Integer vSupport, Integer hSupport) {
		this.code = code;
		this.value = value;		
		this.horizontalSupport = hSupport;
		this.verticalSupport = vSupport;
	}
    

	/**
	 * 
	 * @param code
	 * @param value
	 * @param vSupport
	 * @param hSupport
	 * @param face
	 */
	DesignElement(Integer code, String value, Integer vSupport, Integer hSupport, Face face) {
		this.code = code;
		this.value = value;		
		this.horizontalSupport = hSupport;
		this.verticalSupport = vSupport;
		this.face = face;
	}
	
	/**
	 * 
	 * @param code
	 * @param value
	 * @param vSupport
	 * @param hSupport
	 * @param family
	 */
	DesignElement(Integer code, String value, Integer vSupport, Integer hSupport, DesignElement family) {
		this.code = code;
		this.value = value;		
		this.horizontalSupport = hSupport;
		this.verticalSupport = vSupport;
		this.family = family;
	}
	
	/**
	 * 
	 * @param code
	 * @param value
	 * @param vSupport
	 * @param hSupport
	 * @param face
	 * @param family
	 */
	DesignElement(Integer code, String value, Integer vSupport, Integer hSupport, Face face, DesignElement family) {
		this.code = code;
		this.value = value;		
		this.horizontalSupport = hSupport;
		this.verticalSupport = vSupport;
		this.face = face;
		this.family = family;
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
	public static DesignElement getByCode(Integer code) {
		return (DesignElement) codes.get(code);
	}
	/**
	 * 
	 * @param value
	 * @return
	 */
	public static DesignElement getByValue(String value) {
		return (DesignElement) values.get(value);
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
	 * @return the horizontalSupport
	 */
	public Integer getHorizontalSupport() {
		return horizontalSupport;
	}

	/**
	 * @param horizontalSupport the horizontalSupport to set
	 */
	public void setHorizontalSupport(Integer support) {
		this.horizontalSupport = support;
	}

	/**
	 * @return the verticalSupport
	 */
	public Integer getVerticalSupport() {
		return verticalSupport;
	}

	/**
	 * @param verticalSupport the verticalSupport to set
	 */
	public void setVerticalSupport(Integer verticalSupport) {
		this.verticalSupport = verticalSupport;
	}

	/**
	 * @return the face
	 */
	public Face getFace() {
		return face;
	}

	/**
	 * @param face the face to set
	 */
	public void setFace(Face face) {
		this.face = face;
	}

	/**
	 * @return the family
	 */
	public DesignElement getFamily() {
		return family;
	}

	/**
	 * @param family the family to set
	 */
	public void setFamily(DesignElement family) {
		this.family = family;
	}

}
