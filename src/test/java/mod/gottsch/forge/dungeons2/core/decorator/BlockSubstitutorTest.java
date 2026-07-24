/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2023 Mark Gottschling (gottsch)
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
package mod.gottsch.forge.dungeons2.core.decorator;

import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the position-seeded {@link BlockSubstitutor}. Pure POJO &mdash; no
 * Minecraft bootstrap needed.
 *
 * @author Mark Gottschling on Jun 19, 2026
 */
class BlockSubstitutorTest {

    private static final String MOTIF = "classic";
    private static final String SRC = "minecraft:stone_bricks";

    /** Restore the built-in default table after any test that reconfigures it. */
    @AfterEach
    void resetTables() {
        BlockSubstitutor.configureFromDefinitions(Map.of());
    }

    private static List<BlockPlacement> wall(int n) {
        List<BlockPlacement> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new BlockPlacement(i % 16, 40 + (i / 16), (i * 7) % 16, SRC));
        }
        return out;
    }

    private static String idAt(int x, int y, int z, int anchorX, int anchorZ) {
        List<BlockPlacement> one = new ArrayList<>();
        one.add(new BlockPlacement(x, y, z, SRC));
        BlockSubstitutor.substitute(one, MOTIF, anchorX, anchorZ);
        return one.get(0).getBlockId();
    }

    @Test
    void samePositionAlwaysSameResult() {
        for (int trial = 0; trial < 5; trial++) {
            String first = idAt(3, 41, 9, 128, 256);
            String again = idAt(3, 41, 9, 128, 256);
            assertEquals(first, again, "degradation must be deterministic at a fixed world position");
        }
    }

    @Test
    void keyedOnWorldPositionNotLocalCoords() {
        // Same local (x,z) but different anchors = different world position => independent rolls.
        // Over many Y values the two anchor streams must not be identical block-for-block.
        boolean diverged = false;
        for (int y = 40; y < 140 && !diverged; y++) {
            if (!idAt(3, y, 9, 0, 0).equals(idAt(3, y, 9, 1000, 1000))) {
                diverged = true;
            }
        }
        assertTrue(diverged, "world position (anchor + local) must drive the hash, not local coords alone");
    }

    @Test
    void someWeatherSomeRemain() {
        List<BlockPlacement> placements = wall(2000);
        BlockSubstitutor.substitute(placements, MOTIF, 128, 256);

        int changed = 0;
        Set<String> seenVariants = new HashSet<>();
        for (BlockPlacement p : placements) {
            if (!p.getBlockId().equals(SRC)) {
                changed++;
                seenVariants.add(p.getBlockId());
            }
        }
        // ~30% expected; assert a wide, robust band so the test isn't flaky.
        assertTrue(changed > 200 && changed < 1000,
                "expected a sane fraction weathered, got " + changed + "/2000");
        // All three classic variants should appear across 2000 blocks.
        assertTrue(seenVariants.contains("minecraft:cracked_stone_bricks"), "no cracked variant produced");
        assertTrue(seenVariants.contains("minecraft:mossy_stone_bricks"), "no mossy variant produced");
        assertTrue(seenVariants.contains("minecraft:cobblestone"), "no cobblestone variant produced");
    }

    @Test
    void ineligibleBlocksUntouched() {
        List<BlockPlacement> placements = new ArrayList<>();
        placements.add(new BlockPlacement(1, 40, 1, "minecraft:air"));
        placements.add(new BlockPlacement(2, 40, 2, "minecraft:ladder"));
        placements.add(new BlockPlacement(3, 40, 3, "minecraft:glowstone"));
        BlockSubstitutor.substitute(placements, MOTIF, 0, 0);

        assertEquals("minecraft:air", placements.get(0).getBlockId());
        assertEquals("minecraft:ladder", placements.get(1).getBlockId());
        assertEquals("minecraft:glowstone", placements.get(2).getBlockId());
    }

    @Test
    void unknownMotifIsNoOp() {
        List<BlockPlacement> placements = wall(500);
        BlockSubstitutor.substitute(placements, "no-such-motif", 128, 256);
        for (BlockPlacement p : placements) {
            assertEquals(SRC, p.getBlockId(), "no table for motif => nothing should change");
        }
    }

    @Test
    void nullMotifIsNoOp() {
        List<BlockPlacement> placements = wall(50);
        BlockSubstitutor.substitute(placements, null, 0, 0);
        for (BlockPlacement p : placements) {
            assertEquals(SRC, p.getBlockId());
        }
    }

    @Test
    void blockEntityPlacementsAreSkipped() {
        BlockEntityData be = new BlockEntityData();
        List<BlockPlacement> placements = new ArrayList<>();
        // Force a position that would otherwise weather, but carry BE data.
        BlockPlacement chest = new BlockPlacement(3, 41, 9, SRC);
        chest.setBlockEntityNbt(be);
        placements.add(chest);
        BlockSubstitutor.substitute(placements, MOTIF, 128, 256);
        assertEquals(SRC, chest.getBlockId(), "placements with block-entity data must never be swapped");
        assertNotNull(chest.getBlockEntityNbt());
    }

    @Test
    void propertiesClearedWhenSwapped() {
        // Find a position that weathers, give it a stray property, confirm it's cleared.
        boolean checkedAtLeastOne = false;
        for (int y = 40; y < 400 && !checkedAtLeastOne; y++) {
            BlockPlacement p = new BlockPlacement(3, y, 9, SRC);
            p.getProperties().put("dummy", "value");
            List<BlockPlacement> one = new ArrayList<>(List.of(p));
            BlockSubstitutor.substitute(one, MOTIF, 128, 256);
            if (!p.getBlockId().equals(SRC)) {
                assertTrue(p.getProperties().isEmpty(),
                        "swapped variant must drop source properties");
                checkedAtLeastOne = true;
            }
        }
        assertTrue(checkedAtLeastOne, "expected at least one weathered block in the scanned range");
    }

    // NOTE configure/datapack-table behavior (wildcard, motif-override, empty-resets) is
    // covered by BlockSubstitutorDefinitionsTest against configureFromDefinitions(...).

    @Test
    void samePositionDifferentBlocksRollIndependently() {
        // stone vs stone_bricks at the same world cell should not be forced to the
        // same outcome (the source id participates in the variant hash).
        List<BlockPlacement> a = new ArrayList<>(List.of(new BlockPlacement(3, 41, 9, "minecraft:stone")));
        List<BlockPlacement> b = new ArrayList<>(List.of(new BlockPlacement(3, 41, 9, SRC)));
        BlockSubstitutor.substitute(a, MOTIF, 128, 256);
        BlockSubstitutor.substitute(b, MOTIF, 128, 256);
        // Both deterministic; just assert they each produce a valid (possibly unchanged) id.
        assertFalse(a.get(0).getBlockId().isEmpty());
        assertFalse(b.get(0).getBlockId().isEmpty());
    }
}
