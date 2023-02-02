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
package mod.gottsch.forge.dungeons2.core.generator;

import java.util.Comparator;

/**
 * @author Mark Gottschling on Jun 24, 2020
 *
 */
public class Coords2DComparator implements Comparator<Coords2D> {

	@Override
	public int compare(Coords2D coords1, Coords2D coords2) {
		if (coords1.getX() < coords2.getX()) {
			return -1;
		}
		else if (coords1.getX() > coords2.getX()) {
			return 1;
		}
		else {
			if (coords1.getY() < coords2.getY()) {
				return -1;
			}
			else if (coords1.getY() > coords2.getY()) {
				return 1;
			}
			else {
				return 0;
			}
		}
	}
}
