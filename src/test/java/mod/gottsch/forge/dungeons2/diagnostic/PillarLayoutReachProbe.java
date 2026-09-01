package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.Coords2D;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.ColonnadePillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.GridPillarPatternProvider;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.pillar.QuartetPillarPatternProvider;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many of the rooms the planner actually builds each pillar layout can draw in.
 *
 * <h2>Why this is not the scheme-incidence number</h2>
 * <p>{@code SchemeIncidenceTest} counts rooms whose {@code pillars} slot survives its size gate.
 * For {@code grid} those are the same thing &mdash; if the gate passes, columns appear. For
 * {@code colonnade} they are <strong>not</strong>: the layout also declines a room that is not
 * elongated, which happens after the gate and is invisible to it. So a colonnade scheme can report
 * healthy incidence while drawing nothing in most of the rooms it wins.</p>
 *
 * <p>That gap is exactly the kind of thing this theme has been caught by before, which is why the
 * reach is measured against real planner output rather than reasoned about.</p>
 */
class PillarLayoutReachProbe {

    private static final int DUNGEONS = 60;
    private static final Coords ANCHOR = new Coords(0, 0, 0);
    private static final int SURFACE_Y = 72;

    @Test
    void howOftenEachLayoutCanDraw() {
        GridPillarPatternProvider grid = new GridPillarPatternProvider(4, 2);
        ColonnadePillarPatternProvider colonnade = new ColonnadePillarPatternProvider(4, 2);
        // Authored at the SHIPPED spacing (6), deliberately wider than the grid's 4 -- spacing is
        // the lever that keeps a quartet off the grid's footprint, so measuring it at the grid's
        // own spacing would measure a layout nobody ships.
        QuartetPillarPatternProvider quartet = new QuartetPillarPatternProvider(6, 2);

        int rooms = 0;
        int eligible = 0;      // rooms passing the shipped gate (minSize 9, minHeight 7)
        int gridDraws = 0;
        int colonnadeDraws = 0;
        // Rooms where the grid already draws EXACTLY the colonnade. This is the "check what the
        // list already draws before adding geometry" test, applied after the fact: in a narrow room
        // the grid's cross axis only fits two rows anyway, so the two layouts converge. Measured at
        // 26.7% -- real overlap, but the colonnade is distinct in the other three quarters.
        int quartetDraws = 0;
        int quartetSameAsGrid = 0;
        int identical = 0;

        for (int i = 0; i < DUNGEONS; i++) {
            long seed = 0xD2_0BADC0DEL + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    seed, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM).plan();
            if (planned.isEmpty()) {
                continue;
            }
            for (FloorLayout floor : planned.get().getFloors()) {
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() != RoomRole.NORMAL) {
                        continue;
                    }
                    rooms++;
                    int minSide = Math.min(room.getWidth(), room.getDepth());
                    if (minSide < 9 || room.getHeight() < 7) {
                        continue;
                    }
                    eligible++;
                    int iw = room.getWidth() - 2;
                    int id = room.getDepth() - 2;
                    if (!grid.footprint(iw, id).isEmpty()) {
                        gridDraws++;
                    }
                    if (!quartet.footprint(iw, id).isEmpty()) {
                        quartetDraws++;
                        if (keys(quartet.footprint(iw, id)).equals(keys(grid.footprint(iw, id)))) {
                            quartetSameAsGrid++;
                        }
                    }
                    if (!colonnade.footprint(iw, id).isEmpty()) {
                        colonnadeDraws++;
                        if (keys(grid.footprint(iw, id)).equals(keys(colonnade.footprint(iw, id)))) {
                            identical++;
                        }
                    }
                }
            }
        }

        System.out.printf("%nPillar layout reach over %d MEDIUM dungeons, %d NORMAL rooms%n",
                DUNGEONS, rooms);
        System.out.printf("  eligible (min_size 9, min_height 7)  %d  (%.1f%% of rooms)%n",
                eligible, pct(eligible, rooms));
        System.out.printf("  grid draws in                      %d  (%.1f%% of eligible)%n",
                gridDraws, pct(gridDraws, eligible));
        System.out.printf("  colonnade draws in                 %d  (%.1f%% of eligible)%n",
                colonnadeDraws, pct(colonnadeDraws, eligible));
        System.out.printf("    ...of those, identical to grid   %d  (%.1f%% of colonnade draws)%n",
                identical, pct(identical, colonnadeDraws));
        System.out.printf("  quartet draws in                   %d  (%.1f%% of eligible)%n",
                quartetDraws, pct(quartetDraws, eligible));
        System.out.printf("    ...of those, identical to grid   %d  (%.1f%% of quartet draws)%n",
                quartetSameAsGrid, pct(quartetSameAsGrid, quartetDraws));

        assertTrue(rooms > 50, "need a meaningful sample, got " + rooms);
        // Not a tuning bar -- just the floor below which a layout is not worth shipping as its own
        // scheme, because the scheme would win rooms and draw nothing in nearly all of them.
        assertTrue(pct(colonnadeDraws, eligible) > 5.0,
                "a colonnade scheme would draw in almost none of the rooms it wins: "
                        + pct(colonnadeDraws, eligible) + "% of eligible");
    }

    private static Set<String> keys(Set<Coords2D> cells) {
        Set<String> out = new HashSet<>();
        cells.forEach(c -> out.add(c.getX() + "," + c.getY()));
        return out;
    }

    private static double pct(int n, int of) {
        return of == 0 ? 0 : 100.0 * n / of;
    }
}
