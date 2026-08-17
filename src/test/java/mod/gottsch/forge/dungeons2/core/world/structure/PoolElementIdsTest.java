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
package mod.gottsch.forge.dungeons2.core.world.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.diagnostic.TestRegistries;
import net.minecraft.SharedConstants;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a pool element's template id back out of it &mdash; backlog #44's prerequisite.
 *
 * <p>Nothing could cap a prefab room before this, because D2 never learned which prefab vanilla
 * handed it: {@code AssembledRoom} carried a footprint and the layout recorded a single constant
 * for every assembled room. The identity has to come from the element, and the element does not
 * expose it.</p>
 *
 * <p><strong>What this actually guards</strong> is the choice of mechanism. The obvious two are
 * both wrong in ways a green test suite would not reveal: reflection on {@code template} works in
 * dev and fails against a reobfuscated jar, and scraping {@code toString()} depends on a format
 * that is not API. Going through the element's own codec means reading the very {@code "location"}
 * string the pack author wrote, so this test decodes a pool element in exactly the shipped JSON
 * form and asserts the same string comes back.</p>
 */
class PoolElementIdsTest {

    private static final String SHIPPED_ELEMENT = """
            {
              "element_type": "minecraft:single_pool_element",
              "location": "dungeons2:rooms/classic/5x5/5x5_junction_1",
              "processors": "dungeons2:classic_weathering",
              "projection": "rigid"
            }""";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Decoded through {@link TestRegistries}' {@code RegistryOps}, not bare {@code JsonOps}: the
     * shipped form names a {@code processors} list, and that field is a {@code Holder} the codec can
     * only resolve against a populated processor-list registry. Decoding a cut-down element without
     * it would have tested a JSON shape no pack actually uses.
     */
    private static StructurePoolElement decode(String json) {
        JsonElement parsed = JsonParser.parseString(json);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, TestRegistries.get());
        return StructurePoolElement.CODEC.parse(ops, parsed)
                .result()
                .orElseThrow(() -> new AssertionError("the shipped pool-element form no longer"
                        + " decodes -- the pools themselves would be broken too: " + json));
    }

    @Test
    void aSinglePoolElementReportsTheLocationThePackDeclared() {
        assertEquals(Optional.of("dungeons2:rooms/classic/5x5/5x5_junction_1"),
                PoolElementIds.locationOf(decode(SHIPPED_ELEMENT)));
    }

    /**
     * The round trip is the whole point: whatever an author writes under {@code "location"} is
     * exactly what comes back, so a cap keyed on this string is keyed on the pool JSON itself.
     */
    @Test
    void anyLocationRoundTripsVerbatim() {
        for (String location : List.of("dungeons2:rooms/classic/11x11/11x11_hall_1",
                "minecraft:village/plains/houses/plains_small_house_1",
                "someaddon:rooms/x")) {
            String json = SHIPPED_ELEMENT.replace(
                    "dungeons2:rooms/classic/5x5/5x5_junction_1", location);
            assertEquals(Optional.of(location), PoolElementIds.locationOf(decode(json)),
                    "location did not survive the round trip: " + location);
        }
    }

    /**
     * An element with no template is empty rather than an error. A caller must read that as
     * "unlimited", not as a failure -- an uncapped room is a far smaller problem than a dungeon
     * that refuses to generate.
     */
    @Test
    void anElementWithNoTemplateIsEmptyNotAnError() {
        StructurePoolElement empty = decode("""
                { "element_type": "minecraft:empty_pool_element" }""");
        assertTrue(PoolElementIds.locationOf(empty).isEmpty());
    }

    /** No pieces, no ids -- and no exception, which is what the assembler's empty path relies on. */
    @Test
    void anEmptyPieceListYieldsNoIds() {
        assertEquals(List.of(), PoolElementIds.locationsOf(List.of()));
    }
}
