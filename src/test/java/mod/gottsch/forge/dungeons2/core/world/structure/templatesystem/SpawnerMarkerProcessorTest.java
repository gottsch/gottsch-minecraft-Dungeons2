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

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backlog #10: the authored marker block becomes the right block-entity tag.
 *
 * <h2>What is and is not covered, and why</h2>
 * <p>{@code processBlock} resolves {@code DungeonsBlocks.MOB_SET_SPAWNER}, a Forge
 * {@code RegistryObject} that only populates during mod loading &mdash; unreachable in a plain unit
 * test. So the class exposes the two decisions either side of that lookup ({@code isSpawnerMarker},
 * {@code spawnerTag}) and they are tested directly. Same split as
 * {@code DungeonStructure.chooseStartPool}.</p>
 *
 * <p>The marker is matched by <strong>registry id</strong>, which is also what makes it testable:
 * {@code ForgeRegistries.BLOCKS.getKey} answers for vanilla blocks under a bare {@code Bootstrap},
 * so the negative cases below are real. The positive case cannot be built here &mdash;
 * {@code dungeons2:spawner_marker} is not in the registry without mod loading &mdash; so it is
 * covered by {@code marker_block} round-tripping through the codec and by
 * {@code ShippedSpawnerMarkerTest} checking the shipped template actually carries the block.</p>
 *
 * @author Mark Gottschling on Aug 14, 2026
 */
class SpawnerMarkerProcessorTest {

    private static final ResourceLocation VERMIN = new ResourceLocation("dungeons2:classic_vermin");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static SpawnerMarkerProcessor processor() {
        return new SpawnerMarkerProcessor(
                VERMIN, SpawnerMarkerProcessor.DEFAULT_MARKER_BLOCK, 8.0D, 1, 3);
    }

    private static StructureTemplate.StructureBlockInfo block(net.minecraft.world.level.block.Block b) {
        return new StructureTemplate.StructureBlockInfo(BlockPos.ZERO, b.defaultBlockState(), null);
    }

    @Test
    void ordinaryTemplateBlocksAreLeftAlone() {
        // Most of a room template is these. A false positive here would delete authored geometry
        // and replace it with an invisible block.
        for (net.minecraft.world.level.block.Block b : new net.minecraft.world.level.block.Block[] {
                Blocks.STONE_BRICKS, Blocks.AIR, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_LOG}) {
            assertFalse(processor().isSpawnerMarker(block(b)),
                    b + " must not be treated as the spawner marker");
        }
    }

