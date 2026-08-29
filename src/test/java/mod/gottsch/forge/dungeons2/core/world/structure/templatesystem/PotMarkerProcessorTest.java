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
package mod.gottsch.forge.dungeons2.core.world.structure.templatesystem;

import mod.gottsch.forge.dungeons2.core.block.entity.PotMarkerBlockEntity;
import mod.gottsch.forge.dungeons2.core.block.entity.PotMarkerBlockEntity.Variant;
import mod.gottsch.forge.dungeons2.core.data.PotionEffectSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authored vocabulary a {@code dungeons2:pot_marker} carries &mdash; backlog #56.
 *
 * <h2>What can and cannot be tested here</h2>
 * <p>Forge <strong>locks the block registry</strong> outside a running game, so the marker BLOCK and
 * its block entity cannot be constructed in a unit test at all &mdash; the trap the backlog entry
 * flagged before either marker was built, and the same one the fungus-growth entry describes. What
 * survives that is the part where the bugs actually live: the NBT the marker carries is read back by
 * plain static helpers on the processor, and those need no registry. They are package-private for
 * exactly this reason.</p>
 *
 * <p>So this covers the contract between an author and the generator &mdash; what a hand-written tag
 * means, and what an incomplete one falls back to. It does not cover registration, rendering, or the
 * spawn itself; those are in-game checks.</p>
 */
class PotMarkerProcessorTest {

