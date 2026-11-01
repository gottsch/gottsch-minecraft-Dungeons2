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
package mod.gottsch.forge.dungeons2.core.world;

import mod.gottsch.forge.dungeons2.core.map.FloorMapStamp;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every dungeon floor in a dimension: where it is, its walking plane, its level and its stairs down
 * ({@link FloorMapStamp}), so "which floor is this player on?" can be answered anywhere in a
 * dungeon. Saved with the level, as {@code data/dungeons2_floors.dat}, for as long as the world
 * lasts - the dungeon itself never goes away either.
 *
 * <p>It exists for the crafted Dungeon Map, which has no chest to read its floor from. The obvious
 * question, "ask the structure", does not work: vanilla writes a structure's chunk references only
 * within eight chunks of its start, and a dungeon sprawls further (see {@link BossRoomRegistry},
 * which met the same wall first).</p>
 *
 * <p>A floor is filed by its room pieces as they are built. Each room carries its floor's stamp and
 * files it, on the server thread, the first time one of its chunks generates; the same floor arriving
 * again - from its other rooms, or a chunk generated twice - is recognised and dropped. A floor is
 * therefore known once any room of it has been built, which is before anyone can stand in it.
 * Dungeons generated before the index existed are not in it.</p>
 *
 * <p>Looked up only when a player starts a map, so a plain list is fast enough: even a thousand
 * dungeons is a few thousand containment tests, once per click.</p>
 *
 * @author Mark Gottschling on 10/26/2026
 */
public class DungeonFloorIndex extends SavedData {

    private static final String NAME = "dungeons2_floors";
    private static final String FLOORS_TAG = "floors";

    /**
     * How far from the walking plane a player still counts as on the floor - the same band a Dungeon
     * Map draws in: a corridor descends at most 4, and the next floor is twenty blocks off.
     */
    public static final int BELOW = 6;
    public static final int ABOVE = 4;

    private final List<FloorMapStamp> floors = new ArrayList<>();

    public static DungeonFloorIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DungeonFloorIndex::load, DungeonFloorIndex::new, NAME);
    }

    /** Files a floor, unless it is already filed. Server thread only. */
    public void add(FloorMapStamp floor) {
        for (FloorMapStamp existing : this.floors) {
            if (sameFloor(existing, floor)) {
                return;
            }
        }
        this.floors.add(floor);
        this.setDirty();
    }

    /** The floor whose footprint holds {@code pos} and whose walking plane it is near, if any. */
    public Optional<FloorMapStamp> floorAt(BlockPos pos) {
        for (FloorMapStamp floor : this.floors) {
            if (pos.getX() >= floor.minX() && pos.getX() <= floor.maxX()
                    && pos.getZ() >= floor.minZ() && pos.getZ() <= floor.maxZ()
                    && pos.getY() >= floor.floorY() - BELOW && pos.getY() <= floor.floorY() + ABOVE) {
                return Optional.of(floor);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return this.floors.size();
    }

    /** A floor is its footprint and its plane; nothing else about it can differ between two copies. */
    private static boolean sameFloor(FloorMapStamp a, FloorMapStamp b) {
        return a.floorY() == b.floorY() && a.minX() == b.minX() && a.minZ() == b.minZ()
                && a.maxX() == b.maxX() && a.maxZ() == b.maxZ();
    }

    public static DungeonFloorIndex load(CompoundTag tag) {
        DungeonFloorIndex data = new DungeonFloorIndex();
        for (Tag entry : tag.getList(FLOORS_TAG, Tag.TAG_COMPOUND)) {
            FloorMapStamp floor = FloorMapStamp.load((CompoundTag) entry);
            if (floor != null) {
                data.floors.add(floor);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (FloorMapStamp floor : this.floors) {
            list.add(floor.save());
        }
        tag.put(FLOORS_TAG, list);
        return tag;
    }
}
