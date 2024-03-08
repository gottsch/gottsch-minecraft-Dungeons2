
package mod.gottsch.forge.dungeons2.core.enums;

import mod.gottsch.forge.gottschcore.enums.IEnum;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 
 * @author Mark Gottschling on Mar 6, 2024
 *
 */
public enum WallElementType implements IRoomElementType {
	WALL(0, "wall"),
	UNKNOWN(-1, "unknown");

	private static final Map<Integer, IEnum> codes = new HashMap<Integer, IEnum>();
	private static final Map<String, IEnum> values = new HashMap<String, IEnum>();
	private Integer code;
	private String value;

	// setup reverse lookup
	static {
		for (WallElementType x : EnumSet.allOf(WallElementType.class)) {
			codes.put(x.getCode(), x);
			values.put(x.getValue(), x);
		}
	}

	/**
	 * Full constructor
	 * @param code
	 * @param value
	 */
	WallElementType(Integer code, String value) {
		this.code = code;
		this.value = value;
	}

	public static WallElementType get(String name) {
		try {
			return valueOf(name.toUpperCase());
		}
		catch(Exception e) {
			return WallElementType.UNKNOWN;
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
	public static WallElementType getByCode(Integer code) {
		return (WallElementType) codes.get(code);
	}
	/**
	 * 
	 * @param value
	 * @return
	 */
	public static WallElementType getByValue(String value) {
		return (WallElementType) values.get(value);
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
		List<String> names = EnumSet.allOf(WallElementType.class).stream().map(x -> x.name()).collect(Collectors.toList());
		return names;
	}
}