    private static CompoundTag variantTag(String entity, Integer weight) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PotMarkerBlockEntity.ENTITY, entity);
        if (weight != null) {
            tag.putInt(PotMarkerBlockEntity.WEIGHT, weight);
        }
        return tag;
    }

    private static CompoundTag effectTag(String effect, Integer amplifier, Integer duration) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PotMarkerBlockEntity.EFFECT, effect);
        if (amplifier != null) {
            tag.putInt(PotMarkerBlockEntity.AMPLIFIER, amplifier);
        }
        if (duration != null) {
            tag.putInt(PotMarkerBlockEntity.DURATION, duration);
        }
        return tag;
    }

    private static CompoundTag withList(String key, CompoundTag... entries) {
        ListTag list = new ListTag();
        for (CompoundTag entry : entries) {
            list.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.put(key, list);
        return tag;
    }

    // ---------- variants ----------

    @Test
    void aVariantWithNoWeightIsDrawableRatherThanNever() {
        List<Variant> variants = PotMarkerProcessor.variants(
                withList(PotMarkerBlockEntity.VARIANTS, variantTag("dungeonblocks:red_pot", null)));

        // Weight 0 would parse as "never drawn", which is not what someone writing a bare entity id
        // means -- and an undrawable variant is only noticed several dungeons later.
        assertEquals(1, variants.size());
        assertEquals(1, variants.get(0).weight());
        assertEquals("dungeonblocks:red_pot", variants.get(0).entity());
    }

    @Test
    void aVariantWithNoEntityIdIsSkippedRatherThanSpawningNothing() {
        List<Variant> variants = PotMarkerProcessor.variants(withList(PotMarkerBlockEntity.VARIANTS,
                variantTag("", 5), variantTag("dungeonblocks:blue_pot", 2)));

        // Keeping an empty id would let it win the weighted draw and spawn nothing at all, which
        // reads in game as "the marker did not work" rather than "one variant is malformed".
        assertEquals(1, variants.size());
        assertEquals("dungeonblocks:blue_pot", variants.get(0).entity());
    }

    @Test
    void aMarkerWithNoVariantsListReadsAsEmpty() {
        assertTrue(PotMarkerProcessor.variants(new CompoundTag()).isEmpty());
        assertTrue(PotMarkerProcessor.variants(null).isEmpty());
    }

    // ---------- the weighted draw ----------

    @Test
    void theDrawHonoursTheWeights() {
        List<Variant> variants = List.of(
                new Variant("dungeonblocks:red_pot", 9),
                new Variant("dungeonblocks:blue_pot", 1));

        Map<String, Integer> counts = new HashMap<>();
        RandomSource random = RandomSource.create(0xD2_56L);
        for (int i = 0; i < 10_000; i++) {
            counts.merge(PotMarkerProcessor.pick(variants, random), 1, Integer::sum);
        }

        // 9:1 with a 10k sample: the loose bounds are deliberate, the point is that the weights are
        // read at all rather than that the RNG is uniform.
        int red = counts.getOrDefault("dungeonblocks:red_pot", 0);
        int blue = counts.getOrDefault("dungeonblocks:blue_pot", 0);
        assertEquals(10_000, red + blue);
        assertTrue(red > blue * 5,
                "the rarer variant came up too often to be weighted: red " + red + ", blue " + blue);
        assertTrue(blue > 0, "the rarer variant never came up, so the draw is not really weighted");
    }

    @Test
    void aSingleVariantIsAlwaysDrawn() {
        List<Variant> only = List.of(new Variant("dungeonblocks:pot", 1));
        RandomSource random = RandomSource.create(1L);
        for (int i = 0; i < 100; i++) {
            assertEquals("dungeonblocks:pot", PotMarkerProcessor.pick(only, random));
        }
    }

    // ---------- effects ----------

    @Test
    void anEffectWithNoDurationGetsOneLongEnoughToSee() {
        List<PotionEffectSpec> effects = PotMarkerProcessor.effects(
                withList(PotMarkerBlockEntity.EFFECTS, effectTag("minecraft:poison", null, null)));

        // 0 ticks is an effect nobody would ever notice, so a marker that names an effect and
        // nothing else has to still do something visible.
        assertEquals(1, effects.size());
        assertEquals("minecraft:poison", effects.get(0).effect());
        assertEquals(0, effects.get(0).amplifier());
        assertNotEquals(0, effects.get(0).duration());
    }

    @Test
    void amplifierAndDurationAreReadVerbatim() {
        List<PotionEffectSpec> effects = PotMarkerProcessor.effects(withList(
                PotMarkerBlockEntity.EFFECTS, effectTag("minecraft:wither", 2, 600)));

        assertEquals(new PotionEffectSpec("minecraft:wither", 2, 600), effects.get(0));
    }

    @Test
    void severalEffectsAllSurvive() {
        List<PotionEffectSpec> effects = PotMarkerProcessor.effects(withList(
                PotMarkerBlockEntity.EFFECTS,
                effectTag("minecraft:poison", 0, 200),
                effectTag("minecraft:blindness", 0, 100)));

        // A potion carries a SET of effects -- PotionUtils.getAllEffects returns a list and
        // PotionEntity adds every one of them to its cloud -- so the marker must not collapse to one.
        assertEquals(2, effects.size());
    }

    @Test
    void aMarkerWithNoEffectsReadsAsEmpty() {
        assertTrue(PotMarkerProcessor.effects(new CompoundTag()).isEmpty());
        assertTrue(PotMarkerProcessor.effects(null).isEmpty());
    }

    // ---------- loot ----------

    @Test
    void anEmptyLootTableStringIsTreatedAsAbsent() {
        CompoundTag tag = new CompoundTag();
        tag.putString(PotMarkerBlockEntity.LOOT_TABLE, "");

        // So it falls through to the pool-level default rather than being written as a table id of
        // "", which PotEntity#dropLoot would treat as a table and find nothing in.
        assertNull(PotMarkerProcessor.markerLootTable(tag));
        assertNull(PotMarkerProcessor.markerLootTable(new CompoundTag()));
        assertNull(PotMarkerProcessor.markerLootTable(null));
    }

    @Test
    void aMarkerLootTableIsReadBack() {
        CompoundTag tag = new CompoundTag();
        tag.putString(PotMarkerBlockEntity.LOOT_TABLE, "dungeons2:pots/boss");
        assertEquals("dungeons2:pots/boss", PotMarkerProcessor.markerLootTable(tag));
    }

    // ---------- the loot seed ----------

    @Test
    void theSeedIsNeverZeroAndIsStableForAPosition() {
        // 0 means "roll fresh when the pot is broken", which is exactly what worldgen does not want
        // -- a structure's contents are fixed when it generates, like a vanilla structure chest.
        assertNotEquals(0L, PotMarkerProcessor.seedFor(BlockPos.ZERO));

        BlockPos pos = new BlockPos(101, 42, -77);
        assertEquals(PotMarkerProcessor.seedFor(pos), PotMarkerProcessor.seedFor(pos),
                "the same position must seed the same, or each chunk pass rolls a different pot and"
                        + " the bounding-box clip stops being the only thing deciding what spawns");
        assertNotEquals(PotMarkerProcessor.seedFor(pos),
                PotMarkerProcessor.seedFor(pos.above()),
                "two markers in a column would carry identical loot");
    }
}
