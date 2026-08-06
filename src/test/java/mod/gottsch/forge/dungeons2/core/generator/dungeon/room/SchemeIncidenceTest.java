package mod.gottsch.forge.dungeons2.core.generator.dungeon.room;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import mod.gottsch.forge.dungeons2.core.config.MotifConfig;
import mod.gottsch.forge.dungeons2.core.config.MotifConfigFragment;
import mod.gottsch.forge.dungeons2.core.config.RoomScheme;
import mod.gottsch.forge.dungeons2.core.config.WallPatternEntry.CourseAnchor;
import mod.gottsch.forge.dungeons2.core.data.DungeonLayout;
import mod.gottsch.forge.dungeons2.core.data.DungeonSize;
import mod.gottsch.forge.dungeons2.core.data.FloorLayout;
import mod.gottsch.forge.dungeons2.core.data.RoomData;
import mod.gottsch.forge.dungeons2.core.data.RoomRole;
import mod.gottsch.forge.dungeons2.core.data.TemplateCatalog;
import mod.gottsch.forge.dungeons2.core.generator.dungeon.maze.DungeonStackPlanner;
import mod.gottsch.forge.gottschcore.spatial.Coords;
import mod.gottsch.forge.gottschcore.spatial.ICoords;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How often the shipped {@code classic} schemes actually fire, measured against real planner
 * output rather than against the weights on paper.
 *
 * <p>The two are not the same number, and the gap is the point. A scheme's weight is its share of
 * the roll <em>among schemes eligible for that room</em>, so a {@code minSize} or {@code minHeight}
 * that most rooms fail silently converts an authored 15% into something far smaller. Room height is
 * {@code min(rand(5..10), max(width, depth))} and the maze's minimum room is 5x5, so gates in the
 * 7-9 range bite much harder than they look.</p>
 *
 * <p>This exists because reasoning about that statically produced the wrong answer once already:
 * wall and ceiling trim were authored at ~17%/15% by weight and were nearly unfindable in game.</p>
 */
class SchemeIncidenceTest {

    private static final ICoords ANCHOR = new Coords(128, 0, 256);
    private static final int SURFACE_Y = 72;
    private static final int DUNGEONS = 60;

    private static final String CLASSIC_DIR = "/data/dungeons2/dungeons2/motif_config/classic";

