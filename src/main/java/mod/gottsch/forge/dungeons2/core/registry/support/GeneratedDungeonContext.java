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

import mod.gottsch.forge.gottschcore.spatial.Box;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.nbt.CompoundTag;

/**
 * 
 * @author Mark Gottschling Feb 1, 2023
 *
 */
public class GeneratedDungeonContext extends GeneratedContext {
	// TODO all the coords code can be moved to the GeneratedContext abstract
	private ICoords minCoords;
	private ICoords maxCoords;
	
	public GeneratedDungeonContext() {
		
	}
	
	/**
	 * 
	 * @param start
	 * @param end
	 */
	public GeneratedDungeonContext(ICoords start, ICoords end) {
		this.minCoords = start;
		this.maxCoords = end;
		this.setCoords(start);
	}
	
	public GeneratedDungeonContext(Box box) {
		this.minCoords = box.getMinCoords();
		this.maxCoords = box.getMaxCoords();
	}

	@Override
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.put("coords", getCoords().save(new CompoundTag()));
		tag.put("minCoords", this.minCoords.save(new CompoundTag()));
		tag.put("maxCoords", this.maxCoords.save(new CompoundTag()));
		return tag;
	}
	
	@Override
	public void load(CompoundTag tag) {
		if (tag.contains("coords")) {
			CompoundTag coordsTag = tag.getCompound("coords");
			this.setCoords(Coords.EMPTY.load(coordsTag));
		}
		if (tag.contains("minCoords") && tag.contains("maxCoords")) {
			this.minCoords = Coords.EMPTY.load(tag.getCompound("minCoords"));
			this.maxCoords = Coords.EMPTY.load(tag.getCompound("maxCoords"));
		}
	}
	
	@Override
	public ICoords getMinCoords() {
		return minCoords;
	}

	@Override
	public void setMinCoords(ICoords minCoords) {
		this.minCoords = minCoords;
	}

	@Override
	public ICoords getMaxCoords() {
		return maxCoords;
	}

	@Override
	public void setMaxCoords(ICoords maxCoords) {
		this.maxCoords = maxCoords;
	}
}
