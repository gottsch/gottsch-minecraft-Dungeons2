/**
 * 
 */
package com.someguyssoftware.dungeons2.registry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.someguyssoftware.dungeons2.Dungeons2;
import com.someguyssoftware.dungeons2.model.DungeonInfo;

/**
 * 
 * @author Mark Gottschling on Aug 19, 2017
 *
 */
public class DungeonRegistry {
	private static final int MAX_SIZE = 25;
	
	private static DungeonRegistry instance = new DungeonRegistry();
	private ListMultimap<String, DungeonInfo> registry;
	
	/**
	 * 
	 */
	private DungeonRegistry() {
		registry = LinkedListMultimap.create();
	}
	
	/**
	 * 
	 * @return
	 */
	public static DungeonRegistry getInstance() {
		return instance;
	}
	
	/**
	 * 
	 * @param key
	 * @return
	 */
	public boolean isRegistered(final String key) {
		return registry.containsKey(key);
	}
	
	/**
	 * Registers a DungeonInfo with a key.
	 * @param key
	 * @param info
	 */
	public synchronized void register(final String key, final DungeonInfo info) {	
		Dungeons2.log.debug("Registering dungeon using key: " + key);
		// test the size
		if (registry.size() >= MAX_SIZE) {
			// remove the first element
			String headKey = registry.keySet().iterator().next();
			unregister(headKey);
		}
		// register by the unique key
		registry.put(key, info);
	}
	
	/**
	 * 
	 * @param key
	 */
	public synchronized void unregister(final String key) {
		if (registry.containsKey(key)) {
			registry.removeAll(key);
		}
	}
	
	/**
	 * 
	 * @param key
	 * @return
	 */
	public List<DungeonInfo> get(String key) {
		List<DungeonInfo> info = null;
		if (registry.containsKey(key)) {
			info = registry.get(key);
		}
		return info;
	}
	
	/**
	 * This will not update parent collection.
	 * @return
	 */
	public List<DungeonInfo> getEntries() {
		HashSet<DungeonInfo> set = Sets.newHashSet(registry.values());
		return new ArrayList<>(set);
	}
}
