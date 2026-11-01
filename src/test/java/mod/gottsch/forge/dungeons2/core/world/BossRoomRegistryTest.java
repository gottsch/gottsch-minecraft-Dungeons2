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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry's job is to turn the per-chunk slices a room is recorded in back into the room.
 *
 * <p>Everything here is about that. The protection built on it asks "is a boss alive in the room
 * holding this block", so a room left filed as four quarters would ask that question of a quarter at
 * a time and leave three walls breakable while the boss stood by the fourth.</p>
 *
 * @author Mark Gottschling on Sep 8, 2026
 */
public class BossRoomRegistryTest {

    /**
     * The case that motivated the merge: vanilla clips a piece to the chunk box before a processor
     * sees it, so a room wider than a chunk is recorded as adjacent slices that share no cell — one
     * ends at 15 and the next begins at 16.
     */
    @Test
    public void abuttingChunkSlicesBecomeOneRoom() {
        BossRoomRegistry registry = new BossRoomRegistry();
        registry.add(new BoundingBox(0, 60, 0, 15, 70, 20));
        registry.add(new BoundingBox(16, 60, 0, 30, 70, 20));

        assertEquals(1, registry.size(), "abutting slices of one room must merge");

        Optional<BoundingBox> room = registry.roomAt(new BlockPos(2, 62, 2));
        assertTrue(room.isPresent());
        assertEquals(new BoundingBox(0, 60, 0, 30, 70, 20), room.get(),
                "the merged room must be the union of its slices, inflated by nothing");
    }

    /**
     * A block in one slice must resolve to the WHOLE room, since that box is what the living-boss
     * search is then bounded by. This is the assertion that would have failed in game.
     */
    @Test
    public void aBlockInOneSliceResolvesToTheWholeRoom() {
        BossRoomRegistry registry = new BossRoomRegistry();
        registry.add(new BoundingBox(0, 60, 0, 15, 70, 20));
        registry.add(new BoundingBox(16, 60, 0, 30, 70, 20));

        BoundingBox room = registry.roomAt(new BlockPos(30, 70, 20)).orElseThrow();
        assertTrue(room.isInside(new BlockPos(0, 60, 0)),
                "the far corner of the room must be guarded from a block in the other slice");
    }

    /** Slices can arrive in any order, and one can bridge two that do not touch each other. */
    @Test
    public void aSliceBridgingTwoOthersMergesAllThree() {
        BossRoomRegistry registry = new BossRoomRegistry();
        registry.add(new BoundingBox(0, 60, 0, 15, 70, 20));
        registry.add(new BoundingBox(32, 60, 0, 47, 70, 20));
        assertEquals(2, registry.size(), "the outer slices do not touch, so they stay separate");

        registry.add(new BoundingBox(16, 60, 0, 31, 70, 20));
        assertEquals(1, registry.size(), "the middle slice must absorb both");
        assertEquals(new BoundingBox(0, 60, 0, 47, 70, 20),
                registry.roomAt(new BlockPos(40, 65, 10)).orElseThrow());
    }

    /** A chunk can be generated more than once in a session; the same room must not pile up. */
    @Test
    public void anIdenticalBoxIsNotFiledTwice() {
        BossRoomRegistry registry = new BossRoomRegistry();
        BoundingBox room = new BoundingBox(0, 60, 0, 15, 70, 20);
        registry.add(room);
        registry.add(room);

        assertEquals(1, registry.size());
        assertEquals(room, registry.roomAt(new BlockPos(1, 61, 1)).orElseThrow());
    }

    /**
     * Two boss rooms far apart stay two rooms. The planner never places rooms without a gap, so
     * merging on contact cannot join rooms that are genuinely distinct.
     */
    @Test
    public void separateRoomsStaySeparate() {
        BossRoomRegistry registry = new BossRoomRegistry();
        registry.add(new BoundingBox(0, 60, 0, 15, 70, 20));
        registry.add(new BoundingBox(100, 60, 100, 115, 70, 120));

        assertEquals(2, registry.size());
        assertFalse(registry.roomAt(new BlockPos(1, 61, 1)).orElseThrow()
                .isInside(new BlockPos(101, 61, 101)));
    }

    /** A block in no recorded room is not guarded, which is what leaves the rest of a dungeon minable. */
    @Test
    public void aBlockOutsideEveryRoomResolvesToNothing() {
        BossRoomRegistry registry = new BossRoomRegistry();
        registry.add(new BoundingBox(0, 60, 0, 15, 70, 20));

        assertTrue(registry.roomAt(new BlockPos(200, 65, 200)).isEmpty());
    }

    /** It is SavedData, and a room must survive the restart that a long fight can span. */
    @Test
    public void roomsSurviveASaveAndLoad() {
        BossRoomRegistry registry = new BossRoomRegistry();
        registry.add(new BoundingBox(0, 60, 0, 15, 70, 20));
        registry.add(new BoundingBox(100, 60, 100, 115, 70, 120));

        BossRoomRegistry loaded = BossRoomRegistry.load(registry.save(new CompoundTag()));

        assertEquals(2, loaded.size());
        assertEquals(new BoundingBox(0, 60, 0, 15, 70, 20),
                loaded.roomAt(new BlockPos(1, 61, 1)).orElseThrow());
        assertEquals(new BoundingBox(100, 60, 100, 115, 70, 120),
                loaded.roomAt(new BlockPos(101, 61, 101)).orElseThrow());
    }
}
