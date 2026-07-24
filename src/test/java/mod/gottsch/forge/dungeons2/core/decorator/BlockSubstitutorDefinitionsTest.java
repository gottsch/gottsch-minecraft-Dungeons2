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

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.decorator.data.SubstitutionDefinition;
import mod.gottsch.forge.dungeons2.core.decorator.data.SubstitutionRule;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase B coverage for the datapack entry point
 * {@link BlockSubstitutor#configureFromDefinitions(Map)}: parity with the legacy
 * {@code configure(...)} path, the {@code global} -> wildcard mapping, and the
 * empty-resets-to-defaults contract. Pure POJO &mdash; {@link ResourceLocation} needs
 * no Minecraft bootstrap.
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
class BlockSubstitutorDefinitionsTest {

    private static final String SRC = "minecraft:stone_bricks";

    @AfterEach
    void resetTables() {
        BlockSubstitutor.configureFromDefinitions(Map.of());
    }

    private static ResourceLocation rl(String id) {
        return new ResourceLocation(id);
    }

    private static SubstitutionRule rule(String from, double chance, String... to) {
        return new SubstitutionRule(rl(from), List.of(to).stream().map(BlockSubstitutorDefinitionsTest::rl).toList(),
                Optional.of(chance), Optional.empty());
    }

    private static List<BlockPlacement> wall(int n) {
        List<BlockPlacement> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new BlockPlacement(i % 16, 40 + (i / 16), (i * 7) % 16, SRC));
        }
        return out;
    }

    @Test
    void globalFileAppliesToEveryMotif() {
        BlockSubstitutor.configureFromDefinitions(Map.of(
                "global", new SubstitutionDefinition(List.of(rule(SRC, 1.0, "minecraft:gold_block")))));

        for (String motif : new String[]{"classic", "some-other-motif"}) {
            List<BlockPlacement> placements = wall(100);
            BlockSubstitutor.substitute(placements, motif, 128, 256);
            for (BlockPlacement p : placements) {
                assertEquals("minecraft:gold_block", p.getBlockId(),
                        "global table should apply under motif " + motif);
            }
        }
    }

    @Test
    void motifFileOverridesGlobal() {
        BlockSubstitutor.configureFromDefinitions(Map.of(
                "global", new SubstitutionDefinition(List.of(rule(SRC, 1.0, "minecraft:gold_block"))),
                "classic", new SubstitutionDefinition(List.of(rule(SRC, 1.0, "minecraft:diamond_block")))));

        List<BlockPlacement> classic = wall(50);
        BlockSubstitutor.substitute(classic, "classic", 0, 0);
        for (BlockPlacement p : classic) {
            assertEquals("minecraft:diamond_block", p.getBlockId(),
                    "classic should use its motif-specific table");
        }

        List<BlockPlacement> other = wall(50);
        BlockSubstitutor.substitute(other, "ruins", 0, 0);
        for (BlockPlacement p : other) {
            assertEquals("minecraft:gold_block", p.getBlockId(),
                    "non-classic should fall back to the global table");
        }
    }

    @Test
    void emptyResetsToBuiltInDefaults() {
        // Wipe with an override, then reset to defaults and confirm the built-in
        // classic table is back (stone_bricks weathers under "classic").
        BlockSubstitutor.configureFromDefinitions(Map.of());

        List<BlockPlacement> placements = wall(2000);
        BlockSubstitutor.substitute(placements, "classic", 128, 256);
        long changed = placements.stream().filter(p -> !p.getBlockId().equals(SRC)).count();
        assertEquals(true, changed > 200 && changed < 1000,
                "built-in classic defaults should weather a sane fraction, got " + changed);
    }
}
