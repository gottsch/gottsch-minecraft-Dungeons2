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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data describing a single room within a {@link FloorLayout}.
 *
 * <p>Coordinates ({@code originX} / {@code originZ}) are <strong>floor-local</strong>
 * grid coordinates &mdash; not world coordinates. Translation to world space happens
 * later (in the Forge shell) by combining the room's origin with the parent
 * floor's anchor and floor-Y.</p>
 *
 * <p>{@code width} (X) and {@code depth} (Z) come from the maze planner's
 * 2D grid. {@code height} (Y) is decided by the stack planner at conversion time
 * (currently 5&ndash;10 blocks, mirroring the original {@code DungeonGenerator}
 * behavior).</p>
 *
 * <p>{@code role} marks rooms reserved for template attachment ({@link RoomRole#START}
 * for the upstairs anchor, {@link RoomRole#END} for the downstairs anchor). The
 * piece emitter in the Forge shell skips rooms whose role isn't {@code NORMAL}
 * so the template piece covers them without overlap.</p>
 *
 * <p>{@code templateId} is reserved for the future Phase 8 mixed-mode templated
 * rooms; null in v1.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports. GottschCore-free at this level;
 * world-space conversion happens in the Forge shell.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class RoomData {
    private int id;
    private int originX;
    private int originZ;
    private int width;
    private int depth;
    private int height;
    private RoomRole role = RoomRole.NORMAL;
    private List<Coords2D> doorways = new ArrayList<>();
    /** Phase 8 hook: non-null when this room is rendered from a template prefab. */
    private String templateId;

    public RoomData() {}

    public RoomData(int id, int originX, int originZ, int width, int depth, int height, RoomRole role) {
        this.id = id;
        this.originX = originX;
        this.originZ = originZ;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.role = role;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOriginX() { return originX; }
    public void setOriginX(int originX) { this.originX = originX; }

    public int getOriginZ() { return originZ; }
    public void setOriginZ(int originZ) { this.originZ = originZ; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public RoomRole getRole() { return role; }
    public void setRole(RoomRole role) { this.role = role; }

    public List<Coords2D> getDoorways() {
        if (doorways == null) doorways = new ArrayList<>();
        return doorways;
    }
    public void setDoorways(List<Coords2D> doorways) { this.doorways = doorways; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    @Override
    public String toString() {
        return "RoomData{id=" + id +
                ", origin=(" + originX + "," + originZ + ")" +
                ", size=" + width + "x" + depth + "x" + height +
                ", role=" + role +
                (templateId != null ? ", template=" + templateId : "") +
                ", doorways=" + getDoorways().size() +
                '}';
    }
}
