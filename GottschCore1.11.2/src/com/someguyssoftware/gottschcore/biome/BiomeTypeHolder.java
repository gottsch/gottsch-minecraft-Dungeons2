/**
 * 
 */
package com.someguyssoftware.gottschcore.biome;

/**
 * A class to hold BiomeTypes.
 * @author Mark Gottschling on Sep 28, 2015
 *
 */
public class BiomeTypeHolder {
	private int dictionaryId;
	private Object biomeType;
	
	public BiomeTypeHolder(){}
	
	public BiomeTypeHolder(int dictionaryId, Object biomeType) {
		setDictionaryId(dictionaryId);
		setBiomeType(biomeType);
	}

	/**
	 * @return the dictionaryId
	 */
	public int getDictionaryId() {
		return dictionaryId;
	}

	/**
	 * @param dictionaryId the dictionaryId to set
	 */
	public void setDictionaryId(int dictionaryId) {
		this.dictionaryId = dictionaryId;
	}

	/**
	 * @return the biomeType
	 */
	public Object getBiomeType() {
		return biomeType;
	}

	/**
	 * @param biomeType the biomeType to set
	 */
	public void setBiomeType(Object biomeType) {
		this.biomeType = biomeType;
	}
}