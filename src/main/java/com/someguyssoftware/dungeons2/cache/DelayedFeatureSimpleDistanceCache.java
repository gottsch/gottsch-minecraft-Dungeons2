package com.someguyssoftware.dungeons2.cache;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.dungeons2.bst.IInterval;
import com.someguyssoftware.gottschcore.positional.ICoords;

/**
 * Wraps SimpleDistanceCache, adding a delayCount property.
 * @author Mark Gottschling on May 19, 2023
 *
 */
public class DelayedFeatureSimpleDistanceCache<T> extends SimpleDistanceCache<T> {
	
	private int delayCount = 0;
	
	/**
	 * 
	 */
	public DelayedFeatureSimpleDistanceCache(int max) {
		super(max);
		setDelayCount(delayCount);
	}
	
	public int getDelayCount() {
		return delayCount;
	}

	public void setDelayCount(int delayCount) {
		this.delayCount = delayCount;
	}

//	public void clear() {
//		distanceCache.clear();
//	}
	
//	public Dump dump() {
//		List<String> list = new ArrayList<>();
//		this.dimensionDistanceCache.forEach((key, value) -> {
//			list.add(String.format("%s = %s", key, value));
//		});
//		
//		return new Dump(this.delayCount, list);
//	}
//	
//	public static class Dump {
//		public int delayCount = 0;
//		public List<String> cache = new ArrayList<>();
//		
//		public Dump(int count, List<String> cache) {
//			this.delayCount = count;
//			this.cache = cache;
//		}
//
//		@Override
//		public String toString() {
//			return "Dump [delayCount=" + delayCount + ", cache=" + cache + "]";
//		}
//	}
}
