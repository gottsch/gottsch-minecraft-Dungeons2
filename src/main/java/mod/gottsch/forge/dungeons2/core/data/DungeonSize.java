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

/**
 * Dungeon size tier. Drives floor-count range and per-floor XZ footprint range.
 *
 * <p>Values are chosen deterministically from the structure's RNG seed during planning.
 * Each tier carries its own min/max floor count and min/max footprint (the
 * XZ extent of any single floor's maze grid).</p>
 *
 * <p>Footprint dimensions are in maze-grid cells. The maze planner enforces
 * odd-numbered dimensions internally; the values below are chosen to be odd
 * so the planner accepts them without rounding.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public enum DungeonSize {
    // SMALL's footprint was raised from 25-35 on 2026-08-31 (Mark: "a little larger"). It was the
    // outlier of the three, and by a long way: MEASURED excavation was ~8,500 blocks against
    // MEDIUM's ~48,000, so the step from SMALL to MEDIUM was 5.6x while MEDIUM to LARGE was only
    // 2.7x. The floor range is deliberately NOT touched -- floors multiply where footprint scales
    // as an area, so 1-2 is what keeps a SMALL dungeon a quick shallow one rather than a MEDIUM
    // with fewer rooms.
    SMALL(1, 2, 31, 43),
    MEDIUM(2, 4, 35, 55),
    LARGE(3, 5, 55, 75);

    private final int minFloors;
    private final int maxFloors;
    private final int minFootprint;
    private final int maxFootprint;

    DungeonSize(int minFloors, int maxFloors, int minFootprint, int maxFootprint) {
        this.minFloors = minFloors;
        this.maxFloors = maxFloors;
        this.minFootprint = minFootprint;
        this.maxFootprint = maxFootprint;
    }

    public int getMinFloors() {
        return minFloors;
    }

    public int getMaxFloors() {
        return maxFloors;
    }

    public int getMinFootprint() {
        return minFootprint;
    }

    public int getMaxFootprint() {
        return maxFootprint;
    }
}
