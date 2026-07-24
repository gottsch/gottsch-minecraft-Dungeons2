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

import mod.gottsch.forge.gottschcore.spatial.ICoords;

import java.util.ArrayList;
import java.util.List;

/**
 * The top-level output of the planning phase. Captures the entire multi-floor
 * dungeon as a tree of pure-data nodes ready for the Forge shell to wrap in
 * {@code StructurePiece}s.
 *
 * <p>The shape is:</p>
 * <pre>
 *   DungeonLayout
 *     ├─ entrance:    EntranceData                  (surface &rarr; floor 0)
 *     ├─ floors:      List&lt;FloorLayout&gt;        (top-down ordered, floor 0 first)
 *     │    └─ rooms / corridors / doors per floor
 *     └─ transitions: List&lt;TransitionData&gt;     (one per inter-floor link)
 * </pre>
 *
 * <p>{@code anchor} is the world-space XZ origin of the dungeon (the entrance's
 * XZ center / origin). Y is taken from {@link EntranceData#getSurfaceY()}.</p>
 *
 * <p>{@code bboxMin}/{@code bboxMax} form a 3D AABB of the entire dungeon &mdash;
 * useful for collision checks against other structures and for the structure
 * placement system. Computed during planning.</p>
 *
 * <p>Pure POJO &mdash; GottschCore {@link ICoords} only. No Minecraft imports.</p>
 *
 * @author Mark Gottschling on May 25, 2026
 */
public class DungeonLayout {
    private String motifValue;
    private DungeonSize size;
    private ICoords anchor;
    private ICoords bboxMin;
    private ICoords bboxMax;
    private EntranceData entrance;
    private List<FloorLayout> floors = new ArrayList<>();
    private List<TransitionData> transitions = new ArrayList<>();
    private long seed;

    public DungeonLayout() {}

    public String getMotifValue() { return motifValue; }
    public void setMotifValue(String motifValue) { this.motifValue = motifValue; }

    public DungeonSize getSize() { return size; }
    public void setSize(DungeonSize size) { this.size = size; }

    public ICoords getAnchor() { return anchor; }
    public void setAnchor(ICoords anchor) { this.anchor = anchor; }

    public ICoords getBboxMin() { return bboxMin; }
    public void setBboxMin(ICoords bboxMin) { this.bboxMin = bboxMin; }

    public ICoords getBboxMax() { return bboxMax; }
    public void setBboxMax(ICoords bboxMax) { this.bboxMax = bboxMax; }

    public EntranceData getEntrance() { return entrance; }
    public void setEntrance(EntranceData entrance) { this.entrance = entrance; }

    public List<FloorLayout> getFloors() {
        if (floors == null) floors = new ArrayList<>();
        return floors;
    }
    public void setFloors(List<FloorLayout> floors) { this.floors = floors; }

    public List<TransitionData> getTransitions() {
        if (transitions == null) transitions = new ArrayList<>();
        return transitions;
    }
    public void setTransitions(List<TransitionData> transitions) { this.transitions = transitions; }

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    /** Verbose multi-line description, useful for the Phase 1 determinism test deliverable. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("DungeonLayout{motif=").append(motifValue)
                .append(", size=").append(size)
                .append(", seed=").append(seed)
                .append(", anchor=").append(anchor)
                .append(", bbox=").append(bboxMin).append("..").append(bboxMax)
                .append("}\n");
        sb.append("  entrance: ").append(entrance).append('\n');
        for (FloorLayout floor : getFloors()) {
            sb.append("  ").append(floor).append('\n');
            for (RoomData room : floor.getRooms()) {
                sb.append("    ").append(room).append('\n');
            }
            for (CorridorData corridor : floor.getCorridors()) {
                sb.append("    ").append(corridor).append('\n');
            }
            for (DoorData door : floor.getDoors()) {
                sb.append("    ").append(door).append('\n');
            }
        }
        for (TransitionData transition : getTransitions()) {
            sb.append("  ").append(transition).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "DungeonLayout{motif=" + motifValue +
                ", size=" + size +
                ", seed=" + seed +
                ", floors=" + getFloors().size() +
                ", transitions=" + getTransitions().size() +
                ", entrance=" + (entrance != null ? entrance.getTemplateId() : "null") +
                '}';
    }
}
