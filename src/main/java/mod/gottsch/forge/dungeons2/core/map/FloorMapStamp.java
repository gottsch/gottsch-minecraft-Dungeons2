/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.map;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/**
 * What a Dungeon Map needs to know about the floor it maps: where the floor is, how high its walking
 * plane lies, and where its stairs down are.
 *
 * <p>None of it can be found out later. A structure lookup cannot see a D2 dungeon from inside past
 * eight chunks, and the planner's layout is gone once the pieces are written, so the room piece
 * stamps it on each chest it fills, as {@code ForgeData.}{@value #KEY} &mdash; the same route the
 * boss tier takes to the lore books &mdash; and the map is made from the stamp of the chest it is
 * found in.</p>
 *
 * <p>All coordinates are world coordinates, inclusive. The stairs rectangle is the floor's
 * {@code END} slot, the transition shaft down; the bottom floor has none.</p>
 *
 * @param floorIndex 0 at the top floor
 * @param floorY     the floor's walking plane
 * @param down       the stairs down as {@code {minX, minZ, maxX, maxZ}}, or null
 *
 * @author Mark Gottschling on 10/25/2026
 */
public record FloorMapStamp(int floorIndex, int floorY, int minX, int minZ, int maxX, int maxZ,
                            @Nullable int[] down) {

    /** The {@code ForgeData} key a room piece writes on its chests. */
    public static final String KEY = "D2FloorMap";

    /** The map's centre: the middle of the floor. */
    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }

    /**
     * The finest map scale that shows the whole floor, with a block of margin each side: 0 (a block a
     * pixel) unless the floor is wider than a map. Every shipped footprint is 75 or less, so 0.
     */
    public byte scale() {
        int span = Math.max(maxX - minX, maxZ - minZ) + 3;
        byte scale = 0;
        while ((128 << scale) < span && scale < 4) {
            scale++;
        }
        return scale;
    }

    /** Whether a column lies in the stairs down. */
    public boolean isStairsDown(int x, int z) {
        return down != null && x >= down[0] && x <= down[2] && z >= down[1] && z <= down[3];
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Floor", floorIndex);
        tag.putInt("Y", floorY);
        tag.putIntArray("Bounds", new int[]{minX, minZ, maxX, maxZ});
        if (down != null) {
            tag.putIntArray("Down", down);
        }
        return tag;
    }

    /** Null for a tag that is not a whole stamp. */
    @Nullable
    public static FloorMapStamp load(CompoundTag tag) {
        int[] bounds = tag.getIntArray("Bounds");
        if (bounds.length != 4 || !tag.contains("Y")) {
            return null;
        }
        int[] down = tag.getIntArray("Down");
        return new FloorMapStamp(tag.getInt("Floor"), tag.getInt("Y"), bounds[0], bounds[1], bounds[2], bounds[3],
                down.length == 4 ? down : null);
    }
}
