package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.world.structure.DungeonStructure;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Of the wall faces a player can see <em>from inside a corridor</em>, how many are the corridor's
 * own to style? Step 5 of {@code CorridorGeometryAndCoursesPlan-Aug03} §5, never run until now.
 *
 * <h2>Why it exists</h2>
 * <p>The plan opened with this number: 51.2% corridor-owned, 44.7% the room behind the wall, 4.2%
 * doors &mdash; the argument for building corridor courses at all, and the reason the default was
 * set to the motif's baseline room wall rather than to nothing. Steps 1&ndash;4 have all shipped
 * since (variable height, arches, per-floor styles, courses), and the emit order changed underneath
 * them so a room now keeps its own perimeter. Every one of those moves the number.</p>
 *
 * <h2>Read the comparison carefully</h2>
 * <p><strong>The 51.2% has no recorded method.</strong> It predates this probe and no code that
 * produced it survives, so what it counted &mdash; which rows, whether corners, what it did with
 * untouched stone &mdash; is unknown. Treat it as the shape of an answer, not a baseline this can
 * be differenced against. What is defensible is the comparison <em>within</em> this run: the two
 * {@link FloorPlanExporter.PieceOrder}s are measured by one method, and that difference is real.</p>
 *
 * <p>Prints; asserts only that the sample is meaningful. Run with
 * {@code ./gradlew test --tests "*CorridorWallOwnershipProbe" -i}.</p>
 */
class CorridorWallOwnershipProbe {

    private static final int DUNGEONS = 40;
    /** What {@code generation_config/default.json} ships. */
    private static final int SHIPPED_CORRIDOR_WIDTH = 3;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void howMuchOfACorridorsWallIsItsOwn() {
        MotifConfig motifConfig = MotifConfigs.load("classic");

        FloorPlanExporter.Faces emit = new FloorPlanExporter.Faces();
        FloorPlanExporter.Faces roomsFirst = new FloorPlanExporter.Faces();
        int dungeons = 0;

        for (int i = 0; i < DUNGEONS; i++) {
            long seed = 0xD2_0BADC0DEL + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM)
                    .withCorridorWidth(SHIPPED_CORRIDOR_WIDTH)
                    .withCorridorStyles(DungeonStructure.corridorStyleWeights(motifConfig.corridor()))
                    .plan();
            if (planned.isEmpty()) {
                continue;
            }
            dungeons++;
            emit.add(new FloorPlanExporter(planned.get(), motifConfig)
                    .withOrder(FloorPlanExporter.PieceOrder.EMIT)
                    .corridorWallOwnership());
            // The pre-2026-08-03 order, where the corridor overwrote the room's wall. Kept as the
            // counterfactual: without it "corridor-owned" has no scale to be read against.
            roomsFirst.add(new FloorPlanExporter(planned.get(), motifConfig)
                    .withOrder(FloorPlanExporter.PieceOrder.ROOMS_FIRST)
                    .corridorWallOwnership());
        }

        System.out.printf("%nCorridor-visible wall ownership, %d MEDIUM dungeons, motif classic,%n"
                + "corridor width %d, per-floor styles as shipped%n%n",
                dungeons, SHIPPED_CORRIDOR_WIDTH);
        System.out.println("EMIT (production: corridors, then rooms, then doors)");
        System.out.print(emit.format());
        System.out.printf("  adjacent cells no piece wrote: %d%n", emit.unwritten);
        System.out.println();
        System.out.println("ROOMS_FIRST (the pre-Aug-03 counterfactual)");
        System.out.print(roomsFirst.format());

        assertTrue(emit.total() > 10_000, "need a meaningful sample of faces");
    }
}
