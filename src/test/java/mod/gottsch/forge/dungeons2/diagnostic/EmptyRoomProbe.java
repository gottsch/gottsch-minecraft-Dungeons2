package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.data.BlockPlacement;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.EntityPlacement;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.enums.DungeonMotif;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomPropGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform.BasicPlatformGenerator;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.platform.PlatformPatternSelector;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How many rooms come out with <strong>nothing drawn in them at all</strong>, broken down by size.
 *
 * <h2>Why this is not {@code SchemeIncidenceTest}'s "no decoration drawn" figure</h2>
 * <p>That one asks whether a scheme's <em>slots</em> survived their gates, which reads 0%. It counts
 * a wall slot as drawn even when every course inside it gated out &mdash; the same slot-level
 * measurement gap noted against {@code hasTopCourse} in backlog #22. This walks down to the courses,
 * so a room whose only treatment is a cornice it is too short for counts as what it looks like in
 * game: empty.</p>
 *
 * <p>Exists to answer "where is the emptiness?" with a size band rather than a total, because the
 * answer decides what is worth building: a feature that needs a 9-wide room cannot reach a
 * population that is mostly 5 and 7 wide, however good it is.</p>
 */
class EmptyRoomProbe {

    /** The entrance floor. These cases are about size and weight, not depth. */
    private static final int ENTRANCE_FLOOR = 0;

    private static final int DUNGEONS = 60;

    /** Needed since this probe started running the real generators: they resolve block states. */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void whereAreTheEmptyRooms() {
        MotifConfig config = MotifConfigs.load("classic");

        int rooms = 0;
        int empty = 0;        // not one block drawn anywhere
        int surfaceOnly = 0;  // surfaces dressed, but nothing standing in the room
        int builtNothing = 0; // the scheme CLAIMED furniture and the generators emitted none
        int potsPlaced = 0;   // loot density: pots are the only slot carrying a loot table
        // Which scheme actually wins, overall and inside each size band. SchemeIncidenceTest is
        // @Disabled while classic is cut down, so nothing else reports this.
        Map<String, int[]> bySchemeAndSize = new TreeMap<>();
        Map<Integer, int[]> byMinSide = new TreeMap<>();   // [rooms, surfaceOnly]
        Map<Integer, int[]> byHeight = new TreeMap<>();

        for (int i = 0; i < DUNGEONS; i++) {
            long seed = 0xD2_0BADC0DEL + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    seed, new Coords(0, 0, 0), 72, "classic", new TemplateCatalog())
                    .withSize(DungeonSize.MEDIUM).plan();
            if (planned.isEmpty()) {
                continue;
            }
            RandomSource random = RandomSource.create(seed);
            for (FloorLayout floor : planned.get().getFloors()) {
                for (RoomData room : floor.getRooms()) {
                    if (room.getRole() != RoomRole.NORMAL) {
                        continue;
                    }
                    int w = room.getWidth();
                    int d = room.getDepth();
                    int h = room.getHeight();
                    rooms++;

                    RoomScheme scheme = RoomSchemeSelector.select(config.schemes(), w, d, h, ENTRANCE_FLOOR, random);
                    // [total, at min side 7, at min side 9, at 11+]
                    int minSide = Math.min(w, d);
                    int[] tally = bySchemeAndSize.computeIfAbsent(scheme.name(), k -> new int[4]);
                    tally[0]++;
                    tally[minSide <= 7 ? 1 : (minSide == 9 ? 2 : 3)]++;
                    if (!drawsAnythingVisible(scheme, w, d, h)) {
                        empty++;
                    }
                    boolean furnished = standsInTheRoom(scheme, w, d, h);
                    if (!furnished) {
                        surfaceOnly++;
                    } else {
                        // A SEPARATE RandomSource on purpose: the furniture generators draw from
                        // whatever they are handed, and sharing the selector's stream would shift
                        // every later room's scheme roll -- silently making this measurement
                        // incomparable with the run before it.
                        int[] built = build(scheme, room, floor.getFloorY(),
                                RandomSource.create(seed + rooms));
                        potsPlaced += built[0];
                        // The gap this probe was blind to: a slot can survive its gate and still
                        // emit nothing, because a dais that lands on a doorway approach is dropped
                        // whole and a pot with no eligible cell is skipped.
                        if (built[1] == 0) {
                            builtNothing++;
                        }
                    }
                    byMinSide.computeIfAbsent(Math.min(w, d), k -> new int[2])[0]++;
                    byHeight.computeIfAbsent(h, k -> new int[2])[0]++;
                    if (!furnished) {
                        byMinSide.get(Math.min(w, d))[1]++;
                        byHeight.get(h)[1]++;
                    }
                }
            }
        }

