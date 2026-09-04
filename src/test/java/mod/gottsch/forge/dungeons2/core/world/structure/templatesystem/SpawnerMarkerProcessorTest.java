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

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import mod.gottsch.forge.dungeons2.core.config.SpawnerConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
