/**
 * 
 */
package com.someguyssoftware.gottschcore.biome;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Mark Gottschling on Jan 17, 2016
 *
 */
public class BiomeDictionaryManager {
	
	private static BiomeDictionaryManager instance;
	private static Map<String, IBiomeDictionary> map;
	
	static {
		getInstance();
		map = new HashMap<>();
	}
	
	/**
	 * 
	 */
	private BiomeDictionaryManager() {		
	}

	/**
	 * 
	 * @return
	 */
	public static synchronized BiomeDictionaryManager getInstance() {
		if (instance == null) {
			try {
				instance = new BiomeDictionaryManager();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return instance;
	}

	/**
	 * 
	 * @param key
	 * @return
	 */
	public IBiomeDictionary getDictionary(String key) {
		if (map.containsKey(key)) {
			return map.get(key);
		}
		return null;
	}

	/**
	 * 
	 * @param key
	 * @param dictionary
	 */
	public void registerDictionary(String key, IBiomeDictionary dictionary) {
		map.put(key, dictionary);
	}
	
	/**
	 * 
	 * @param key
	 */
	public void unregisterDictionary(String key) {
		if (map.containsKey(key)) {
			map.remove(key);
		}
	}
	
	/**
	 * 
	 * @return
	 */
	public Collection<IBiomeDictionary> getAll() {
		if (map != null) {
			return map.values();
		}
		return null;
	}
	
}
