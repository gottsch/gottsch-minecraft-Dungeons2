/**
 * 
 */
package com.someguyssoftware.dungeonsengine.style;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import com.someguyssoftware.dungeonsengine.enums.Face;
import com.someguyssoftware.dungeonsengine.style.ArchitecturalElement;
import com.someguyssoftware.dungeonsengine.style.IArchitecturalElement;
import com.someguyssoftware.gottschcore.enums.IEnum;

/**
 * @author Mark Gottschling on Aug 19, 2018
 *
 */
public class Elements {
	public static IArchitecturalElement NONE = new ArchitecturalElement("none", -1, -1);

	public static IArchitecturalElement AIR = new ArchitecturalElement("air", 0, 0);
	public static IArchitecturalElement SURFACE_AIR = new ArchitecturalElement("surface_air", 0, 0, AIR);
	public static IArchitecturalElement FLOOR_AIR = new ArchitecturalElement("floor_air", 0, 0, SURFACE_AIR);
	public static IArchitecturalElement WALL_AIR = new ArchitecturalElement("wall_air", 0, 0, SURFACE_AIR);
	public static IArchitecturalElement CEILING_AIR = new ArchitecturalElement("ceiling_air", 0, 0, SURFACE_AIR);

	public static IArchitecturalElement FLOOR = new ArchitecturalElement("floor", 100, 50);
	public static IArchitecturalElement FLOOR_ALT = new ArchitecturalElement("floor_alt", 100, 50);
	public static IArchitecturalElement WALL = new ArchitecturalElement("wall", 100, 50);
	public static IArchitecturalElement WALL_BASE = new ArchitecturalElement("wall_base", 100, 50, WALL);
	public static IArchitecturalElement WALL_CAPITAL = new ArchitecturalElement("wall_capital", 100, 50, WALL);
	public static IArchitecturalElement CEILING = new ArchitecturalElement("ceiling", 100, 50);
	
	// legecy - TODO remove
	public static IArchitecturalElement FACADE = new ArchitecturalElement("facade", 100, 0);
	public static IArchitecturalElement FACADE_SUPPORT = new ArchitecturalElement("facade_support", 100, 100, WALL);
		
	public static IArchitecturalElement BASE = new ArchitecturalElement("base", 100, 0).setFace(Face.EXTERIOR);
	public static IArchitecturalElement COLUMN = new ArchitecturalElement("column", 100, 100).setFace(Face.EXTERIOR);
	public static IArchitecturalElement CAPITAL = new ArchitecturalElement("capital", 100, 0).setFace(Face.EXTERIOR);
	
	public static IArchitecturalElement TRIM = new ArchitecturalElement("trim", 0, 0, FACADE).setFace(Face.INTERIOR);
	public static IArchitecturalElement CROWN = new ArchitecturalElement("crown", 100, 0, FACADE).setFace(Face.INTERIOR);
	public static IArchitecturalElement CORNICE = new ArchitecturalElement("cornice", 100, 0, FACADE).setFace(Face.EXTERIOR);
	public static IArchitecturalElement PLINTH = new ArchitecturalElement("plinth", 0, 0, FACADE).setFace(Face.EXTERIOR);
	
	public static IArchitecturalElement PILASTER = new ArchitecturalElement("pilaster", 100, 100).setFace(Face.INTERIOR);
	public static IArchitecturalElement PILASTER_BASE = new ArchitecturalElement("pilaster_base", 100, 0).setFace(Face.INTERIOR);
	public static IArchitecturalElement PILASTER_CAPITAL = new ArchitecturalElement("pilaster_capital", 100, 0).setFace(Face.INTERIOR);
	public static IArchitecturalElement PILLAR = new ArchitecturalElement("pillar", 100, 100).setFace(Face.INTERIOR);
	public static IArchitecturalElement PILLAR_BASE = new ArchitecturalElement("pillar_base", 100, 0).setFace(Face.INTERIOR);
	public static IArchitecturalElement PILLAR_CAPITAL = new ArchitecturalElement("pillar_capital", 100, 0).setFace(Face.INTERIOR);
	
	public static IArchitecturalElement CRENELLATION = new ArchitecturalElement("crenellation", 100, 50).setFace(Face.EXTERIOR);
	public static IArchitecturalElement PARAPET = new ArchitecturalElement("parapet", 100, 50).setFace(Face.EXTERIOR);
	public static IArchitecturalElement MERLON = new ArchitecturalElement("merlon", 100, 50).setFace(Face.EXTERIOR);
	
	public static IArchitecturalElement COFFER = new ArchitecturalElement("coffer", 100, 100).setFace(Face.INTERIOR);
	public static IArchitecturalElement COFFERED_MIDBEAM  = new ArchitecturalElement("coffered_midbeam", 100, 100).setFace(Face.INTERIOR);
	public static IArchitecturalElement COFFERED_CROSSBEAM = new ArchitecturalElement("coffered_crossbeam", 100, 100).setFace(Face.EXTERIOR);
	public static IArchitecturalElement LADDER = new ArchitecturalElement("ladder", 100, 0);
	public static IArchitecturalElement LADDER_PILLAR = new ArchitecturalElement("ladder_pillar", 100, 100);
	public static IArchitecturalElement GUTTER = new ArchitecturalElement("gutter", 100, 50);
	public static IArchitecturalElement GRATE = new ArchitecturalElement("grate", 100, 50);
	
	public static IArchitecturalElement WINDOW = new ArchitecturalElement("window", 100, 0);
	public static IArchitecturalElement SIGN = new ArchitecturalElement("sign", 0, 0);
	
