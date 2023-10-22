package com.someguyssoftware.dungeons2.cache;

import java.util.List;

import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * 
 * @author Mark Gottschling on May 19, 2023
 *
 * @param <T>
 */
public interface ISimpleDistanceCache<T> {

	/**
	 * 
	 * @param key
	 * @return
	 */
	boolean isCached(T object);

	/**
	 * 
	 */
	void cache(ICoords key, T object);

	/**
	 * 
	 * @param object
	 */
	void uncache(ICoords key);

	/**
	 * 
	 */
	void clear();

	/**
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	public boolean withinArea(ICoords start, ICoords end);
	
	/**
	 * This will not update parent collection.
	 * @return
	 */
	List<T> getValues();
}
