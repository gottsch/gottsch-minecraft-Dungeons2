/*
 * This file is part of  Dungeons2.
 * Copyright (c) 2026 Mark Gottschling (gottsch)
 *
 * Dungeons2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dungeons2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Dungeons2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package mod.gottsch.forge.dungeons2.core.loader;

import mod.gottsch.forge.dungeons2.core.config.DungeonGenerationConfig;
import mod.gottsch.forge.dungeons2.core.loader.JigsawChains.Template;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Backlog #52: a transition has to <em>reach</em>, and until now nothing checked that it did.
 *
 * <h2>The span is the traverse; the volume is not</h2>
 * <p>What must equal the floor-to-floor pitch is the distance between a transition's two
 * {@code dungeons2:door} markers &mdash; those are the points the maze attaches the two floors'
 * corridors to. The piece's bounding box is a larger and unrelated number: {@code stairs_1} is a
 * 21-block-tall building whose doors are 12 apart, {@code ladder1} is 18 tall with the same 12-block
 * traverse. {@code DungeonStructure} used to compare the assembly's <em>volume</em> against a
 * hand-copied 12, which passed {@code stairs_1} on the strength of the 21 without ever looking at
 * where its doors were.</p>
 *
 * <h2>Why this test rather than only the runtime check</h2>
 * <p>The runtime check fires during worldgen, once, into a log, on whatever seed happened to
 * generate. This one fails the build the moment the planner's pitch and the shipped templates
 * disagree &mdash; which is exactly what happens the moment {@code floorHeight} is raised for
 * backlog #29. Without it the symptom in game is a stairwell that ends in stone: the upper door
 * marker is handed to the maze as if it sat on the upper walking plane, and nothing downstream
 * re-checks it. No error, no log, no test.</p>
 *
 * <p>So: <strong>if you are here because this test went red after changing the floor height, it is
 * working.</strong> The templates named in the failure need re-cutting; see backlog #52 for which
 * of them can be stretched and which are monolithic.</p>
 */
@org.junit.jupiter.api.Tag("release") // fully qualified: net.minecraft.nbt.Tag is imported below
class TransitionSpanTest {

    private static final String TRANSITION_ROOT = "/data/dungeons2/structures/transitions";
    private static final String POOL_ROOT = "/data/dungeons2/worldgen/template_pool/transitions";

    private static final String DOOR = "dungeons2:door";
    private static final String CONNECTOR = "dungeons2:connector";

    /**
     * Blocks a chained segment gains per join, on top of its own up-jigsaw's local Y.
     *
     * <p>Vanilla's {@code JigsawPlacement} seats a child so its target marker is <em>adjacent</em>
     * to the source marker rather than coincident with it, which for a vertical connection is one
     * block. This is the one number here that is not read out of a file, and it is
     * <strong>self-checking on the shipped state</strong>: {@code stairs_2}'s segments rise 5 by
     * marker, and 5 does not divide the shipped pitch of 12 while 5+1 does. If the constant were
     * wrong, {@link #aChainedTransitionCanLandExactlyOnThePitch} would be red today.</p>
     */
    private static final int JIGSAW_JOIN_OFFSET = 1;

    /**
     * What worldgen actually plans at, asked rather than copied &mdash; the shipped
     * {@code generation_config}'s pitch, which {@code RoomHeightBandTest} pins against the file on
     * disk, and which the planner's own default delegates to.
     */
    private static int pitch() {
        return DungeonGenerationConfig.DEFAULT.pitch();
    }

