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

import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.nbt.CompoundTag;

/**
 * 
 * @author Mark Gottschling Feb 1, 2023
 *
 */
public interface IGeneratedContext {

	CompoundTag save();

	void load(CompoundTag tag);

	ICoords getCoords();

	void setCoords(ICoords coords);
	//	public IRarity getRarity() {
	//		return rarity;
	//	}
	//	public void setRarity(IRarity rarity) {
	//		this.rarity = rarity;
	//	}

	ICoords getMinCoords();
	void setMinCoords(ICoords minCoords);

	ICoords getMaxCoords();
	void setMaxCoords(ICoords maxCoords);

}