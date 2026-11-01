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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;

import java.util.ArrayList;
import java.util.List;

/**
 * One floor of a multi-level dungeon.
 *
 * <p>Each floor owns its own maze grid &mdash; rooms, corridors, and doors are all
 * floor-local. Cross-floor stitching is handled by {@link TransitionData}
 * (siblings inside {@link DungeonLayout}, not nested here).</p>
 *
 * <p>{@code floorY} is the Y coordinate of the floor's <em>floor surface</em>
 * (the walking plane). {@code ceilingY} is the Y of the ceiling. Together they
 * give the room piece its vertical extent.</p>
 *
 * <p>{@code footprint} is the XZ region this floor occupies in floor-local
 * grid coords (origin always 0,0; width and height set by the size tier roll).
 * Floors may differ in XZ footprint &mdash; the chunk-safe Structure system
 * makes the old same-footprint constraint unnecessary.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class FloorLayout {
    private int floorIndex;
    private int floorY;
    private int ceilingY;
    private Rectangle2D footprint;
    private List<RoomData> rooms = new ArrayList<>();
    private List<CorridorData> corridors = new ArrayList<>();
    private List<DoorData> doors = new ArrayList<>();
    /**
     * The maze grid this floor was carved from. <strong>Transient / not
     * serialized</strong> &mdash; it exists only between planning and rendering
     * within the same pass so {@code BasicCorridorGenerator} can resolve which
     * neighbor cells need wall columns. Phase 3 will fold each corridor's wall
     * columns into {@link CorridorData} so pieces round-trip through NBT without
     * the grid; until then a deserialized {@code FloorLayout} has a null grid.
     */
    private transient Grid2D grid;

    public FloorLayout() {}

    public FloorLayout(int floorIndex, int floorY, int ceilingY, Rectangle2D footprint) {
        this.floorIndex = floorIndex;
        this.floorY = floorY;
        this.ceilingY = ceilingY;
        this.footprint = footprint;
    }

    public int getFloorIndex() { return floorIndex; }
    public void setFloorIndex(int floorIndex) { this.floorIndex = floorIndex; }

    public int getFloorY() { return floorY; }
    public void setFloorY(int floorY) { this.floorY = floorY; }

    public int getCeilingY() { return ceilingY; }
    public void setCeilingY(int ceilingY) { this.ceilingY = ceilingY; }

    public Rectangle2D getFootprint() { return footprint; }
    public void setFootprint(Rectangle2D footprint) { this.footprint = footprint; }

    public List<RoomData> getRooms() {
        if (rooms == null) rooms = new ArrayList<>();
        return rooms;
    }
    public void setRooms(List<RoomData> rooms) { this.rooms = rooms; }

    public List<CorridorData> getCorridors() {
        if (corridors == null) corridors = new ArrayList<>();
        return corridors;
    }
    public void setCorridors(List<CorridorData> corridors) { this.corridors = corridors; }

    public List<DoorData> getDoors() {
        if (doors == null) doors = new ArrayList<>();
        return doors;
    }
    public void setDoors(List<DoorData> doors) { this.doors = doors; }

    /** Transient maze grid; null on a deserialized layout. See the field doc. */
    public Grid2D getGrid() { return grid; }
    public void setGrid(Grid2D grid) { this.grid = grid; }

    @Override
    public String toString() {
        return "FloorLayout{index=" + floorIndex +
                ", Y=" + floorY + ".." + ceilingY +
                ", footprint=" + footprint +
                ", rooms=" + getRooms().size() +
                ", corridors=" + getCorridors().size() +
                ", doors=" + getDoors().size() +
                '}';
    }
}
