/* Diagnostic (Jul 22): is the maze deterministic for a fixed seed? If two plans
 * with the same seed produce different corridor cells, the world render (a later
 * plan) will diverge from the logged plan — the "corridor through a room". */
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
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MazeDeterminismTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static DungeonLayout plan(long seed) {
        return new DungeonStackPlanner(seed, new Coords(0, 0, 0), 96, "classic", new TemplateCatalog())
                .withSize(DungeonSize.LARGE).withFloorCount(4)
                .plan().orElseThrow();
    }

    private static Set<String> corridorCells(DungeonLayout layout, int floor) {
        Set<String> out = new TreeSet<>();
        FloorLayout f = layout.getFloors().get(floor);
        for (CorridorData c : f.getCorridors()) {
            for (Coords2D cell : c.getCells()) {
                out.add(cell.getX() + "," + cell.getY());
            }
        }
        return out;
    }

    @Test
    void sameSeedProducesSameCorridorCells() {
        long seed = 0xABCDEFL;
        DungeonLayout a = plan(seed);
        DungeonLayout b = plan(seed);
        for (int f = 0; f < a.getFloors().size(); f++) {
            Set<String> ca = corridorCells(a, f);
            Set<String> cb = corridorCells(b, f);
            assertEquals(ca, cb,
                    "floor " + f + ": same seed produced DIFFERENT corridor cells -> maze is non-deterministic"
                            + " (a=" + ca.size() + " cells, b=" + cb.size() + " cells, shared="
                            + intersect(ca, cb) + ")");
        }
    }

    private static int intersect(Set<String> a, Set<String> b) {
        int n = 0;
        for (String s : a) if (b.contains(s)) n++;
        return n;
    }
}
