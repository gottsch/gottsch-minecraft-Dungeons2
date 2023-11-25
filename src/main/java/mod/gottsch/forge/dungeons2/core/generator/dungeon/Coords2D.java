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

/**
 * @author Mark Gottschling on Jun 21, 2020
 *
 */
public class Coords2D {
	/* in the context of this 2d generator library, all coords are positive */
	public static Coords2D EMPTY = new Coords2D(-1, -1);
	private int x;
	private int y;
	
	/**
	 * 
	 */
	public Coords2D() {}
	
	/**
	 * 
	 * @param x
	 * @param y
	 */
	public Coords2D(int x, int y) {
		this.x = x;
		this.y = y;
	}
	
	public Coords2D(Coords2D coords) {
		this.x = coords.getX();
		this.y = coords.getY();		
	}
	
	public double getDistance(Coords2D destination) {
	    double d0 = this.getX() - destination.getX();
	    double d1 = this.getY() - destination.getY();
	    return Math.sqrt(d0 * d0 + d1 * d1);
	}
	
    public void translate(int xDistance, int yDistance) {
        this.x += xDistance;
        this.y += yDistance;
    }
    
	public void setLocation(int x, int y) {
		setX(x);
		setY(y);
	}
	
	public int getX() {
		return x;
	}
	public void setX(int x) {
		this.x = x;
	}
	public int getY() {
		return y;
	}
	public void setY(int y) {
		this.y = y;
	}

	@Override
	public String toString() {
		return "Coords2D [x=" + x + ", y=" + y + "]";
	}
}
