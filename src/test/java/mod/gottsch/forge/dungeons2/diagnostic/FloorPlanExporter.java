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
package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Cell;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Grid2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonCorridorPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonDoorPiece;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonPieceEmitter;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonRoomPiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns a planned {@link DungeonLayout} into the JSON model the floor-plan viewer reads.
 *
 * <h2>Why it renders through the pieces</h2>
 * <p>It does <strong>not</strong> call {@code DungeonLayoutRenderer}. It emits the real
 * {@link StructurePiece}s via {@link DungeonPieceEmitter} and asks each one for its own placements,
 * which buys three things the shared-stream renderer cannot give:</p>
 * <ul>
 *     <li><strong>Attribution.</strong> Every block knows which piece wrote it, so a cell written by
 *         two pieces is visible as such. That is the whole point of the tool.</li>
 *     <li><strong>Fidelity.</strong> Each piece seeds itself from {@code DungeonPiece#deterministicRandom}
 *         (anchor XZ + floor Y + piece id), exactly as it does in game, so a room here rolls the same
 *         scheme it rolls in the world. A shared {@code RandomSource} would roll something else.</li>
 *     <li><strong>Order.</strong> Pieces are written in emit order, which is the order
 *         {@code postProcess} runs them, so "last writer wins" resolves the same way it does in the
 *         world.</li>
 * </ul>
 *
 * <h2>The contested-cell model</h2>
 * <p>Room boxes intersect by one cell &mdash; the deliberate shared-wall design &mdash; so a wall
 * between two rooms is one column written by both. Every write to a cell is recorded, not just the
 * winner, and a cell with writes from more than one piece is flagged. The viewer draws those in
 * alarm colours and lists the losing blocks on hover.</p>
 */
public final class FloorPlanExporter {

    /** One piece that wrote blocks: a room, a corridor, or a door. */
    private record Owner(String kind, int id, String label, String detail) {}

    /** Every write that landed on one (x,y,z), in the order they landed. */
    private static final class CellWrites {
        final List<int[]> writes = new ArrayList<>(1); // {ownerIndex, paletteIndex}

        int winnerOwner() { return writes.get(writes.size() - 1)[0]; }
        int winnerBlock() { return writes.get(writes.size() - 1)[1]; }

        boolean contested() {
            int first = writes.get(0)[0];
            for (int[] w : writes) {
                if (w[0] != first) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The order pieces are written in. {@link #EMIT} is what the game does; the others exist so the
     * consequences of changing {@code DungeonPieceEmitter}'s order can be measured before anything
     * ships. Order decides who wins a shared cell, and nothing else.
     */
    public enum PieceOrder {
        /**
         * Whatever {@link DungeonPieceEmitter} does &mdash; corridors, then rooms, then doors, so a
         * room wins its own perimeter. This is production.
         */
        EMIT,
        /**
         * Rooms, then corridors, then doors: the order production used before 2026-08-03, where the
         * corridor overwrote the room's wall with the motif's plain block. Kept so the tool can
         * still measure the counterfactual &mdash; without it there is nothing to compare against
         * and the audit's numbers lose their meaning.
         */
        ROOMS_FIRST;

        int rank(StructurePiece piece) {
            if (piece instanceof DungeonDoorPiece) {
                return 2;
            }
            boolean corridor = piece instanceof DungeonCorridorPiece;
            // EMIT keeps the emitter's own order; ROOMS_FIRST puts rooms ahead of corridors.
            return corridor ? 1 : 0;
        }
    }

    private final DungeonLayout layout;
    private final MotifConfig motifConfig;
    private PieceOrder order = PieceOrder.EMIT;

    /** Block id -> palette index, insertion ordered so the palette array can just be the key set. */
    private final Map<String, Integer> palette = new LinkedHashMap<>();

    // Everything below is keyed by floorY: a piece knows its Y but not its floor index, and floorY
    // is unique per floor, so it is the join key between the two halves of the model.
    private final Map<Integer, List<Owner>> owners = new HashMap<>();
    private final Map<Integer, Map<Long, CellWrites>> cells = new HashMap<>();
    private final Map<Integer, List<EntityPlacement>> entities = new HashMap<>();
    private final Map<Integer, String> roomSchemes = new HashMap<>();
    private boolean built;

    public FloorPlanExporter(DungeonLayout layout, MotifConfig motifConfig) {
        this.layout = layout;
        this.motifConfig = motifConfig;
    }

    public FloorPlanExporter withOrder(PieceOrder order) {
        this.order = order;
        return this;
    }

    /** Renders every piece once, recording every write. Idempotent. */
    private void build() {
        if (built) {
            return;
        }
        built = true;
        for (FloorLayout floor : layout.getFloors()) {
            owners.put(floor.getFloorY(), new ArrayList<>());
            cells.put(floor.getFloorY(), new LinkedHashMap<>());
            entities.put(floor.getFloorY(), new ArrayList<>());
        }

        int anchorX = layout.getAnchor().getX();
        int anchorZ = layout.getAnchor().getZ();

        for (StructurePiece piece : ordered(DungeonPieceEmitter.emit(layout, anchorX, anchorZ))) {
            int floorY;
            Owner owner;
            List<BlockPlacement> blocks;

            if (piece instanceof DungeonRoomPiece room) {
                RoomData data = room.getRoom();
                floorY = floorYOf(piece);
                String scheme = room.rolledScheme(motifConfig).name();
                roomSchemes.put(data.getId(), scheme);
                owner = new Owner("room", data.getId(), "room " + data.getId(),
                        data.getWidth() + "x" + data.getDepth() + "x" + data.getHeight()
                                + " · " + scheme);
                RoomPlacements placements = room.renderRoom(motifConfig);
                blocks = placements.getBlocks();
                List<EntityPlacement> floorEntities = entities.get(floorY);
                if (floorEntities != null) {
                    floorEntities.addAll(placements.getEntities());
                }
            } else if (piece instanceof DungeonCorridorPiece corridor) {
                floorY = floorYOf(piece);
                CorridorData data = corridor.getCorridor();
                owner = new Owner("corridor", data.getId(), "corridor " + data.getId(),
                        data.getCells().size() + " cells");
                blocks = corridor.renderPlacements(motifConfig);
            } else if (piece instanceof DungeonDoorPiece doorPiece) {
                floorY = floorYOf(piece);
                DoorData data = doorPiece.getDoor();
                owner = new Owner("door", data.getRegionA(),
                        "door (" + data.getX() + "," + data.getZ() + ")",
                        data.getRegionA() + " ↔ " + data.getRegionB() + " · "
                                + data.getFacing());
                blocks = doorPiece.renderPlacements(motifConfig);
            } else {
                continue;
            }

            List<Owner> floorOwners = owners.get(floorY);
            Map<Long, CellWrites> floorCells = cells.get(floorY);
            if (floorOwners == null || floorCells == null) {
                continue; // a piece on a Y no floor claims; nothing sensible to draw it on
            }
            int ownerIndex = floorOwners.size();
            floorOwners.add(owner);
            for (BlockPlacement p : blocks) {
                floorCells.computeIfAbsent(key(p.getX(), p.getY(), p.getZ()), k -> new CellWrites())
                        .writes.add(new int[] { ownerIndex, paletteIndex(p.getBlockId()) });
            }
        }
    }

    /**
     * Regroups the emitted pieces per floor. Floors stay in emit order (their Y descends, so a
     * numeric sort would invert them); only the kinds within a floor are reordered, stably, so
     * sibling rooms keep their relative order and the only variable is which kind writes last.
     */
    private List<StructurePiece> ordered(List<StructurePiece> pieces) {
        if (order == PieceOrder.EMIT) {
            return pieces;
        }
        // ROOMS_FIRST: re-sort each floor so rooms are written before corridors.
        Map<Integer, List<StructurePiece>> byFloor = new LinkedHashMap<>();
        for (StructurePiece piece : pieces) {
            byFloor.computeIfAbsent(floorYOf(piece), k -> new ArrayList<>()).add(piece);
        }
        List<StructurePiece> out = new ArrayList<>(pieces.size());
        for (List<StructurePiece> floor : byFloor.values()) {
            floor.sort(Comparator.comparingInt(order::rank));
            out.addAll(floor);
        }
        return out;
    }

    /** Builds the whole model as a JSON string. */
    public String toJson() {
        build();
        int anchorX = layout.getAnchor().getX();
        int anchorZ = layout.getAnchor().getZ();

        StringBuilder json = new StringBuilder(1 << 20);
        json.append("{\"seed\":").append(layout.getSeed())
                .append(",\"size\":\"").append(layout.getSize()).append('"')
                .append(",\"motif\":\"").append(esc(layout.getMotifValue())).append('"')
                .append(",\"anchor\":[").append(anchorX).append(',').append(anchorZ).append(']');

        Rectangle2D entrance = layout.getEntrance() == null ? null : layout.getEntrance().getFootprint();
        if (entrance != null) {
            json.append(",\"entrance\":").append(rect(entrance));
        }

        json.append(",\"floors\":[");
        for (int i = 0; i < layout.getFloors().size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            FloorLayout floor = layout.getFloors().get(i);
            appendFloor(json, floor, owners.get(floor.getFloorY()), cells.get(floor.getFloorY()),
                    entities.get(floor.getFloorY()));
        }
        json.append(']');

        json.append(",\"palette\":[");
        int p = 0;
        for (String block : palette.keySet()) {
            if (p++ > 0) {
                json.append(',');
            }
            json.append('"').append(esc(block)).append('"');
        }
        json.append("]}");
        return json.toString();
    }

    private void appendFloor(StringBuilder json, FloorLayout floor, List<Owner> owners,
                             Map<Long, CellWrites> cells, List<EntityPlacement> entities) {
        json.append("{\"index\":").append(floor.getFloorIndex())
                .append(",\"floorY\":").append(floor.getFloorY())
                .append(",\"ceilingY\":").append(floor.getCeilingY());
        if (floor.getFootprint() != null) {
            json.append(",\"footprint\":").append(rect(floor.getFootprint()));
        }

        json.append(",\"rooms\":[");
        for (int i = 0; i < floor.getRooms().size(); i++) {
            RoomData room = floor.getRooms().get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":").append(room.getId())
                    .append(",\"x\":").append(room.getOriginX())
                    .append(",\"z\":").append(room.getOriginZ())
                    .append(",\"w\":").append(room.getWidth())
                    .append(",\"d\":").append(room.getDepth())
                    .append(",\"h\":").append(room.getHeight())
                    .append(",\"role\":\"").append(room.getRole()).append('"');
            String scheme = roomSchemes.get(room.getId());
            if (scheme != null) {
                json.append(",\"scheme\":\"").append(esc(scheme)).append('"');
            }
            if (room.getTemplateId() != null) {
                json.append(",\"template\":\"").append(esc(room.getTemplateId())).append('"');
            }
            json.append(",\"doorways\":").append(coordList(room.getDoorways())).append('}');
        }
        json.append(']');

        json.append(",\"corridors\":[");
        for (int i = 0; i < floor.getCorridors().size(); i++) {
            CorridorData corridor = floor.getCorridors().get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":").append(corridor.getId())
                    .append(",\"cells\":").append(coordList(corridor.getCells()))
                    .append(",\"doorCells\":").append(coordList(corridor.getDoorCells()))
                    .append('}');
        }
        json.append(']');

        json.append(",\"doors\":[");
        for (int i = 0; i < floor.getDoors().size(); i++) {
            DoorData door = floor.getDoors().get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"x\":").append(door.getX())
                    .append(",\"z\":").append(door.getZ())
                    .append(",\"a\":").append(door.getRegionA())
                    .append(",\"b\":").append(door.getRegionB())
                    .append(",\"facing\":\"").append(door.getFacing()).append("\"}");
        }
        json.append(']');

        appendGrid(json, floor.getGrid());
        appendOwners(json, owners);
        appendLayers(json, cells);
        appendEntities(json, entities);

        json.append('}');
    }

    /** The maze grid as one row-major string of cell-type digits &mdash; compact and easy to index. */
    private void appendGrid(StringBuilder json, Grid2D grid) {
        if (grid == null) {
            return;
        }
        StringBuilder rows = new StringBuilder(grid.getWidth() * grid.getHeight());
        for (int z = 0; z < grid.getHeight(); z++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                Cell cell = grid.get(x, z);
                rows.append(cell == null ? '0' : (char) ('0' + cell.getType().getId()));
            }
        }
        json.append(",\"grid\":{\"w\":").append(grid.getWidth())
                .append(",\"h\":").append(grid.getHeight())
                .append(",\"cells\":\"").append(rows).append("\"}");
    }

    private void appendOwners(StringBuilder json, List<Owner> owners) {
        json.append(",\"owners\":[");
        if (owners != null) {
            for (int i = 0; i < owners.size(); i++) {
                Owner o = owners.get(i);
                if (i > 0) {
                    json.append(',');
                }
                json.append("{\"kind\":\"").append(o.kind())
                        .append("\",\"id\":").append(o.id())
                        .append(",\"label\":\"").append(esc(o.label()))
                        .append("\",\"detail\":\"").append(esc(o.detail())).append("\"}");
            }
        }
        json.append(']');
    }

    /**
     * Blocks, grouped by Y so the viewer can scrub vertically without re-bucketing 100k entries.
     *
     * <p>Each layer is one flat int array, five per cell: {@code x, z, block, owner, writes}. Flat
     * because a layer runs to tens of thousands of cells and an array of objects would multiply the
     * file size for no gain. Contested cells (more than one distinct owner wrote here) carry their
     * full write history in {@code contested}, which stays small.</p>
     */
    private void appendLayers(StringBuilder json, Map<Long, CellWrites> cells) {
        Map<Integer, List<Map.Entry<Long, CellWrites>>> byY = new TreeMap<>();
        if (cells != null) {
            for (Map.Entry<Long, CellWrites> e : cells.entrySet()) {
                byY.computeIfAbsent(unpackY(e.getKey()), k -> new ArrayList<>()).add(e);
            }
        }
        json.append(",\"layers\":[");
        boolean firstLayer = true;
        for (Map.Entry<Integer, List<Map.Entry<Long, CellWrites>>> layer : byY.entrySet()) {
            if (!firstLayer) {
                json.append(',');
            }
            firstLayer = false;
            json.append("{\"y\":").append(layer.getKey()).append(",\"d\":[");
            StringBuilder contested = new StringBuilder();
            boolean firstCell = true;
            for (Map.Entry<Long, CellWrites> e : layer.getValue()) {
                CellWrites cell = e.getValue();
                if (!firstCell) {
                    json.append(',');
                }
                firstCell = false;
                int x = unpackX(e.getKey());
                int z = unpackZ(e.getKey());
                json.append(x).append(',').append(z).append(',')
                        .append(cell.winnerBlock()).append(',')
                        .append(cell.winnerOwner()).append(',')
                        .append(cell.contested() ? cell.writes.size() : 1);
                if (cell.contested()) {
                    if (contested.length() > 0) {
                        contested.append(',');
                    }
                    contested.append("{\"x\":").append(x).append(",\"z\":").append(z)
                            .append(",\"w\":[");
                    for (int i = 0; i < cell.writes.size(); i++) {
                        if (i > 0) {
                            contested.append(',');
                        }
                        contested.append(cell.writes.get(i)[0]).append(',')
                                .append(cell.writes.get(i)[1]);
                    }
                    contested.append("]}");
                }
            }
            json.append("],\"contested\":[").append(contested).append("]}");
        }
        json.append(']');
    }

    private void appendEntities(StringBuilder json, List<EntityPlacement> entities) {
        json.append(",\"entities\":[");
        if (entities != null) {
            for (int i = 0; i < entities.size(); i++) {
                EntityPlacement e = entities.get(i);
                if (i > 0) {
                    json.append(',');
                }
                json.append("{\"x\":").append(e.getX())
                        .append(",\"y\":").append(e.getY())
                        .append(",\"z\":").append(e.getZ())
                        .append(",\"id\":\"").append(esc(e.getEntityId())).append("\"}");
            }
        }
        json.append(']');
    }

    /**
     * A text audit of who wins the contested cells, printed alongside the HTML.
     *
     * <p>The viewer shows this one cell at a time; this is the same information totalled, so two
     * runs (two seeds, or two {@link PieceOrder}s) can be compared as numbers rather than by
     * squinting at two pictures.</p>
     */
    public String audit() {
        build();
        StringBuilder sb = new StringBuilder();

        // Per-floor corridor geometry first: it is the one thing the 2D plan cannot show at all,
        // and after §5.3 it differs floor to floor, so "which style did this floor roll" is the
        // question a run of this tool is usually being asked.
        sb.append("Corridor style per floor\n");
        for (FloorLayout floor : layout.getFloors()) {
            String style = floor.getCorridors().isEmpty()
                    ? "(no corridors)"
                    : floor.getCorridors().get(0).getStyleName();
            int height = floor.getCorridors().isEmpty()
                    ? 0 : floor.getCorridors().get(0).getWallHeight();
            sb.append(String.format("  floor %d  style %-12s height %d%n",
                    floor.getFloorIndex(),
                    style.isEmpty() ? "(baseline)" : style,
                    height));
        }
        sb.append('\n');

        sb.append("Contested cells (written by more than one piece), whole dungeon\n");

        Map<String, Integer> byContenders = new TreeMap<>();
        Map<String, Integer> trimLost = new TreeMap<>();
        int total = 0;
        for (Map.Entry<Integer, Map<Long, CellWrites>> floorEntry : cells.entrySet()) {
            List<Owner> floorOwners = owners.get(floorEntry.getKey());
            for (CellWrites cell : floorEntry.getValue().values()) {
                if (!cell.contested()) {
                    continue;
                }
                total++;
                Set<String> kinds = new TreeSet<>();
                for (int[] w : cell.writes) {
                    kinds.add(floorOwners.get(w[0]).kind());
                }
                String winner = floorOwners.get(cell.winnerOwner()).kind();
                byContenders.merge(String.join("+", kinds) + " -> " + winner, 1, Integer::sum);

                // The writes that actually changed what a player sees. Same block in and out is
                // contention with no consequence, and most of it is.
                int firstBlock = cell.writes.get(0)[1];
                if (firstBlock != cell.winnerBlock()) {
                    trimLost.merge(blockName(firstBlock) + " -> " + blockName(cell.winnerBlock()),
                            1, Integer::sum);
                }
            }
        }
        sb.append("  order: ").append(order).append("   total: ").append(total).append('\n');
        byContenders.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sb.append(String.format("    %-34s %6d%n", e.getKey(), e.getValue())));

        sb.append("  first write overwritten by a DIFFERENT block:\n");
        if (trimLost.isEmpty()) {
            sb.append("    (none)\n");
        }
        trimLost.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sb.append(String.format("    %-58s %6d%n", e.getKey(), e.getValue())));

        sb.append(wallOwnership());
        sb.append(sealedDoorways());
        return sb.toString();
    }

    /**
     * Who owns each room's four walls, at the wall row (floor+2) where trim lives.
     *
     * <p>A side is credited to whoever won the plurality of its cells. This is the number the
     * shared-wall problem is actually about: a room whose sides are mostly owned by something else
     * wears something else's material, whatever its own scheme says.</p>
     */
    private String wallOwnership() {
        int sides = 0;
        int own = 0;
        int corridor = 0;
        int otherRoom = 0;
        int door = 0;
        int rooms = 0;
        int roomsOwningAll = 0;

        for (FloorLayout floor : layout.getFloors()) {
            int y = floor.getFloorY() + 2;
            Map<Long, CellWrites> floorCells = cells.get(floor.getFloorY());
            List<Owner> floorOwners = owners.get(floor.getFloorY());
            if (floorCells == null || floorOwners == null) {
                continue;
            }
            for (RoomData room : floor.getRooms()) {
                if (room.getRole() != RoomRole.NORMAL) {
                    continue;
                }
                rooms++;
                int mine = 0;
                for (List<int[]> side : sidesOf(room)) {
                    Map<Integer, Integer> tally = new HashMap<>();
                    for (int[] xz : side) {
                        CellWrites cell = floorCells.get(key(xz[0], y, xz[1]));
                        if (cell != null) {
                            tally.merge(cell.winnerOwner(), 1, Integer::sum);
                        }
                    }
                    if (tally.isEmpty()) {
                        continue;
                    }
                    sides++;
                    int leader = tally.entrySet().stream()
                            .max(Map.Entry.comparingByValue()).orElseThrow().getKey();
                    Owner o = floorOwners.get(leader);
                    if ("corridor".equals(o.kind())) {
                        corridor++;
                    } else if ("door".equals(o.kind())) {
                        door++;
                    } else if (o.id() == room.getId()) {
                        own++;
                        mine++;
                    } else {
                        otherRoom++;
                    }
                }
                if (mine == 4) {
                    roomsOwningAll++;
                }
            }
        }
        if (sides == 0) {
            return "";
        }
        return String.format("  wall ownership at floor+2, %d NORMAL rooms, %d sides%n"
                        + "    own room %5.1f%%   corridor %5.1f%%   other room %5.1f%%   door %5.1f%%%n"
                        + "    rooms owning all four of their own sides: %d of %d%n",
                rooms, sides,
                100.0 * own / sides, 100.0 * corridor / sides,
                100.0 * otherRoom / sides, 100.0 * door / sides,
                roomsOwningAll, rooms);
    }

    /**
     * Door cells left solid at the two door-half rows.
     *
     * <p>The room side masks doorways from {@code RoomData#getDoorways}; the corridor side masks
     * them from the maze grid's {@code DOOR} cells. Those are two different sources, so whichever
     * piece writes last decides whether a doorway is open &mdash; and any {@link PieceOrder} change
     * has to be checked against this, not just against who owns the trim.</p>
     */
    private String sealedDoorways() {
        int sealed = 0;
        int checked = 0;
        for (FloorLayout floor : layout.getFloors()) {
            Map<Long, CellWrites> floorCells = cells.get(floor.getFloorY());
            if (floorCells == null) {
                continue;
            }
            for (DoorData d : floor.getDoors()) {
                checked++;
                for (int v = 1; v <= 2; v++) {
                    CellWrites cell = floorCells.get(key(d.getX(), floor.getFloorY() + v, d.getZ()));
                    // Air (a doorless opening) and a door block are both walkable; anything else
                    // at a door-half row means the opening got bricked up.
                    if (cell != null && !isPassable(blockName(cell.winnerBlock()))) {
                        sealed++;
                        break;
                    }
                }
            }
        }
        return String.format("  doorways solid at the door-half rows: %d of %d%n", sealed, checked);
    }

    /** Whether a block at a door-half row still lets a player through. */
    private static boolean isPassable(String blockId) {
        return blockId.endsWith(":air") || blockId.endsWith("_door") || blockId.endsWith("_gate");
    }

    /** The four wall runs of a room's own box, as (x, z) pairs; corners belong to north and south. */
    private static List<List<int[]>> sidesOf(RoomData room) {
        List<int[]> north = new ArrayList<>();
        List<int[]> south = new ArrayList<>();
        List<int[]> west = new ArrayList<>();
        List<int[]> east = new ArrayList<>();
        for (int x = room.getOriginX(); x < room.getOriginX() + room.getWidth(); x++) {
            north.add(new int[] { x, room.getOriginZ() });
            south.add(new int[] { x, room.getOriginZ() + room.getDepth() - 1 });
        }
        for (int z = room.getOriginZ() + 1; z < room.getOriginZ() + room.getDepth() - 1; z++) {
            west.add(new int[] { room.getOriginX(), z });
            east.add(new int[] { room.getOriginX() + room.getWidth() - 1, z });
        }
        return List.of(north, south, west, east);
    }

    private String blockName(int paletteIndex) {
        for (Map.Entry<String, Integer> e : palette.entrySet()) {
            if (e.getValue() == paletteIndex) {
                return e.getKey();
            }
        }
        return "?";
    }

    private int paletteIndex(String blockId) {
        return palette.computeIfAbsent(blockId == null ? "?" : blockId, k -> palette.size());
    }

    /**
     * The floor a piece belongs to. {@code DungeonPiece} keeps its floor Y protected, but the piece's
     * bounding box starts at it for all three procedural pieces, so the box's minY is the same number.
     */
    private static int floorYOf(StructurePiece piece) {
        return piece.getBoundingBox().minY();
    }

    // Coordinates can be negative (corridor wall cells reach out of bounds), so bias before packing.
    private static final long BIAS = 1 << 20;

    private static long key(int x, int y, int z) {
        return ((long) (x + BIAS) << 42) | ((long) (y + BIAS) << 21) | (z + BIAS);
    }

    private static int unpackX(long k) { return (int) ((k >>> 42) - BIAS); }
    private static int unpackY(long k) { return (int) (((k >>> 21) & 0x1FFFFF) - BIAS); }
    private static int unpackZ(long k) { return (int) ((k & 0x1FFFFF) - BIAS); }

    private static String rect(Rectangle2D r) {
        return "{\"x\":" + r.getOrigin().getX() + ",\"z\":" + r.getOrigin().getY()
                + ",\"w\":" + r.getWidth() + ",\"h\":" + r.getHeight() + "}";
    }

    private static String coordList(List<Coords2D> coords) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < coords.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(coords.get(i).getX()).append(',').append(coords.get(i).getY());
        }
        return sb.append(']').toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
