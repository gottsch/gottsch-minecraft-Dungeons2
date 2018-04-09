/**
 * 
 */
package com.someguyssoftware.gottschcore;

import java.lang.reflect.Field;

/**
 * @author Mark Gottschling on Jul 23, 2017
 *
 */
public abstract class AbstractModObjectHolder {
	
	/**
	 * 
	 * @param clazz
	 * @param name
	 * @param value
	 */
	public static void setPropertyWithReflection(Class clazz, String name, Object value) {
		// get the field by reflection
		Field f = null;
		try {
			f = clazz.getField(name);
		} catch (NoSuchFieldException e) {
			GottschCore.logger.warn(String.format("No such field [%s] for class", name, clazz.getSimpleName()));
		} catch (SecurityException e) {
			GottschCore.logger.warn("Security violation: ", e);
		}
		
		// set the field property
		try {
			if (f != null) {
				f.set(null, value);
			}
		} catch (IllegalArgumentException e) {
			GottschCore.logger.warn(String.format("IllegalArguementException for field [%s] using argument [%s]", f.getName(), value));
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			GottschCore.logger.warn(String.format("IllegalAccessException for field [%s]", f.getName(), value));
		}
	}
}
