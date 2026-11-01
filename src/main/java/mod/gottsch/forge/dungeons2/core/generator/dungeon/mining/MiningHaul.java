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
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
     * <h2>Which stacks get in, and where they sit, are two separate questions</h2>
     * <p><strong>What gets in</strong> is decided in list order, 64 to a slot, and anything past
     * {@link #CHEST_SLOTS} is <strong>dropped</strong> rather than spilling into a second chest. So
     * the order this haul was built in decides what survives a chest that will not hold everything,
     * which is why {@link MiningChestPlanner} builds it rarest-first: a dungeon that excavated
     * enough coal to fill a chest on its own must not push the diamonds out.</p>
     *
     * <p><strong>Where each one sits</strong> is then <em>shuffled</em> across the chest (Mark,
     * 2026-08-31: "shuffle the chest slots though so it looks a little randomly placed"). Filling
     * from slot 0 in sorted order produced a chest that reads as generated the moment it is opened
     * &mdash; diamonds top-left, then a neat descending block of ore. A spoil pile is a heap
     * somebody tipped out, so it is laid out like one. Nothing downstream depends on slot order:
     * vanilla reads each entry's own {@code Slot} field.</p>
     *
     * <p>The two are kept separate deliberately. Shuffling the stacks themselves and then filling
     * from slot 0 would be one line shorter and would silently undo the truncation guarantee above,
     * because the coal could then sort ahead of the diamonds.</p>
     */
    public String itemsSnbt() {
        List<Stack> fills = slotFills();
        int[] slots = shuffledSlots(fills.size());

        // Emitted in SLOT order rather than fill order -- the tag is read by a person debugging a
        // chest, and one whose Slot values ascend is far easier to check against what the container
        // actually shows. Vanilla does not care either way.
        String[] bySlot = new String[CHEST_SLOTS];
        for (int i = 0; i < fills.size(); i++) {
            Stack fill = fills.get(i);
            // Slot and Count are BYTES in a vanilla container tag, not ints. A plain integer decodes
            // to the wrong tag type and the item silently does not appear -- the stringified-value
            // trap BlockEntityData's own doc warns about, one layer down.
            bySlot[slots[i]] = "{Slot:" + slots[i] + "b,id:\"" + fill.item()
                    + "\",Count:" + fill.count() + "b}";
        }

        StringBuilder snbt = new StringBuilder("[");
        boolean first = true;
        for (String entry : bySlot) {
            if (entry == null) {
                continue;
            }
            if (!first) {
                snbt.append(',');
            }
            snbt.append(entry);
            first = false;
        }
        return snbt.append(']').toString();
    }

    /**
     * The haul split into slot-sized fills, in list order, truncated at {@link #CHEST_SLOTS}.
     * Rarest-first is preserved here, which is what makes the truncation drop the common ore.
     */
    private List<Stack> slotFills() {
        List<Stack> fills = new ArrayList<>();
        for (Stack stack : stacks) {
            int remaining = stack.count();
            while (remaining > 0 && fills.size() < CHEST_SLOTS) {
                int inSlot = Math.min(remaining, MAX_PER_SLOT);
                fills.add(new Stack(stack.item(), inSlot));
                remaining -= inSlot;
            }
        }
        return fills;
    }

    /**
     * {@code count} distinct slot numbers drawn from the whole chest, shuffled.
     *
     * <h2>Seeded from the haul's own contents</h2>
     * <p>The layout has to be identical every time this is called: the chest is written during
     * {@code postProcess}, which re-runs per chunk, and the same haul is re-rendered after the piece
     * is loaded back from the save. A {@code RandomSource} threaded in from the room would satisfy
     * that today and quietly stop doing so the moment anything upstream of it draws one more number.
     * Deriving the seed from the contents makes the layout a pure function of the haul, so it cannot
     * drift &mdash; and two dungeons that excavated identically getting the same arrangement is not
     * something anybody can observe.</p>
     *
     * <p>The mix is written out with {@code String#hashCode} rather than taken from the record's
     * generated {@code hashCode}: {@code String}'s is specified by the JDK and will be the same in
     * ten years, a record's is produced by an unspecified bootstrap method and is not promised to be
     * stable at all. Getting that wrong would re-arrange every existing chest on a JDK upgrade.</p>
     */
    private int[] shuffledSlots(int count) {
        long seed = 1125899906842597L; // an odd prime; any fixed non-zero start does
        for (Stack stack : stacks) {
            seed = seed * 31L + stack.item().hashCode();
            seed = seed * 31L + stack.count();
        }
        Random random = new Random(seed);

        int[] slots = new int[CHEST_SLOTS];
        for (int i = 0; i < CHEST_SLOTS; i++) {
            slots[i] = i;
        }
        // Fisher-Yates over the whole chest, then take the first `count`. Shuffling all 27 rather
        // than picking `count` of them is what spreads a small haul across the chest instead of
        // clustering it in the low slots.
        for (int i = CHEST_SLOTS - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = slots[i];
            slots[i] = slots[j];
            slots[j] = swap;
        }
        return Arrays.copyOf(slots, count);
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
