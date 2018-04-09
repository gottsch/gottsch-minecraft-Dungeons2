/**
 * 
 */
package com.someguyssoftware.gottschcore.random;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

// TODO review if all ints need to change to double
/**
 * This type of weighted collection must use non-negative integers.
 * @author Mark Gottschling on Aug 21, 2015
 *
 */
public class RandomProbabilityCollection<T extends IRandomProbabilityItem> {
	private final NavigableMap<Integer, T> map = new TreeMap<Integer, T>();
	private Random random;
	private int total = 0;
	
	/**
	 * 
	 */
	public RandomProbabilityCollection() {
		setRandom(new Random());
	}
	
	/**
	 * This method requires that the collection uses a specific interface to expose the weight
	 * @param collection
	 */
	public RandomProbabilityCollection(Collection<T> collection) {
		this(new Random(), collection);
	}
	
	/**
	 * 
	 * @param random
	 */
	public RandomProbabilityCollection(Random random) {
		setRandom(random);
	}
	
	/**
	 * This method requires that the collection uses a specific interface to expose the weight
	 * @param random
	 * @param collection
	 */
	public RandomProbabilityCollection(Random random, Collection<T> collection) {
		setRandom(random);
		add(collection);
	}
	
	/**
	 * 
	 * @param collection
	 */
	public void add(Collection<T> collection) {
		Iterator<T> iterator = collection.iterator();
		while (iterator.hasNext()) {
			T item =  iterator.next();
			add(item.getProbability(), item);
		}		
	}
	
	/**
	 * Add an item with a given weight.
	 * @param weight
	 * @param item
	 */
	public void add(int weight, T item) {
		if (weight <= 0) return;
		total += weight;
		map.put(total, item);
	}
	
	/**
	 * Get the next random value.
	 * @return
	 */
	public IRandomProbabilityItem next() {
		if (map.isEmpty() || total < 1) return null;
		int value = getRandom().nextInt(total);
		return map.ceilingEntry(value).getValue();
	}
	
	/**
	 * 
	 */
	public void clear() {
		map.clear();
		setTotal(0);
	}
	
	/**
	 * 
	 * @return
	 */
	public int size() {
		if (map == null) return 0;
		return map.size();
	}
	
	/**
	 * @return the random
	 */
	public Random getRandom() {
		return random;
	}

	/**
	 * @param random the random to set
	 */
	public void setRandom(Random random) {
		this.random = random;
	}
	
	/**
	 * 
	 * @return
	 */
	private int getTotal() {
		return this.total;
	}
	
	/**
	 * 
	 * @param total
	 */
	private void setTotal(int total) {
		this.total = total;
	}
	
	public Map<Integer, T> getMap() {
		return map;
	}
}
