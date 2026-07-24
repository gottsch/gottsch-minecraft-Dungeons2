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

import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;

/**
 * The dungeon's surface-to-floor-0 transition.
 *
 * <p>Rendered as a single {@code TemplateStructurePiece} in the Forge shell.
 * The piece spans from {@link #surfaceY} down to {@link #floor0Y} and reserves
 * the {@link RoomRole#START} slot of floor 0 &mdash; the maze planner routes
 * floor 0's mazes around the footprint stored here.</p>
 *
 * <p>{@code templateId} is a namespaced resource location string
 * (e.g. {@code "dungeons2:entrances/classic_staircase"}) resolved at render
 * time. Internal layout (staircase, hatch, ruined tower, etc.) is entirely up
 * to the {@code .nbt} template.</p>
 *
 * <p>{@code footprint} is in floor-local grid coords on floor 0's grid.
 * {@code rotation} is in 90&deg; increments (0/90/180/270).</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class EntranceData {
    private String templateId;
    private int surfaceY;
    private int floor0Y;
    private Rectangle2D footprint;
    private int rotation;

    public EntranceData() {}

    public EntranceData(String templateId, int surfaceY, int floor0Y, Rectangle2D footprint, int rotation) {
        this.templateId = templateId;
        this.surfaceY = surfaceY;
        this.floor0Y = floor0Y;
        this.footprint = footprint;
        this.rotation = rotation;
    }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public int getSurfaceY() { return surfaceY; }
    public void setSurfaceY(int surfaceY) { this.surfaceY = surfaceY; }

    public int getFloor0Y() { return floor0Y; }
    public void setFloor0Y(int floor0Y) { this.floor0Y = floor0Y; }

    public Rectangle2D getFootprint() { return footprint; }
    public void setFootprint(Rectangle2D footprint) { this.footprint = footprint; }

    public int getRotation() { return rotation; }
    public void setRotation(int rotation) { this.rotation = rotation; }

    @Override
    public String toString() {
        return "EntranceData{template=" + templateId +
                ", Y=" + surfaceY + "->" + floor0Y +
                ", footprint=" + footprint +
                ", rot=" + rotation +
                '}';
    }
}
