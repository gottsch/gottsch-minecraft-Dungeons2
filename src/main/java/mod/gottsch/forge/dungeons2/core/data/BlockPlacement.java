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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One block to place into the world.
 *
 * <p>Pure data &mdash; produced by Phase 2 builders, consumed by Phase 3
 * {@code StructurePiece#postProcess} via a {@code BlockStateCodec.resolve}
 * call. The {@link #blockEntityNbt} field carries spawner / chest / sign
 * metadata so block-entity placements travel through the same render plan
 * as plain blocks (no separate side-channel).</p>
 *
 * <h2>Coordinate space</h2>
 * <ul>
 *     <li>{@link #x} / {@link #z} are <strong>floor-local</strong> grid coords
 *         (same space as the parent {@code FloorLayout}'s footprint). The
 *         Forge shell adds the dungeon's world-space anchor at render time.</li>
 *     <li>{@link #y} is the <strong>absolute world Y</strong>. The builder
 *         already knows the floor's {@code floorY} and adds the local Y
 *         offset; the shell needs no further translation.</li>
 * </ul>
 *
 * <h2>Block identity</h2>
 * <p>{@link #blockId} is a namespaced resource location string
 * (e.g. {@code "minecraft:stone_bricks"}). {@link #properties} carries any
 * {@code BlockState} property values the builder explicitly set (e.g.
 * {@code "facing":"north"} for doors, {@code "half":"upper"}). Properties
 * absent from the map default to the block's {@code defaultBlockState()}.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports. Construction and equality work
 * without any class from {@code net.minecraft.*} on the classpath.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class BlockPlacement {
    private int x;
    private int y;
    private int z;
    private String blockId;
    private Map<String, String> properties = new LinkedHashMap<>();
    /** Non-null when this placement carries block-entity data (spawners, chests, signs). */
    private BlockEntityData blockEntityNbt;

    public BlockPlacement() {}

    public BlockPlacement(int x, int y, int z, String blockId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
    }

    public BlockPlacement(int x, int y, int z, String blockId, Map<String, String> properties) {
        this(x, y, z, blockId);
        if (properties != null) {
            this.properties = new LinkedHashMap<>(properties);
        }
    }

    public BlockPlacement(int x, int y, int z, String blockId,
                          Map<String, String> properties, BlockEntityData blockEntityNbt) {
        this(x, y, z, blockId, properties);
        this.blockEntityNbt = blockEntityNbt;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public String getBlockId() { return blockId; }
    public void setBlockId(String blockId) { this.blockId = blockId; }

    public Map<String, String> getProperties() {
        if (properties == null) properties = new LinkedHashMap<>();
        return properties;
    }
    public void setProperties(Map<String, String> properties) {
        this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
    }

    public BlockEntityData getBlockEntityNbt() { return blockEntityNbt; }
    public void setBlockEntityNbt(BlockEntityData blockEntityNbt) { this.blockEntityNbt = blockEntityNbt; }

    /** Convenience: read-only view of the properties map. */
    public Map<String, String> propertiesView() {
        return Collections.unmodifiableMap(getProperties());
    }

    @Override
    public String toString() {
        return "BlockPlacement{(" + x + "," + y + "," + z + ")"
                + " " + blockId
                + (properties != null && !properties.isEmpty() ? " " + properties : "")
                + (blockEntityNbt != null ? " be=" + blockEntityNbt : "")
                + '}';
    }
}
