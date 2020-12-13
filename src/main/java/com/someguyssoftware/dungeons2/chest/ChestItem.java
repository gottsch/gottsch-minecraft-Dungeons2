/**
 * 
 */
package com.someguyssoftware.dungeons2.chest;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Since;

/**
 * @author Mark Gottschling on Jul 4, 2016
 * @version 2.0
 * @since 1.0.0
 */
public class ChestItem {
	/*
	 * Internal ID used to reference item within JSON file
	 */
	@Since(1.0)
	private String id;
	/*
	 * Minecraft ChestItem NAME
	 */
	@Since(1.0)
	private String name;
	
	/**
	 * @since 2.0
	 */
	@Since(2.0)
	private String type;
	
	/*
	 * A description of the ChestItem
	 */
	@Since(1.0)
	@SerializedName("desc")
	private String description;	
	@Since(1.0)
	private int damage;
	
	/**
	 * 
	 */
	public ChestItem() {}
			
	/**
	 * 
	 * @param id
	 * @param NAME
	 */
	public ChestItem(String id, String name) {
		setId(id);
		setName(name);
	}
	
	/**
	 * 
	 * @param id
	 * @param NAME
	 * @param damage
	 */
	public ChestItem(String id, String name, int damage) {
		setId(id);
		setName(name);
		setDamage(damage);
	}
	
	/**
	 * 
	 * @param id
	 * @param name
	 * @param type
	 * @param damage
	 */
	public ChestItem(String id, String name, String type, int damage) {
		setId(id);
		setName(name);
		setDamage(damage);
	}
	
	/**
	 * 
	 * @param id
	 * @param NAME
	 * @param description
	 */
	public ChestItem (String id, String name, String desc) {
		this(id, name);
		setDescription(desc);
	}

	/**
	 * @return the NAME
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param NAME the NAME to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description the description to set
	 */
	public void setDescription(String desc) {
		this.description = desc;
	}

	@Override
	public String toString() {
		return "ChestItem [id=" + id + ", name=" + name + ", type=" + type + ", description=" + description
				+ ", damage=" + damage + "]";
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the damage
	 */
	public int getDamage() {
		return damage;
	}

	/**
	 * @param damage the damage to set
	 */
	public void setDamage(int damage) {
		this.damage = damage;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
