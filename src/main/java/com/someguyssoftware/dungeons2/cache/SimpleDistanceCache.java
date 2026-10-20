package com.someguyssoftware.dungeons2.cache;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.bst.CoordsInterval;
import com.someguyssoftware.dungeons2.bst.CoordsIntervalTree;
import com.someguyssoftware.dungeons2.bst.IInterval;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * 
 * @author Mark Gottschling on May 19, 2023
 *
 * @param <T>
 */
public class SimpleDistanceCache<T> implements ISimpleDistanceCache<T> {

	private LinkedList<T> cache;
	private int maxSize;
	
	/*
	 * a Interval BST registry to determine the proximity of pois.
	 */
	private final CoordsIntervalTree<T> distanceCache;
	
	/**
	 * 
	 */
	public SimpleDistanceCache(int size) {
		cache = new LinkedList<>();
		maxSize = size;
		distanceCache = new CoordsIntervalTree<>();
	}
	
	/**
	 * 
	 * @param object
	 * @return
	 */
	@Override
	public boolean isCached(final T object) {
		return cache.contains(object);
	}
	
	/**
	 * 
	 */
	@Override
	public synchronized void cache(final ICoords key, final T object) {	
		Dungeons2.log.debug("caching @ -> {}", object.toString());
		if (isCached(object)) {
			return;
		}
		
		// test the size
		if (cache.size() >= maxSize) {
			// remove the first element in list (oldest).
			cache.pollFirst();
		}
		cache.add(object);		
		distanceCache.insert(new CoordsInterval<T>(key.withY(0), key.add(1, -key.getY(), 1), object));
	}
	
	/**
	 * 
	 * @param object
	 */
	@Override
	public synchronized void uncache(ICoords key) {
		// use the same interval shape as cache()
		CoordsInterval<T> target = new CoordsInterval<T>(key.withY(0), key.add(1, -key.getY(), 1), null);
		// NOTE delete() returns the new root, not the deleted interval, so look up the data first
		List<IInterval<T>> matches = distanceCache.getOverlapping(distanceCache.getRoot(), target);
		distanceCache.delete(target);
		// remove the matching object, not whichever is last
		matches.forEach(match -> cache.remove(match.getData()));
	}
	
	/**
	 * 
	 * @param start
	 * @param end
	 * @return
	 */
	@Override
	public boolean withinArea(ICoords start, ICoords end) {
		List<IInterval<T>> overlaps = distanceCache.getOverlapping(distanceCache.getRoot(), new CoordsInterval<>(start, end));
		if (overlaps.isEmpty()) {
			return false;
		}
		return true;
	}
	
	@Override
	public List<T> getData(ICoords start, ICoords end) {
		List<IInterval<T>> list = distanceCache.getOverlapping(distanceCache.getRoot(), new CoordsInterval<>(start, end));
		List<T> data = new ArrayList<>();
		list.forEach(element -> {
			data.add(element.getData());
		});		
		return data;
	}
	
	/**
	 * This will not update parent collection.
	 * @return
	 */
	@Override
	public List<T> getValues() {
		return new ArrayList<>(cache);
	}
	
	/**
	 * 
	 */
	@Override
	public void clear() {
		cache.clear();
		distanceCache.clear();
	}

	@Override
	public String toString() {
		return "SimpleDistanceCache [cache=" + cache + ", maxSize=" + maxSize + ", distanceCache=" + distanceCache
				+ "]";
	}
	
	// TEMP
//	public void dump() {
//		for (T c : registry) {
//			Dungeons2.log.debug("Dungeon Registry entry -> {}", c.toString());
//		}
//	}
}