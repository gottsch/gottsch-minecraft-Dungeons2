/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2022 Mark Gottschling (gottsch)
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Mark Gottschling on Jun 23, 2022
 *
 */
public abstract class BaseRoom implements IRoom {

	// are relative to the room (coords)
	private Map<Direction2D, List<IConnector>> connectors; 
	
	public BaseRoom() {
	}

	@Override
	public boolean hasConnectors() {
		return connectors != null && !connectors.isEmpty();
	}
	
	@Override
	public Map<Direction2D, List<IConnector>> getConnectors() {
		if (connectors == null) {
			connectors = new HashMap<>();
		}
		return connectors;
	}

	public void setConnectors(Map<Direction2D, List<IConnector>> connectors) {
		this.connectors = connectors;
	}
}