        System.out.printf("%nRooms with NOTHING drawn, over %d MEDIUM dungeons, %d NORMAL rooms%n",
                DUNGEONS, rooms);
        System.out.printf("  not one block drawn anywhere       %d (%.1f%%)%n", empty, 100.0 * empty / rooms);
        System.out.printf("  surfaces dressed, room itself bare  %d (%.1f%%)%n",
                surfaceOnly, 100.0 * surfaceOnly / rooms);
        System.out.printf("  claimed furniture, built none       %d (%.1f%% of all rooms)%n",
                builtNothing, 100.0 * builtNothing / rooms);
        System.out.printf("  loot pots placed                    %d (%.2f per room, %.1f per dungeon)%n%n",
                potsPlaced, (double) potsPlaced / rooms, (double) potsPlaced / DUNGEONS);
        System.out.println("  min(width,depth)   rooms   bare room");
        byMinSide.forEach((k, v) -> System.out.printf("      %2d          %5d   %5d  (%5.1f%%)%n",
                k, v[0], v[1], 100.0 * v[1] / v[0]));
        System.out.println("  height             rooms   bare room");
        byHeight.forEach((k, v) -> System.out.printf("      %2d          %5d   %5d  (%5.1f%%)%n",
                k, v[0], v[1], 100.0 * v[1] / v[0]));

        System.out.printf("%n  scheme                   all rooms      side 7      side 9     side 11+%n");
        final int totalRooms = rooms;
        bySchemeAndSize.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> System.out.printf("  %-20s %5d (%4.1f%%) %5d %5d %5d%n",
                        e.getKey(), e.getValue()[0], 100.0 * e.getValue()[0] / totalRooms,
                        e.getValue()[1], e.getValue()[2], e.getValue()[3]));

        assertTrue(rooms > 50, "need a meaningful sample");
    }

    /**
     * Whether this scheme puts a single block in this room that a player would see. Walks into the
     * wall slot's courses rather than trusting the slot, which is the difference between this and
     * the scheme-incidence number.
     */
    /**
     * Whether anything actually <em>stands in</em> the room -- columns or props -- as opposed to the
     * room's surfaces being dressed around an otherwise empty box. This is the number that matches
     * "I want very few empty rooms": a plinth course round the wall is decoration, but a player
     * walking in still sees a bare floor and open air.
     */
    private static boolean standsInTheRoom(RoomScheme scheme, int w, int d, int h) {
        return scheme.potsFor(w, d, h).isPresent()
                || scheme.pillarsFor(w, d, h)
                        .map(p -> !p.forRoom(w, d, h).patterns().isEmpty())
                        .orElse(false)
                || scheme.platformsFor(w, d, h)
                        .map(p -> !p.forRoom(w, d, h).patterns().isEmpty())
                        .orElse(false);
    }

    /**
     * Whether the furniture slots a scheme claims for this room actually emit something, by running
     * the real generators rather than trusting the gate. The reason to bother: a dais whose
     * footprint touches a doorway approach is dropped whole, and a pot with no eligible cell is
     * skipped -- both after the gate has already passed.
     *
     * <p>Pillars are deliberately not counted here; {@code PillarLayoutReachProbe} measures their
     * reach in more detail. The {@code occupied} set is empty, so a scheme carrying floor-level
     * projecting wall trim (today only {@code pilastered_hall}) reads as a slight over-estimate --
     * the trim would take a few of the cells its pots could otherwise use.</p>
     */
    private static int[] build(RoomScheme scheme, RoomData room, int floorY, RandomSource random) {
        int w = room.getWidth();
        int d = room.getDepth();
        int h = room.getHeight();

        List<EntityPlacement> entities = new ArrayList<>();
        scheme.potsFor(w, d, h).ifPresent(pots ->
                RoomPropGenerator.placePots(room, floorY, pots, random, entities));

        List<BlockPlacement> blocks = new ArrayList<>();
        new BasicPlatformGenerator()
                .withPlatformLayouts(PlatformPatternSelector.layoutsFor(scheme.platformsFor(w, d, h), w, d, h))
                .build(room, floorY, DungeonMotif.CLASSIC, random, blocks);

        // Fall back to the claim for the one slot not run here.
        boolean pillars = scheme.pillarsFor(w, d, h)
                .map(p -> !p.forRoom(w, d, h).patterns().isEmpty())
                .orElse(false);

        int anything = (!entities.isEmpty() || !blocks.isEmpty() || pillars) ? 1 : 0;
        return new int[] {entities.size(), anything};
    }

    private static boolean drawsAnythingVisible(RoomScheme scheme, int w, int d, int h) {
        if (scheme.floorFor(w, d, h).isPresent()
                || scheme.ceilingFor(w, d, h).map(c -> !c.forRoom(w, d, h).patterns().isEmpty()).orElse(false)
                || scheme.potsFor(w, d, h).isPresent()
                || scheme.pillarsFor(w, d, h).map(p -> !p.forRoom(w, d, h).patterns().isEmpty()).orElse(false)
                // platforms was missing here: a scheme whose only content is a dais would have been
                // counted as drawing nothing at all. It never showed because every classic scheme
                // also carries a wall slot, but the omission is a real one.
                || scheme.platformsFor(w, d, h).map(p -> !p.forRoom(w, d, h).patterns().isEmpty()).orElse(false)) {
            return true;
        }
        return scheme.wallFor(w, d, h)
                .map(wall -> wall.forRoom(w, d, h))
                .map(WallPatternEntry::patterns)
                .map(patterns -> !patterns.isEmpty())
                .orElse(false);
    }

}
