package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Rectangle2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much of a room's wall ring it shares with a <em>neighbouring room</em>, and by how much those
 * neighbours differ in height. Backlog #18.
 *
 * <h2>Why both numbers, and why together</h2>
 * <p>The sharing rate decides whether #18 is worth acting on at all. The <strong>height</strong>
 * distribution decides which of the proposed fixes is safe: an ownership rule that splits a shared
 * wall by height ("the shorter room owns up to its ceiling, the taller room owns the rows above")
 * puts two rooms' trim on one physical wall. Wall courses can be anchored to {@code top}, so each
 * room would draw its crown at its <em>own</em> ceiling &mdash; two crowns, stacked, on one wall.
 * That only matters if neighbours actually differ in height, which is what this measures.</p>
 *
 * <h2>What counts as a shared side</h2>
 * <p>Room boxes include their wall ring and adjacent rooms overlap by exactly one column &mdash;
 * the deliberate shared-wall design. So A's west side is shared when some B ends exactly where A
 * begins. A <strong>corner touch</strong> (the perpendicular ranges meeting in a single cell) is
 * excluded: one cell is not a wall run, and counting it would inflate the rate with contacts that
 * have no visible trim consequence.</p>
 */
class SharedWallProbe {

    private static final int DUNGEONS = 60;
    /** What {@code generation_config/default.json} ships. */
    private static final int SHIPPED_ATTEMPTS_PER_FLOOR = 4;

    private static final int[][] ROTATION_OFFSET = {{0, 0}, {-6, 0}, {-6, -6}, {0, -6}};

    /** See {@code RoomAssemblyPlacementTest.ROTATED_7X7} -- same model, kept local to this probe. */
    private static final DungeonStackPlanner.RoomAssembler ROTATED_7X7 =
            (wx, wy, wz, seed, commit) -> {
                int[] o = ROTATION_OFFSET[Math.floorMod(new java.util.Random(seed).nextInt(), 4)];
                Rectangle2D fp = new Rectangle2D(wx + o[0], wz + o[1], 7, 7);
                int minX = fp.getMinX();
                int minZ = fp.getMinY();
                return Optional.of(new DungeonStackPlanner.AssembledRoom(fp,
                        List.of(new Coords2D(minX, minZ + 3), new Coords2D(minX + 3, minZ),
                                new Coords2D(minX + 3, minZ + 6), new Coords2D(minX + 6, minZ + 3)),
                        List.of()));
            };

