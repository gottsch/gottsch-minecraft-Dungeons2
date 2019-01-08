/**
 * 
 */
package com.someguyssoftware.dungeonsengine.config;

import java.util.ArrayList;
import java.util.List;

import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.enums.Rarity;

/**
 * @author Mark Gottschling on Jan 5, 2019
 *
 */
public class ChestConfig implements IChestConfig {

	private List<Rarity> rarity;
	private Quantity probability;
	private LootTableMethod lootTableMethod;
	private String lootTableName;
	
	/**
	 * 
	 */
	public ChestConfig() {
	}

	/**
	 * 
	 * @param config
	 */
	public ChestConfig(IChestConfig config) {
		this.setLootTableName(config.getLootTableName());
		this.setLootTableMethod(config.getLootTableMethod());
		this.setProbability(new Quantity(config.getProbability()));
		this.setRarity(new ArrayList<>(config.getRarity()));
	}
	
	@Override
	public IChestConfig copy() {
		return new ChestConfig(this);
	}
	
	@Override
	public IChestConfig apply(IChestConfig config) {
		if (getLootTableMethod() == null) setLootTableMethod(config.getLootTableMethod());
		if (getLootTableName() == null) setLootTableName(config.getLootTableName());
		if (getProbability() == null) setProbability(new Quantity(config.getProbability()));
		if (getRarity() == null) setRarity(new ArrayList<>(config.getRarity()));
		return this;
	}
	
	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IChestConfig#getRarity()
	 */
	@Override
	public List<Rarity> getRarity() {
		return rarity;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IChestConfig#setRarity(com.someguyssoftware.gottschcore.enums.Rarity)
	 */
	@Override
	public void setRarity(List<Rarity> rarity) {
		this.rarity = rarity;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IChestConfig#getProbability()
	 */
	@Override
	public Quantity getProbability() {
		return probability;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IChestConfig#setProbability(com.someguyssoftware.gottschcore.Quantity)
	 */
	@Override
	public void setProbability(Quantity probability) {
		this.probability = probability;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IChestConfig#getLootTableType()
	 */
	@Override
	public LootTableMethod getLootTableMethod() {
		return lootTableMethod;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IChestConfig#setLootTableType(java.lang.String)
	 */
	@Override
	public void setLootTableMethod(LootTableMethod lootTableType) {
		this.lootTableMethod = lootTableType;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IChestConfig#getLootTableName()
	 */
	@Override
	public String getLootTableName() {
		return lootTableName;
	}

	/* (non-Javadoc)
	 * @see com.someguyssoftware.dungeonsengine.config.IChestConfig#setLootTableName(java.lang.String)
	 */
	@Override
	public void setLootTableName(String lootTableName) {
		this.lootTableName = lootTableName;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ChestConfig [rarity=" + rarity + ", probability=" + probability + ", lootTableMethod=" + lootTableMethod + ", lootTableName=" + lootTableName + "]";
	}

}
