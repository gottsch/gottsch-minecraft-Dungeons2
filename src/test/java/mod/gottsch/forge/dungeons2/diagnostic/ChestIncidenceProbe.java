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

import mod.gottsch.forge.dungeons2.core.config.ChestConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.BlockEntityData;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.BasicRoomGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often a procedural room actually gets a chest, and which loot band the chests it gets draw
 * from. Backlog #48's missing measurement.
 *
 * <h2>Why the shipped config does not answer this</h2>
 * <p>Three of the eleven {@code classic} schemes carry a {@code chests} slot &mdash;
 * {@code vaulted_hall}, {@code hypostyle_hall}, and {@code joisted_hall_stone}, which inherits it
 * from the abstract {@code grand_hall}. All three ship {@code minCount: 0} / {@code maxCount: 1},
 * so the naive reading is "half of those three". That reading is wrong three times over, and each
 * of the three gaps is invisible in the JSON:</p>
 * <ol>
 *   <li>the three carriers are a fraction of all rooms, and each is size-gated at {@code minSize 9};</li>
 *   <li>a slot that survives its count roll still has to find a wall-adjacent cell that the trim,
 *       the columns, the platforms and the spawner have not already claimed;</li>
 *   <li><strong>and, unlike the spawner, a chest can be suppressed by the depth table.</strong> A
 *       scheme that names no tables of its own resolves against {@code chestLootByFloorIndex}, and
 *       a floor with no band places <em>nothing</em> &mdash; deliberately, because an empty chest
 *       is indistinguishable from a looted one. That is a silent zero, and it is exactly the shape
 *       of thing a probe exists to catch.</li>
 * </ol>
 *
 * <h2>It runs the real generator rather than re-deriving the slot</h2>
 * <p>Same argument as {@code SpawnerIncidenceProbe}: chests are drawn <em>against</em> the cells the
 * wall, pillar, platform and spawner generators have claimed, so the claiming order is what decides
 * whether one fits. This calls {@link BasicRoomGenerator#build} and counts the chest placements it
 * emitted &mdash; what a player would find, not what the scheme asked for.</p>
 *
 * <h2>The band breakdown is the number worth having</h2>
 * <p>Incidence alone does not tell you whether the tiering works. The bands ship at floors 0 / 2 / 4
 * (shallow &rarr; deep &rarr; hoard), and a band nothing reaches is authored loot no player will
 * ever see. So the per-floor table mix is reported alongside the rate, and the probe asserts that
 * every shipped table was actually drawn from.</p>
 *
 * <p>Diagnostic, not a guard: it pins no rate. Numbers move whenever a scheme's weight or a size
 * gate changes, and pinning them would be a test that fails for the wrong reason.</p>
 */
class ChestIncidenceProbe {

    private static final int DUNGEONS = 60;

    /** The block-entity type a chest cell carries; see {@code RoomChestGenerator#CHEST_ENTITY}. */
    private static final String CHEST_ENTITY = "minecraft:chest";
    private static final String LOOT_TABLE = "LootTable";
    private static final String LOOT_TABLE_SEED = "LootTableSeed";

    /** The generators resolve block states. */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void howOftenDoesARoomGetAChest() {
        MotifConfig config = MotifConfigs.load("classic");

        int rooms = 0;
        int roomsWithChest = 0;
        int chests = 0;
        int carriesSlot = 0;    // scheme rolled DOES have a chests slot
        int slotResolved = 0;   // ...and it resolved to at least one loot table

        Map<Integer, int[]> byFloor = new TreeMap<>();    // [rooms, roomsWithChest, chests]
        Map<Integer, int[]> byMinSide = new TreeMap<>();  // [rooms, roomsWithChest]
        Map<String, int[]> bySize = new TreeMap<>();      // dungeon size tier
        Map<String, Integer> byTable = new TreeMap<>();
        Map<Integer, Map<String, Integer>> tablesByFloor = new TreeMap<>();

        for (DungeonSize size : new DungeonSize[] {DungeonSize.MEDIUM, DungeonSize.LARGE}) {
            for (int i = 0; i < DUNGEONS; i++) {
                // Spread, not sequential: RandomSource.create(0,1,2,...) correlates hard on its
                // FIRST draw, and the first draw here is the count roll -- the very thing measured.
                long seed = 0xD2_C4E5_0001L + i * 7919L;
                Optional<DungeonLayout> planned = new DungeonStackPlanner(
                        seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                        .withSize(size).plan();
                if (planned.isEmpty()) {
                    continue;
                }
                for (FloorLayout floor : planned.get().getFloors()) {
                    int floorIndex = floor.getFloorIndex();
                    for (RoomData room : floor.getRooms()) {
                        // NORMAL only, matching SpawnerIncidenceProbe: START/END are template-covered
                        // and TERMINAL is one room per dungeon, whose incidence would distort a rate.
                        if (room.getRole() != RoomRole.NORMAL) {
                            continue;
                        }
                        rooms++;

                        // Seeded from the room id, chunk-independently -- the same roll the real
                        // render makes rather than a fresh one.
                        RoomPlacements out = new RoomPlacements();
                        new BasicRoomGenerator().withMotifConfig(config).build(
                                room, floor.getFloorY(), floorIndex, DungeonMotif.CLASSIC,
                                RandomSource.create(seed + room.getId()), out);

                        int placed = 0;
                        for (BlockPlacement p : out.getBlocks()) {
                            BlockEntityData nbt = p.getBlockEntityNbt();
                            if (nbt == null || !CHEST_ENTITY.equals(nbt.getType())) {
                                continue;
                            }
                            placed++;
                            assertTrue(nbt.getData().containsKey(LOOT_TABLE),
                                    "a chest was emitted with no LootTable at " + p
                                            + ". An empty chest reads as a looted one, so the"
                                            + " decision was to place NOTHING instead");
                            assertTrue(!"0".equals(nbt.getData().get(LOOT_TABLE_SEED)),
                                    "LootTableSeed 0 means 'roll fresh on open' to vanilla, so this"
                                            + " chest is re-rollable by reloading the save");
                            String table = nbt.getData().get(LOOT_TABLE);
                            byTable.merge(table, 1, Integer::sum);
                            tablesByFloor.computeIfAbsent(floorIndex, k -> new TreeMap<>())
                                    .merge(table, 1, Integer::sum);
                        }

                        chests += placed;
                        int minSide = Math.min(room.getWidth(), room.getDepth());
                        byFloor.computeIfAbsent(floorIndex, k -> new int[3])[0]++;
                        byMinSide.computeIfAbsent(minSide, k -> new int[2])[0]++;
                        bySize.computeIfAbsent(size.name(), k -> new int[2])[0]++;
                        if (placed > 0) {
                            roomsWithChest++;
                            byFloor.get(floorIndex)[1]++;
                            byMinSide.get(minSide)[1]++;
                            bySize.get(size.name())[1]++;
                        }
                        byFloor.get(floorIndex)[2] += placed;

                        Optional<ChestConfig> slot = slotFor(config, room, floorIndex, seed);
                        if (slot.isPresent()) {
                            carriesSlot++;
                            if (!slot.get().resolvedAgainst(config.chestBandFor(floorIndex))
                                    .declaredLootTables().isEmpty()) {
                                slotResolved++;
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("%nCHEST INCIDENCE -- %d dungeons per size tier, %d procedural rooms%n",
                DUNGEONS, rooms);
        System.out.printf("  rooms with >=1 chest : %d (%.1f%%)%n",
                roomsWithChest, pct(roomsWithChest, rooms));
        System.out.printf("  chests placed        : %d (%.2f per room, %.1f per dungeon)%n",
                chests, rooms == 0 ? 0 : (double) chests / rooms,
                (double) chests / (DUNGEONS * 2));

        // Three stages, reported separately, or the headline is unreadable: a room gets no chest
        // because its scheme has no slot, because the slot resolved to no loot table on this floor,
        // or because the 0..1 count roll came up 0. Only what is left over after all three is cell
        // exhaustion -- the one outcome that would be a bug rather than a design.
        double expected = 50.0D;  // minCount 0 / maxCount 1, uniform inclusive
        double placedOfResolved = pct(roomsWithChest, slotResolved);
        System.out.printf("%n  stage 1 -- scheme carries a chests slot   : %d (%.1f%% of rooms)%n",
                carriesSlot, pct(carriesSlot, rooms));
        System.out.printf("  stage 2 -- slot resolved to >=1 table     : %d (%.1f%% of stage 1)%n",
                slotResolved, pct(slotResolved, carriesSlot));
        System.out.printf("  stage 3 -- of those, actually placed      : %d (%.1f%%, expected %.1f%%"
                        + " from minCount 0 / maxCount 1)%n",
                roomsWithChest, placedOfResolved, expected);
        System.out.printf("  => cell exhaustion is at most             : %.1f pp (~%d rooms)%n",
                Math.max(0.0D, expected - placedOfResolved),
                Math.round(slotResolved * Math.max(0.0D, expected - placedOfResolved) / 100.0D));

        System.out.printf("%n  by floor (incidence should be FLAT; the TABLE MIX should not be --"
                + " the bands are the point)%n");
        byFloor.forEach((floorIndex, t) -> {
            System.out.printf("    floor %-2d  rooms %-5d with chest %-5d (%.1f%%)  chests %d%n",
                    floorIndex, t[0], t[1], pct(t[1], t[0]), t[2]);
            Map<String, Integer> mix = tablesByFloor.get(floorIndex);
            if (mix != null) {
                int total = mix.values().stream().mapToInt(Integer::intValue).sum();
                mix.forEach((table, n) -> System.out.printf(
                        "                %-38s %-5d (%.1f%%)%n", table, n, pct(n, total)));
            }
        });

        System.out.printf("%n  by loot table, all floors%n");
        int allTables = byTable.values().stream().mapToInt(Integer::intValue).sum();
        byTable.forEach((table, n) -> System.out.printf(
                "    %-40s %-5d (%.1f%%)%n", table, n, pct(n, allTables)));

        System.out.printf("%n  by room min side (all three carriers are gated at minSize 9)%n");
        byMinSide.forEach((side, t) -> System.out.printf(
                "    %-3d  rooms %-5d with chest %-5d (%.1f%%)%n",
                side, t[0], t[1], pct(t[1], t[0])));

        System.out.printf("%n  by dungeon size%n");
        bySize.forEach((name, t) -> System.out.printf(
                "    %-7s rooms %-5d with chest %-5d (%.1f%%)%n",
                name, t[0], t[1], pct(t[1], t[0])));
        System.out.println();

        assertTrue(rooms > 0, "no procedural rooms were built, so this probe measured nothing");
        assertTrue(chests > 0,
                "not one chest was placed across " + rooms + " rooms. Either the chests slot stopped"
                        + " being shipped, or every candidate cell is claimed before the chest runs,"
                        + " or no floor resolved a loot band -- all three are real regressions");
        // A table nothing draws from is authored loot no player will ever see, and it fails
        // silently: the dungeon still generates, still has chests, and simply never opens that tier.
        for (String table : shippedTables(config)) {
            assertTrue(byTable.containsKey(table),
                    "the shipped loot table " + table + " was never drawn across " + chests
                            + " chests. Its band is authored but unreachable -- either no dungeon"
                            + " goes deep enough to reach the band's minFloorIndex, or its weight"
                            + " is small enough to be effectively zero");
        }
    }

    /** Every loot table any shipped band can offer, so an unreachable one is a failure not a shrug. */
    private static Set<String> shippedTables(MotifConfig config) {
        Set<String> tables = new TreeSet<>();
        for (int floorIndex = 0; floorIndex < 16; floorIndex++) {
            config.chestBandFor(floorIndex).ifPresent(band -> band.lootTables()
                    .forEach(entry -> tables.add(entry.lootTable())));
        }
        return tables;
    }

    /**
     * The chests slot the scheme this room rolled carries, used to separate "no slot" from "a slot
     * that resolved to nothing" from "nothing fit".
     *
     * <p>Re-rolls the scheme off the same seed the build used, which is sound because the selector
     * is a pure function of (schemes, dimensions, floor, random) and the random is recreated
     * identically. Worth stating: a shared stream would make this re-roll silently disagree with the
     * one inside the generator.</p>
     */
    private static Optional<ChestConfig> slotFor(MotifConfig config, RoomData room, int floorIndex,
                                                 long seed) {
        return RoomSchemeSelector
                .select(config.schemes(), room.getWidth(), room.getDepth(), room.getHeight(),
                        floorIndex, RandomSource.create(seed + room.getId()))
                .chestsFor(room.getWidth(), room.getDepth(), room.getHeight());
    }

    private static double pct(int part, int whole) {
        return whole == 0 ? 0.0D : 100.0D * part / whole;
    }
}
