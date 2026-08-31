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
package mod.gottsch.forge.dungeons2.core.generator.dungeon.mining;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Mining Chest holds: a list of items and counts, and the SNBT that puts them in a chest.
 *
 * <h2>Contents, not a loot table</h2>
 * <p>Every other chest in this mod carries a {@code LootTable} and a {@code LootTableSeed} and lets
 * vanilla roll it. This one cannot: its contents are computed per dungeon from what that dungeon
 * excavated, and a loot table is authored ahead of time and knows nothing about the dungeon it is
 * rolled in. So the items are written straight into the block entity's {@code Items} list, which is
 * the same thing vanilla's own {@code /setblock} with NBT does.</p>
 *
 * <p>That rides the {@code nbtValues} half of {@code BlockEntityData} &mdash; the SNBT map added for
 * the vanilla spawner's nested compounds. {@code Items} is the first <em>list</em> to use it, and it
 * needs no new machinery: {@code DungeonPiece#applyBlockEntity} parses whatever is in that map with
 * {@code TagParser} and merges it into the entity's own tag.</p>
 *
 * <p>No Minecraft imports, like the rest of the data layer &mdash; the SNBT is text until
 * {@code DungeonPiece} parses it. That is also why counts are plain ints here and only become
 * {@code byte}s in the emitted text.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
public record MiningHaul(List<Stack> stacks) {

    /** A vanilla single chest. Contents beyond this are dropped &mdash; see {@link #itemsSnbt}. */
    public static final int CHEST_SLOTS = 27;

    /** Vanilla's per-slot ceiling for an ordinary item, and the reason a haul is split at all. */
    public static final int MAX_PER_SLOT = 64;

    /** One item and how many of it, before any split into slot-sized stacks. */
    public record Stack(String item, int count) {}

    public boolean isEmpty() {
        return stacks == null || stacks.isEmpty();
    }

    /** Total items, for logging and for the tests that calibrate the table. */
    public int totalItems() {
        int total = 0;
        for (Stack stack : stacks) {
            total += stack.count();
        }
        return total;
    }

    /**
     * The chest's {@code Items} list as SNBT.
     *
     * <p>Slots are filled in list order, 64 to a slot, and anything past {@link #CHEST_SLOTS} is
     * <strong>dropped</strong> rather than spilling into a second chest. So the order this haul was
     * built in decides what survives a chest that will not hold everything, which is why
     * {@link MiningChestPlanner} builds it rarest-first: a dungeon that excavated enough coal to
     * fill a chest on its own must not push the diamonds out.</p>
     */
    public String itemsSnbt() {
        StringBuilder snbt = new StringBuilder("[");
        int slot = 0;
        for (Stack stack : stacks) {
            int remaining = stack.count();
            while (remaining > 0 && slot < CHEST_SLOTS) {
                int inSlot = Math.min(remaining, MAX_PER_SLOT);
                if (slot > 0) {
                    snbt.append(',');
                }
                // Slot and Count are BYTES in a vanilla container tag, not ints. A plain integer
                // decodes to the wrong tag type and the item silently does not appear -- the
                // stringified-value trap BlockEntityData's own doc warns about, one layer down.
                snbt.append("{Slot:").append(slot).append("b,id:\"").append(stack.item())
                        .append("\",Count:").append(inSlot).append("b}");
                remaining -= inSlot;
                slot++;
            }
        }
        return snbt.append(']').toString();
    }

    /** How many slots {@link #itemsSnbt} would fill, uncapped &mdash; for the overflow log. */
    public int slotsNeeded() {
        int slots = 0;
        for (Stack stack : stacks) {
            slots += (stack.count() + MAX_PER_SLOT - 1) / MAX_PER_SLOT;
        }
        return slots;
    }

    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        for (Stack stack : stacks) {
            parts.add(stack.count() + "x " + stack.item());
        }
        return String.join(", ", parts);
    }
}
