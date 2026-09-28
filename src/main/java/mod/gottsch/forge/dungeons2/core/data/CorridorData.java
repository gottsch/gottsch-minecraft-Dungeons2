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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * <p>{@code doorCells} is the subset of those bordering cells whose grid type is
 * {@code DOOR}. They are held separately from {@code wallCells} (and are NOT
 * repeated in it) because they render as a wall column pierced at the two
 * door-half levels rather than a solid one &mdash; see
 * {@code BasicCorridorGenerator}.</p>
 *
 * <p>{@code wallHeight} is the corridor's wall height in blocks, resolved from the
 * motif's {@code CorridorConfig} at plan time and carried here for the same reason
 * {@code wallCells} is: the piece needs it at <em>construction</em> time to size its
 * bounding box, and it cannot reach the datapack registry until {@code postProcess}.
 * Riding on the data means it travels through NBT for free and the piece stays
 * self-describing after deserialization.</p>
 *
 * <p>{@code templateId} is reserved for the future Phase 8 mixed-mode templated
 * corridor segments; null in v1.</p>
 *
 * <p>Pure POJO &mdash; no Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class CorridorData {
    /**
     * Fallback wall height for a corridor nobody injected one into (tests, and any
     * pre-height save deserialized by {@code PieceNbt}). Deliberately a plain literal
     * rather than a reference to {@code CorridorConfig.DEFAULT_HEIGHT}, which is the
     * canonical value but lives on the Minecraft-importing config side of the fence;
     * this class is a pure POJO. Keep the two in step.
     */
    public static final int DEFAULT_WALL_HEIGHT = 5;

    /**
     * The style name meaning "this motif's baseline geometry", for a corridor nobody rolled a style
     * for (tests, and any pre-styles save). Same literal as {@code CorridorStyle.BASELINE}, and
     * duplicated for the same reason {@link #DEFAULT_WALL_HEIGHT} is: that constant lives on the
     * Minecraft-importing config side of the fence and this class is a pure POJO. Keep the two in
     * step &mdash; the style codec rejects a blank name, so no authored style can collide with it.
     */
    public static final String BASELINE_STYLE = "";

    private int id;
    private List<Coords2D> cells = new ArrayList<>();
    /**
     * Grid cells bordering this corridor that need a wall column. Populated at
     * conversion time so corridor rendering no longer depends on the transient
     * maze grid. May contain negative / out-of-bounds coords (the original
     * builder emits walls for out-of-bounds neighbors too).
     */
    private List<Coords2D> wallCells = new ArrayList<>();
    /**
     * Bordering grid cells of type DOOR. Disjoint from {@link #wallCells}; these
     * render as a wall column with the two door-half levels left as air so the
     * decoration pass never anchors growth facing a door cell.
     */
    private List<Coords2D> doorCells = new ArrayList<>();
    /**
     * Wall height in blocks: the column runs {@code floorY .. floorY + wallHeight - 1}.
     * Injected by {@code DungeonStackPlanner} from the motif's {@code CorridorConfig}.
     */
    private int wallHeight = DEFAULT_WALL_HEIGHT;
    /**
     * The name of the {@code CorridorStyle} this corridor was built to, rolled once per floor by
     * {@code DungeonStackPlanner} so every corridor on a floor matches. Only the <em>name</em>
     * travels: the profile and blocks are re-resolved from the datapack at render time, whereas
     * {@link #wallHeight} cannot be, because it sizes the piece's bounding box at construction.
     * That makes {@code wallHeight} authoritative and this a decoration key &mdash; if a datapack
     * edit makes the two disagree, the height wins and the shape of the excavation is unchanged.
     */
    private String styleName = BASELINE_STYLE;
    /**
     * #108: how far each cell's walking plane sits BELOW the floor's, parallel to {@link #cells}.
     * Empty means every cell is at depth 0, which is every corridor that does not sink and every save
     * from before it could. Decided by the planner, for the reason {@link #wallHeight} is: it sizes
     * the piece's box downward at construction.
     */
    private int[] cellDepths = new int[0];
    /** Lazily built from {@link #cellDepths}; transient. */
    private Map<Coords2D, Integer> depthIndex;
    /**
     * #108 stage 3: the trench cells of this corridor's trap, empty for none. Planned, for the
     * {@link #cellDepths} reason: the trench reaches below the floor and the box has to cover it.
     */
    private List<Coords2D> trapCells = new ArrayList<>();
    /** How far below the WALKING PLANE (not the local floor) the trench's floor sits. */
    private int trapFloorDepth;
    /** {@code CorridorTrap.HAZARD} or {@code FALSE_FLOOR}; the kind survives a datapack edit. */
    private String trapKind = "";
    /** Which of the style's trap options was drawn, for its materials at render; -1 for none. */
    private int trapOption = -1;
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
    public void setCells(List<Coords2D> cells) {
        this.cells = cells;
        this.depthIndex = null;
    }

    public List<Coords2D> getWallCells() {
        if (wallCells == null) wallCells = new ArrayList<>();
        return wallCells;
    }
    public void setWallCells(List<Coords2D> wallCells) { this.wallCells = wallCells; }

    public List<Coords2D> getDoorCells() {
        if (doorCells == null) doorCells = new ArrayList<>();
        return doorCells;
    }
    public void setDoorCells(List<Coords2D> doorCells) { this.doorCells = doorCells; }

    public int getWallHeight() { return wallHeight; }
    public void setWallHeight(int wallHeight) { this.wallHeight = wallHeight; }

    public String getStyleName() { return styleName == null ? BASELINE_STYLE : styleName; }
    public void setStyleName(String styleName) { this.styleName = styleName; }

    /** Parallel to {@link #getCells()}; empty when nothing sinks. */
    public int[] getCellDepths() {
        return cellDepths == null ? new int[0] : cellDepths;
    }

    public void setCellDepths(int[] cellDepths) {
        this.cellDepths = cellDepths == null ? new int[0] : cellDepths;
        this.depthIndex = null;
    }

    /** True when any cell sits below the walking plane. */
    public boolean isSunk() {
        return maxDepth() > 0;
    }

    /** The deepest cell, or 0. What the piece's box reaches down by. */
    public int maxDepth() {
        int max = 0;
        for (int d : getCellDepths()) {
            max = Math.max(max, d);
        }
        return max;
    }

    /**
     * A cell's depth below the walking plane; 0 for a cell that does not sink and for any cell that
     * is not this corridor's (a wall, a door, a neighbour).
     */
    public int depthAt(Coords2D cell) {
        int[] depths = getCellDepths();
        if (depths.length == 0) {
            return 0;
        }
        if (depthIndex == null) {
            Map<Coords2D, Integer> index = new HashMap<>();
            List<Coords2D> all = getCells();
            for (int i = 0; i < all.size() && i < depths.length; i++) {
                index.put(all.get(i), depths[i]);
            }
            depthIndex = index;
        }
        return depthIndex.getOrDefault(cell, 0);
    }

    public List<Coords2D> getTrapCells() {
        if (trapCells == null) trapCells = new ArrayList<>();
        return trapCells;
    }

    public int getTrapFloorDepth() { return trapFloorDepth; }
    public String getTrapKind() { return trapKind == null ? "" : trapKind; }
    public int getTrapOption() { return trapOption; }

    public boolean hasTrap() {
        return !getTrapCells().isEmpty();
    }

    public void setTrap(List<Coords2D> cells, int floorDepth, String kind, int option) {
        this.trapCells = cells == null ? new ArrayList<>() : new ArrayList<>(cells);
        this.trapFloorDepth = floorDepth;
        this.trapKind = kind;
        this.trapOption = option;
    }

    /** How far below the walking plane this piece reaches: its deepest cell, or its trench. */
    public int reachBelow() {
        return Math.max(maxDepth(), hasTrap() ? trapFloorDepth : 0);
    }

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
