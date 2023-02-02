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

import java.util.List;
import java.util.Map;

import mod.gottsch.forge.dungeons2.core.generator.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.INode;
import mod.gottsch.forge.dungeons2.core.generator.Rectangle2D;


/**
 * @author Mark Gottschling on Sep 15, 2020
 *
 */
// TODO should probably be totally disconnected from the graphing classes
public interface IRoom extends INode {
	public Rectangle2D getBox();
	public void setBox(Rectangle2D box);
	
	IRoomRole getRole();
	IRoom setRole(IRoomRole roomRole);
	
	List<Coords2D> getExits();
	void setExits(List<Coords2D> exits);
	
	List<IRoomFlag> getFlags();
	void setFlags(List<IRoomFlag> flags);
	boolean hasFlag(IRoomFlag flag);
	
	boolean hasConnectors();
	Map<Direction2D, List<IConnector>> getConnectors();
}