	/*
	 * Multi-block elements
	 */
	
	// QUOIN
	
	// ALLURE/WALL-WALK
	
	// BUTTRESS
	
	/**
	 * 
	 */
	public Elements() {	
	}
	
	/**
	 * 
	 * @param name
	 * @return
	 */
	public static IArchitecturalElement getElement(String name) {
		return ElementsEnum.valueOf(name.toUpperCase()).getElement();
	}
	
	/**
	 * Wrapper for all the ArchitecturalElements to enable fast lookups.
	 * @author Mark Gottschling on Aug 19, 2018
	 *
	 */
	public enum ElementsEnum implements IEnum {
		NONE(-1, Elements.NONE.getName(), Elements.NONE),
		AIR(0, Elements.AIR.getName(), Elements.AIR),
		SURFACE_AIR(0, Elements.SURFACE_AIR.getName(), Elements.SURFACE_AIR),
		FLOOR_AIR(0, Elements.FLOOR_AIR.getName(), Elements.FLOOR_AIR),
		WALL_AIR(0, Elements.WALL_AIR.getName(), Elements.WALL_AIR),
		CEILING_AIR(0, Elements.CEILING_AIR.getName(), Elements.CEILING_AIR),
		
		FLOOR(1, Elements.FLOOR.getName(), Elements.FLOOR),
		FLOOR_ALT(1, Elements.FLOOR_ALT.getName(), Elements.FLOOR_ALT),
		WALL(2, Elements.WALL.getName(), Elements.WALL),
		WALL_BASE(3, Elements.WALL_BASE.getName(), Elements.WALL_BASE),
		WALL_CAPITAL(4, Elements.WALL_CAPITAL.getName(), Elements.WALL_CAPITAL),
		CEILING(3, Elements.CEILING.getName(), Elements.CEILING),
		BASE(4, Elements.BASE.getName(), Elements.BASE),
		COLUMN(5, Elements.COLUMN.getName(), Elements.COLUMN),
		CAPITAL(6, Elements.CAPITAL.getName(), Elements.CAPITAL),
		TRIM(7, Elements.TRIM.getName(), Elements.TRIM),
		CROWN(8, Elements.CROWN.getName(), Elements.CROWN),
		CORNICE(9, Elements.CORNICE.getName(), Elements.CORNICE),
		PLINTH(10, Elements.PLINTH.getName(), Elements.PLINTH),
		PILASTER(11, Elements.PILASTER.getName(), Elements.PILASTER),
		PILASTER_BASE(11, Elements.PILASTER_BASE.getName(), Elements.PILASTER_BASE),
		PILASTER_CAPITAL(11, Elements.PILASTER_CAPITAL.getName(), Elements.PILASTER_CAPITAL),
		PILLAR(12, Elements.PILLAR.getName(), Elements.PILLAR),
		PILLAR_BASE(12, Elements.PILLAR_BASE.getName(), Elements.PILLAR_BASE),
		PILLAR_CAPITAL(12, Elements.PILLAR_CAPITAL.getName(), Elements.PILLAR_CAPITAL),		
		COFFERED_CROSSBEAM(13, Elements.COFFERED_CROSSBEAM.getName(), Elements.COFFERED_CROSSBEAM),
		COFFERED_MIDBEAM(13, Elements.COFFERED_MIDBEAM.getName(), Elements.COFFERED_MIDBEAM),
		LADDER(25, Elements.LADDER.getName(), Elements.LADDER),
		LADDER_PILLAR(26, Elements.LADDER_PILLAR.getName(), Elements.LADDER_PILLAR),
		GUTTER(26, Elements.GUTTER.getName(), Elements.GUTTER),
		GRATE(27, Elements.GRATE.getName(), Elements.GRATE),
		WINDOW(27, Elements.WINDOW.getName(), Elements.WINDOW),
		SIGN(28, Elements.SIGN.getName(), Elements.SIGN),
		
		CRENELLATION(50, Elements.CRENELLATION.getName(), Elements.CRENELLATION),
		PARAPET(51, Elements.PARAPET.getName(), Elements.PARAPET),
		MERLON(52, Elements.MERLON.getName(), Elements.MERLON),
		
		FACADE(100, Elements.FACADE.getName(), Elements.FACADE),
		FACADE_SUPPORT(101, Elements.FACADE_SUPPORT.getName(), Elements.FACADE_SUPPORT);
		
		private Integer code;
		private String value;
		private IArchitecturalElement element;
		private static final Map<Integer, IEnum> codes = new HashMap<Integer, IEnum>();
		private static final Map<String, IEnum> values = new HashMap<String, IEnum>();
		
		// setup reverse lookup
		static {
			for (ElementsEnum x : EnumSet.allOf(ElementsEnum.class)) {
				codes.put(x.getCode(), x);
				values.put(x.getValue(), x);
			}
		}
		
		/**
		 * 
		 * @param code
		 * @param element
		 */
		ElementsEnum(int code, String value, IArchitecturalElement element) {
			this.code = code;
			this.value = value;
			this.element = element;
		}
		
		/**
		 * 
		 * @return
		 */
		public IArchitecturalElement getElement() {
			return this.element;
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
		public static ElementsEnum getByCode(Integer code) {
			return (ElementsEnum) codes.get(code);
		}
		/**
		 * 
		 * @param value
		 * @return
		 */
		public static ElementsEnum getByValue(String value) {
			return (ElementsEnum) values.get(value);
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
	}
}
