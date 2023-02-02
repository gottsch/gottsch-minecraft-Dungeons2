/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2020 Mark Gottschling (gottsch)
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

import mod.gottsch.forge.dungeons2.core.generator.Rectangle2D;

/**
 * @author Mark Gottschling on Sep 21, 2020
 *
 */
public class Wayline {
	private WayConnector connector1;
	private WayConnector connector2;
	private Rectangle2D box;
	private Wayline next;
	
	public Wayline() {}
	
	public Wayline(WayConnector connector1, WayConnector connector2) {
		this.connector1 = connector1;
		this.connector2 = connector2;
		this.box = new Rectangle2D(connector1.getCoords(), connector2.getCoords());
	}

	public WayConnector getConnector1() {
		return connector1;
	}

	public void setConnector1(WayConnector connector1) {
		this.connector1 = connector1;
	}

	public WayConnector getConnector2() {
		return connector2;
	}

	public void setConnector2(WayConnector connector2) {
		this.connector2 = connector2;
	}

	public int getWeight() {
		return Math.max(this.box.getWidth(), this.box.getHeight());
	}

	public Rectangle2D getBox() {
		return box;
	}

	public void setBox(Rectangle2D box) {
		this.box = box;
	}

	@Override
	public String toString() {
		return "Wayline [connector1=" + connector1 == null ? "null" : connector1 + ", connector2=" + connector2 == null ? "null" : connector2 + ", box=" + box == null ? "null" : box +
				", next=" + next == null ? "null" : next +"]";
	}

	public Wayline getNext() {
		return next;
	}

	public void setNext(Wayline next) {
		this.next = next;
	}
}
