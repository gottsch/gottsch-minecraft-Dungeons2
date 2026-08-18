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
package mod.gottsch.forge.dungeons2.core.util;

import mod.gottsch.forge.gottschcore.mobset.MobSetData;
import mod.gottsch.forge.gottschcore.mobset.WeightedMob;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The mob-set &rarr; vanilla {@code minecraft:spawner} conversion.
 *
 * <p>Every assertion here is about text that has to survive a parser, so they all round-trip
 * through {@code TagParser} rather than comparing strings: a builder that emits plausible-looking
 * SNBT which does not parse would produce a spawner with vanilla's own defaults &mdash; a pig cage
 * in a dungeon &mdash; and string equality would not notice.</p>
 */
class VanillaSpawnerNbtTest {

    private static final ResourceLocation SET = new ResourceLocation("dungeons2", "classic_vermin");

    private static MobSetData set(WeightedMob... mobs) {
        return new MobSetData(SET, "classic", 2, 4, List.of(mobs), false);
    }

    private static WeightedMob mob(String id, int weight) {
        return new WeightedMob(new ResourceLocation(id), weight);
    }

    @Test
    void spawnDataParsesToTheEntityVanillaReads() throws Exception {
        CompoundTag tag = TagParser.parseTag(VanillaSpawnerNbt.spawnData("dungeons2:rat"));
        assertEquals("dungeons2:rat",
                tag.getCompound("entity").getString("id"),
                "vanilla reads the mob at SpawnData.entity.id -- any other shape shows a pig");
    }

    /**
     * The trap this whole channel exists for. {@code SpawnPotentials} is a <strong>list</strong>,
     * and {@code TagParser.parseTag} only accepts a compound, so the value has to be wrapped to be
     * parsed at all. If this ever became a compound, both routes would still "work" right up until
     * vanilla ignored it.
     */
    @Test
    void spawnPotentialsIsAListAndNeedsTheWrapToParse() {
        String snbt = VanillaSpawnerNbt.spawnPotentials(List.of(mob("dungeons2:rat", 10)));

        assertThrows(Exception.class, () -> TagParser.parseTag(snbt),
                "a bare list must NOT parse as a compound -- if it does, the wrap is pointless"
                        + " and the reason for DungeonPiece#parseNbtValue has been lost");

        Tag wrapped = assertDoesNotThrowValue(snbt);
        assertInstanceOf(ListTag.class, wrapped, "SpawnPotentials must stay a list");
    }

    private static Tag assertDoesNotThrowValue(String snbt) {
        try {
            return TagParser.parseTag("{v:" + snbt + "}").get("v");
        } catch (Exception e) {
            throw new AssertionError("the wrap-and-unwrap trick failed to parse: " + snbt, e);
        }
    }

    @Test
    void everyMobInTheSetReachesThePotentialsWithItsWeight() {
        String snbt = VanillaSpawnerNbt.spawnPotentials(
                List.of(mob("dungeons2:rat", 10), mob("dungeons2:giant_rat", 3)));
        ListTag potentials = (ListTag) assertDoesNotThrowValue(snbt);

        assertEquals(2, potentials.size(), "the whole set maps across, not just the drawn mob");
        CompoundTag first = potentials.getCompound(0);
        assertEquals(10, first.getInt("weight"));
        assertEquals("dungeons2:rat",
                first.getCompound("data").getCompound("entity").getString("id"));
        assertEquals(3, potentials.getCompound(1).getInt("weight"));
    }

    /**
     * Zero-weighted mobs are dropped rather than passed through: vanilla accepts them into its
     * weighted list and then never draws them, so a wholly zero-weighted set would build a cage
     * that looks configured and spawns nothing.
     */
    @Test
    void zeroAndNegativeWeightsAreDropped() {
        assertEquals(1, VanillaSpawnerNbt.usableMobs(
                set(mob("dungeons2:rat", 10), mob("dungeons2:giant_rat", 0))).size());
        assertTrue(VanillaSpawnerNbt.usableMobs(
                        set(mob("dungeons2:rat", 0), mob("dungeons2:giant_rat", -1))).isEmpty(),
                "an entirely un-drawable set must read as unusable, so the caller places nothing");
    }

    @Test
    void anEmptyOrNullSetIsUnusableRatherThanThrowing() {
        assertTrue(VanillaSpawnerNbt.usableMobs(null).isEmpty());
        assertTrue(VanillaSpawnerNbt.usableMobs(set()).isEmpty());
    }

    /** The scalars ride the existing flat channel, so they must all be plain integers. */
    @Test
    void tuningIsAllIntegersAndClampsSpawnCount() {
        var tuning = VanillaSpawnerNbt.tuning(3);
        tuning.forEach((key, value) -> assertDoesNotThrowInt(key, value));
        assertEquals("3", tuning.get(VanillaSpawnerNbt.SPAWN_COUNT));

        assertEquals("1", VanillaSpawnerNbt.tuning(0).get(VanillaSpawnerNbt.SPAWN_COUNT),
                "a SpawnCount of 0 is a cage that ticks forever and produces nothing");
        assertEquals("1", VanillaSpawnerNbt.tuning(-5).get(VanillaSpawnerNbt.SPAWN_COUNT));
    }

    private static void assertDoesNotThrowInt(String key, String value) {
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new AssertionError("tuning value '" + key + "' is not an int: " + value, e);
        }
    }

    /** The whole tag, assembled the way both routes assemble it, has to be readable by vanilla. */
    @Test
    void theAssembledTagCarriesEverythingVanillaNeeds() throws Exception {
        List<WeightedMob> mobs =
                VanillaSpawnerNbt.usableMobs(set(mob("dungeons2:rat", 10), mob("dungeons2:giant_rat", 3)));

        CompoundTag tag = new CompoundTag();
        tag.put(VanillaSpawnerNbt.SPAWN_DATA,
                TagParser.parseTag(VanillaSpawnerNbt.spawnData("dungeons2:rat")));
        tag.put(VanillaSpawnerNbt.SPAWN_POTENTIALS,
                assertDoesNotThrowValue(VanillaSpawnerNbt.spawnPotentials(mobs)));
        VanillaSpawnerNbt.tuning(2).forEach((k, v) -> tag.putInt(k, Integer.parseInt(v)));

        assertNotNull(tag.getCompound(VanillaSpawnerNbt.SPAWN_DATA));
        assertEquals(2, tag.getList(VanillaSpawnerNbt.SPAWN_POTENTIALS, Tag.TAG_COMPOUND).size());
        assertEquals(2, tag.getInt(VanillaSpawnerNbt.SPAWN_COUNT));
        assertEquals(VanillaSpawnerNbt.DEFAULT_REQUIRED_PLAYER_RANGE,
                tag.getInt(VanillaSpawnerNbt.REQUIRED_PLAYER_RANGE));
    }
}
