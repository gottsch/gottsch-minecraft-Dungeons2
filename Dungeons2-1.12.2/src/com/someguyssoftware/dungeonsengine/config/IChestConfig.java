package com.someguyssoftware.dungeonsengine.config;

import java.util.List;

import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.enums.Rarity;

public interface IChestConfig {

	/**
	 * @return the rarity
	 */
	List<Rarity> getRarity();

	/**
	 * @param rarity the rarity to set
	 */
	void setRarity(List<Rarity> rarity);

	/**
	 * @return the probability
	 */
	Quantity getProbability();

	/**
	 * @param probability the probability to set
	 */
	void setProbability(Quantity probability);

	/**
	 * @return the lootTableMethod
	 */
	LootTableMethod getLootTableMethod();

	/**
	 * @param lootTableMethod the lootTableMethod to set
	 */
	void setLootTableMethod(LootTableMethod lootTableMethod);

	/**
	 * @return the lootTableName
	 */
	String getLootTableName();

	/**
	 * @param lootTableName the lootTableName to set
	 */
	void setLootTableName(String lootTableName);

	IChestConfig copy();

	IChestConfig apply(IChestConfig chestConfig);

}