package mod.gottsch.forge.dungeons2.diagnostic;

import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.room.RoomSchemeSelector;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

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

    private static final int DUNGEONS = 60;

    @Test
    void whereAreTheEmptyRooms() {
        MotifConfig config = MotifConfigs.load("classic");

        int rooms = 0;
        int empty = 0;        // not one block drawn anywhere
        int surfaceOnly = 0;  // surfaces dressed, but nothing standing in the room
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

                    RoomScheme scheme = RoomSchemeSelector.select(config.schemes(), w, d, h, random);
                    if (!drawsAnythingVisible(scheme, w, d, h)) {
                        empty++;
                    }
                    boolean furnished = standsInTheRoom(scheme, w, d, h);
                    if (!furnished) {
                        surfaceOnly++;
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
        System.out.printf("  surfaces dressed, room itself bare  %d (%.1f%%)%n%n",
                surfaceOnly, 100.0 * surfaceOnly / rooms);
        System.out.println("  min(width,depth)   rooms   bare room");
        byMinSide.forEach((k, v) -> System.out.printf("      %2d          %5d   %5d  (%5.1f%%)%n",
                k, v[0], v[1], 100.0 * v[1] / v[0]));
        System.out.println("  height             rooms   bare room");
        byHeight.forEach((k, v) -> System.out.printf("      %2d          %5d   %5d  (%5.1f%%)%n",
                k, v[0], v[1], 100.0 * v[1] / v[0]));

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

    private static boolean drawsAnythingVisible(RoomScheme scheme, int w, int d, int h) {
        if (scheme.floorFor(w, d, h).isPresent()
                || scheme.ceilingFor(w, d, h).map(c -> !c.forRoom(w, d, h).patterns().isEmpty()).orElse(false)
                || scheme.potsFor(w, d, h).isPresent()
                || scheme.pillarsFor(w, d, h).map(p -> !p.forRoom(w, d, h).patterns().isEmpty()).orElse(false)) {
            return true;
        }
        return scheme.wallFor(w, d, h)
                .map(wall -> wall.forRoom(w, d, h))
                .map(WallPatternEntry::patterns)
                .map(patterns -> !patterns.isEmpty())
                .orElse(false);
    }

}
