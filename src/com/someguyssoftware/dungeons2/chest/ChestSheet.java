/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.annotations.Since;

/**
 * @author Mark Gottschling on Sep 3, 2016
 *
 */
public class ChestSheet {
	@Since(1.0)
	private Map<String, ChestItem> items;	
	@Since(1.0)
	private Map<String, ChestItemGroup> groups;
	@Since(1.0)
	private Map<String, ChestContainer> containers;	
	
	/**
	 * 
	 */
	public ChestSheet() {
		setGroups(new LinkedHashMap<String, ChestItemGroup>());
		setContainers(new LinkedHashMap<String, ChestContainer>());
	}

	/**
	 * @return the groups
	 */
	public Map<String, ChestItemGroup> getGroups() {
		return groups;
	}

	/**
	 * @param groups the groups to set
	 */
	public void setGroups(Map<String, ChestItemGroup> groups) {
		this.groups = groups;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ChestSheet [items=" + items + ", groups=" + groups + ", containers=" + containers + "]";
	}

	/**
	 * @return the containers
	 */
	public Map<String, ChestContainer> getContainers() {
		return containers;
	}

	/**
	 * @param containers the containers to set
	 */
	public void setContainers(Map<String, ChestContainer> containers) {
		this.containers = containers;
	}

	/**
	 * @return the items
	 */
	public Map<String, ChestItem> getItems() {
		if (items == null) {
			items = new LinkedHashMap<>();
		}
		return items;
	}

	/**
	 * @param items the items to set
	 */
	public void setItems(Map<String, ChestItem> items) {
		this.items = items;
	}
}
