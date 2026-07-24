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
package mod.gottsch.forge.dungeons2.core.decorator.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase A round-trip tests for the datapack-JSON block-provider / substitution
 * Codec models. Pure POJO &mdash; block ids stay as {@link ResourceLocation} so no
 * Minecraft bootstrap is required.
 *
 * @author Mark Gottschling on Jul 20, 2026
 */
class BlockProviderCodecTest {

    private static ResourceLocation mc(String path) {
        return new ResourceLocation("minecraft", path);
    }

    @Test
    void blockSetDefinitionRoundTrips() {
        BlockSetDefinition def = new BlockSetDefinition("default", Map.of(
                "wall", mc("stone_bricks"),
                "corner", mc("polished_andesite")));

        JsonElement json = BlockSetDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def).result().orElseThrow();
        BlockSetDefinition back = BlockSetDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(def, back);
    }

    @Test
    void blockProviderDefinitionRoundTripsWithMultipleBlockSets() {
        BlockProviderDefinition def = new BlockProviderDefinition(Map.of(
                "wall_pattern", List.of(
                        new BlockSetDefinition("default", Map.of("wall", mc("stone_bricks")))),
                "floor_border_pattern", List.of(
                        new BlockSetDefinition("default", Map.of(
                                "border", mc("polished_andesite"),
                                "center", mc("chiseled_stone_bricks"))),
                        new BlockSetDefinition("greek", Map.of(
                                "border", new ResourceLocation("dungeonblocks", "stone_greek_block"),
                                "center", mc("chiseled_stone_bricks"))))));

        JsonElement json = BlockProviderDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def).result().orElseThrow();
        BlockProviderDefinition back = BlockProviderDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(def, back);
        assertEquals(2, back.patterns().get("floor_border_pattern").size());
    }

    @Test
    void substitutionRuleRoundTripsWithOptionalFields() {
        SubstitutionRule full = new SubstitutionRule(
                mc("stone_bricks"),
                List.of(mc("mossy_stone_bricks"), mc("cracked_stone_bricks"), mc("cobblestone")),
                Optional.of(0.25),
                Optional.of(List.of(3, 2, 1)));

        JsonElement json = SubstitutionRule.CODEC.encodeStart(JsonOps.INSTANCE, full).result().orElseThrow();
        SubstitutionRule back = SubstitutionRule.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(full, back);
    }

    @Test
    void substitutionRuleOmitsAbsentOptionals() {
        SubstitutionRule minimal = new SubstitutionRule(
                mc("cobblestone"),
                List.of(mc("mossy_cobblestone"), mc("gravel")),
                Optional.empty(),
                Optional.empty());

        JsonElement json = SubstitutionRule.CODEC.encodeStart(JsonOps.INSTANCE, minimal).result().orElseThrow();
        assertFalseHas(json, "chance");
        assertFalseHas(json, "weights");

        SubstitutionRule back = SubstitutionRule.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(minimal, back);
    }

    @Test
    void substitutionDefinitionRoundTrips() {
        SubstitutionDefinition def = new SubstitutionDefinition(List.of(
                new SubstitutionRule(mc("stone_bricks"),
                        List.of(mc("mossy_stone_bricks")), Optional.empty(), Optional.empty()),
                new SubstitutionRule(mc("polished_andesite"),
                        List.of(mc("andesite")), Optional.of(0.5), Optional.empty())));

        JsonElement json = SubstitutionDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def).result().orElseThrow();
        SubstitutionDefinition back = SubstitutionDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();

        assertEquals(def, back);
    }

    private static void assertFalseHas(JsonElement json, String field) {
        assertTrue(json.isJsonObject() && !json.getAsJsonObject().has(field),
                "absent optional should not be serialized: " + field);
    }
}