    @Test
    void howMuchOfARoomsWallIsItsNeighbours() {
        int rooms = 0;
        int[] sidesSharedHistogram = new int[5];
        int sharedPairs = 0;
        int sameHeight = 0;
        Map<Integer, Integer> heightDeltas = new TreeMap<>();
        // Backlog #18 follow-up: a prefab room's wall is authored, so losing it to a procedural
        // neighbour costs more than one procedural scheme losing to another.
        int prefabs = 0;
        int prefabsSharingWithProcedural = 0;
        int prefabProceduralSides = 0;
        // START / END: the entrance and transition slots, also authored.
        int slots = 0;
        int slotsSharingWithProcedural = 0;
        int slotProceduralSides = 0;

        for (int i = 0; i < DUNGEONS; i++) {
            long seed = 0xD2_0BADC0DEL + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    // Prefabs only exist when an assembler is supplied, and real jigsaw assembly
                    // needs a server. This models the shipped prefabs the same way
                    // RoomAssemblyPlacementTest does: 7x7, rotated one of four ways.
                    .withRoomTemplateAttempts(SHIPPED_ATTEMPTS_PER_FLOOR)
                    .withRoomAssembler(ROTATED_7X7)
                    .plan();
            if (planned.isEmpty()) {
                continue;
            }
            for (FloorLayout floor : planned.get().getFloors()) {
                List<RoomData> all = new ArrayList<>(floor.getRooms());
                for (RoomData room : all) {
                    if (room.getRole() != RoomRole.NORMAL) {
                        // START / END are the entrance and transition slots -- authored jigsaw
                        // pieces, so the same "authored should beat generated" question applies.
                        slots++;
                        int slotSides = 0;
                        for (int side = 0; side < 4; side++) {
                            if (neighbourOn(room, side, all, RoomRole.NORMAL) != null) {
                                slotSides++;
                            }
                        }
                        if (slotSides > 0) {
                            slotsSharingWithProcedural++;
                            slotProceduralSides += slotSides;
                        }
                        continue;
                    }
                    rooms++;
                    boolean isPrefab = room.getTemplateId() != null;
                    if (isPrefab) {
                        prefabs++;
                    }
                    int sides = 0;
                    int prefabSidesHere = 0;
                    for (int side = 0; side < 4; side++) {
                        RoomData neighbour = neighbourOn(room, side, all);
                        if (neighbour == null) {
                            continue;
                        }
                        sides++;
                        sharedPairs++;
                        int delta = Math.abs(room.getHeight() - neighbour.getHeight());
                        if (delta == 0) {
                            sameHeight++;
                        }
                        heightDeltas.merge(delta, 1, Integer::sum);
                        if (isPrefab && neighbour.getTemplateId() == null) {
                            prefabSidesHere++;
                        }
                    }
                    sidesSharedHistogram[sides]++;
                    if (prefabSidesHere > 0) {
                        prefabsSharingWithProcedural++;
                        prefabProceduralSides += prefabSidesHere;
                    }
                }
            }
        }

        int sharingAtLeastOne = rooms - sidesSharedHistogram[0];
        System.out.printf("%nRoom-on-room shared walls, %d MEDIUM dungeons, %d NORMAL rooms%n",
                DUNGEONS, rooms);
        System.out.printf("  rooms sharing >= 1 side with another room: %d (%.1f%%)%n",
                sharingAtLeastOne, 100.0 * sharingAtLeastOne / rooms);
        System.out.println("  sides shared   rooms");
        for (int s = 0; s <= 4; s++) {
            System.out.printf("       %d        %5d  (%5.1f%%)%n",
                    s, sidesSharedHistogram[s], 100.0 * sidesSharedHistogram[s] / rooms);
        }

        System.out.printf("%n  shared-wall neighbour pairs (counted once per side): %d%n", sharedPairs);
        System.out.printf("  neighbours of EQUAL height: %d (%.1f%%) -- the rest would carry two%n"
                        + "  differently-placed top-anchored courses under a height-split rule%n",
                sameHeight, 100.0 * sameHeight / sharedPairs);
        System.out.println("  |height difference|   pairs");
        final int pairs = sharedPairs;
        heightDeltas.forEach((k, v) -> System.out.printf("        %2d            %5d  (%5.1f%%)%n",
                k, v, 100.0 * v / pairs));

        System.out.printf("%n  PREFAB rooms: %d (%.1f%% of NORMAL)%n", prefabs, 100.0 * prefabs / rooms);
        if (prefabs > 0) {
            System.out.printf("  ...sharing >= 1 wall with a PROCEDURAL room: %d (%.1f%% of prefabs)%n",
                    prefabsSharingWithProcedural, 100.0 * prefabsSharingWithProcedural / prefabs);
            System.out.printf("  ...total such sides: %d (%.2f per prefab)%n",
                    prefabProceduralSides, (double) prefabProceduralSides / prefabs);
        }

        System.out.printf("%n  START/END slots (entrance + transitions): %d%n", slots);
        if (slots > 0) {
            System.out.printf("  ...sharing >= 1 wall with a PROCEDURAL room: %d (%.1f%% of slots)%n",
                    slotsSharingWithProcedural, 100.0 * slotsSharingWithProcedural / slots);
            System.out.printf("  ...total such sides: %d (%.2f per slot)%n",
                    slotProceduralSides, (double) slotProceduralSides / slots);
        }

        assertTrue(rooms > 50, "need a meaningful sample");
    }

    /**
     * The room sharing {@code room}'s wall on {@code side} (0=west, 1=east, 2=north, 3=south), or
     * null. Returns the first found: a single side can in principle be shared by two short rooms,
     * which this deliberately does not try to model &mdash; the question is whether the side is
     * the neighbour's to style, not how many neighbours have a claim.
     */
    private static RoomData neighbourOn(RoomData room, int side, List<RoomData> all) {
        return neighbourOn(room, side, all, RoomRole.NORMAL);
    }

    /** As above, restricted to neighbours in {@code role}. */
    private static RoomData neighbourOn(RoomData room, int side, List<RoomData> all, RoomRole role) {
        int minX = room.getOriginX();
        int maxX = room.getOriginX() + room.getWidth() - 1;
        int minZ = room.getOriginZ();
        int maxZ = room.getOriginZ() + room.getDepth() - 1;

        for (RoomData other : all) {
            if (other == room || other.getRole() != role) {
                continue;
            }
            int oMinX = other.getOriginX();
            int oMaxX = other.getOriginX() + other.getWidth() - 1;
            int oMinZ = other.getOriginZ();
            int oMaxZ = other.getOriginZ() + other.getDepth() - 1;

            boolean touches = switch (side) {
                case 0 -> oMaxX == minX;
                case 1 -> oMinX == maxX;
                case 2 -> oMaxZ == minZ;
                default -> oMinZ == maxZ;
            };
            if (!touches) {
                continue;
            }
            // The run along the shared column must be longer than a single cell, or this is a
            // corner touch rather than a shared wall.
            int overlap = (side <= 1)
                    ? Math.min(maxZ, oMaxZ) - Math.max(minZ, oMinZ) + 1
                    : Math.min(maxX, oMaxX) - Math.max(minX, oMinX) + 1;
            if (overlap > 1) {
                return other;
            }
        }
        return null;
    }
}
