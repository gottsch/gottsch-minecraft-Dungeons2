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
 * One floor-to-floor link in the dungeon stack.
 *
 * <p>Rendered as a single 2-story {@code TemplateStructurePiece} in the Forge
 * shell. The piece <em>is</em> floor {@code upperFloorIndex}'s
 * {@link RoomRole#END} room AND floor {@code lowerFloorIndex}'s
 * {@link RoomRole#START} room &mdash; one piece, two floors tall, single
 * template. Internal descent layout (ladder, stairs, spiral, drop shaft) is
 * defined entirely by the {@code .nbt} template.</p>
 *
 * <p>{@code footprint} is the shared XZ rectangle &mdash; identical on both
 * floors (in floor-local grid coords). {@code upperY} is the upper floor's
 * floor-Y (where the player descends from); {@code lowerY} is the lower floor's
 * floor-Y (where they land).</p>
 *
 * <p>{@code rotation} is in 90&deg; increments (0/90/180/270).</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class TransitionData {
    private String templateId;
    private int upperFloorIndex;
    private int lowerFloorIndex;
    private Rectangle2D footprint;
    private int upperY;
    private int lowerY;
    private int rotation;

    public TransitionData() {}

    public TransitionData(String templateId, int upperFloorIndex, int lowerFloorIndex,
                          Rectangle2D footprint, int upperY, int lowerY, int rotation) {
        this.templateId = templateId;
        this.upperFloorIndex = upperFloorIndex;
        this.lowerFloorIndex = lowerFloorIndex;
        this.footprint = footprint;
        this.upperY = upperY;
        this.lowerY = lowerY;
        this.rotation = rotation;
    }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public int getUpperFloorIndex() { return upperFloorIndex; }
    public void setUpperFloorIndex(int upperFloorIndex) { this.upperFloorIndex = upperFloorIndex; }

    public int getLowerFloorIndex() { return lowerFloorIndex; }
    public void setLowerFloorIndex(int lowerFloorIndex) { this.lowerFloorIndex = lowerFloorIndex; }

    public Rectangle2D getFootprint() { return footprint; }
    public void setFootprint(Rectangle2D footprint) { this.footprint = footprint; }

    public int getUpperY() { return upperY; }
    public void setUpperY(int upperY) { this.upperY = upperY; }

    public int getLowerY() { return lowerY; }
    public void setLowerY(int lowerY) { this.lowerY = lowerY; }

    public int getRotation() { return rotation; }
    public void setRotation(int rotation) { this.rotation = rotation; }

    @Override
    public String toString() {
        return "TransitionData{template=" + templateId +
                ", floors=" + upperFloorIndex + "->" + lowerFloorIndex +
                ", Y=" + upperY + "->" + lowerY +
                ", footprint=" + footprint +
                ", rot=" + rotation +
                '}';
    }
}
