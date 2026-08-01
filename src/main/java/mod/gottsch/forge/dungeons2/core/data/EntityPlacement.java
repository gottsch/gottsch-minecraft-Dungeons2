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
package mod.gottsch.forge.dungeons2.core.data;

/**
 * One entity to spawn into the world &mdash; the entity-side counterpart of
 * {@link BlockPlacement}, and the first thing this pipeline emits that is not a block.
 *
 * <h2>Coordinate space</h2>
 * <p>Identical to {@link BlockPlacement}: {@link #x}/{@link #z} are <strong>floor-local</strong>
 * grid coords (the Forge shell adds the dungeon's world anchor at render time) and {@link #y} is
 * <strong>absolute world Y</strong>. {@link #xOffset}/{@link #zOffset} then position the entity
 * within that cell &mdash; 0.5/0.5 is the centre, which is what you want for anything standing on a
 * floor block. Y needs no offset: an entity's position is its feet, so {@code floorY + 1} rests
 * exactly on the floor block at {@code floorY}.</p>
 *
 * <h2>Why entities need care that blocks do not</h2>
 * <p><strong>Spawning is not idempotent.</strong> Every other placement in this pipeline is: a
 * piece's {@code postProcess} runs once per overlapping chunk and writing the same block twice is a
 * no-op, which is why nothing else has to think about it. Adding the same entity twice produces two
 * entities, so a room straddling four chunks would get four stacked pots. The consumer
 * ({@code DungeonPiece#placeEntities}) must therefore clip to the chunk box, exactly as
 * {@code placeAll} already does for block-entity placements.</p>
 *
 * <h2>Loot</h2>
 * <p>{@link #lootTable} and {@link #lootTableSeed} are written as the vanilla-standard {@code
 * LootTable} / {@code LootTableSeed} NBT keys, the same pair chests and mob death loot use. A seed
 * of {@code 0} means "roll fresh when the entity is broken"; any other value fixes the contents at
 * generation time, which is the behaviour worldgen wants. These two are spelled out as fields
 * rather than a general NBT map because they are the only entity NBT anything here needs &mdash;
 * generalize when there is a second use, the same reasoning {@code FloorPatternEntry} applies to
 * its {@code type} discriminator.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports, matching {@link BlockPlacement}. Construction and
 * equality work without any class from {@code net.minecraft.*} on the classpath, which is what lets
 * the placement planners be unit tested without a running Forge instance.</p>
 *
 * @author Mark Gottschling on Jul 31, 2026
 */
public class EntityPlacement {

    /** Cell centre &mdash; the sane default for anything resting on a floor block. */
    public static final double CENTRE = 0.5D;

    private int x;
    private int y;
    private int z;
    private String entityId;
    private double xOffset = CENTRE;
    private double zOffset = CENTRE;
    private float yRot;
    /** Null when this entity has no loot table (it then drops nothing). */
    private String lootTable;
    /** 0 means "roll fresh at break time"; anything else fixes the loot at generation. */
    private long lootTableSeed;

    public EntityPlacement() {}

    public EntityPlacement(int x, int y, int z, String entityId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.entityId = entityId;
    }

    public EntityPlacement(int x, int y, int z, String entityId, float yRot,
                           String lootTable, long lootTableSeed) {
        this(x, y, z, entityId);
        this.yRot = yRot;
        this.lootTable = lootTable;
        this.lootTableSeed = lootTableSeed;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public double getXOffset() { return xOffset; }
    public void setXOffset(double xOffset) { this.xOffset = xOffset; }

    public double getZOffset() { return zOffset; }
    public void setZOffset(double zOffset) { this.zOffset = zOffset; }

    public float getYRot() { return yRot; }
    public void setYRot(float yRot) { this.yRot = yRot; }

    public String getLootTable() { return lootTable; }
    public void setLootTable(String lootTable) { this.lootTable = lootTable; }

    public long getLootTableSeed() { return lootTableSeed; }
    public void setLootTableSeed(long lootTableSeed) { this.lootTableSeed = lootTableSeed; }

    @Override
    public String toString() {
        return "EntityPlacement{" + entityId + " @ (" + x + "," + y + "," + z + ")"
                + (lootTable == null ? "" : " loot=" + lootTable) + "}";
    }
}