    /**
     * A monolithic transition &mdash; two door planes in one template &mdash; must span the pitch by
     * itself. It has no way to stretch: {@code stairs_1}'s upper marker cannot move without
     * re-cutting the stairs beneath it.
     */
    @Test
    void everyMonolithicTransitionSpansExactlyThePitch() {
        int pitch = pitch();
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, Template> entry : reachable().entrySet()) {
            Template template = entry.getValue();
            if (!template.isMonolithic()) {
                continue;
            }
            checked++;
            if (template.doorSpan() != pitch) {
                offenders.add(entry.getKey() + ": doors at Y=" + template.doorYs()
                        + " span " + template.doorSpan() + ", pitch is " + pitch);
            }
        }
        // No "checked >= 1" guard. ZERO monolithic transitions in a pool is a legitimate state --
        // a motif may ship only chained ones, which is exactly where classic stands since ladder1
        // and stairs_1 were retired for the pitch raise. Vacuity is guarded elsewhere and better:
        // reachable() fails if no pool names anything, and theSweepFindsTheShippedTransitions fails
        // if the markers are not being read.
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " shipped transition(s) do not span the floor pitch of " + pitch
                    + ". The two floors would not connect, silently -- re-cut the template(s) or "
                    + "change the pitch back:\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * A chained transition must land <strong>exactly</strong> on the upper plane, and the only way
     * to know is to WALK IT: follow each link's {@code target} to the template carrying that
     * {@code name}, seat it, and add up the rise until a piece with a door plane terminates the
     * chain.
     *
     * <h2>The model this replaced was wrong, and passed anyway</h2>
     * <p>It assumed a chain <em>repeats one middle segment</em>, so it asked whether the per-segment
     * rise divided the pitch. Neither shipped chain repeats: {@code stairs_2} is
     * bottom&rarr;mid&rarr;top and {@code winding_stairs_1} is bottom&rarr;mid_bottom&rarr;
     * mid_top&rarr;top, each link naming exactly one successor. The old check agreed with reality
     * at the old pitch of 12 only because 12 happens to be a multiple of 6 &mdash; a coincidence,
     * not a measurement. At 22 it called the correct {@code winding_stairs_1} chain broken (its
     * last piece contributes its door offset of 4, not another full segment of 6, for 6+6+6+4=22),
     * which is the failure mode that gets a release gate ignored.</p>
     *
     * <p>The walk needs no new information &mdash; the names were always in the files.</p>
     */
    @Test
    void aChainedTransitionLandsExactlyOnThePitch() {
        int pitch = pitch();
        Map<String, Template> shipped = reachable();
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, Template> entry : shipped.entrySet()) {
            Template template = entry.getValue();
            if (template.isMonolithic() || template.outgoing() == null
                    || template.doorYs().isEmpty()) {
                continue; // not an ENTRY piece: no door plane to start from, or nothing to follow
            }
            checked++;
            walk(entry.getKey(), template, shipped, pitch).ifPresent(offenders::add);
        }
        assertTrue(checked >= 1, "expected at least one chained transition, found " + checked);
        if (!offenders.isEmpty()) {
            fail(offenders.size() + " chained transition(s) do not land on the floor pitch of "
                    + pitch + ":\n  " + String.join("\n  ", offenders));
        }
    }

    /**
     * Follows one chain from its entry piece, returning a complaint or empty if it lands.
     *
     * <p>The walking is {@code JigsawChains}'; what belongs here is the terminal question, which is
     * the half that differs between the two gates. A transition must span the pitch EXACTLY,
     * because both planes it joins are already fixed; an entrance only has to drop far enough
     * &mdash; see {@code EntranceSpanTest}.</p>
     */
    private static Optional<String> walk(String name, Template entry,
                                         Map<String, Template> shipped, int pitch) {
        int entryDoorY = entry.doorYs().get(0);
        List<String> problems = new ArrayList<>();
        for (JigsawChains.Walk walk : JigsawChains.walk(name, shipped)) {
            if (walk.failed()) {
                problems.add(walk.trail() + ": " + walk.failure());
                continue;
            }
            if (walk.end().doorYs().isEmpty()) {
                problems.add(walk.trail() + ": ends at " + walk.endName() + ", which carries no"
                        + " door plane -- the chain arrives nowhere");
                continue;
            }
            int landed = walk.endOriginY() + walk.end().doorYs().get(0) - entryDoorY;
            if (landed != pitch) {
                problems.add(walk.trail() + ": lands " + landed + " above its own door plane, but"
                        + " the pitch is " + pitch + " -- it stops " + Math.abs(pitch - landed)
                        + " block(s) " + (landed < pitch ? "short of" : "past") + " the floor above");
            }
        }
        return problems.isEmpty() ? Optional.empty()
                : Optional.of(String.join("\n  ", problems));
    }

    /**
     * There is no third category. A transition template either carries both door planes itself or
     * hands off to a continuation pool; one that does neither is a stairwell that goes nowhere, and
     * it would assemble and place perfectly happily.
     */
    @Test
    void everyTransitionEitherSpansOrContinues() {
        List<String> orphans = new ArrayList<>();
        for (Map.Entry<String, Template> entry : reachable().entrySet()) {
            Template template = entry.getValue();
            if (!template.isMonolithic() && template.continuationY() == null
                    && template.doorYs().isEmpty()) {
                orphans.add(entry.getKey() + ": no second door plane and no continuation jigsaw");
            }
        }
        if (!orphans.isEmpty()) {
            fail(orphans.size() + " transition template(s) neither span nor continue:\n  "
                    + String.join("\n  ", orphans));
        }
    }

    /** Everything above passes vacuously if the templates are not being found or not read. */
    @Test
    void theSweepFindsTheShippedTransitions() {
        Map<String, Template> found = templates();
        assertTrue(found.size() >= 5, "expected the shipped transition templates, found "
                + found.keySet());
        assertTrue(found.values().stream().anyMatch(Template::isMonolithic),
                "no template carried two door planes -- the markers are not being read");
        assertTrue(found.values().stream().anyMatch(t -> t.continuationY() != null),
                "no template carried a continuation jigsaw -- the markers are not being read");
    }

    /** The numbers, for the record and for whoever re-cuts these. */
    @Test
    void report() {
        int pitch = pitch();
        System.out.println("=== #52 transition spans, planner pitch " + pitch + " ===");
        Map<String, Template> shipped = reachable();
        templates().forEach((name, t) -> System.out.println("  " + name
                + (shipped.containsKey(name) ? "" : "  [RETIRED -- in no pool]")
                + "  doorY=" + t.doorYs()
                + (t.isMonolithic() ? "  span=" + t.doorSpan() : "")
                + (t.continuationY() != null ? "  continues at Y=" + t.continuationY() : "")));
    }

    // ---------- what ships ----------

    private static Map<String, Template> templates() {
        return JigsawChains.templates(TRANSITION_ROOT);
    }

    /** See {@code JigsawChains#reachable}: the gate judges what ships IN PLAY, not what is on disk. */
    private static Map<String, Template> reachable() {
        Map<String, Template> shipped = JigsawChains.reachable(templates(), POOL_ROOT);
        assertFalse(shipped.isEmpty(), "no transition template pool named any element");
        return shipped;
    }
}
