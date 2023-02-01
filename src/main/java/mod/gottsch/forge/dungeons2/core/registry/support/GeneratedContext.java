/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.registry.support;

import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.nbt.CompoundTag;

/**
 * 
 * @author Mark Gottschling Jan 31, 2023
 *
 */
public abstract class GeneratedContext implements IGeneratedContext {
	private ICoords coords;
	
	public GeneratedContext() {}
	public GeneratedContext(ICoords coords) {
		this.coords = coords;
	}
	
	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put("coords", coords.save(new CompoundTag()));
//		tag.putString("rarity", rarity.getValue());		
		return tag;
	}
	
	@Override
	public void load(CompoundTag tag) {
		if (tag.contains("coords")) {
			CompoundTag coordsTag = tag.getCompound("coords");
			this.coords = Coords.EMPTY.load(coordsTag);
		}
//		if (tag.contains("rarity")) {
//			Optional<IRarity> rarity = TreasureApi.getRarity(tag.getString("rarity"));
//			if (rarity.isPresent()) {
//				this.rarity = rarity.get();
//			}
//			else {
//				this.rarity = Rarity.NONE;
//			}
//		}
	}
	
	@Override
	public ICoords getCoords() {
		return coords;
	}
	@Override
	public void setCoords(ICoords coords) {
		this.coords = coords;
	}
//	public IRarity getRarity() {
//		return rarity;
//	}
//	public void setRarity(IRarity rarity) {
//		this.rarity = rarity;
//	}
}
