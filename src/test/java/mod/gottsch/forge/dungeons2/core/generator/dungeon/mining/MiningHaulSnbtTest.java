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

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SNBT a {@link MiningHaul} emits, run through vanilla's own parser and container loader
 * &mdash; backlog #7.
 *
 * <h2>Why this is its own test</h2>
 * <p>The haul travels to the world as <em>text</em>: {@code BlockEntityData#withNbt} holds SNBT
 * unparsed, because the data layer has no Minecraft on its classpath, and it is parsed much later in
 * {@code DungeonPiece#applyBlockEntity} against a real block entity. So a malformed tag &mdash; or a
 * well-formed one with an {@code int} where vanilla wants a {@code byte} &mdash; is not a compile
 * error, not a load error, and not a crash. It is an empty chest, discovered by a player.</p>
 *
 * <p>That is not hypothetical here. {@code Slot} and {@code Count} are both {@code byte}s in a
 * vanilla container tag, and {@code BlockEntityData}'s own documentation records a real bug of
 * exactly this shape one layer up: {@code proximity} stored as a string and read back as 0.</p>
 *
 * @author Mark Gottschling on Aug 31, 2026
 */
class MiningHaulSnbtTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ListTag parse(MiningHaul haul) throws Exception {
        CompoundTag tag = TagParser.parseTag("{Items:" + haul.itemsSnbt() + "}");
        return tag.getList("Items", Tag.TAG_COMPOUND);
    }

    /**
     * Vanilla parses it, and reads back the items that went in &mdash; asserted through
     * {@code ContainerHelper.loadAllItems}, the very code a chest block entity uses.
     *
     * <p>Going through the real loader rather than reading the tag by hand is the point: it is what
     * would catch {@code Count} as an int, since a tag that parses can still load as an empty
     * container.</p>
     *
     * <p>Asserted on the container's CONTENTS rather than on particular slots, because the slots are
     * shuffled. What matters is that every item put in comes back out, wherever it landed.</p>
     */
    @Test
    void vanillaLoadsTheHaulBackAsRealItemStacks() throws Exception {
        MiningHaul haul = new MiningHaul(List.of(
                new MiningHaul.Stack("minecraft:diamond", 3),
                new MiningHaul.Stack("minecraft:coal", 12)));

        NonNullList<ItemStack> contents = load(haul);
        assertEquals(3, countOf(contents, Items.DIAMOND), "the diamonds did not come back");
        assertEquals(12, countOf(contents, Items.COAL), "the coal did not come back");
        assertEquals(2, occupiedSlots(contents), "two stacks should occupy exactly two slots");
    }

    /**
     * <strong>The stacks are scattered, not stacked up from slot 0.</strong>
     *
     * <p>Mark, 2026-08-31: "shuffle the chest slots though so it looks a little randomly placed." A
     * chest filled from slot 0 in sorted order reads as generated the moment it is opened.</p>
     *
     * <p>Asserted as "not the sorted arrangement" over several distinct hauls rather than on one,
     * because any single shuffle can legitimately come back looking tidy. The seed is derived from
     * the haul's contents, so different hauls are independent draws.</p>
     */
    @Test
    void theStacksAreScatteredAcrossTheChest() throws Exception {
        int tidy = 0;
        for (int extra = 0; extra < 8; extra++) {
            MiningHaul haul = new MiningHaul(List.of(
                    new MiningHaul.Stack("minecraft:diamond", 2),
                    new MiningHaul.Stack("minecraft:raw_gold", 9 + extra),
                    new MiningHaul.Stack("minecraft:coal", 40 + extra)));
            ListTag items = parse(haul);
            boolean fromZeroInOrder = items.getCompound(0).getByte("Slot") == 0
                    && items.getCompound(1).getByte("Slot") == 1
                    && items.getCompound(2).getByte("Slot") == 2;
            if (fromZeroInOrder) {
                tidy++;
            }
        }
        assertTrue(tidy <= 1, tidy + " of 8 hauls filled slots 0,1,2 in order -- the shuffle is not"
                + " happening, or is not varying with the haul");
    }

    /** Whatever the arrangement, no two fills ever share a slot. */
    @Test
    void everyFillGetsItsOwnSlot() throws Exception {
        MiningHaul haul = new MiningHaul(List.of(
                new MiningHaul.Stack("minecraft:diamond", 2),
                new MiningHaul.Stack("minecraft:coal", 64 * 5)));
        ListTag items = parse(haul);

        Set<Byte> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            byte slot = items.getCompound(i).getByte("Slot");
            assertTrue(slot >= 0 && slot < MiningHaul.CHEST_SLOTS, "slot " + slot + " is off the chest");
            assertTrue(seen.add(slot), "slot " + slot + " was written twice, so one stack is lost");
        }
        assertEquals(6, items.size(), "2 diamonds and 5 stacks of coal is six fills");
    }

    /**
     * The same haul always lands in the same arrangement.
     *
     * <p>Not cosmetic. {@code postProcess} re-runs per chunk and the haul is re-rendered after the
     * piece is loaded from the save, so a shuffle that varied between calls would rewrite the chest
     * with a different arrangement each time &mdash; and, at a chunk seam, would make the two runs
     * disagree about what the chest holds.</p>
     */
    @Test
    void theSameHaulAlwaysLandsInTheSameArrangement() {
        MiningHaul haul = new MiningHaul(List.of(
                new MiningHaul.Stack("minecraft:diamond", 2),
                new MiningHaul.Stack("minecraft:coal", 70)));
        String first = haul.itemsSnbt();
        assertEquals(first, haul.itemsSnbt(), "two calls on one haul disagreed");
        assertEquals(first, new MiningHaul(List.copyOf(haul.stacks())).itemsSnbt(),
                "an equal haul rebuilt from the same stacks arranged itself differently, so the"
                        + " arrangement would not survive the NBT round trip");
    }

    /** A count over one stack is split across slots, because vanilla's Count is a byte. */
    @Test
    void aCountOverAStackIsSplitAcrossSlots() throws Exception {
        MiningHaul haul = new MiningHaul(List.of(new MiningHaul.Stack("minecraft:coal", 150)));
        ListTag items = parse(haul);

        assertEquals(3, items.size(), "150 coal should occupy three slots");
        // Which slot holds which fill is shuffled, so the counts are asserted as a multiset.
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            counts.add((int) items.getCompound(i).getByte("Count"));
        }
        counts.sort(null);
        assertEquals(List.of(22, 64, 64), counts, "150 coal should split 64 + 64 + 22");
        assertEquals(150, countOf(load(haul), Items.COAL), "coal was lost in the split");
    }

    /**
     * Slot and Count are BYTES, not ints.
     *
     * <p>Asserted on the tag type itself rather than on the value, because the values agree either
     * way and only the type is wrong. This is the assertion that fails if the {@code b} suffixes are
     * ever dropped from the emitted text.</p>
     */
    @Test
    void slotAndCountAreBytes() throws Exception {
        ListTag items = parse(new MiningHaul(List.of(new MiningHaul.Stack("minecraft:redstone", 5))));
        CompoundTag entry = items.getCompound(0);
        assertEquals(Tag.TAG_BYTE, entry.get("Slot").getId(), "Slot must be a byte");
        assertEquals(Tag.TAG_BYTE, entry.get("Count").getId(), "Count must be a byte");
        assertEquals(Tag.TAG_STRING, entry.get("id").getId(), "id must be a string");
    }

    /**
     * A haul too big for one chest is truncated from the END, and the end is the common ore.
     *
     * <p>Truncation is a degradation that should never fire on the shipped table
     * ({@code MiningHaulCalibrationTest.everyHaulFitsInOneChest} holds it to that), but if it ever
     * does it must not be the diamonds that vanish.</p>
     */
    @Test
    void anOverfullHaulDropsTheCommonOreAndKeepsTheRare() throws Exception {
        MiningHaul haul = new MiningHaul(List.of(
                new MiningHaul.Stack("minecraft:diamond", 2),
                new MiningHaul.Stack("minecraft:coal", 64 * 40)));
        ListTag items = parse(haul);

        assertEquals(MiningHaul.CHEST_SLOTS, items.size(), "should have filled the chest exactly");
        // Somewhere in the chest, not slot 0: the shuffle decides where, the rarest-first fill order
        // decides that it survived at all. Those are the two halves itemsSnbt keeps separate.
        assertEquals(2, countOf(load(haul), Items.DIAMOND),
                "the diamonds were dropped instead of the coal");
        assertTrue(haul.slotsNeeded() > MiningHaul.CHEST_SLOTS,
                "this test needs a haul that genuinely overflows");
    }

    // -------- helpers --------

    /** The haul loaded into a chest-sized container, through vanilla's own loader. */
    private static NonNullList<ItemStack> load(MiningHaul haul) throws Exception {
        CompoundTag tag = TagParser.parseTag("{Items:" + haul.itemsSnbt() + "}");
        NonNullList<ItemStack> contents =
                NonNullList.withSize(MiningHaul.CHEST_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, contents);
        return contents;
    }

    /** How many of {@code item} the container holds, across however many slots. */
    private static int countOf(NonNullList<ItemStack> contents, Item item) {
        int total = 0;
        for (ItemStack stack : contents) {
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int occupiedSlots(NonNullList<ItemStack> contents) {
        int occupied = 0;
        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    /** An empty haul emits an empty list rather than malformed text. */
    @Test
    void anEmptyHaulEmitsAnEmptyList() throws Exception {
        assertEquals("[]", new MiningHaul(List.of()).itemsSnbt());
        assertEquals(0, parse(new MiningHaul(List.of())).size());
    }

}
