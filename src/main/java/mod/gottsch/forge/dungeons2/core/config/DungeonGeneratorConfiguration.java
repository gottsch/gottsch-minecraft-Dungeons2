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
package mod.gottsch.forge.dungeons2.core.config;

/**
 * 
 * @author Mark Gottschling Feb 1, 2023
 *
 */
public class DungeonGeneratorConfiguration {

	private String name;
	private String size;
	private int width;
	private int depth;
	private int minLevels;
	private int maxLevels;
	private int minRooms;
	private int maxRooms;
	private int minRoomSize;
	private int maxRoomSize;
	private int minRoomHeight;
	private int maxRoomHeight;
	private boolean hardBoundary;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getSize() {
		return size;
	}
	public void setSize(String size) {
		this.size = size;
	}
	public int getWidth() {
		return width;
	}
	public void setWidth(int width) {
		this.width = width;
	}
	public int getDepth() {
		return depth;
	}
	public void setDepth(int depth) {
		this.depth = depth;
	}
	public int getMinLevels() {
		return minLevels;
	}
	public void setMinLevels(int minLevels) {
		this.minLevels = minLevels;
	}
	public int getMaxLevels() {
		return maxLevels;
	}
	public void setMaxLevels(int maxLevels) {
		this.maxLevels = maxLevels;
	}
	public int getMinRooms() {
		return minRooms;
	}
	public void setMinRooms(int minRooms) {
		this.minRooms = minRooms;
	}
	public int getMaxRooms() {
		return maxRooms;
	}
	public void setMaxRooms(int maxRooms) {
		this.maxRooms = maxRooms;
	}
	public int getMinRoomSize() {
		return minRoomSize;
	}
	public void setMinRoomSize(int minRoomSize) {
		this.minRoomSize = minRoomSize;
	}
	public int getMaxRoomSize() {
		return maxRoomSize;
	}
	public void setMaxRoomSize(int maxRoomSize) {
		this.maxRoomSize = maxRoomSize;
	}
	public int getMinRoomHeight() {
		return minRoomHeight;
	}
	public void setMinRoomHeight(int minRoomHeight) {
		this.minRoomHeight = minRoomHeight;
	}
	public int getMaxRoomHeight() {
		return maxRoomHeight;
	}
	public void setMaxRoomHeight(int maxRoomHeight) {
		this.maxRoomHeight = maxRoomHeight;
	}
	public boolean getHardBoundary() {
		return hardBoundary;
	}
	public boolean isHardBoundary() {
		return hardBoundary;
	}
	public void setHardBoundary(boolean hardBoundary) {
		this.hardBoundary = hardBoundary;
	}
//	@Override
//	public String toString() {
//		return "GeneratorConfig [name=" + name + ", size=" + size + ", width=" + width + ", depth=" + depth
//				+ ", minLevels=" + minLevels + ", maxLevels=" + maxLevels + ", minRooms=" + minRooms + ", maxRooms="
//				+ maxRooms + ", minRoomSize=" + minRoomSize + ", maxRoomSize=" + maxRoomSize + ", minRoomHeight="
//				+ minRoomHeight + ", maxRoomHeight=" + maxRoomHeight + ", hardBoundary=" + hardBoundary + "]";
//	}
//
//	public static interface IDungeonSize {}
//
//	public static enum DungeonSize implements IDungeonSize {
//		SMALL,
//		MEDIUM,
//		LARGE;
//	}
}

