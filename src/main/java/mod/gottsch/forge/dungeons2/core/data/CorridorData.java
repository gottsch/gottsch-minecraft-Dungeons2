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
 * Plain data describing one corridor region within a {@link FloorLayout}.
 *
 * <p>A corridor region is one connected run produced by the maze planner's
 * Prim's-algorithm carve step (one {@code prims()} invocation = one region).
 * The {@code cells} list captures every grid cell belonging to this corridor
 * in floor-local grid coordinates (X = grid X, Z = grid Y).</p>
 *
 * <p>{@code wallCells} captures every grid cell that borders this corridor and
 * needs a wall column (rock / wall / door / connector / out-of-bounds neighbors,
 * 8-connected). Phase 1 leaves it empty; {@code DungeonStackPlanner.convertLevel}
 * populates it from the maze grid so the Phase 3 corridor piece can render walls
 * <em>without</em> the transient {@link FloorLayout#getGrid() grid} (which is
 * null after NBT deserialization). This resolves the grid round-trip TODO noted
 * on {@code FloorLayout.grid}.</p>
 *
 * <p>{@code templateId} is reserved for the future Phase 8 mixed-mode templated
 * corridor segments; null in v1.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class CorridorData {
    private int id;
    private List<Coords2D> cells = new ArrayList<>();
    /**
     * Grid cells bordering this corridor that need a wall column. Populated at
     * conversion time so corridor rendering no longer depends on the transient
     * maze grid. May contain negative / out-of-bounds coords (the original
     * builder emits walls for out-of-bounds neighbors too).
     */
    private List<Coords2D> wallCells = new ArrayList<>();
    /** Phase 8 hook: non-null when this corridor is rendered from a template prefab. */
    private String templateId;

    public CorridorData() {}

    public CorridorData(int id) {
        this.id = id;
    }

    public CorridorData(int id, List<Coords2D> cells) {
        this.id = id;
        this.cells = cells;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public List<Coords2D> getCells() {
        if (cells == null) cells = new ArrayList<>();
        return cells;
    }
    public void setCells(List<Coords2D> cells) { this.cells = cells; }

    public List<Coords2D> getWallCells() {
        if (wallCells == null) wallCells = new ArrayList<>();
        return wallCells;
    }
    public void setWallCells(List<Coords2D> wallCells) { this.wallCells = wallCells; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    @Override
    public String toString() {
        return "CorridorData{id=" + id +
                ", cells=" + getCells().size() +
                (templateId != null ? ", template=" + templateId : "") +
                '}';
    }
}
