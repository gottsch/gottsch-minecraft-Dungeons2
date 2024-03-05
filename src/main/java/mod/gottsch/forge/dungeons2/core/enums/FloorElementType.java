
package mod.gottsch.forge.dungeons2.core.enums;

import mod.gottsch.forge.gottschcore.enums.IEnum;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 
 * @author Mark Gottschling on Mar 3, 2024
 *
 */
public enum FloorElementType implements IRoomElementType {
	FLOOR(0, "floor"),
	FLOOR_BORDER(1, "floor_border"),
	FLOOR_PADDED_BORDER(2, "floor_padded_border"),
	FLOOR_CORNER(3, "floor_corner"),
	UNKNOWN(-1, "unknown");

	private static final Map<Integer, IEnum> codes = new HashMap<Integer, IEnum>();
	private static final Map<String, IEnum> values = new HashMap<String, IEnum>();
	private Integer code;
	private String value;

	// setup reverse lookup
	static {
		for (FloorElementType x : EnumSet.allOf(FloorElementType.class)) {
			codes.put(x.getCode(), x);
			values.put(x.getValue(), x);
		}
	}

	/**
	 * Full constructor
	 * @param code
	 * @param value
	 */
	FloorElementType(Integer code, String value) {
		this.code = code;
		this.value = value;
	}

	public static FloorElementType get(String name) {
		try {
			return valueOf(name.toUpperCase());
		}
		catch(Exception e) {
			return FloorElementType.UNKNOWN;
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
	public static FloorElementType getByCode(Integer code) {
		return (FloorElementType) codes.get(code);
	}
	/**
	 * 
	 * @param value
	 * @return
	 */
	public static FloorElementType getByValue(String value) {
		return (FloorElementType) values.get(value);
	}

	@Override
	public Map<Integer, IEnum> getCodes() {
		return codes;
	}
	@Override
	public Map<String, IEnum> getValues() {
		return values;
	}
	
	/**
	 * 
	 * @return
	 */
	public static List<String> getNames() {
		List<String> names = EnumSet.allOf(FloorElementType.class).stream().map(x -> x.name()).collect(Collectors.toList());
		return names;
	}
}