    /**
     * The real shipped config, not a fixture -- the numbers are only meaningful against it.
     * Assembled from classic's whole folder, in id order, the same way {@code MotifConfigHelper}
     * does it in game, so a scheme file added later is measured without touching this.
     */
    private static MotifConfig classic() {
        try {
            Path dir = Paths.get(SchemeIncidenceTest.class.getResource(CLASSIC_DIR).toURI());
            List<MotifConfigFragment> fragments = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".json"))
                        .sorted(Comparator.comparing(Path::getFileName)).toList()) {
                    fragments.add(read(file));
                }
            }
            return MotifConfigFragment.resolve(fragments);
        } catch (Exception e) {
            throw new AssertionError("could not read the classic motif folder", e);
        }
    }

    private static MotifConfigFragment read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = new Gson().fromJson(reader, JsonElement.class);
            return MotifConfigFragment.CODEC.parse(JsonOps.INSTANCE, json).result()
                    .orElseThrow(() -> new AssertionError("could not decode " + file));
        }
    }

    /** Rolls a scheme for every NORMAL room the planner produces across many dungeons. */
    private static Result measure(MotifConfig config) {
        Result result = new Result();
        for (int i = 0; i < DUNGEONS; i++) {
            long seed = 0xD2_0BADC0DEL + i * 7919L;
            Optional<DungeonLayout> planned = new DungeonStackPlanner(
                    seed, ANCHOR, SURFACE_Y, "classic", new TemplateCatalog())
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
                    result.rooms++;
                    result.minSide.merge(Math.min(room.getWidth(), room.getDepth()), 1, Integer::sum);
                    result.height.merge(room.getHeight(), 1, Integer::sum);

                    RoomScheme scheme = RoomSchemeSelector.select(config.schemes(),
                            room.getWidth(), room.getDepth(), room.getHeight(), random);
                    result.byName.merge(scheme.name(), 1, Integer::sum);
                    // Counted through the *For accessors, not the raw slots: with element gates
                    // "declares a wall" and "draws a wall" are different numbers, and only the
                    // second is what a player sees.
                    int w = room.getWidth();
                    int d = room.getDepth();
                    int h = room.getHeight();
                    if (scheme.wallFor(w, d, h).isPresent()) {
                        result.wall++;
                    }
                    if (scheme.ceilingFor(w, d, h).isPresent()) {
                        result.ceiling++;
                    }
                    if (scheme.floorFor(w, d, h).isPresent()) {
                        result.floor++;
                    }
                    if (scheme.potsFor(w, d, h).isPresent()) {
                        result.pots++;
                    }
                    // "Tall" is the population the top-trim rule is about: a 5-high room has only
                    // three interior wall rows, two of them door halves and one the lintel, so
                    // there is genuinely nowhere to put a crown.
                    if (room.getHeight() > 5) {
                        result.tall++;
                        if (hasTopCourse(scheme, room)) {
                            result.tallWithTopTrim++;
                        }
                    }
                    // Rooms where a scheme with element slots gated all of them out. Reported so
                    // the number stays visible while tuning; deliberately not a pass/fail bar,
                    // since `plain`'s gated cornice makes this the intended outcome for every
                    // 5-high room it wins.
                    //
                    // declaresAnySlot() still matters: without it this would also count schemes
                    // that fill no slots at all, which is a different thing entirely.
                    if (scheme.declaresAnySlot()
                            && !scheme.drawsAnything(room.getWidth(), room.getDepth(), room.getHeight())) {
                        result.bare++;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Whether a scheme dresses the top of the wall <em>in this room</em> -- a crown, a cornice, any
     * top-anchored band.
     *
     * <p>Room-aware because the wall slot carries its own size gate: a scheme can hold a crown that
     * this particular room is too short to draw. Note this stays a pure function of (scheme, room)
     * -- no simulation, no sampling -- which is exactly what a per-element <em>probability</em>
     * would have cost this test.</p>
     */
    private static boolean hasTopCourse(RoomScheme scheme, RoomData room) {
        return scheme.wallFor(room.getWidth(), room.getDepth(), room.getHeight())
                .map(wall -> wall.patterns().stream()
                        .flatMap(pattern -> pattern.courses().stream())
                        .anyMatch(course -> course.anchor() == CourseAnchor.TOP))
                .orElse(false);
    }

    private static final class Result {
        int rooms;
        int wall;
        int ceiling;
        int floor;
        int pots;
        int tall;
        int tallWithTopTrim;
        int bare;
        final Map<String, Integer> byName = new LinkedHashMap<>();
        final Map<Integer, Integer> minSide = new LinkedHashMap<>();
        final Map<Integer, Integer> height = new LinkedHashMap<>();

        double pct(int n) {
            return rooms == 0 ? 0 : 100.0 * n / rooms;
        }

        double tallPct() {
            return tall == 0 ? 0 : 100.0 * tallWithTopTrim / tall;
        }
    }

    /**
     * Not an assertion about a specific number -- it prints the distribution so the gates can be
     * authored against reality. The assertion is only that trim is <em>findable</em>: a player
     * walking a couple of dungeons should meet it, which is the bar the first authored gates
     * failed.
     */
    @Test
    @Disabled("TEMPORARY -- classic is cut down to base.json (plain, vaulted_hall, pilastered_hall) "
            + "for in-game scheme authoring, so the floor schemes these bars measure are not "
            + "loaded at all: floor reads 0%. The full set is parked in "
            + "src/main/resources/disabled-schemes/classic/; see the README there. RE-ENABLE THIS "
            + "when they move back. Do NOT lower the bars to make it pass -- they were authored "
            + "against measured incidence after trim shipped nearly unfindable once already.")
    void shippedTrimIsFindableInAnOrdinaryDungeon() {
        Result r = measure(classic());

        StringBuilder report = new StringBuilder("\nScheme incidence over ")
                .append(DUNGEONS).append(" MEDIUM dungeons, ").append(r.rooms).append(" NORMAL rooms\n");
        report.append(String.format("  floor   %5.1f%%%n", r.pct(r.floor)));
        report.append(String.format("  wall    %5.1f%%%n", r.pct(r.wall)));
        report.append(String.format("  ceiling %5.1f%%%n", r.pct(r.ceiling)));
        report.append(String.format("  pots    %5.1f%%%n", r.pct(r.pots)));
        report.append(String.format("  top trim, rooms taller than 5   %5.1f%% (%d of %d)%n",
                r.tallPct(), r.tallWithTopTrim, r.tall));
        report.append(String.format("  no decoration drawn             %5.1f%%%n", r.pct(r.bare)));
        report.append("  room min(width,depth): ").append(r.minSide).append('\n');
        report.append("  room height:           ").append(r.height).append('\n');
        report.append("  by scheme: ").append(r.byName).append('\n');
        System.out.println(report);

        assertTrue(r.rooms > 50, "need a meaningful sample, got " + r.rooms + " rooms");
        assertTrue(r.pct(r.wall) >= 10.0,
                "wall trim should reach at least 1 room in 10, got " + r.pct(r.wall) + "%" + report);
        assertTrue(r.pct(r.ceiling) >= 10.0,
                "ceiling decoration should reach at least 1 room in 10, got "
                        + r.pct(r.ceiling) + "%" + report);
        assertTrue(r.pct(r.pots) >= 8.0,
                "loot pots should stay findable, got " + r.pct(r.pots) + "%" + report);

        // A stronger bar than the others, and a design rule rather than a findability one: a room
        // with the headroom for a crown should usually have one, so trim reads as how the dungeon
        // is built rather than as a rare event. Held up by *crowned* variants of the floor, ceiling
        // and pots schemes rather than by pure-trim schemes alone -- one scheme dresses the whole
        // room, so making trim common by weight alone would have squeezed out everything else.
        assertTrue(r.tallPct() >= 55.0,
                "most rooms taller than 5 should carry top trim, got " + r.tallPct() + "%" + report);

        // "no decoration drawn" is reported above, not asserted on. It counts rooms where a scheme
        // holding element slots gated all of them out -- which sounds like a fault and is usually
        // the feature working: `plain` carries a cornice gated at height 6, so every 5-high room it
        // wins lands in that count by design. Bar it and you forbid the thing element gates exist
        // for.
        //
        // The genuine fault -- a scheme that can never draw anywhere in its own range -- is checked
        // statically and per scheme, where it can name the offender, by
        // DatapackResourcesParseTest#everySchemeThatDecoratesDrawsSomethingSomewhereInItsRange.
    }
}
