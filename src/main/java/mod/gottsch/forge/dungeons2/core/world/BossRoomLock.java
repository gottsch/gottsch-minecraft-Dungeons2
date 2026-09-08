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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/**
 * Which boss rooms have had their boss killed, per dimension.
 *
 * <p>Records only the <em>cleared</em> rooms. A boss room is never registered when it generates
 * &mdash; there is nothing to write down until something happens in it, and a dungeon the player
 * never visits should cost nothing on disk. "Is this room locked?" is therefore answered as
 * "the structure says this is a boss room, and this file does not say it is cleared".</p>
 *
 * <p>The key is the room piece's bounding-box minimum corner, packed. It is stable for the life of
 * the world: a structure piece's box is fixed at generation and the same seed regenerates the same
 * box, so a cleared room stays cleared across a chunk unloading, a restart, or the piece being
 * re-read from the structure references.</p>
 *
 * @author Mark Gottschling on Sep 7, 2026
 */
public class BossRoomLock extends SavedData {

    private static final String NAME = "dungeons2_boss_rooms";
    private static final String CLEARED_TAG = "cleared";

    private final Set<Long> cleared = new HashSet<>();

    public static BossRoomLock get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BossRoomLock::load, BossRoomLock::new, NAME);
    }

    public boolean isCleared(long roomKey) {
        return this.cleared.contains(roomKey);
    }

    /** Marks a room cleared. No-op if it already was, so a second boss dying costs no write. */
    public void markCleared(long roomKey) {
        if (this.cleared.add(roomKey)) {
            this.setDirty();
        }
    }

    public static BossRoomLock load(CompoundTag tag) {
        BossRoomLock data = new BossRoomLock();
        for (long key : tag.getLongArray(CLEARED_TAG)) {
            data.cleared.add(key);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(CLEARED_TAG, this.cleared.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
}
