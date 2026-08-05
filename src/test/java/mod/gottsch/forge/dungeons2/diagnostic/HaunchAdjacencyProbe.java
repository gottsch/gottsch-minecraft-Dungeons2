package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.CorridorData;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.BlockStateCodec;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.corridor.BasicCorridorGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostic: where do two arch haunches end up beside each other, and in what arrangement?
 *
 * <p>Reported in game as "stairs stacked in front of stairs". A haunch is a stair at the ceiling
 * row leaning into its wall; two of them adjacent is only correct when they run <em>along</em> a
 * wall (a chamfer) or meet at a corner. Two in a line <em>along the facing axis</em> — one behind
 * the other, both leaning the same way — is a stair with another stair in front of it, which is the
 * shape being reported.</p>
 *
 * <p>Prints; asserts nothing. Run with
 * {@code ./gradlew test --tests "*HaunchAdjacencyProbe" -i}.</p>
 */
class HaunchAdjacencyProbe {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private record Haunch(int x, int y, int z, String facing, String shape) {}

    @Test
    void classifyAdjacentHaunches() {
        var motifConfig = MotifConfigs.load("classic");
        Map<String, Integer> arrangements = new TreeMap<>();
        Map<String, Integer> widthOfInFront = new TreeMap<>();
        int totalHaunches = 0;

        for (long seed = 0; seed < 12; seed++) {
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

                    Map<Long, Haunch> byPos = new HashMap<>();
                    for (BlockPlacement bp : out) {
                        BlockState state = BlockStateCodec.resolve(bp);
                        if (!state.getBlock().getDescriptionId().contains("stairs")) {
                            continue;
                        }
                        String facing = String.valueOf(state.getValues().entrySet().stream()
                                .filter(e -> e.getKey().getName().equals("facing"))
                                .map(e -> e.getValue().toString()).findFirst().orElse("?"));
                        String shape = String.valueOf(state.getValues().entrySet().stream()
                                .filter(e -> e.getKey().getName().equals("shape"))
                                .map(e -> e.getValue().toString()).findFirst().orElse("?"));
                        byPos.put(key(bp.getX(), bp.getY(), bp.getZ()),
                                new Haunch(bp.getX(), bp.getY(), bp.getZ(), facing, shape));
                    }
                    totalHaunches += byPos.size();

                    java.util.Set<Coords2D> cells = new java.util.HashSet<>(corridor.getCells());
                    for (Haunch h : byPos.values()) {
                        int[] step = step(h.facing());
                        // The cell this haunch leans AWAY from -- i.e. directly in front of its
                        // open face. A haunch there, facing the same way, is one stair immediately
                        // in front of another.
                        Haunch inFront = byPos.get(key(h.x() - step[0], h.y(), h.z() - step[1]));
                        Haunch behind = byPos.get(key(h.x() + step[0], h.y(), h.z() + step[1]));
                        if (inFront != null && inFront.facing().equals(h.facing())) {
                            arrangements.merge("IN FRONT, same facing (" + h.facing() + ")", 1, Integer::sum);
                            widthOfInFront.merge("shape " + h.shape() + " -> " + inFront.shape(), 1, Integer::sum);
                        } else if (inFront != null) {
                            arrangements.merge("in front, opposed facing (2-wide pinch)", 1, Integer::sum);
                        }
                        if (behind != null) {
                            arrangements.merge("behind (into its own wall!)", 1, Integer::sum);
                        }
                        // Along the wall run: the normal chamfer.
                        int[] left = step(rotate(h.facing()));
                        if (byPos.containsKey(key(h.x() + left[0], h.y(), h.z() + left[1]))) {
                            arrangements.merge("alongside (normal chamfer run)", 1, Integer::sum);
                        }
                        if (!cells.contains(new Coords2D(h.x(), h.z()))) {
                            arrangements.merge("NOT IN A CORRIDOR CELL", 1, Integer::sum);
                        }
                    }
                }
            }
        }

        System.out.println("=== haunch adjacency over 12 MEDIUM dungeons ===");
        final int total = totalHaunches;
        System.out.println("total haunches: " + total);
        arrangements.forEach((k, v) ->
                System.out.printf("  %-42s %6d  (%.1f%% of haunches)%n", k, v, 100.0 * v / total));
        System.out.println("  -- shapes of the 'IN FRONT, same facing' pairs --");
        widthOfInFront.forEach((k, v) -> System.out.printf("     %-40s %6d%n", k, v));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0xFFFFF) << 22 | ((long) z & 0x3FFFFF);
    }

    /** Unit step in the direction a haunch faces (its solid side, i.e. toward its wall). */
    private static int[] step(String facing) {
        return switch (facing) {
            case "north" -> new int[]{0, -1};
            case "south" -> new int[]{0, 1};
            case "west" -> new int[]{-1, 0};
            case "east" -> new int[]{1, 0};
            default -> new int[]{0, 0};
        };
    }

    private static String rotate(String facing) {
        return switch (facing) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> facing;
        };
    }
}
