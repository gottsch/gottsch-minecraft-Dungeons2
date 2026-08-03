package mod.gottsch.forge.dungeons2.diagnostic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the floor-plan diagnostic honest. It is a tool, not shipped behaviour, so this checks the
 * two things that would make it quietly lie rather than every field it writes:
 *
 * <ul>
 *     <li>the JSON parses and carries a layer per rendered Y with attribution on every cell;</li>
 *     <li>contested cells are actually found &mdash; the shared-wall design guarantees they exist,
 *         so zero of them means the attribution wiring broke, not that the dungeon got tidier.</li>
 * </ul>
 */
class FloorPlanExporterTest {

    private static final long SEED = 0xD2_0BADC0DEL;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject export() {
        DungeonLayout layout = new DungeonStackPlanner(
                SEED, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                .withSize(DungeonSize.SMALL)
                .plan()
                .orElseThrow(() -> new AssertionError("planner produced no layout for the fixed seed"));
        MotifConfig config = MotifConfigs.load("classic");
        return new Gson().fromJson(new FloorPlanExporter(layout, config).toJson(), JsonObject.class);
    }

    @Test
    void exportsAttributedCellsForEveryFloor() {
        JsonObject model = export();
        JsonArray palette = model.getAsJsonArray("palette");
        assertFalse(palette.isEmpty(), "a rendered dungeon must contribute blocks to the palette");

        JsonArray floors = model.getAsJsonArray("floors");
        assertFalse(floors.isEmpty(), "layout should have at least one floor");

        for (int i = 0; i < floors.size(); i++) {
            JsonObject floor = floors.get(i).getAsJsonObject();
            String where = "floor " + floor.get("index");
            assertNotNull(floor.get("grid"), where + " should carry its maze grid");

            JsonArray owners = floor.getAsJsonArray("owners");
            assertFalse(owners.isEmpty(), where + " rendered no pieces");

            JsonArray layers = floor.getAsJsonArray("layers");
            assertFalse(layers.isEmpty(), where + " rendered no blocks");
            for (int j = 0; j < layers.size(); j++) {
                JsonArray d = layers.get(j).getAsJsonObject().getAsJsonArray("d");
                assertEquals(0, d.size() % 5,
                        where + " layer data must be five ints per cell (x, z, block, owner, writes)");
                for (int k = 0; k < d.size(); k += 5) {
                    int block = d.get(k + 2).getAsInt();
                    int owner = d.get(k + 3).getAsInt();
                    assertTrue(block >= 0 && block < palette.size(), where + " bad palette index");
                    assertTrue(owner >= 0 && owner < owners.size(), where + " bad owner index");
                }
            }
        }
    }

    /**
     * Rooms share walls by design and the last piece rendered wins the column, so a real layout
     * always has cells written more than once. Finding none would mean the exporter had stopped
     * recording losing writes -- which is precisely the signal the viewer exists to show.
     */
    @Test
    void findsContestedCells() {
        JsonObject model = export();
        int contested = 0;
        JsonArray floors = model.getAsJsonArray("floors");
        for (int i = 0; i < floors.size(); i++) {
            JsonArray layers = floors.get(i).getAsJsonObject().getAsJsonArray("layers");
            for (int j = 0; j < layers.size(); j++) {
                contested += layers.get(j).getAsJsonObject().getAsJsonArray("contested").size();
            }
        }
        assertTrue(contested > 0,
                "shared walls mean some cell is always written twice; found none, so attribution is broken");
    }
}
