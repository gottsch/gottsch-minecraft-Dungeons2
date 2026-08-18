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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Block-entity metadata attached to a {@link BlockPlacement}.
 *
 * <p>Carries spawner, chest, sign, loot-table, etc. data alongside the block
 * placement so the Phase 3 renderer applies it uniformly through one code
 * path. {@link #data} keys are NBT field names; values are stringified for
 * loader-portability (the renderer parses back to the right NBT type).</p>
 *
 * <h2>Examples</h2>
 * <ul>
 *     <li><strong>Spawner:</strong> {@code type="minecraft:mob_spawner",
 *         data={"EntityType":"minecraft:zombie", "SpawnRange":"4",
 *         "MaxNearbyEntities":"6"}}</li>
 *     <li><strong>Chest:</strong> {@code type="minecraft:chest",
 *         data={"LootTable":"dungeons2:chests/classic_treasure",
 *         "LootTableSeed":"12345"}}</li>
 * </ul>
 *
 * <h2>{@link #nbtValues}: the flat map cannot express everything</h2>
 * <p>{@link #data} is {@code String -> String} and the renderer sniffs each value back to an int,
 * long, double or string. That covers every field the proximity spawner and a chest need, and it
 * cannot express a <strong>nested compound</strong> at all &mdash; which a vanilla
 * {@code minecraft:spawner} requires, since its mob lives at
 * {@code SpawnData.entity.id} and its weighted alternatives at {@code SpawnPotentials}.</p>
 *
 * <p>So nested values travel as <strong>SNBT text in their own map</strong>, parsed by the renderer
 * with {@code TagParser}. A separate map rather than SNBT smuggled into {@link #data}: that would
 * mean the renderer guessing whether {@code "{id:1}"} is a compound or a string it should store
 * verbatim, and value-sniffing in this exact code path has already produced one real bug &mdash;
 * {@code proximity} was stored as a string and read back as {@code 0}. The author says which kind
 * of value it is by choosing the map; nothing infers it.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports. That is the whole reason these are strings rather than
 * {@code CompoundTag}s: this class sits in the data layer, which stays loader-agnostic, and the
 * parse happens in {@code DungeonPiece} where Minecraft is already in scope.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class BlockEntityData {
    /** Block-entity type as a resource location string (e.g. {@code "minecraft:mob_spawner"}). */
    private String type;
    private Map<String, String> data = new LinkedHashMap<>();
    /** NBT key &rarr; SNBT text, for values that are compounds or lists. See the class doc. */
    private Map<String, String> nbtValues = new LinkedHashMap<>();

    public BlockEntityData() {}

    public BlockEntityData(String type) {
        this.type = type;
    }

    public BlockEntityData(String type, Map<String, String> data) {
        this.type = type;
        if (data != null) {
            this.data = new LinkedHashMap<>(data);
        }
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, String> getData() {
        if (data == null) data = new LinkedHashMap<>();
        return data;
    }
    public void setData(Map<String, String> data) {
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }

    public Map<String, String> getNbtValues() {
        if (nbtValues == null) nbtValues = new LinkedHashMap<>();
        return nbtValues;
    }
    public void setNbtValues(Map<String, String> nbtValues) {
        this.nbtValues = nbtValues == null ? new LinkedHashMap<>() : new LinkedHashMap<>(nbtValues);
    }

    /** Fluent helper: add one NBT key/value. */
    public BlockEntityData with(String key, String value) {
        getData().put(key, value);
        return this;
    }

    /**
     * Fluent helper: add one NBT key whose value is a compound or list, given as SNBT text.
     *
     * <p>Unparsed here on purpose &mdash; this class has no Minecraft on its classpath. A malformed
     * string therefore fails at render time, where it is logged against the position it was going
     * to be written to; see {@code DungeonPiece#applyBlockEntity}.</p>
     */
    public BlockEntityData withNbt(String key, String snbt) {
        getNbtValues().put(key, snbt);
        return this;
    }

    @Override
    public String toString() {
        return "BlockEntityData{type=" + type
                + (data != null && !data.isEmpty() ? " " + data : "")
                + (nbtValues != null && !nbtValues.isEmpty() ? " " + nbtValues : "") + '}';
    }
}
