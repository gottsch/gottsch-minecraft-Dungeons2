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
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import mod.gottsch.forge.dungeons2.core.generator.Coords2D;

/**
 * @author Mark Gottschling on Sep 22, 2020
 *
 */
public class WayConnector {
	private Coords2D coords;
	private IRoom room;
	
	public WayConnector(Coords2D coords) {
		this.coords = coords;
	}
	
	public WayConnector(Coords2D coords, IRoom room) {
		this(coords);
		this.room = room;
	}
	
	public WayConnector(WayConnector wc) {
		this(new Coords2D(wc.getCoords()), new Room());
	}
	
	public Coords2D getCoords() {
		return coords;
	}
	public void setCoords(Coords2D coords) {
		this.coords = coords;
	}
	public IRoom getRoom() {
		return room;
	}
	public void setRoom(IRoom room) {
		this.room = room;
	}

	@Override
	public String toString() {
		return "WayConnector [coords=" + ((coords == null) ? "null" : coords) + ", room.id=" + ((room == null) ? "null" : room.getId()) + "]";
	}
}
