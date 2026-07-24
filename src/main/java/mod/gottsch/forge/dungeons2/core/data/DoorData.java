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
package mod.gottsch.forge.dungeons2.core.data;

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Direction2D;

/**
 * Plain data describing one door / doorway within a {@link FloorLayout}.
 *
 * <p>Coordinates are floor-local grid coordinates (X = grid X, Z = grid Y).
 * {@code regionA} and {@code regionB} are the maze-planner region ids (room or
 * corridor) the door connects &mdash; useful for piece emitters that need to
 * resolve "which two structures does this door bridge".</p>
 *
 * <p>{@code facing} is the cardinal axis the door opens along (NORTH/SOUTH means
 * a door in an east-west wall; EAST/WEST means a door in a north-south wall).</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class DoorData {
    private int x;
    private int z;
    private int regionA;
    private int regionB;
    private Direction2D facing = Direction2D.NONE;

    public DoorData() {}

    public DoorData(int x, int z, int regionA, int regionB, Direction2D facing) {
        this.x = x;
        this.z = z;
        this.regionA = regionA;
        this.regionB = regionB;
        this.facing = facing;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public int getRegionA() { return regionA; }
    public void setRegionA(int regionA) { this.regionA = regionA; }

    public int getRegionB() { return regionB; }
    public void setRegionB(int regionB) { this.regionB = regionB; }

    public Direction2D getFacing() { return facing; }
    public void setFacing(Direction2D facing) { this.facing = facing; }

    @Override
    public String toString() {
        return "DoorData{(" + x + "," + z + ")" +
                ", regions=" + regionA + "<->" + regionB +
                ", facing=" + facing +
                '}';
    }
}
