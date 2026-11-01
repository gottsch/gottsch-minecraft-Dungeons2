package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import mod.gottsch.forge.dungeons2.core.config.HangingConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomPlacements;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.diagnostic.MotifConfigs;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * <strong>How often a rolled {@code hangings} option actually hangs something</strong>, per scheme,
 * through the real room generator on planned dungeons. A measurement: it prints and passes.
 *
 * <p>For a room whose option rolled and placed nothing, the refusal is classified from the middle
 * column: what closes its air run, and how long the run is.</p>
 */
@Tag("probe")
class HangingIncidenceProbe {

    private static final int DUNGEONS = 40;
    private static final Set<String> FIXTURES = Set.of("dungeonblocks:chandelier",
            "dungeonblocks:meat_hook", "dungeonblocks:censer", "dungeonblocks:gibbet");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void howOftenDoesARolledHangingHang() {
        MotifConfig classic = MotifConfigs.load("classic");
        // scheme -> [rooms, option rolled, rooms with a fixture]
        Map<String, int[]> byScheme = new TreeMap<>();
        Map<String, Integer> refusals = new TreeMap<>();
        Map<String, Integer> fixtures = new TreeMap<>();

        for (DungeonSize size : new DungeonSize[] {DungeonSize.MEDIUM, DungeonSize.LARGE}) {
            for (int i = 0; i < DUNGEONS; i++) {
                long seed = 0xD2_4A46_0001L + i * 7919L;
                Optional<DungeonLayout> planned = new DungeonStackPlanner(
                        seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                        .withSize(size).plan();
                if (planned.isEmpty()) {
                    continue;
                }
                for (FloorLayout floor : planned.get().getFloors()) {
                    int floorIndex = floor.getFloorIndex();
                    MotifConfig config = classic.forFloor(floorIndex);
                    for (RoomData room : floor.getRooms()) {
                        if (room.getRole() != RoomRole.NORMAL) {
                            continue;
                        }
                        long roomSeed = seed + room.getId();
                        BasicRoomGenerator gen = new BasicRoomGenerator().withMotifConfig(config);
                        RoomScheme scheme = gen.selectScheme(room, floorIndex, RandomSource.create(roomSeed));
                        Optional<HangingConfig> hangings = scheme.hangingsFor(
                                room.getWidth(), room.getDepth(), room.getHeight());
                        int[] row = byScheme.computeIfAbsent(scheme.name(), k -> new int[3]);
                        row[0]++;
                        if (hangings.isEmpty()) {
                            continue;
                        }
                        row[1]++;

                        RoomPlacements out = new RoomPlacements();
                        gen.build(room, floor.getFloorY(), floorIndex, DungeonMotif.CLASSIC,
                                RandomSource.create(roomSeed), out);
                        int hung = 0;
                        for (BlockPlacement p : out.getBlocks()) {
                            if (FIXTURES.contains(p.getBlockId())) {
                                hung++;
                                fixtures.merge(p.getBlockId(), 1, Integer::sum);
                            }
                        }
                        if (hung > 0) {
                            row[2]++;
                            continue;
                        }
                        refusals.merge(scheme.name() + " h" + room.getHeight() + " "
                                + classify(room, floor.getFloorY(), out), 1, Integer::sum);
                    }
                }
            }
        }

        System.out.printf("%n=== hangings: scheme -> rooms / option rolled / hung ===%n");
        byScheme.forEach((name, r) -> {
            if (r[1] > 0) {
                System.out.printf("  %-18s %5d %5d %5d%n", name, r[0], r[1], r[2]);
            }
        });
        System.out.println("=== fixtures placed ===");
        fixtures.forEach((k, v) -> System.out.printf("  %-28s %d%n", k, v));
        System.out.println("=== rolled but placed nothing: middle column ===");
        refusals.forEach((k, v) -> System.out.printf("  %-70s %d%n", k, v));
    }

    /** What the middle column looks like, in the terms the generator judges it by. */
    private static String classify(RoomData room, int floorY, RoomPlacements out) {
        Coords2D mid = new Coords2D(room.getOriginX() + room.getWidth() / 2,
                room.getOriginZ() + room.getDepth() / 2);
        Map<Integer, BlockPlacement> column = new HashMap<>();
        for (BlockPlacement p : out.getBlocks()) {
            if (p.getX() == mid.getX() && p.getZ() == mid.getY() && p.getY() > floorY) {
                column.put(p.getY(), p);
            }
        }
        int y = floorY + 1;
        while (column.containsKey(y) && !"minecraft:air".equals(column.get(y).getBlockId())) {
            y++;
        }
        int bottom = y;
        while (column.containsKey(y) && "minecraft:air".equals(column.get(y).getBlockId())) {
            y++;
        }
        BlockPlacement top = column.get(y);
        return "run=" + (y - bottom) + " from+" + (bottom - floorY) + " closedBy="
                + (top == null ? "NOTHING" : top.getBlockId() + top.getProperties()
                + " hook=" + RoomHangingGenerator.holdsAHook(top));
    }
}
