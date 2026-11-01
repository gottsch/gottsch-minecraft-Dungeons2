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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Where every generated boss room is, per dimension.
 *
 * <p>Written once when the room generates ({@code SpawnerMarkerProcessor} finds the boss spawner and
 * measures the piece it is in) and read for ever after. This is the third attempt at answering
 * "which blocks belong to this boss room", and the first that is both exact and cheap:</p>
 *
 * <ol>
 *   <li><strong>Ask the structure.</strong> Never worked once &mdash; {@code getStructureAt} reads
 *       the chunk's references back to the structure, and vanilla writes those only within eight
 *       chunks of a structure's start, while a Dungeons2 dungeon sprawls past 128 blocks. Failed
 *       closed and silently.</li>
 *   <li><strong>A box around the boss.</strong> Cheap, and visibly not the room: round where a room
 *       is square, and sliding about as the boss paced.</li>
 *   <li><strong>Flood-fill the room at runtime.</strong> Exact when the room is sealed, but a
 *       weathering hole into a corridor makes the fill escape, and the decay pass punches those
 *       holes routinely. It also paid for a fill on every block break near a boss.</li>
 * </ol>
 *
 * <p>Recording it at generation is immune to all three problems. The room is measured before any
 * player can alter it, so later decay, breaches and open doors change nothing; there is no structure
 * lookup and no fill, only a containment test against a handful of boxes; and it survives chunk
 * unloads and restarts because it is saved with the level.</p>
 *
 * <p>The one thing it is not handed is a whole room: a piece spanning chunks is processed in
 * per-chunk slices, so a room is filed a piece at a time and reassembled by {@link #add}.</p>
 *
 * @author Mark Gottschling on Sep 8, 2026
 */
public class BossRoomRegistry extends SavedData {

    private static final String NAME = "dungeons2_boss_rooms";
    private static final String ROOMS_TAG = "rooms";

    private final List<BoundingBox> rooms = new ArrayList<>();

    public static BossRoomRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BossRoomRegistry::load, BossRoomRegistry::new, NAME);
    }

    /**
     * Records a boss room, absorbing any box it touches.
     *
     * <p><strong>A room arrives in SLICES, not whole.</strong> Vanilla clips
     * {@code processBlockInfos} to the chunk box before a processor sees the list, so a boss room —
     * the largest room this mod places, and wider than sixteen blocks far more often than not —
     * is handed to {@code SpawnerMarkerProcessor} once per chunk it covers, each time as the part
     * of itself inside that chunk. Filing those as separate rooms would protect every block of the
     * room and yet ask "is a boss alive in here" of a quarter of it at a time, so a boss in the
     * north half would leave the south wall breakable. Merging on contact rebuilds the room: the
     * union of its chunk slices is exactly its own extent, with nothing inflated.</p>
     *
     * <p>Touching counts as overlapping, since abutting slices share no cell — the seam is where
     * one ends at 15 and the next begins at 16. Two genuinely distinct boss rooms cannot be merged
     * by that rule: the planner never places rooms without a gap between them.</p>
     *
     * <p>This also subsumes plain duplicates, which happen on their own — a chunk can be generated
     * more than once in a session, and the identical box would otherwise be filed twice.</p>
     */
    public void add(BoundingBox room) {
        BoundingBox merged = room;
        boolean absorbed = true;
        // Repeated, not one pass: absorbing one slice can grow the box into another it did not
        // previously touch, and slices arrive in no particular order.
        while (absorbed) {
            absorbed = false;
            Iterator<BoundingBox> it = this.rooms.iterator();
            while (it.hasNext()) {
                BoundingBox existing = it.next();
                if (existing.intersects(merged.inflatedBy(1))) {
                    merged = merged.encapsulate(existing);
                    it.remove();
                    absorbed = true;
                }
            }
        }
        this.rooms.add(merged);
        this.setDirty();
    }

    /** The boss room containing {@code pos}, if any. */
    public Optional<BoundingBox> roomAt(BlockPos pos) {
        for (BoundingBox room : this.rooms) {
            if (room.isInside(pos)) {
                return Optional.of(room);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return this.rooms.size();
    }

    public static BossRoomRegistry load(CompoundTag tag) {
        BossRoomRegistry data = new BossRoomRegistry();
        for (Tag entry : tag.getList(ROOMS_TAG, Tag.TAG_INT_ARRAY)) {
            int[] b = ((net.minecraft.nbt.IntArrayTag) entry).getAsIntArray();
            if (b.length == 6) {
                data.rooms.add(new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BoundingBox room : this.rooms) {
            list.add(new net.minecraft.nbt.IntArrayTag(new int[] {
                    room.minX(), room.minY(), room.minZ(), room.maxX(), room.maxY(), room.maxZ()}));
        }
        tag.put(ROOMS_TAG, list);
        return tag;
    }
}
