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
package mod.gottsch.forge.dungeons2.core.data;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Phase 2 {@link BlockPlacement} and {@link BlockEntityData}
 * POJOs work with no Minecraft classes on the classpath. These types are
 * what every Phase 2 builder emits and what Phase 3 piece renderers consume.
 *
 * @author Mark Gottschling on May 25, 2026
 */
class BlockPlacementTest {

    @Test
    void plainBlockPlacementHasCoordsAndIdNoProperties() {
        BlockPlacement bp = new BlockPlacement(5, 64, -3, "minecraft:stone_bricks");
        assertEquals(5, bp.getX());
        assertEquals(64, bp.getY());
        assertEquals(-3, bp.getZ());
        assertEquals("minecraft:stone_bricks", bp.getBlockId());
        assertNotNull(bp.getProperties());
        assertTrue(bp.getProperties().isEmpty(), "Default props are empty");
        assertNull(bp.getBlockEntityNbt(), "Plain placement has no BE data");
    }

    @Test
    void placementWithPropertiesCopiesDefensively() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("facing", "north");
        props.put("half", "lower");
        BlockPlacement bp = new BlockPlacement(0, 0, 0, "minecraft:oak_door", props);

        assertEquals(2, bp.getProperties().size());
        assertEquals("north", bp.getProperties().get("facing"));
        assertEquals("lower", bp.getProperties().get("half"));

        // Mutating the input map MUST NOT affect the placement.
        props.put("open", "true");
        assertEquals(2, bp.getProperties().size(),
                "Input map mutation should not bleed into placement");
    }

    @Test
    void placementWithBlockEntityCarriesData() {
        BlockEntityData spawner = new BlockEntityData("minecraft:mob_spawner")
                .with("EntityType", "minecraft:zombie")
                .with("SpawnRange", "4");
        BlockPlacement bp = new BlockPlacement(
                10, 60, 10, "minecraft:spawner",
                new LinkedHashMap<>(), spawner);

        assertNotNull(bp.getBlockEntityNbt());
        assertEquals("minecraft:mob_spawner", bp.getBlockEntityNbt().getType());
        assertEquals("minecraft:zombie", bp.getBlockEntityNbt().getData().get("EntityType"));
        assertEquals("4", bp.getBlockEntityNbt().getData().get("SpawnRange"));
    }

    @Test
    void propertiesViewIsUnmodifiable() {
        BlockPlacement bp = new BlockPlacement(0, 0, 0, "minecraft:stone");
        bp.getProperties().put("test", "value");
        Map<String, String> view = bp.propertiesView();
        assertEquals(1, view.size());
        try {
            view.put("should", "fail");
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // good
        }
    }

    @Test
    void toStringDescribesPlacement() {
        BlockPlacement bp = new BlockPlacement(1, 2, 3, "minecraft:dirt");
        String s = bp.toString();
        assertTrue(s.contains("(1,2,3)"));
        assertTrue(s.contains("minecraft:dirt"));
    }

    @Test
    void blockEntityDataFluentBuilds() {
        BlockEntityData be = new BlockEntityData("minecraft:chest")
                .with("LootTable", "dungeons2:chests/classic_treasure")
                .with("LootTableSeed", "12345");
        assertEquals("minecraft:chest", be.getType());
        assertEquals(2, be.getData().size());
        assertSame(be, be.with("ExtraTag", "value"), "with() should return same instance");
        assertEquals(3, be.getData().size());
    }

    @Test
    void emptyBlockEntityDataInitsEmptyMap() {
        BlockEntityData be = new BlockEntityData();
        assertNull(be.getType());
        assertNotNull(be.getData());
        assertTrue(be.getData().isEmpty());

        be.setType("minecraft:furnace");
        assertEquals("minecraft:furnace", be.getType());
    }

    @Test
    void nullPropertiesMapStillSafe() {
        BlockPlacement bp = new BlockPlacement(0, 0, 0, "minecraft:stone");
        bp.setProperties(null);
        assertNotNull(bp.getProperties(), "Getter should lazy-init empty map");
        assertTrue(bp.getProperties().isEmpty());
        assertFalse(bp.toString().contains("null"),
                "toString should not surface a literal 'null' for missing props");
    }
}
