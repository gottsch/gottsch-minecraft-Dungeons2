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
package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <strong>Every room slot the maze reserves is built by somebody.</strong>
 *
 * <h2>The hole this exists to stop</h2>
 * <p>The maze reserves a footprint for each room, walls its neighbours against it and routes a door
 * into it. Whether anything then <em>builds</em> that footprint was, until 2026-08-11, decided by two
 * unrelated places: {@code DungeonPieceEmitter} skipped every non-{@code NORMAL} role because "the
 * template piece covers those slots", and the planner handed the bottom floor an end room that no
 * template piece covers &mdash; there is no downstairs transition below the bottom floor.</p>
 *
 * <p>The result was a reserved, doored, never-built room in every dungeon. It was invisible wherever
 * the untouched terrain there happened to be solid stone, and at bottom-floor depth it is frequently
 * a cave, so it read as a hole into lava. Found in game, not here, because nothing asked this
 * question.</p>
 *
 * <h2>What is actually asserted</h2>
 * <p>That the roles partition <strong>exhaustively</strong> into "this mod builds it" and "a named
 * template slot covers it". A headless test cannot check that a jigsaw piece really assembled &mdash;
 * there is no {@code StructureTemplateManager} here &mdash; but it can check that every room falls
 * into one of the two buckets and that the covered bucket contains only slots that genuinely have a
 * coverer. That is exactly the check that was missing: {@code TERMINAL} used to fall into the
 * covered bucket with nothing covering it.</p>
 *
 * @author Mark Gottschling on Aug 11, 2026
 */
class SlotCoverageTest {

    private static final String MOTIF = DungeonMotif.CLASSIC.getValue();
    private static final Coords ANCHOR = new Coords(0, 0, 0);
    private static final int SURFACE_Y = 72;

    private static Optional<DungeonLayout> plan(long seed, DungeonSize size) {
        return new DungeonStackPlanner(seed, ANCHOR, SURFACE_Y, MOTIF, new TemplateCatalog())
                .withSize(size).plan();
    }

    /**
     * Which template piece is supposed to cover a slot, or null when nothing does.
     *
     * <p>This is the whole contract, written out in one place so it can be wrong loudly rather than
     * quietly. Mirrors {@code RoomRole}'s javadoc.</p>
     */
    private static String covererOf(RoomData room, int floorIndex, int floorCount) {
        return switch (room.getRole()) {
            case START -> floorIndex == 0 ? "the assembled entrance" : "the transition from above";
            case END -> floorIndex < floorCount - 1 ? "the transition below" : null;
            case NORMAL -> room.getTemplateId() != null ? "an assembled prefab room" : null;
            case TERMINAL -> null;
            // #46. BOSS is set ONLY when an authored end_rooms template assembled and the planner
            // adopted it, so unlike the TERMINAL-as-END mistake this bucket really does have a
            // coverer. An attempt that failed leaves the slot TERMINAL and the row above.
            case BOSS -> "the assembled boss room";
        };
    }

    /**
     * <strong>The invariant.</strong> Every room is either procedurally built or has a named
     * coverer; nothing may be neither.
     *
     * <p>Reverting {@code TERMINAL} to {@code END} fails this on the bottom floor of every dungeon,
     * which is what makes it a real check rather than a restatement of the code.</p>
     */
    @Test
    void everyReservedSlotIsEitherBuiltOrCovered() {
        List<String> orphans = new ArrayList<>();
        int roomsChecked = 0;
        for (long seed = 0; seed < 120; seed++) {
            for (DungeonSize size : List.of(DungeonSize.SMALL, DungeonSize.MEDIUM, DungeonSize.LARGE)) {
                Optional<DungeonLayout> opt = plan(seed, size);
                if (opt.isEmpty()) {
                    continue;
                }
                int floorCount = opt.get().getFloors().size();
                for (FloorLayout floor : opt.get().getFloors()) {
                    for (RoomData room : floor.getRooms()) {
                        roomsChecked++;
                        boolean built = room.getRole().isProcedurallyBuilt()
                                && room.getTemplateId() == null;
                        String coverer = covererOf(room, floor.getFloorIndex(), floorCount);
                        if (!built && coverer == null) {
                            orphans.add("seed " + seed + " " + size + " floor "
                                    + floor.getFloorIndex() + "/" + floorCount + ": "
                                    + room.getRole() + " room at "
                                    + room.getOriginX() + "," + room.getOriginZ()
                                    + " " + room.getWidth() + "x" + room.getDepth()
                                    + " is reserved but nothing builds it");
                        }
                    }
                }
            }
        }
        assertTrue(roomsChecked > 1000, "expected a real sample, checked " + roomsChecked + " rooms");
        if (!orphans.isEmpty()) {
            Set<String> distinct = new LinkedHashSet<>(orphans);
            fail(orphans.size() + " reserved room slot(s) are built by nothing -- each one is a door"
                    + " opening into raw terrain, and a hole into a cave when that terrain is hollow."
                    + " First few:\n  "
                    + String.join("\n  ", distinct.stream().limit(5).toList()));
        }
    }

    /**
     * The bottom floor's endpoint specifically, because it is the case that shipped broken and the
     * one a future refactor is most likely to fold back into {@code END}.
     */
    @Test
    void theBottomFloorsEndpointIsTerminalAndThereforeBuilt() {
        int found = 0;
        for (long seed = 0; seed < 60; seed++) {
            Optional<DungeonLayout> opt = plan(seed, DungeonSize.MEDIUM);
            if (opt.isEmpty()) {
                continue;
            }
            List<FloorLayout> floors = opt.get().getFloors();
            FloorLayout bottom = floors.get(floors.size() - 1);
            List<RoomData> terminals = bottom.getRooms().stream()
                    .filter(room -> room.getRole() == RoomRole.TERMINAL).toList();
            assertTrue(terminals.size() == 1,
                    "seed " + seed + ": expected exactly one TERMINAL on the bottom floor, got "
                            + terminals.size());
            assertTrue(terminals.get(0).getRole().isProcedurallyBuilt(),
                    "seed " + seed + ": the terminal room must be built by us");
            found++;
        }
        assertTrue(found > 40, "expected most seeds to plan, got " + found + "/60");
    }

    /** And no upper floor grows one -- a TERMINAL above the bottom would be a dead end mid-dungeon. */
    @Test
    void onlyTheBottomFloorHasATerminalRoom() {
        for (long seed = 0; seed < 60; seed++) {
            Optional<DungeonLayout> opt = plan(seed, DungeonSize.LARGE);
            if (opt.isEmpty()) {
                continue;
            }
            List<FloorLayout> floors = opt.get().getFloors();
            for (int i = 0; i < floors.size() - 1; i++) {
                boolean hasTerminal = floors.get(i).getRooms().stream()
                        .anyMatch(room -> room.getRole() == RoomRole.TERMINAL);
                assertTrue(!hasTerminal,
                        "seed " + seed + ": floor " + i + " of " + floors.size()
                                + " has a TERMINAL room, but it has a floor below it");
            }
        }
    }
}
