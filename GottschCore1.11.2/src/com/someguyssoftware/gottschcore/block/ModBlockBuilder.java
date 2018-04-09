/**
 * 
 */
package com.someguyssoftware.gottschcore.block;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;

/**
 * @author Mark Gottschling on Jul 23, 2017
 *
 */
public class ModBlockBuilder {
	private String modID;
	private String name;
	private Material material;
	private MapColor mapColor;
	private Float hardness;
	private String toolClass;
	private Integer toolLevel;
	private Float resistance;
	private SoundType soundType;
	
	private Boolean normalCube = true;
	private CreativeTabs tab;
	/**
	 * 
	 */
	public ModBlockBuilder() {}
	
	/**
	 * 
	 * @return
	 */
	public ModBlock build() {
		ModBlock block = new ModBlock(modID, name, material, mapColor);
		
		// set the harvest level
		if ((this.toolClass != null && !this.toolClass.equals("")) && this.toolLevel != null) {
			block.setHarvestLevel(toolClass, toolLevel);
		}		
		if (this.hardness != null) block.setHardness(this.hardness);
		if (this.resistance != null) block.setResistance(this.resistance);
		if (this.soundType != null) block.setSoundType(this.soundType);
		block.setNormalCube(this.normalCube);
		
		block.setCreativeTab(this.tab);
		return block;
	}
	
	/**
	 * 
	 */
	public void clear() {
		withModID(null)
		.withName(null)
		.withMaterial(null)
		.withMapColor(null)
		.withHardness(null)
		.withHarvestLevel(null, null)
		.withResistance(null)
		.withSoundType(null)
		.withNormalCube(true);		
	}

	public String getModID() {
		return modID;
	}

	public ModBlockBuilder withModID(String modID) {
		this.modID = modID;
		return this;
	}

	public String getName() {
		return name;
	}

	public ModBlockBuilder withName(String name) {
		this.name = name;
		return this;
	}

	public Material getMaterial() {
		return material;
	}

	public ModBlockBuilder withMaterial(Material material) {
		this.material = material;
		return this;
	}

	public MapColor getMapColor() {
		return mapColor;
	}

	public ModBlockBuilder withMapColor(MapColor mapColor) {
		this.mapColor = mapColor;
		return this;
	}

	public ModBlockBuilder withHarvestLevel(String toolClass, Integer toolLevel) {
		this.toolClass = toolClass;
		this.toolLevel = toolLevel;
		return this;
	}
	
	public Boolean isNormalCube() {
		return normalCube;
	}

	public ModBlockBuilder withNormalCube(Boolean normalCube) {
		this.normalCube = normalCube;
		return this;
	}

	public Float getHardness() {
		return hardness;
	}

	public ModBlockBuilder withHardness(Float hardness) {
		this.hardness = hardness;
		return this;
	}

	public SoundType getSoundType() {
		return soundType;
	}

	public ModBlockBuilder withSoundType(SoundType soundType) {
		this.soundType = soundType;
		return this;
	}
	
	public CreativeTabs getCreativeTab() {
		return tab;
	}

	public ModBlockBuilder withCreativeTab(CreativeTabs tab) {
		this.tab = tab;
		return this;
	}

	public Float getResistance() {
		return resistance;
	}

	public ModBlockBuilder withResistance(Float resistance) {
		this.resistance = resistance;
		return this;
	}
}
