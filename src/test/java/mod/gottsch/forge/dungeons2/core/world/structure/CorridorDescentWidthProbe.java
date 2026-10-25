package mod.gottsch.forge.dungeons2.core.world.structure;

import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * How wide the corridor is where it sinks (#108). A measurement: it prints and passes.
 *
 * <p>Width here is what a player sees across the corridor at that cell: the unbroken run of
 * corridor cells through it, across the direction of travel. Reported both as the SHORTER of the
 * row and column extents (the planner's rule) and per floor index, since the report was the
 * deepslate band.</p>
 */
@Tag("probe")
class CorridorDescentWidthProbe {

    private static final int DUNGEONS = 40;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void howWideIsACorridorWhereItSinks() {
        Map<Integer, int[]> allByWidth = new TreeMap<>();   // width -> [cells, sunk cells]
        Map<Integer, Integer> unionWidth = new TreeMap<>();
        Map<Integer, int[]> byFloor = new TreeMap<>();      // floor -> [sunk cells, sunk cells width>=... ]
        int sunkCorridors = 0;
        int corridors = 0;
        for (int i = 0; i < DUNGEONS; i++) {
            long seed = 0xD2_DE5C_0001L + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(seed, new Coords(0, 0, 0), 72,
                    "classic", new TemplateCatalog()).withSize(DungeonSize.MEDIUM)
                    // Every corridor tries to sink, at the shipped depth/run/landing.
                    .withCorridorStyles(java.util.List.of(new mod.gottsch.forge.dungeons2.core.data.CorridorStyleWeight(
                            "vaulted", 1, 7, new mod.gottsch.forge.dungeons2.core.data.CorridorDescent(1.0, 2, 4, 2, 1))))
                    .plan();
            if (planned.isEmpty()) continue;
            for (FloorLayout floor : planned.get().getFloors()) {
                Set<Coords2D> floorCells = new HashSet<>();
                for (CorridorData other : floor.getCorridors()) floorCells.addAll(other.getCells());
                for (CorridorData corridor : floor.getCorridors()) {
                    corridors++;
                    if (corridor.isSunk()) sunkCorridors++;
                    Set<Coords2D> cells = new HashSet<>(corridor.getCells());
                    for (Coords2D c : corridor.getCells()) {
                        int width = Math.min(extent(cells, c, 1, 0), extent(cells, c, 0, 1));
                        int[] row = allByWidth.computeIfAbsent(width, k -> new int[2]);
                        row[0]++;
                        if (corridor.depthAt(c) > 0) {
                            row[1]++;
                            int seen = Math.min(extent(floorCells, c, 1, 0), extent(floorCells, c, 0, 1));
                            unionWidth.merge(seen, 1, Integer::sum);
                            byFloor.computeIfAbsent(floor.getFloorIndex(), k -> new int[1])[0]++;
                        }
                    }
                }
            }
        }
        System.out.printf("%n=== corridor width where it sinks, %d MEDIUM dungeons (%d of %d corridors sink) ===%n",
                DUNGEONS, sunkCorridors, corridors);
        System.out.println("  width   cells   sunk");
        allByWidth.forEach((w, r) -> System.out.printf("  %5d %7d %6d%n", w, r[0], r[1]));
        System.out.println("  sunk cells by width measured against EVERY corridor on the floor:");
        unionWidth.forEach((w, n) -> System.out.printf("  %5d %6d%n", w, n));
        System.out.println("  sunk cells by floor index: ");
        byFloor.forEach((f, r) -> System.out.printf("    floor %d: %d%n", f, r[0]));
    }

    private static int extent(Set<Coords2D> cells, Coords2D c, int dx, int dz) {
        int n = 1;
        for (int s = 1; cells.contains(new Coords2D(c.getX() + dx * s, c.getY() + dz * s)); s++) n++;
        for (int s = 1; cells.contains(new Coords2D(c.getX() - dx * s, c.getY() - dz * s)); s++) n++;
        return n;
    }
}
