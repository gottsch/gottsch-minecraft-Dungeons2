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
package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often a procedural room actually gets a spawner, and how many mobs the dungeon is carrying.
 *
 * <h2>Why the shipped config does not answer this</h2>
 * <p>The nine {@code classic} hall schemes carry {@code spawners} at {@code minCount: 0} /
 * {@code maxCount: 1}, and there is no probability field &mdash; the {@code 0} is the whole
 * incidence lever, so the naive reading is "half of the halls". That reading is wrong twice over:
 * the halls are only a fraction of all rooms (the rest roll {@code plain}, which has no slot at
 * all), and a slot that survives its roll can still emit nothing, because the spawner has to find
 * an eligible interior cell that the wall trim, the columns and the platforms have not already
 * claimed. Both gaps are invisible in the JSON.</p>
 *
 * <h2>It runs the real generator rather than re-deriving the slot</h2>
 * <p>{@code EmptyRoomProbe} rebuilds a subset of the slots with an empty {@code occupied} set and
 * documents the resulting over-estimate. That shortcut is not available here: the spawner is placed
 * <em>against</em> cells the wall, pillar and platform generators claimed, so the claiming order is
 * exactly what decides whether one fits. This probe therefore calls
 * {@link BasicRoomGenerator#build} and counts the {@code dungeons2:mob_set_spawner} placements it
 * emitted &mdash; measuring what a player would find, not what the scheme asked for.</p>
 *
 * <h2>Floors are real floors</h2>
 * <p>Rooms are walked with their own {@link FloorLayout#getFloorIndex()} rather than a constant,
 * so the per-depth breakdown is the real population. Nothing shipped gates a scheme by floor today,
 * so incidence is expected to be flat with depth &mdash; a slope here would mean something is
 * varying that nobody authored, which is worth seeing.</p>
 *
 * <h2>Two stages, reported separately</h2>
 * <p>A room ends up without a spawner for two unrelated reasons, and collapsing them makes the
 * headline meaningless: either its scheme has no slot at all (it rolled {@code plain}), or it has
 * one and the {@code 0..1} count roll came up 0. Only what is left over after both is
 * <em>cell exhaustion</em> &mdash; the slot surviving its roll and still finding nowhere to stand,
 * which is the one outcome that would be a bug rather than a design.</p>
 *
 * <p>Diagnostic, not a guard: it asserts only that the measurement is non-vacuous. Numbers move
 * whenever a scheme's weight or a size gate changes, and pinning them would be a test that fails
 * for the wrong reason.</p>
 */
class SpawnerIncidenceProbe {

    private static final int DUNGEONS = 60;

    private static final String SPAWNER = "dungeons2:mob_set_spawner";

    /** The generators resolve block states. */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void howOftenDoesARoomGetASpawner() {
        MotifConfig config = MotifConfigs.load("classic");

        int rooms = 0;
        int roomsWithSpawner = 0;
        int spawners = 0;
        int carriesSlot = 0;       // scheme rolled DOES have a spawners slot

        Map<Integer, int[]> byFloor = new TreeMap<>();    // [rooms, roomsWithSpawner, spawners]
        Map<Integer, int[]> byMinSide = new TreeMap<>();  // [rooms, roomsWithSpawner]
        Map<String, int[]> bySize = new TreeMap<>();      // dungeon size tier

        for (DungeonSize size : new DungeonSize[] {DungeonSize.MEDIUM, DungeonSize.LARGE}) {
            for (int i = 0; i < DUNGEONS; i++) {
                long seed = 0xD2_5A0E_0001L + i * 7919L;
                Optional<DungeonLayout> planned = new DungeonStackPlanner(
                        seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                        .withSize(size).plan();
                if (planned.isEmpty()) {
                    continue;
                }
                for (FloorLayout floor : planned.get().getFloors()) {
                    int floorIndex = floor.getFloorIndex();
                    for (RoomData room : floor.getRooms()) {
                        // NORMAL only: START/END are covered by templates, and TERMINAL is a single
                        // room per dungeon whose incidence would distort a per-room rate.
                        if (room.getRole() != RoomRole.NORMAL) {
                            continue;
                        }
                        rooms++;

                        // The piece seeds from the room id, chunk-independently, so this is the
                        // same roll the real render makes rather than a fresh one.
                        RoomPlacements out = new RoomPlacements();
                        new BasicRoomGenerator().withMotifConfig(config).build(
                                room, floor.getFloorY(), floorIndex, DungeonMotif.CLASSIC,
                                RandomSource.create(seed + room.getId()), out);

                        int placed = 0;
                        for (BlockPlacement p : out.getBlocks()) {
                            if (SPAWNER.equals(p.getBlockId())) {
                                placed++;
                            }
                        }

                        spawners += placed;
                        int minSide = Math.min(room.getWidth(), room.getDepth());
                        byFloor.computeIfAbsent(floorIndex, k -> new int[3])[0]++;
                        byMinSide.computeIfAbsent(minSide, k -> new int[2])[0]++;
                        bySize.computeIfAbsent(size.name(), k -> new int[2])[0]++;
                        if (placed > 0) {
                            roomsWithSpawner++;
                            byFloor.get(floorIndex)[1]++;
                            byMinSide.get(minSide)[1]++;
                            bySize.get(size.name())[1]++;
                        }
                        if (carriesTheSlot(config, room, floorIndex, seed)) {
                            carriesSlot++;
                        }
                        byFloor.get(floorIndex)[2] += placed;
                    }
                }
            }
        }

        System.out.printf("%nSPAWNER INCIDENCE -- %d dungeons per size tier, %d procedural rooms%n",
                DUNGEONS, rooms);
        System.out.printf("  rooms with >=1 spawner : %d (%.1f%%)%n",
                roomsWithSpawner, pct(roomsWithSpawner, rooms));
        System.out.printf("  spawners placed        : %d (%.2f per room, %.1f per dungeon)%n",
                spawners, rooms == 0 ? 0 : (double) spawners / rooms,
                (double) spawners / (DUNGEONS * 2));
        // The two stages have to be reported separately or the headline is unreadable. A room gets
        // no spawner for two completely different reasons: its scheme has no slot (it rolled
        // `plain`), or it has one and the 0..1 count roll came up 0. Only what is left after BOTH
        // is cell exhaustion -- the failure mode that would actually be a bug.
        double expected = 50.0D;  // minCount 0 / maxCount 1, uniform inclusive
        double placedOfCarrying = pct(roomsWithSpawner, carriesSlot);
        System.out.printf("%n  stage 1 -- scheme carries a spawners slot : %d (%.1f%% of rooms)%n",
                carriesSlot, pct(carriesSlot, rooms));
        System.out.printf("  stage 2 -- of those, actually placed      : %d (%.1f%%, expected %.1f%%"
                        + " from minCount 0 / maxCount 1)%n",
                roomsWithSpawner, placedOfCarrying, expected);
        System.out.printf("  => cell exhaustion is at most             : %.1f pp (~%d rooms)%n",
                Math.max(0.0D, expected - placedOfCarrying),
                Math.round(carriesSlot * Math.max(0.0D, expected - placedOfCarrying) / 100.0D));

        System.out.printf("%n  by floor (depth should be FLAT -- nothing shipped gates by floor)%n");
        byFloor.forEach((floorIndex, t) -> System.out.printf(
                "    floor %-2d  rooms %-5d with spawner %-5d (%.1f%%)  spawners %d%n",
                floorIndex, t[0], t[1], pct(t[1], t[0]), t[2]));

        System.out.printf("%n  by room min side (the halls need room; plain has no slot)%n");
        byMinSide.forEach((side, t) -> System.out.printf(
                "    %-3d  rooms %-5d with spawner %-5d (%.1f%%)%n",
                side, t[0], t[1], pct(t[1], t[0])));

        System.out.printf("%n  by dungeon size%n");
        bySize.forEach((name, t) -> System.out.printf(
                "    %-7s rooms %-5d with spawner %-5d (%.1f%%)%n",
                name, t[0], t[1], pct(t[1], t[0])));
        System.out.println();

        assertTrue(rooms > 0, "no procedural rooms were built, so this probe measured nothing");
        assertTrue(spawners > 0,
                "not one spawner was placed across " + rooms + " rooms. Either the spawners slot"
                        + " stopped being shipped, or every candidate cell is being claimed before"
                        + " the spawner runs -- both are real regressions, not measurement noise");
    }

    /**
     * Whether the scheme this room rolled carries a spawners slot at all, used only to separate
     * "the scheme never had one" from "it had one and nothing fit".
     *
     * <p>Re-rolls the scheme off the same seed the build used. That is sound because the selector is
     * a pure function of (schemes, dimensions, floor, random) and the random is recreated
     * identically &mdash; but it is worth stating, since a shared stream would have made this
     * re-roll silently disagree with the one inside the generator.</p>
     */
    private static boolean carriesTheSlot(MotifConfig config, RoomData room, int floorIndex, long seed) {
        return mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector
                .select(config.schemes(), room.getWidth(), room.getDepth(), room.getHeight(),
                        floorIndex, RandomSource.create(seed + room.getId()))
                .spawnersFor(room.getWidth(), room.getDepth(), room.getHeight())
                .isPresent();
    }

    private static double pct(int part, int whole) {
        return whole == 0 ? 0.0D : 100.0D * part / whole;
    }
}
