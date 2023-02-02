/*
 * This file is part of  Dungeons2.
 * Copyright (c) 202 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.generator;

/**
 * 
 * @author Mark Gottschling on Sep 17, 2020
 *
 */
public interface INode {

	int getId();
	void setId(int id);

	public Coords2D getOrigin();
	public void setOrigin(Coords2D origin);

	public int getMaxDegrees();
	public INode setMaxDegrees(int degrees);

	public NodeType getType();
	public INode setType(NodeType type);

	// TODO rethink these - they probably belong in IRoom as a Node doesn't have a
	// size.
	/*
	 * convenience methods
	 */
	public Coords2D getCenter();

	public int getMinX();
	public int getMaxX();

	public int getMinY();
	public int getMaxY();
}