    /**
     * The old design matched this and could not work &mdash; vanilla strips structure blocks out of
     * a jigsaw pool element before the pool's processors run. Kept as a test so nobody re-adds the
     * match. See {@link JigsawStripsStructureBlocksTest}.
     */
    @Test
    void aDataStructureBlockIsNoLongerTheMarker() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("mode", "DATA");
        nbt.putString("metadata", "d2:spawner");
        assertFalse(processor().isSpawnerMarker(new StructureTemplate.StructureBlockInfo(
                        BlockPos.ZERO, Blocks.STRUCTURE_BLOCK.defaultBlockState(), nbt)),
                "matching a DATA structure block is the design that did not work -- it never"
                        + " reaches a pool processor at all");
    }

    @Test
    void theTagCarriesTheConfiguredSetAndTuning() {
        CompoundTag tag = processor().spawnerTag();
        assertEquals(VERMIN.toString(), tag.getString("mobSetName"));
        assertEquals("dungeons2:mob_set_spawner", tag.getString("id"),
                "the id must be the BLOCK ENTITY type's registry name -- vanilla's placeInWorld"
                        + " loads the tag against it, and a wrong one silently yields no entity");
        assertEquals(1, tag.getInt("minMobs"));
        assertEquals(3, tag.getInt("maxMobs"));
        assertEquals(8.0D, tag.getDouble("proximity"));
    }

    /**
     * A second motif can point at its own marker block without code. No longer the ONLY way to get
     * a second mob set -- see the per-cell override tests below -- but still legitimate.
     */
    @Test
    void theMarkerBlockIsConfigurable() {
        SpawnerMarkerProcessor custom = new SpawnerMarkerProcessor(
                VERMIN, new ResourceLocation("dungeons2:other_marker"), 8.0D, 1, 3);
        assertFalse(custom.isSpawnerMarker(block(Blocks.STONE_BRICKS)));
        assertEquals(VERMIN.toString(), custom.spawnerTag().getString("mobSetName"));
    }

    // ---- per-cell overrides (2026-09-03) ------------------------------------------------------
    //
    // These go through Overrides directly rather than processBlock, for the reason the class note
    // gives: the positive marker match needs dungeons2:spawner_marker in the Forge registry, which
    // no headless test has. Overrides is where the whole of the stated-wins rule lives, so testing
    // it here covers both routes and the log line at once.

    private static SpawnerMarkerProcessor.Overrides overrides(CompoundTag nbt) {
        return new SpawnerMarkerProcessor.Overrides(nbt);
    }

    /**
     * The case the boss room needed: one template naming its own set at its own trigger distance,
     * which no value of the pool-wide codec fields can express.
     */
    @Test
    void aMarkerMayNameItsOwnSetAndProximity() {
        CompoundTag marker = new CompoundTag();
        marker.putString("mobSetName", "dungeons2:small_dungeon_boss");
        marker.putDouble("proximity", 20.0D);

        CompoundTag tag = processor().spawnerTag(overrides(marker));
        assertEquals("dungeons2:small_dungeon_boss", tag.getString("mobSetName"));
        assertEquals(20.0D, tag.getDouble("proximity"));
        assertEquals(1, tag.getInt("minMobs"), "an unstated key must still come from the pool");
        assertEquals(3, tag.getInt("maxMobs"), "an unstated key must still come from the pool");
    }

    /**
     * The compatibility property that let every shipped template stay untouched: no NBT at all is
     * the normal case for a structure cell, not an error.
     */
    @Test
    void aMarkerThatStatesNothingIsUnchanged() {
        assertEquals(processor().spawnerTag().toString(),
                processor().spawnerTag(overrides(null)).toString(),
                "a marker with no block-entity NBT must produce exactly the pool's tag");
    }

    /**
     * An author typing {@code proximity:20} in a /data merge writes an IntTag, and {@code getDouble}
     * on one reads 0 -- the same shape of bug as a proximity stored as a string. Accepted and
     * converted rather than ignored, because a silent 0 is a spawner that only fires when the player
     * stands in the cell.
     */
    @Test
    void anIntegerProximityIsAccepted() {
        CompoundTag marker = new CompoundTag();
        marker.putInt("proximity", 20);
        assertEquals(20.0D, processor().spawnerTag(overrides(marker)).getDouble("proximity"));
    }

    // ---- the boss rung (2026-09-04) ------------------------------------------------------------
    //
    // The one field where the POOL outranks the marker, because `boss: true` declares a role and
    // asks to be told the value. These go through spawnerTag(overrides, pooled) with the resolved
    // set passed in, mirroring what processBlock does -- the marker match itself still needs a
    // Forge registry no headless test has.

    private static final ResourceLocation BOSS_SET =
            new ResourceLocation("dungeons2:large_dungeon_boss");

    private static SpawnerMarkerProcessor tiered() {
        return new SpawnerMarkerProcessor(VERMIN, Optional.of(BOSS_SET),
                SpawnerMarkerProcessor.DEFAULT_MARKER_BLOCK, 8.0D, 1, 3,
                mod.gottsch.forge.dungeons2.core.config.SpawnerConfig.Kind.PROXIMITY);
    }

    private static StructureTemplate.StructureBlockInfo marker(CompoundTag nbt) {
        return new StructureTemplate.StructureBlockInfo(BlockPos.ZERO,
                Blocks.STONE_BRICKS.defaultBlockState(), nbt);
    }

    /** The reported bug, in one assertion: the tier's set, not the template's. */
    @Test
    void aBossMarkerTakesTheTiersSet() {
        CompoundTag boss = new CompoundTag();
        boss.putBoolean("boss", true);
        assertTrue(SpawnerMarkerProcessor.isBossMarker(marker(boss)));
        assertEquals(BOSS_SET.toString(),
                tiered().spawnerTag(overrides(boss), BOSS_SET).getString("mobSetName"));
    }

    /**
     * A boss marker that ALSO names a set still takes the tier's. Inverted from every other field
     * on this marker, and deliberately: the flag would otherwise do nothing on the template most
     * likely to carry a leftover id, which is exactly how the tier got welded on the first time.
     * BossRoomAuthoringTest fails such a template outright; this pins the runtime behaviour if one
     * ever reaches here.
     */
    @Test
    void theTierOutranksAnAuthoredSetOnABossMarker() {
        CompoundTag boss = new CompoundTag();
        boss.putBoolean("boss", true);
        boss.putString("mobSetName", "dungeons2:small_dungeon_boss");
        assertEquals(BOSS_SET.toString(),
                tiered().spawnerTag(overrides(boss), BOSS_SET).getString("mobSetName"));
    }

    /**
     * The footgun that made the flag worth having. An ordinary spawner that names no set must take
     * the pool's ORDINARY default, never the boss's -- a boss room holds several of these and
     * exactly one boss.
     */
    @Test
    void aMarkerThatNamesNoSetIsNotTreatedAsTheBoss() {
        assertFalse(SpawnerMarkerProcessor.isBossMarker(marker(null)));
        assertFalse(SpawnerMarkerProcessor.isBossMarker(marker(new CompoundTag())));
        assertEquals(VERMIN.toString(),
                tiered().spawnerTag(overrides(new CompoundTag())).getString("mobSetName"),
                "absence must mean the pool's ordinary set, not the boss's");
    }

    /** A pool that declares no tier leaves a boss marker on the ordinary chain rather than failing. */
    @Test
    void aBossMarkerInAnUntieredPoolFallsThrough() {
        CompoundTag boss = new CompoundTag();
        boss.putBoolean("boss", true);
        boss.putString("mobSetName", "dungeons2:classic_undead");
        assertEquals("dungeons2:classic_undead",
                processor().spawnerTag(overrides(boss)).getString("mobSetName"));
    }

    /** A marker may ask for a visible cage even though the pool's entry means the ambush block. */
    @Test
    void aMarkerMayOverrideTheSpawnerKind() {
        CompoundTag marker = new CompoundTag();
        marker.putString("type", "vanilla");
        assertEquals(SpawnerConfig.Kind.VANILLA,
                overrides(marker).kind(SpawnerConfig.Kind.PROXIMITY));
    }

    /**
     * Degrade, do not throw. This runs on a worldgen thread inside a processor vanilla gives no
     * error path, so a typo in hand-authored NBT has to leave a working dungeon and a WARN.
     */
    @Test
    void aMalformedOverrideFallsBackToThePool() {
        CompoundTag badSet = new CompoundTag();
        badSet.putString("mobSetName", "Not A Resource Location");
        assertEquals(VERMIN, overrides(badSet).mobSet(VERMIN));

        CompoundTag badKind = new CompoundTag();
        badKind.putString("type", "vanila");
        assertEquals(SpawnerConfig.Kind.PROXIMITY,
                overrides(badKind).kind(SpawnerConfig.Kind.PROXIMITY));
    }

    // ---- probability (2026-09-06) --------------------------------------------------------------
    //
    // The authored answer to what `min_count: 0` does for the procedural slot: a marker that
    // sometimes produces no spawner at all. Tested through rollsOut rather than processBlock for
    // the reason in the class note -- the marker MATCH needs a populated Forge registry, this
    // decision does not.

    private static SpawnerMarkerProcessor withProbability(float probability) {
        return new SpawnerMarkerProcessor(VERMIN, Optional.empty(), Optional.empty(),
                Optional.empty(), SpawnerMarkerProcessor.DEFAULT_MARKER_BLOCK, 8.0D, 1, 3,
                probability, SpawnerConfig.Kind.PROXIMITY);
    }

    private static CompoundTag probabilityTag(float probability) {
        CompoundTag marker = new CompoundTag();
        marker.putFloat("probability", probability);
        return marker;
    }

    /**
     * The regression this whole design is shaped around, and the only test that would catch it.
     *
     * <p>An unconditional roll would draw a {@code nextFloat()} ahead of the mob draw on every
     * marker in every world, so every already-generated spawner would show a different mob after
     * this field shipped. Asserting "returns false" is not enough &mdash; the source must be
     * <em>untouched</em>, which is what the second assertion checks.</p>
     */
    @Test
    void aMarkerThatStatesNoProbabilityConsumesNoRandomValue() {
        RandomSource used = RandomSource.create(42L);
        RandomSource untouched = RandomSource.create(42L);

        assertFalse(processor().rollsOut(overrides(null), used),
                "a marker that states nothing must never roll out");
        assertEquals(untouched.nextInt(1000), used.nextInt(1000),
                "an unstated probability must consume NO random value, or every existing world's"
                        + " spawners draw a different mob");
    }

    /** The same guarantee for a pool that states the default explicitly. */
    @Test
    void anExplicitProbabilityOfOneConsumesNoRandomValue() {
        RandomSource used = RandomSource.create(7L);
        RandomSource untouched = RandomSource.create(7L);

        assertFalse(withProbability(1.0F).rollsOut(overrides(probabilityTag(1.0F)), used));
        assertEquals(untouched.nextInt(1000), used.nextInt(1000));
    }

    /** Zero is never, whatever the seed. */
    @Test
    void aProbabilityOfZeroAlwaysRollsOut() {
        for (long seed = 0L; seed < 25L; seed++) {
            assertTrue(withProbability(0.0F).rollsOut(overrides(null), RandomSource.create(seed)),
                    "probability 0 must roll out at every seed, including " + seed);
        }
    }

    /** The layering is the point of putting it in the codec at all: the marker wins, both ways. */
    @Test
    void aMarkerOverridesThePoolsProbability() {
        assertTrue(withProbability(1.0F).rollsOut(overrides(probabilityTag(0.0F)),
                        RandomSource.create(3L)),
                "a marker stating 0 must roll out even though the pool is certain");

        for (long seed = 0L; seed < 25L; seed++) {
            assertFalse(withProbability(0.0F).rollsOut(overrides(probabilityTag(1.0F)),
                            RandomSource.create(seed)),
                    "a marker stating 1 must never roll out even though the pool is never");
        }
    }

    /**
     * Clamped rather than thrown, unlike the datapack field below: this arrives on a worldgen thread
     * where there is no error path, and {@code probability:5} is unambiguous about the intent.
     */
    @Test
    void anOutOfRangeMarkerProbabilityIsClamped() {
        assertEquals(1.0F, overrides(probabilityTag(5.0F)).probability(0.0F));
        assertEquals(0.0F, overrides(probabilityTag(-1.0F)).probability(1.0F));

        for (long seed = 0L; seed < 25L; seed++) {
            assertFalse(withProbability(0.0F).rollsOut(overrides(probabilityTag(5.0F)),
                    RandomSource.create(seed)), "a clamped 5.0 must behave exactly as 1.0");
        }
    }

    /**
     * An author typing {@code probability:0} in a /data merge writes an IntTag, the same trap
     * {@link #anIntegerProximityIsAccepted} covers for proximity. A silently ignored 0 would be a
     * marker that always spawns while its author believes it never does.
     */
    @Test
    void anIntegerProbabilityIsAccepted() {
        CompoundTag marker = new CompoundTag();
        marker.putInt("probability", 0);
        assertEquals(0.0F, overrides(marker).probability(1.0F));
        assertTrue(processor().rollsOut(overrides(marker), RandomSource.create(11L)));
    }

    // ---- the datapack field --------------------------------------------------------------------

    private static DataResult<SpawnerMarkerProcessor> decode(String json) {
        return SpawnerMarkerProcessor.codec(() -> null)
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    /** Absent means certain, which is what keeps every shipped processor list working untouched. */
    @Test
    void aProcessorListThatStatesNoProbabilityDecodesToCertainty() {
        SpawnerMarkerProcessor decoded = decode(
                "{\"mob_set\":\"dungeons2:classic_vermin\",\"proximity\":8.0}")
                .result().orElseThrow();

        RandomSource used = RandomSource.create(5L);
        RandomSource untouched = RandomSource.create(5L);
        assertFalse(decoded.rollsOut(overrides(null), used));
        assertEquals(untouched.nextInt(1000), used.nextInt(1000));
    }

    /** And a stated one is carried through to the roll. */
    @Test
    void aProcessorListMayStateAProbability() {
        SpawnerMarkerProcessor decoded = decode(
                "{\"mob_set\":\"dungeons2:classic_vermin\",\"proximity\":8.0,\"probability\":0.0}")
                .result().orElseThrow();
        assertTrue(decoded.rollsOut(overrides(null), RandomSource.create(9L)));
    }

    /**
     * A load error, NOT a clamp -- the opposite of {@link #anOutOfRangeMarkerProbabilityIsClamped},
     * and deliberately so. A datapack value is authored once and read by a human, so #31's posture
     * applies: say it is wrong rather than quietly meaning something else.
     */
    @Test
    void anOutOfRangeDatapackProbabilityIsALoadError() {
        assertTrue(decode("{\"mob_set\":\"dungeons2:classic_vermin\",\"proximity\":8.0,"
                        + "\"probability\":5.0}").error().isPresent(),
                "probability above 1 must fail the load rather than clamp");
        assertTrue(decode("{\"mob_set\":\"dungeons2:classic_vermin\",\"proximity\":8.0,"
                        + "\"probability\":-0.5}").error().isPresent(),
                "probability below 0 must fail the load rather than clamp");
    }
}
