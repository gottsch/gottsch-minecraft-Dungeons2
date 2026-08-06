package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.BasicCorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Diagnostic: which rows of a corridor's wall column can a player actually see?
 *
 * <p>Reported in game: the {@code left_/right_large_stone_brick} course that all three
 * {@code classic} corridor styles author at {@code bottom}/0 is completely absent from corridors.
 * It is not absent &mdash; {@code emitWallColumn} runs {@code yOffset} from <strong>0</strong>, so
 * {@code bottom}/0 resolves to world Y {@code floorY}, the same row as the corridor's own floor
 * plane. A room's wall is different: {@code WallSurface.emit} writes at {@code floorY + 1 + v}, so
 * the same authored course starts one row higher and is visible.</p>
 *
 * <p>"Visible" here means the cell has at least one orthogonally adjacent air cell &mdash; the only
 * way a block face can be seen from inside the passage. Deliberately measured rather than reasoned:
 * the claim being tested is a geometric one about the finished world, and the existing unit test
 * ({@code CorridorCoursesTest#aBottomAnchoredCourseLandsOnTheFloorRow}) pins the row without ever
 * asking whether that row is exposed.</p>
 *
 * <p>Prints; asserts nothing. Run with
 * {@code ./gradlew test --tests "*LargeBrickCourseProbe" -i}.</p>
 *
 * <p><strong>Note for anyone extending this:</strong> do not try to count
 * {@code dungeonblocks:*} blocks here. A bare {@code Bootstrap.bootStrap()} registers no mod
 * blocks, so those ids resolve to null, the whole course degrades, and the count is a flat zero
 * that says nothing about the game. Everything below is deliberately vanilla-only.</p>
 */
class CorridorWallRowVisibilityProbe {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) << 26 | ((long) z & 0x3FFFFFF);
    }

    @Test
    void whichCorridorWallRowsAreVisible() {
        var motifConfig = MotifConfigs.load("classic");
        int dungeons = 4;

        // yOffset -> [cells at that row, cells with at least one air neighbour]
        Map<Integer, int[]> byRow = new TreeMap<>();

        for (long seed = 0; seed < dungeons; seed++) {
            DungeonLayout layout = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    .withCorridorWidth(3)
                    .withCorridorStyles(DungeonStructure.corridorStyleWeights(motifConfig.corridor()))
                    .plan().orElseThrow();

            for (FloorLayout floor : layout.getFloors()) {
                BasicCorridorGenerator gen = new BasicCorridorGenerator().withMotifConfig(motifConfig);
                for (CorridorData corridor : floor.getCorridors()) {
                    List<BlockPlacement> out = new ArrayList<>();
                    gen.build(corridor, floor.getFloorY(), DungeonMotif.CLASSIC,
                            RandomSource.create(seed), out);

                    Map<Long, String> world = new HashMap<>();
                    Set<Long> air = new HashSet<>();
                    for (BlockPlacement bp : out) {
                        long k = key(bp.getX(), bp.getY(), bp.getZ());
                        world.put(k, bp.getBlockId());
                        if ("minecraft:air".equals(bp.getBlockId())) {
                            air.add(k);
                        } else {
                            air.remove(k);
                        }
                    }

                    for (BlockPlacement bp : out) {
                        if ("minecraft:air".equals(bp.getBlockId())) {
                            continue;
                        }
                        // Only wall COLUMNS carry courses. A column whose floorY+1 cell is air is
                        // the passage itself, and its floorY cell is the walking surface -- counting
                        // that as a visible "row 0" is what made the first run of this probe read
                        // 50% instead of 0.
                        if (air.contains(key(bp.getX(), floor.getFloorY() + 1, bp.getZ()))) {
                            continue;
                        }
                        int yOffset = bp.getY() - floor.getFloorY();
                        int[] tally = byRow.computeIfAbsent(yOffset, r -> new int[2]);
                        tally[0]++;
                        boolean exposed =
                                air.contains(key(bp.getX() + 1, bp.getY(), bp.getZ()))
                                        || air.contains(key(bp.getX() - 1, bp.getY(), bp.getZ()))
                                        || air.contains(key(bp.getX(), bp.getY(), bp.getZ() + 1))
                                        || air.contains(key(bp.getX(), bp.getY(), bp.getZ() - 1))
                                        || air.contains(key(bp.getX(), bp.getY() + 1, bp.getZ()))
                                        || air.contains(key(bp.getX(), bp.getY() - 1, bp.getZ()));
                        if (exposed) {
                            tally[1]++;
                        }
                    }
                }
            }
        }

        System.out.printf("%nCorridor wall rows over %d MEDIUM dungeons%n", dungeons);
        System.out.println("  yOffset  (world Y = floorY + yOffset)   solid cells   exposed to air");
        for (Map.Entry<Integer, int[]> row : byRow.entrySet()) {
            int[] t = row.getValue();
            System.out.printf("    %2d %s %10d %12d  (%5.1f%%)%n",
                    row.getKey(),
                    row.getKey() == 0 ? "<- buried: NOT addressable" : "                          ",
                    t[0], t[1], t[0] == 0 ? 0.0 : 100.0 * t[1] / t[0]);
        }
    }
}
