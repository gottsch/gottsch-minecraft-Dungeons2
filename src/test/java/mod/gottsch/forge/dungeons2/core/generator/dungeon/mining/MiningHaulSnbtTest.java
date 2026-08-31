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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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
     */
    @Test
    void vanillaLoadsTheHaulBackAsRealItemStacks() throws Exception {
        MiningHaul haul = new MiningHaul(List.of(
                new MiningHaul.Stack("minecraft:diamond", 3),
                new MiningHaul.Stack("minecraft:coal", 12)));

        CompoundTag tag = TagParser.parseTag("{Items:" + haul.itemsSnbt() + "}");
        NonNullList<ItemStack> contents = NonNullList.withSize(MiningHaul.CHEST_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, contents);

        assertEquals(Items.DIAMOND, contents.get(0).getItem(),
                "slot 0 did not come back as the diamonds; the haul must sort rarest-first so a"
                        + " truncated chest keeps them");
        assertEquals(3, contents.get(0).getCount());
        assertEquals(Items.COAL, contents.get(1).getItem());
        assertEquals(12, contents.get(1).getCount());
    }

    /** A count over one stack is split across slots, because vanilla's Count is a byte. */
    @Test
    void aCountOverAStackIsSplitAcrossSlots() throws Exception {
        MiningHaul haul = new MiningHaul(List.of(new MiningHaul.Stack("minecraft:coal", 150)));
        ListTag items = parse(haul);

        assertEquals(3, items.size(), "150 coal should occupy three slots");
        assertEquals(64, items.getCompound(0).getByte("Count"));
        assertEquals(64, items.getCompound(1).getByte("Count"));
        assertEquals(22, items.getCompound(2).getByte("Count"));
        assertEquals(0, items.getCompound(0).getByte("Slot"));
        assertEquals(2, items.getCompound(2).getByte("Slot"));
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
        assertEquals("minecraft:diamond", items.getCompound(0).getString("id"),
                "the diamonds were dropped instead of the coal");
        assertTrue(haul.slotsNeeded() > MiningHaul.CHEST_SLOTS,
                "this test needs a haul that genuinely overflows");
    }

    /** An empty haul emits an empty list rather than malformed text. */
    @Test
    void anEmptyHaulEmitsAnEmptyList() throws Exception {
        assertEquals("[]", new MiningHaul(List.of()).itemsSnbt());
        assertEquals(0, parse(new MiningHaul(List.of())).size());
    }

}
