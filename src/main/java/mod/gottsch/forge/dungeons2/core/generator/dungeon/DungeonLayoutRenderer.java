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
package mod.gottsch.forge.dungeons2.core.generator.dungeon;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DoorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.enums.IDungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.BasicCorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.ICorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.BasicDoorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.door.IDoorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.IRoomGenerator;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2 capstone: turns a whole {@link DungeonLayout} into one flat
 * {@link BlockPlacement} list by walking each floor's rooms, corridors, and
 * doors through the {@code Basic*Generator} family.
 *
 * <p>This is the single consumer the Phase 1 planner was built for &mdash; and
 * the same render pass the Phase 3 {@code StructurePiece}s reuse per chunk.
 * Coordinates in the returned placements are <strong>floor-local grid X/Z</strong>
 * with <strong>world-absolute Y</strong> (each floor's {@code floorY} is already
 * absolute). The caller (debug command or piece renderer) adds the dungeon's
 * world-space anchor XZ.</p>
 *
 * <h2>What is and isn't rendered</h2>
 * <ul>
 *     <li><strong>Rendered:</strong> {@link RoomRole#NORMAL} and
 *         {@link RoomRole#TERMINAL} rooms, every corridor, every door.</li>
 *     <li><strong>Skipped:</strong> {@link RoomRole#START} / {@link RoomRole#END}
 *         rooms, the entrance, and transitions &mdash; those are template pieces
 *         (or synthetic placeholders) handled by the shell, not procedural
 *         builders. This mirrors the Phase 4 emitter contract.</li>
 * </ul>
 *
 * <p>Touches {@code net.minecraft.*} (via the builders / {@link BlockStateCodec}),
 * so it lives on the Forge shell side, not in the pure-POJO core.</p>
 *
 * @author Mark Gottschling on Jun 14, 2026
 */
public final class DungeonLayoutRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DungeonLayoutRenderer.class);

    private final IRoomGenerator roomGenerator;
    private final ICorridorGenerator corridorGenerator;
    private final IDoorGenerator doorGenerator;

    public DungeonLayoutRenderer() {
        this(new BasicRoomGenerator(), new BasicCorridorGenerator(), new BasicDoorGenerator());
    }

    public DungeonLayoutRenderer(IRoomGenerator roomGenerator,
                                 ICorridorGenerator corridorGenerator,
                                 IDoorGenerator doorGenerator) {
        this.roomGenerator = roomGenerator;
        this.corridorGenerator = corridorGenerator;
        this.doorGenerator = doorGenerator;
    }

    /** Renders every floor of the layout into one combined placement list. */
    public List<BlockPlacement> render(DungeonLayout layout, RandomSource random) {
        return renderAll(layout, random).getBlocks();
    }

    /**
     * Renders blocks <em>and</em> entities. {@link #render} is the block-only view of this, kept
     * because most callers (and every geometry test) only care about blocks; anything that actually
     * writes to a world should use this one, or a room's pots are silently dropped.
     */
    public RoomPlacements renderAll(DungeonLayout layout, RandomSource random) {
        RoomPlacements out = new RoomPlacements();
        IDungeonMotif motif = resolveMotif(layout.getMotifValue());
        for (FloorLayout floor : layout.getFloors()) {
            renderFloor(floor, motif, random, out);
        }
        return out;
    }

    /** Block-only overload, for callers that render geometry and nothing else. */
    public void renderFloor(FloorLayout floor, IDungeonMotif motif,
                            RandomSource random, List<BlockPlacement> out) {
        RoomPlacements placements = new RoomPlacements();
        renderFloor(floor, motif, random, placements);
        out.addAll(placements.getBlocks());
    }

    /** Renders one floor's normal rooms, corridors, and doors into {@code placements}. */
    public void renderFloor(FloorLayout floor, IDungeonMotif motif,
                            RandomSource random, RoomPlacements placements) {
        List<BlockPlacement> out = placements.getBlocks();
        int floorY = floor.getFloorY();

        // Corridors, then rooms, then doors -- deliberately the same order
        // DungeonPieceEmitter uses, so that this renderer and the piece pipeline resolve a
        // shared cell identically. See the comment there for why a room must win its own
        // perimeter; if that order ever changes, it has to change in both places or the
        // debug command and every renderer-based test quietly stop matching what generates.
        Grid2D grid = floor.getGrid();
        if (grid != null) {
            for (CorridorData corridor : floor.getCorridors()) {
                corridorGenerator.build(corridor, grid, floorY, motif, random, out);
            }
        } else {
            // Should only happen on a deserialized layout, which Phase 2 never
            // renders. Phase 3 will carry wall cells on CorridorData instead.
            LOGGER.warn("FloorLayout {} has no transient grid; skipping corridor rendering",
                    floor.getFloorIndex());
        }

        for (RoomData room : floor.getRooms()) {
            // START/END rooms are covered by the entrance/transition template
            // (or a synthetic placeholder); skip them here.
            if (!room.getRole().isProcedurallyBuilt()) {
                continue;
            }
            roomGenerator.build(room, floorY, motif, random, placements);
        }

        for (DoorData door : floor.getDoors()) {
            doorGenerator.build(door, floorY, motif, random, out);
        }
    }

    /** Resolves the planner's motif string to an enum, defaulting to CLASSIC. */
    private static IDungeonMotif resolveMotif(String motifValue) {
        if (motifValue != null) {
            DungeonMotif motif = DungeonMotif.getByValue(motifValue);
            if (motif != null && motif != DungeonMotif.UNKNOWN) {
                return motif;
            }
        }
        return DungeonMotif.CLASSIC;
    }
}
